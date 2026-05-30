package com.codepilot.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codepilot.mapper.AnalysisRecordMapper;
import com.codepilot.mapper.EvaluationSampleMapper;
import com.codepilot.mapper.RuleConfidenceMapper;
import com.codepilot.mapper.RuleFeedbackMapper;
import com.codepilot.model.dto.FeedbackSubmitRequest;
import com.codepilot.model.dto.RuleConfidenceResponse;
import com.codepilot.model.entity.AnalysisRecord;
import com.codepilot.model.entity.EvaluationSample;
import com.codepilot.model.entity.RuleConfidence;
import com.codepilot.model.entity.RuleFeedback;
import com.codepilot.review.AnalysisResult;
import com.codepilot.rule.RuleEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RuleFeedbackService {

    private final RuleFeedbackMapper feedbackMapper;
    private final RuleConfidenceMapper confidenceMapper;
    private final EvaluationSampleMapper evaluationMapper;
    private final AnalysisRecordMapper analysisRecordMapper;
    private final RuleEngine ruleEngine;

    public RuleFeedbackService(RuleFeedbackMapper feedbackMapper,
                               RuleConfidenceMapper confidenceMapper,
                               EvaluationSampleMapper evaluationMapper,
                               AnalysisRecordMapper analysisRecordMapper,
                               RuleEngine ruleEngine) {
        this.feedbackMapper = feedbackMapper;
        this.confidenceMapper = confidenceMapper;
        this.evaluationMapper = evaluationMapper;
        this.analysisRecordMapper = analysisRecordMapper;
        this.ruleEngine = ruleEngine;
    }

    @Transactional
    public void submitFeedback(FeedbackSubmitRequest request) {
        RuleFeedback feedback = new RuleFeedback();
        feedback.setAnalysisId(request.getAnalysisId());
        feedback.setFindingId(request.getFindingId());
        feedback.setRuleName(request.getRuleName());
        feedback.setCategory(request.getCategory());
        feedback.setFileName(request.getFileName());
        feedback.setLineNumber(request.getLineNumber());
        feedback.setFeedbackType(request.getFeedbackType());
        feedback.setComment(request.getComment());
        feedbackMapper.insert(feedback);

        recalculateRuleConfidence(request.getRuleName());
        ruleEngine.refreshConfidenceCache();
        log.info("Feedback recorded: rule={}, type={}, finding={}",
                request.getRuleName(), request.getFeedbackType(), request.getFindingId());
    }

    public List<RuleConfidenceResponse> getAllRuleStats() {
        List<RuleConfidence> all = confidenceMapper.selectList(null);
        return all.stream()
                .map(this::toResponse)
                .sorted(Comparator.comparing(RuleConfidenceResponse::getRuleName))
                .collect(Collectors.toList());
    }

    public RuleConfidenceResponse getRuleStats(String ruleName) {
        RuleConfidence rc = confidenceMapper.findByRuleName(ruleName);
        if (rc == null) {
            return RuleConfidenceResponse.builder()
                    .ruleName(ruleName)
                    .totalHits(0)
                    .minConfidenceThreshold(BigDecimal.valueOf(0.5))
                    .enabled(true)
                    .build();
        }
        return toResponse(rc);
    }

    public Map<String, Object> runEvaluation(String ruleName) {
        List<EvaluationSample> samples;
        if (ruleName != null && !ruleName.isEmpty()) {
            samples = evaluationMapper.findByRule(ruleName);
        } else {
            samples = evaluationMapper.selectList(null);
        }

        int truePositives = 0;
        int falsePositivesEval = 0;
        int trueNegatives = 0;
        int falseNegatives = 0;
        int total = samples.size();

        for (EvaluationSample sample : samples) {
            // Use should_match as ground truth
            // For now, this is a framework — actual rule evaluation needs to
            // run the rule engine against the code snippet.
            // Here we record the structure for future implementation.
            if (sample.getShouldMatch() != null && sample.getShouldMatch()) {
                truePositives++;
            } else if (sample.getShouldMatch() != null) {
                falsePositivesEval++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleName", ruleName != null ? ruleName : "all");
        result.put("totalSamples", total);
        result.put("truePositives", truePositives);
        result.put("falsePositives", falsePositivesEval);
        result.put("precision", total > 0 ?
                BigDecimal.valueOf(truePositives)
                        .divide(BigDecimal.valueOf(truePositives + falsePositivesEval), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        return result;
    }

    public List<EvaluationSample> getEvaluationSamples(String ruleName) {
        if (ruleName != null && !ruleName.isEmpty()) {
            return evaluationMapper.findByRule(ruleName);
        }
        return evaluationMapper.selectList(null);
    }

    public List<String> getEvalDistinctRules() {
        return evaluationMapper.findDistinctRules();
    }

    /**
     * Persist analysis result to DB for history tracking.
     */
    public void saveAnalysisRecord(AnalysisResult result) {
        AnalysisRecord record = new AnalysisRecord();
        record.setAnalysisId(result.getAnalysisId());
        record.setOwner(result.getOwner());
        record.setRepo(result.getRepo());
        record.setPrNumber(result.getPrNumber());
        record.setPrTitle(result.getPrTitle());
        record.setPrUrl(result.getPrUrl());
        record.setAuthor(result.getAuthor());
        record.setStatus(result.getStatus() != null ? result.getStatus().name() : "COMPLETED");
        record.setTotalFindings(result.getRuleResults() != null ? result.getRuleResults().size() : 0);
        record.setRiskScore(result.getRiskScore() != null ? result.getRiskScore().getTotalScore() : 0);
        record.setAnalysisResult(JSONUtil.toJsonStr(result));
        analysisRecordMapper.insert(record);
        log.info("Analysis record saved: {}", result.getAnalysisId());
    }

    private void recalculateRuleConfidence(String ruleName) {
        RuleConfidence rc = confidenceMapper.findByRuleName(ruleName);
        if (rc == null) {
            rc = new RuleConfidence();
            rc.setRuleName(ruleName);
            rc.setTotalHits(0);
            rc.setConfirmed(0);
            rc.setFalsePositives(0);
            rc.setMissedIssues(0);
            rc.setMinConfidenceThreshold(BigDecimal.valueOf(0.5));
            rc.setEnabled(true);
        }

        // Count feedback by type for this rule
        List<Map<String, Object>> counts = feedbackMapper.countByType(ruleName);
        int confirmed = 0, fp = 0, missed = 0;
        for (Map<String, Object> row : counts) {
            String type = String.valueOf(row.get("feedback_type"));
            int cnt = ((Number) row.get("cnt")).intValue();
            switch (type) {
                case "CONFIRMED" -> confirmed = cnt;
                case "FALSE_POSITIVE" -> fp = cnt;
                case "MISSED_ISSUE" -> missed = cnt;
            }
        }

        int totalFeedback = confirmed + fp + missed;
        rc.setTotalHits(totalFeedback);
        rc.setConfirmed(confirmed);
        rc.setFalsePositives(fp);
        rc.setMissedIssues(missed);

        // Precision = confirmed / (confirmed + false_positives)
        if (confirmed + fp > 0) {
            rc.setPrecisionRate(BigDecimal.valueOf(confirmed)
                    .divide(BigDecimal.valueOf(confirmed + fp), 4, RoundingMode.HALF_UP));
        }

        // Dynamically adjust threshold based on precision:
        // High precision → lower threshold (fewer FP, can be more aggressive)
        // Low precision → higher threshold (many FP, need to be more conservative)
        if (totalFeedback >= 5 && rc.getPrecisionRate() != null) {
            double precision = rc.getPrecisionRate().doubleValue();
            double threshold = 0.8 - (precision * 0.6); // precision=1.0→0.2, precision=0.5→0.5, precision=0.0→0.8
            threshold = Math.max(0.2, Math.min(0.8, threshold));
            rc.setMinConfidenceThreshold(BigDecimal.valueOf(threshold));
        }

        if (rc.getId() == null) {
            confidenceMapper.insert(rc);
        } else {
            confidenceMapper.updateById(rc);
        }
        log.info("Rule confidence updated: {} precision={} threshold={}",
                ruleName, rc.getPrecisionRate(), rc.getMinConfidenceThreshold());
    }

    private RuleConfidenceResponse toResponse(RuleConfidence rc) {
        return RuleConfidenceResponse.builder()
                .ruleName(rc.getRuleName())
                .totalHits(rc.getTotalHits() != null ? rc.getTotalHits() : 0)
                .confirmed(rc.getConfirmed() != null ? rc.getConfirmed() : 0)
                .falsePositives(rc.getFalsePositives() != null ? rc.getFalsePositives() : 0)
                .missedIssues(rc.getMissedIssues() != null ? rc.getMissedIssues() : 0)
                .precisionRate(rc.getPrecisionRate() != null ? rc.getPrecisionRate() : BigDecimal.ZERO)
                .recallEstimate(rc.getRecallEstimate() != null ? rc.getRecallEstimate() : BigDecimal.ZERO)
                .minConfidenceThreshold(rc.getMinConfidenceThreshold() != null
                        ? rc.getMinConfidenceThreshold() : BigDecimal.valueOf(0.5))
                .enabled(rc.getEnabled() != null && rc.getEnabled())
                .build();
    }
}
