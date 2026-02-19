package com.smartbudget.recommendation;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RecommendationResultDTO {
    private Map<String, Object> spendingPattern;
    private String llmAnalysis;
    private List<RecommendationDTO> recommendations;
}
