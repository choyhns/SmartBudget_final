package com.smartbudget.monthlyreport;

import lombok.Data;

@Data
public class MonthlyReportRequestDTO {
    private Long userId;
    private Long monthlyBudget;
}
