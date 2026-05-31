package com.codepilot.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RuleConfidenceResponse {

    private String ruleName;
    private int totalHits;
    private int confirmed;
    private int falsePositives;
    private int missedIssues;
    private BigDecimal precisionRate;
    private BigDecimal recallEstimate;
    private BigDecimal minConfidenceThreshold;
    private boolean enabled;
}
