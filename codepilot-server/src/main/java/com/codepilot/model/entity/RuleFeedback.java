package com.codepilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rule_feedback")
public class RuleFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String analysisId;
    private String findingId;
    private String ruleName;
    private String category;
    private String fileName;
    private Integer lineNumber;
    private String feedbackType;  // CONFIRMED, FALSE_POSITIVE, MISSED_ISSUE
    private String comment;
    private LocalDateTime createdAt;
}
