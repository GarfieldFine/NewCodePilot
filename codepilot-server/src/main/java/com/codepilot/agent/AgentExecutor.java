package com.codepilot.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Executes individual agents and emits AgentEvent lifecycle events.
 */
@Slf4j
@Component
public class AgentExecutor {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Run a single agent (sync execute), emitting lifecycle events via callback.
     */
    public AgentResult runAgent(Agent agent, AgentContext context, Consumer<AgentEvent> eventSink) {
        if (!agent.shouldRun(context)) {
            AgentResult skipped = AgentResult.skipped(agent.getName(), "Precondition not met");
            eventSink.accept(AgentEvent.skipped(context.getAnalysisId(), agent.getName(), skipped.getSummary()));
            return skipped;
        }

        long start = System.currentTimeMillis();
        String traceId = context.getAnalysisId();

        eventSink.accept(AgentEvent.started(traceId, agent.getName(), agent.getDescription()));
        eventSink.accept(AgentEvent.running(traceId, agent.getName(), "Executing " + agent.getName()));

        try {
            AgentResult result = agent.execute(context);
            result.setDurationMs(System.currentTimeMillis() - start);
            eventSink.accept(AgentEvent.completed(traceId, agent.getName(), result.getSummary(), result.getOutput()));
            log.info("Agent [{}] completed in {}ms: {}", agent.getName(), result.getDurationMs(), result.getSummary());
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Agent [{}] failed after {}ms: {}", agent.getName(), duration, e.getMessage());
            eventSink.accept(AgentEvent.failed(traceId, agent.getName(), e.getMessage()));
            return AgentResult.failed(agent.getName(), e.getMessage());
        }
    }

    /**
     * Run a single agent using streaming execution (executeStreaming).
     * The agent itself emits intermediate events (chunk progress, AI tokens).
     * Only calls executeStreaming — the agent is responsible for its own logic.
     */
    public AgentResult runAgentStreaming(Agent agent, AgentContext context, Consumer<AgentEvent> eventSink) {
        if (!agent.shouldRun(context)) {
            AgentResult skipped = AgentResult.skipped(agent.getName(), "Precondition not met");
            eventSink.accept(AgentEvent.skipped(context.getAnalysisId(), agent.getName(), skipped.getSummary()));
            return skipped;
        }

        long start = System.currentTimeMillis();
        String traceId = context.getAnalysisId();

        eventSink.accept(AgentEvent.started(traceId, agent.getName(), agent.getDescription()));
        eventSink.accept(AgentEvent.running(traceId, agent.getName(), "Executing " + agent.getName()));

        try {
            agent.executeStreaming(context, eventSink);
            long duration = System.currentTimeMillis() - start;
            log.info("Agent [{}] completed (streaming) in {}ms", agent.getName(), duration);
            String summary = agent.getName() + " completed in " + duration + "ms";
            eventSink.accept(AgentEvent.completed(traceId, agent.getName(), summary, Map.of("durationMs", duration)));
            return AgentResult.success(agent.getName(), summary, Map.of("durationMs", duration));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Agent [{}] failed after {}ms: {}", agent.getName(), duration, e.getMessage());
            eventSink.accept(AgentEvent.failed(traceId, agent.getName(), e.getMessage()));
            return AgentResult.failed(agent.getName(), e.getMessage());
        }
    }

    /**
     * Run multiple agents sequentially.
     */
    public List<AgentResult> runSequential(List<Agent> agents, AgentContext context,
                                           Consumer<AgentEvent> eventSink) {
        List<AgentResult> results = new ArrayList<>();
        for (Agent agent : agents) {
            results.add(runAgent(agent, context, eventSink));
        }
        return results;
    }

    /**
     * Run multiple agents sequentially (streaming variant).
     */
    public List<AgentResult> runSequentialStreaming(List<Agent> agents, AgentContext context,
                                                    Consumer<AgentEvent> eventSink) {
        List<AgentResult> results = new ArrayList<>();
        for (Agent agent : agents) {
            results.add(runAgentStreaming(agent, context, eventSink));
        }
        return results;
    }

    /**
     * Run multiple agents in parallel.
     */
    public List<AgentResult> runParallel(List<Agent> agents, AgentContext context,
                                         Consumer<AgentEvent> eventSink) {
        List<CompletableFuture<AgentResult>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(
                        () -> runAgent(agent, context, eventSink), executor))
                .toList();

        return futures.stream()
                .map(f -> {
                    try { return f.get(120, TimeUnit.SECONDS); }
                    catch (Exception e) { return AgentResult.failed("parallel-agent", e.getMessage()); }
                })
                .toList();
    }

    /**
     * Run multiple agents in parallel (streaming variant).
     */
    public List<AgentResult> runParallelStreaming(List<Agent> agents, AgentContext context,
                                                  Consumer<AgentEvent> eventSink) {
        List<CompletableFuture<AgentResult>> futures = agents.stream()
                .map(agent -> CompletableFuture.supplyAsync(
                        () -> runAgentStreaming(agent, context, eventSink), executor))
                .toList();

        return futures.stream()
                .map(f -> {
                    try { return f.get(120, TimeUnit.SECONDS); }
                    catch (Exception e) { return AgentResult.failed("parallel-agent", e.getMessage()); }
                })
                .toList();
    }
}
