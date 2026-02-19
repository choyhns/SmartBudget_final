package com.smartbudget.rag;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.smartbudget.rag.config.RagProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RAG: report_chunks. useChroma=true면 Chroma에 벡터, PostgreSQL에는 content만. 아니면 pgvector/JSONB.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReportChunkRepository {

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

    public void deleteByReportId(long reportId) {
        jdbc.update("DELETE FROM projectdb.report_chunks WHERE report_id = ?", reportId);
    }

    /**
     * Chroma 사용 시: embedding 없이 INSERT 후 생성된 chunk_id 반환. 그 외에는 0 반환.
     */
    public long insertReturningId(long reportId, long userId, String yearMonth, int chunkIndex, String content, String docType) {
        if (!ragProperties.isUseChroma()) return 0;
        String type = (docType != null && !docType.isBlank()) ? docType : "chunk";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
            con -> {
                var ps = con.prepareStatement(
                    "INSERT INTO projectdb.report_chunks (report_id, user_id, year_month, chunk_index, content, doc_type) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS
                );
                ps.setLong(1, reportId);
                ps.setLong(2, userId);
                ps.setString(3, yearMonth);
                ps.setInt(4, chunkIndex);
                ps.setString(5, content);
                ps.setString(6, type);
                return ps;
            },
            keyHolder
        );
        var key = keyHolder.getKey();
        return key != null ? key.longValue() : 0;
    }

    public void insert(long reportId, long userId, String yearMonth, int chunkIndex, String content, float[] embedding) {
        insert(reportId, userId, yearMonth, chunkIndex, content, embedding, "chunk");
    }

    public void insert(long reportId, long userId, String yearMonth, int chunkIndex, String content, float[] embedding, String docType) {
        if (ragProperties.isUseChroma()) {
            return;
        }
        if (embedding == null || embedding.length == 0) return;
        String type = (docType != null && !docType.isBlank()) ? docType : "chunk";
        if (ragProperties.isUseJsonb()) {
            String json = toJsonArrayLiteral(embedding);
            jdbc.update(
                    "INSERT INTO projectdb.report_chunks (report_id, user_id, year_month, chunk_index, content, embedding, doc_type) " +
                            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)",
                    reportId, userId, yearMonth, chunkIndex, content, json, type
            );
        } else {
            String v = toJsonArrayLiteral(embedding);
            jdbc.update(
                    "INSERT INTO projectdb.report_chunks (report_id, user_id, year_month, chunk_index, content, embedding, doc_type) " +
                            "VALUES (?, ?, ?, ?, ?, ?::vector, ?)",
                    reportId, userId, yearMonth, chunkIndex, content, v, type
            );
        }
    }

    /**
     * 해당 사용자·연월의 리포트 청크 content 목록 (chunk_index 순). QA 근거 보조용.
     */
    public List<String> selectContentByUserAndYearMonth(long userId, String yearMonth) {
        String yyyyMm = yearMonth != null ? yearMonth.replace("-", "").trim() : null;
        if (yyyyMm == null) return List.of();
        return jdbc.query(
            "SELECT content FROM projectdb.report_chunks WHERE user_id = ? AND year_month = ? ORDER BY chunk_index",
            (rs, i) -> rs.getString("content"),
            userId, yyyyMm
        );
    }

    /**
     * user_id, year_month 로 필터, 쿼리 벡터와 코사인 거리 기준 상위 k개 청크 조회.
     * JSONB 모드에서는 Spring에서 사용하지 않음 (Python RAG에서 검색).
     */
    public List<ReportChunk> findNearest(long userId, String yearMonth, float[] queryEmbedding, int k) {
        if (queryEmbedding == null || queryEmbedding.length == 0) return List.of();
        if (ragProperties.isUseJsonb()) {
            return List.of();
        }
        String v = toJsonArrayLiteral(queryEmbedding);
        return jdbc.query(
                "SELECT chunk_id, report_id, user_id, year_month, chunk_index, content, doc_type " +
                        "FROM projectdb.report_chunks " +
                        "WHERE user_id = ? AND year_month = ? " +
                        "ORDER BY embedding <=> ?::vector " +
                        "LIMIT ?",
                (rs, i) -> new ReportChunk(
                        rs.getLong("chunk_id"),
                        rs.getLong("report_id"),
                        rs.getLong("user_id"),
                        rs.getString("year_month"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getString("doc_type")
                ),
                userId, yearMonth, v, k
        );
    }
}
