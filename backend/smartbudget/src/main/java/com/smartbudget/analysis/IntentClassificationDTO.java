package com.smartbudget.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 질문 Intent 분류 결과 (JSON 스키마).
 * - intent: CAUSE | COMPARISON | BUDGET | PATTERN | GENERAL
 * - yearMonth/categoryHint: 추출된 경우만 설정
 * - needsFollowUp: 기간/카테고리 없어 기본값 사용 시 true
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IntentClassificationDTO {

    public static final String INTENT_CAUSE = "CAUSE";
    public static final String INTENT_COMPARISON = "COMPARISON";
    public static final String INTENT_BUDGET = "BUDGET";
    public static final String INTENT_PATTERN = "PATTERN";
    public static final String INTENT_GENERAL = "GENERAL";

    /** CAUSE | COMPARISON | BUDGET | PATTERN | GENERAL */
    private String intent;

    /** 추출된 연월 (yyyyMM). 없으면 null */
    private String yearMonth;

    /** 추출된 카테고리 힌트 (예: 식비, 카페). 없으면 null */
    private String categoryHint;

    /** 기간/카테고리 미지정으로 기본값 사용 시 true → 답변 후 되묻기 1회 */
    private boolean needsFollowUp;
}
