package com.codepilot.controller;

import com.codepilot.model.dto.ApiResponse;
import com.codepilot.model.dto.FeedbackSubmitRequest;
import com.codepilot.model.dto.RuleConfidenceResponse;
import com.codepilot.model.entity.EvaluationSample;
import com.codepilot.service.RuleFeedbackService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class RuleFeedbackController {

    private final RuleFeedbackService feedbackService;

    public RuleFeedbackController(RuleFeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** Submit user feedback on a specific finding */
    @PostMapping("/feedback")
    public ApiResponse<String> submitFeedback(@Valid @RequestBody FeedbackSubmitRequest request) {
        feedbackService.submitFeedback(request);
        return ApiResponse.success("Feedback recorded");
    }

    /** Get accuracy statistics for all rules */
    @GetMapping("/feedback/stats")
    public ApiResponse<List<RuleConfidenceResponse>> getAllStats() {
        return ApiResponse.success(feedbackService.getAllRuleStats());
    }

    /** Get accuracy statistics for a specific rule */
    @GetMapping("/feedback/stats/{ruleName}")
    public ApiResponse<RuleConfidenceResponse> getRuleStats(@PathVariable String ruleName) {
        return ApiResponse.success(feedbackService.getRuleStats(ruleName));
    }

    /** Get evaluation dataset samples */
    @GetMapping("/evaluation/samples")
    public ApiResponse<List<EvaluationSample>> getSamples(
            @RequestParam(required = false) String ruleName) {
        return ApiResponse.success(feedbackService.getEvaluationSamples(ruleName));
    }

    /** Get distinct rule names in evaluation dataset */
    @GetMapping("/evaluation/rules")
    public ApiResponse<List<String>> getEvalRules() {
        return ApiResponse.success(feedbackService.getEvalDistinctRules());
    }

    /** Run evaluation for a specific rule or all rules */
    @GetMapping("/evaluation/run")
    public ApiResponse<Map<String, Object>> runEvaluation(
            @RequestParam(required = false) String ruleName) {
        return ApiResponse.success(feedbackService.runEvaluation(ruleName));
    }
}
