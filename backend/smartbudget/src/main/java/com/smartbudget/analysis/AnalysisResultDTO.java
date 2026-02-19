package com.smartbudget.analysis;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DB 분석 쿼리 결과 래퍼. 템플릿별 Row 타입 정의.
 */
public class AnalysisResultDTO {

    /** 월별 총 수입/지출 1행 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpendingRow {
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private Integer transactionCount;
    }

    /** 카테고리별 금액 1행 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryAmountRow {
        private String categoryName;
        private BigDecimal amount;
    }

    /** 카테고리별 이번달 vs 전달 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryCompareRow {
        private String categoryName;
        private BigDecimal currentAmount;
        private BigDecimal previousAmount;
        private BigDecimal diffAmount;
    }

    /** 요일별 지출 (1=일요일 ~ 7=토요일) */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekdayRow {
        private Integer dayOfWeek;
        private BigDecimal amount;
        private Integer txCount;
    }

    /** 주차별 지출 (1~5) */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekOfMonthRow {
        private Integer weekOfMonth;
        private BigDecimal amount;
        private Integer txCount;
    }

    /** 가맹점별 지출 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantRow {
        private String merchant;
        private BigDecimal amount;
        private Integer txCount;
    }

    /** 예산 대비 실제 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetActualRow {
        private String categoryName;
        private BigDecimal budgetAmount;
        private BigDecimal actualAmount;
        private BigDecimal overAmount;
    }

    /** 분석 결과 집합: 서비스에서 쿼리 실행 후 텍스트로 조합 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceBundle {
        private String yearMonth;
        private String previousYearMonth;
        private SpendingRow spending;
        private List<CategoryAmountRow> categoryTop;
        private List<CategoryCompareRow> categoryCompare;
        private List<WeekdayRow> byWeekday;
        private List<WeekOfMonthRow> byWeekOfMonth;
        private List<MerchantRow> topMerchants;
        private List<BudgetActualRow> budgetActual;
    }
}
