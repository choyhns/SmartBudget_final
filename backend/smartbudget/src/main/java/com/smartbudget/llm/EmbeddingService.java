package com.smartbudget.llm;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Gemini Embedding API (embedContent) 기반 텍스트 임베딩.
 * RAG용 문서/질문 벡터 생성.
 */
@Slf4j
@Service
public class EmbeddingService {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String DEFAULT_MODEL = "text-embedding-004";
    private static final int OUTPUT_DIMENSIONALITY = 768;

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public EmbeddingService(
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${rag.embedding.model:" + DEFAULT_MODEL + "}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder().baseUrl(GEMINI_API_URL).build();
    }

    /**
     * 단일 텍스트 임베딩. taskType 미지정(일반).
     */
    public float[] embed(String text) {
        return embed(text, null);
    }

    /**
     * 단일 텍스트 임베딩.
     * @param taskType "RETRIEVAL_DOCUMENT" | "RETRIEVAL_QUERY" | null
     */
    public float[] embed(String text, String taskType) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Gemini API key not set; skipping embed.");
            return null;
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String body = buildEmbedRequest(text, taskType);
            String modelPath = model.startsWith("models/") ? model : "models/" + model;
            String res = webClient.post()
                    .uri("/" + modelPath + ":embedContent?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseEmbeddingResponse(res);
        } catch (Exception e) {
            log.error("Embedding failed for text length={}", text.length(), e);
            return null;
        }
    }

    /**
     * 여러 텍스트 배치 임베딩 (순차 호출). taskType = RETRIEVAL_DOCUMENT 권장.
     */
    public List<float[]> embedBatch(List<String> texts, String taskType) {
        List<float[]> out = new ArrayList<>();
        for (String t : texts) {
            float[] e = embed(t, taskType);
            out.add(e != null ? e : new float[0]);
        }
        return out;
    }

    private String buildEmbedRequest(String text, String taskType) {
        try {
            String escaped = text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            StringBuilder sb = new StringBuilder();
            sb.append("{\"model\":\"models/").append(model).append("\",");
            sb.append("\"content\":{\"parts\":[{\"text\":\"").append(escaped).append("\"}]}");
            if (taskType != null && !taskType.isEmpty()) {
                sb.append(",\"taskType\":\"").append(taskType).append("\"");
            }
            sb.append(",\"outputDimensionality\":").append(OUTPUT_DIMENSIONALITY).append("}");
            return sb.toString();
        } catch (Exception e) {
            log.error("Build embed request failed", e);
            return "{}";
        }
    }

    private float[] parseEmbeddingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode emb = root.path("embedding");
            if (emb.isMissingNode()) {
                emb = root.path("embeddings").path(0);
            }
            JsonNode vals = emb.path("values");
            if (!vals.isArray()) {
                return null;
            }
            int n = vals.size();
            float[] arr = new float[n];
            for (int i = 0; i < n; i++) {
                arr[i] = (float) vals.get(i).asDouble();
            }
            return arr;
        } catch (Exception e) {
            log.error("Parse embedding response failed: {}", response, e);
            return null;
        }
    }

    public int getOutputDimensionality() {
        return OUTPUT_DIMENSIONALITY;
    }
}
