package com.smartbudget.analysis;

/**
 * PR2: 사용자 질문 Intent 구분. Classifier 출력 및 needsDb/needsRag 매핑용.
 */
public enum Intent {

    /** 월 요약 (총 지출/수입/TOP 카테고리 등) */
    SUMMARY,

    /** 원인 분석 (왜 늘었는지, 급증 원인 등) */
    CAUSE_ANALYSIS,

    /** 기간 비교 (전월 대비, 지난달 vs 이번달) */
    COMPARISON,

    /** 예산 상태 (한도, 초과, 잔여) */
    BUDGET_STATUS,

    /** 패턴 심화 (야식, 시간대, 요일, 가맹점, 키워드) */
    PATTERN_DEEPDIVE,

    /** 조언/행동 (절약 팁, 어떻게 줄이지 등) */
    ADVICE_ACTION,

    /** 정의/도움말 (~이 뭐야, 뜻, 정의) */
    DEFINITION_HELP,

    /** 데이터 정정 요청 (틀렸어, 수정, 누락, 중복) */
    DATA_FIX;

    /** DB 분석 템플릿 필요 여부 */
    public boolean needsDb() {
        return this == SUMMARY || this == CAUSE_ANALYSIS || this == COMPARISON
            || this == BUDGET_STATUS || this == PATTERN_DEEPDIVE;
    }

    /** RAG/리포트 맥락 필요 여부 */
    public boolean needsRag() {
        return this == ADVICE_ACTION || this == DEFINITION_HELP;
    }

    public static Intent fromString(String s) {
        if (s == null || s.isBlank()) return null;
        String u = s.trim().toUpperCase().replace("-", "_");
        for (Intent v : values()) {
            if (v.name().equals(u)) return v;
        }
        return null;
    }
}
