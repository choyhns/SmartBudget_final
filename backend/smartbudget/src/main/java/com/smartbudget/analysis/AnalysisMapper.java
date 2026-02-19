package com.smartbudget.analysis;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.smartbudget.analysis.AnalysisResultDTO.BudgetActualRow;
import com.smartbudget.analysis.AnalysisResultDTO.CategoryAmountRow;
import com.smartbudget.analysis.AnalysisResultDTO.CategoryCompareRow;
import com.smartbudget.analysis.AnalysisResultDTO.MerchantRow;
import com.smartbudget.analysis.AnalysisResultDTO.SpendingRow;
import com.smartbudget.analysis.AnalysisResultDTO.WeekOfMonthRow;
import com.smartbudget.analysis.AnalysisResultDTO.WeekdayRow;

/**
 * DB 분석용 고정 SQL 템플릿만 실행. LLM이 SQL을 생성하지 않음.
 */
@Mapper
public interface AnalysisMapper {

    /** 해당 월 총 수입/지출/건수 */
    SpendingRow selectSpendingByYearMonth(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth);

    /** 카테고리별 지출 상위 N (지출만, amount < 0) */
    List<CategoryAmountRow> selectCategoryExpensesTop(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth,
            @Param("limit") int limit);

    /** 카테고리별 이번달 vs 전달 비교 */
    List<CategoryCompareRow> selectCategoryCompareWithPrevious(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth,
            @Param("previousYearMonth") String previousYearMonth);

    /** 요일별 지출 (1=일 ~ 7=토) */
    List<WeekdayRow> selectExpenseByWeekday(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth);

    /** 주차별 지출 (1~5주차) */
    List<WeekOfMonthRow> selectExpenseByWeekOfMonth(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth);

    /** 가맹점별 지출 상위 N */
    List<MerchantRow> selectTopMerchantsByExpense(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth,
            @Param("limit") int limit);

    /** 예산 대비 실제 (카테고리별 예산 설정된 것만) */
    List<BudgetActualRow> selectBudgetVsActual(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth);

    /** 특정 카테고리명(부분 일치) 해당 월 지출 합계 - 원인 분석용 */
    CategoryAmountRow selectCategoryExpenseByName(
            @Param("userId") Long userId,
            @Param("yearMonth") String yearMonth,
            @Param("categoryName") String categoryName);

    /** 전월 총 지출 (비교용) */
    SpendingRow selectSpendingByYearMonthPrevious(
            @Param("userId") Long userId,
            @Param("previousYearMonth") String previousYearMonth);

    // ----- PR1: 9개 분석 템플릿 (날짜 범위: tx_datetime >= startDate AND < endDate) -----

    /** 1) month_summary - 집계 1행 */
    AnalysisTemplateDTO.MonthSummaryRow selectMonthSummaryTotals(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 1) month_summary - 카테고리 Top N */
    List<AnalysisTemplateDTO.CategoryTopRow> selectMonthSummaryCategoryTopN(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit);

    /** 2) month_compare - 기간 A 집계 */
    AnalysisTemplateDTO.PeriodTotalsRow selectPeriodTotals(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 3) category_delta_topN */
    List<AnalysisTemplateDTO.CategoryDeltaRow> selectCategoryDeltaTopN(
            @Param("userId") Long userId,
            @Param("curStart") LocalDate curStart,
            @Param("curEnd") LocalDate curEnd,
            @Param("prevStart") LocalDate prevStart,
            @Param("prevEnd") LocalDate prevEnd,
            @Param("limit") int limit);

    /** 4) target_breakdown - 상위 가맹점 */
    List<AnalysisTemplateDTO.MerchantTopRow> selectTargetBreakdownMerchantTopN(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("categoryId") Long categoryId,
            @Param("limit") int limit);

    /** 4) target_breakdown - 시간대 분포 */
    List<AnalysisTemplateDTO.TimebandRow> selectTargetBreakdownTimeband(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("categoryId") Long categoryId);

    /** 4) target_breakdown - 요일별 분포 */
    List<AnalysisTemplateDTO.DowRow> selectTargetBreakdownDow(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("categoryId") Long categoryId);

    /** 5) aov_vs_count_decompose - 한 기간 1행 (Java에서 두 기간 호출 후 분해) */
    AnalysisTemplateDTO.PeriodAovRow selectPeriodAov(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** 6) timeband_delta */
    List<AnalysisTemplateDTO.TimebandDeltaRow> selectTimebandDelta(
            @Param("userId") Long userId,
            @Param("curStart") LocalDate curStart,
            @Param("curEnd") LocalDate curEnd,
            @Param("prevStart") LocalDate prevStart,
            @Param("prevEnd") LocalDate prevEnd,
            @Param("categoryId") Long categoryId);

    /** 7) dow_delta */
    List<AnalysisTemplateDTO.DowDeltaRow> selectDowDelta(
            @Param("userId") Long userId,
            @Param("curStart") LocalDate curStart,
            @Param("curEnd") LocalDate curEnd,
            @Param("prevStart") LocalDate prevStart,
            @Param("prevEnd") LocalDate prevEnd,
            @Param("categoryId") Long categoryId);

    /** 8) top_merchants */
    List<AnalysisTemplateDTO.MerchantTopRow> selectTopMerchants(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("categoryId") Long categoryId,
            @Param("limit") int limit);

    /** 9) keyword_stats - 키워드 1개당 1행 (키워드 없으면 호출하지 않음) */
    AnalysisTemplateDTO.KeywordStatsRow selectKeywordStats(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword);
}
