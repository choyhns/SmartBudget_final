package com.smartbudget.llm;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartbudget.analysis.ClassifierOutputDTO;
import com.smartbudget.analysis.Intent;

import lombok.extern.slf4j.Slf4j;

/**
 * Python FastAPI AI 서버 호출 (임베딩, LLM).
 * RAG 질문 답변 시 Spring은 facts + user_id/year_month/top_k만 전달하고,
 * AI Service가 쿼리 임베딩·벡터 검색·프롬프트·LLM 호출을 담당.
 */
@Slf4j
@Service
public class PythonAIService {

    private final WebClient webClient;
    private final String pythonServerUrl;
    private final ObjectMapper objectMapper;

    public PythonAIService(@Value("${ml.server.url:http://localhost:8000}") String pythonServerUrl) {
        this.pythonServerUrl = pythonServerUrl;
        this.objectMapper = new ObjectMapper();
        // /api/embed/batch 응답(40개×768차원 등)이 기본 256KB 초과 → 16MB로 상향
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
        this.webClient = WebClient.builder()
            .baseUrl(pythonServerUrl)
            .exchangeStrategies(strategies)
            .build();
    }

    /**
     * 텍스트 임베딩 생성
     */
    public float[] embed(String text, String taskType) {
        try {
            String requestBody = buildEmbedRequest(text, taskType);
            String response = webClient.post()
                .uri("/api/embed")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseEmbeddingResponse(response);
        } catch (Exception e) {
            log.error("Python embedding failed", e);
            return null;
        }
    }

    /**
     * 배치 임베딩
     */
    public List<float[]> embedBatch(List<String> texts, String taskType) {
        try {
            String requestBody = buildEmbedBatchRequest(texts, taskType);
            String response = webClient.post()
                .uri("/api/embed/batch")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseEmbedBatchResponse(response);
        } catch (Exception e) {
            log.error("Python batch embedding failed", e);
            return List.of();
        }
    }

    /**
     * Chroma에 청크 한 건 인덱싱 (RAG 인덱싱 시 호출)
     */
    public void indexChunkToChroma(long chunkId, long reportId, long userId, String yearMonth, String content, float[] embedding) {
        try {
            String requestBody = buildRagIndexChunkRequest(chunkId, reportId, userId, yearMonth, content, embedding);
            webClient.post()
                .uri("/api/rag/index-chunk")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        } catch (Exception e) {
            log.error("Chroma index-chunk failed", e);
        }
    }

