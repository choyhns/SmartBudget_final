package com.smartbudget.monthlyreport;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MonthlyReportDTO {
    private Long reportId;
    private String yearMonth; // "2024-05" 형식
    private String metricsJson; // JSON 문자열로 저장
    private String llmSummaryText;
    private String llmModel;
    private LocalDateTime createdAt;
    private Long userId;
    private Boolean isCurrentMonth; // 이번달 여부 (true: 이번달, false: 지난달)
}
