# SmartBudget 프로젝트 기획안 및 보고서

> 프레젠테이션(PPT) 제작용 기획안·보고서  
> 문서 작성일: 2026년 2월 4일

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [프로젝트 기획](#2-프로젝트-기획)
3. [프로젝트 설계](#3-프로젝트-설계)
4. [주요 화면별 로직 및 기능 상세](#4-주요-화면별-로직-및-기능-상세)

---

## 1. 프로젝트 개요

### 1.1 추진 배경 및 목적

- **배경**: 개인 재정 관리의 디지털 전환 수요 증가, 영수증·카드 내역의 수동 입력 부담, 예산 대비 지출 파악의 어려움.
- **목적**:  
  - **영수증 OCR**로 입력 편의성 향상 및 오입력 감소  
  - **카테고리 자동 분류**(ML/룰 기반)로 가계부 정리 자동화  
  - **월별 AI 리포트**로 소비 패턴 요약 및 질의응답(RAG) 제공  
  - **소비 패턴 기반 금융상품·카드 추천**으로 맞춤형 금융 정보 제공  

### 1.2 기존 서비스와 차이점

| 구분 | 기존 가계부/자산관리 앱 | SmartBudget |
|------|-------------------------|-------------|
| **데이터 입력** | 수동 입력 위주 | 영수증 촬영 → OCR → 상호/금액/날짜 자동 추출, 카테고리 자동 분류 |
| **분류 방식** | 사용자 직접 카테고리 선택 | PaddleOCR + ML/룰 기반 자동 분류, 필요 시 Gemini 보조 |
| **리포트** | 고정형 차트·표 | 월별 메트릭 + **LLM 요약** + **RAG 기반 질의응답**(pgvector) |
| **추천** | 정적 상품 목록 | **소비 패턴 분석 → Gemini 기반 카드·적금/예금 추천** 및 사유 텍스트 제공 |
| **인프라** | 단일 백엔드 | Spring Boot + Python OCR/ML 서버 + (선택) AWS S3 영수증 저장 |

### 1.3 팀원 역할

- **한동해**: 백엔드(Spring Boot), 영수증·S3·OCR 연동, API 설계, DB 스키마 및 수정사항 정리  
- **김윤호**: 프론트엔드(React), 로그인/라우팅(OAuth·JWT), UI/UX 및 클라이언트 연동  
- *(추가 팀원이 있다면 역할을 보완하여 기입)*  
- **공통**: OCR 파이프라인(OCR_receipts), ML 서버(ml-server), AI 리포트·RAG·추천 로직 연동

### 1.4 개발 환경

| 구분 | 기술 스택 |
|------|-----------|
| **프론트엔드** | React 19, Vite 7, TypeScript, React Router 7, Tailwind CSS 4, Recharts, Lucide React |
| **백엔드** | Java 17, Spring Boot 3.5, Spring Security, OAuth2(카카오), JWT, MyBatis, PostgreSQL |
| **OCR/ML 서버** | Python, FastAPI, PaddleOCR, Scikit-learn(Naive Bayes + TF-IDF), 룰 기반 분류(rule_seed) |
| **AI 서버(ml-server)** | Python, FastAPI, Gemini API(임베딩·LLM), Tesseract/EasyOCR(옵션) |
| **AI/LLM** | Google Gemini(임베딩 text-embedding-004, LLM 1.5 Flash), RAG(pgvector 768차원) |
| **인프라·저장** | AWS S3(영수증 이미지), PostgreSQL + pgvector(벡터 검색) |
| **빌드·실행** | Gradle(bootRun), npm/vite(dev/build), uvicorn(OCR_receipts, ml-server) |

### 1.5 일정 계획

- **1단계(기획·설계)**: 요구사항 정의, 시스템 흐름도·ERD 설계, API 명세  
- **2단계(백엔드·DB)**: Spring Boot API, 인증(OAuth2/JWT), 거래/예산/카테고리/영수증 CRUD, S3 연동  
- **3단계(OCR·ML)**: OCR_receipts 파이프라인(PaddleOCR → 라인 그룹핑 → 파싱 → 카테고리 분류), ml-server(분류·임베딩·LLM)  
- **4단계(프론트)**: 랜딩·로그인·대시보드·가계부·예산·추천·AI 리포트 화면, 영수증 업로드·OCR 미리보기 연동  
- **5단계(AI·추천)**: 월별 리포트 생성·RAG 청크 저장·질의응답, 소비 패턴 기반 금융상품 추천  
- **6단계(테스트·배포)**: 통합 테스트, 배포 환경 설정  

*(실제 마일스톤 일자는 팀 내 일정에 맞게 구체화)*

---

## 2. 프로젝트 기획

### 2.1 영수증 OCR 및 카테고리 구분

- **흐름**: 이미지 업로드 → **Detection(박스)** → **Recognition(글자)** → 라인 그룹핑/머지 → 필드 파싱(MERCHANT/DATE/ITEMS/TOTAL) → **카테고리 분류**(ML 또는 룰 fallback) → DB 저장·거래 생성.
- **구성**  
  - **OCR_receipts**(FastAPI): PaddleOCR 기반 텍스트 추출, 라인 그룹핑, 규칙 기반 정보 추출(total_mapping), ML 텍스트 분류기(Naive Bayes + TF-IDF) 또는 rule_seed 룰 기반 분류.  
  - **백엔드**: `processOcrOnly`(미리보기·폼 자동 입력), `saveReceiptAndCreateTransaction`(S3 업로드 → receipt insert → OCR 호출 → ocr_raw_json/ocr_parsed_json 갱신 → 거래 생성).  
- **카테고리**: Python ML 모델 또는 Gemini 분류 중 선택 가능(`ml.classifier.engine`, `ocr.engine`).

#### 2.1.1 OCR_receipts: PaddleOCR과 머신러닝 데이터 흐름

아래는 영수증 이미지가 입력된 뒤 최종 파싱·분류 결과까지 이어지는 **시스템 데이터 흐름**을 단계별로 정리한 것이다.

| 단계 | 구간 | 설명 |
|------|------|------|
| **1** | **영수증 이미지 등록 (User Input)** | 사용자가 Gradio/API를 통해 영수증 이미지(파일 또는 바이트)를 업로드한다. |
| | | 파이프라인 진입점: `process_ocr_lines_parsed(image_bytes, filename)` 또는 FastAPI `POST /ocr`, `POST /process`. |
| | | 이미지는 임시 경로에 저장된 뒤 PaddleOCR 입력으로 전달된다. |
| **2** | **PaddleOCR 실행 (검출·방향분류·인식)** | `run_ocr_to_json.ocr_image_to_raw_json()`에서 PaddleOCR(`lang="korean"`, `use_angle_cls=True`)을 호출한다. |
| | | **Detection** → 텍스트 영역 박스 추출, **Angle classification** → 회전 보정, **Recognition** → 각 박스 내 글자 인식. |
| | | 출력: `raw_json` = `{ "image": 파일명, "items": [ { "box", "text", "score" }, ... ] }`. |
| **3** | **Raw 결과 필터링** | OCR items에 대해 신뢰도(`score < 0.5`)·최소 크기(높이/너비) 기준으로 노이즈 박스를 제거한다. |
| | | 너무 작은 박스(점선·잔상 등)를 걸러 내어 이후 라인 그룹핑·파싱 품질을 높인다. |
| **4** | **토큰 빌드 및 라인 그룹핑** | `line_grouping.build_tokens()`로 items → `Token` 리스트로 변환 후, `group_tokens_into_lines()`로 **y좌표·라인 높이 기반** 그룹핑. |
| | | 영수증 헤더/본문/합계 등 글자 크기가 달라도, 라인별 높이(median)에 맞춘 `y_threshold_ratio`로 같은 줄을 묶는다. |
| | | 결과: 줄 단위 `List[List[Token]]` → `lines_to_json()`으로 `lines_json`(줄별 `line_text`, `tokens`) 생성. |
| **5** | **라인별 텍스트 결합 (공백 보정)** | 4번에서 묶인 줄별 토큰을 **x좌표 순**으로 정렬한 뒤, `join_tokens_with_spacing()`으로 인접 토큰 사이 **실제 픽셀 간격**(gap = 다음 토큰 x1 − 이전 토큰 x2)을 계산한다. |
| | | 간격이 **문자당 폭 중앙값(med_char_w) × gap_ratio(기본 0.9)** 이상이면 공백을 삽입해, "가맹점명" 같은 긴 토큰과 "1,000원" 같은 짧은 토큰이 섞여도 자연스러운 한 줄 문자열이 만들어진다. |
| | | `lines_to_json()` 내부에서 각 줄마다 이 처리를 적용해 `line_text`를 채우며, 동시에 `line_bbox`·`tokens` 메타데이터를 담아 receipt_mapping 단계로 넘긴다. |
| **6** | **영수증 필드 추출 (receipt_mapping)** | 5번까지 만든 줄 단위 텍스트(`lines`)를 대상으로 하는 **OCR 결과 후처리** 단계다. `extract_receipt_fields(lines)`에서 **정규식·키워드**로 가맹점·날짜·시간·상품 항목·합계 금액을 추출한다. |
| | | 가맹점: "가맹점명 xxx", 사업자번호 라인, "...점/지점" 패턴 등 우선순위 적용. 합계: "합계/결제금액" 키워드·결제수단 맥락 fallback. |
| | | 출력: `ReceiptResult` → `parsed_json`(merchant, date, datetime, items, total, total_confidence). |
| **7** | **구조화된 파싱 결과 출력** | `raw_json`, `lines_json`, `parsed_json`이 API/Gradio 응답으로 반환된다. |
| | | 프론트/백엔드에서는 `parsed_json`으로 폼 자동 입력·거래 생성에 사용하고, 필요 시 `raw_json`/`lines_json`으로 디버깅·재가공한다. |
| **8** | **(선택) ML 카테고리 분류** | `POST /classify` 등에서 **가맹점명 + items** 텍스트를 `models/textclf`의 학습된 분류기(TF-IDF + scikit-learn)에 넣어 **카테고리**를 예측한다. |
| | | 신뢰도·margin이 충분하면 ML 결과를 쓰고, 그렇지 않으면 `rule_seed` 룰 기반 분류로 fallback. |
| | | `POST /process`는 OCR+파싱까지 수행하고, 카테고리는 현재 룰 기반으로 붙이며, 별도 `/classify` 호출 시 ML이 사용된다. |

- **요약**: **1** 사용자 이미지 → **2~3** PaddleOCR 및 필터링 → **4~5** 라인 그룹핑·텍스트 결합 → **6~7** 규칙 기반 필드 추출·구조화 출력 → **8** (선택) ML 카테고리 분류.

### 2.2 금융상품 추천 서비스

- **목적**: 사용자 월별 소비 패턴(총 지출, 카테고리별 지출, 거래 건수, 최다 사용 카테고리)을 분석하여 맞춤형 카드·금융상품을 추천.
- **구현**:  
  - 소비 패턴 분석(총 지출, 카테고리별 금액, 상위 카테고리)  
  - 카드/상품 마스터 조회 후 **Gemini**로 추천 문장 생성  
  - 추천 결과를 `recommendations` 테이블에 저장(rec_type: CARD/PRODUCT, item_id, score, reason_text)  
  - 프론트: 추천 카드·적금/예금 등 상품 카드 및 AI 사유 표시

### 2.3 AI 리포트

- **내용**: 월별 거래 메트릭(수입/지출/잔액, 카테고리별 비중) + **LLM 요약 텍스트** + **RAG 기반 질의응답**.
- **구성**  
  - **월별 리포트 생성**: 지난달 데이터로 메트릭·요약 생성, `monthly_reports` 저장, 리포트 청크 분할 후 **Gemini 임베딩** → `report_chunks`(pgvector) 저장.  
  - **RAG 답변**: 사용자 질문 → 질문 임베딩 → pgvector 유사도 검색(top_k) → 검색된 청크 + 질문으로 LLM 답변 생성.  
- **프론트**: 월 선택, 요약 표시, 고정 질문 카드(예산/재정 상태, 최다 지출, 절약 팁, 전체 요약) 및 직접 질문 입력.

---

## 3. 프로젝트 설계

### 3.1 시스템 흐름도

```
[사용자]
   │
   ├─ 랜딩/로그인 (OAuth2 카카오, JWT)
   │
   ▼
[프론트엔드 React]
   │
   ├─ 대시보드: 이번 달 지출·예산 사용률, 카테고리·일별 추이 차트
   ├─ 거래 등록: 수동 입력 또는 영수증 업로드 → OCR 미리보기 → 저장
   ├─ 가계부: 거래 목록 조회/수정/삭제
   ├─ 예산/목표: 월 예산, 카테고리별 예산, 저축 목표
   ├─ 추천: 소비 패턴 기반 카드·금융상품 추천 (Gemini)
   └─ AI 리포트: 월별 요약 + RAG 질의응답 (pgvector + Gemini)
   │
   ▼
[Spring Boot 백엔드]
   │
   ├─ 인증: JWT 발급/검증, OAuth2 연동
   ├─ 거래/예산/카테고리/결제수단/저축목표 CRUD
   ├─ 영수증: S3 업로드, receipt 저장, OCR 연동
   │     └─ OCR_receipts (Python) ← PaddleOCR, ML/룰 분류
   ├─ 월별 리포트: 메트릭 집계, Python AI 서버 호출(요약·RAG)
   └─ 추천: 소비 패턴 분석 → Gemini 추천 문장 → recommendations 저장
   │
   ▼
[PostgreSQL]  ←  [pgvector] (report_chunks 임베딩)
   │
   ▼
[Python AI 서버 ml-server] (선택)
   ├─ 임베딩 / RAG 검색 / LLM 요약·답변
   └─ Gemini API
```

### 3.2 주요 서비스 화면

| 화면 | 경로 | 설명 |
|------|------|------|
| **랜딩** | `/` | 서비스 소개, 로그인/시작하기 |
| **로그인** | `/login` | 이메일/비밀번호 또는 카카오 OAuth, JWT 발급 |
| **대시보드** | `/app/dashboard` | 환영 문구, 이번 달 지출·예산 사용률, 카테고리 파이 차트, 최근 7일 추이 |
| **거래 추가** | `/app/add` | 수동 입력 또는 영수증 업로드 → OCR 미리보기 → 금액/상호/날짜/카테고리 자동 채움 → 저장 |
| **가계부** | `/app/ledger` | 거래 목록(날짜/금액/메모/카테고리), 수정·삭제 |
| **예산·목표** | `/app/budget` | 월 예산 설정, 카테고리별 예산, 저축 목표 |
| **추천** | `/app/recommendations` | 소비 요약, 카드 추천, 적금/예금 등 금융상품 추천, AI 추천 사유 |
| **AI 리포트** | `/app/ai-report` | 월 선택, LLM 요약, 고정 질문(예산 상태/최다 지출/절약 팁/요약) 및 직접 질문, RAG 답변 |
| **설정** | `/app/settings` | 마이페이지/설정 |

### 3.3 ERD

**핵심 테이블 요약**

- **users**: user_id, email, password_hash, created_at  
- **categories**: category_id, name, parent_id  
- **payment_methods**: method_id, name  
- **transactions**: tx_id, tx_datetime, amount, merchant, memo, source, user_id, method_id, category_id, created_at  
- **budgets**: budget_id, year_month, total_budget, user_id  
- **category_budgets**: cat_budget_id, year_month, budget_amount, user_id, category_id  
- **category_predictions**: pred_id, tx_id, predicted_category_id, user_final_category_id, model_name, confidence, corrected  
- **saving_goals**: goal_id, goal_amount, start_date, target_date, monthly_target, user_id  
- **goal_transactions**: goal_tx_id, goal_id, tx_id, amount  
- **cards**: card_id, name, company, benefits_json, tags  
- **user_cards**: user_card_id, user_id, card_id  
- **products**: product_id, type, name, bank, rate, conditions_json, tags  
- **receipt_files**: file_id, url_path, ocr_raw_json, ocr_parsed_json, status, user_id, tx_id, created_at  
- **recommendations**: rec_id, year_month, rec_type, item_id, score, reason_text, user_id, created_at  
- **monthly_reports**: report_id, year_month, user_id, metrics_json, llm_summary_text, llm_model, is_current_month, created_at  
- **report_chunks**: chunk_id, report_id, user_id, year_month, chunk_index, content, embedding vector(768), created_at  

**관계 요약**

- users ← transactions, budgets, category_budgets, saving_goals, user_cards, receipt_files, recommendations, monthly_reports, report_chunks  
- categories ← transactions, category_budgets, category_predictions  
- transactions ← category_predictions, goal_transactions, receipt_files  
- monthly_reports ← report_chunks  
- cards, products ← recommendations(item_id, rec_type으로 구분)

---

## 4. 주요 화면별 기능 기술 설명

### 4.1 시작화면 (Landing)

- `/` 경로 접근 시 `authService.isAuthenticated()` 검사 → 인증 시 `/app/dashboard`로 리다이렉트
- 비인증 시 Landing 컴포넌트 렌더, Hero·기능 카드 정적 컨텐츠 표시
- CTA: "무료로 시작하기"/"로그인" 클릭 시 `navigate("/login")` 실행

### 4.2 대시보드 (Dashboard)

- **데이터 소스**: App 마운트 시 `GET /api/transactions?userId={uid}` 호출 → `transactions` 테이블에서 사용자별 전체 거래 조회 후 state 저장, Dashboard에 props로 전달
- **이번 달 지출 총액**: 클라이언트에서 `transactions` 배열을 `type === 'EXPENSE'` 및 `tx_datetime` 기준 현재 월로 필터한 뒤 `amount` 합산(SUM)
- **예산 대비 사용률**: 지출 총액 ÷ 월 예산(`budget.monthlyBudget`) × 100, 80% 초과 시 UI 색상 변경(rose)
- **최근 7일 지출 트렌드**: `transactions`에서 최근 7일 일별 `EXPENSE` amount를 집계하여 Recharts `AreaChart`로 시각화
- **카테고리별 지출 비중**: `transactions`를 `category` 키 기준 그룹핑 후 amount 합산, Recharts `PieChart`(도넛) 및 상위 5개 리스트로 표시

### 4.3 거래 추가 (TransactionForm)

- **수동 입력**: 폼 필드(date, category, amount, merchant) 입력 후 "거래 저장" → `POST /api/transactions` (JSON body) → `transactions` 테이블 INSERT, `source='MANUAL'`
- **영수증 OCR(미리보기)**: 이미지 선택 시 `POST /api/receipts/ocr-only` (FormData multipart) → OCR 파이프라인 실행, DB/S3 저장 없이 JSON 응답 반환 → 응답의 storeName/totalAmount/date/classification으로 폼 필드 자동 채움
- **영수증 포함 저장**: "거래 저장" 클릭 시 `POST /api/receipts/save-and-create-transaction` (FormData: file + date, amount, merchant, type, categoryId, userId) → S3 업로드, `receipt_files` INSERT, OCR 실행 후 ocr_raw_json/ocr_parsed_json 갱신, `transactions` INSERT
- **카테고리 옵션**: `GET /api/categories`로 `categories` 테이블 조회, `parent_id IS NULL`인 부모 카테고리만 select 옵션으로 렌더

### 4.4 가계부 (Ledger)

- **데이터 소스**: App에서 동일한 `transactions` state 전달 (거래 추가/삭제 시 `loadTransactions` 재호출로 동기화)
- **월간 달력**: `currentViewDate`(년/월)로 표시 월 변경, 달력 그리드 각 셀에 해당 날짜의 수입/지출 amount SUM 표시
- **거래 목록**: `transactions`를 `tx_datetime` 기준 현재 표시 월로 필터, 선택된 날짜(`selectedDate`)가 있으면 해당 일자만 추가 필터 → 날짜별 그룹핑 후 내림차순 정렬하여 렌더
- **삭제**: 행 삭제 버튼 클릭 시 `DELETE /api/transactions/{txId}` 호출 → `transactions` 테이블 DELETE, 성공 시 App의 `transactions` state에서 해당 id 제거

### 4.5 AI 리포트 (AIReport)

- **데이터 소스**: `transactions`, `budget`(월 예산)은 App에서 props 전달
- **월 목록 로드**: 마운트 시 `GET /api/monthly-reports?userId={uid}` → `monthly_reports` 테이블에서 사용자별 리포트 목록 조회, `yearMonth` 내림차순 정렬 후 select 옵션 렌더
- **리포트 조회**: `selectedYearMonth` 변경 시 `GET /api/monthly-reports/{yearMonth}` → `monthly_reports` 조회, 응답의 `metrics_json`, `llm_summary_text`를 `parseReportToAnalysis`로 파싱하여 재정 위험 분석·반복 패턴 Top3·절약 팁 UI 표시
- **리포트 생성**: "이번 달 리포트 생성" 클릭 시 `POST /api/monthly-reports/current/generate` (JSON: `userId`, `monthlyBudget`) → 백엔드에서 `transactions` 테이블 해당 월 조회, `calculateMetrics`로 totalIncome/totalExpense/netAmount/categoryExpenses 집계 → Python AI 서버(Gemini LLM) `generateAnalysisSummary` 호출 → `monthly_reports` INSERT 또는 UPDATE (`metrics_json`, `llm_summary_text`) → RAG 활성화 시 `report_chunks` 청킹·임베딩(Gemini text-embedding-004) 저장
- **고정 질문 카드**: 예산/재정 상태, 최다 지출, 절약 팁, 전체 요약 클릭 시 이미 로드된 `report` 객체에서 `riskAssessment`, `topPatterns`, `savingTips`를 조합하여 즉시 채팅 메시지로 표시 (추가 API 호출 없음)
- **직접 질문**: 사용자 입력 질문 전송 시 `POST /api/monthly-reports/ask` (JSON: `yearMonth`, `question`) → RAG 활성 시 pgvector 유사도 검색(top-k) 후 검색 청크 + 질문으로 LLM 답변 생성; 비활성 시 `metrics_json` + `llm_summary_text`를 맥락으로 Python AI `answerReportQuestion` 호출 → 응답 `answer`를 채팅 메시지로 표시

---

## 부록: 참고 파일

- 백엔드 스키마: `backend/smartbudget/src/main/resources/schema.sql`, `schema_monthly_reports.sql`, `schema_rag.sql`  
- OCR 파이프라인: `OCR_receipts/README.md`  
- ML/AI 서버: `ml-server/README.md`  
- 수정 이력: `한동해의 수정사항.txt`, `김윤호의 수정사항.txt`

---

*이 문서는 SmartBudget 프로젝트 코드베이스와 리소스를 기반으로 작성되었으며, PPT 제작 시 위 목차와 표·ERD 요약을 슬라이드별로 배치하면 됩니다.*
