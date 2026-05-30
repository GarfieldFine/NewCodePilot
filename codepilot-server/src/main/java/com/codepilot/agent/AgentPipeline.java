package com.codepilot.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Orchestrates agent execution in a multi-stage pipeline.
 *
 * Pipeline stages:
 *   Stage 1 (SEQUENTIAL): RepositoryAnalyze → DiffAnalyze → ContextBuild
 *   Stage 2 (PARALLEL):    RiskDetect(Rules) + RiskDetect(AI)
 *   Stage 3 (SEQUENTIAL):  ReviewGenerate
 *   Stage 4 (FINAL):       SummaryMerge
 */
@Slf4j
@Component
public class AgentPipeline {

    private final AgentExecutor agentExecutor;

    public AgentPipeline(AgentExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
    }

    /**
     * A single stage in the pipeline. Contains a list of agents and execution mode.
     */
    public record PipelineStage(String name, List<Agent> agents, ExecutionMode mode) {
        public enum ExecutionMode { SEQUENTIAL, PARALLEL }
    }

    /**
     * Execute the full pipeline synchronously (for blocking analyze flow).
     */
    public AgentContext execute(List<PipelineStage> stages, AgentContext context,
                                Consumer<AgentEvent> eventSink) {
        log.info("Starting agent pipeline for analysis {}", context.getAnalysisId());

        for (PipelineStage stage : stages) {
            eventSink.accept(AgentEvent.pipelineStage(context.getAnalysisId(), stage.name()));
            log.info("Pipeline stage [{}] starting with {} agents (mode={})",
                    stage.name(), stage.agents().size(), stage.mode());

            List<AgentResult> results;
            if (stage.mode() == PipelineStage.ExecutionMode.PARALLEL) {
                results = agentExecutor.runParallel(stage.agents(), context, eventSink);
            } else {
                results = agentExecutor.runSequential(stage.agents(), context, eventSink);
            }

            logStageResults(stage.name(), results);
        }

        log.info("Agent pipeline completed for analysis {}", context.getAnalysisId());
        return context;
    }

    /**
     * Execute the full pipeline as a Flux of AgentEvent (for streaming flow).
     */
    public Flux<AgentEvent> executeStream(List<PipelineStage> stages, AgentContext context) {
        return Flux.create(sink -> {
            try {
                execute(stages, context, sink::next);
                sink.complete();
            } catch (Exception e) {
                log.error("Pipeline stream error: {}", e.getMessage());
                sink.error(e);
            }
        });
    }

    /**
     * Execute the full pipeline with streaming agents (for three-layer streaming flow).
     * Agents that override executeStreaming will emit CHUNK_PROGRESS and AI_TOKEN events.
     *
     * Uses Sinks.Many with a bounded buffer (64 events) instead of Flux.create's unbounded
     * buffer. Pipeline execution is deferred via Flux.defer so the Sink is guaranteed to
     * have a subscriber (the Netty SSE writer) before the first event is emitted.
     */
    public Flux<AgentEvent> executeStreaming(List<PipelineStage> stages, AgentContext context) {
        return Flux.defer(() -> {
            Sinks.Many<AgentEvent> sink = Sinks.many()
                    .multicast()
                    .onBackpressureBuffer(64);

            Consumer<AgentEvent> eventSink = event -> {
                Sinks.EmitResult r = sink.tryEmitNext(event);
                if (r.isFailure()) {
                    LockSupport.parkNanos(100_000);
                    while (!sink.tryEmitNext(event).isSuccess()) {
                        LockSupport.parkNanos(100_000);
                    }
                }
            };

            // Run pipeline on a worker thread so the subscribing thread (boundedElastic)
            // is not blocked by the long-running pipeline execution.
            // Uses a plain Thread — boundedElastic workers are already pooled.
            Thread pipelineThread = new Thread(() -> {
                try {
                    for (PipelineStage stage : stages) {
                        eventSink.accept(AgentEvent.pipelineStage(context.getAnalysisId(), stage.name()));
                        log.info("Pipeline stage [{}] starting with {} agents (mode={})",
                                stage.name(), stage.agents().size(), stage.mode());

                        List<AgentResult> results;
                        if (stage.mode() == PipelineStage.ExecutionMode.PARALLEL) {
                            results = agentExecutor.runParallelStreaming(stage.agents(), context, eventSink);
                        } else {
                            results = agentExecutor.runSequentialStreaming(stage.agents(), context, eventSink);
                        }

                        logStageResults(stage.name(), results);
                    }
                    log.info("Agent pipeline (streaming) completed for analysis {}", context.getAnalysisId());
                    sink.tryEmitComplete();
                } catch (Exception e) {
                    log.error("Pipeline streaming error: {}", e.getMessage());
                    sink.tryEmitError(e);
                }
            }, "pipeline-" + context.getAnalysisId());
            pipelineThread.setDaemon(true);
            pipelineThread.start();

            return sink.asFlux();
        });
    }

    private void logStageResults(String stageName, List<AgentResult> results) {
        long succeeded = results.stream().filter(r -> r.getStatus() == AgentStatus.COMPLETED).count();
        long failed = results.stream().filter(r -> r.getStatus() == AgentStatus.FAILED).count();
        long skipped = results.stream().filter(r -> r.getStatus() == AgentStatus.SKIPPED).count();
        log.info("Stage [{}] complete: {} succeeded, {} failed, {} skipped",
                stageName, succeeded, failed, skipped);
    }
}
