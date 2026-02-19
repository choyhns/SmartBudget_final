package com.smartbudget.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 리포트 질문 카드 마스터. tag로 카테고리 구분.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionCard {

    private Long cardId;
    private String questionText;
    private String tag;
    private String categoryLabel;
    private Integer sortOrder;

    /** API 응답용. DB에는 embedding 저장하지만 응답에는 포함하지 않음 */
    public static QuestionCard of(Long cardId, String questionText, String tag, String categoryLabel, Integer sortOrder) {
        return new QuestionCard(cardId, questionText, tag, categoryLabel, sortOrder);
    }
}
