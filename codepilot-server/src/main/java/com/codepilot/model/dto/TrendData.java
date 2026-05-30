package com.codepilot.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class TrendData {
    private List<TrendPoint> riskScoreTrend;
    private List<TrendPoint> findingsTrend;
    private List<TrendPoint> prCountTrend;
    private BigDecimal overallAvgRiskScore;
    private BigDecimal overallAvgFindings;
    private int totalPRsAnalyzed;
    private int trendDays;

    @Data
    @Builder
    public static class TrendPoint {
        private String date;
        private BigDecimal avgRiskScore;
        private int avgFindings;
        private int maxRiskScore;
        private int minRiskScore;
        private int prCount;
    }
}
