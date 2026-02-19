package com.smartbudget.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * PR1: yearMonth(YYYYMM) → 월 범위 변환 검증.
 */
class AnalysisDateUtilsTest {

    @Test
    void startOfMonth_returnsFirstDay() {
        assertThat(AnalysisDateUtils.startOfMonth("202501")).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(AnalysisDateUtils.startOfMonth("202512")).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(AnalysisDateUtils.startOfMonth("2025-01")).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void endOfMonthExclusive_returnsNextMonthFirstDay() {
        assertThat(AnalysisDateUtils.endOfMonthExclusive("202501")).isEqualTo(LocalDate.of(2025, 2, 1));
        assertThat(AnalysisDateUtils.endOfMonthExclusive("202512")).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void nullOrBlank_returnsNull() {
        assertThat(AnalysisDateUtils.startOfMonth(null)).isNull();
        assertThat(AnalysisDateUtils.startOfMonth("")).isNull();
        assertThat(AnalysisDateUtils.endOfMonthExclusive(null)).isNull();
    }
}
