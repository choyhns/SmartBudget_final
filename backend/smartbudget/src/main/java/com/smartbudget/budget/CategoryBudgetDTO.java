package com.smartbudget.budget;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CategoryBudgetDTO {
    private Long catBudgetId;
    private String yearMonth; // "202501" 형식
    private BigDecimal budgetAmount;
    private LocalDateTime createdAt;
    private Long userId;
    private Long categoryId;
    
    // 조회용 추가 필드
    private String categoryName;
}
