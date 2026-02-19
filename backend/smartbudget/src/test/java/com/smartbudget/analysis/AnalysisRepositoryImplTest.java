package com.smartbudget.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PR1: 분석 템플릿 Repository 단위 테스트. Mapper 호출 및 null/빈 결과 처리 검증.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisRepositoryImplTest {

    @Mock
    private AnalysisMapper analysisMapper;

    @InjectMocks
    private AnalysisRepositoryImpl repository;

    private static final Long USER_ID = 1L;
    private static final String YEAR_MONTH = "202501";
    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = LocalDate.of(2025, 2, 1);

    @BeforeEach
    void setUp() {
        when(analysisMapper.selectMonthSummaryTotals(eq(USER_ID), eq(START), eq(END)))
            .thenReturn(new AnalysisTemplateDTO.MonthSummaryRow(
                BigDecimal.valueOf(500000),
                BigDecimal.valueOf(800000),
                30,
                BigDecimal.valueOf(16666)
            ));
        when(analysisMapper.selectMonthSummaryCategoryTopN(eq(USER_ID), eq(START), eq(END), eq(10)))
            .thenReturn(List.of(
                new AnalysisTemplateDTO.CategoryTopRow(1L, "식비", BigDecimal.valueOf(200000))
            ));
    }

    @Test
    void monthSummary_callsMapperWithDateRange() {
        AnalysisQueryParams params = AnalysisQueryParams.builder()
            .userId(USER_ID)
            .yearMonth(YEAR_MONTH)
            .limit(10)
            .build();

        AnalysisTemplateDTO.MonthSummary result = repository.monthSummary(params);

        verify(analysisMapper).selectMonthSummaryTotals(USER_ID, START, END);
        verify(analysisMapper).selectMonthSummaryCategoryTopN(USER_ID, START, END, 10);
        assertThat(result.getTotalExpense()).isEqualByComparingTo(BigDecimal.valueOf(500000));
        assertThat(result.getTxCount()).isEqualTo(30);
        assertThat(result.getByCategoryTopN()).hasSize(1);
        assertThat(result.getByCategoryTopN().get(0).getCategoryName()).isEqualTo("식비");
    }

    @Test
    void monthSummary_nullYearMonth_returnsEmptySafeResult() {
        AnalysisQueryParams params = AnalysisQueryParams.builder()
            .userId(USER_ID)
            .yearMonth(null)
            .build();

        AnalysisTemplateDTO.MonthSummary result = repository.monthSummary(params);

        assertThat(result.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTxCount()).isEqualTo(0);
        assertThat(result.getByCategoryTopN()).isEmpty();
    }

    @Test
    void keywordStats_emptyKeywords_returnsEmptyList() {
        AnalysisQueryParams params = AnalysisQueryParams.builder()
            .userId(USER_ID)
            .yearMonth(YEAR_MONTH)
            .keywords(Collections.emptyList())
            .build();

        List<AnalysisTemplateDTO.KeywordStatsRow> result = repository.keywordStats(params);

        assertThat(result).isEmpty();
    }

    @Test
    void keywordStats_nullKeywords_returnsEmptyList() {
        AnalysisQueryParams params = AnalysisQueryParams.builder()
            .userId(USER_ID)
            .yearMonth(YEAR_MONTH)
            .build();

        List<AnalysisTemplateDTO.KeywordStatsRow> result = repository.keywordStats(params);

        assertThat(result).isEmpty();
    }
}
