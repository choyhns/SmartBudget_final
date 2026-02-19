-- Users 테이블 (OAuth/프로필 포함)
CREATE TABLE IF NOT EXISTS projectdb.users (
	user_id bigserial NOT NULL,
	email varchar NOT NULL,
	password_hash varchar NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	"name" varchar(20) NULL,
	phone varchar(20) NULL,
	addr varchar(100) NULL,
	birth date NULL,
	gender varchar(10) NULL,
	photo text NULL,
	provider varchar(20) DEFAULT 'local'::character varying NOT NULL,
	provider_id varchar(100) NULL,
	"role" varchar(20) DEFAULT 'USER'::character varying NOT NULL,
	CONSTRAINT users_email_key UNIQUE (email),
	CONSTRAINT users_pkey PRIMARY KEY (user_id)
);

-- Categories 테이블
CREATE TABLE IF NOT EXISTS projectdb.categories (
	category_id bigserial NOT NULL,
	"name" varchar NOT NULL,
	parent_id int8 NULL,
	CONSTRAINT categories_pkey PRIMARY KEY (category_id),
	CONSTRAINT categories_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES projectdb.categories(category_id)
);

-- Payment Methods 테이블
CREATE TABLE IF NOT EXISTS projectdb.payment_methods (
	method_id bigserial NOT NULL,
	"name" varchar NOT NULL,
	CONSTRAINT payment_methods_pkey PRIMARY KEY (method_id)
);

-- Transactions 테이블
CREATE TABLE IF NOT EXISTS projectdb.transactions (
	tx_id bigserial NOT NULL,
	tx_datetime timestamp NOT NULL,
	amount numeric NOT NULL,
	merchant varchar NULL,
	memo text NULL,
	"source" varchar NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	user_id int8 NOT NULL,
	method_id int8 NULL,
	category_id int8 NULL,
	CONSTRAINT transactions_pkey PRIMARY KEY (tx_id),
	CONSTRAINT transactions_category_id_fkey FOREIGN KEY (category_id) REFERENCES projectdb.categories(category_id),
	CONSTRAINT transactions_method_id_fkey FOREIGN KEY (method_id) REFERENCES projectdb.payment_methods(method_id),
	CONSTRAINT transactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);

-- Budgets 테이블
CREATE TABLE IF NOT EXISTS projectdb.budgets (
	budget_id bigserial NOT NULL,
	year_month bpchar(6) NOT NULL,
	total_budget numeric NOT NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	updated_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	user_id int8 NOT NULL,
	CONSTRAINT budgets_pkey PRIMARY KEY (budget_id),
	CONSTRAINT budgets_user_id_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);

-- Category Budgets 테이블
CREATE TABLE IF NOT EXISTS projectdb.category_budgets (
	cat_budget_id bigserial NOT NULL,
	year_month bpchar(6) NOT NULL,
	budget_amount numeric NOT NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	user_id int8 NOT NULL,
	category_id int8 NOT NULL,
	CONSTRAINT category_budgets_pkey PRIMARY KEY (cat_budget_id),
	CONSTRAINT category_budgets_category_id_fkey FOREIGN KEY (category_id) REFERENCES projectdb.categories(category_id),
	CONSTRAINT category_budgets_user_id_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);

-- Category Predictions 테이블
CREATE TABLE IF NOT EXISTS projectdb.category_predictions (
	pred_id bigserial NOT NULL,
	model_name varchar NULL,
	model_version varchar NULL,
	confidence numeric NULL,
	corrected bool DEFAULT false NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	tx_id int8 NOT NULL,
	predicted_category_id int8 NULL,
	user_final_category_id int8 NULL,
	CONSTRAINT category_predictions_pkey PRIMARY KEY (pred_id),
	CONSTRAINT category_predictions_predicted_category_id_fkey FOREIGN KEY (predicted_category_id) REFERENCES projectdb.categories(category_id),
	CONSTRAINT category_predictions_tx_id_fkey FOREIGN KEY (tx_id) REFERENCES projectdb.transactions(tx_id) ON DELETE CASCADE,
	CONSTRAINT category_predictions_user_final_category_id_fkey FOREIGN KEY (user_final_category_id) REFERENCES projectdb.categories(category_id)
);

