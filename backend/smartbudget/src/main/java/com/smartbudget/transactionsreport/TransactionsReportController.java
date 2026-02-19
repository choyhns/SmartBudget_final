package com.smartbudget.transactionsreport;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TransactionsReportController {
    
    private final TransactionsReportService transactionsReportService;
    
    @PostMapping("/api/transactions/report")
    public TransactionsReportResponseDTO generateReport(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody TransactionsReportRequestDTO request) throws Exception {
        return transactionsReportService.generateReport(
            userId, 
            request.getYearMonth(), 
            request.getMonthlyBudget()
        );
    }
}
