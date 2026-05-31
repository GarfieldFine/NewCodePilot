package com.codepilot.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class PrAnalyzeRequest {

    @NotBlank(message = "PR URL is required")
    @Pattern(regexp = "^https?://github\\.com/[^/]+/[^/]+/pull/\\d+.*$",
            message = "Invalid GitHub PR URL format")
    private String prUrl;

    private String provider;

    private List<String> providers;
}
