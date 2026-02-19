package com.smartbudget.savinggoal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartbudget.transaction.TransactionDTO;
import com.smartbudget.transaction.TransactionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SavingGoalService {
    
    @Autowired
    private SavingGoalMapper savingGoalMapper;

    @Autowired
    private TransactionService transactionService;
    
    /**
     * 사용자의 모든 저축 목표 조회
     */
    public List<SavingGoalDTO> getAllGoals(Long userId) {
        List<SavingGoalDTO> goals = savingGoalMapper.selectGoalsByUser(userId);
        goals.forEach(this::calculateProgress);
        return goals;
    }
    
    /**
     * 저축 목표 상세 조회
     */
    public SavingGoalDTO getGoalById(Long goalId) {
        SavingGoalDTO goal = savingGoalMapper.selectGoalById(goalId);
        if (goal != null) {
            calculateProgress(goal);
        }
        return goal;
    }
    
    /**
     * 저축 목표 생성
     */
    @Transactional
    public SavingGoalDTO createGoal(Long userId, SavingGoalDTO goalRequest) {
        SavingGoalDTO goal = new SavingGoalDTO();
        goal.setUserId(userId);
        goal.setGoalTitle(goalRequest.getGoalTitle());
        goal.setGoalAmount(goalRequest.getGoalAmount());
        goal.setStartDate(goalRequest.getStartDate() != null ? goalRequest.getStartDate() : LocalDate.now());
        goal.setTargetDate(goalRequest.getTargetDate());
        
        // 월 저축 목표액 자동 계산 (목표일이 있는 경우)
        if (goal.getTargetDate() != null && goal.getGoalAmount() != null) {
            long months = ChronoUnit.MONTHS.between(goal.getStartDate(), goal.getTargetDate());
            if (months > 0) {
                goal.setMonthlyTarget(goal.getGoalAmount().divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING));
            }
        } else if (goalRequest.getMonthlyTarget() != null) {
            goal.setMonthlyTarget(goalRequest.getMonthlyTarget());
        }
        
        savingGoalMapper.insertGoal(goal);
        return getGoalById(goal.getGoalId());
    }
    
    /**
     * 저축 목표 수정
     */
    @Transactional
    public SavingGoalDTO updateGoal(Long goalId, SavingGoalDTO goalRequest) {
        SavingGoalDTO existing = savingGoalMapper.selectGoalById(goalId);
        if (existing == null) {
            throw new RuntimeException("Goal not found: " + goalId);
        }
        
        existing.setGoalTitle(goalRequest.getGoalTitle());
        existing.setGoalAmount(goalRequest.getGoalAmount());
        existing.setStartDate(goalRequest.getStartDate());
        existing.setTargetDate(goalRequest.getTargetDate());
        existing.setMonthlyTarget(goalRequest.getMonthlyTarget());
        
        savingGoalMapper.updateGoal(existing);
        return getGoalById(goalId);
    }
    
    /**
     * 저축 목표 삭제
     */
    public void deleteGoal(Long goalId) {
        savingGoalMapper.deleteGoal(goalId);
    }
    
    /**
     * 저축 거래 추가 (목표에 금액 추가)
     * txId가 없으면 저축용 거래를 새로 생성한 뒤 goal_transactions에 연결한다.
     */
    @Transactional
    public GoalTransactionDTO addSaving(Long goalId, BigDecimal amount, Long txId) {
        if (txId == null) {
            SavingGoalDTO goal = getGoalById(goalId);
            if (goal == null) {
                throw new RuntimeException("Goal not found: " + goalId);
            }
            TransactionDTO tx = new TransactionDTO();
            tx.setTxDatetime(LocalDateTime.now());
            tx.setAmount(amount);
            tx.setUserId(goal.getUserId());
            tx.setMemo("저축");
            try {
                transactionService.createTransaction(tx);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create saving transaction", e);
            }
            txId = tx.getTxId();
        }

        GoalTransactionDTO goalTx = new GoalTransactionDTO();
        goalTx.setGoalId(goalId);
        goalTx.setAmount(amount);
        goalTx.setTxId(txId);

        savingGoalMapper.insertGoalTransaction(goalTx);
        return goalTx;
    }
    
    /**
     * 목표의 저축 거래 목록 조회
     */
    public List<GoalTransactionDTO> getGoalTransactions(Long goalId) {
        return savingGoalMapper.selectGoalTransactions(goalId);
    }
    
    /**
     * 저축 거래 삭제
     */
    public void deleteSaving(Long goalTxId) {
        savingGoalMapper.deleteGoalTransaction(goalTxId);
    }
    
    /**
     * 달성률 및 남은 기간 계산
     */
    private void calculateProgress(SavingGoalDTO goal) {
        if (goal.getGoalAmount() != null && goal.getGoalAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentAmount = goal.getCurrentAmount() != null ? goal.getCurrentAmount() : BigDecimal.ZERO;
            goal.setProgressPercent(
                currentAmount.divide(goal.getGoalAmount(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
            );
        }
        
        if (goal.getTargetDate() != null) {
            long months = ChronoUnit.MONTHS.between(LocalDate.now(), goal.getTargetDate());
            goal.setRemainingMonths(Math.max(0, (int) months));
        }
    }
}
