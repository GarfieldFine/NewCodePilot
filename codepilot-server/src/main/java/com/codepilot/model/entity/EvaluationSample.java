package com.codepilot.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evaluation_sample")
public class EvaluationSample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;
    private String language;
    private String title;
    private String codeSnippet;
    private Boolean shouldMatch;
    private String expectedSeverity;
    private String notes;
    private LocalDateTime createdAt;
}
