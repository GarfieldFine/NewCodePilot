package com.codepilot.controller;

import com.codepilot.model.dto.ApiResponse;
import com.codepilot.model.dto.ComparisonResult;
import com.codepilot.model.dto.HistoryItem;
import com.codepilot.model.dto.TrendData;
import com.codepilot.service.HistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    /** Paginated analysis history for a repo */
    @GetMapping("/history/{owner}/{repo}")
    public ApiResponse<Map<String, Object>> getHistory(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(historyService.getHistory(owner, repo, page, size));
    }

    /** Code quality trend data for charts */
    @GetMapping("/trends/{owner}/{repo}")
    public ApiResponse<TrendData> getTrends(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(historyService.getTrends(owner, repo, days));
    }

    /** Compare two most recent analyses of a PR */
    @GetMapping("/compare/{owner}/{repo}/{prNumber}")
    public ApiResponse<ComparisonResult> comparePR(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable int prNumber) {
        return ApiResponse.success(historyService.comparePr(owner, repo, prNumber));
    }

    /** Recent analyses across all repos (global view) */
    @GetMapping("/history/recent")
    public ApiResponse<List<HistoryItem>> getRecent(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(historyService.getRecentGlobal(limit));
    }

    /** List all tracked repos */
    @GetMapping("/history/repos")
    public ApiResponse<List<Map<String, Object>>> getRepos() {
        return ApiResponse.success(historyService.getRepos());
    }

    /** Get full analysis result by analysisId (for report export) */
    @GetMapping("/analysis/{analysisId}")
    public ApiResponse<Map<String, Object>> getAnalysisResult(
            @PathVariable String analysisId) {
        Map<String, Object> result = historyService.getAnalysisResult(analysisId);
        if (result == null) {
            return ApiResponse.error(404, "Analysis not found");
        }
        return ApiResponse.success(result);
    }
}
