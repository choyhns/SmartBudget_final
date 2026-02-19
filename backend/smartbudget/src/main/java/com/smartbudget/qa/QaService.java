package com.smartbudget.qa;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smartbudget.analysis.AnalysisQueryParams;
import com.smartbudget.analysis.AnalysisRepository;
import com.smartbudget.analysis.AnalysisTemplateDTO;
import com.smartbudget.analysis.ClassifierOutputDTO;
import com.smartbudget.analysis.Intent;
import com.smartbudget.analysis.IntentClassifierService;
import com.smartbudget.llm.PythonAIService;
import com.smartbudget.rag.ReportChunkRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PR3: POST /api/qa/ask. Intent 분류 → PR1 분석 실행(dbEvidence) → (필요 시) RAG → Gemini 근거 기반 답변.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int DEFAULT_LIMIT = 10;

    private final IntentClassifierService intentClassifierService;
    private final AnalysisRepository analysisRepository;
    private final PythonAIService pythonAIService;
    private final ReportChunkRepository reportChunkRepository;
    private final QaConversationMemory conversationMemory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * yearMonth 파라미터가 있으면 우선 적용. 그 외 classifier 슬롯 + 기본값(이번달/전월).
     */
    public QaAskResponseDTO ask(Long userId, String requestYearMonth, String question) {
        if (userId == null || question == null || question.isBlank()) {
            return QaAskResponseDTO.builder()
                .answerText("질문 내용을 입력해 주세요.")
                .usedIntent(Intent.SUMMARY.name())
                .evidence(new QaAskResponseDTO.EvidencePayload(null, null))
                .build();
        }

        String defaultYearMonth = normalizeYearMonth(requestYearMonth) != null
            ? normalizeYearMonth(requestYearMonth)
            : YearMonth.now().format(YYYYMM);
        String defaultBaseline = YearMonth.now().minusMonths(1).format(YYYYMM);

        String trimmedQuestion = question.trim();

        // 이전 대화 히스토리 블록 생성 (최근 5턴)
        String historyBlock = conversationMemory.buildHistoryBlock(userId, 5);

        ClassifierOutputDTO classified = intentClassifierService.classify(trimmedQuestion, defaultYearMonth, defaultBaseline);
        if (requestYearMonth != null && !requestYearMonth.isBlank()) {
            classified.setYearMonth(normalizeYearMonth(requestYearMonth));
        }

        Intent intent = classified.getIntent() != null ? classified.getIntent() : Intent.SUMMARY;
        String effectiveYearMonth = classified.getYearMonth() != null ? classified.getYearMonth() : defaultYearMonth;
        String effectiveBaseline = classified.getBaselineYearMonth() != null ? classified.getBaselineYearMonth() : defaultBaseline;

        AnalysisQueryParams params = toQueryParams(userId, classified, effectiveYearMonth, effectiveBaseline);

        Map<String, Object> dbEvidence = new LinkedHashMap<>();
        StringBuilder evidenceText = new StringBuilder();

        if (classified.isNeedsDb()) {
            buildDbEvidenceAndText(intent, params, classified, dbEvidence, evidenceText);
        }

        List<String> ragChunks = null;
        if (classified.isNeedsRag() || (classified.isNeedsDb() && evidenceText.length() < 200)) {
            List<String> chunks = reportChunkRepository.selectContentByUserAndYearMonth(userId, effectiveYearMonth);
            if (chunks != null && !chunks.isEmpty()) {
                ragChunks = chunks;
            }
        }

        // LLM에 전달할 질문: 이전 대화를 프롬프트 상단에 포함
        String llmQuestion = buildQuestionWithHistory(historyBlock, trimmedQuestion);

        String answerText = pythonAIService.answerFromEvidence(llmQuestion, evidenceText.toString(), ragChunks);
        if (answerText == null || answerText.isBlank()) {
            answerText = "데이터를 바탕으로 답변을 만들지 못했어요. 해당 월 리포트를 생성했는지, 또는 질문을 조금 더 구체적으로 해 주실 수 있을까요?";
        }

        // 성공적으로 답변을 생성했다면 히스토리에 현재 턴을 추가
        if (answerText != null && !answerText.isBlank()) {
            conversationMemory.addTurn(userId, trimmedQuestion, answerText);
        }

        Map<String, Object> ragMeta = new LinkedHashMap<>();
        if (ragChunks != null) {
            ragMeta.put("snippetCount", ragChunks.size());
        }

        return QaAskResponseDTO.builder()
            .answerText(answerText)
            .usedIntent(intent.name())
            .evidence(new QaAskResponseDTO.EvidencePayload(dbEvidence.isEmpty() ? null : dbEvidence, ragMeta.isEmpty() ? null : ragMeta))
            .followUpQuestion(classified.getFollowUpQuestion())
            .build();
    }

    private String buildQuestionWithHistory(String historyBlock, String currentQuestion) {
        if (historyBlock == null || historyBlock.isBlank()) {
            return currentQuestion;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 최근 사용자와 AI의 대화 히스토리입니다.\n");
        sb.append(historyBlock).append("\n\n");
        sb.append("위 히스토리를 참고해서, 다음 현재 질문에 답변해 주세요.\n");
        sb.append("현재 질문: ").append(currentQuestion);
        return sb.toString();
    }

    private void buildDbEvidenceAndText(Intent intent, AnalysisQueryParams params,
                                        ClassifierOutputDTO classified,
                                        Map<String, Object> dbEvidence, StringBuilder evidenceText) {
        evidenceText.append("【분석 기준월 ").append(params.getYearMonth()).append(" (기간 A=이번달/선택월)】");
        if (params.getBaselineYearMonth() != null && !params.getBaselineYearMonth().isBlank()) {
            evidenceText.append(" 【비교 기준월(전달) ").append(params.getBaselineYearMonth()).append(" (기간 B)】");
        }
        evidenceText.append("\n");

        switch (intent) {
            case SUMMARY -> {
                AnalysisTemplateDTO.MonthSummary ms = analysisRepository.monthSummary(params);
                putAsMap(dbEvidence, "monthSummary", ms);
                appendMonthSummary(evidenceText, ms);
            }
            case COMPARISON -> {
                AnalysisTemplateDTO.MonthCompare mc = analysisRepository.monthCompare(params);
                putAsMap(dbEvidence, "monthCompare", mc);
                appendMonthCompare(evidenceText, mc);
            }
            case CAUSE_ANALYSIS -> {
                List<AnalysisTemplateDTO.CategoryDeltaRow> catDelta = analysisRepository.categoryDeltaTopN(params);
                putAsMap(dbEvidence, "categoryDeltaTopN", catDelta);
                appendCategoryDelta(evidenceText, catDelta);
                AnalysisTemplateDTO.TargetBreakdown tb = analysisRepository.targetBreakdown(params);
                putAsMap(dbEvidence, "targetBreakdown", tb);
                appendTargetBreakdown(evidenceText, tb);
                AnalysisTemplateDTO.AovVsCountDecompose aov = analysisRepository.aovVsCountDecompose(params);
                putAsMap(dbEvidence, "aovVsCountDecompose", aov);
                appendAovDecompose(evidenceText, aov);
            }
            case PATTERN_DEEPDIVE -> {
                String dim = classified.getDimension();
                if ("timeband".equals(dim)) {
                    List<AnalysisTemplateDTO.TimebandDeltaRow> td = analysisRepository.timebandDelta(params);
                    putAsMap(dbEvidence, "timebandDelta", td);
                    appendTimebandDelta(evidenceText, td);
                } else if ("dow".equals(dim)) {
                    List<AnalysisTemplateDTO.DowDeltaRow> dd = analysisRepository.dowDelta(params);
                    putAsMap(dbEvidence, "dowDelta", dd);
                    appendDowDelta(evidenceText, dd);
                } else if ("merchant".equals(dim)) {
                    List<AnalysisTemplateDTO.MerchantTopRow> tm = analysisRepository.topMerchants(params);
                    putAsMap(dbEvidence, "topMerchants", tm);
                    appendTopMerchants(evidenceText, tm);
                } else if ("keyword".equals(dim) || (classified.getKeywords() != null && !classified.getKeywords().isEmpty())) {
                    List<AnalysisTemplateDTO.KeywordStatsRow> ks = analysisRepository.keywordStats(params);
                    putAsMap(dbEvidence, "keywordStats", ks);
                    appendKeywordStats(evidenceText, ks);
                } else {
                    List<AnalysisTemplateDTO.TimebandDeltaRow> td = analysisRepository.timebandDelta(params);
                    List<AnalysisTemplateDTO.DowDeltaRow> dd = analysisRepository.dowDelta(params);
                    List<AnalysisTemplateDTO.MerchantTopRow> tm = analysisRepository.topMerchants(params);
                    putAsMap(dbEvidence, "timebandDelta", td);
                    putAsMap(dbEvidence, "dowDelta", dd);
                    putAsMap(dbEvidence, "topMerchants", tm);
                    appendTimebandDelta(evidenceText, td);
                    appendDowDelta(evidenceText, dd);
                    appendTopMerchants(evidenceText, tm);
                }
            }
            case BUDGET_STATUS -> {
                AnalysisTemplateDTO.MonthSummary ms = analysisRepository.monthSummary(params);
                putAsMap(dbEvidence, "monthSummary", ms);
                appendMonthSummary(evidenceText, ms);
            }
            default -> {
                AnalysisTemplateDTO.MonthSummary ms = analysisRepository.monthSummary(params);
                putAsMap(dbEvidence, "monthSummary", ms);
                appendMonthSummary(evidenceText, ms);
            }
        }
    }

    private AnalysisQueryParams toQueryParams(Long userId, ClassifierOutputDTO c, String yearMonth, String baselineYearMonth) {
        return AnalysisQueryParams.builder()
            .userId(userId)
            .yearMonth(yearMonth)
            .baselineYearMonth(baselineYearMonth)
            .categoryId(c.getCategoryId())
            .limit(DEFAULT_LIMIT)
            .keywords(c.getKeywords().isEmpty() ? null : c.getKeywords())
            .build();
    }

    private void putAsMap(Map<String, Object> out, String key, Object value) {
        if (value == null) return;
        try {
            if (value instanceof List) {
                List<Map<String, Object>> list = objectMapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() {});
                out.put(key, list);
            } else {
                Map<String, Object> m = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
                out.put(key, m);
            }
        } catch (Exception e) {
            out.put(key, value);
        }
    }

    private void appendMonthSummary(StringBuilder sb, AnalysisTemplateDTO.MonthSummary ms) {
        if (ms == null) return;
        sb.append("총 지출: ").append(ms.getTotalExpense()).append("원, 총 수입: ").append(ms.getTotalIncome()).append("원, 거래 ").append(ms.getTxCount()).append("건, AOV ").append(ms.getAov()).append("원.\n");
        if (ms.getByCategoryTopN() != null && !ms.getByCategoryTopN().isEmpty()) {
            sb.append("카테고리 TOP(기준월 지출): ");
            ms.getByCategoryTopN().stream().limit(10).forEach(r -> sb.append(r.getCategoryName()).append(" ").append(r.getTotalAmount()).append("원; "));
            sb.append("\n");
        }
    }

    private void appendMonthCompare(StringBuilder sb, AnalysisTemplateDTO.MonthCompare mc) {
        if (mc == null) return;
        sb.append("기간 A(이번달) 지출: ").append(mc.getPeriodA() != null ? mc.getPeriodA().getTotalExpense() : "0").append("원. 기간 B(전달) 지출: ").append(mc.getPeriodB() != null ? mc.getPeriodB().getTotalExpense() : "0").append("원. 총 차이: ").append(mc.getDeltaExpense()).append("원.\n");
        if (mc.getByCategoryDeltaTopN() != null && !mc.getByCategoryDeltaTopN().isEmpty()) {
            sb.append("카테고리별 이번달(기간A)/전달(기간B)/증감: ");
            mc.getByCategoryDeltaTopN().stream().limit(10).forEach(r -> {
                sb.append(r.getCategoryName()).append(" 이번달 ").append(r.getCurrentAmount() != null ? r.getCurrentAmount() : "0").append("원 전달 ").append(r.getBaselineAmount() != null ? r.getBaselineAmount() : "0").append("원 증감 ").append(r.getDeltaAmount()).append("원; ");
            });
            sb.append("\n");
        }
    }

    private void appendCategoryDelta(StringBuilder sb, List<AnalysisTemplateDTO.CategoryDeltaRow> list) {
        if (list == null || list.isEmpty()) return;
        sb.append("카테고리별 이번달/전달/증감: ");
        list.stream().limit(10).forEach(r -> {
            sb.append(r.getCategoryName()).append(" 이번달 ").append(r.getCurrentAmount() != null ? r.getCurrentAmount() : "0").append("원 전달 ").append(r.getBaselineAmount() != null ? r.getBaselineAmount() : "0").append("원 증감 ").append(r.getDeltaAmount()).append("원; ");
        });
        sb.append("\n");
    }

    private void appendTargetBreakdown(StringBuilder sb, AnalysisTemplateDTO.TargetBreakdown tb) {
        if (tb == null) return;
        if (tb.getMerchantTopN() != null && !tb.getMerchantTopN().isEmpty()) {
            sb.append("상위 가맹점: ");
            tb.getMerchantTopN().stream().limit(5).forEach(r -> sb.append(r.getMerchant()).append(" ").append(r.getTotalAmount()).append("원; "));
            sb.append("\n");
        }
        if (tb.getTimebandDist() != null && !tb.getTimebandDist().isEmpty()) {
            sb.append("시간대: ");
            tb.getTimebandDist().forEach(r -> sb.append(r.getTimeBand()).append(" ").append(r.getTotalAmount()).append("원; "));
            sb.append("\n");
        }
    }

    private void appendAovDecompose(StringBuilder sb, AnalysisTemplateDTO.AovVsCountDecompose aov) {
        if (aov == null) return;
        sb.append("총액 변화: ").append(aov.getDeltaTotal()).append("원 (건수 기여: ").append(aov.getDeltaFromCount()).append(", 건당금액 기여: ").append(aov.getDeltaFromAov()).append(").\n");
    }

    private void appendTimebandDelta(StringBuilder sb, List<AnalysisTemplateDTO.TimebandDeltaRow> list) {
        if (list == null || list.isEmpty()) return;
        sb.append("시간대별 증감: ");
        list.forEach(r -> sb.append(r.getTimeBand()).append(" 현재 ").append(r.getCurrentAmount()).append(" 전기 ").append(r.getBaselineAmount()).append(" 차이 ").append(r.getDeltaAmount()).append("; "));
        sb.append("\n");
    }

    private void appendDowDelta(StringBuilder sb, List<AnalysisTemplateDTO.DowDeltaRow> list) {
        if (list == null || list.isEmpty()) return;
        sb.append("요일별 증감: ");
        list.forEach(r -> sb.append("dow ").append(r.getDow()).append(" ").append(r.getDeltaAmount()).append("원; "));
        sb.append("\n");
    }

    private void appendTopMerchants(StringBuilder sb, List<AnalysisTemplateDTO.MerchantTopRow> list) {
        if (list == null || list.isEmpty()) return;
        sb.append("가맹점 TOP: ");
        list.stream().limit(5).forEach(r -> sb.append(r.getMerchant()).append(" ").append(r.getTotalAmount()).append("원; "));
        sb.append("\n");
    }

    private void appendKeywordStats(StringBuilder sb, List<AnalysisTemplateDTO.KeywordStatsRow> list) {
        if (list == null || list.isEmpty()) return;
        sb.append("키워드 통계: ");
        list.forEach(r -> sb.append(r.getKeyword()).append(" ").append(r.getTotalAmount()).append("원 ").append(r.getTxCount()).append("건; "));
        sb.append("\n");
    }

    private static String normalizeYearMonth(String s) {
        if (s == null || s.isBlank()) return null;
        return s.replace("-", "").trim();
    }
}
