package com.codepilot.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codepilot.mapper.AnalysisRecordMapper;
import com.codepilot.model.dto.ComparisonResult;
import com.codepilot.model.dto.HistoryItem;
import com.codepilot.model.dto.TrendData;
import com.codepilot.model.entity.AnalysisRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HistoryService {

    private final AnalysisRecordMapper analysisRecordMapper;

    public HistoryService(AnalysisRecordMapper analysisRecordMapper) {
        this.analysisRecordMapper = analysisRecordMapper;
    }

    /** Paginated history for a repo */
    public Map<String, Object> getHistory(String owner, String repo, int page, int size) {
        int offset = (page - 1) * size;
        List<AnalysisRecord> records = analysisRecordMapper.findHistory(owner, repo, offset, size);
        int total = analysisRecordMapper.countByRepo(owner, repo);

        List<HistoryItem> items = records.stream()
                .map(this::toHistoryItem)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (int) Math.ceil((double) total / size));
        return result;
    }

    /** Get trend data for charts */
    public TrendData getTrends(String owner, String repo, int days) {
        List<Map<String, Object>> dailyData = analysisRecordMapper.getDailyTrend(owner, repo, days);

        List<TrendData.TrendPoint> points = dailyData.stream()
                .map(row -> TrendData.TrendPoint.builder()
                        .date(String.valueOf(row.get("date")))
                        .avgRiskScore(toBigDecimal(row.get("avg_risk_score")))
                        .avgFindings(toInt(row.get("avg_findings")))
                        .maxRiskScore(toInt(row.get("max_risk_score")))
                        .minRiskScore(toInt(row.get("min_risk_score")))
                        .prCount(toInt(row.get("pr_count")))
                        .build())
                .collect(Collectors.toList());

        // Calculate overall averages
        BigDecimal avgRisk = BigDecimal.ZERO;
        BigDecimal avgFindings = BigDecimal.ZERO;
        int totalPRs = 0;
        for (TrendData.TrendPoint p : points) {
            totalPRs += p.getPrCount();
        }

        // Weighted average by PR count
        if (totalPRs > 0) {
            double weightedRisk = 0;
            double weightedFindings = 0;
            for (TrendData.TrendPoint p : points) {
                double weight = (double) p.getPrCount() / totalPRs;
                weightedRisk += p.getAvgRiskScore().doubleValue() * weight;
                weightedFindings += p.getAvgFindings() * weight;
            }
            avgRisk = BigDecimal.valueOf(weightedRisk).setScale(1, RoundingMode.HALF_UP);
            avgFindings = BigDecimal.valueOf(weightedFindings).setScale(1, RoundingMode.HALF_UP);
        }

        return TrendData.builder()
                .riskScoreTrend(points)
                .findingsTrend(points)
                .prCountTrend(points)
                .overallAvgRiskScore(avgRisk)
                .overallAvgFindings(avgFindings)
                .totalPRsAnalyzed(totalPRs)
                .trendDays(days)
                .build();
    }

    /** Compare two analyses of the same PR */
    public ComparisonResult comparePr(String owner, String repo, int prNumber) {
        List<AnalysisRecord> records = analysisRecordMapper.findAllByPr(owner, repo, prNumber);

        if (records.size() < 2) {
            return ComparisonResult.builder()
                    .current(records.isEmpty() ? null : toHistoryItem(records.get(0)))
                    .previous(null)
                    .riskScoreDelta(0)
                    .findingsDelta(0)
                    .resolvedFindings(List.of())
                    .newFindings(List.of())
                    .build();
        }

        // Most recent = current, second most recent = previous
        AnalysisRecord current = records.get(0);
        AnalysisRecord previous = records.get(1);

        int riskDelta = current.getRiskScore() - previous.getRiskScore();
        int findingsDelta = current.getTotalFindings() - previous.getTotalFindings();

        return ComparisonResult.builder()
                .current(toHistoryItem(current))
                .previous(toHistoryItem(previous))
                .riskScoreDelta(riskDelta)
                .findingsDelta(findingsDelta)
                .resolvedFindings(List.of())
                .newFindings(List.of())
                .build();
    }

    /** Recent analyses across all repos */
    public List<HistoryItem> getRecentGlobal(int limit) {
        return analysisRecordMapper.findRecentGlobal(limit).stream()
                .map(this::toHistoryItem)
                .collect(Collectors.toList());
    }

    /** List tracked repos */
    public List<Map<String, Object>> getRepos() {
        return analysisRecordMapper.findDistinctRepos();
    }

    /** Get full analysis result by analysisId (for exporting reports) */
    public Map<String, Object> getAnalysisResult(String analysisId) {
        AnalysisRecord record = analysisRecordMapper.findByAnalysisId(analysisId);
        if (record == null) {
            return null;
        }
        String json = record.getAnalysisResult();
        if (json == null || json.isEmpty()) {
            return null;
        }
        JSONObject obj = JSONUtil.parseObj(json);
        return obj.toBean(Map.class);
    }

    private HistoryItem toHistoryItem(AnalysisRecord record) {
        return HistoryItem.builder()
                .analysisId(record.getAnalysisId())
                .prNumber(record.getPrNumber())
                .prTitle(record.getPrTitle())
                .author(record.getAuthor())
                .riskScore(record.getRiskScore())
                .totalFindings(record.getTotalFindings())
                .status(record.getStatus())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        try {
            return BigDecimal.valueOf(Double.parseDouble(val.toString()));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return (int) Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
