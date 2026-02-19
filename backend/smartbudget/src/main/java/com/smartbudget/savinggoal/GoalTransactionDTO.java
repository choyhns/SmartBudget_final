package com.smartbudget.savinggoal;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GoalTransactionDTO {
    private Long goalTxId;
    private BigDecimal amount;
    private Long goalId;
    private Long txId;
    
    // 조회용 추가 필드
    private LocalDateTime txDatetime;
    private String memo;
}
