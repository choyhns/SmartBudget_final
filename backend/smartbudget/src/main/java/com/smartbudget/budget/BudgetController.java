package com.smartbudget.budget;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    /**
     * 월별 총 예산 조회 (없으면 404 → 프론트에서 null 처리, JSON 빈 응답 오류 방지)
     */
    @GetMapping("/{yearMonth}")
    public ResponseEntity<BudgetDTO> getBudget(
            @PathVariable String yearMonth,
            @RequestParam(defaultValue = "1") Long userId) {
        BudgetDTO b = budgetService.getBudgetByYearMonth(userId, yearMonth);
        if (b == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(b);
    }

    /**
     * 사용자의 모든 예산 목록 조회
     */
    @GetMapping
    public List<BudgetDTO> getAllBudgets(@RequestParam(defaultValue = "1") Long userId) {
        return budgetService.getAllBudgets(userId);
    }

    /**
     * 월별 총 예산 설정
     */
    @PostMapping("/{yearMonth}")
    public BudgetDTO setMonthlyBudget(
            @PathVariable String yearMonth,
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody BudgetRequestDTO request) {
        return budgetService.setMonthlyBudget(userId, yearMonth, request.getTotalBudget());
    }

    /**
     * 카테고리별 예산 목록 조회
     */
    @GetMapping("/{yearMonth}/categories")
    public List<CategoryBudgetDTO> getCategoryBudgets(
            @PathVariable String yearMonth,
            @RequestParam(defaultValue = "1") Long userId) {
        return budgetService.getCategoryBudgets(userId, yearMonth);
    }

    /**
     * 카테고리별 예산 설정
     */
    @PostMapping("/{yearMonth}/categories/{categoryId}")
    public CategoryBudgetDTO setCategoryBudget(
            @PathVariable String yearMonth,
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody CategoryBudgetRequestDTO request) {
        return budgetService.setCategoryBudget(userId, yearMonth, categoryId, request.getBudgetAmount());
    }

    /**
     * 카테고리 예산 삭제
     */
    @DeleteMapping("/categories/{catBudgetId}")
    public void deleteCategoryBudget(@PathVariable Long catBudgetId) {
        budgetService.deleteCategoryBudget(catBudgetId);
    }

    /**
     * 예산 대비 지출 현황 조회
     */
    @GetMapping("/{yearMonth}/status")
    public BudgetStatusDTO getBudgetStatus(
            @PathVariable String yearMonth,
            @RequestParam(defaultValue = "1") Long userId) throws Exception {
        return budgetService.getBudgetStatus(userId, yearMonth);
    }

    /**
     * 예산/목표 인사이트: 저축 추천, 예산 대비 사용, 추세·이번 달 전망 (Python LLM)
     */
    @GetMapping("/{yearMonth}/insight")
    public java.util.Map<String, String> getBudgetInsight(
            @PathVariable String yearMonth,
            @RequestParam(defaultValue = "1") Long userId) {
        String insight = budgetService.getBudgetInsight(userId, yearMonth);
        return java.util.Map.of("insight", insight != null ? insight : "");
    }
}
