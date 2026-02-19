package com.smartbudget.recommendation;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RecommendationDTO {
    private Long recId;
    private String yearMonth;
    private String recType;        // CARD, PRODUCT
    private Long itemId;           // card_id or product_id
    private BigDecimal score;      // 추천 점수
    private String reasonText;     // 추천 이유
    private LocalDateTime createdAt;
    private Long userId;
    
    // 조회용 추가 필드
    private String itemName;
    private String itemDetails;
}
