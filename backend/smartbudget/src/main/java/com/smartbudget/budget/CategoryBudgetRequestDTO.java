package com.smartbudget.budget;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CategoryBudgetRequestDTO {
    private BigDecimal budgetAmount;
}
