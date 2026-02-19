package com.smartbudget.analysis;

import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 분석 쿼리 템플릿 공통 파라미터.
 * yearMonth → 월 범위(startDate ~ endDate)로 변환해 WHERE tx_datetime >= start AND < end 사용.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisQueryParams {

    private Long userId;
    /** YYYYMM (기준 기간) */
    private String yearMonth;
    /** YYYYMM (비교 기준 기간, optional) */
    private String baselineYearMonth;
    private Long categoryId;
    @Builder.Default
    private int limit = 10;
    /** keyword_stats용. 비어 있으면 빈 결과 반환 */
    private List<String> keywords;

    public List<String> getKeywords() {
        return keywords == null ? Collections.emptyList() : keywords;
    }
}
