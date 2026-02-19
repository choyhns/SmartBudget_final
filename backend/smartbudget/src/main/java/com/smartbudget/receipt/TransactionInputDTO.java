package com.smartbudget.receipt;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionInputDTO {
    private LocalDateTime txDatetime;
    private BigDecimal amount;
    private String merchant;
    private String memo;
    private String source;
    private Long methodId;
    private Long categoryId; // 수동 지정 시 사용, null이면 자동 분류
}
