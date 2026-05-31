package com.codepilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_confidence")
public class RuleConfidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;
    private Integer totalHits;
    private Integer confirmed;
    private Integer falsePositives;
    private Integer missedIssues;
    private BigDecimal precisionRate;
    private BigDecimal recallEstimate;
    private BigDecimal minConfidenceThreshold;
    private Boolean enabled;
    private LocalDateTime updatedAt;
}
