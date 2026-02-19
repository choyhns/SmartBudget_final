package com.smartbudget.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryClassificationResult {
    private String category;
    private Double confidence;
    private String reason;
}
