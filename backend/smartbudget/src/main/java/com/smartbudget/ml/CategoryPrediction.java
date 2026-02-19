package com.smartbudget.ml;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.util.List;

/**
 * Python ML 카테고리 분류 결과 DTO
 * ml-server(8000)는 snake_case(top_predictions) 반환 → @JsonAlias로 수용
 */
@Data
public class CategoryPrediction {
    // 예측된 카테고리
    private String category;

    // 신뢰도 (0.0 ~ 1.0)
    private Double confidence;

    // 분류 이유/근거
    private String reason;

    // 상위 N개 예측 결과 (옵션)
    @JsonAlias("top_predictions")
    private List<PredictionCandidate> topPredictions;
    
    @Data
    public static class PredictionCandidate {
        private String category;
        private Double confidence;
    }
}
