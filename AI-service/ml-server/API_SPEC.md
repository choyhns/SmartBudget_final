# Python AI Service – JSON API 스펙 (고정)

Spring Boot가 내부망으로만 호출. **입력/출력은 아래 JSON 스펙으로 고정.**  
Python AI Service는 **카테고리 분류, 월별 요약(LLM), 질문응답(Ask)** 만 담당. DB 직접 쓰기 없음 (필요 시 read-only 또는 Spring이 전달한 데이터만 사용).

---

## 1. 카테고리 분류

**요청** `POST /api/classify`
```json
{
  "merchant": "string",
  "memo": "string (optional)",
  "categories": ["string"]
}
```

**응답** `200 OK`
```json
{
  "category": "string",
  "confidence": 0.0,
  "reason": "string",
  "top_predictions": [{"category": "string", "confidence": 0.0}]
}
```

**배치** `POST /api/classify/batch`
```json
{ "transactions": [{"merchant": "...", "memo": "..."}], "categories": ["string"] }
```
→ `List<ClassifyResponse>`

---

## 2. 월별 요약 (LLM)

**요청** `POST /api/llm/analyze`
```json
{
  "transactions": [...],
  "metrics": {
    "totalIncome": 0,
    "totalExpense": 0,
    "netAmount": 0,
    "transactionCount": 0,
    "categoryExpenses": {"string": 0}
  },
  "monthly_budget": 0
}
```

**응답** `200 OK`
```json
{ "summary": "string" }
```

---

## 3. 질문응답 (Ask)

### 3-1. RAG 질문 답변 (Spring이 facts + user_id/year_month/top_k 전달)

**요청** `POST /api/llm/rag-answer`
```json
{
  "question": "string",
  "facts": "string (Spring이 DB/통계로 생성한 Facts 블록)",
  "user_id": 0,
  "year_month": "YYYYMM",
  "top_k": 5
}
```

**응답** `200 OK`
```json
{ "answer": "string" }
```

### 3-2. 전체 맥락 질문 답변 (Spring이 metrics + llm_summary 전달)

**요청** `POST /api/llm/answer`
```json
{
  "question": "string",
  "metrics": {...},
  "llm_summary": "string"
}
```

**응답** `200 OK`
```json
{ "answer": "string" }
```

---

## 4. 기타 (Spring이 호출하는 보조 API)

- **임베딩** `POST /api/embed` – 단일 텍스트  
- **배치 임베딩** `POST /api/embed/batch` – RAG 인덱싱용  
- **벡터 검색** `POST /api/rag/search` – (선택) Spring이 쿼리 임베딩 전달 시  
- **RAG 단일 호출** `POST /api/rag/answer` – user_id, year_month, question만 전달 (Facts 없음)

---

## 원칙

- **DB**: Python은 직접 쓰지 않음. read-only(예: report_chunks 검색) 또는 Spring이 넘긴 payload만 사용.
- **입출력**: 위 JSON 스펙 유지. 필드 추가/변경 시 Spring Boot와 협의.
