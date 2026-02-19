-- Monthly Reports 테이블
CREATE TABLE IF NOT EXISTS projectdb.monthly_reports (
	report_id bigserial NOT NULL,
	year_month bpchar(6) NULL,
	metrics_json jsonb NULL,
	llm_summary_text text NULL,
	llm_model varchar NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	user_id int8 NOT NULL,
	is_current_month bool DEFAULT false NULL,
	CONSTRAINT monthly_reports_pkey PRIMARY KEY (report_id),
	CONSTRAINT monthly_reports_user_id_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_monthly_reports_user_id ON projectdb.monthly_reports(user_id);
CREATE INDEX IF NOT EXISTS idx_monthly_reports_year_month ON projectdb.monthly_reports(year_month);
CREATE INDEX IF NOT EXISTS idx_monthly_reports_current ON projectdb.monthly_reports(user_id, is_current_month) WHERE is_current_month = true;
