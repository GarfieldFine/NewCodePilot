package com.codepilot.chunk;

import com.codepilot.agent.AgentEvent;
import com.codepilot.review.PRAnalysisChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Concurrent chunk analysis scheduler.
 *
 * Submits all chunks for parallel AI review, collects results in original order,
 * handles timeouts and partial failures gracefully.
 */
@Slf4j
@Component
public class ChunkAnalyzer {

    private final ExecutorService executor;
    private final long timeoutSeconds;

    public ChunkAnalyzer(@Value("${review.chunk-parallelism:4}") int parallelism,
                         @Value("${review.chunk-timeout-seconds:180}") long timeoutSeconds) {
        this.executor = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r, "chunk-analyzer");
            t.setDaemon(true);
            return t;
        });
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Analyze all chunks concurrently using the provided analysis function.
     * Results are returned in the same order as the input chunks.
     *
     * @param chunks    chunks to analyze
     * @param analyzeFn function that analyzes a single chunk and returns the AI review text
     * @return ordered list of analysis results (one per chunk)
     */
    public List<ChunkResult> analyze(List<PRAnalysisChunk> chunks,
                                     ChunkAnalysisFunction analyzeFn) {
        if (chunks.isEmpty()) return List.of();

        log.info("Starting concurrent analysis of {} chunks (parallelism={}, timeout={}s)",
                chunks.size(),
                ((ThreadPoolExecutor) executor).getMaximumPoolSize(),
                timeoutSeconds);

        // Track chunk index for ordering
        List<CompletableFuture<ChunkResult>> futures = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            final int index = i;
            final PRAnalysisChunk chunk = chunks.get(i);
            CompletableFuture<ChunkResult> future = CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                try {
                    String output = analyzeFn.analyze(chunk);
                    long duration = System.currentTimeMillis() - start;
                    log.info("Chunk {}/{} analyzed in {}ms ({} chars)", index + 1, chunks.size(), duration, output.length());
                    return ChunkResult.success(index, output, duration);
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    log.error("Chunk {}/{} failed after {}ms: {}", index + 1, chunks.size(), duration, e.getMessage());
                    return ChunkResult.failed(index, e.getMessage(), duration);
                }
            }, executor);
            futures.add(future);
        }

        // Collect results in original order
        ChunkResult[] results = new ChunkResult[chunks.size()];
        for (int i = 0; i < futures.size(); i++) {
            try {
                ChunkResult result = futures.get(i).get(timeoutSeconds, TimeUnit.SECONDS);
                results[result.index()] = result;
            } catch (TimeoutException e) {
                log.error("Chunk {} timed out after {}s", i + 1, timeoutSeconds);
                results[i] = ChunkResult.failed(i, "Timeout after " + timeoutSeconds + "s", timeoutSeconds * 1000);
            } catch (Exception e) {
                log.error("Chunk {} unexpected error: {}", i + 1, e.getMessage());
                results[i] = ChunkResult.failed(i, e.getMessage(), 0);
            }
        }

        List<ChunkResult> resultList = Arrays.asList(results);
        long succeeded = resultList.stream().filter(ChunkResult::success).count();
        log.info("Chunk analysis complete: {}/{} succeeded", succeeded, chunks.size());

        return resultList;
    }

    /**
     * Analyze all chunks concurrently with progress callback for streaming.
     * Emits CHUNK_PROGRESS events per chunk start/completion.
     *
     * @param chunks           chunks to analyze
     * @param analyzeFn        function that analyzes a single chunk
     * @param progressCallback callback for emitting AgentEvent progress
     * @param traceId          analysis trace ID for event correlation
     * @return ordered list of analysis results
     */
    public List<ChunkResult> analyze(List<PRAnalysisChunk> chunks,
                                     ChunkAnalysisFunction analyzeFn,
                                     Consumer<AgentEvent> progressCallback,
                                     String traceId) {
        if (chunks.isEmpty()) return List.of();

        log.info("Starting concurrent analysis of {} chunks (parallelism={}, timeout={}s, streaming)",
                chunks.size(),
                ((ThreadPoolExecutor) executor).getMaximumPoolSize(),
                timeoutSeconds);

        List<CompletableFuture<ChunkResult>> futures = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            final int index = i;
            final PRAnalysisChunk chunk = chunks.get(i);
            final int totalChunks = chunks.size();

            CompletableFuture<ChunkResult> future = CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                String firstFile = chunk.files().isEmpty() ? null : chunk.files().get(0).getFilename();

                // Emit chunk started
                progressCallback.accept(
                        AgentEvent.chunkProgress(traceId, index, totalChunks, "STARTED", firstFile));

                try {
                    String output = analyzeFn.analyze(chunk);
                    long duration = System.currentTimeMillis() - start;
                    log.info("Chunk {}/{} analyzed in {}ms ({} chars)", index + 1, totalChunks, duration, output.length());

                    // Emit chunk completed
                    progressCallback.accept(
                            AgentEvent.chunkProgress(traceId, index, totalChunks, "COMPLETED", firstFile));

                    return ChunkResult.success(index, output, duration);
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    log.error("Chunk {}/{} failed after {}ms: {}", index + 1, totalChunks, duration, e.getMessage());

                    // Emit chunk failed
                    progressCallback.accept(
                            AgentEvent.chunkProgress(traceId, index, totalChunks, "FAILED", firstFile));

                    return ChunkResult.failed(index, e.getMessage(), duration);
                }
            }, executor);
            futures.add(future);
        }

        // Collect results in original order
        ChunkResult[] results = new ChunkResult[chunks.size()];
        for (int i = 0; i < futures.size(); i++) {
            try {
                ChunkResult result = futures.get(i).get(timeoutSeconds, TimeUnit.SECONDS);
                results[result.index()] = result;
            } catch (TimeoutException e) {
                log.error("Chunk {} timed out after {}s", i + 1, timeoutSeconds);
                results[i] = ChunkResult.failed(i, "Timeout after " + timeoutSeconds + "s", timeoutSeconds * 1000);
            } catch (Exception e) {
                log.error("Chunk {} unexpected error: {}", i + 1, e.getMessage());
                results[i] = ChunkResult.failed(i, e.getMessage(), 0);
            }
        }

        List<ChunkResult> resultList = Arrays.asList(results);
        long succeeded = resultList.stream().filter(ChunkResult::success).count();
        log.info("Chunk analysis complete: {}/{} succeeded", succeeded, chunks.size());

        return resultList;
    }

    /**
     * Functional interface for analyzing a single chunk.
     * Implementations handle prompt building + AI invocation.
     */
    @FunctionalInterface
    public interface ChunkAnalysisFunction {
        String analyze(PRAnalysisChunk chunk) throws Exception;
    }

    /**
     * Result of analyzing a single chunk.
     */
    public record ChunkResult(int index, boolean success, String output, long durationMs) {
        public static ChunkResult success(int index, String output, long durationMs) {
            return new ChunkResult(index, true, output, durationMs);
        }

        public static ChunkResult failed(int index, String error, long durationMs) {
            return new ChunkResult(index, false, "[AI Review failed: " + error + "]", durationMs);
        }

        public boolean isSuccess() { return success; }
    }
}
