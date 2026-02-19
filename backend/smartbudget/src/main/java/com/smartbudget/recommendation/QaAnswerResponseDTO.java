package com.smartbudget.recommendation;

import java.util.List;
import lombok.Data;

/**
 * Q&A 응답 DTO
 * - answer: 사용자에게 보여줄 답변(LLM/RAG 결과)
 * - sources: 어떤 데이터(카드/상품/월별리포트/거래)를 근거로 했는지(간단)
 */
@Data
public class QaAnswerResponseDTO {
  private String answer;
  private List<QaSourceDTO> sources;

  @Data
  public static class QaSourceDTO {
    private String type; // CARD | PRODUCT | MONTHLY_REPORT | TRANSACTIONS | RECOMMENDATION
    private String label;
  }
}

