package com.codepilot.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ComparisonResult {
    private HistoryItem current;
    private HistoryItem previous;
    private int riskScoreDelta;
    private int findingsDelta;
    private List<ComparisonFinding> resolvedFindings;
    private List<ComparisonFinding> newFindings;

    @Data
    @Builder
    public static class ComparisonFinding {
        private String ruleName;
        private String category;
        private String file;
        private String message;
        private boolean isNew;
    }
}
