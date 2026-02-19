package com.smartbudget.monthlyreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbudget.budget.BudgetService;
import com.smartbudget.llm.PythonAIService;
import com.smartbudget.rag.RagService;
import com.smartbudget.transaction.TransactionDTO;
import com.smartbudget.transaction.TransactionMapper;

/**
 * monthly report metrics_json 구조 검증: yearMonth, categoryExpenseRatios, last7Days(total, series), totalExpense 양수.
 */
@ExtendWith(MockitoExtension.class)
class MonthlyReportServiceTest {

    @Mock
    private MonthlyReportMapper monthlyReportMapper;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private PythonAIService pythonAIService;

    @Mock
    private RagService ragService;

    @Mock
    private BudgetService budgetService;

    @InjectMocks
    private MonthlyReportService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        doReturn(null).when(monthlyReportMapper).selectReportByYearMonth(anyLong(), any());
        doReturn("AI summary").when(pythonAIService).generateAnalysisSummary(any(), any(), any());
        doReturn(false).when(ragService).isEnabled();
        doNothing().when(ragService).indexReport(any(), any());
    }

    @Test
    void generateOrUpdateCurrentMonthReport_metricsJson_containsRequiredFields() throws Exception {
        String yearMonth = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        TransactionDTO tx = new TransactionDTO();
        tx.setTxId(1L);
        tx.setAmount(new BigDecimal("-50000"));
        tx.setTxDatetime(LocalDateTime.now());
        tx.setCategoryName("식비");
        doReturn(List.of(tx)).when(transactionMapper).selectTransactionsByYearMonth(eq(1L), eq(yearMonth));
        doReturn(Collections.emptyList()).when(transactionMapper)
            .selectTransactionsByDateRange(eq(1L), any(LocalDate.class), any(LocalDate.class));
        doReturn(Collections.emptyList()).when(budgetService).getCategoryBudgets(eq(1L), eq(yearMonth));
        doReturn(null).when(budgetService).getBudgetByYearMonth(eq(1L), eq(yearMonth));

        service.generateOrUpdateCurrentMonthReport(1L, null);

        ArgumentCaptor<MonthlyReportDTO> reportCaptor = ArgumentCaptor.forClass(MonthlyReportDTO.class);
        verify(monthlyReportMapper).insertMonthlyReport(reportCaptor.capture());
        MonthlyReportDTO saved = reportCaptor.getValue();
        assertThat(saved.getMetricsJson()).isNotBlank();

        Map<String, Object> metrics = objectMapper.readValue(
            saved.getMetricsJson(),
            new TypeReference<Map<String, Object>>() {}
        );
        assertThat(metrics).containsKey("yearMonth");
        assertThat(metrics.get("yearMonth")).isEqualTo(yearMonth);
        assertThat(metrics).containsKey("categoryExpenseRatios");
        assertThat(metrics.get("categoryExpenseRatios")).isInstanceOf(Map.class);
        assertThat(metrics).containsKey("last7Days");
        @SuppressWarnings("unchecked")
        Map<String, Object> last7 = (Map<String, Object>) metrics.get("last7Days");
        assertThat(last7).containsKey("total");
        assertThat(last7).containsKey("series");
        assertThat(last7.get("series")).isInstanceOf(List.class);

        Object totalExpense = metrics.get("totalExpense");
        assertThat(totalExpense).isNotNull();
        assertThat(((Number) totalExpense).longValue()).isGreaterThanOrEqualTo(0);
    }
}
