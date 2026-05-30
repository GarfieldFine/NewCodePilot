package com.codepilot.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HistoryItem {
    private String analysisId;
    private Integer prNumber;
    private String prTitle;
    private String author;
    private Integer riskScore;
    private Integer totalFindings;
    private String status;
    private LocalDateTime createdAt;
}
