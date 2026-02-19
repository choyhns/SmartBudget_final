package com.smartbudget.budget;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BudgetDTO {
    private Long budgetId;
    private String yearMonth; // "202501" 형식
    private BigDecimal totalBudget;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long userId;
}
