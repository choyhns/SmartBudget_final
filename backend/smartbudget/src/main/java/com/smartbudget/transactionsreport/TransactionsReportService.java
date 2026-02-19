package com.smartbudget.transactionsreport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.smartbudget.transaction.TransactionDTO;
import com.smartbudget.transaction.TransactionMapper;

@Service
public class TransactionsReportService {
    
    @Autowired
    TransactionMapper transactionMapper;

    /**
     * DB에서 모든 거래 내역을 가져와서 분석 데이터를 생성합니다.
     */
    public TransactionsReportResponseDTO generateReport(Long userId, String yearMonth, BigDecimal monthlyBudget) throws Exception {
        // DB에서 거래 내역 가져오기
        List<TransactionDTO> transactions = transactionMapper.selectTransactionsByYearMonth(userId, yearMonth);
        
        // 총 지출 (음수 금액의 절대값 합계)
        BigDecimal totalExpense = transactions.stream()
            .map(TransactionDTO::getAmount)
            .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) < 0)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 총 수입 (양수 금액 합계)
        BigDecimal totalIncome = transactions.stream()
            .map(TransactionDTO::getAmount)
            .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 카테고리별 지출 집계
        Map<String, BigDecimal> categoryExpenses = transactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) < 0)
            .collect(Collectors.groupingBy(
                t -> t.getCategoryName() != null ? t.getCategoryName() : "미분류",
                Collectors.reducing(BigDecimal.ZERO, 
                    t -> t.getAmount().abs(), 
                    BigDecimal::add)
            ));
        
        // 가장 많이 지출한 카테고리 TOP 3
        List<String> topCategories = categoryExpenses.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(3)
            .map(e -> e.getKey() + ": " + String.format("%,.0f", e.getValue()) + "원")
            .collect(Collectors.toList());
        
        // 위험 평가
        String riskAssessment;
        if (monthlyBudget != null && totalExpense.compareTo(monthlyBudget) > 0) {
            BigDecimal overBudget = totalExpense.subtract(monthlyBudget);
            riskAssessment = String.format(
                "⚠️ 예산 초과: 이번 달 지출이 예산을 %,.0f원 초과했습니다. (총 지출: %,.0f원, 예산: %,.0f원)",
                overBudget, totalExpense, monthlyBudget
            );
        } else if (monthlyBudget != null && totalExpense.compareTo(monthlyBudget.multiply(BigDecimal.valueOf(0.9))) > 0) {
            riskAssessment = String.format(
                "⚠️ 예산 경고: 이번 달 지출이 예산의 90%%에 근접했습니다. (총 지출: %,.0f원, 예산: %,.0f원)",
                totalExpense, monthlyBudget
            );
        } else {
            riskAssessment = String.format(
                "✅ 예산 관리 양호: 현재 지출은 %,.0f원입니다. (예산: %,.0f원)",
                totalExpense, monthlyBudget != null ? monthlyBudget : BigDecimal.ZERO
            );
        }
        
        // 지출 패턴 분석
        List<String> topPatterns = topCategories.isEmpty() 
            ? List.of("아직 지출 데이터가 없습니다.")
            : topCategories;
        
        // 절약 팁 (기본적인 팁 제공, 실제로는 AI로 생성 가능)
        List<String> savingTips = List.of(
            "가장 많이 지출한 카테고리에서 10% 절감 목표 설정",
            "소액 거래를 모아서 한 번에 결제하기",
            "월말 예산 검토 및 다음 달 계획 수립"
        );
        
        TransactionsReportResponseDTO response = new TransactionsReportResponseDTO();
        response.setRiskAssessment(riskAssessment);
        response.setTopPatterns(topPatterns);
        response.setSavingTips(savingTips);
        response.setTotalExpense(totalExpense);
        response.setTotalIncome(totalIncome);
        response.setCategoryExpenses(categoryExpenses);
        
        return response;
    }
}
