package com.smartbudget.ml;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Python ML 서버와 통신하는 서비스
 * - OCR: Tesseract/EasyOCR 등으로 텍스트 추출
 * - 카테고리 분류: 학습된 ML 모델로 분류
 */
@Slf4j
@Service
public class PythonMLService {
    
    @Value("${ml.server.url:http://localhost:8000}")
    private String mlServerUrl;
    
    @Value("${ml.server.timeout:30000}")
    private int timeout;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public PythonMLService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 영수증 이미지 OCR 처리
     * Python 서버의 /api/ocr 엔드포인트 호출
     */
    public OcrResult extractTextFromReceipt(byte[] imageData, String mimeType) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageData);
            
            Map<String, Object> requestBody = Map.of(
                "image", base64Image,
                "mime_type", mimeType != null ? mimeType : "image/jpeg"
            );
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServerUrl + "/api/ocr"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), OcrResult.class);
            } else {
                log.error("OCR API error: {} - {}", response.statusCode(), response.body());
                return createDefaultOcrResult("OCR API 오류: " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("OCR 처리 중 오류 발생", e);
            return createDefaultOcrResult("OCR 처리 실패: " + e.getMessage());
        }
    }
    
    /**
     * 거래 내역 카테고리 분류
     * Python 서버의 /api/classify 엔드포인트 호출
     */
    public CategoryPrediction classifyTransaction(String merchant, String memo, List<String> availableCategories) {
        try {
            Map<String, Object> requestBody = Map.of(
                "merchant", merchant != null ? merchant : "",
                "memo", memo != null ? memo : "",
                "categories", availableCategories
            );
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServerUrl + "/api/classify"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), CategoryPrediction.class);
            } else {
                log.error("분류 API error: {} - {}", response.statusCode(), response.body());
                return createDefaultPrediction(availableCategories);
            }
        } catch (Exception e) {
            log.error("카테고리 분류 중 오류 발생", e);
            return createDefaultPrediction(availableCategories);
        }
    }
    
    /**
     * 배치 분류 - 여러 거래를 한 번에 분류
     */
    public List<CategoryPrediction> classifyTransactionsBatch(List<Map<String, String>> transactions, List<String> availableCategories) {
        try {
            Map<String, Object> requestBody = Map.of(
                "transactions", transactions,
                "categories", availableCategories
            );
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServerUrl + "/api/classify/batch"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(timeout * 3)) // 배치는 시간이 더 걸림
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<List<CategoryPrediction>>() {});
            } else {
                log.error("배치 분류 API error: {} - {}", response.statusCode(), response.body());
                return List.of();
            }
        } catch (Exception e) {
            log.error("배치 분류 중 오류 발생", e);
            return List.of();
        }
    }
    
    /**
     * ML 서버 상태 확인
     */
    public boolean isServerHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mlServerUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("ML 서버 상태 확인 실패: {}", e.getMessage());
            return false;
        }
    }
    
    private OcrResult createDefaultOcrResult(String error) {
        OcrResult result = new OcrResult();
        result.setSuccess(false);
        result.setError(error);
        return result;
    }
    
    private CategoryPrediction createDefaultPrediction(List<String> categories) {
        CategoryPrediction prediction = new CategoryPrediction();
        prediction.setCategory(categories.isEmpty() ? "기타" : categories.get(categories.size() - 1));
        prediction.setConfidence(0.0);
        prediction.setReason("ML 서버 연결 실패로 기본값 사용");
        return prediction;
    }
}
