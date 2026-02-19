# SmartBudget Python AI Service

Python FastAPI 기반 – **카테고리 분류, 월별 요약(LLM), 질문응답(Ask)** 만 담당.  
외부 API는 Spring Boot가 제공하며, AI 호출은 Spring → Python(내부망)으로만 수행.  
**DB 직접 쓰기 없음** (필요 시 read-only 또는 Spring이 전달한 데이터만 사용). **입력/출력은 JSON 스펙으로 고정** (see [API_SPEC.md](API_SPEC.md)).

## 기능

1. **카테고리 분류** – Spring이 거래 목록/카테고리 후보 전달 → 분류 결과 반환
2. **월별 요약 (LLM)** – Spring이 거래/메트릭/예산 전달 → 분석 요약 텍스트 반환
3. **질문응답 (Ask)** – RAG(`/api/llm/rag-answer`) 또는 전체 맥락(`/api/llm/answer`). Spring이 facts/metrics/llm_summary 전달
4. **OCR** – 영수증 이미지 → 텍스트 추출 (Spring이 호출)
5. **임베딩/벡터 검색** – RAG용; Spring이 인덱싱·쿼리 시 호출

## 설치

```bash
# 가상환경 생성
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate

# 의존성 설치
pip install -r requirements.txt

# Tesseract 설치 (선택사항)
# Windows: https://github.com/UB-Mannheim/tesseract/wiki
# Mac: brew install tesseract tesseract-lang
# Linux: apt-get install tesseract-ocr tesseract-ocr-kor
```

## 실행

```bash
python run.py
```

서버가 http://localhost:8000 에서 실행됩니다.

## API 엔드포인트

### 상태 확인
```
GET /health
```

### AI 서비스

#### 임베딩 생성
```
POST /api/embed
Content-Type: application/json

{
    "text": "텍스트",
    "task_type": "RETRIEVAL_DOCUMENT" | "RETRIEVAL_QUERY" | null
}
```

#### 배치 임베딩
```
POST /api/embed/batch
Content-Type: application/json

{
    "texts": ["텍스트1", "텍스트2"],
    "task_type": "RETRIEVAL_DOCUMENT"
}
```

#### RAG 벡터 저장소 (Chroma)
- **RAG_USE_CHROMA=true** (기본): 벡터는 Chroma에 저장, PostgreSQL에는 content만 저장. pgvector 불필요.
- **CHROMA_PERSIST_DIR**: Chroma DB 디렉터리 (기본 `./chroma_data`).
- **RAG_USE_CHROMA=false**: PostgreSQL(pgvector 또는 JSONB) 사용.

#### 벡터 검색 (RAG)
```
POST /api/rag/search
Content-Type: application/json

{
    "user_id": 1,
    "year_month": "202501",
    "query_embedding": [0.1, -0.2, ...],
    "top_k": 5
}
```

#### RAG 질문 답변 (Spring이 facts + user_id/year_month/top_k 전달)
```
POST /api/llm/rag-answer
Content-Type: application/json

{
    "question": "이번 달 예산 상태 어때?",
    "facts": "Spring이 DB/통계로 생성한 Facts 블록",
    "user_id": 1,
    "year_month": "202501",
    "top_k": 5
}
→ { "answer": "string" }
```

#### RAG 기반 질문 답변 (Facts 없음)
```
POST /api/rag/answer
Content-Type: application/json

{
    "user_id": 1,
    "year_month": "202501",
    "question": "이번 달 예산 상태 어때?"
}
```

#### 리포트 분석 요약 생성
```
POST /api/llm/analyze
Content-Type: application/json

{
    "transactions": [...],
    "metrics": {"totalIncome": 3000000, "totalExpense": 1500000, ...},
    "monthly_budget": 2000000
}
```

#### 리포트 맥락 기반 질문 답변
```
POST /api/llm/answer
Content-Type: application/json

{
    "question": "어디에 가장 많이 썼어?",
    "metrics": {...},
    "llm_summary": "이번 달 소비 패턴..."
}
```

### OCR
```
POST /api/ocr
Content-Type: application/json

{
    "image": "<base64_encoded_image>",
    "mime_type": "image/jpeg"
}
```

### 카테고리 분류
```
POST /api/classify
Content-Type: application/json

{
    "merchant": "스타벅스 강남점",
    "memo": "아메리카노",
    "categories": ["식비", "교통", "쇼핑", "기타"]
}
```

### 배치 분류
```
POST /api/classify/batch
Content-Type: application/json

{
    "transactions": [
        {"merchant": "스타벅스", "memo": "커피"},
        {"merchant": "카카오택시", "memo": ""}
    ],
    "categories": ["식비", "교통", "쇼핑", "기타"]
}
```

### 모델 학습 (관리자용)
```
POST /api/train
Content-Type: application/json

{
    "training_data": [
        {"text": "스타벅스 아메리카노", "category": "식비"},
        {"text": "카카오택시 강남역", "category": "교통"}
    ]
}
```

## 모델 학습

`models/category_classifier.pkl` 파일로 학습된 모델이 저장됩니다.

학습 데이터 형식:
```python
training_data = [
    {"text": "거래 내용", "category": "카테고리"},
    ...
]
```

## 환경변수

`.env` 파일 생성 (`.env.example` 참고):

```bash
# Tesseract (OCR)
TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata

# 서버 설정
HOST=0.0.0.0
PORT=8000

# PostgreSQL (벡터 검색용)
DB_URL=jdbc:postgresql://localhost:5432/smartbudget
DB_USERNAME=postgres
DB_PASSWORD=your_password
DB_NAME=smartbudget

# Gemini API
GEMINI_API_KEY=your_gemini_api_key

# RAG 설정
RAG_TOP_K=5
RAG_EMBEDDING_MODEL=text-embedding-004
```

## 디렉토리 구조

```
ml-server/
├── app/
│   ├── __init__.py
│   ├── main.py          # FastAPI 앱
│   ├── ocr.py           # OCR 서비스
│   ├── classifier.py    # 분류기
│   ├── embedding.py    # 임베딩 서비스 (Gemini)
│   ├── llm.py           # LLM 서비스 (Gemini, 프롬프트 빌드)
│   └── rag.py           # RAG 서비스 (벡터 검색 + 답변)
├── models/              # 학습된 모델 저장
├── requirements.txt
├── run.py
├── .env.example
└── README.md
```

## 아키텍처

- **Spring Boot**: 인증/JWT, 권한검사, DB CRUD, 월별 집계(metrics), 리포트 저장, 스케줄러, API Gateway. 외부(프론트) API는 모두 Spring이 제공. AI 호출은 Spring → Python(내부망)만.
- **Python AI Service**: 카테고리 분류, 월별 요약(LLM), 질문응답(Ask). DB 직접 쓰기 없음. 입출력 JSON 스펙 고정 ([API_SPEC.md](API_SPEC.md)).
- **PostgreSQL + pgvector**: Spring이 report_chunks 등 관리; Python은 RAG 시 read-only 검색만 가능.
