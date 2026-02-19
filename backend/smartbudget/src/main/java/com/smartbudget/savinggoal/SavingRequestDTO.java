package com.smartbudget.savinggoal;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SavingRequestDTO {
    private BigDecimal amount;
    private Long txId; // 연결할 거래 ID (optional)
}
