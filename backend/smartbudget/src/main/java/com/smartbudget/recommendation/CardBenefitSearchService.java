package com.smartbudget.recommendation;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbudget.llm.GeminiService;

import lombok.extern.slf4j.Slf4j;

/**
 * 카드 이름으로 구글 검색 후 스니펫을 가져와, Gemini로 혜택만 추려서 정리.
 * Google Custom Search API 키·검색엔진 ID가 설정된 경우에만 검색 사용.
 */
@Slf4j
@Service
public class CardBenefitSearchService {

    private static final String CUSTOM_SEARCH_URL = "https://www.googleapis.com/customsearch/v1";

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${google.customsearch.api.key:}")
    private String apiKey;

    @Value("${google.customsearch.cx:}")
    private String searchEngineId;

    @Autowired(required = false)
    private GeminiService geminiService;

    /** 검색 API 사용 가능 여부 */
    public boolean isSearchConfigured() {
        return apiKey != null && !apiKey.isBlank() && searchEngineId != null && !searchEngineId.isBlank();
    }

    /**
     * 카드명으로 검색한 뒤 스니펫을 Gemini에 넘겨 혜택만 정리.
     * 실패 시 null 반환 (호출 측에서 JSON 등 폴백 사용).
     */
    public String fetchBenefitsFromWeb(String cardName) {
        if (!isSearchConfigured()) return null;
        try {
            String snippetsText = fetchRawSnippets(cardName);
            if (snippetsText == null || snippetsText.isBlank()) {
                return null;
            }
            
            if (geminiService != null) {
                String extracted = geminiService.extractCardBenefitsFromSnippets(cardName != null ? cardName : "", snippetsText);
                if (extracted != null && !extracted.isBlank()) return extracted;
            }
            return formatSnippetsAsBenefits(cardName, snippetsText);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 카드명으로 검색한 뒤 원시 스니펫 텍스트만 반환 (AI 정리 없음)
     */
    public String fetchRawSnippets(String cardName) {
        String query = (cardName != null ? cardName.trim() : "") + " 신한카드 혜택";
        return fetchRawSnippetsForQuery(query, 5);
    }

    /**
     * 임의의 검색 쿼리로 Google Custom Search 후 스니펫 텍스트만 반환.
     * 검색 + RAG 결합 시 쿼리(예: "신한카드 여행 혜택 카드 추천")로 검색해 evidence에 붙일 때 사용.
     */
    public String fetchRawSnippetsForQuery(String query, int num) {
        if (!isSearchConfigured()) return null;
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            int safeNum = Math.min(10, Math.max(1, num));
            String url = CUSTOM_SEARCH_URL + "?key=" + apiKey + "&cx=" + searchEngineId + "&q=" + encoded + "&num=" + safeNum;

            String responseBody = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            List<String> snippets = parseSnippets(responseBody);
            if (snippets.isEmpty()) return null;

            return String.join("\n\n", snippets);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseSnippets(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            if (!items.isArray()) return List.of();
            return java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .map(item -> {
                    String snippet = item.path("snippet").asText("");
                    String title = item.path("title").asText("");
                    if (snippet.isBlank() && title.isBlank()) return null;
                    if (snippet.isBlank()) return title;
                    return title + "\n" + snippet;
                })
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String formatSnippetsAsBenefits(String cardName, String snippetsText) {
        return "【" + (cardName != null ? cardName : "") + "】 검색 결과 요약\n\n" + snippetsText + "\n\n※ 정확한 혜택·조건은 신한카드 공식 채널에서 확인해주세요.";
    }
}
