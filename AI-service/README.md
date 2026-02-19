# SmartBudget AI-service

Python 기반 ML/AI 서비스 통합 실행 환경

## 📋 목차

- [필수 요구사항](#필수-요구사항)
- [빠른 시작](#빠른-시작)
- [상세 설정](#상세-설정)
- [문제 해결](#문제-해결)

## 필수 요구사항

- **Python 3.10.x** (권장: 3.10.6)
  - Python 3.14 등 최신 버전에서는 numpy 빌드 오류가 발생할 수 있습니다
  - [Python 3.10.6 다운로드](https://www.python.org/downloads/release/python-3106/)
  - 설치 시 **"Add Python to PATH"** 체크 필수

## 빠른 시작

### Windows (PowerShell)

```powershell
cd AI-service
.\run_with_py310.ps1
```

이 스크립트가 자동으로:
1. Python 3.10 확인
2. `.venv310` 가상환경 생성 (최초 1회)
3. `requirements.txt` 의존성 설치 (최초 1회)
4. `run_all.py` 실행

### Windows (CMD)

```cmd
cd AI-service
run_with_py310.bat
```

### Linux / macOS

```bash
cd AI-service
python3.10 -m venv .venv310
source .venv310/bin/activate  # Linux/Mac
pip install -r requirements.txt
python run_all.py
```

## 상세 설정

### 1. 가상환경 수동 생성

```powershell
# Windows
py -3.10 -m venv .venv310
.\.venv310\Scripts\Activate.ps1

# Linux/Mac
python3.10 -m venv .venv310
source .venv310/bin/activate
```

### 2. 의존성 설치

```bash
pip install -r requirements.txt
```

### 3. 실행

```bash
python run_all.py
```

또는 개별 실행:

```bash
# ml-server만 (포트 8000)
cd ml-server
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# OCR_receipts만 (포트 8001)
cd OCR_receipts
python run.py
```

## 서비스 엔드포인트

실행 후 다음 주소에서 접근 가능:

- **ml-server**: http://localhost:8000
  - Health: http://localhost:8000/health
  - API 문서: http://localhost:8000/docs
- **Recommendation**: http://localhost:8000/recommendation
- **OCR_receipts**: http://localhost:8001
  - API 문서: http://localhost:8001/docs

## 환경 변수 설정

**통합 .env 파일 사용** (권장)

모든 서비스가 `AI-service/.env` 파일을 공유합니다:

```bash
# .env.example을 복사하여 .env 생성
cp .env.example .env

# 또는 Windows
copy .env.example .env
```

`.env` 파일을 열어 실제 값으로 수정:

```env
# 필수: Gemini API 키
GEMINI_API_KEY=your_gemini_api_key_here

# 선택: Google Custom Search (recommendation용)
GOOGLE_CUSTOM_SEARCH_API_KEY=your_key
GOOGLE_CUSTOM_SEARCH_CX=your_cx

# 선택: PostgreSQL (RAG 벡터 검색용)
DB_PASSWORD=your_password
```

**참고:**
- 루트의 `.env` 파일이 모든 서비스(ml-server, recommendation, OCR_receipts)에서 자동으로 로드됩니다
- 하위 폴더의 `.env` 파일(ml-server/.env, recommendation/.env)은 더 이상 필요하지 않습니다
- `.env` 파일은 Git에 포함되지만, 실제 API 키는 `.env.local` 사용을 권장합니다

## 문제 해결

### ❌ "Python 3.10을 찾을 수 없습니다"

**해결:**
1. Python 3.10.x 설치 확인: `py -3.10 --version` 또는 `python3.10 --version`
2. 설치되지 않았다면 [Python 3.10.6 다운로드](https://www.python.org/downloads/release/python-3106/)
3. 설치 시 **"Add Python to PATH"** 체크

### ❌ "ModuleNotFoundError: No module named 'xxx'"

**원인:** 가상환경이 없거나 의존성이 설치되지 않음

**해결:**
```powershell
# 가상환경 생성 및 의존성 설치
.\run_with_py310.ps1
```

또는 수동:
```powershell
py -3.10 -m venv .venv310
.\.venv310\Scripts\Activate.ps1
pip install -r requirements.txt
```

### ❌ "Form data requires python-multipart"

**원인:** `python-multipart` 패키지가 설치되지 않음

**해결:**
```bash
pip install python-multipart
```

또는 전체 의존성 재설치:
```bash
pip install -r requirements.txt
```

### ❌ "UnicodeDecodeError: 'cp949' codec can't decode"

**원인:** Windows 인코딩 문제

**해결:** `run_with_py310.ps1` 또는 `run_with_py310.bat` 사용 (자동 UTF-8 설정)

### ❌ 포트가 이미 사용 중

**해결:**
- 포트 8000 또는 8001을 사용하는 다른 프로세스 종료
- 또는 환경 변수로 포트 변경:
  ```powershell
  $env:ML_SERVER_PORT="8002"
  $env:OCR_PORT="8003"
  python run_all.py
  ```

## 프로젝트 구조

```
AI-service/
├── README.md              # 이 파일
├── requirements.txt       # 통합 의존성
├── .env                   # 통합 환경 변수 (모든 서비스 공유)
├── .env.example           # 환경 변수 예시 파일
├── run_all.py            # 통합 실행 스크립트
├── run_with_py310.ps1    # Windows PowerShell 실행 스크립트
├── run_with_py310.bat    # Windows CMD 실행 스크립트
├── .python-version       # Python 버전 지정 (pyenv용)
├── .gitignore            # Git 제외 파일
├── ml-server/            # OCR/분류/LLM/RAG 서버
│   ├── app/
│   └── requirements.txt
├── OCR_receipts/         # PaddleOCR 영수증 OCR
│   ├── src/
│   └── requirement.txt
└── recommendation/       # 카드 추천 AI (Flask)
    └── app.py
```

**환경 변수:**
- 모든 서비스는 루트의 `.env` 파일을 공유합니다
- 하위 폴더의 `.env` 파일은 더 이상 사용하지 않습니다 (하위 호환성 유지)

## 백엔드 연동 설정

Spring Boot 백엔드에서 사용하려면 `application.properties`에 추가:

```properties
ml.server.url=http://localhost:8000
recommendation.ai.server.url=http://localhost:8000/recommendation
ocr.receipts.server.url=http://localhost:8001
```

또는 환경 변수:

```bash
export ML_SERVER_URL=http://localhost:8000
export RECOMMENDATION_AI_SERVER_URL=http://localhost:8000/recommendation
export OCR_RECEIPTS_SERVER_URL=http://localhost:8001
```

## 참고

- 가상환경(`.venv310`)은 `.gitignore`에 포함되어 Git에 올라가지 않습니다
- 각 개발자는 로컬에서 `run_with_py310.ps1` 또는 수동으로 가상환경을 생성해야 합니다
- Python 3.10.x 사용을 강력히 권장합니다 (다른 버전에서는 호환성 문제 발생 가능)
