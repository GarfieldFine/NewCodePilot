package com.codepilot.review;

import com.codepilot.agent.AgentEvent;
import com.codepilot.agent.AgentPipeline;
import com.codepilot.agent.AgentContext;
import com.codepilot.agent.AgentPipeline.PipelineStage;
import com.codepilot.agent.AgentPipeline.PipelineStage.ExecutionMode;
import com.codepilot.agent.agents.*;
import com.codepilot.ai.AiProvider;
import com.codepilot.ai.AiProviderFactory;
import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import com.codepilot.model.enums.AnalysisStatus;
import com.codepilot.model.enums.RiskLevel;
import com.codepilot.rule.RuleEngine;
import com.codepilot.rule.RuleResult;
import com.codepilot.scorer.RiskScore;
import com.codepilot.scorer.RiskScoreCalculator;
import com.codepilot.sse.SseEmitterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * Review engine orchestrating the multi-agent pipeline.
 *
 * Stage 1 (SEQUENTIAL): RepositoryAnalyze → DiffAnalyze → ContextBuild
 * Stage 2 (PARALLEL):    RiskDetect
 * Stage 3 (SEQUENTIAL):  ReviewGenerate
 * Stage 4 (FINAL):       SummaryMerge
 */
@Slf4j
@Component
public class ReviewEngine {

    private final AiProviderFactory aiProviderFactory;
    private final RuleEngine ruleEngine;
    private final RiskScoreCalculator scoreCalculator;
    private final DiffContextBuilder contextBuilder;
    private final SseEmitterService sseService;
    private final PrSplitter prSplitter;

    // Agent layer
    private final AgentPipeline agentPipeline;
    private final RepositoryAnalyzeAgent repositoryAnalyzeAgent;
    private final DiffAnalyzeAgent diffAnalyzeAgent;
    private final ContextBuildAgent contextBuildAgent;
    private final RiskDetectAgent riskDetectAgent;
    private final ReviewGenerateAgent reviewGenerateAgent;
    private final SummaryMergeAgent summaryMergeAgent;

    public ReviewEngine(AiProviderFactory aiProviderFactory,
                        RuleEngine ruleEngine,
                        RiskScoreCalculator scoreCalculator,
                        DiffContextBuilder contextBuilder,
                        SseEmitterService sseService,
                        PrSplitter prSplitter,
                        AgentPipeline agentPipeline,
                        RepositoryAnalyzeAgent repositoryAnalyzeAgent,
                        DiffAnalyzeAgent diffAnalyzeAgent,
                        ContextBuildAgent contextBuildAgent,
                        RiskDetectAgent riskDetectAgent,
                        ReviewGenerateAgent reviewGenerateAgent,
                        SummaryMergeAgent summaryMergeAgent) {
        this.aiProviderFactory = aiProviderFactory;
        this.ruleEngine = ruleEngine;
        this.scoreCalculator = scoreCalculator;
        this.contextBuilder = contextBuilder;
        this.sseService = sseService;
        this.prSplitter = prSplitter;
        this.agentPipeline = agentPipeline;
        this.repositoryAnalyzeAgent = repositoryAnalyzeAgent;
        this.diffAnalyzeAgent = diffAnalyzeAgent;
        this.contextBuildAgent = contextBuildAgent;
        this.riskDetectAgent = riskDetectAgent;
        this.reviewGenerateAgent = reviewGenerateAgent;
        this.summaryMergeAgent = summaryMergeAgent;
    }

