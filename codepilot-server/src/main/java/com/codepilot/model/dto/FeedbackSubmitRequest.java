package com.codepilot.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackSubmitRequest {

    @NotBlank
    private String analysisId;

    @NotBlank
    private String findingId;

    @NotBlank
    private String ruleName;

    private String category;
    private String fileName;
    private Integer lineNumber;

    /** CONFIRMED, FALSE_POSITIVE, or MISSED_ISSUE */
    @NotBlank
    private String feedbackType;

    private String comment;
}