    /**
     * Chroma에서 해당 report_id 청크 전부 삭제
     */
    public void deleteReportChunksFromChroma(long reportId) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("report_id", reportId));
            webClient.post()
                .uri("/api/rag/delete-report-chunks")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        } catch (Exception e) {
            log.warn("Chroma delete-report-chunks failed (reportId={})", reportId, e);
        }
    }

    /**
     * 벡터 유사도 검색 (Chroma 또는 PostgreSQL)
     */
    public List<ReportChunk> searchChunks(long userId, String yearMonth, float[] queryEmbedding, int topK) {
        try {
            String requestBody = buildRagSearchRequest(userId, yearMonth, queryEmbedding, topK);
            String response = webClient.post()
                .uri("/api/rag/search")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseRagSearchResponse(response);
        } catch (Exception e) {
            log.error("Python RAG search failed", e);
            return List.of();
        }
    }

    /**
     * Spring이 준 facts + candidate_docs만 받아 LLM으로 답변 생성. (Python은 DB 접근하지 않음)
     *
     * @param question     사용자 질문
     * @param factsBlock   Spring이 DB/통계로 만든 Facts 블록 문자열
     * @param candidateDocs Spring이 검색한 후보 문서 목록 (각 항목은 "[doc_type]\ncontent" 형태 권장)
     * @return LLM 답변, 실패 시 null
     */
    public String answerWithContext(String question, String factsBlock, List<String> candidateDocs) {
        try {
            String requestBody = buildRagContextRequest(question, factsBlock, candidateDocs);
            String response = webClient.post()
                .uri("/api/llm/rag-answer")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseRagAnswerResponse(response);
        } catch (WebClientResponseException.NotFound e) {
            log.debug("Python rag-answer returned not found");
            return null;
        } catch (Exception e) {
            log.error("Python answerWithContext failed", e);
            return null;
        }
    }

    /**
     * RAG 질문 답변: Spring이 facts + user_id/year_month/top_k만 전달.
     * AI Service가 쿼리 임베딩 → 자체 벡터 검색 → 프롬프트 구성 → LLM 호출 후 답변 반환.
     */
    public String answerWithRagDelegated(long userId, String yearMonth, String question, String factsBlock, int topK) {
        try {
            String requestBody = buildRagDelegatedRequest(userId, yearMonth, question, factsBlock, topK);
            String response = webClient.post()
                .uri("/api/llm/rag-answer")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseRagAnswerResponse(response);
        } catch (WebClientResponseException.NotFound e) {
            log.debug("Python rag-answer returned not found");
            return null;
        } catch (Exception e) {
            log.error("Python answerWithRagDelegated failed", e);
            return null;
        }
    }

    /**
     * @deprecated Python이 DB/검색을 하던 구 방식. 대신 {@link #answerWithRagDelegated} 사용.
     */
    @Deprecated
    public String answerWithRag(long userId, String yearMonth, String question) {
        try {
            String requestBody = buildRagAnswerRequest(userId, yearMonth, question);
            String response = webClient.post()
                .uri("/api/rag/answer")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseRagAnswerResponse(response);
        } catch (WebClientResponseException.NotFound e) {
            log.debug("RAG search returned no results");
            return null;
        } catch (Exception e) {
            log.error("Python RAG answer failed", e);
            return null;
        }
    }

    /**
     * 리포트 분석 요약 생성
     */
    public String generateAnalysisSummary(
            List<Map<String, Object>> transactions,
            Map<String, Object> metrics,
            Long monthlyBudget) {
        try {
            String requestBody = buildAnalyzeRequest(transactions, metrics, monthlyBudget);
            String response = webClient.post()
                .uri("/api/llm/analyze")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseAnalyzeResponse(response);
        } catch (Exception e) {
            log.error("Python LLM analyze failed", e);
            return generateDefaultSummary(metrics, monthlyBudget);
        }
    }

    /**
     * 리포트 맥락 기반 질문 답변 (전체 맥락)
     */
    public String answerReportQuestion(String question, Map<String, Object> metrics, String llmSummary) {
        try {
            String requestBody = buildAnswerRequest(question, metrics, llmSummary);
            String response = webClient.post()
                .uri("/api/llm/answer")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseAnswerResponse(response);
        } catch (Exception e) {
            log.error("Python LLM answer failed", e);
            return generateDefaultQaAnswer(question, metrics);
        }
    }

    /**
     * 근거 기반 QA: DB 분석 결과(evidenceText) + 선택적 RAG 청크로 답변. 추측 금지, 최소 2개 수치/랭킹.
     */
    public String answerFromEvidence(String question, String evidenceText, List<String> ragChunks) {
        try {
            String requestBody = buildEvidenceAnswerRequest(question, evidenceText, ragChunks);
            String response = webClient.post()
                .uri("/api/llm/evidence-answer")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseRagAnswerResponse(response);
        } catch (Exception e) {
            log.error("Python evidence-answer failed", e);
            return null;
        }
    }

    /**
     * PR2: Intent + 슬롯 분류 (LLM JSON). 룰로 애매할 때만 호출.
     */
    public ClassifierOutputDTO classifyIntent(String question) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("question", question != null ? question : ""));
            String response = webClient.post()
                .uri("/api/llm/classify-intent")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseClassifyIntentResponse(response);
        } catch (Exception e) {
            log.error("Python classify-intent failed", e);
            return null;
        }
    }

    /**
     * 예산/목표 인사이트: 저축 추천, 예산 대비 사용, 추세·이번 달 전망. Spring이 payload 구성 후 전달.
     */
    public String getBudgetInsight(Map<String, Object> payload) {
        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            String response = webClient.post()
                .uri("/api/llm/budget-insight")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseBudgetInsightResponse(response);
        } catch (Exception e) {
            log.error("Python budget-insight failed", e);
            return "";
        }
    }

    // ========== Private Helper Methods ==========

    private String buildEmbedRequest(String text, String taskType) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "text", text,
                "task_type", taskType != null ? taskType : ""
            ));
        } catch (Exception e) {
            log.error("Build embed request failed", e);
            return "{}";
        }
    }

    private String buildEmbedBatchRequest(List<String> texts, String taskType) {
        try {
            Map<String, Object> req = Map.of("texts", texts);
            if (taskType != null) {
                req = Map.of("texts", texts, "task_type", taskType);
            }
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.error("Build embed batch request failed", e);
            return "{}";
        }
    }

    private String buildRagSearchRequest(long userId, String yearMonth, float[] queryEmbedding, int topK) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "user_id", userId,
                "year_month", yearMonth,
                "query_embedding", queryEmbedding,
                "top_k", topK
            ));
        } catch (Exception e) {
            log.error("Build RAG search request failed", e);
            return "{}";
        }
    }

    private String buildRagIndexChunkRequest(long chunkId, long reportId, long userId, String yearMonth, String content, float[] embedding) {
        try {
            List<Double> emb = new java.util.ArrayList<>();
            for (float v : embedding) {
                emb.add((double) v);
            }
            return objectMapper.writeValueAsString(Map.of(
                "chunk_id", chunkId,
                "report_id", reportId,
                "user_id", userId,
                "year_month", yearMonth != null ? yearMonth : "",
                "content", content != null ? content : "",
                "embedding", emb
            ));
        } catch (Exception e) {
            log.error("Build RAG index-chunk request failed", e);
            return "{}";
        }
    }

    private String buildRagContextRequest(String question, String factsBlock, List<String> candidateDocs) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "question", question != null ? question : "",
                "facts", factsBlock != null ? factsBlock : "",
                "candidate_docs", candidateDocs != null ? candidateDocs : List.of()
            ));
        } catch (Exception e) {
            log.error("Build rag-answer context request failed", e);
            return "{}";
        }
    }

    private String buildRagDelegatedRequest(long userId, String yearMonth, String question, String factsBlock, int topK) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "question", question != null ? question : "",
                "facts", factsBlock != null ? factsBlock : "",
                "user_id", userId,
                "year_month", yearMonth != null ? yearMonth : "",
                "top_k", topK
            ));
        } catch (Exception e) {
            log.error("Build rag-delegated request failed", e);
            return "{}";
        }
    }

    private String buildRagAnswerRequest(long userId, String yearMonth, String question) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "user_id", userId,
                "year_month", yearMonth,
                "question", question
            ));
        } catch (Exception e) {
            log.error("Build RAG answer request failed", e);
            return "{}";
        }
    }

    private String buildAnalyzeRequest(List<Map<String, Object>> transactions, Map<String, Object> metrics, Long monthlyBudget) {
        try {
            Map<String, Object> req = Map.of(
                "transactions", transactions,
                "metrics", metrics
            );
            if (monthlyBudget != null) {
                req = Map.of(
                    "transactions", transactions,
                    "metrics", metrics,
                    "monthly_budget", monthlyBudget
                );
            }
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.error("Build analyze request failed", e);
            return "{}";
        }
    }

    private String buildAnswerRequest(String question, Map<String, Object> metrics, String llmSummary) {
        try {
            Map<String, Object> req = Map.of(
                "question", question,
                "metrics", metrics,
                "llm_summary", llmSummary != null ? llmSummary : ""
            );
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.error("Build answer request failed", e);
            return "{}";
        }
    }

    private String buildEvidenceAnswerRequest(String question, String evidenceText, List<String> ragChunks) {
        try {
            Map<String, Object> req = new java.util.HashMap<>();
            req.put("question", question != null ? question : "");
            req.put("evidence_text", evidenceText != null ? evidenceText : "");
            if (ragChunks != null && !ragChunks.isEmpty()) {
                req.put("rag_chunks", ragChunks);
            }
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            log.error("Build evidence-answer request failed", e);
            return "{}";
        }
    }

    private float[] parseEmbeddingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode emb = root.path("embedding");
            if (!emb.isArray()) return null;
            float[] arr = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                arr[i] = (float) emb.get(i).asDouble();
            }
            return arr;
        } catch (Exception e) {
            log.error("Parse embedding response failed", e);
            return null;
        }
    }

    private List<float[]> parseEmbedBatchResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode embs = root.path("embeddings");
            if (!embs.isArray()) return List.of();
            return java.util.stream.IntStream.range(0, embs.size())
                .mapToObj(i -> {
                    JsonNode emb = embs.get(i);
                    float[] arr = new float[emb.size()];
                    for (int j = 0; j < emb.size(); j++) {
                        arr[j] = (float) emb.get(j).asDouble();
                    }
                    return arr;
                })
                .toList();
        } catch (Exception e) {
            log.error("Parse embed batch response failed", e);
            return List.of();
        }
    }

    private List<ReportChunk> parseRagSearchResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode chunks = root.path("chunks");
            if (!chunks.isArray()) return List.of();
            return java.util.stream.StreamSupport.stream(chunks.spliterator(), false)
                .map(chunk -> new ReportChunk(
                    chunk.path("chunk_id").asLong(),
                    chunk.path("report_id").asLong(),
                    chunk.path("user_id").asLong(),
                    chunk.path("year_month").asText(),
                    chunk.path("chunk_index").asInt(),
                    chunk.path("content").asText()
                ))
                .toList();
        } catch (Exception e) {
            log.error("Parse RAG search response failed", e);
            return List.of();
        }
    }

    private ClassifierOutputDTO parseClassifyIntentResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            Intent intent = Intent.fromString(root.has("intent") ? root.path("intent").asText() : null);
            double confidence = root.path("confidence").asDouble(0.5);
            String yearMonth = pathText(root, "year_month");
            String baselineYearMonth = pathText(root, "baseline_year_month");
            Long categoryId = root.path("category_id").isNull() || root.path("category_id").isMissingNode()
                ? null : root.path("category_id").asLong();
            String dimension = pathText(root, "dimension");
            List<String> keywords = new java.util.ArrayList<>();
            JsonNode kw = root.path("keywords");
            if (kw.isArray()) {
                for (JsonNode k : kw) keywords.add(k.asText());
            }
            boolean needsDb = root.path("needs_db").asBoolean(false);
            boolean needsRag = root.path("needs_rag").asBoolean(false);
            String followUp = pathText(root, "follow_up_question");

            return ClassifierOutputDTO.builder()
                .intent(intent != null ? intent : Intent.SUMMARY)
                .confidence(confidence)
                .yearMonth(yearMonth)
                .baselineYearMonth(baselineYearMonth)
                .categoryId(categoryId)
                .dimension(dimension)
                .keywords(keywords.isEmpty() ? null : keywords)
                .needsDb(needsDb)
                .needsRag(needsRag)
                .followUpQuestion(followUp)
                .build();
        } catch (Exception e) {
            log.error("Parse classify-intent response failed", e);
            return null;
        }
    }

    private static String pathText(JsonNode root, String field) {
        JsonNode n = root.path(field);
        if (n.isNull() || n.isMissingNode()) return null;
        String s = n.asText();
        return (s != null && !s.isBlank()) ? s : null;
    }

    private String parseRagAnswerResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("answer").asText();
        } catch (Exception e) {
            log.error("Parse RAG answer response failed", e);
            return null;
        }
    }

    private String parseAnalyzeResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("summary").asText();
        } catch (Exception e) {
            log.error("Parse analyze response failed", e);
            return null;
        }
    }

    private String parseAnswerResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("answer").asText();
        } catch (Exception e) {
            log.error("Parse answer response failed", e);
            return null;
        }
    }

    private String parseBudgetInsightResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode insight = root.path("insight");
            return insight.isMissingNode() ? "" : insight.asText();
        } catch (Exception e) {
            log.error("Parse budget insight response failed", e);
            return "";
        }
    }

    private String generateDefaultSummary(Map<String, Object> metrics, Long monthlyBudget) {
        Object totalExpense = metrics.get("totalExpense");
        Object totalIncome = metrics.get("totalIncome");
        StringBuilder summary = new StringBuilder();
        summary.append("이번 달 총 수입은 ").append(totalIncome).append("원, 총 지출은 ").append(totalExpense).append("원입니다. ");
        if (monthlyBudget != null && totalExpense instanceof Number) {
            long exp = ((Number) totalExpense).longValue();
            if (exp > monthlyBudget) {
                summary.append("예산을 ").append(exp - monthlyBudget).append("원 초과했습니다. ");
            } else {
                summary.append("예산 내에서 지출을 관리하고 있습니다. ");
            }
        }
        summary.append("규칙적인 예산 관리를 통해 더 나은 재정 상태를 유지하시기 바랍니다.");
        return summary.toString();
    }

    private String generateDefaultQaAnswer(String question, Map<String, Object> metrics) {
        Object exp = metrics.get("totalExpense");
        Object inc = metrics.get("totalIncome");
        return String.format("이번 달 총 지출은 %s원, 총 수입은 %s원입니다. " +
            "리포트를 생성한 뒤 질문해 주시면 더 자세히 답변드릴 수 있어요.",
            exp != null ? exp : "0", inc != null ? inc : "0");
    }

    // RAG용 DTO
    public static class ReportChunk {
        private final long chunkId;
        private final long reportId;
        private final long userId;
        private final String yearMonth;
        private final int chunkIndex;
        private final String content;

        public ReportChunk(long chunkId, long reportId, long userId, String yearMonth, int chunkIndex, String content) {
            this.chunkId = chunkId;
            this.reportId = reportId;
            this.userId = userId;
            this.yearMonth = yearMonth;
            this.chunkIndex = chunkIndex;
            this.content = content;
        }

        public long getChunkId() { return chunkId; }
        public long getReportId() { return reportId; }
        public long getUserId() { return userId; }
        public String getYearMonth() { return yearMonth; }
        public int getChunkIndex() { return chunkIndex; }
        public String getContent() { return content; }
    }
}