    /**
     * Execute full PR analysis using the multi-agent pipeline.
     * Maintains backward compatibility with existing PrAnalysisService.
     */
    public Mono<AnalysisResult> analyze(PrInfo prInfo, String providerName) {
        AiProvider aiProvider = aiProviderFactory.getProvider(providerName);
        AgentContext agentContext = AgentContext.from(prInfo, providerName);
        sseService.createEmitter(agentContext.getAnalysisId());

        log.info("Starting agent-pipeline analysis {} with provider: {}",
                agentContext.getAnalysisId(), aiProvider.getProviderName());

        return Mono.fromCallable(() -> {
            List<PipelineStage> stages = buildStages();

            agentPipeline.execute(stages, agentContext, event -> {
                relayToSse(agentContext.getAnalysisId(), event);
            });

            AnalysisResult result = agentContext.getFinalResult();
            if (result != null) {
                sseService.complete(agentContext.getAnalysisId(), result);
                return result;
            }

            return buildResultLegacy(prInfo, agentContext);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    /**
     * Execute streaming PR analysis using the multi-agent pipeline.
     * Emits agent lifecycle events + chunk progress + AI tokens as Flux of AgentEvent.
     */
    public Flux<AgentEvent> analyzeStream(PrInfo prInfo, String providerName) {
        AgentContext agentContext = AgentContext.from(prInfo, providerName);
        sseService.createEmitter(agentContext.getAnalysisId());

        AiProvider aiProvider = aiProviderFactory.getProvider(providerName);
        log.info("Starting agent-pipeline stream analysis {} with {}",
                agentContext.getAnalysisId(), aiProvider.getProviderName());

        List<PipelineStage> stages = buildStages();

        // Use streaming pipeline so agents can emit CHUNK_PROGRESS + AI_TOKEN events
        // Offload to boundedElastic to avoid blocking the Netty event loop
        Flux<AgentEvent> agentEvents = agentPipeline.executeStreaming(stages, agentContext)
                .subscribeOn(Schedulers.boundedElastic());

        // After agents complete, emit ANALYSIS_COMPLETE with final result
        Flux<AgentEvent> completionEvent = Flux.defer(() -> {
            AnalysisResult result = agentContext.getFinalResult();
            if (result == null) {
                result = buildResultLegacy(prInfo, agentContext);
            }
            return Flux.just(AgentEvent.analysisComplete(agentContext.getAnalysisId(), result));
        });

        return Flux.concat(agentEvents, completionEvent)
                .onErrorResume(e -> {
                    log.error("Agent pipeline stream error: {}", e.getMessage());
                    return Flux.just(AgentEvent.error(agentContext.getAnalysisId(), e.getMessage()));
                });
    }

    private List<PipelineStage> buildStages() {
        return List.of(
                new PipelineStage("Understanding Repository",
                        List.of(repositoryAnalyzeAgent, diffAnalyzeAgent, contextBuildAgent),
                        ExecutionMode.SEQUENTIAL),
                new PipelineStage("Detecting Risks",
                        List.of(riskDetectAgent),
                        ExecutionMode.SEQUENTIAL),
                new PipelineStage("Generating AI Review",
                        List.of(reviewGenerateAgent),
                        ExecutionMode.SEQUENTIAL),
                new PipelineStage("Merging Analysis",
                        List.of(summaryMergeAgent),
                        ExecutionMode.SEQUENTIAL)
        );
    }

    /**
     * Relays agent SSE events to the legacy SseEmitterService for backward compatibility.
     */
    private void relayToSse(String analysisId, AgentEvent event) {
        switch (event.getType()) {
            case AGENT_STARTED -> sseService.emit(analysisId, "AI_REVIEWING",
                    "[" + event.getAgentName() + "] " + event.getMessage());
            case AGENT_COMPLETED -> sseService.emit(analysisId, "AI_REVIEWING",
                    "[OK] " + event.getAgentName() + " - " + event.getMessage());
            case AGENT_FAILED -> sseService.emit(analysisId, "AI_REVIEWING",
                    "[ERROR] " + event.getAgentName() + " - " + event.getMessage());
            case PIPELINE_STAGE -> sseService.emit(analysisId, "BUILDING_CONTEXT",
                    event.getAgentName());
        }
    }

    /**
     * Legacy fallback for building AnalysisResult when agent pipeline doesn't produce one.
     */
    private AnalysisResult buildResultLegacy(PrInfo prInfo, AgentContext agentContext) {
        List<RuleResult> ruleResults = agentContext.getRuleResults() != null
                ? agentContext.getRuleResults() : ruleEngine.analyze(prInfo);

        String aiOutput = agentContext.getChunkReviews() != null
                ? String.join("\n", agentContext.getChunkReviews()) : "";

        RiskScore riskScore = agentContext.getRiskScore() != null
                ? agentContext.getRiskScore()
                : scoreCalculator.calculate(prInfo, ruleResults, aiOutput);

        return AnalysisResult.builder()
                .analysisId(agentContext.getAnalysisId())
                .prTitle(prInfo.getTitle())
                .prUrl(prInfo.getHtmlUrl())
                .prNumber(prInfo.getNumber())
                .owner(prInfo.getOwner())
                .repo(prInfo.getRepo())
                .author(prInfo.getAuthor())
                .changedFiles(prInfo.getChangedFiles())
                .additions(prInfo.getAdditions())
                .deletions(prInfo.getDeletions())
                .ruleResults(ruleResults)
                .riskScore(riskScore)
                .aiRawOutput(aiOutput)
                .status(AnalysisStatus.COMPLETED)
                .fileAnalysis(buildFileAnalysis(prInfo, ruleResults))
                .build();
    }

    private List<FileAnalysis> buildFileAnalysis(PrInfo prInfo, List<RuleResult> allRules) {
        List<FileAnalysis> analysis = new ArrayList<>();
        for (PrFile file : prInfo.getFiles()) {
            List<RuleResult> fileRules = allRules.stream()
                    .filter(r -> file.getFilename().equals(r.getFile()))
                    .toList();
            RiskLevel highestRisk = fileRules.stream()
                    .map(RuleResult::getRiskLevel)
                    .max(Comparator.comparingInt(RiskLevel::getLevel))
                    .orElse(RiskLevel.LOW);
            analysis.add(FileAnalysis.builder()
                    .filename(file.getFilename())
                    .language(file.getLanguage())
                    .status(file.getStatus())
                    .additions(file.getAdditions())
                    .deletions(file.getDeletions())
                    .riskLevel(highestRisk)
                    .findings(fileRules)
                    .build());
        }
        return analysis;
    }
}
