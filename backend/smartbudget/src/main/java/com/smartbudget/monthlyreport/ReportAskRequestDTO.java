package com.smartbudget.monthlyreport;

import lombok.Data;

@Data
public class ReportAskRequestDTO {
    private Long userId;
    private String yearMonth;
    private String question;
}
