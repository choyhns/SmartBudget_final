package com.smartbudget.receipt;

import com.smartbudget.llm.CategoryClassificationResult;
import com.smartbudget.llm.ReceiptOcrResult;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptProcessResult {
    private ReceiptFileDTO receipt;
    private ReceiptOcrResult ocrResult;
    private CategoryClassificationResult classification;
    private String message;
}
