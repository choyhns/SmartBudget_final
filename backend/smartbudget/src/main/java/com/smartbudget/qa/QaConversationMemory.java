package com.smartbudget.qa;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Q/A 대화 히스토리 (A방식: 자바에서 관리).
 * - userId 기준으로 최근 N턴만 메모리에 보관
 * - Python LLM에는 문자열 블록으로만 전달
 */
@Component
public class QaConversationMemory {

    /** 사용자당 최대 저장 턴 수 */
    private static final int MAX_TURNS_PER_USER = 10;

    private final Map<Long, Deque<QaTurn>> conversations = new ConcurrentHashMap<>();

    public void addTurn(Long userId, String question, String answer) {
        if (userId == null || question == null || question.isBlank() || answer == null || answer.isBlank()) {
            return;
        }
        Deque<QaTurn> deque = conversations.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new QaTurn(question.trim(), answer.trim(), LocalDateTime.now()));
            while (deque.size() > MAX_TURNS_PER_USER) {
                deque.pollFirst();
            }
        }
    }

    /**
     * LLM 프롬프트용 히스토리 블록 생성.
     * 최근 turnsLimit 턴을 시간순으로 이어 붙인다.
     */
    public String buildHistoryBlock(Long userId, int turnsLimit) {
        if (userId == null || turnsLimit <= 0) {
            return "";
        }
        Deque<QaTurn> deque = conversations.get(userId);
        if (deque == null || deque.isEmpty()) {
            return "";
        }
        List<QaTurn> snapshot;
        synchronized (deque) {
            snapshot = new ArrayList<>(deque);
        }
        int fromIndex = Math.max(0, snapshot.size() - turnsLimit);
        StringBuilder sb = new StringBuilder();
        for (int i = fromIndex; i < snapshot.size(); i++) {
            QaTurn t = snapshot.get(i);
            sb.append("사용자: ").append(t.question()).append("\n");
            sb.append("AI: ").append(t.answer()).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Q/A 한 턴.
     */
    public record QaTurn(String question, String answer, LocalDateTime timestamp) {
    }
}

