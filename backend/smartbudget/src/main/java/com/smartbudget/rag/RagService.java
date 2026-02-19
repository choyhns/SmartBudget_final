package com.smartbudget.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartbudget.llm.PythonAIService;
import com.smartbudget.monthlyreport.MonthlyReportDTO;
import com.smartbudget.rag.config.RagProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG: DB/통계·Facts는 Spring 담당. 벡터 검색·프롬프트·LLM은 AI Service 담당.
 * useChroma=true면 벡터는 Chroma(ml-server), PostgreSQL에는 content만 저장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final String TASK_DOCUMENT = "RETRIEVAL_DOCUMENT";

    private final PythonAIService pythonAIService;
    private final ReportChunkRepository chunkRepo;
    private final RagDocumentBuilder documentBuilder;
    private final RagProperties ragProperties;

    @Value("${rag.enabled:false}")
    private boolean enabled;

    @Value("${rag.top-k:5}")
    private int topK;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 리포트 저장/수정 시 호출. 기존 청크 삭제 후 청킹 → 임베딩 → 저장.
     * useChroma면 PostgreSQL에 content만 저장하고 Chroma에 벡터 인덱싱.
     */
    public void indexReport(MonthlyReportDTO report, Map<String, Object> metrics) {
        if (!enabled) return;
        if (report == null || report.getReportId() == null) return;

        chunkRepo.deleteByReportId(report.getReportId());
        if (ragProperties.isUseChroma()) {
            pythonAIService.deleteReportChunksFromChroma(report.getReportId());
        }

        List<ChunkWithType> chunksWithType = buildChunksWithType(report, metrics);
        if (chunksWithType.isEmpty()) return;
        List<String> contents = chunksWithType.stream().map(ChunkWithType::getContent).toList();
        List<float[]> embeddings = pythonAIService.embedBatch(contents, TASK_DOCUMENT);

        for (int i = 0; i < chunksWithType.size(); i++) {
            float[] emb = i < embeddings.size() ? embeddings.get(i) : null;
            if (emb == null || emb.length == 0) continue;
            ChunkWithType ct = chunksWithType.get(i);
            if (ragProperties.isUseChroma()) {
                long chunkId = chunkRepo.insertReturningId(
                        report.getReportId(),
                        report.getUserId(),
                        report.getYearMonth(),
                        i,
                        ct.getContent(),
                        ct.getDocType()
                );
                if (chunkId > 0) {
                    pythonAIService.indexChunkToChroma(
                            chunkId,
                            report.getReportId(),
                            report.getUserId(),
                            report.getYearMonth(),
                            ct.getContent(),
                            emb
                    );
                }
            } else {
                chunkRepo.insert(
                        report.getReportId(),
                        report.getUserId(),
                        report.getYearMonth(),
                        i,
                        ct.getContent(),
                        emb,
                        ct.getDocType()
                );
            }
        }
        log.info("RAG indexed report {} ({} chunks)", report.getReportId(), chunksWithType.size());
    }

    /**
     * Spring이 DB/통계로 facts만 구성하고, AI Service에 (question, facts, user_id, year_month, top_k) 전달.
     * 벡터 검색·프롬프트·LLM은 AI Service가 담당 (POST /api/llm/rag-answer).
     */
    public String answerWithRag(long userId, String yearMonth, String question) {
        if (!enabled) return null;
        String yyyyMm = yearMonth != null ? yearMonth.replace("-", "").trim() : null;
        if (yyyyMm == null || question == null || question.isBlank()) return null;

        String factsBlock = "";
        try {
            var result = documentBuilder.buildFacts(userId, yyyyMm);
            factsBlock = result != null && result.getFactsBlock() != null ? result.getFactsBlock() : "";
        } catch (Exception e) {
            log.warn("RagService: buildFacts failed for user={}, yearMonth={}", userId, yyyyMm, e);
        }

        return pythonAIService.answerWithRagDelegated(userId, yyyyMm, question.trim(), factsBlock, topK);
    }

    private List<String> buildChunks(MonthlyReportDTO report, Map<String, Object> metrics) {
        return buildChunksWithType(report, metrics).stream().map(ChunkWithType::getContent).toList();
    }

    /** 청크별 doc_type 지정: summary, stats, category_expenses */
    private List<ChunkWithType> buildChunksWithType(MonthlyReportDTO report, Map<String, Object> metrics) {
        List<ChunkWithType> out = new ArrayList<>();
        String summary = report.getLlmSummaryText();
        if (summary != null && !summary.isBlank()) {
            out.add(new ChunkWithType("【월별 AI 요약】\n" + summary, "summary"));
        }

        StringBuilder stats = new StringBuilder("【통계】");
        Object inc = metrics != null ? metrics.get("totalIncome") : null;
        Object exp = metrics != null ? metrics.get("totalExpense") : null;
        Object net = metrics != null ? metrics.get("netAmount") : null;
        Object cnt = metrics != null ? metrics.get("transactionCount") : null;
        stats.append(" 총 수입 ").append(inc != null ? inc : "0").append("원");
        stats.append(", 총 지출 ").append(exp != null ? exp : "0").append("원");
        stats.append(", 순액 ").append(net != null ? net : "0").append("원");
        stats.append(", 거래 ").append(cnt != null ? cnt : "0").append("건.");
        out.add(new ChunkWithType(stats.toString(), "stats"));

        if (metrics != null && metrics.containsKey("categoryExpenses")) {
            @SuppressWarnings("unchecked")
            Map<String, ?> ce = (Map<String, ?>) metrics.get("categoryExpenses");
            if (ce != null && !ce.isEmpty()) {
                StringBuilder cat = new StringBuilder("【카테고리별 지출】");
                ce.forEach((k, v) -> cat.append(" ").append(k).append(" ").append(v).append("원,"));
                cat.setLength(cat.length() - 1);
                out.add(new ChunkWithType(cat.toString(), "category_expenses"));
            }
        }

        return out;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ChunkWithType {
        private final String content;
        private final String docType;
    }
}
