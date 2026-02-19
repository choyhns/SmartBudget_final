package com.smartbudget.rag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartbudget.llm.PythonAIService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 질문 카드: 임베딩 시드, 월별 컨텍스트 기반 top-k 추천, 클릭 이력 리랭크.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionCardService {

    private static final String TASK_DOCUMENT = "RETRIEVAL_DOCUMENT";
    private static final String TASK_QUERY = "RETRIEVAL_QUERY";
    /** 사용자 지출 맥락과 유사한 질문 카드만 추천, 기본 5개 */
    private static final int DEFAULT_RECOMMEND_LIMIT = 5;
    private static final int CANDIDATE_MULTIPLIER = 2;

    private final QuestionCardRepository cardRepo;
    private final QuestionCardClickRepository clickRepo;
    private final RagDocumentBuilder documentBuilder;
    private final PythonAIService pythonAIService;

    @Value("${rag.question-cards.recommend-limit:" + DEFAULT_RECOMMEND_LIMIT + "}")
    private int recommendLimit;

    /**
     * embedding이 비어 있는 카드에 대해 Python으로 임베딩 생성 후 저장.
     * 앱 기동 후 1회 또는 관리용 호출.
     */
    public int ensureEmbeddings() {
        List<QuestionCard> toEmbed = cardRepo.findAllWithNullEmbedding();
        if (toEmbed.isEmpty()) return 0;
        List<String> texts = toEmbed.stream().map(QuestionCard::getQuestionText).toList();
        List<float[]> embeddings = pythonAIService.embedBatch(texts, TASK_DOCUMENT);
        int updated = 0;
        for (int i = 0; i < toEmbed.size() && i < embeddings.size(); i++) {
            float[] emb = embeddings.get(i);
            if (emb != null && emb.length > 0) {
                cardRepo.updateEmbedding(toEmbed.get(i).getCardId(), emb);
                updated++;
            }
        }
        log.info("Question card embeddings updated: {} / {}", updated, toEmbed.size());
        return updated;
    }

    /**
     * 월별 리포트 기반 상황 컨텍스트를 임베딩해, 유사한 질문 카드 top-k 추천.
     * 이미 클릭한 카드는 후순위로 리랭크(개인화).
     */
    public List<QuestionCard> recommendCards(long userId, String yearMonth, Integer limit) {
        int k = limit != null && limit > 0 ? limit : recommendLimit;
        int candidateK = Math.min(100, k * CANDIDATE_MULTIPLIER);

        if (cardRepo.countCardsWithNullEmbedding() > 0) {
            ensureEmbeddings();
        }

        String yyyyMm = yearMonth != null ? yearMonth.replace("-", "").trim() : null;
        String contextText = buildContextForRecommend(userId, yyyyMm);
        float[] contextEmbedding = pythonAIService.embed(contextText, TASK_QUERY);
        if (contextEmbedding == null || contextEmbedding.length == 0) {
            return cardRepo.findAll().stream().limit(k).collect(Collectors.toList());
        }

        List<QuestionCard> candidates = cardRepo.findNearest(contextEmbedding, candidateK);
        if (candidates.isEmpty()) {
            return cardRepo.findAll().stream().limit(k).collect(Collectors.toList());
        }

        Set<Long> clickedIds = new HashSet<>(clickRepo.findClickedCardIds(userId, yyyyMm != null ? yyyyMm : yearMonth));
        List<QuestionCard> notClicked = new ArrayList<>();
        List<QuestionCard> clicked = new ArrayList<>();
        for (QuestionCard c : candidates) {
            if (clickedIds.contains(c.getCardId())) clicked.add(c);
            else notClicked.add(c);
        }
        notClicked.addAll(clicked);
        return notClicked.stream().limit(k).collect(Collectors.toList());
    }

    public void logClick(long userId, String yearMonth, long cardId) {
        clickRepo.insert(userId, yearMonth, cardId);
    }

    private String buildContextForRecommend(long userId, String yearMonth) {
        if (yearMonth == null || yearMonth.isEmpty()) return "";
        try {
            var result = documentBuilder.buildFacts(userId, yearMonth);
            return result != null && result.getFactsBlock() != null ? result.getFactsBlock() : "";
        } catch (Exception e) {
            log.warn("buildContextForRecommend failed for user={}, yearMonth={}", userId, yearMonth, e);
            return "";
        }
    }
}
