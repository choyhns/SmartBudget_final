package com.smartbudget.monthlyreport;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.smartbudget.transaction.TransactionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyReportScheduler {
    
    private final MonthlyReportService monthlyReportService;
    private final TransactionMapper transactionMapper;
    
    /**
     * 매월 1일 00:00:00에 실행
     * 모든 사용자에 대해 지난달 리포트 생성
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void generateMonthlyReports() {
        log.info("Starting monthly report generation for last month");
        
        try {
            // 모든 사용자 ID 가져오기 (임시로 하드코딩, 추후 User 테이블과 연동)
            // 실제로는 UserMapper에서 모든 사용자 조회
            List<Long> userIds = List.of(1L); // 임시
            
            LocalDate lastMonth = LocalDate.now().minusMonths(1);
            log.info("Generating reports for month: {}", lastMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
            
            for (Long userId : userIds) {
                try {
                    monthlyReportService.generateLastMonthReport(userId);
                    log.info("Successfully generated report for user: {}", userId);
                } catch (Exception e) {
                    log.error("Failed to generate report for user: {}", userId, e);
                }
            }
            
            log.info("Completed monthly report generation");
        } catch (Exception e) {
            log.error("Error in monthly report scheduler", e);
        }
    }
}