-- Saving Goals 테이블
CREATE TABLE IF NOT EXISTS projectdb.saving_goals (
	goal_id bigserial NOT NULL,
	goal_amount numeric NOT NULL,
	start_date date NULL,
	target_date date NULL,
	monthly_target numeric NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	user_id int8 NOT NULL,
	CONSTRAINT saving_goals_pkey PRIMARY KEY (goal_id),
	CONSTRAINT saving_goals_user_id_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);

-- Goal Transactions 테이블
CREATE TABLE IF NOT EXISTS projectdb.goal_transactions (
	goal_tx_id bigserial NOT NULL,
	amount numeric NOT NULL,
	goal_id int8 NOT NULL,
	tx_id int8 NOT NULL,
	CONSTRAINT goal_transactions_pkey PRIMARY KEY (goal_tx_id),
	CONSTRAINT goal_transactions_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES projectdb.saving_goals(goal_id) ON DELETE CASCADE,
	CONSTRAINT goal_transactions_tx_id_fkey FOREIGN KEY (tx_id) REFERENCES projectdb.transactions(tx_id) ON DELETE CASCADE
);

-- Cards 테이블
CREATE TABLE IF NOT EXISTS projectdb.cards (
	card_id bigserial NOT NULL,
	"name" varchar NOT NULL,
	company varchar NULL,
	benefits_json jsonb NULL,
	image_url text NULL,
	link text NULL,
	tags _varchar NULL,
	CONSTRAINT cards_pkey PRIMARY KEY (card_id)
);

-- User Cards 테이블
CREATE TABLE IF NOT EXISTS projectdb.user_cards (
	user_card_id bigserial NOT NULL,
	user_id int8 NOT NULL,
	card_id int8 NOT NULL,
	CONSTRAINT user_cards_pkey PRIMARY KEY (user_card_id),
	CONSTRAINT user_cards_card_id_fkey FOREIGN KEY (card_id) REFERENCES projectdb.cards(card_id) ON DELETE CASCADE,
	CONSTRAINT user_cards_user_id_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);

-- Products 테이블
CREATE TABLE IF NOT EXISTS projectdb.products (
	product_id bigserial NOT NULL,
	"type" varchar NULL,
	"name" varchar NULL,
	bank varchar NULL,
	rate numeric NULL,
	conditions_json jsonb NULL,
	tags _varchar NULL,
	CONSTRAINT products_pkey PRIMARY KEY (product_id)
);

-- Receipt Files 테이블
CREATE TABLE IF NOT EXISTS projectdb.receipt_files (
	file_id bigserial NOT NULL,
	url_path text NOT NULL,
	ocr_raw_json jsonb NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	user_id int8 NOT NULL,
	tx_id int8 NULL,
	ocr_parsed_json jsonb NULL,
	status varchar(20) DEFAULT 'UPLOADED'::character varying NOT NULL,
	CONSTRAINT receipt_files_pkey PRIMARY KEY (file_id),
	CONSTRAINT receipt_files_status_chk CHECK (((status)::text = ANY ((ARRAY['UPLOADED'::character varying, 'OCR_DONE'::character varying, 'CLASSIFIED'::character varying, 'CONFIRMED'::character varying, 'FAILED'::character varying])::text[]))) NOT VALID,
	CONSTRAINT receipt_files_tx_id_fkey FOREIGN KEY (tx_id) REFERENCES projectdb.transactions(tx_id) ON DELETE CASCADE,
	CONSTRAINT receipt_files_user_id_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);

-- Recommendations 테이블
CREATE TABLE IF NOT EXISTS projectdb.recommendations (
	rec_id bigserial NOT NULL,
	year_month bpchar(6) NULL,
	rec_type varchar NULL,
	item_id int8 NULL,
	score numeric NULL,
	reason_text text NULL,
	created_at timestamp DEFAULT CURRENT_TIMESTAMP NULL,
	user_id int8 NOT NULL,
	CONSTRAINT recommendations_pkey PRIMARY KEY (rec_id),
	CONSTRAINT recommendations_user_id_fkey FOREIGN KEY (user_id) REFERENCES projectdb.users(user_id) ON DELETE CASCADE
);
