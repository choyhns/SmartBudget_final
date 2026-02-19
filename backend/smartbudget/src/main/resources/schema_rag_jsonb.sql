-- RAG: report_chunks (pgvector 없이 embedding을 JSONB로 저장)
-- Railway 등 pgvector 미지원 환경용. 유사도 검색은 앱(Python/Java)에서 수행.

CREATE TABLE IF NOT EXISTS projectdb.report_chunks (
    chunk_id    bigserial PRIMARY KEY,
    report_id   bigint NOT NULL,
    user_id     bigint NOT NULL,
    year_month  char(6) NOT NULL,
    chunk_index int NOT NULL,
    content     text NOT NULL,
    embedding   jsonb NULL,
    doc_type    varchar(32) DEFAULT 'chunk',
    created_at  timestamptz DEFAULT current_timestamp,
    CONSTRAINT report_chunks_report_fkey
        FOREIGN KEY (report_id) REFERENCES projectdb.monthly_reports(report_id) ON DELETE CASCADE,
    CONSTRAINT report_chunks_user_fkey
        FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_report_chunks_report_id ON projectdb.report_chunks(report_id);
CREATE INDEX IF NOT EXISTS idx_report_chunks_user_year ON projectdb.report_chunks(user_id, year_month);

COMMENT ON TABLE projectdb.report_chunks IS 'RAG: 월별 리포트 청크. embedding은 JSONB 배열(768차원). pgvector 없이 앱에서 유사도 계산.';
