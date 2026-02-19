package com.smartbudget.recommendation;

import lombok.Data;

/**
 * 선택지(질문) 목록 DTO
 * - 프론트에서 버튼/선택지로 노출
 */
@Data
public class QaOptionDTO {
  private String id;          // question id
  private String title;       // 사용자에게 보여줄 문장
  private String targetType;  // CARD | PRODUCT | RECOMMENDATIONS | NONE
  private boolean needsItemSelection; // 카드/상품 선택이 필요한지
}

