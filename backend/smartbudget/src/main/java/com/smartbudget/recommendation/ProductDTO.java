package com.smartbudget.recommendation;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductDTO {
    private Long productId;
    private String type;           // 예금, 적금, 펀드 등
    private String name;
    private String bank;
    private BigDecimal rate;       // 금리
    private String conditionsJson; // 조건 정보 JSON
    private String[] tags;
}
