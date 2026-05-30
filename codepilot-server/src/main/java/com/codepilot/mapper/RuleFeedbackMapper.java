package com.codepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codepilot.model.entity.RuleFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RuleFeedbackMapper extends BaseMapper<RuleFeedback> {

    @Select("SELECT feedback_type, COUNT(*) as cnt FROM rule_feedback WHERE rule_name = #{ruleName} GROUP BY feedback_type")
    List<java.util.Map<String, Object>> countByType(@Param("ruleName") String ruleName);

    @Select("SELECT * FROM rule_feedback WHERE analysis_id = #{analysisId} ORDER BY created_at DESC")
    List<RuleFeedback> findByAnalysisId(@Param("analysisId") String analysisId);
}
