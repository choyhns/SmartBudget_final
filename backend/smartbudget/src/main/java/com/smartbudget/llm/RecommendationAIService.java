package com.smartbudget.llm;

import java.math.BigDecimal;
import java.util.ArrayList;
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

/**
 * Python Recommendation AI 서버 호출
 * smartbudget/recommandation 서비스와 통신
 */
@Slf4j
@Service
public class RecommendationAIService {

    private final WebClient webClient;
    private final String recommendationServerUrl;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RecommendationAIService(
            @Value("${recommendation.ai.server.url:http://localhost:5001}") String recommendationServerUrl,
            @Value("${recommendation.ai.enabled:true}") boolean enabled) {
        this.recommendationServerUrl = recommendationServerUrl;
        this.enabled = enabled;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
            .baseUrl(recommendationServerUrl)
            .build();
    }

    /**
     * 단일 카드에 대한 적합 이유 생성
     */
    public String generateCardSuitableReason(
            String cardName,
            String cardCompany,
            String cardBenefitsJson,
            Map<String, Object> spendingPattern,
            String monthlyReportSummary) {
        
        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("cardName", cardName);
            requestBody.put("cardCompany", cardCompany);
            requestBody.put("cardBenefitsJson", cardBenefitsJson);
            requestBody.put("spendingPattern", spendingPattern);
            requestBody.put("monthlyReportSummary", monthlyReportSummary != null ? monthlyReportSummary : "");

            String response = webClient.post()
                .uri("/generate-card-suitable-reason")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("reason")) {
                return root.get("reason").asText();
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 여러 카드에 대한 적합 이유를 한 번에 생성 (배치)
     */
    public List<String> generateCardSuitableReasonsBatch(
            List<Map<String, Object>> cardsInfo,
            Map<String, Object> spendingPattern,
            String monthlyReportSummary) {
        
        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("cardsInfo", cardsInfo);
            requestBody.put("spendingPattern", spendingPattern);
            requestBody.put("monthlyReportSummary", monthlyReportSummary != null ? monthlyReportSummary : "");

            String response = webClient.post()
                .uri("/generate-card-suitable-reasons-batch")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("reasons")) {
                List<String> reasons = new ArrayList<>();
                JsonNode reasonsNode = root.get("reasons");
                if (reasonsNode.isArray()) {
                    for (JsonNode reasonNode : reasonsNode) {
                        String reason = reasonNode.asText();
                        if (reason != null && !reason.isBlank()) {
                            reasons.add(reason);
                        } else {
                            reasons.add(""); // 빈 문자열도 추가 (나중에 템플릿으로 대체)
                        }
                    }
                }
                log.info("Python 배치 AI 응답 파싱 완료: {}건 (예상: {}건)", reasons.size(), cardsInfo.size());
                return reasons;
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            log.warn("Python AI 서비스 응답에 'reasons' 필드가 없음: {}", response);
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * '왜 이 카드들이 추천됐는지' 질문에 대한 AI 상세 설명 (카드별 구체적 근거)
     */
    public String explainWhyRecommended(
            List<Map<String, Object>> recommendedCards,
            Map<String, Object> spendingPattern,
            String monthlyReportSummary) {

        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }
        if (recommendedCards == null || recommendedCards.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("recommendedCards", recommendedCards);
            requestBody.put("spendingPattern", spendingPattern != null ? spendingPattern : new HashMap<>());
            requestBody.put("monthlyReportSummary", monthlyReportSummary != null ? monthlyReportSummary : "");

            String response = webClient.post()
                .uri("/explain-why-recommended")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("answer")) {
                return root.get("answer").asText();
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * '이 카드의 연회비는 얼마야?' 질문에 대한 AI 답변
     */
    public String answerCardAnnualFee(String cardName, String cardCompany, String benefitsJson) {
        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("cardName", cardName != null ? cardName : "");
            requestBody.put("cardCompany", cardCompany != null ? cardCompany : "");
            requestBody.put("benefitsJson", benefitsJson != null ? benefitsJson : "");

            String response = webClient.post()
                .uri("/answer-card-annual-fee")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("answer")) {
                return root.get("answer").asText();
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * '전월 실적 채워야하는 카드인가?' 질문에 대한 AI 답변
     */
    public String answerCardMonthlyRequirement(String cardName, String cardCompany, String benefitsJson) {
        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("cardName", cardName != null ? cardName : "");
            requestBody.put("cardCompany", cardCompany != null ? cardCompany : "");
            requestBody.put("benefitsJson", benefitsJson != null ? benefitsJson : "");

            String response = webClient.post()
                .uri("/answer-card-monthly-requirement")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("answer")) {
                return root.get("answer").asText();
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 사용자 직접 입력 질문에 답변 (Google Search Grounding 사용)
     */
    public String answerCustomQuestion(
            String question,
            Map<String, Object> card,
            Map<String, Object> spendingPattern,
            String monthlyReportSummary) {
        
        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("question", question);
            if (card != null) {
                requestBody.put("card", card);
            }
            requestBody.put("spendingPattern", spendingPattern);
            requestBody.put("monthlyReportSummary", monthlyReportSummary != null ? monthlyReportSummary : "");

            String response = webClient.post()
                .uri("/answer-custom-question")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("answer")) {
                return root.get("answer").asText();
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 카드 자세한 혜택: 스니펫 있으면 스니펫 기반, 없으면 benefitsJson 기반으로 AI가 정리
     */
    public String getCardBenefitsDetail(String cardName, String cardCompany, String benefitsJson, String snippetsText) {
        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("cardName", cardName != null ? cardName : "");
            requestBody.put("cardCompany", cardCompany != null ? cardCompany : "");
            requestBody.put("benefitsJson", benefitsJson != null ? benefitsJson : "");
            requestBody.put("snippetsText", snippetsText != null ? snippetsText : "");

            String response = webClient.post()
                .uri("/get-card-benefits-detail")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("answer")) {
                return root.get("answer").asText();
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 검색 스니펫에서 카드 혜택만 추려서 정리
     */
    public String extractCardBenefitsFromSnippets(String cardName, String snippetsText) {
        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("cardName", cardName);
            requestBody.put("snippetsText", snippetsText);

            String response = webClient.post()
                .uri("/extract-card-benefits")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("benefits")) {
                JsonNode benefitsNode = root.get("benefits");
                if (benefitsNode.isNull()) {
                    return null;
                }
                return benefitsNode.asText();
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 리포트 맥락 기반 질문 답변
     */
    public String answerReportQuestion(String question, Map<String, Object> metrics, String llmSummary) {
        if (!enabled) {
            log.debug("Recommendation AI 서비스가 비활성화되어 있습니다.");
            return null;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("question", question);
            requestBody.put("spendingPattern", metrics);
            requestBody.put("monthlyReportSummary", llmSummary != null ? llmSummary : "");

            String response = webClient.post()
                .uri("/answer-custom-question")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("answer")) {
                return root.get("answer").asText();
            } else if (root.has("error")) {
                log.warn("Python AI 서비스 오류: {}", root.get("error").asText());
                return null;
            }
            return null;
        } catch (WebClientResponseException e) {
            log.warn("Python AI 서비스 호출 실패 (HTTP {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("Python AI 서비스 호출 실패: {}", e.getMessage());
            return null;
        }
    }
}
