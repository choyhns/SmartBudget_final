package com.smartbudget.budget;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BudgetStatusDTO {
    private String yearMonth;
    private BigDecimal totalBudget;
    private BigDecimal totalSpent;
    private BigDecimal remainingBudget;
    private BigDecimal budgetUsagePercent; // 0-100
    private List<CategoryBudgetDTO> categoryBudgets;
}
