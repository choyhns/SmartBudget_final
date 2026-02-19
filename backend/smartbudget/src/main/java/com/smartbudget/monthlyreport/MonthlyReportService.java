package com.smartbudget.monthlyreport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartbudget.budget.BudgetDTO;
import com.smartbudget.budget.BudgetService;
import com.smartbudget.budget.CategoryBudgetDTO;
import com.smartbudget.llm.PythonAIService;
import com.smartbudget.rag.RagService;
import com.smartbudget.transaction.TransactionDTO;
import com.smartbudget.transaction.TransactionMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 월별 리포트: DB CRUD, 월별 집계(metrics), 리포트 저장, 스케줄러. AI는 Spring → Python만 호출.
 */
@Slf4j
@Service
public class MonthlyReportService {

    @Autowired
    private MonthlyReportMapper monthlyReportMapper;

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private PythonAIService pythonAIService;

    @Autowired
    private RagService ragService;

    @Autowired
    private BudgetService budgetService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    
    /**
     * 특정 월의 리포트 조회
     */
    public MonthlyReportDTO getReportByYearMonth(Long userId, String yearMonth) throws Exception {
        return monthlyReportMapper.selectReportByYearMonth(userId, yearMonth);
    }
    
    /**
     * 사용자의 모든 리포트 조회
     */
    public List<MonthlyReportDTO> getAllReports(Long userId) throws Exception {
        return monthlyReportMapper.selectReportsByUser(userId);
    }
    
    /**
     * 이번 달 리포트 조회
     */
    public MonthlyReportDTO getCurrentMonthReport(Long userId) throws Exception {
        return monthlyReportMapper.selectCurrentMonthReport(userId);
    }
    
    /**
     * 지난달 데이터를 분석하여 리포트 생성 및 저장
     * 매월 1일에 스케줄러로 호출됨
     */
    @Transactional
    public void generateLastMonthReport(Long userId) throws Exception {
        // 지난달 계산
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        String yearMonth = lastMonth.format(YEAR_MONTH_FORMATTER);
        
        log.info("Generating monthly report for user {} for month {}", userId, yearMonth);
        
        // 이미 리포트가 있는지 확인
        MonthlyReportDTO existingReport = monthlyReportMapper.selectReportByYearMonth(userId, yearMonth);
        if (existingReport != null) {
            log.info("Report already exists for {} - {}", userId, yearMonth);
            return;
        }
        
        // 지난달 거래 내역 조회
        List<TransactionDTO> transactions = transactionMapper.selectTransactionsByYearMonth(userId, yearMonth);
        
        // 통계 계산 (yearMonth 포함, last7Days = 해당 월 마지막 7일)
        Map<String, Object> metrics = calculateMetrics(transactions, userId, yearMonth);
        
        // 예산: DB에서 해당 월 사용자 예산 조회
        Long monthlyBudget = resolveMonthlyBudgetFromDb(userId, yearMonth);
        
        // LLM으로 분석 요약 생성 (Python AI 서버)
        List<Map<String, Object>> transactionMaps = convertToMapList(transactions);
        
        String llmSummary = pythonAIService.generateAnalysisSummary(transactionMaps, metrics, monthlyBudget);
        
        // 리포트 저장
        MonthlyReportDTO report = new MonthlyReportDTO();
        report.setYearMonth(yearMonth);
        report.setMetricsJson(objectMapper.writeValueAsString(metrics));
        report.setLlmSummaryText(llmSummary);
        report.setLlmModel("gemini-2.0-flash");
        report.setUserId(userId);
        report.setIsCurrentMonth(false); // 지난달 리포트
        report.setCreatedAt(LocalDateTime.now());
        
        monthlyReportMapper.insertMonthlyReport(report);
        log.info("Monthly report generated and saved for user {} - {}", userId, yearMonth);
        try {
            ragService.indexReport(report, metrics);
        } catch (Exception e) {
            log.warn("RAG index report failed (non-fatal)", e);
        }
    }
    
