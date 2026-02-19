package com.smartbudget.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.smartbudget.rag.config.RagProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 질문 카드 마스터 및 벡터 유사도 검색. pgvector 또는 JSONB (useJsonb=true, Railway 등).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class QuestionCardRepository {

    private static final String TABLE = "projectdb.question_cards";

    private final JdbcTemplate jdbc;
    private final RagProperties ragProperties;

    private static String toJsonArrayLiteral(float[] emb) {
        if (emb == null || emb.length == 0) return "[]";
        StringBuilder sb = new StringBuilder().append('[');
        for (int i = 0; i < emb.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(emb[i]);
        }
        return sb.append(']').toString();
    }

    private static float[] parseEmbeddingFromJson(String json) {
        if (json == null || json.isBlank()) return null;
        String s = json.trim();
        if (s.length() < 2 || s.charAt(0) != '[') return null;
        String inner = s.substring(1, s.length() - 1).trim();
        if (inner.isEmpty()) return new float[0];
        String[] parts = inner.split("[, \t]+");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double n = Math.sqrt(normA) * Math.sqrt(normB);
        return n == 0 ? 0 : dot / n;
    }

    public List<QuestionCard> findAll() {
        return jdbc.query(
                "SELECT card_id, question_text, tag, category_label, sort_order FROM " + TABLE + " ORDER BY sort_order, card_id",
                (rs, i) -> QuestionCard.of(
                        rs.getLong("card_id"),
                        rs.getString("question_text"),
                        rs.getString("tag"),
                        rs.getString("category_label"),
                        rs.getObject("sort_order", Integer.class)
                )
        );
    }

    public void updateEmbedding(long cardId, float[] embedding) {
        if (embedding == null || embedding.length == 0) return;
        String lit = toJsonArrayLiteral(embedding);
        if (ragProperties.isUseJsonb()) {
            jdbc.update("UPDATE " + TABLE + " SET embedding = ?::jsonb WHERE card_id = ?", lit, cardId);
        } else {
            jdbc.update("UPDATE " + TABLE + " SET embedding = ?::vector WHERE card_id = ?", lit, cardId);
        }
    }

    /**
     * 쿼리 벡터와 코사인 유사도 기준 상위 k개 카드 조회. JSONB 모드에서는 앱에서 유사도 계산.
     */
    public List<QuestionCard> findNearest(float[] queryEmbedding, int k) {
        if (queryEmbedding == null || queryEmbedding.length == 0) return List.of();
        if (ragProperties.isUseJsonb()) {
            List<ReportRow> rows = jdbc.query(
                    "SELECT card_id, question_text, tag, category_label, sort_order, embedding FROM " + TABLE + " WHERE embedding IS NOT NULL",
                    (rs, i) -> new ReportRow(
                            QuestionCard.of(
                                    rs.getLong("card_id"),
                                    rs.getString("question_text"),
                                    rs.getString("tag"),
                                    rs.getString("category_label"),
                                    rs.getObject("sort_order", Integer.class)
                            ),
                            getEmbeddingString(rs, "embedding")
                    )
            );
            List<QuestionCard> out = new ArrayList<>();
            List<Scored> scored = new ArrayList<>();
            for (ReportRow r : rows) {
                float[] emb = parseEmbeddingFromJson(r.embeddingJson);
                if (emb == null) continue;
                double sim = cosineSimilarity(queryEmbedding, emb);
                scored.add(new Scored(r.card, sim));
            }
            scored.sort((a, b) -> Double.compare(b.score, a.score));
            int limit = Math.min(k, scored.size());
            for (int i = 0; i < limit; i++) {
                out.add(scored.get(i).card);
            }
            return out;
        }
        String v = toJsonArrayLiteral(queryEmbedding);
        return jdbc.query(
                "SELECT card_id, question_text, tag, category_label, sort_order " +
                        "FROM " + TABLE + " " +
                        "WHERE embedding IS NOT NULL " +
                        "ORDER BY embedding <=> ?::vector " +
                        "LIMIT ?",
                (rs, i) -> QuestionCard.of(
                        rs.getLong("card_id"),
                        rs.getString("question_text"),
                        rs.getString("tag"),
                        rs.getString("category_label"),
                        rs.getObject("sort_order", Integer.class)
                ),
                v, k
        );
    }

    private static String getEmbeddingString(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Object o = rs.getObject(col);
        if (o == null) return null;
        if (o instanceof String) return (String) o;
        return o.toString();
    }

    private static final class ReportRow {
        final QuestionCard card;
        final String embeddingJson;
        ReportRow(QuestionCard card, String embeddingJson) {
            this.card = card;
            this.embeddingJson = embeddingJson;
        }
    }

    private static final class Scored {
        final QuestionCard card;
        final double score;
        Scored(QuestionCard card, double score) {
            this.card = card;
            this.score = score;
        }
    }

    public int countCardsWithNullEmbedding() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE + " WHERE embedding IS NULL", Integer.class);
        return n != null ? n : 0;
    }

    /** 임베딩이 아직 없는 카드만 조회 (card_id, question_text). 시드 임베딩용. */
    public List<QuestionCard> findAllWithNullEmbedding() {
        return jdbc.query(
                "SELECT card_id, question_text, tag, category_label, sort_order FROM " + TABLE + " WHERE embedding IS NULL ORDER BY sort_order, card_id",
                (rs, i) -> QuestionCard.of(
                        rs.getLong("card_id"),
                        rs.getString("question_text"),
                        rs.getString("tag"),
                        rs.getString("category_label"),
                        rs.getObject("sort_order", Integer.class)
                )
        );
    }
}
