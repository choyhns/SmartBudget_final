package com.smartbudget.budget;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class BudgetRequestDTO {
    private BigDecimal totalBudget;
}
