# RAG (질문 카드 Q&A) 설정

리포트 Q&A를 **RAG** 방식으로 동작시키려면 아래 순서로 설정하면 됩니다.

## 1. pgvector 확장 설치

PostgreSQL에 [pgvector](https://github.com/pgvector/pgvector) 확장이 설치되어 있어야 합니다.

```bash
# Ubuntu/Debian
sudo apt install postgresql-16-pgvector   # 버전은 사용 중인 PG에 맞게

# Mac (Homebrew)
brew install pgvector
```

DB에 확장 생성:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

## 2. 스키마 적용

`schema_rag.sql` 실행:

```bash
psql -h <host> -U <user> -d <db> -f src/main/resources/schema_rag.sql
```

또는 기존 마이그레이션/스키마 실행 방식에 맞춰 `schema_rag.sql` 내용을 포함시키면 됩니다.

## 3. 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `RAG_ENABLED` | RAG 인덱싱/질문 답변 사용 여부 | `false` |
| `RAG_TOP_K` | 검색할 청크 개수 | `5` |
| `RAG_EMBEDDING_MODEL` | 임베딩 모델 (Python 쪽) | `text-embedding-004` |
| `GEMINI_API_KEY` | Gemini API 키 (Python AI Service에서 LLM/임베딩 사용) | - |
| `ML_SERVER_URL` | Python AI 서버 URL | `http://localhost:8000` |

- **AI 호출**: Spring은 LLM/임베딩을 직접 호출하지 않음. Spring → Python(내부망)만 호출하며, **Python AI Service가 Gemini API**로 월별 요약·질문 답변·임베딩 수행.

## 4. 동작 방식

- **리포트 저장/수정 시**: Spring이 리포트를 청크로 나누고, Python 임베딩 API 호출 후 `report_chunks` 에 저장.
- **질문 시** (`rag.enabled=true`):
  1. **Spring**: DB/통계로 Facts 블록 생성 후 Python `POST /api/llm/rag-answer` 호출 (question, facts, user_id, year_month, top_k).
  2. **Python**: 쿼리 임베딩 → `report_chunks` 벡터 검색 → Facts + 검색 청크 + 질문으로 프롬프트 구성 → **Gemini API**로 답변 생성 후 반환.

`rag.enabled=false` 이면 Spring이 Python `POST /api/llm/answer` (question, metrics, llm_summary)만 호출하며, Python이 Gemini로 답변 생성.

## 5. 질문 카드 추천 (태그 + 벡터 추천 + 클릭 리랭크)

- **테이블**: `question_cards`(카드 마스터 + embedding), `question_card_clicks`(클릭 로그).
- **스키마/시드**: `schema_question_cards.sql` 실행 후 `data_question_cards.sql` 1회 실행.
- **동작**:  
  1. 카드 문구를 Python 임베딩 API로 벡터 저장(최초 추천 요청 시 또는 `ensureEmbeddings()` 호출 시).  
  2. 사용자가 월별 리포트를 열면, 해당 월 Facts를 임베딩해 유사한 질문 카드 top-k 추천.  
  3. 이미 클릭한 카드는 후순위로 리랭크(개인화).  
- **API**: `GET /api/monthly-reports/cards/recommended?yearMonth=&limit=`, `POST /api/monthly-reports/ask/card-click` (body: `yearMonth`, `cardId`).

## 6. 확장 시

- `report_chunks` 가 많아지면 `ivfflat` 인덱스 추가를 고려하세요.  
  `schema_rag.sql` 안 주석에 있는 `CREATE INDEX ... ivfflat` 예시를 참고하면 됩니다.
