package com.smartbudget.ml;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.util.List;

/**
 * Python OCR 서버 응답 DTO
 * ml-server(8000)는 snake_case(store_name, total_amount 등) 반환 → @JsonAlias로 수용
 */
@Data
public class OcrResult {
    private boolean success;
    private String error;

    // 추출된 정보 (Python snake_case 별칭 수용)
    @JsonAlias("store_name")
    private String storeName;
    @JsonAlias("store_address")
    private String storeAddress;
    private String date;          // yyyy-MM-dd 형식
    private String time;          // HH:mm:ss 형식
    @JsonAlias("total_amount")
    private Long totalAmount;
    @JsonAlias("payment_method")
    private String paymentMethod;
    @JsonAlias("card_info")
    private String cardInfo;

    // 개별 항목
    private List<OcrItem> items;

    // 원본 텍스트
    @JsonAlias("raw_text")
    private String rawText;
    
    // OCR 신뢰도
    private Double confidence;
    
    @Data
    public static class OcrItem {
        private String name;
        private Integer quantity;
        private Long unitPrice;
        private Long amount;
    }
}
