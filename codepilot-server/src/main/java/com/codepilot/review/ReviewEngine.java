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
import com.codepilot.model.dto.MultiProviderResult;
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
 * Stage 2 (SEQUENTIAL): RiskDetect
 * Stage 3 (SEQUENTIAL): ReviewGenerate
 * Stage 4 (FINAL):    SummaryMerge
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
        }).subscribeOn(Schedulers.boundedElastic());
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

        Flux<AgentEvent> agentEvents = agentPipeline.executeStreaming(stages, agentContext)
                .subscribeOn(Schedulers.boundedElastic());

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

    private List<PipelineStage> buildSharedStages() {
        return List.of(
                new PipelineStage("Understanding Repository",
                        List.of(repositoryAnalyzeAgent, diffAnalyzeAgent, contextBuildAgent),
                        ExecutionMode.SEQUENTIAL),
                new PipelineStage("Detecting Risks",
                        List.of(riskDetectAgent),
                        ExecutionMode.SEQUENTIAL)
        );
    }

    private List<PipelineStage> buildProviderStages(String providerName) {
        return List.of(
                new PipelineStage("Generating AI Review [" + providerName + "]",
                        List.of(reviewGenerateAgent),
                        ExecutionMode.SEQUENTIAL),
                new PipelineStage("Merging Analysis [" + providerName + "]",
                        List.of(summaryMergeAgent),
                        ExecutionMode.SEQUENTIAL)
        );
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
     * Execute multi-provider streaming analysis.
     * Shared stages (1-2) run once, then provider-specific stages (3-4) run per provider in parallel.
     */
    public Flux<AgentEvent> analyzeStreamMulti(PrInfo prInfo, java.util.List<String> providerNames) {
        String primaryProvider = providerNames.get(0);
        AgentContext sharedContext = AgentContext.from(prInfo, primaryProvider);
        sseService.createEmitter(sharedContext.getAnalysisId());

        log.info("Starting multi-provider stream analysis {} with providers: {}",
                sharedContext.getAnalysisId(), providerNames);

        java.util.List<com.codepilot.agent.AgentPipeline.PipelineStage> sharedStages = buildSharedStages();

        Flux<AgentEvent> sharedEvents = agentPipeline.executeStreaming(sharedStages, sharedContext)
                .subscribeOn(Schedulers.boundedElastic());

        Flux<AgentEvent> providerFlux = Flux.defer(() -> {
            java.util.List<Flux<AgentEvent>> fluxes = new java.util.ArrayList<>();
            java.util.Map<String, com.codepilot.model.dto.MultiProviderResult.ProviderResult> providerResults
                    = new java.util.LinkedHashMap<>();

            for (String providerName : providerNames) {
                AgentContext clonedCtx = sharedContext.cloneForProvider(providerName);
                java.util.List<com.codepilot.agent.AgentPipeline.PipelineStage> providerStages
                        = buildProviderStages(providerName);

                Flux<AgentEvent> taggedFlux = agentPipeline
                        .executeStreaming(providerStages, clonedCtx)
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(event -> {
                            event.getMetadata().put("providerName", providerName);
                            return event;
                        })
                        .doOnComplete(() -> {
                            com.codepilot.review.AnalysisResult providerResult = clonedCtx.getFinalResult();
                            if (providerResult != null) {
                                providerResults.put(providerName,
                                        com.codepilot.model.dto.MultiProviderResult.ProviderResult.builder()
                                                .providerName(providerName)
                                                .riskScore(providerResult.getRiskScore())
                                                .aiRawOutput(providerResult.getAiRawOutput())
                                                .tokenUsage(providerResult.getTokenUsage())
                                                .confidenceScores(providerResult.getConfidenceScores())
                                                .agentTimeline(providerResult.getAgentTimeline())
                                                .build());
                            }
                        });

                fluxes.add(taggedFlux);
            }

            Flux<AgentEvent> mergedProviderFlux = Flux.merge(fluxes);

            com.codepilot.model.dto.MultiProviderResult finalResult = buildMultiResult(prInfo, sharedContext, providerResults);

            return Flux.concat(mergedProviderFlux,
                    Flux.just(AgentEvent.multiProviderComplete(sharedContext.getAnalysisId(), finalResult)));
        });

        return Flux.concat(sharedEvents, providerFlux)
                .onErrorResume(e -> {
                    log.error("Multi-provider pipeline error: {}", e.getMessage());
                    return Flux.just(AgentEvent.error(sharedContext.getAnalysisId(), e.getMessage()));
                });
    }

    private com.codepilot.model.dto.MultiProviderResult buildMultiResult(
            PrInfo prInfo, AgentContext sharedContext,
            java.util.Map<String, com.codepilot.model.dto.MultiProviderResult.ProviderResult> providerResults) {
        return com.codepilot.model.dto.MultiProviderResult.builder()
                .analysisId(sharedContext.getAnalysisId())
                .prTitle(prInfo.getTitle())
                .prUrl(prInfo.getHtmlUrl())
                .prNumber(prInfo.getNumber())
                .owner(prInfo.getOwner())
                .repo(prInfo.getRepo())
                .author(prInfo.getAuthor())
                .changedFiles(prInfo.getChangedFiles())
                .additions(prInfo.getAdditions())
                .deletions(prInfo.getDeletions())
                .ruleResults(sharedContext.getRuleResults())
                .fileAnalysis(buildFileAnalysis(prInfo, sharedContext.getRuleResults()))
                .repositoryProfile(sharedContext.getRepositoryContext())
                .providerResults(providerResults)
                .build();
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
