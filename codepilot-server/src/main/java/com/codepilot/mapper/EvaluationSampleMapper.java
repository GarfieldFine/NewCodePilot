package com.codepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codepilot.model.entity.EvaluationSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EvaluationSampleMapper extends BaseMapper<EvaluationSample> {

    @Select("SELECT * FROM evaluation_sample WHERE rule_name = #{ruleName}")
    List<EvaluationSample> findByRule(@Param("ruleName") String ruleName);

    @Select("SELECT DISTINCT rule_name FROM evaluation_sample")
    List<String> findDistinctRules();
}
