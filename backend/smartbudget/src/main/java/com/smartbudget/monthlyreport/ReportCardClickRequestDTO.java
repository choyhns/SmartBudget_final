package com.smartbudget.monthlyreport;

import lombok.Data;

@Data
public class ReportCardClickRequestDTO {
    private Long userId;
    private String yearMonth;
    private Long cardId;
}
