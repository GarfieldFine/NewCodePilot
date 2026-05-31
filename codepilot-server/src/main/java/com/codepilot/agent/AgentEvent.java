package com.codepilot.agent;

import com.codepilot.review.AnalysisResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {
    private String traceId;
    private AgentEventType type;
    private String agentName;
    private String message;
    private long timestamp;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    // -- Factory methods for agent lifecycle --

    public static AgentEvent started(String traceId, String agentName, String description) {
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.AGENT_STARTED)
                .agentName(agentName)
                .message(description)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent running(String traceId, String agentName, String stepInfo) {
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.AGENT_RUNNING)
                .agentName(agentName)
                .message(stepInfo)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent completed(String traceId, String agentName, String summary,
                                       Map<String, Object> output) {
        AgentEventBuilder builder = AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.AGENT_COMPLETED)
                .agentName(agentName)
                .message(summary)
                .timestamp(System.currentTimeMillis());
        if (output != null) {
            builder.metadata(new LinkedHashMap<>(output));
        }
        return builder.build();
    }

    public static AgentEvent failed(String traceId, String agentName, String error) {
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.AGENT_FAILED)
                .agentName(agentName)
                .message(error)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent skipped(String traceId, String agentName, String reason) {
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.AGENT_SKIPPED)
                .agentName(agentName)
                .message(reason)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent pipelineStage(String traceId, String stageName) {
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.PIPELINE_STAGE)
                .agentName(stageName)
                .message("")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // -- Factory methods for chunk / token / completion --

    public static AgentEvent chunkProgress(String traceId, int chunkIndex, int totalChunks,
                                           String status, String fileName) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("chunkIndex", chunkIndex);
        meta.put("totalChunks", totalChunks);
        meta.put("chunkStatus", status);
        if (fileName != null) {
            meta.put("fileName", fileName);
        }
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.CHUNK_PROGRESS)
                .agentName("ChunkAnalyzer")
                .message("Chunk " + (chunkIndex + 1) + "/" + totalChunks + " " + status)
                .timestamp(System.currentTimeMillis())
                .metadata(meta)
                .build();
    }

    public static AgentEvent aiToken(String traceId, String agentName, String token,
                                     Map<String, Object> extraMeta) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("content", token);
        if (extraMeta != null) {
            meta.putAll(extraMeta);
        }
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.AI_TOKEN)
                .agentName(agentName)
                .message(token)
                .timestamp(System.currentTimeMillis())
                .metadata(meta)
                .build();
    }

    public static AgentEvent analysisComplete(String traceId, AnalysisResult result) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("result", result);
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.ANALYSIS_COMPLETE)
                .agentName("ReviewEngine")
                .message("Analysis complete")
                .timestamp(System.currentTimeMillis())
                .metadata(meta)
                .build();
    }

    public static AgentEvent error(String traceId, String errorMessage) {
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.ERROR)
                .agentName("ReviewEngine")
                .message(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentEvent multiProviderComplete(String traceId, Object result) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("result", result);
        return AgentEvent.builder()
                .traceId(traceId)
                .type(AgentEventType.MULTI_PROVIDER_COMPLETE)
                .agentName("ReviewEngine")
                .message("Multi-provider comparison complete")
                .timestamp(System.currentTimeMillis())
                .metadata(meta)
                .build();
    }

    /**
     * Convert to legacy JSON format for backward compatibility with SseEmitterService.
     */
    public String toLegacyJson() {
        String typeStr;
        switch (type) {
            case AGENT_STARTED -> typeStr = "agent_start";
            case AGENT_COMPLETED -> typeStr = "agent_complete";
            case AGENT_FAILED -> typeStr = "agent_error";
            case AGENT_SKIPPED -> typeStr = "agent_skip";
            case PIPELINE_STAGE -> typeStr = "pipeline_stage";
            default -> typeStr = type.name().toLowerCase();
        }
        return String.format("{\"type\":\"%s\",\"agentName\":\"%s\",\"message\":\"%s\"}",
                typeStr.replace("\"", "\\\""),
                agentName != null ? agentName.replace("\"", "\\\"") : "",
                message != null ? message.replace("\"", "\\\"") : "");
    }
}
