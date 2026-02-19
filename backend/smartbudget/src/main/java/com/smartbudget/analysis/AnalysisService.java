package com.smartbudget.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.smartbudget.analysis.AnalysisResultDTO.BudgetActualRow;
import com.smartbudget.analysis.AnalysisResultDTO.CategoryAmountRow;
import com.smartbudget.analysis.AnalysisResultDTO.CategoryCompareRow;
import com.smartbudget.analysis.AnalysisResultDTO.MerchantRow;
import com.smartbudget.analysis.AnalysisResultDTO.SpendingRow;
import com.smartbudget.analysis.AnalysisResultDTO.WeekOfMonthRow;
import com.smartbudget.analysis.AnalysisResultDTO.WeekdayRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DB 분석 쿼리만 실행하고 근거 텍스트 조합. LLM은 SQL 생성하지 않음.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int TOP_N = 5;
    private static final String[] WEEKDAY_NAMES = { "", "일", "월", "화", "수", "목", "금", "토" };

    private final AnalysisMapper analysisMapper;

    /**
     * Intent에 따라 필요한 분석 쿼리 실행 후 근거 텍스트 반환.
     * 최소 2개 이상 수치/랭킹이 포함되도록 구성.
     */
    public String buildEvidenceText(Long userId, IntentClassificationDTO intentDto) {
        String yearMonth = intentDto.getYearMonth();
        if (yearMonth == null || yearMonth.isBlank()) {
            return "";
        }
        yearMonth = yearMonth.replace("-", "").trim();
        String previousYearMonth = previousYearMonth(yearMonth);

        StringBuilder evidence = new StringBuilder();
        evidence.append("【분석 기준월 ").append(yearMonth).append("】\n");

        String intent = intentDto.getIntent() != null ? intentDto.getIntent() : IntentClassificationDTO.INTENT_GENERAL;

        switch (intent) {
            case IntentClassificationDTO.INTENT_CAUSE:
                appendCauseEvidence(evidence, userId, yearMonth, previousYearMonth, intentDto.getCategoryHint());
                break;
            case IntentClassificationDTO.INTENT_COMPARISON:
                appendComparisonEvidence(evidence, userId, yearMonth, previousYearMonth);
                break;
            case IntentClassificationDTO.INTENT_BUDGET:
                appendBudgetEvidence(evidence, userId, yearMonth);
                break;
            case IntentClassificationDTO.INTENT_PATTERN:
                appendPatternEvidence(evidence, userId, yearMonth, intentDto.getCategoryHint());
                break;
            default:
                appendGeneralEvidence(evidence, userId, yearMonth, previousYearMonth);
                break;
        }

        return evidence.toString();
    }

    private void appendCauseEvidence(StringBuilder evidence, Long userId, String yearMonth,
                                     String previousYearMonth, String categoryHint) {
        SpendingRow curr = analysisMapper.selectSpendingByYearMonth(userId, yearMonth);
        if (curr != null) {
            evidence.append("총 지출: ").append(curr.getTotalExpense() != null ? curr.getTotalExpense().toPlainString() : "0").append("원, 거래 ").append(curr.getTransactionCount() != null ? curr.getTransactionCount() : 0).append("건.\n");
        }
        List<CategoryCompareRow> compare = analysisMapper.selectCategoryCompareWithPrevious(userId, yearMonth, previousYearMonth);
        if (compare != null && !compare.isEmpty()) {
            evidence.append("카테고리별 전월 대비: ");
            List<String> lines = compare.stream()
                .limit(7)
                .map(r -> r.getCategoryName() + " " + (r.getCurrentAmount() != null ? r.getCurrentAmount().toPlainString() : "0") + "원(전월 " + (r.getPreviousAmount() != null ? r.getPreviousAmount().toPlainString() : "0") + "원, " + (r.getDiffAmount() != null && r.getDiffAmount().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + (r.getDiffAmount() != null ? r.getDiffAmount().toPlainString() : "0") + "원)")
                .collect(Collectors.toList());
            evidence.append(String.join("; ", lines)).append(".\n");
        }
        if (categoryHint != null && !categoryHint.isBlank()) {
            CategoryAmountRow cat = analysisMapper.selectCategoryExpenseByName(userId, yearMonth, categoryHint);
            if (cat != null && cat.getAmount() != null) {
                evidence.append("[").append(categoryHint).append("] 해당월 지출: ").append(cat.getAmount().toPlainString()).append("원.\n");
            }
        }
        List<CategoryAmountRow> top = analysisMapper.selectCategoryExpensesTop(userId, yearMonth, TOP_N);
        if (top != null && !top.isEmpty()) {
            evidence.append("지출 TOP 카테고리: ");
            evidence.append(top.stream().limit(5).map(r -> r.getCategoryName() + " " + (r.getAmount() != null ? r.getAmount().toPlainString() : "0") + "원").collect(Collectors.joining("; "))).append(".\n");
        }
    }

    private void appendComparisonEvidence(StringBuilder evidence, Long userId, String yearMonth, String previousYearMonth) {
        SpendingRow curr = analysisMapper.selectSpendingByYearMonth(userId, yearMonth);
        SpendingRow prev = previousYearMonth != null ? analysisMapper.selectSpendingByYearMonthPrevious(userId, previousYearMonth) : null;
        if (curr != null) {
            evidence.append("이번달 총 지출: ").append(curr.getTotalExpense() != null ? curr.getTotalExpense().toPlainString() : "0").append("원, 거래 ").append(curr.getTransactionCount() != null ? curr.getTransactionCount() : 0).append("건.\n");
        }
        if (prev != null) {
            evidence.append("전달 총 지출: ").append(prev.getTotalExpense() != null ? prev.getTotalExpense().toPlainString() : "0").append("원.\n");
            if (curr != null && curr.getTotalExpense() != null && prev.getTotalExpense() != null && prev.getTotalExpense().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = curr.getTotalExpense().subtract(prev.getTotalExpense());
                BigDecimal pct = diff.multiply(BigDecimal.valueOf(100)).divide(prev.getTotalExpense(), 1, java.math.RoundingMode.HALF_UP);
                evidence.append("전월 대비: ").append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "").append(diff.toPlainString()).append("원 (").append(pct.toPlainString()).append("%).\n");
            }
        }
        List<CategoryCompareRow> compare = analysisMapper.selectCategoryCompareWithPrevious(userId, yearMonth, previousYearMonth);
        if (compare != null && !compare.isEmpty()) {
            evidence.append("카테고리별 비교: ");
            evidence.append(compare.stream().limit(7).map(r ->
                r.getCategoryName() + " " + (r.getCurrentAmount() != null ? r.getCurrentAmount().toPlainString() : "0") + "원 vs 전월 " + (r.getPreviousAmount() != null ? r.getPreviousAmount().toPlainString() : "0") + "원"
            ).collect(Collectors.joining("; "))).append(".\n");
        }
    }

    private void appendBudgetEvidence(StringBuilder evidence, Long userId, String yearMonth) {
        SpendingRow spending = analysisMapper.selectSpendingByYearMonth(userId, yearMonth);
        if (spending != null) {
            evidence.append("총 지출: ").append(spending.getTotalExpense() != null ? spending.getTotalExpense().toPlainString() : "0").append("원.\n");
        }
        List<BudgetActualRow> budgetActual = analysisMapper.selectBudgetVsActual(userId, yearMonth);
        if (budgetActual != null && !budgetActual.isEmpty()) {
            evidence.append("예산 대비 실제: ");
            evidence.append(budgetActual.stream().map(r ->
                r.getCategoryName() + " 예산 " + (r.getBudgetAmount() != null ? r.getBudgetAmount().toPlainString() : "0") + "원, 실제 " + (r.getActualAmount() != null ? r.getActualAmount().toPlainString() : "0") + "원" + (r.getOverAmount() != null && r.getOverAmount().compareTo(BigDecimal.ZERO) > 0 ? ", 초과 " + r.getOverAmount().toPlainString() + "원" : "")
            ).collect(Collectors.joining("; "))).append(".\n");
        } else {
            List<CategoryAmountRow> top = analysisMapper.selectCategoryExpensesTop(userId, yearMonth, TOP_N);
            if (top != null && !top.isEmpty()) {
                evidence.append("카테고리별 지출: ").append(top.stream().map(r -> r.getCategoryName() + " " + (r.getAmount() != null ? r.getAmount().toPlainString() : "0") + "원").collect(Collectors.joining("; "))).append(".\n");
            }
        }
    }

    private void appendPatternEvidence(StringBuilder evidence, Long userId, String yearMonth, String categoryHint) {
        List<CategoryAmountRow> top = analysisMapper.selectCategoryExpensesTop(userId, yearMonth, TOP_N);
        if (top != null && !top.isEmpty()) {
            evidence.append("카테고리 TOP: ").append(top.stream().map(r -> r.getCategoryName() + " " + (r.getAmount() != null ? r.getAmount().toPlainString() : "0") + "원").collect(Collectors.joining("; "))).append(".\n");
        }
        List<WeekdayRow> byWeekday = analysisMapper.selectExpenseByWeekday(userId, yearMonth);
        if (byWeekday != null && !byWeekday.isEmpty()) {
            evidence.append("요일별 지출: ");
            evidence.append(byWeekday.stream().map(r -> {
                int dow = (r.getDayOfWeek() != null && r.getDayOfWeek() >= 1 && r.getDayOfWeek() <= 7) ? r.getDayOfWeek() : 0;
                return WEEKDAY_NAMES[dow] + " " + (r.getAmount() != null ? r.getAmount().toPlainString() : "0") + "원";
            }).collect(Collectors.joining("; "))).append(".\n");
        }
        List<WeekOfMonthRow> byWeek = analysisMapper.selectExpenseByWeekOfMonth(userId, yearMonth);
        if (byWeek != null && !byWeek.isEmpty()) {
            evidence.append("주차별 지출: ");
            evidence.append(byWeek.stream().map(r -> r.getWeekOfMonth() + "주차 " + (r.getAmount() != null ? r.getAmount().toPlainString() : "0") + "원").collect(Collectors.joining("; "))).append(".\n");
        }
        List<MerchantRow> merchants = analysisMapper.selectTopMerchantsByExpense(userId, yearMonth, TOP_N);
        if (merchants != null && !merchants.isEmpty()) {
            evidence.append("가맹점 TOP: ").append(merchants.stream().map(r -> r.getMerchant() + " " + (r.getAmount() != null ? r.getAmount().toPlainString() : "0") + "원").collect(Collectors.joining("; "))).append(".\n");
        }
        if (categoryHint != null && !categoryHint.isBlank()) {
            CategoryAmountRow cat = analysisMapper.selectCategoryExpenseByName(userId, yearMonth, categoryHint);
            if (cat != null && cat.getAmount() != null) {
                evidence.append("[").append(categoryHint).append("] 해당월: ").append(cat.getAmount().toPlainString()).append("원.\n");
            }
        }
    }

    private void appendGeneralEvidence(StringBuilder evidence, Long userId, String yearMonth, String previousYearMonth) {
        SpendingRow curr = analysisMapper.selectSpendingByYearMonth(userId, yearMonth);
        if (curr != null) {
            evidence.append("총 수입: ").append(curr.getTotalIncome() != null ? curr.getTotalIncome().toPlainString() : "0").append("원, 총 지출: ").append(curr.getTotalExpense() != null ? curr.getTotalExpense().toPlainString() : "0").append("원, 거래 ").append(curr.getTransactionCount() != null ? curr.getTransactionCount() : 0).append("건.\n");
        }
        List<CategoryAmountRow> top = analysisMapper.selectCategoryExpensesTop(userId, yearMonth, TOP_N);
        if (top != null && !top.isEmpty()) {
            evidence.append("지출 TOP: ").append(top.stream().map(r -> r.getCategoryName() + " " + (r.getAmount() != null ? r.getAmount().toPlainString() : "0") + "원").collect(Collectors.joining("; "))).append(".\n");
        }
        if (previousYearMonth != null) {
            SpendingRow prev = analysisMapper.selectSpendingByYearMonthPrevious(userId, previousYearMonth);
            if (prev != null && curr != null && curr.getTotalExpense() != null && prev.getTotalExpense() != null && prev.getTotalExpense().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = curr.getTotalExpense().subtract(prev.getTotalExpense());
                BigDecimal pct = diff.multiply(BigDecimal.valueOf(100)).divide(prev.getTotalExpense(), 1, java.math.RoundingMode.HALF_UP);
                evidence.append("전월 대비 지출: ").append(diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "").append(diff.toPlainString()).append("원 (").append(pct.toPlainString()).append("%).\n");
            }
        }
    }

    private static String previousYearMonth(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth, YYYYMM).minusMonths(1).format(YYYYMM);
        } catch (Exception e) {
            return null;
        }
    }
}
