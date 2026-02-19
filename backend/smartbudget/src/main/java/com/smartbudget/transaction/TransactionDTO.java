package com.smartbudget.transaction;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionDTO {
    private Long txId;
    private LocalDateTime txDatetime;
    private BigDecimal amount;
    private String merchant;
    private String memo;
    private String source;
    private LocalDateTime createdAt;
    private Long userId;
    private Long methodId;
    private Long categoryId;
    
    // 조회용 추가 필드 (JOIN 결과용)
    private String categoryName;
    private String methodName;
    
    // Receipt 연결용 (요청 시에만 사용)
    private Long receiptFileId;
}
