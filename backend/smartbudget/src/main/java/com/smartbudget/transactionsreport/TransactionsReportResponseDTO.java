package com.smartbudget.transactionsreport;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class TransactionsReportResponseDTO {
    private String riskAssessment;
    private List<String> topPatterns;
    private List<String> savingTips;
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private Map<String, BigDecimal> categoryExpenses;
}
