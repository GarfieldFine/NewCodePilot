package com.codepilot.rule;

import com.codepilot.model.enums.RiskLevel;

import java.util.Map;

public interface Rule {

    String getRuleName();

    String getCategory();

    RiskLevel getDefaultRiskLevel();

    RuleResult check(String filepath, String content, Map<String, Object> context);

    default boolean isEnabled() {
        return true;
    }

    default int getPriority() {
        return 100;
    }

    /**
     * Base confidence level for this rule (0.0-1.0).
     * High FP-risk rules should return lower values.
     * This is the static default; dynamic thresholds from
     * rule_confidence table override it when feedback data exists.
     */
    default double getBaseConfidence() {
        return 0.7;
    }
}
