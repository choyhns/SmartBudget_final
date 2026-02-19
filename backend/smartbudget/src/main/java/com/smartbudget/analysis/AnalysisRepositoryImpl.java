package com.smartbudget.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PR1: 9개 분석 템플릿 실행. 파라미터 통일 후 날짜 범위로 Mapper 호출.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AnalysisRepositoryImpl implements AnalysisRepository {

    private final AnalysisMapper analysisMapper;

    @Override
    public AnalysisTemplateDTO.MonthSummary monthSummary(AnalysisQueryParams params) {
        LocalDate start = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate end = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        if (start == null || end == null || params.getUserId() == null) {
            return emptyMonthSummary();
        }
        AnalysisTemplateDTO.MonthSummaryRow row = analysisMapper.selectMonthSummaryTotals(params.getUserId(), start, end);
        List<AnalysisTemplateDTO.CategoryTopRow> topN = analysisMapper.selectMonthSummaryCategoryTopN(
            params.getUserId(), start, end, params.getLimit());
        return AnalysisTemplateDTO.MonthSummary.builder()
            .totalExpense(nullSafe(row.getTotalExpense()))
            .totalIncome(nullSafe(row.getTotalIncome()))
            .txCount(row.getTxCount() != null ? row.getTxCount() : 0)
            .aov(nullSafe(row.getAov()))
            .byCategoryTopN(topN != null ? topN : Collections.emptyList())
            .build();
    }

    @Override
    public AnalysisTemplateDTO.MonthCompare monthCompare(AnalysisQueryParams params) {
        if (params.getUserId() == null || params.getYearMonth() == null || params.getBaselineYearMonth() == null) {
            return emptyMonthCompare();
        }
        LocalDate aStart = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate aEnd = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        LocalDate bStart = AnalysisDateUtils.startOfMonth(params.getBaselineYearMonth());
        LocalDate bEnd = AnalysisDateUtils.endOfMonthExclusive(params.getBaselineYearMonth());
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return emptyMonthCompare();
        }
        AnalysisTemplateDTO.PeriodTotalsRow periodA = analysisMapper.selectPeriodTotals(params.getUserId(), aStart, aEnd);
        AnalysisTemplateDTO.PeriodTotalsRow periodB = analysisMapper.selectPeriodTotals(params.getUserId(), bStart, bEnd);
        List<AnalysisTemplateDTO.CategoryDeltaRow> deltas = analysisMapper.selectCategoryDeltaTopN(
            params.getUserId(), aStart, aEnd, bStart, bEnd, params.getLimit());

        BigDecimal expA = nullSafe(periodA != null ? periodA.getTotalExpense() : null);
        BigDecimal expB = nullSafe(periodB != null ? periodB.getTotalExpense() : null);
        BigDecimal incA = nullSafe(periodA != null ? periodA.getTotalIncome() : null);
        BigDecimal incB = nullSafe(periodB != null ? periodB.getTotalIncome() : null);

        return AnalysisTemplateDTO.MonthCompare.builder()
            .periodA(toPeriodTotals(periodA))
            .periodB(toPeriodTotals(periodB))
            .deltaExpense(expA.subtract(expB))
            .deltaIncome(incA.subtract(incB))
            .byCategoryDeltaTopN(deltas != null ? deltas : Collections.emptyList())
            .build();
    }

    @Override
    public List<AnalysisTemplateDTO.CategoryDeltaRow> categoryDeltaTopN(AnalysisQueryParams params) {
        if (params.getUserId() == null || params.getYearMonth() == null || params.getBaselineYearMonth() == null) {
            return Collections.emptyList();
        }
        LocalDate curStart = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate curEnd = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        LocalDate prevStart = AnalysisDateUtils.startOfMonth(params.getBaselineYearMonth());
        LocalDate prevEnd = AnalysisDateUtils.endOfMonthExclusive(params.getBaselineYearMonth());
        if (curStart == null || curEnd == null || prevStart == null || prevEnd == null) {
            return Collections.emptyList();
        }
        List<AnalysisTemplateDTO.CategoryDeltaRow> list = analysisMapper.selectCategoryDeltaTopN(
            params.getUserId(), curStart, curEnd, prevStart, prevEnd, params.getLimit());
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public AnalysisTemplateDTO.TargetBreakdown targetBreakdown(AnalysisQueryParams params) {
        LocalDate start = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate end = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        if (start == null || end == null || params.getUserId() == null) {
            return AnalysisTemplateDTO.TargetBreakdown.builder().build();
        }
        Long catId = params.getCategoryId();
        int limit = params.getLimit();
        List<AnalysisTemplateDTO.MerchantTopRow> merchants = analysisMapper.selectTargetBreakdownMerchantTopN(
            params.getUserId(), start, end, catId, limit);
        List<AnalysisTemplateDTO.TimebandRow> timeband = analysisMapper.selectTargetBreakdownTimeband(
            params.getUserId(), start, end, catId);
        List<AnalysisTemplateDTO.DowRow> dow = analysisMapper.selectTargetBreakdownDow(
            params.getUserId(), start, end, catId);
        return AnalysisTemplateDTO.TargetBreakdown.builder()
            .merchantTopN(merchants != null ? merchants : Collections.emptyList())
            .timebandDist(timeband != null ? timeband : Collections.emptyList())
            .dowDist(dow != null ? dow : Collections.emptyList())
            .build();
    }

    @Override
    public AnalysisTemplateDTO.AovVsCountDecompose aovVsCountDecompose(AnalysisQueryParams params) {
        if (params.getUserId() == null || params.getYearMonth() == null || params.getBaselineYearMonth() == null) {
            return AnalysisTemplateDTO.AovVsCountDecompose.builder().build();
        }
        LocalDate curStart = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate curEnd = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        LocalDate prevStart = AnalysisDateUtils.startOfMonth(params.getBaselineYearMonth());
        LocalDate prevEnd = AnalysisDateUtils.endOfMonthExclusive(params.getBaselineYearMonth());
        if (curStart == null || curEnd == null || prevStart == null || prevEnd == null) {
            return AnalysisTemplateDTO.AovVsCountDecompose.builder().build();
        }
        AnalysisTemplateDTO.PeriodAovRow current = analysisMapper.selectPeriodAov(params.getUserId(), curStart, curEnd);
        AnalysisTemplateDTO.PeriodAovRow baseline = analysisMapper.selectPeriodAov(params.getUserId(), prevStart, prevEnd);
        if (current == null) current = new AnalysisTemplateDTO.PeriodAovRow(0, BigDecimal.ZERO, BigDecimal.ZERO);
        if (baseline == null) baseline = new AnalysisTemplateDTO.PeriodAovRow(0, BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal curTotal = nullSafe(current.getTotalAmount());
        BigDecimal baseTotal = nullSafe(baseline.getTotalAmount());
        int curCnt = current.getTxCount() != null ? current.getTxCount() : 0;
        int baseCnt = baseline.getTxCount() != null ? baseline.getTxCount() : 0;
        BigDecimal curAov = nullSafe(current.getAov());
        BigDecimal baseAov = nullSafe(baseline.getAov());

        BigDecimal deltaTotal = curTotal.subtract(baseTotal);
        // 근사: delta_from_count = baseAov * (curCnt - baseCnt), delta_from_aov = baseCnt * (curAov - baseAov)
        BigDecimal deltaFromCount = baseAov.multiply(BigDecimal.valueOf(curCnt - baseCnt));
        BigDecimal deltaFromAov = BigDecimal.valueOf(baseCnt).multiply(curAov.subtract(baseAov));

        return AnalysisTemplateDTO.AovVsCountDecompose.builder()
            .current(current)
            .baseline(baseline)
            .deltaTotal(deltaTotal)
            .deltaFromCount(deltaFromCount)
            .deltaFromAov(deltaFromAov)
            .build();
    }

    @Override
    public List<AnalysisTemplateDTO.TimebandDeltaRow> timebandDelta(AnalysisQueryParams params) {
        if (params.getUserId() == null || params.getYearMonth() == null || params.getBaselineYearMonth() == null) {
            return Collections.emptyList();
        }
        LocalDate curStart = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate curEnd = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        LocalDate prevStart = AnalysisDateUtils.startOfMonth(params.getBaselineYearMonth());
        LocalDate prevEnd = AnalysisDateUtils.endOfMonthExclusive(params.getBaselineYearMonth());
        if (curStart == null || curEnd == null || prevStart == null || prevEnd == null) {
            return Collections.emptyList();
        }
        List<AnalysisTemplateDTO.TimebandDeltaRow> list = analysisMapper.selectTimebandDelta(
            params.getUserId(), curStart, curEnd, prevStart, prevEnd, params.getCategoryId());
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<AnalysisTemplateDTO.DowDeltaRow> dowDelta(AnalysisQueryParams params) {
        if (params.getUserId() == null || params.getYearMonth() == null || params.getBaselineYearMonth() == null) {
            return Collections.emptyList();
        }
        LocalDate curStart = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate curEnd = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        LocalDate prevStart = AnalysisDateUtils.startOfMonth(params.getBaselineYearMonth());
        LocalDate prevEnd = AnalysisDateUtils.endOfMonthExclusive(params.getBaselineYearMonth());
        if (curStart == null || curEnd == null || prevStart == null || prevEnd == null) {
            return Collections.emptyList();
        }
        List<AnalysisTemplateDTO.DowDeltaRow> list = analysisMapper.selectDowDelta(
            params.getUserId(), curStart, curEnd, prevStart, prevEnd, params.getCategoryId());
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<AnalysisTemplateDTO.MerchantTopRow> topMerchants(AnalysisQueryParams params) {
        LocalDate start = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate end = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        if (start == null || end == null || params.getUserId() == null) {
            return Collections.emptyList();
        }
        List<AnalysisTemplateDTO.MerchantTopRow> list = analysisMapper.selectTopMerchants(
            params.getUserId(), start, end, params.getCategoryId(), params.getLimit());
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public List<AnalysisTemplateDTO.KeywordStatsRow> keywordStats(AnalysisQueryParams params) {
        if (params.getUserId() == null || params.getYearMonth() == null) {
            return Collections.emptyList();
        }
        List<String> keywords = params.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDate start = AnalysisDateUtils.startOfMonth(params.getYearMonth());
        LocalDate end = AnalysisDateUtils.endOfMonthExclusive(params.getYearMonth());
        if (start == null || end == null) {
            return Collections.emptyList();
        }
        List<AnalysisTemplateDTO.KeywordStatsRow> result = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            AnalysisTemplateDTO.KeywordStatsRow row = analysisMapper.selectKeywordStats(
                params.getUserId(), start, end, params.getCategoryId(), keyword.trim());
            if (row != null) {
                result.add(row);
            }
        }
        return result;
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static AnalysisTemplateDTO.MonthSummary emptyMonthSummary() {
        return AnalysisTemplateDTO.MonthSummary.builder()
            .totalExpense(BigDecimal.ZERO)
            .totalIncome(BigDecimal.ZERO)
            .txCount(0)
            .aov(BigDecimal.ZERO)
            .byCategoryTopN(Collections.emptyList())
            .build();
    }

    private static AnalysisTemplateDTO.MonthCompare emptyMonthCompare() {
        AnalysisTemplateDTO.PeriodTotals empty = new AnalysisTemplateDTO.PeriodTotals(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        return AnalysisTemplateDTO.MonthCompare.builder()
            .periodA(empty)
            .periodB(empty)
            .deltaExpense(BigDecimal.ZERO)
            .deltaIncome(BigDecimal.ZERO)
            .byCategoryDeltaTopN(Collections.emptyList())
            .build();
    }

    private static AnalysisTemplateDTO.PeriodTotals toPeriodTotals(AnalysisTemplateDTO.PeriodTotalsRow row) {
        if (row == null) {
            return new AnalysisTemplateDTO.PeriodTotals(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        return new AnalysisTemplateDTO.PeriodTotals(
            nullSafe(row.getTotalExpense()),
            nullSafe(row.getTotalIncome()),
            row.getTxCount() != null ? row.getTxCount() : 0
        );
    }
}
