package com.smartbudget.savinggoal;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/saving-goals")
public class SavingGoalController {

    private final SavingGoalService savingGoalService;

    /**
     * 모든 저축 목표 조회
     */
    @GetMapping
    public List<SavingGoalDTO> getAllGoals(@RequestParam(defaultValue = "1") Long userId) {
        return savingGoalService.getAllGoals(userId);
    }

    /**
     * 저축 목표 상세 조회
     */
    @GetMapping("/{goalId}")
    public SavingGoalDTO getGoal(@PathVariable Long goalId) {
        return savingGoalService.getGoalById(goalId);
    }

    /**
     * 저축 목표 생성
     */
    @PostMapping
    public SavingGoalDTO createGoal(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody SavingGoalDTO request) {
        return savingGoalService.createGoal(userId, request);
    }

    /**
     * 저축 목표 수정
     */
    @PutMapping("/{goalId}")
    public SavingGoalDTO updateGoal(
            @PathVariable Long goalId,
            @RequestBody SavingGoalDTO request) {
        return savingGoalService.updateGoal(goalId, request);
    }

    /**
     * 저축 목표 삭제
     */
    @DeleteMapping("/{goalId}")
    public void deleteGoal(@PathVariable Long goalId) {
        savingGoalService.deleteGoal(goalId);
    }

    /**
     * 저축 추가 (목표에 금액 입금)
     */
    @PostMapping("/{goalId}/savings")
    public GoalTransactionDTO addSaving(
            @PathVariable Long goalId,
            @RequestBody SavingRequestDTO request) {
        return savingGoalService.addSaving(goalId, request.getAmount(), request.getTxId());
    }

    /**
     * 목표의 저축 거래 목록 조회
     */
    @GetMapping("/{goalId}/savings")
    public List<GoalTransactionDTO> getGoalTransactions(@PathVariable Long goalId) {
        return savingGoalService.getGoalTransactions(goalId);
    }

    /**
     * 저축 거래 삭제
     */
    @DeleteMapping("/savings/{goalTxId}")
    public void deleteSaving(@PathVariable Long goalTxId) {
        savingGoalService.deleteSaving(goalTxId);
    }
}
