# SmartBudget Recommendation AI Service

카드 추천 및 Q&A를 위한 Python AI 서비스입니다.

## 설치

```bash
# 가상환경 생성 (Windows)
python -m venv .venv
.venv\Scripts\activate

# 가상환경 생성 (Linux/Mac)
python -m venv .venv
source .venv/bin/activate

# 패키지 설치
pip install -r requirements.txt
```

## 환경 변수 설정

`.env.example`을 복사하여 `.env` 파일을 만들고 다음을 설정하세요:

```
GEMINI_API_KEY=your_api_key_here
GEMINI_MODEL=gemini-2.5-flash
PORT=5001
DEBUG=false
```

**직접 질문 답변 시 검색 사용 (선택)**  
질문에 대해 웹 검색 결과를 반영하려면 [Google Custom Search API](https://developers.google.com/custom-search/v1/overview) 키와 검색엔진 ID를 설정하세요. 설정 시 질문으로 검색한 스니펫이 컨텍스트에 포함됩니다.

```
GOOGLE_CUSTOM_SEARCH_API_KEY=your_custom_search_api_key
GOOGLE_CUSTOM_SEARCH_CX=your_search_engine_id
```

또는 백엔드와 동일한 이름 사용: `GOOGLE_CUSTOMSEARCH_API_KEY`, `GOOGLE_CUSTOMSEARCH_CX`

**403이 나오는 동안 검색 끄기**  
`.env`에 `CUSTOM_SEARCH_ENABLED=false` 를 넣고 서버를 다시 띄우면, Custom Search 호출을 하지 않아 403 로그가 나오지 않습니다. (답변은 기존처럼 맥락만으로 생성됩니다.)

**Custom Search 403 Forbidden을 해결하려면**  
1. [Google Cloud Console](https://console.cloud.google.com/) → 사용 중인 프로젝트 선택 → **API 및 서비스** → **라이브러리** → "Custom Search API" 검색 후 **사용 설정**  
2. **사용자 인증 정보**에서 사용하는 API 키의 **제한** 확인: "API 제한"이면 "Custom Search API"가 포함되는지, "HTTP 리퍼러" 등이면 localhost 허용 여부 확인  
3. 해당 프로젝트에 **결제 계정이 연결**되어 있는지 확인 (무료 할당량 100회/일도 결제 연결 필요할 수 있음)

또는 상위 디렉토리의 `.env` 파일에서 `GEMINI_API_KEY`를 자동으로 로드합니다.

## 실행

```bash
python app.py
```

서비스는 기본적으로 `http://localhost:5001`에서 실행됩니다.

## API 엔드포인트

### GET /health
헬스 체크

### POST /generate-card-suitable-reason
단일 카드에 대한 적합 이유 생성

### POST /generate-card-suitable-reasons-batch
여러 카드에 대한 적합 이유를 한 번에 생성 (배치)

### POST /answer-custom-question
사용자 직접 입력 질문에 답변. Custom Search API 설정 시 질문으로 웹 검색한 스니펫을 컨텍스트에 넣어 최신 정보를 반영합니다.

### POST /extract-card-benefits
검색 스니펫에서 카드 혜택만 추려서 정리

## Java 백엔드 연동

Java 백엔드에서 이 서비스를 호출하려면 `PythonAIService`를 사용하거나 새로운 HTTP 클라이언트를 만들어야 합니다.
