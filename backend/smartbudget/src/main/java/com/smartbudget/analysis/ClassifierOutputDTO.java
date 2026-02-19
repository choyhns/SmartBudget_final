package com.smartbudget.analysis;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PR2: Classifier 출력 DTO.
 * - 슬롯이 비면 기본값으로 1차 분석 + followUpQuestion 1개만 제공하도록 설계.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ClassifierOutputDTO {

    private Intent intent;
    /** 0..1, 룰/LLM 신뢰도 */
    private double confidence;

    /** 기준 연월 (YYYYMM). 없으면 null → 백엔드에서 이번달 기본 */
    private String yearMonth;
    /** 비교 기준 연월 (YYYYMM). 없으면 null → 백엔드에서 전월 기본 */
    private String baselineYearMonth;

    private Long categoryId;
    /** timeband | dow | merchant | keyword */
    private String dimension;
    private List<String> keywords;

    private boolean needsDb;
    private boolean needsRag;

    /** 슬롯 부족 시 되묻기 1개 */
    private String followUpQuestion;

    public List<String> getKeywords() {
        return keywords == null ? Collections.emptyList() : keywords;
    }
}
