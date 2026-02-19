package com.smartbudget.analysis;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PR1: 분석 쿼리 템플릿 9개 결과 DTO. JSON 직렬화로 LLM 전달용.
 */
public class AnalysisTemplateDTO {

    /** 1) month_summary */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MonthSummary {
        private BigDecimal totalExpense;
        private BigDecimal totalIncome;
        private Integer txCount;
        private BigDecimal aov;
        private List<CategoryTopRow> byCategoryTopN;
    }

    /** month_summary 집계 1행 (Mapper 결과용) */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MonthSummaryRow {
        private BigDecimal totalExpense;
        private BigDecimal totalIncome;
        private Integer txCount;
        private BigDecimal aov;
    }

    /** period totals 1행 (Mapper 결과용) */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PeriodTotalsRow {
        private BigDecimal totalExpense;
        private BigDecimal totalIncome;
        private Integer txCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CategoryTopRow {
        private Long categoryId;
        private String categoryName;
        private BigDecimal totalAmount;
    }

    /** 2) month_compare */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MonthCompare {
        private PeriodTotals periodA;
        private PeriodTotals periodB;
        private BigDecimal deltaExpense;
        private BigDecimal deltaIncome;
        private List<CategoryDeltaRow> byCategoryDeltaTopN;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PeriodTotals {
        private BigDecimal totalExpense;
        private BigDecimal totalIncome;
        private Integer txCount;
    }

    /** 3) category_delta_topN (이번달/전달 금액 포함해 LLM이 "현재 0원"으로 오해하지 않도록) */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CategoryDeltaRow {
        private Long categoryId;
        private String categoryName;
        private BigDecimal currentAmount;  // 이번 달 해당 카테고리 지출
        private BigDecimal baselineAmount; // 전달 해당 카테고리 지출
        private BigDecimal deltaAmount;    // 증감(차이)
    }

    /** 4) target_breakdown */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TargetBreakdown {
        private List<MerchantTopRow> merchantTopN;
        private List<TimebandRow> timebandDist;
        private List<DowRow> dowDist;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MerchantTopRow {
        private String merchant;
        private BigDecimal totalAmount;
        private Integer txCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimebandRow {
        private String timeBand;
        private BigDecimal totalAmount;
        private Integer txCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DowRow {
        private Integer dow;
        private BigDecimal totalAmount;
        private Integer txCount;
    }

    /** 5) aov_vs_count_decompose (Java에서 두 기간 호출 후 조합) */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AovVsCountDecompose {
        private PeriodAovRow current;
        private PeriodAovRow baseline;
        private BigDecimal deltaTotal;
        /** 총액 변화 중 건수 변화 기여(근사) */
        private BigDecimal deltaFromCount;
        /** 총액 변화 중 건당금액 변화 기여(근사) */
        private BigDecimal deltaFromAov;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PeriodAovRow {
        private Integer txCount;
        private BigDecimal aov;
        private BigDecimal totalAmount;
    }

    /** 6) timeband_delta */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimebandDeltaRow {
        private String timeBand;
        private BigDecimal currentAmount;
        private BigDecimal baselineAmount;
        private BigDecimal deltaAmount;
    }

    /** 7) dow_delta */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DowDeltaRow {
        private Integer dow;
        private BigDecimal currentAmount;
        private BigDecimal baselineAmount;
        private BigDecimal deltaAmount;
    }

    /** 8) top_merchants - List&lt;MerchantTopRow&gt; 로 재사용 */

    /** 9) keyword_stats */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KeywordStatsRow {
        private String keyword;
        private BigDecimal totalAmount;
        private Integer txCount;
    }
}
