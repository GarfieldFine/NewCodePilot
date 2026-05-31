package com.codepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codepilot.model.entity.AnalysisRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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

    /** Paginated history for a repo */
    @Select("SELECT * FROM analysis_record WHERE owner = #{owner} AND repo = #{repo} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<AnalysisRecord> findHistory(@Param("owner") String owner,
                                     @Param("repo") String repo,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /** Count total analyses for a repo */
    @Select("SELECT COUNT(*) FROM analysis_record WHERE owner = #{owner} AND repo = #{repo}")
    int countByRepo(@Param("owner") String owner, @Param("repo") String repo);

    /** Trend: risk score and findings grouped by date (last N days) */
    @Select("SELECT DATE(created_at) as date, " +
            "COUNT(*) as pr_count, " +
            "AVG(risk_score) as avg_risk_score, " +
            "AVG(total_findings) as avg_findings, " +
            "MAX(risk_score) as max_risk_score, " +
            "MIN(risk_score) as min_risk_score " +
            "FROM analysis_record " +
            "WHERE owner = #{owner} AND repo = #{repo} " +
            "AND created_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY date ASC")
    List<Map<String, Object>> getDailyTrend(@Param("owner") String owner,
                                            @Param("repo") String repo,
                                            @Param("days") int days);

    /** Recent analyses across all repos */
    @Select("SELECT * FROM analysis_record ORDER BY created_at DESC LIMIT #{limit}")
    List<AnalysisRecord> findRecentGlobal(@Param("limit") int limit);

    /** Get all distinct repos that have analysis records */
    @Select("SELECT CONCAT(owner, '/', repo) as repoKey, owner, repo FROM analysis_record GROUP BY owner, repo ORDER BY MAX(created_at) DESC")
    List<Map<String, Object>> findDistinctRepos();

    /** Find previous analysis for a PR to enable comparison */
    @Select("SELECT * FROM analysis_record WHERE owner = #{owner} AND repo = #{repo} AND pr_number = #{prNumber} ORDER BY created_at DESC")
    List<AnalysisRecord> findAllByPr(@Param("owner") String owner,
                                     @Param("repo") String repo,
                                     @Param("prNumber") Integer prNumber);

    /** Find a single analysis record by analysis_id */
    @Select("SELECT * FROM analysis_record WHERE analysis_id = #{analysisId} LIMIT 1")
    AnalysisRecord findByAnalysisId(@Param("analysisId") String analysisId);

    /** Find records with total_findings = 0 for recalculation migration */
    @Select("SELECT * FROM analysis_record WHERE total_findings = 0 AND analysis_result IS NOT NULL AND analysis_result != ''")
    List<AnalysisRecord> findRecordsWithZeroFindings();
}

