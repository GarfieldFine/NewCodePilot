package com.codepilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_record")
public class AnalysisRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String analysisId;
    private String owner;
    private String repo;
    private Integer prNumber;
    private String prTitle;
    private String prUrl;
    private String author;
    private String status;
    private Integer totalFindings;
    private Integer riskScore;
    private String tokenUsage;
    private String analysisResult;
    private LocalDateTime createdAt;
}
