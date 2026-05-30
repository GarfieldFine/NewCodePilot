package com.codepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codepilot.model.entity.AnalysisRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AnalysisRecordMapper extends BaseMapper<AnalysisRecord> {

    @Select("SELECT * FROM analysis_record WHERE owner = #{owner} AND repo = #{repo} AND pr_number = #{prNumber} ORDER BY created_at DESC LIMIT 1")
    AnalysisRecord findLatest(@Param("owner") String owner,
                              @Param("repo") String repo,
                              @Param("prNumber") Integer prNumber);

    @Select("SELECT * FROM analysis_record WHERE owner = #{owner} AND repo = #{repo} ORDER BY created_at DESC LIMIT #{limit}")
    List<AnalysisRecord> findRecent(@Param("owner") String owner,
                                    @Param("repo") String repo,
                                    @Param("limit") int limit);
}
