package com.smartbudget.recommendation;

import lombok.Data;

@Data
public class CardDTO {
    private Long cardId;
    private String name;
    private String company;
    private String benefitsJson;      // 요약 혜택 JSON (카테고리 매칭 등에 사용)
    private String benefitsDetailText; // LLM 기반 상세 혜택 설명 텍스트
    private String monthlyRequirement; // 전월 실적(월 이용금액) 조건 요약
    private String imageUrl;    // 카드 이미지 URL
    private String link;        // 카드 상세/신청 페이지 URL
    private String[] tags;
}
