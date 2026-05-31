package com.codepilot.rule;

import com.codepilot.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult {
    private String ruleName;
    private String category;
    private RiskLevel riskLevel;
    private String file;
    private Integer line;
    private String message;
    private String suggestion;
    private String codeSnippet;
    private boolean matched;
    /** Confidence score of this finding (0.0-1.0). Below threshold means needs AI secondary review. */
    @Builder.Default
    private double confidence = 1.0;
    /** True if this finding passed the confidence threshold and is considered reliable. */
    @Builder.Default
    private boolean confidencePassed = true;
}
