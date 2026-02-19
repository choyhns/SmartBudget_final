package com.smartbudget.llm;

import lombok.Data;
import java.util.List;

@Data
public class ReceiptOcrResult {
    private String storeName;
    private String storeAddress;
    private String date;
    private String time;
    private Long totalAmount;
    private String paymentMethod;
    private String cardInfo;
    private List<ReceiptItem> items;
    private String rawText;
    
    @Data
    public static class ReceiptItem {
        private String name;
        private Integer quantity;
        private Long unitPrice;
        private Long amount;
    }
}
