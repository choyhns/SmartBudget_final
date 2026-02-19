package com.smartbudget.llm;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeminiService {
    
    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String GEMINI_FLASH_MODEL = "gemini-2.0-flash";
    
    public GeminiService(@Value("${gemini.api.key:}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
            .baseUrl(GEMINI_API_URL)
            .build();
    }
    
    /**
     * 영수증 이미지에서 텍스트 및 거래 정보 추출 (OCR)
     */
    public ReceiptOcrResult extractReceiptInfo(byte[] imageData, String mimeType) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Gemini API key is not configured.");
            return null;
        }
        
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageData);
            
            String prompt = """
                이 영수증 이미지를 분석해서 다음 정보를 JSON 형식으로 추출해주세요:
                
                {
                    "storeName": "가게명",
                    "storeAddress": "주소 (있으면)",
                    "date": "YYYY-MM-DD 형식 날짜",
                    "time": "HH:mm 형식 시간 (있으면)",
                    "totalAmount": 총금액(숫자만),
                    "paymentMethod": "결제수단 (카드/현금/등)",
                    "cardInfo": "카드 정보 (있으면)",
                    "items": [
                        {"name": "품목명", "quantity": 수량, "unitPrice": 단가, "amount": 금액}
                    ],
                    "rawText": "영수증 전체 텍스트"
                }
                
                정보를 추출할 수 없는 필드는 null로 설정해주세요.
                반드시 유효한 JSON만 출력하세요.
                """;
            
            String requestBody = buildVisionRequest(prompt, base64Image, mimeType);
            
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            String jsonResponse = parseGeminiResponse(response);
            return parseReceiptOcrResult(jsonResponse);
            
        } catch (Exception e) {
            log.error("Error extracting receipt info", e);
            return null;
        }
    }
    
    /**
     * 거래 내역 텍스트로부터 카테고리 자동 분류
     */
    public CategoryClassificationResult classifyTransaction(String merchant, String memo, List<String> availableCategories) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Gemini API key is not configured.");
            return new CategoryClassificationResult("미분류", 0.0, "API 키 미설정");
        }
        
        try {
            String categoriesStr = String.join(", ", availableCategories);
            
            String prompt = String.format("""
                다음 거래 정보를 분석하여 가장 적절한 카테고리를 선택해주세요.
                
                거래 정보:
                - 가맹점/상호명: %s
                - 메모/설명: %s
                
                선택 가능한 카테고리: %s
                
                다음 JSON 형식으로 응답해주세요:
                {
                    "category": "선택한 카테고리명",
                    "confidence": 0.0~1.0 사이 확신도,
                    "reason": "선택 이유 간단히"
                }
                
                반드시 유효한 JSON만 출력하세요.
                """, 
                merchant != null ? merchant : "없음",
                memo != null ? memo : "없음",
                categoriesStr);
            
            String requestBody = buildTextRequest(prompt);
            
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            String jsonResponse = parseGeminiResponse(response);
            return parseCategoryResult(jsonResponse);
            
        } catch (Exception e) {
            log.error("Error classifying transaction", e);
            return new CategoryClassificationResult("미분류", 0.0, "분류 실패: " + e.getMessage());
        }
    }
    
    /**
     * 카드/금융상품 추천
     */
    public String generateRecommendations(
            Map<String, Object> spendingPattern,
            List<Map<String, Object>> availableCards,
            List<Map<String, Object>> availableProducts) {
        
        if (apiKey == null || apiKey.isEmpty()) {
            return generateDefaultRecommendation(spendingPattern);
        }
        
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("다음은 사용자의 소비 패턴 데이터입니다.\n\n");
            prompt.append("=== 소비 패턴 ===\n");
            prompt.append(objectMapper.writeValueAsString(spendingPattern));
            prompt.append("\n\n");
            
            if (availableCards != null && !availableCards.isEmpty()) {
                prompt.append("=== 이용 가능한 카드 목록 ===\n");
                prompt.append(objectMapper.writeValueAsString(availableCards));
                prompt.append("\n\n");
            }
            
            if (availableProducts != null && !availableProducts.isEmpty()) {
                prompt.append("=== 이용 가능한 금융상품 목록 ===\n");
                prompt.append(objectMapper.writeValueAsString(availableProducts));
                prompt.append("\n\n");
            }
            
            prompt.append("""
                위 데이터를 바탕으로 다음을 포함한 추천을 작성해주세요:
                1. 소비 패턴에 맞는 최적의 카드 추천 (혜택 중심)
                2. 저축/투자 금융상품 추천
                3. 절약을 위한 구체적인 조언
                
                한국어로 300-500자 정도로 작성해주세요.
                """);
            
            String requestBody = buildTextRequest(prompt.toString());
            
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            return parseGeminiResponse(response);
            
        } catch (Exception e) {
            log.error("Error generating recommendations", e);
            return generateDefaultRecommendation(spendingPattern);
        }
    }
    
    /**
     * 이 카드가 사용자에게 적합한 이유를 소비 패턴·분석 기준으로 생성
     */
    public String generateCardSuitableReason(
            String cardName,
            String cardCompany,
            String cardBenefitsJson,
            Map<String, Object> spendingPattern,
            String monthlyReportSummary) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "이 카드의 혜택과 사용자의 상위 지출 카테고리를 매칭해 추천합니다.";
        }
        try {
            // spendingPattern에서 핵심만 추출 (너무 길면 400 발생 가능)
            Map<String, Object> compactPattern = new HashMap<>();
            compactPattern.put("yearMonth", spendingPattern.getOrDefault("yearMonth", ""));
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> categoryExpenses = (Map<String, BigDecimal>) spendingPattern.get("categoryExpenses");
            if (categoryExpenses != null && !categoryExpenses.isEmpty()) {
                // 상위 5개 카테고리만
                List<Map.Entry<String, BigDecimal>> top5 = categoryExpenses.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(5)
                    .collect(java.util.stream.Collectors.toList());
                Map<String, BigDecimal> top5Map = new HashMap<>();
                for (Map.Entry<String, BigDecimal> e : top5) {
                    top5Map.put(e.getKey(), e.getValue());
                }
                compactPattern.put("categoryExpenses", top5Map);
            }
            compactPattern.put("topCategory", spendingPattern.getOrDefault("topCategory", "없음"));
            BigDecimal totalExpense = (BigDecimal) spendingPattern.getOrDefault("totalExpense", java.math.BigDecimal.ZERO);
            compactPattern.put("totalExpense", totalExpense);

            String spendingStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(compactPattern);
            String prompt = String.format("""
                당신은 카드 추천 AI 어시스턴트입니다.
                [카드 정보]
                - 카드명: %s (%s)
                - 혜택 요약: %s

                [사용자 소비 패턴]
                %s

                [월별 분석 요약(있으면)]
                %s

                위 정보를 바탕으로 "이 카드가 이 사용자에게 왜 적합한지"를 정확히 3줄로 요약해주세요.
                1줄: 사용자의 상위 지출 카테고리(소비 패턴에 나온 카테고리)를 언급하세요.
                2줄: 이 카드의 혜택(요약/benefits) 중 그 카테고리와 매칭되는 구체적인 혜택(예: 교통 15%% 캐시백, 식비 10%% 등)을 적어주세요.
                3줄: 실질적인 도움이나 추가 혜택을 언급하세요.
                "상위 지출 카테고리 OO·OO와 이 카드의 OO 혜택을 매칭해 추천했습니다" 식으로 구체적으로 작성해주세요. 이모지 사용 가능.
                """,
                cardName != null ? cardName : "",
                cardCompany != null ? cardCompany : "",
                cardBenefitsJson != null ? cardBenefitsJson : "없음",
                spendingStr,
                monthlyReportSummary != null && !monthlyReportSummary.isBlank() ? monthlyReportSummary : "없음");

            String requestBody = buildTextRequest(prompt);
            if (log.isDebugEnabled()) {
                log.debug("Gemini 요청 본문 길이: {} bytes", requestBody.length());
            }
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseGeminiResponse(response);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.warn("카드 적합 이유 생성 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e; // RecommendationService에서 템플릿 사용하도록 예외 전파
        } catch (Exception e) {
            log.warn("카드 적합 이유 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException(e); // RecommendationService에서 템플릿 사용하도록 예외 전파
        }
    }

    /**
     * 여러 카드에 대한 적합 이유를 한 번의 Gemini 호출로 생성 (배치 처리)
     * @param cardsInfo 카드 정보 리스트 (각 Map에는 "name", "company", "benefitsJson" 포함)
     * @param spendingPattern 소비 패턴 Map
     * @param monthlyReportSummary 월별 분석 요약
     * @return 각 카드에 대한 적합 이유 리스트 (순서는 cardsInfo와 동일)
     */
    public List<String> generateCardSuitableReasonsBatch(
            List<Map<String, Object>> cardsInfo,
            Map<String, Object> spendingPattern,
            String monthlyReportSummary) {
        if (apiKey == null || apiKey.isEmpty()) {
            return cardsInfo.stream()
                .map(c -> "이 카드의 혜택과 사용자의 상위 지출 카테고리를 매칭해 추천합니다.")
                .collect(java.util.stream.Collectors.toList());
        }
        if (cardsInfo == null || cardsInfo.isEmpty()) {
            return List.of();
        }
        try {
            // spendingPattern에서 핵심만 추출
            Map<String, Object> compactPattern = new HashMap<>();
            compactPattern.put("yearMonth", spendingPattern.getOrDefault("yearMonth", ""));
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> categoryExpenses = (Map<String, BigDecimal>) spendingPattern.get("categoryExpenses");
            if (categoryExpenses != null && !categoryExpenses.isEmpty()) {
                // 상위 5개 카테고리만
                List<Map.Entry<String, BigDecimal>> top5 = categoryExpenses.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(5)
                    .collect(java.util.stream.Collectors.toList());
                Map<String, BigDecimal> top5Map = new HashMap<>();
                for (Map.Entry<String, BigDecimal> e : top5) {
                    top5Map.put(e.getKey(), e.getValue());
                }
                compactPattern.put("categoryExpenses", top5Map);
            }
            compactPattern.put("topCategory", spendingPattern.getOrDefault("topCategory", "없음"));
            BigDecimal totalExpense = (BigDecimal) spendingPattern.getOrDefault("totalExpense", java.math.BigDecimal.ZERO);
            compactPattern.put("totalExpense", totalExpense);

            String spendingStr = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(compactPattern);
            
            // 여러 카드 정보를 문자열로 구성
            StringBuilder cardsInfoStr = new StringBuilder();
            for (int i = 0; i < cardsInfo.size(); i++) {
                Map<String, Object> card = cardsInfo.get(i);
                cardsInfoStr.append(String.format("""
                    [카드 %d]
                    - 카드명: %s (%s)
                    - 혜택 요약: %s
                    
                    """,
                    i + 1,
                    card.getOrDefault("name", ""),
                    card.getOrDefault("company", ""),
                    card.getOrDefault("benefitsJson", "없음")));
            }
            
            String prompt = String.format("""
                당신은 카드 추천 AI 어시스턴트입니다.
                
                [카드 정보 목록]
                %s
                
                [사용자 소비 패턴]
                %s
                
                [월별 분석 요약(있으면)]
                %s
                
                위 정보를 바탕으로 각 카드가 이 사용자에게 왜 적합한지 설명해주세요.
                각 카드에 대해 반드시 다음을 포함해주세요:
                1. 먼저 3줄로 주요 근거를 요약해주세요 (각 줄은 한 문장으로)
                2. 그 다음 "---" 구분선을 넣어주세요
                3. 그 다음 상세 설명을 작성해주세요:
                   - 사용자의 상위 지출 카테고리(소비 패턴에 나온 카테고리)를 언급
                   - 이 카드의 혜택 중 그 카테고리와 매칭되는 구체적인 혜택(예: 교통 15%% 캐시백, 식비 10%% 등)을 명시
                   - 식비의 경우 "외식", "배달", "카페", "음식점" 등 세부 카테고리도 언급
                
                응답 형식:
                [카드 1]
                (3줄 요약)
                
                ---
                
                (상세 설명)
                
                [카드 2]
                (3줄 요약)
                
                ---
                
                (상세 설명)
                
                [카드 3]
                (3줄 요약)
                
                ---
                
                (상세 설명)
                
                이모지 사용 가능합니다.
                """,
                cardsInfoStr.toString(),
                spendingStr,
                monthlyReportSummary != null && !monthlyReportSummary.isBlank() ? monthlyReportSummary : "없음");

            String requestBody = buildTextRequest(prompt);
            if (log.isDebugEnabled()) {
                log.debug("배치 Gemini 요청 본문 길이: {} bytes", requestBody.length());
            }
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            String fullResponse = parseGeminiResponse(response);
            return parseBatchReasons(fullResponse, cardsInfo.size());
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.warn("배치 카드 적합 이유 생성 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.warn("배치 카드 적합 이유 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 배치 응답을 파싱하여 각 카드별 이유 리스트로 변환
     */
    private List<String> parseBatchReasons(String fullResponse, int expectedCount) {
        List<String> reasons = new java.util.ArrayList<>();
        if (fullResponse == null || fullResponse.isBlank()) {
            return reasons;
        }
        
        // "[카드 N]" 패턴으로 분리
        String[] parts = fullResponse.split("\\[카드\\s+\\d+\\]");
        for (int i = 1; i < parts.length && reasons.size() < expectedCount; i++) {
            String cardReason = parts[i].trim();
            if (!cardReason.isBlank()) {
                reasons.add(cardReason);
            }
        }
        
        // 파싱 실패 시 전체 응답을 첫 번째 카드로 사용하고 나머지는 기본 메시지
        if (reasons.isEmpty() && !fullResponse.isBlank()) {
            reasons.add(fullResponse.trim());
            for (int i = 1; i < expectedCount; i++) {
                reasons.add("이 카드의 혜택과 사용자의 상위 지출 카테고리를 매칭해 추천합니다.");
            }
        } else if (reasons.size() < expectedCount) {
            // 부족한 만큼 기본 메시지 추가
            for (int i = reasons.size(); i < expectedCount; i++) {
                reasons.add("이 카드의 혜택과 사용자의 상위 지출 카테고리를 매칭해 추천합니다.");
            }
        }
        
        return reasons;
    }

    /**
     * 검색 스니펫에서 카드 혜택만 추려서 항목별로 정리 (요약 X, 항목 나열).
     */
    public String extractCardBenefitsFromSnippets(String cardName, String snippetsText) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        if (snippetsText == null || snippetsText.isBlank()) {
            return null;
        }
        try {
            String prompt = String.format("""
                다음은 카드 「%s」에 대한 웹 검색 결과 스니펫입니다.
                
                [검색 결과]
                %s
                
                위 내용에서 이 카드의 혜택만 추려서 항목별로 정리해 주세요.
                - 요약하지 말고, 혜택 항목을 빠짐없이 나열하세요.
                - 각 항목은 한 줄로, 예: "• 교통 15%% 캐시백", "• 통신비 5,000원 할인" 형식으로 작성하세요.
                - 검색 결과에 없는 내용은 만들지 마세요.
                - 마지막에 "※ 정확한 혜택·조건은 신한카드 공식 채널에서 확인해주세요." 한 줄만 추가하세요.
                """,
                cardName != null ? cardName : "",
                snippetsText);

            String requestBody = buildTextRequest(prompt);
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.warn("검색 스니펫에서 혜택 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 거래 내역 데이터를 분석하여 LLM 요약 텍스트 생성
     */
    public String generateAnalysisSummary(
            List<Map<String, Object>> transactions,
            Map<String, Object> metrics,
            Long monthlyBudget) {
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Gemini API key is not configured. Returning default summary.");
            return generateDefaultSummary(metrics, monthlyBudget);
        }
        
        try {
            String prompt = buildAnalysisPrompt(transactions, metrics, monthlyBudget);
            String requestBody = buildTextRequest(prompt);
            
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            return parseGeminiResponse(response);
            
        } catch (WebClientResponseException e) {
            log.error("Gemini API error: {}", e.getResponseBodyAsString(), e);
            return generateDefaultSummary(metrics, monthlyBudget);
        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            return generateDefaultSummary(metrics, monthlyBudget);
        }
    }
    
    // ========== Private Helper Methods ==========
    
    private String buildTextRequest(String prompt) {
        try {
            Map<String, Object> parts = new HashMap<>();
            parts.put("text", prompt != null ? prompt : "");
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(parts));
            Map<String, Object> body = new HashMap<>();
            body.put("contents", List.of(content));
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("Error building text request", e);
            return "{\"contents\":[{\"parts\":[{\"text\":\"Error\"}]}]}";
        }
    }
    
    private String buildVisionRequest(String prompt, String base64Image, String mimeType) {
        try {
            String escapedPrompt = prompt.replace("\\", "\\\\")
                                         .replace("\"", "\\\"")
                                         .replace("\n", "\\n")
                                         .replace("\r", "\\r")
                                         .replace("\t", "\\t");
            return String.format("""
                {
                    "contents": [{
                        "parts": [
                            {"text": "%s"},
                            {
                                "inline_data": {
                                    "mime_type": "%s",
                                    "data": "%s"
                                }
                            }
                        ]
                    }]
                }
                """, escapedPrompt, mimeType, base64Image);
        } catch (Exception e) {
            log.error("Error building vision request", e);
            return null;
        }
    }
    
    private String buildAnalysisPrompt(
            List<Map<String, Object>> transactions,
            Map<String, Object> metrics,
            Long monthlyBudget) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("다음은 사용자의 월별 소비 데이터입니다.\n\n");
        prompt.append("=== 거래 내역 ===\n");
        prompt.append("총 거래 건수: ").append(transactions.size()).append("건\n");
        prompt.append("총 수입: ").append(metrics.get("totalIncome")).append("원\n");
        prompt.append("총 지출: ").append(metrics.get("totalExpense")).append("원\n");
        prompt.append("월 예산: ").append(monthlyBudget != null ? monthlyBudget : "미설정").append("원\n\n");
        
        if (metrics.containsKey("categoryExpenses")) {
            prompt.append("=== 카테고리별 지출 ===\n");
            @SuppressWarnings("unchecked")
            Map<String, ?> categoryExpenses = (Map<String, ?>) metrics.get("categoryExpenses");
            categoryExpenses.forEach((category, amount) -> {
                prompt.append("- ").append(category).append(": ").append(amount).append("원\n");
            });
            prompt.append("\n");
        }
        
        prompt.append("위 데이터를 바탕으로 다음을 포함한 한국어 분석 요약을 작성해주세요:\n");
        prompt.append("1. 전반적인 소비 패턴 분석\n");
        prompt.append("2. 예산 대비 지출 평가\n");
        prompt.append("3. 주요 지출 카테고리 특징\n");
        prompt.append("4. 개선 가능한 소비 습관 및 절약 팁\n");
        prompt.append("5. 다음 달 예산 관리 권장사항\n\n");
        prompt.append("요약은 300-500자 정도로 작성해주세요.");
        
        return prompt.toString();
    }
    
    private String parseGeminiResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
            log.warn("Unexpected Gemini API response structure");
            return "분석 결과를 가져오는데 문제가 발생했습니다.";
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
            return "분석 결과를 파싱하는데 실패했습니다.";
        }
    }
    
    private ReceiptOcrResult parseReceiptOcrResult(String jsonResponse) {
        try {
            // JSON 코드 블록 제거
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();
            
            return objectMapper.readValue(cleanJson, ReceiptOcrResult.class);
        } catch (Exception e) {
            log.error("Error parsing receipt OCR result: {}", jsonResponse, e);
            ReceiptOcrResult result = new ReceiptOcrResult();
            result.setRawText(jsonResponse);
            return result;
        }
    }
    
    private CategoryClassificationResult parseCategoryResult(String jsonResponse) {
        try {
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();
            
            JsonNode root = objectMapper.readTree(cleanJson);
            return new CategoryClassificationResult(
                root.path("category").asText("미분류"),
                root.path("confidence").asDouble(0.0),
                root.path("reason").asText("")
            );
        } catch (Exception e) {
            log.error("Error parsing category result: {}", jsonResponse, e);
            return new CategoryClassificationResult("미분류", 0.0, "파싱 실패");
        }
    }
    
    private String generateDefaultSummary(Map<String, Object> metrics, Long monthlyBudget) {
        StringBuilder summary = new StringBuilder();
        Object totalExpenseObj = metrics.get("totalExpense");
        Object totalIncomeObj = metrics.get("totalIncome");
        
        summary.append("이번 달 총 수입은 ").append(totalIncomeObj).append("원, 총 지출은 ").append(totalExpenseObj).append("원입니다. ");
        
        if (monthlyBudget != null && totalExpenseObj instanceof Number) {
            long totalExpense = ((Number) totalExpenseObj).longValue();
            if (totalExpense > monthlyBudget) {
                summary.append("예산을 ").append(totalExpense - monthlyBudget).append("원 초과했습니다. ");
            } else {
                summary.append("예산 내에서 지출을 관리하고 있습니다. ");
            }
        }
        
        summary.append("규칙적인 예산 관리를 통해 더 나은 재정 상태를 유지하시기 바랍니다.");
        
        return summary.toString();
    }
    
    private String generateDefaultRecommendation(Map<String, Object> spendingPattern) {
        return "소비 패턴을 분석한 결과, 지출 관리가 필요합니다. " +
               "주요 지출 카테고리를 확인하고 불필요한 지출을 줄여보세요. " +
               "정기 저축을 통해 목표 달성을 향해 나아가시기 바랍니다.";
    }
    
    /**
     * 사용자 질문에 대해 리포트 기반으로 답변 생성 (질문 카드용)
     */
    public String answerReportQuestion(String question, Map<String, Object> metrics, String llmSummary) {
        if (apiKey == null || apiKey.isEmpty()) {
            return generateDefaultQaAnswer(question, metrics);
        }
        
        try {
            StringBuilder context = new StringBuilder();
            context.append("=== 기존 AI 분석 요약 ===\n").append(llmSummary != null ? llmSummary : "").append("\n\n");
            context.append("=== 월별 통계 ===\n");
            context.append("총 수입: ").append(metrics.get("totalIncome")).append("원\n");
            context.append("총 지출: ").append(metrics.get("totalExpense")).append("원\n");
            if (metrics.containsKey("categoryExpenses")) {
                context.append("카테고리별 지출: ").append(metrics.get("categoryExpenses")).append("\n");
            }
            
            String prompt = String.format("""
                당신은 사용자의 소비 리포트를 분석해 알려주는 AI 어시스턴트입니다.
                아래 [리포트 맥락]을 바탕으로 사용자의 [질문]에 친근하고 간결하게 답변해주세요.
                답변은 2~4문장, 150자 이내로 핵심만 전달하세요. 이모지 사용 가능.
                
                [리포트 맥락]
                %s
                
                [사용자 질문]
                %s
                
                [답변]
                """,
                context.toString(),
                question != null ? question : "");
            
            String requestBody = buildTextRequest(prompt);
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.error("Error answering report question", e);
            return generateDefaultQaAnswer(question, metrics);
        }
    }
    
    private String generateDefaultQaAnswer(String question, Map<String, Object> metrics) {
        Object exp = metrics.get("totalExpense");
        Object inc = metrics.get("totalIncome");
        return String.format("이번 달 총 지출은 %s원, 총 수입은 %s원입니다. " +
            "리포트를 생성한 뒤 질문해 주시면 더 자세히 답변드릴 수 있어요.",
            exp != null ? exp : "0", inc != null ? inc : "0");
    }

    /**
     * RAG: 검색된 청크만 컨텍스트로 사용해 질문에 답변.
     */
    public String answerFromRagContext(String question, List<String> chunkContents) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "임베딩/생성 API를 설정한 뒤 다시 시도해 주세요.";
        }
        if (chunkContents == null || chunkContents.isEmpty()) {
            return "검색된 리포트 내용이 없어 답변할 수 없어요. 해당 월 리포트를 생성했는지 확인해 주세요.";
        }
        try {
            String context = String.join("\n\n---\n\n", chunkContents);
            String prompt = String.format("""
                당신은 사용자의 소비 리포트를 분석해 알려주는 AI 어시스턴트입니다.
                아래 [검색된 리포트 내용]만을 바탕으로 사용자의 [질문]에 친근하고 간결하게 답변해주세요.
                답변은 2~4문장, 150자 이내로 핵심만 전달하세요. 이모지 사용 가능.

                [검색된 리포트 내용]
                %s

                [사용자 질문]
                %s

                [답변]
                """, context, question != null ? question : "");

            String requestBody = buildTextRequest(prompt);
            String response = webClient.post()
                .uri("/" + GEMINI_FLASH_MODEL + ":generateContent?key=" + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.error("Error answering from RAG context", e);
            return "답변 생성 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요.";
        }
    }
}
