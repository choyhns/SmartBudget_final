package com.smartbudget.rag;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 질문 카드 클릭 로그. 추천 리랭크 시 사용.
 */
@Repository
@RequiredArgsConstructor
public class QuestionCardClickRepository {

    private static final String TABLE = "projectdb.question_card_clicks";

    private final JdbcTemplate jdbc;

    public void insert(long userId, String yearMonth, long cardId) {
        String ym = yearMonth != null ? yearMonth.replace("-", "").trim() : null;
        if (ym == null || ym.isEmpty()) return;
        jdbc.update("INSERT INTO " + TABLE + " (user_id, year_month, card_id) VALUES (?, ?, ?)", userId, ym, cardId);
    }

    /**
     * 해당 사용자·연월에서 이미 클릭한 카드 ID 목록 (리랭크 시 후순위로).
     */
    public List<Long> findClickedCardIds(long userId, String yearMonth) {
        String ym = yearMonth != null ? yearMonth.replace("-", "").trim() : null;
        if (ym == null || ym.isEmpty()) return List.of();
        List<Long> ids = jdbc.queryForList(
                "SELECT DISTINCT card_id FROM " + TABLE + " WHERE user_id = ? AND year_month = ? ORDER BY card_id",
                Long.class, userId, ym
        );
        return ids != null ? ids : List.of();
    }
}
