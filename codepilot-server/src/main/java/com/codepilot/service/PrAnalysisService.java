package com.codepilot.service;

import com.codepilot.agent.AgentEvent;
import com.codepilot.cache.AnalysisCacheService;
import com.codepilot.github.GitHubClient;
import com.codepilot.github.model.PrInfo;
import com.codepilot.review.AnalysisResult;
import com.codepilot.review.ReviewEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class PrAnalysisService {

    private final GitHubClient gitHubClient;
    private final ReviewEngine reviewEngine;
    private final AnalysisCacheService cacheService;
    private final RuleFeedbackService feedbackService;

    public PrAnalysisService(GitHubClient gitHubClient,
                             ReviewEngine reviewEngine,
                             AnalysisCacheService cacheService,
                             RuleFeedbackService feedbackService) {
        this.gitHubClient = gitHubClient;
        this.reviewEngine = reviewEngine;
        this.cacheService = cacheService;
        this.feedbackService = feedbackService;
    }

    public Mono<AnalysisResult> analyzePr(String prUrl, String providerName) {
        return gitHubClient.fetchPrDetail(prUrl)
                .flatMap(prInfo -> {
                    GitHubClient.PrUrlInfo urlInfo = GitHubClient.parsePrUrl(prUrl);
                    return cacheService.getCached(urlInfo.owner(), urlInfo.repo(), urlInfo.prNumber())
                            .flatMap(cached -> {
                                if (cached != null) {
                                    log.info("Returning cached analysis for {}", prUrl);
                                    return Mono.just(cached);
                                }
                                return doAnalyze(prInfo, providerName, urlInfo);
                            })
                            .switchIfEmpty(doAnalyze(prInfo, providerName, urlInfo));
                });
    }

    public Flux<AgentEvent> analyzePrStream(String prUrl, String providerName) {
        return gitHubClient.fetchPrDetail(prUrl)
                .flatMapMany(prInfo -> reviewEngine.analyzeStream(prInfo, providerName))
                .onErrorResume(e -> {
                    log.error("Stream analysis error: {}", e.getMessage());
                    return Flux.just(AgentEvent.error("unknown", e.getMessage()));
                });
    }

    private Mono<AnalysisResult> doAnalyze(PrInfo prInfo, String providerName,
                                            GitHubClient.PrUrlInfo urlInfo) {
        return reviewEngine.analyze(prInfo, providerName)
                .flatMap(result -> {
                    try {
                        feedbackService.saveAnalysisRecord(result);
                    } catch (Exception e) {
                        log.warn("Failed to save analysis record: {}", e.getMessage());
                    }
                    return cacheService.cacheResult(
                                    urlInfo.owner(), urlInfo.repo(), urlInfo.prNumber(), result)
                            .thenReturn(result);
                });
    }

    public Mono<PrInfo> getPrDetail(String prUrl) {
        return gitHubClient.fetchPrDetail(prUrl);
    }

    public Mono<AnalysisResult> getCachedResult(String owner, String repo, int prNumber) {
        return cacheService.getCached(owner, repo, prNumber);
    }
}
