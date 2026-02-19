package com.smartbudget.analysis;

import java.time.LocalDate;
import java.util.List;

/**
 * PR1: 미리 정의된 분석 쿼리 템플릿 9개만 실행. LLM이 SQL을 생성하지 않음.
 * 입력 파라미터 통일: userId, yearMonth(YYYYMM), baselineYearMonth(optional), categoryId(optional), limit(optional).
 * yearMonth는 월 범위(startDate ~ endDate)로 변환해 WHERE tx_datetime >= start AND < end 적용.
 */
public interface AnalysisRepository {

    /** 1) month_summary */
    AnalysisTemplateDTO.MonthSummary monthSummary(AnalysisQueryParams params);

    /** 2) month_compare */
    AnalysisTemplateDTO.MonthCompare monthCompare(AnalysisQueryParams params);

    /** 3) category_delta_topN */
    List<AnalysisTemplateDTO.CategoryDeltaRow> categoryDeltaTopN(AnalysisQueryParams params);

    /** 4) target_breakdown */
    AnalysisTemplateDTO.TargetBreakdown targetBreakdown(AnalysisQueryParams params);

    /** 5) aov_vs_count_decompose */
    AnalysisTemplateDTO.AovVsCountDecompose aovVsCountDecompose(AnalysisQueryParams params);

    /** 6) timeband_delta */
    List<AnalysisTemplateDTO.TimebandDeltaRow> timebandDelta(AnalysisQueryParams params);

    /** 7) dow_delta */
    List<AnalysisTemplateDTO.DowDeltaRow> dowDelta(AnalysisQueryParams params);

    /** 8) top_merchants */
    List<AnalysisTemplateDTO.MerchantTopRow> topMerchants(AnalysisQueryParams params);

    /** 9) keyword_stats (키워드 없으면 빈 리스트) */
    List<AnalysisTemplateDTO.KeywordStatsRow> keywordStats(AnalysisQueryParams params);
}
