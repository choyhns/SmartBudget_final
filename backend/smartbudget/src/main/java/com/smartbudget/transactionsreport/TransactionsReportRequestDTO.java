package com.smartbudget.transactionsreport;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransactionsReportRequestDTO {
    private String yearMonth;      // "202501" 형식
    private BigDecimal monthlyBudget;
}
