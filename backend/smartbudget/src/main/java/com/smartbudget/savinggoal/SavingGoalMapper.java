package com.smartbudget.savinggoal;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SavingGoalMapper {
    // 저축 목표
    List<SavingGoalDTO> selectGoalsByUser(@Param("userId") Long userId);
    SavingGoalDTO selectGoalById(@Param("goalId") Long goalId);
    int insertGoal(SavingGoalDTO goal);
    int updateGoal(SavingGoalDTO goal);
    int deleteGoal(@Param("goalId") Long goalId);
    
    // 목표별 저축액 조회
    BigDecimal selectTotalSavedAmount(@Param("goalId") Long goalId);
    
    // 목표 거래 연결
    List<GoalTransactionDTO> selectGoalTransactions(@Param("goalId") Long goalId);
    int insertGoalTransaction(GoalTransactionDTO goalTx);
    int deleteGoalTransaction(@Param("goalTxId") Long goalTxId);
}
