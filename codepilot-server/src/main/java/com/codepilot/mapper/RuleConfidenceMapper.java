package com.codepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codepilot.model.entity.RuleConfidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RuleConfidenceMapper extends BaseMapper<RuleConfidence> {

    @Select("SELECT * FROM rule_confidence WHERE rule_name = #{ruleName}")
    RuleConfidence findByRuleName(@Param("ruleName") String ruleName);
}