    /**
     * 이번 달 리포트 생성 또는 업데이트.
     * year_month 기준으로 조회하여, 이번 달 리포트가 있으면 업데이트, 없으면 새로 생성.
     * 달이 바뀌면 새 리포트를 생성하고, 기존 '이번 달' 플래그는 해제한다.
     */
    @Transactional
    public MonthlyReportDTO generateOrUpdateCurrentMonthReport(Long userId, Long requestMonthlyBudget) throws Exception {
        LocalDate currentMonth = LocalDate.now();
        String yearMonth = currentMonth.format(YEAR_MONTH_FORMATTER);
        
        // 이번 달 거래 내역 조회
        List<TransactionDTO> transactions = transactionMapper.selectTransactionsByYearMonth(userId, yearMonth);
        
        // 통계 계산 (yearMonth 포함, last7Days = 오늘 기준 최근 7일)
        Map<String, Object> metrics = calculateMetrics(transactions, userId, yearMonth);
        
        // 예산: 요청값이 없거나 0이면 DB에서 이번 달 예산 조회
        Long monthlyBudget = (requestMonthlyBudget != null && requestMonthlyBudget > 0)
            ? requestMonthlyBudget
            : resolveMonthlyBudgetFromDb(userId, yearMonth);
        
        // LLM으로 분석 요약 생성 (Python AI 서버)
        List<Map<String, Object>> transactionMaps = convertToMapList(transactions);
        
        String llmSummary = pythonAIService.generateAnalysisSummary(transactionMaps, metrics, monthlyBudget);
        
        // 이번 달(year_month) 기준으로 기존 리포트 확인 (달이 바뀌면 새 리포트로 생성)
        MonthlyReportDTO existingReport = monthlyReportMapper.selectReportByYearMonth(userId, yearMonth);
        
        if (existingReport != null) {
            // 같은 달 리포트가 있으면 업데이트
            existingReport.setMetricsJson(objectMapper.writeValueAsString(metrics));
            existingReport.setLlmSummaryText(llmSummary);
            existingReport.setIsCurrentMonth(true);
            monthlyReportMapper.updateMonthlyReport(existingReport);
            try {
                ragService.indexReport(existingReport, metrics);
            } catch (Exception e) {
                log.warn("RAG index report failed (non-fatal)", e);
            }
            return existingReport;
        } else {
            // 새 달이면 기존 '이번 달' 플래그 해제 후 새 리포트 생성
            monthlyReportMapper.clearCurrentMonthFlag(userId);
            MonthlyReportDTO report = new MonthlyReportDTO();
            report.setYearMonth(yearMonth);
            report.setMetricsJson(objectMapper.writeValueAsString(metrics));
            report.setLlmSummaryText(llmSummary);
            report.setLlmModel("gemini-2.0-flash");
            report.setUserId(userId);
            report.setIsCurrentMonth(true);
            report.setCreatedAt(LocalDateTime.now());
            
            monthlyReportMapper.insertMonthlyReport(report);
            try {
                ragService.indexReport(report, metrics);
            } catch (Exception e) {
                log.warn("RAG index report failed (non-fatal)", e);
            }
            return report;
        }
    }

    /**
     * DB에서 해당 사용자·연월의 월 예산 조회. 없으면 null.
     */
    private Long resolveMonthlyBudgetFromDb(Long userId, String yearMonth) {
        if (userId == null || yearMonth == null || yearMonth.isBlank()) return null;
        String yyyyMm = yearMonth.replace("-", "").trim();
        if (yyyyMm.length() != 6) return null;
        try {
            BudgetDTO budget = budgetService.getBudgetByYearMonth(userId, yyyyMm);
            if (budget == null || budget.getTotalBudget() == null) return null;
            return budget.getTotalBudget().longValue();
        } catch (Exception e) {
            log.warn("Failed to resolve monthly budget from DB for user={}, yearMonth={}", userId, yyyyMm, e);
            return null;
        }
    }
    
