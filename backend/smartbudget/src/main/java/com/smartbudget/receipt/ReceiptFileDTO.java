package com.smartbudget.receipt;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReceiptFileDTO {
    private Long fileId;
    private String urlPath;
    private String ocrRawJson;      // OCR 원본 결과
    private String ocrParsedJson;   // 파싱된 결과
    private LocalDateTime createdAt;
    private Long userId;
    private Long txId;              // 연결된 거래 ID
    private String status;          // UPLOADED, OCR_DONE, CLASSIFIED, CONFIRMED, FAILED
}
