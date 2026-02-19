package com.smartbudget.recommendation;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import lombok.Data;

/**
 * Q&A 요청 DTO
 * - questionId: 선택지 id
 * - userId: 사용자 (없으면 1로 처리)
 * - yearMonth: yyyyMM (없으면 현재월)
 * - cardId / productId: 질문에 따라 선택된 아이템
 * - recommendedCardIds: 직접 입력(CUSTOM) 시 화면에 보이는 추천 카드 ID 목록 (맥락 제공)
 */
@Data
public class QaAnswerRequestDTO {
  private String questionId;
  private Long userId;
  private String yearMonth;
  private Long cardId;
  private Long productId;
  /** 직접 입력 질문 (questionId가 CUSTOM일 때 사용) */
  private String customQuestion;
  /** 직접 입력 시 추천된 카드 ID 목록 (배열로 3장 전부 전달) */
  @JsonAlias("recommended_card_ids")
  private List<Long> recommendedCardIds;
}

