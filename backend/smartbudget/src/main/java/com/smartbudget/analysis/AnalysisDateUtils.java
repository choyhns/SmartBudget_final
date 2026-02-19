package com.smartbudget.analysis;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * yearMonth(YYYYMM) → 월 범위 startDate(당월 1일), endDate(다음달 1일) 변환.
 * WHERE tx_datetime >= startDate AND tx_datetime < endDate
 */
public final class AnalysisDateUtils {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private AnalysisDateUtils() {}

    /** 해당 월 1일 00:00 (포함) */
    public static LocalDate startOfMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) return null;
        return YearMonth.parse(yearMonth.replace("-", "").trim(), YYYYMM).atDay(1);
    }

    /** 다음 달 1일 (미포함 상한) */
    public static LocalDate endOfMonthExclusive(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) return null;
        return YearMonth.parse(yearMonth.replace("-", "").trim(), YYYYMM).plusMonths(1).atDay(1);
    }
}