    /**
     * TransactionDTO 리스트를 Map 리스트로 변환 (LLM 분석용)
     */
    private List<Map<String, Object>> convertToMapList(List<TransactionDTO> transactions) {
        return transactions.stream()
            .map(t -> {
                Map<String, Object> map = new HashMap<>();
                map.put("txId", t.getTxId());
                map.put("datetime", t.getTxDatetime() != null ? t.getTxDatetime().toString() : null);
                map.put("amount", t.getAmount());
                map.put("merchant", t.getMerchant());
                map.put("memo", t.getMemo());
                map.put("category", t.getCategoryName());
                map.put("paymentMethod", t.getMethodName());
                return map;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 통계 계산. AI는 계산하지 않고 문장화만 하므로, 여기서 필요한 지표를 모두 계산해 metrics_json에 넣는다.
     * - yearMonth: "YYYYMM"
     * - totalIncome, totalExpense: 지출은 집계 단계에서 양수로 합산
     * - categoryExpenses: { 카테고리명: 금액(양수) }
     * - categoryExpenseRatios: { 카테고리명: 0~1 비율 }
     * - last7Days: { total, series: [{ date: "YYYY-MM-DD", amount }] } (리포트 월이 현재월이면 오늘 기준 최근 7일, 과거월이면 해당 월 마지막 7일)
     */
    private Map<String, Object> calculateMetrics(List<TransactionDTO> transactions, Long userId, String yearMonth) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("yearMonth", yearMonth != null ? yearMonth : "");

        // amount가 양수이면 수입, 음수이면 지출. 지출은 집계 시 양수로 합산.
        BigDecimal totalIncome = transactions.stream()
            .map(TransactionDTO::getAmount)
            .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = transactions.stream()
            .map(TransactionDTO::getAmount)
            .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) < 0)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> categoryExpenses = transactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) < 0)
            .collect(Collectors.groupingBy(
                t -> t.getCategoryName() != null ? t.getCategoryName() : "미분류",
                Collectors.reducing(BigDecimal.ZERO,
                    t -> t.getAmount().abs(),
                    BigDecimal::add)
            ));

        // 카테고리별 지출 비율 (0~1). 없으면 LLM이 계산하지 않도록 여기서만 채움.
        Map<String, Double> categoryExpenseRatios = new LinkedHashMap<>();
        if (totalExpense.compareTo(BigDecimal.ZERO) > 0) {
            for (Map.Entry<String, BigDecimal> e : categoryExpenses.entrySet()) {
                double ratio = e.getValue().divide(totalExpense, 4, RoundingMode.HALF_UP).doubleValue();
                categoryExpenseRatios.put(e.getKey(), ratio);
            }
        }

        metrics.put("totalIncome", totalIncome);
        metrics.put("totalExpense", totalExpense);
        metrics.put("netAmount", totalIncome.subtract(totalExpense));
        metrics.put("transactionCount", transactions.size());
        metrics.put("categoryExpenses", categoryExpenses);
        metrics.put("categoryExpenseRatios", categoryExpenseRatios);
        metrics.put("last7Days", buildLast7Days(userId, yearMonth));
        Map<String, Long> categoryBudgetsMap = buildCategoryBudgetsMap(userId, yearMonth);
        if (!categoryBudgetsMap.isEmpty()) {
            metrics.put("categoryBudgets", categoryBudgetsMap);
        }

        return metrics;
    }

    /**
     * last7Days: 리포트 yearMonth가 현재월이면 오늘 기준 최근 7일, 과거월이면 해당 월 마지막 7일.
     * 반환: { total: number, series: [{ date: "YYYY-MM-DD", amount: number }] }
     */
    private Map<String, Object> buildLast7Days(Long userId, String yearMonth) {
        String yyyyMm = yearMonth != null ? yearMonth.replace("-", "").trim() : null;
        if (yyyyMm == null || yyyyMm.length() != 6 || userId == null) {
            return emptyLast7Days();
        }
        LocalDate endDate;
        LocalDate startDate;
        LocalDate now = LocalDate.now();
        String currentYm = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        if (yyyyMm.equals(currentYm)) {
            // 현재월: 오늘 기준 최근 7일 (오늘 포함)
            endDate = now;
            startDate = endDate.minusDays(6);
        } else {
            // 과거월: 해당 월 마지막 7일
            int y = Integer.parseInt(yyyyMm.substring(0, 4));
            int m = Integer.parseInt(yyyyMm.substring(4, 6));
            endDate = LocalDate.of(y, m, 1).plusMonths(1).minusDays(1);
            startDate = endDate.minusDays(6);
        }
        try {
            List<TransactionDTO> list = transactionMapper.selectTransactionsByDateRange(userId, startDate, endDate);
            // 날짜별 지출 합계 (양수)
            Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                byDate.put(d, BigDecimal.ZERO);
            }
            for (TransactionDTO t : list) {
                if (t.getAmount() == null || t.getAmount().compareTo(BigDecimal.ZERO) >= 0) continue;
                LocalDate d = t.getTxDatetime() != null ? t.getTxDatetime().toLocalDate() : null;
                if (d != null && byDate.containsKey(d)) {
                    byDate.put(d, byDate.get(d).add(t.getAmount().abs()));
                }
            }
            List<Map<String, Object>> series = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                BigDecimal amt = byDate.getOrDefault(d, BigDecimal.ZERO);
                total = total.add(amt);
                Map<String, Object> point = new HashMap<>();
                point.put("date", d.format(iso));
                point.put("amount", amt.intValue());
                series.add(point);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("total", total.intValue());
            result.put("series", series);
            return result;
        } catch (Exception e) {
            log.warn("buildLast7Days failed for userId={}, yearMonth={}", userId, yearMonth, e);
            return emptyLast7Days();
        }
    }

    private Map<String, Object> emptyLast7Days() {
        Map<String, Object> result = new HashMap<>();
        result.put("total", 0);
        result.put("series", new ArrayList<Map<String, Object>>());
        return result;
    }

    /** 카테고리별 예산 맵 (카테고리명 → 예산 금액). 위험도 분석 시 사용률 기반 판단에 사용. */
    private Map<String, Long> buildCategoryBudgetsMap(Long userId, String yearMonth) {
        String yyyyMm = yearMonth != null ? yearMonth.replace("-", "").trim() : null;
        if (userId == null || yyyyMm == null || yyyyMm.length() != 6) return new LinkedHashMap<>();
        try {
            List<CategoryBudgetDTO> list = budgetService.getCategoryBudgets(userId, yyyyMm);
            if (list == null || list.isEmpty()) return new LinkedHashMap<>();
            Map<String, Long> out = new LinkedHashMap<>();
            for (CategoryBudgetDTO cb : list) {
                String name = cb.getCategoryName() != null ? cb.getCategoryName() : "미분류";
                out.put(name, cb.getBudgetAmount() != null ? cb.getBudgetAmount().longValue() : 0L);
            }
            return out;
        } catch (Exception e) {
            log.warn("Failed to load category budgets for metrics userId={}, yearMonth={}", userId, yearMonth, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 질문 카드: Spring이 DB/메트릭 준비 후 Python AI Service만 호출. (RAG 또는 전체 맥락)
     */
    public ReportAskResponseDTO answerQuestion(Long userId, String yearMonth, String question) throws Exception {
        String yyyyMm = yearMonth != null ? yearMonth.replace("-", "") : null;
        MonthlyReportDTO report = monthlyReportMapper.selectReportByYearMonth(userId, yyyyMm);
        if (report == null) {
            throw new RuntimeException("해당 월의 리포트가 없습니다. 먼저 '이번 달 리포트 생성'을 진행해 주세요.");
        }

        if (ragService.isEnabled()) {
            String ragAnswer = ragService.answerWithRag(userId, report.getYearMonth(), question);
            if (ragAnswer != null && !ragAnswer.isBlank()) {
                return new ReportAskResponseDTO(ragAnswer);
            }
        }

        Map<String, Object> metrics = new HashMap<>();
        try {
            if (report.getMetricsJson() != null && !report.getMetricsJson().isEmpty()) {
                metrics = objectMapper.readValue(report.getMetricsJson(), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to parse metricsJson for ask", e);
        }

        String answer = pythonAIService.answerReportQuestion(
            question,
            metrics,
            report.getLlmSummaryText()
        );
        return new ReportAskResponseDTO(answer);
    }
}
