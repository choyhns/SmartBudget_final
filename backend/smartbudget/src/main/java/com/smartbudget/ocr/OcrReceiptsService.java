package com.smartbudget.ocr;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartbudget.ml.CategoryPrediction;
import com.smartbudget.ml.OcrResult;

import lombok.extern.slf4j.Slf4j;

/**
 * OCR_receipts 서버와 통신하는 서비스
 * - OCR: PaddleOCR로 텍스트 추출
 * - 카테고리 분류: ML 모델 또는 룰 기반 분류
 */
@Slf4j
@Service
public class OcrReceiptsService {
    
    @Value("${ocr.receipts.server.url:http://127.0.0.1:8000/receipts}")
    private String ocrReceiptsServerUrl;
    
    /** /process 첫 요청 시 PaddleOCR 로딩으로 2분 초과 가능 → 3분 */
    @Value("${ocr.receipts.server.timeout:180000}")
    private int timeout;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public OcrReceiptsService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 영수증 이미지 OCR 처리
     * OCR_receipts 서버의 /ocr 엔드포인트 호출 (multipart/form-data)
     */
    public OcrResult extractTextFromReceipt(byte[] imageData, String mimeType, String filename) {
        try {
            // multipart/form-data 생성
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 파일 파트
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + (filename != null ? filename : "receipt.jpg") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Type: " + (mimeType != null ? mimeType : "image/jpeg") + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(imageData);
            baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            
            byte[] multipartData = baos.toByteArray();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ocrReceiptsServerUrl + "/ocr"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofMillis(timeout))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartData))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseOcrResponse(response.body());
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
     * OCR + 카테고리 분류 한번에 처리
     * OCR_receipts 서버의 /process 엔드포인트 호출
     */
    public ProcessResult processReceipt(byte[] imageData, String mimeType, String filename) {
        String processUrl = ocrReceiptsServerUrl + "/process";
        try {
            log.info("[OCR] OCR_receipts /process 호출: {}", processUrl);
            // multipart/form-data 생성
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 파일 파트
            baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + (filename != null ? filename : "receipt.jpg") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Type: " + (mimeType != null ? mimeType : "image/jpeg") + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            baos.write(imageData);
            baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            
            byte[] multipartData = baos.toByteArray();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(processUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofMillis(timeout))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipartData))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[OCR] OCR_receipts /process 응답: status={}", response.statusCode());

            if (response.statusCode() == 200) {
                ProcessResult result = parseProcessResponse(response.body());
                OcrResult or = result.getOcrResult();
                log.info("[OCR] OCR_receipts 결과: merchant={}, total={}, category={}",
                    or != null ? or.getStoreName() : null,
                    or != null ? or.getTotalAmount() : null,
                    result.getClassification() != null ? result.getClassification().getCategory() : null);
                return result;
            } else {
                String body = response.body();
                log.error("Process API error: {} - {}", response.statusCode(), body);
                String detail = extractDetailFromErrorBody(body);
                String errMsg = detail != null ? detail : ("Process API 오류: " + response.statusCode());
                OcrResult ocrResult = createDefaultOcrResult(errMsg);
                CategoryPrediction prediction = createDefaultPrediction();
                return new ProcessResult(ocrResult, prediction, null);
            }
        } catch (Exception e) {
            log.error("Process 처리 중 오류 발생", e);
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = e.getClass().getSimpleName() + " (연결 실패 또는 8000/receipts 미기동)";
            } else {
                msg = "Process 처리 실패: " + msg;
            }
            OcrResult ocrResult = createDefaultOcrResult(msg);
            CategoryPrediction prediction = createDefaultPrediction();
            return new ProcessResult(ocrResult, prediction, null);
        }
    }
    
    /**
     * 카테고리 분류만 처리
     * OCR_receipts 서버의 /classify 엔드포인트 호출
     */
    public CategoryPrediction classifyTransaction(String merchant, List<String> items) {
        try {
            Map<String, Object> requestBody = Map.of(
                "merchant", merchant != null ? merchant : "",
                "items", items != null ? items : List.of()
            );
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ocrReceiptsServerUrl + "/classify"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseClassifyResponse(response.body());
            } else {
                log.error("분류 API error: {} - {}", response.statusCode(), response.body());
                return createDefaultPrediction();
            }
        } catch (Exception e) {
            log.error("카테고리 분류 중 오류 발생", e);
            return createDefaultPrediction();
        }
    }
    
    /**
     * 서버 상태 확인
     */
    public boolean isServerHealthy() {
        String healthUrl = ocrReceiptsServerUrl + "/health";
        try {
            log.info("[OCR] OCR_receipts health 체크 요청: {}", healthUrl);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() == 200;
            log.info("[OCR] OCR_receipts health 응답: {} -> {} (status={})", healthUrl, ok ? "OK" : "FAIL", response.statusCode());
            return ok;
        } catch (Exception e) {
            log.warn("[OCR] OCR_receipts health 체크 실패: {} - {}", healthUrl, e.getMessage());
            return false;
        }
    }
    
    /**
     * OCR 응답 파싱
     */
    private OcrResult parseOcrResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode parsedJson = root.path("parsed_json");
            
            OcrResult result = new OcrResult();
            result.setSuccess(true);
            result.setStoreName(parsedJson.path("merchant").asText(null));
            result.setDate(parsedJson.path("date").asText(null));
            result.setTime(parsedJson.path("datetime").asText(null));
            
            // total 파싱
            if (parsedJson.has("total")) {
                JsonNode totalNode = parsedJson.path("total");
                if (totalNode.isNumber()) {
                    result.setTotalAmount(totalNode.asLong());
                } else if (totalNode.isTextual()) {
                    try {
                        result.setTotalAmount(Long.parseLong(totalNode.asText().replaceAll("[^0-9]", "")));
                    } catch (Exception e) {
                        log.warn("Total amount 파싱 실패: {}", totalNode.asText());
                    }
                }
            }
            
            // items 파싱
            List<OcrResult.OcrItem> items = new ArrayList<>();
            if (parsedJson.has("items") && parsedJson.path("items").isArray()) {
                for (JsonNode itemNode : parsedJson.path("items")) {
                    OcrResult.OcrItem item = new OcrResult.OcrItem();
                    item.setName(itemNode.asText());
                    items.add(item);
                }
            }
            result.setItems(items);
            
            // raw_text는 raw_json에서 추출
            if (root.has("raw_json")) {
                JsonNode rawJson = root.path("raw_json");
                if (rawJson.has("raw_text")) {
                    result.setRawText(rawJson.path("raw_text").asText(""));
                }
            }
            
            result.setConfidence(parsedJson.path("total_confidence").asDouble(0.8));
            
            return result;
        } catch (Exception e) {
            log.error("OCR 응답 파싱 실패", e);
            return createDefaultOcrResult("응답 파싱 실패: " + e.getMessage());
        }
    }
    
    /**
     * Process 응답 파싱 (OCR + 분류)
     */
    private ProcessResult parseProcessResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            // OCR 결과 파싱
            OcrResult ocrResult = parseOcrResponse(responseBody);
            
            // parsed_json 추출 (원본 JSON 문자열로 저장)
            String parsedJson = null;
            if (root.has("parsed_json")) {
                parsedJson = objectMapper.writeValueAsString(root.path("parsed_json"));
            }
            
            // 분류 결과 파싱
            JsonNode classification = root.path("classification");
            CategoryPrediction prediction = new CategoryPrediction();
            prediction.setCategory(classification.path("category").asText("기타"));
            prediction.setConfidence(classification.path("confidence").asDouble(0.0));
            
            // topk 파싱
            List<CategoryPrediction.PredictionCandidate> topk = new ArrayList<>();
            if (classification.has("topk") && classification.path("topk").isArray()) {
                for (JsonNode topkNode : classification.path("topk")) {
                    CategoryPrediction.PredictionCandidate candidate = new CategoryPrediction.PredictionCandidate();
                    candidate.setCategory(topkNode.path("category").asText());
                    candidate.setConfidence(topkNode.path("score").asDouble(0.0));
                    topk.add(candidate);
                }
            }
            prediction.setTopPredictions(topk);
            prediction.setReason("OCR_receipts 분류 결과 (모델: " + classification.path("model_version").asText("unknown") + ")");
            
            return new ProcessResult(ocrResult, prediction, parsedJson);
        } catch (Exception e) {
            log.error("Process 응답 파싱 실패", e);
            OcrResult ocrResult = createDefaultOcrResult("응답 파싱 실패: " + e.getMessage());
            CategoryPrediction prediction = createDefaultPrediction();
            return new ProcessResult(ocrResult, prediction, null);
        }
    }
    
    /**
     * 분류 응답 파싱
     */
    private CategoryPrediction parseClassifyResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            CategoryPrediction prediction = new CategoryPrediction();
            prediction.setCategory(root.path("category").asText("기타"));
            prediction.setConfidence(root.path("confidence").asDouble(0.0));
            
            // topk 파싱
            List<CategoryPrediction.PredictionCandidate> topk = new ArrayList<>();
            if (root.has("topk") && root.path("topk").isArray()) {
                for (JsonNode topkNode : root.path("topk")) {
                    CategoryPrediction.PredictionCandidate candidate = new CategoryPrediction.PredictionCandidate();
                    candidate.setCategory(topkNode.path("category").asText());
                    candidate.setConfidence(topkNode.path("score").asDouble(0.0));
                    topk.add(candidate);
                }
            }
            prediction.setTopPredictions(topk);
            prediction.setReason("OCR_receipts 분류 결과 (모델: " + root.path("model_version").asText("unknown") + ")");
            
            return prediction;
        } catch (Exception e) {
            log.error("분류 응답 파싱 실패", e);
            return createDefaultPrediction();
        }
    }
    
    /** FastAPI 오류 응답 {"detail": "..."} 또는 {"detail": ["..."]} 에서 메시지 추출 */
    private String extractDetailFromErrorBody(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode detail = root.path("detail");
            if (detail.isTextual()) return detail.asText();
            if (detail.isArray() && detail.size() > 0) return detail.get(0).asText();
        } catch (Exception ignored) { }
        return null;
    }

    private OcrResult createDefaultOcrResult(String error) {
        OcrResult result = new OcrResult();
        result.setSuccess(false);
        result.setError(error != null ? error : "알 수 없는 오류");
        return result;
    }
    
    private CategoryPrediction createDefaultPrediction() {
        CategoryPrediction prediction = new CategoryPrediction();
        prediction.setCategory("기타");
        prediction.setConfidence(0.0);
        prediction.setReason("OCR_receipts 서버 연결 실패로 기본값 사용");
        return prediction;
    }
    
    /**
     * OCR + 분류 결과를 담는 클래스
     */
    public static class ProcessResult {
        private final OcrResult ocrResult;
        private final CategoryPrediction classification;
        private final String parsedJson; // OCR_receipts 서버의 parsed_json 원본
        
        public ProcessResult(OcrResult ocrResult, CategoryPrediction classification, String parsedJson) {
            this.ocrResult = ocrResult;
            this.classification = classification;
            this.parsedJson = parsedJson;
        }
        
        public OcrResult getOcrResult() {
            return ocrResult;
        }
        
        public CategoryPrediction getClassification() {
            return classification;
        }
        
        public String getParsedJson() {
            return parsedJson;
        }
    }
}
