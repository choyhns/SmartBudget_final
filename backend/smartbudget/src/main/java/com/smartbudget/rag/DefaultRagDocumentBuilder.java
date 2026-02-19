package com.smartbudget.rag;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.smartbudget.budget.BudgetMapper;
import com.smartbudget.budget.CategoryBudgetDTO;
import com.smartbudget.rag.dto.FactsBuildResult;
import com.smartbudget.rag.dto.RagChunk;
import com.smartbudget.rag.model.RagMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RagDocumentBuilder 구현. Facts 블록: 총지출/수입, TOP5, 예산·사용률, 초과 카테고리, 전월 대비.
 * <p>NOTE: transactions에 tx_type 컬럼이 없으면 amount 기준 fallback 사용 (amount &lt; 0 = 지출, &gt; 0 = 수입).
 * TODO: tx_type 추가 시 SQL에서 WHERE tx_type='EXPENSE'/'INCOME' 로 변경.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultRagDocumentBuilder implements RagDocumentBuilder {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int TOP5 = 5;

    private final RagFactsMapper ragFactsMapper;
    private final BudgetMapper budgetMapper;

    @Override
    public List<RagChunk> buildDocuments(Long userId, String yearMonth) {
        FactsBuildResult result = buildFacts(userId, yearMonth);
        return result != null && result.getDocList() != null ? result.getDocList() : List.of();
    }

    @Override
    public FactsBuildResult buildFacts(Long userId, String yearMonth) {
        String yyyyMm = normalizeYearMonth(yearMonth);
        if (userId == null || yyyyMm == null) {
            log.warn("buildFacts: userId or yearMonth is null");
            return new FactsBuildResult("", List.of());
        }

        // TODO: transactions에 tx_type 컬럼이 있으면 SQL에서 WHERE tx_type='EXPENSE'/'INCOME' 로 집계.
        // Fallback 1: amount < 0 = 지출, amount > 0 = 수입 (현재 사용).
        // Fallback 2: tx_type 없을 때 전부 지출로 간주하면 totalIncome=0, totalExpense=SUM(ABS(amount)).
        RagFactsDTO.SelectSpendingRow spending = ragFactsMapper.selectSpendingByYearMonth(userId, yyyyMm);
        BigDecimal totalIncome = spending != null && spending.getTotalIncome() != null ? spending.getTotalIncome() : BigDecimal.ZERO;
        BigDecimal totalExpense = spending != null && spending.getTotalExpense() != null ? spending.getTotalExpense() : BigDecimal.ZERO;

        List<RagFactsDTO.CategoryAmountRow> categoryExpenses = ragFactsMapper.selectCategoryExpensesByYearMonth(userId, yyyyMm);
        List<String> top5Lines = formatCategoryTop5(categoryExpenses, totalExpense);

        BigDecimal totalBudget = null;
        String budgetUsageLine = "";
        try {
            var budget = budgetMapper.selectBudgetByYearMonth(userId, yyyyMm);
            if (budget != null && budget.getTotalBudget() != null) {
                totalBudget = budget.getTotalBudget();
                if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal pct = totalExpense.multiply(BigDecimal.valueOf(100)).divide(totalBudget, 1, RoundingMode.HALF_UP);
                    budgetUsageLine = "예산 " + totalBudget.toPlainString() + "원, 사용률 " + pct.toPlainString() + "%.";
                }
            }
        } catch (Exception e) {
            log.warn("buildFacts: budget lookup failed for user={}, yearMonth={}", userId, yyyyMm, e);
        }

        List<String> overCategoryLines = formatBudgetOverCategories(userId, yyyyMm, categoryExpenses);

        String momLine = formatMonthOverMonth(userId, yyyyMm, totalExpense);

        String factsBlock = buildFactsBlockText(yyyyMm, totalIncome, totalExpense, top5Lines, budgetUsageLine, overCategoryLines, momLine);

        RagMetadata meta = new RagMetadata(userId, yyyyMm, "facts", null);
        RagChunk factsChunk = new RagChunk("facts-" + userId + "-" + yyyyMm, factsBlock, meta, null);
        return new FactsBuildResult(factsBlock, List.of(factsChunk));
    }

    private static String normalizeYearMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) return null;
        return yearMonth.replace("-", "").trim();
    }

    private List<String> formatCategoryTop5(List<RagFactsDTO.CategoryAmountRow> categoryExpenses, BigDecimal totalExpense) {
        List<String> lines = new ArrayList<>();
        if (categoryExpenses == null || categoryExpenses.isEmpty()) return lines;
        int limit = Math.min(TOP5, categoryExpenses.size());
        for (int i = 0; i < limit; i++) {
            RagFactsDTO.CategoryAmountRow row = categoryExpenses.get(i);
            BigDecimal amt = row.getAmount() != null ? row.getAmount() : BigDecimal.ZERO;
            String share = BigDecimal.ZERO.equals(totalExpense)
                    ? "0"
                    : amt.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 1, RoundingMode.HALF_UP).toPlainString();
            lines.add(row.getCategoryName() + " " + amt.toPlainString() + "원 (" + share + "%)");
        }
        return lines;
    }

    private List<String> formatBudgetOverCategories(Long userId, String yearMonth, List<RagFactsDTO.CategoryAmountRow> categoryExpenses) {
        List<String> lines = new ArrayList<>();
        try {
            List<CategoryBudgetDTO> categoryBudgets = budgetMapper.selectCategoryBudgetsByYearMonth(userId, yearMonth);
            if (categoryBudgets == null || categoryExpenses == null) return lines;
            var expenseByCategory = categoryExpenses.stream()
                    .collect(Collectors.toMap(r -> r.getCategoryName() != null ? r.getCategoryName() : "미분류", r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO, (a, b) -> a));
            for (CategoryBudgetDTO cb : categoryBudgets) {
                BigDecimal budgetAmt = cb.getBudgetAmount() != null ? cb.getBudgetAmount() : BigDecimal.ZERO;
                String catName = cb.getCategoryName() != null ? cb.getCategoryName() : "미분류";
                BigDecimal actual = expenseByCategory.getOrDefault(catName, BigDecimal.ZERO);
                if (actual.compareTo(budgetAmt) > 0) {
                    BigDecimal over = actual.subtract(budgetAmt);
                    lines.add(catName + " 예산 " + budgetAmt.toPlainString() + "원 대비 " + over.toPlainString() + "원 초과.");
                }
            }
        } catch (Exception e) {
            log.warn("buildFacts: category budget comparison failed for user={}, yearMonth={}", userId, yearMonth, e);
        }
        return lines;
    }

    private String formatMonthOverMonth(Long userId, String yearMonth, BigDecimal thisMonthExpense) {
        try {
            YearMonth ym = YearMonth.parse(yearMonth, YYYYMM);
            YearMonth prev = ym.minusMonths(1);
            String prevYearMonth = prev.format(YYYYMM);
            RagFactsDTO.SelectSpendingRow prevSpending = ragFactsMapper.selectSpendingByYearMonth(userId, prevYearMonth);
            BigDecimal prevExpense = prevSpending != null && prevSpending.getTotalExpense() != null ? prevSpending.getTotalExpense() : BigDecimal.ZERO;
            if (prevExpense.compareTo(BigDecimal.ZERO) == 0) {
                return "전월 대비: 전월 지출 없음.";
            }
            BigDecimal diff = thisMonthExpense.subtract(prevExpense);
            BigDecimal pct = diff.multiply(BigDecimal.valueOf(100)).divide(prevExpense, 1, RoundingMode.HALF_UP);
            String sign = diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            return "전월 대비: " + sign + diff.toPlainString() + "원 (" + sign + pct.toPlainString() + "%).";
        } catch (Exception e) {
            log.debug("buildFacts: MoM failed for user={}, yearMonth={}", userId, yearMonth, e);
            return "";
        }
    }

    private String buildFactsBlockText(String yyyyMm, BigDecimal totalIncome, BigDecimal totalExpense,
                                       List<String> top5Lines, String budgetUsageLine,
                                       List<String> overCategoryLines, String momLine) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Facts ").append(yyyyMm).append("】\n");
        sb.append("총수입: ").append(totalIncome.toPlainString()).append("원.\n");
        sb.append("총지출: ").append(totalExpense.toPlainString()).append("원.\n");
        if (!top5Lines.isEmpty()) {
            sb.append("카테고리 TOP5(카테고리명, 금액, 비중): ");
            sb.append(String.join("; ", top5Lines)).append(".\n");
        }
        if (!budgetUsageLine.isEmpty()) sb.append(budgetUsageLine).append("\n");
        if (!overCategoryLines.isEmpty()) {
            sb.append("예산 초과 카테고리: ").append(String.join(" ", overCategoryLines)).append("\n");
        }
        if (!momLine.isEmpty()) sb.append(momLine).append("\n");
        return sb.toString();
    }
}
