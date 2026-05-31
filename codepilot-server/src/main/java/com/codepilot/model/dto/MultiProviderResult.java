package com.codepilot.model.dto;

import com.codepilot.ai.model.TokenUsage;
import com.codepilot.review.FileAnalysis;
import com.codepilot.rule.RuleResult;
import com.codepilot.scorer.RiskScore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiProviderResult {
    private String analysisId;
    private String prTitle;
    private String prUrl;
    private Integer prNumber;
    private String owner;
    private String repo;
    private String author;
    private Integer changedFiles;
    private Integer additions;
    private Integer deletions;
    private List<RuleResult> ruleResults;
    private List<FileAnalysis> fileAnalysis;
    private Map<String, Object> repositoryProfile;
    private Map<String, ProviderResult> providerResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderResult {
        private String providerName;
        private RiskScore riskScore;
        private String aiRawOutput;
        private TokenUsage tokenUsage;
        private Map<String, Object> confidenceScores;
        private List<String> agentTimeline;
    }
}
