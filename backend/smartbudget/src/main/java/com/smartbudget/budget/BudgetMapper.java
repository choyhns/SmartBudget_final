package com.smartbudget.budget;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BudgetMapper {
    // 월별 총 예산
    BudgetDTO selectBudgetByYearMonth(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);
    List<BudgetDTO> selectBudgetsByUser(@Param("userId") Long userId);
    int insertBudget(BudgetDTO budget);
    int updateBudget(BudgetDTO budget);
    int deleteBudget(@Param("budgetId") Long budgetId);
    
    // 카테고리별 예산
    List<CategoryBudgetDTO> selectCategoryBudgetsByYearMonth(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);
    CategoryBudgetDTO selectCategoryBudget(@Param("userId") Long userId, @Param("yearMonth") String yearMonth, @Param("categoryId") Long categoryId);
    int insertCategoryBudget(CategoryBudgetDTO categoryBudget);
    int updateCategoryBudget(CategoryBudgetDTO categoryBudget);
    int deleteCategoryBudget(@Param("catBudgetId") Long catBudgetId);
}
