package com.smartbudget.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartbudget.savinggoal.SavingGoalDTO;
import com.smartbudget.savinggoal.SavingGoalService;
import com.smartbudget.transaction.TransactionDTO;
import com.smartbudget.transaction.TransactionMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BudgetService {

    @Autowired
    private BudgetMapper budgetMapper;

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private SavingGoalService savingGoalService;

    @Autowired
    private com.smartbudget.llm.PythonAIService pythonAIService;
    
    /**
     * 월별 총 예산 조회
     */
    public BudgetDTO getBudgetByYearMonth(Long userId, String yearMonth) {
        return budgetMapper.selectBudgetByYearMonth(userId, yearMonth);
    }
    
    /**
     * 사용자의 모든 예산 조회
     */
    public List<BudgetDTO> getAllBudgets(Long userId) {
        return budgetMapper.selectBudgetsByUser(userId);
    }
    
    /**
     * 월별 총 예산 설정 (생성 또는 업데이트)
     */
    @Transactional
    public BudgetDTO setMonthlyBudget(Long userId, String yearMonth, BigDecimal totalBudget) {
        BudgetDTO existing = budgetMapper.selectBudgetByYearMonth(userId, yearMonth);
        
        if (existing != null) {
            existing.setTotalBudget(totalBudget);
            budgetMapper.updateBudget(existing);
            return existing;
        } else {
            BudgetDTO newBudget = new BudgetDTO();
            newBudget.setUserId(userId);
            newBudget.setYearMonth(yearMonth);
            newBudget.setTotalBudget(totalBudget);
            budgetMapper.insertBudget(newBudget);
            return newBudget;
        }
    }
    
    /**
     * 카테고리별 예산 목록 조회
     */
    public List<CategoryBudgetDTO> getCategoryBudgets(Long userId, String yearMonth) {
        return budgetMapper.selectCategoryBudgetsByYearMonth(userId, yearMonth);
    }
    
    /**
     * 카테고리별 예산 설정 (생성 또는 업데이트)
     */
    @Transactional
    public CategoryBudgetDTO setCategoryBudget(Long userId, String yearMonth, Long categoryId, BigDecimal budgetAmount) {
        CategoryBudgetDTO existing = budgetMapper.selectCategoryBudget(userId, yearMonth, categoryId);
        
        if (existing != null) {
            existing.setBudgetAmount(budgetAmount);
            budgetMapper.updateCategoryBudget(existing);
            return existing;
        } else {
            CategoryBudgetDTO newCategoryBudget = new CategoryBudgetDTO();
            newCategoryBudget.setUserId(userId);
            newCategoryBudget.setYearMonth(yearMonth);
            newCategoryBudget.setCategoryId(categoryId);
            newCategoryBudget.setBudgetAmount(budgetAmount);
            budgetMapper.insertCategoryBudget(newCategoryBudget);
            return newCategoryBudget;
        }
    }
    
    /**
     * 카테고리 예산 삭제
     */
    public void deleteCategoryBudget(Long catBudgetId) {
        budgetMapper.deleteCategoryBudget(catBudgetId);
    }
    
    /**
     * 예산 대비 지출 현황 조회
     */
    public BudgetStatusDTO getBudgetStatus(Long userId, String yearMonth) throws Exception {
        BudgetDTO budget = budgetMapper.selectBudgetByYearMonth(userId, yearMonth);
        List<CategoryBudgetDTO> categoryBudgets = budgetMapper.selectCategoryBudgetsByYearMonth(userId, yearMonth);
        
        // 해당 월 지출 합계 계산
        var transactions = transactionMapper.selectTransactionsByYearMonth(userId, yearMonth);
        BigDecimal totalSpent = transactions.stream()
            .map(t -> t.getAmount())
            .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) < 0)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BudgetStatusDTO status = new BudgetStatusDTO();
        status.setYearMonth(yearMonth);
        status.setTotalBudget(budget != null ? budget.getTotalBudget() : null);
        status.setTotalSpent(totalSpent);
        status.setCategoryBudgets(categoryBudgets);
        
        if (budget != null && budget.getTotalBudget() != null) {
            status.setRemainingBudget(budget.getTotalBudget().subtract(totalSpent));
            status.setBudgetUsagePercent(
                totalSpent.divide(budget.getTotalBudget(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
            );
        }
        
        return status;
    }

    /**
     * 예산/목표 인사이트: 소비패턴·저축 추천, 예산 대비 사용, 추세 분석. Python LLM 호출.
     */
    public String getBudgetInsight(Long userId, String yearMonth) {
        String ym = yearMonth != null ? yearMonth.replace("-", "").trim() : null;
        if (ym == null || ym.length() != 6) return "";

        try {
            BudgetDTO budget = budgetMapper.selectBudgetByYearMonth(userId, ym);
            List<CategoryBudgetDTO> categoryBudgets = budgetMapper.selectCategoryBudgetsByYearMonth(userId, ym);
            List<TransactionDTO> transactions = transactionMapper.selectTransactionsByYearMonth(userId, ym);

            BigDecimal totalSpent = transactions.stream()
                .map(TransactionDTO::getAmount)
                .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<Long, BigDecimal> spentByCategory = new LinkedHashMap<>();
            for (TransactionDTO t : transactions) {
                if (t.getAmount() == null || t.getAmount().compareTo(BigDecimal.ZERO) >= 0) continue;
                Long cid = t.getCategoryId() != null ? t.getCategoryId() : 0L;
                spentByCategory.merge(cid, t.getAmount().abs(), BigDecimal::add);
            }

            List<Map<String, Object>> categories = new ArrayList<>();
            for (CategoryBudgetDTO cb : categoryBudgets) {
                BigDecimal spent = spentByCategory.getOrDefault(cb.getCategoryId(), BigDecimal.ZERO);
                categories.add(Map.of(
                    "category_name", cb.getCategoryName() != null ? cb.getCategoryName() : "기타",
                    "budget_amount", cb.getBudgetAmount() != null ? cb.getBudgetAmount().longValue() : 0L,
                    "spent", spent.longValue()
                ));
            }

            List<SavingGoalDTO> goals = savingGoalService.getAllGoals(userId);
            List<Map<String, Object>> savingGoals = goals.stream()
                .map(g -> Map.<String, Object>of(
                    "goal_title", g.getGoalTitle() != null ? g.getGoalTitle() : "저축 목표",
                    "goal_amount", g.getGoalAmount() != null ? g.getGoalAmount().longValue() : 0L,
                    "current_amount", g.getCurrentAmount() != null ? g.getCurrentAmount().longValue() : 0L
                ))
                .collect(Collectors.toList());

            List<Map<String, Object>> lastMonths = new ArrayList<>();
            try {
                YearMonth base = ym != null && ym.contains("-")
                    ? YearMonth.parse(ym)
                    : YearMonth.parse(ym, DateTimeFormatter.ofPattern("yyyyMM"));
                for (int i = 1; i <= 2; i++) {
                    YearMonth prev = base.minusMonths(i);
                    String prevYm = prev.toString().replace("-", "");
                    List<TransactionDTO> prevTx = transactionMapper.selectTransactionsByYearMonth(userId, prevYm);
                    BigDecimal prevSpent = prevTx.stream()
                        .map(TransactionDTO::getAmount)
                        .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) < 0)
                        .map(BigDecimal::abs)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    lastMonths.add(Map.of("year_month", prevYm, "total_spent", prevSpent.longValue()));
                }
            } catch (Exception e) {
                log.warn("Last months trend failed", e);
            }

            long monthlyBudget = budget != null && budget.getTotalBudget() != null
                ? budget.getTotalBudget().longValue() : 0L;

            // 추가 분석 데이터: 경과일·예상 지출·예산 사용률 등 (Service 레이어에서 계산)
            YearMonth targetMonth = YearMonth.parse(ym, DateTimeFormatter.ofPattern("yyyyMM"));
            YearMonth nowMonth = YearMonth.now();
            int daysInMonth = targetMonth.lengthOfMonth();
            int daysElapsed;
            if (targetMonth.isBefore(nowMonth)) {
                daysElapsed = daysInMonth;
            } else if (targetMonth.isAfter(nowMonth)) {
                daysElapsed = 0;
            } else {
                daysElapsed = LocalDate.now().getDayOfMonth();
            }

            long totalSpentLong = totalSpent.longValue();
            int dailyAverageSpend = (daysElapsed > 0) ? (int) Math.round((double) totalSpentLong / daysElapsed) : 0;
            int projectedSpend = (int) Math.round(dailyAverageSpend * (double) daysInMonth);
            int projectedOverAmount = monthlyBudget > 0 ? (int) Math.round((double) projectedSpend - monthlyBudget) : projectedSpend;
            double budgetUsageRate = (monthlyBudget > 0) ? (double) totalSpentLong / monthlyBudget : 0.0;
            double projectedUsageRate = (monthlyBudget > 0) ? (double) projectedSpend / monthlyBudget : 0.0;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("year_month", ym);
            payload.put("monthly_budget", monthlyBudget);
            payload.put("total_spent", totalSpentLong);
            payload.put("categories", categories);
            payload.put("saving_goals", savingGoals);
            payload.put("last_months", lastMonths);
            payload.put("days_elapsed", daysElapsed);
            payload.put("days_in_month", daysInMonth);
            payload.put("daily_average_spend", dailyAverageSpend);
            payload.put("projected_spend", projectedSpend);
            payload.put("projected_over_amount", projectedOverAmount);
            payload.put("budget_usage_rate", budgetUsageRate);
            payload.put("projected_usage_rate", projectedUsageRate);

            return pythonAIService.getBudgetInsight(payload);
        } catch (Exception e) {
            log.error("getBudgetInsight failed for user={}, yearMonth={}", userId, yearMonth, e);
            return "";
        }
    }
}
