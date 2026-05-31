package com.codepilot.rule;

import com.codepilot.github.model.PrFile;
import com.codepilot.github.model.PrInfo;
import com.codepilot.mapper.RuleConfidenceMapper;
import com.codepilot.model.entity.RuleConfidence;
import com.codepilot.model.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RuleEngine {

    private final List<Rule> rules;
    private final RuleConfidenceMapper confidenceMapper;
    private final Map<String, RuleConfidence> confidenceCache = new ConcurrentHashMap<>();

    public RuleEngine(List<Rule> rules, RuleConfidenceMapper confidenceMapper) {
        this.rules = rules.stream()
                .filter(Rule::isEnabled)
                .sorted(Comparator.comparingInt(Rule::getPriority))
                .collect(Collectors.toList());
        this.confidenceMapper = confidenceMapper;
        log.info("Loaded {} risk rules: {}", this.rules.size(),
                this.rules.stream().map(Rule::getRuleName).collect(Collectors.toList()));
    }

    public List<RuleResult> analyze(PrInfo prInfo) {
        List<RuleResult> allResults = new ArrayList<>();
        Map<String, Object> context = buildContext(prInfo);

        for (PrFile file : prInfo.getFiles()) {
            if (file.getPatch() == null) continue;
            String content = file.getPatch();

            for (Rule rule : rules) {
                try {
                    RuleResult result = rule.check(file.getFilename(), content, context);
                    if (result.isMatched()) {
                        result.setFile(file.getFilename());
                        applyConfidenceThreshold(result);
                        allResults.add(result);
                    }
                } catch (Exception e) {
                    log.warn("Rule {} failed on file {}: {}",
                            rule.getRuleName(), file.getFilename(), e.getMessage());
                }
            }
        }

        // Cross-file pattern check on full diff
        String fullDiff = prInfo.getDiffContent();
        if (fullDiff != null) {
            for (Rule rule : rules) {
                try {
                    RuleResult result = rule.check("__full_diff__", fullDiff, context);
                    if (result.isMatched()) {
                        applyConfidenceThreshold(result);
                        allResults.add(result);
                    }
                } catch (Exception e) {
                    log.warn("Rule {} failed on full diff: {}", rule.getRuleName(), e.getMessage());
                }
            }
        }

        int passed = (int) allResults.stream().filter(RuleResult::isConfidencePassed).count();
        int flagged = allResults.size() - passed;
        log.info("Rule engine completed: {} findings ({} passed, {} flagged for review) across {} files",
                allResults.size(), passed, flagged, prInfo.getFiles().size());
        return allResults;
    }

    /**
     * Apply confidence threshold to a rule finding.
     * Uses dynamic threshold from rule_confidence table if available,
     * falling back to the rule's base confidence.
     */
    private void applyConfidenceThreshold(RuleResult result) {
        Rule rule = rules.stream()
                .filter(r -> r.getRuleName().equals(result.getRuleName()))
                .findFirst()
                .orElse(null);
        if (rule == null) return;

        double baseConfidence = rule.getBaseConfidence();
        result.setConfidence(baseConfidence);

        // Look up dynamic threshold from feedback data
        RuleConfidence rc = getConfidenceRecord(rule.getRuleName());
        if (rc != null && rc.getMinConfidenceThreshold() != null) {
            double threshold = rc.getMinConfidenceThreshold().doubleValue();
            if (baseConfidence < threshold) {
                result.setConfidencePassed(false);
                result.setMessage(result.getMessage() + " [AI 二次确认中 — 规则置信度 " +
                        String.format("%.0f%%", baseConfidence * 100) + " < 阈值 " +
                        String.format("%.0f%%", threshold * 100) + "]");
                // Downgrade severity for low-confidence findings
                if (result.getRiskLevel() == RiskLevel.CRITICAL) {
                    result.setRiskLevel(RiskLevel.HIGH);
                }
            }
        }
    }

    private RuleConfidence getConfidenceRecord(String ruleName) {
        return confidenceCache.computeIfAbsent(ruleName, name -> {
            try {
                return confidenceMapper.findByRuleName(name);
            } catch (Exception e) {
                log.debug("Rule confidence not found in DB for {}: {}", name, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Refresh cached confidence thresholds from DB (call after feedback updates).
     */
    public void refreshConfidenceCache() {
        confidenceCache.clear();
        log.info("Rule confidence cache refreshed");
    }

    public Map<RiskLevel, List<RuleResult>> groupByRiskLevel(List<RuleResult> results) {
        return results.stream()
                .collect(Collectors.groupingBy(RuleResult::getRiskLevel));
    }

    public RiskLevel getHighestRiskLevel(List<RuleResult> results) {
        return results.stream()
                .map(RuleResult::getRiskLevel)
                .max(Comparator.comparingInt(RiskLevel::getLevel))
                .orElse(RiskLevel.LOW);
    }

    private Map<String, Object> buildContext(PrInfo prInfo) {
        Map<String, Object> context = new HashMap<>();
        context.put("prTitle", prInfo.getTitle());
        context.put("prDescription", prInfo.getDescription());
        context.put("changedFiles", prInfo.getChangedFiles());
        context.put("additions", prInfo.getAdditions());
        context.put("deletions", prInfo.getDeletions());
        context.put("author", prInfo.getAuthor());
        context.put("baseBranch", prInfo.getBaseBranch());

        List<String> fileNames = prInfo.getFiles().stream()
                .map(PrFile::getFilename)
                .collect(Collectors.toList());
        context.put("fileNames", fileNames);
        return context;
    }
}
