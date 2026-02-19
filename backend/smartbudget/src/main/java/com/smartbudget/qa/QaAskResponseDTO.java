package com.smartbudget.qa;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PR3: POST /api/qa/ask 응답.
 * - evidence.db: PR1 분석 결과 요약(사용한 것만)
 * - evidence.rag: 사용된 RAG 문서/스니펫 메타(가능하면)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QaAskResponseDTO {

    private String answerText;
    private String usedIntent;
    private EvidencePayload evidence;
    private String followUpQuestion;

    /** 하위 호환: answer 필드로 answerText 노출 */
    public String getAnswer() {
        return answerText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EvidencePayload {
        /** PR1 결과 요약(필요한 것만). e.g. monthSummary, categoryDelta, timebandDelta */
        private Map<String, Object> db;
        /** 사용된 RAG 메타. e.g. snippetCount, snippets */
        private Map<String, Object> rag;
    }
}
