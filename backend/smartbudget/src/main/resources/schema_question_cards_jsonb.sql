-- 질문 카드 (pgvector 없이 embedding을 JSONB로 저장)
-- Railway 등 pgvector 미지원 환경용. 유사도 검색은 Spring에서 수행.

CREATE TABLE IF NOT EXISTS projectdb.question_cards (
    card_id       bigserial PRIMARY KEY,
    question_text text NOT NULL,
    tag           varchar(32) NOT NULL,
    category_label varchar(64) NOT NULL,
    sort_order    int DEFAULT 0,
    embedding     jsonb NULL,
    created_at    timestamptz DEFAULT current_timestamp
);

CREATE INDEX IF NOT EXISTS idx_question_cards_tag ON projectdb.question_cards(tag);

COMMENT ON TABLE projectdb.question_cards IS 'AI 리포트 질문 카드. embedding은 JSONB 배열. pgvector 없이 앱에서 유사도 계산.';

CREATE TABLE IF NOT EXISTS projectdb.question_card_clicks (
    id         bigserial PRIMARY KEY,
    user_id    bigint NOT NULL,
    year_month char(6) NOT NULL,
    card_id    bigint NOT NULL,
    clicked_at timestamptz DEFAULT current_timestamp,
    CONSTRAINT qc_clicks_user_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE,
    CONSTRAINT qc_clicks_card_fkey FOREIGN KEY (card_id) REFERENCES projectdb.question_cards(card_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_qc_clicks_user_year ON projectdb.question_card_clicks(user_id, year_month);

COMMENT ON TABLE projectdb.question_card_clicks IS '질문 카드 클릭 로그.';
