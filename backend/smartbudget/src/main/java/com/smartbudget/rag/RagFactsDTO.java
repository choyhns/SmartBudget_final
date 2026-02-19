package com.smartbudget.rag;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG용 Facts: SQL로 집계한 통계만 담고, LLM은 이 값을 인용만 하도록 함.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagFactsDTO {

    /** 해당 월 총 수입 (amount > 0 합계) */
    private BigDecimal totalIncome;
    /** 해당 월 총 지출 (amount < 0 절대값 합계) */
    private BigDecimal totalExpense;
    /** 순액 (수입 - 지출) */
    private BigDecimal netAmount;
    /** 거래 건수 */
    private int transactionCount;
    /** 카테고리별 지출 (카테고리명, 금액) */
    private List<CategoryAmountRow> categoryExpenses;
    /** 월 총 예산 (budgets.total_budget) */
    private BigDecimal totalBudget;
    /** 카테고리별 예산 (카테고리명, 예산액) */
    private List<CategoryAmountRow> categoryBudgets;
    /** 월별 리포트 AI 요약문 (있을 경우) */
    private String reportSummaryText;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryAmountRow {
        private String categoryName;
        private BigDecimal amount;
    }

    /** SQL 집계 결과 1행 (selectSpendingByYearMonth용) */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectSpendingRow {
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal netAmount;
        private Integer transactionCount;
    }
}
