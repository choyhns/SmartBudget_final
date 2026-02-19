# Python 서비스 통합 실행 (OCR + ml-server + Recommendation)

세 개의 Python 서비스를 **한 번에** 실행하는 방법입니다.  
모든 Python 관련 코드는 **`AI-service/`** 폴더 아래에 있습니다.

## 1. 폴더 구조 (AI-service)

```
AI-service/
├── run_all.py          # 통합 실행 스크립트
├── requirements.txt    # 통합 의존성 (한 번에 설치)
├── ml-server/          # OCR/분류/LLM/RAG
├── OCR_receipts/       # PaddleOCR 영수증
└── recommandation/     # 카드 추천 AI (Flask)
```

## 2. 통합 구조 (주소)

| 서비스 | 설명 | 통합 후 주소 |
|--------|------|--------------|
| **ml-server** | OCR(EasyOCR/Tesseract), 카테고리 분류, LLM, RAG | `http://localhost:8000` |
| **Recommendation** | 카드 추천 AI (Flask, Gemini) | `http://localhost:8000/recommendation` |
| **OCR_receipts** | PaddleOCR 기반 영수증 OCR (별도 프로세스) | `http://localhost:8001` |

- Recommendation은 ml-server에 **마운트**되어 같은 포트(8000)에서 동작합니다.
- OCR_receipts는 의존성/경로 이슈로 **별도 프로세스**, 포트 8001에서 실행됩니다.

## 3. 한 번에 실행하기

**Python 3.10 권장** (3.14 등에서는 numpy 빌드 오류가 날 수 있음)

**방법 A – Python 3.10 고정 스크립트 (권장)**

```powershell
cd AI-service
.\run_with_py310.ps1
```

또는 CMD에서:
```cmd
cd AI-service
run_with_py310.bat
```

- Python 3.10이 없으면 [Python 3.10.6 설치](https://www.python.org/downloads/release/python-3106/) 후 설치 시 **"Add Python to PATH"** 체크.
- 스크립트가 `.venv310` 가상환경을 만들고, 여기서 `run_all.py`를 실행합니다.

**방법 B – 직접 실행**

```bash
cd AI-service
python run_all.py
```

- ml-server(Recommendation 포함)가 8000번, OCR_receipts가 8001번 포트에서 기동됩니다.
- 종료: `Ctrl+C`

## 4. 백엔드 설정 (통합 실행 시)

Spring Boot가 통합 서버를 쓰려면 아래처럼 설정합니다.

**방법 A – 환경 변수**

```bash
# Windows (PowerShell)
$env:ML_SERVER_URL="http://localhost:8000"
$env:RECOMMENDATION_AI_SERVER_URL="http://localhost:8000/recommendation"
$env:OCR_RECEIPTS_SERVER_URL="http://localhost:8001"

# Linux / macOS
export ML_SERVER_URL=http://localhost:8000
export RECOMMENDATION_AI_SERVER_URL=http://localhost:8000/recommendation
export OCR_RECEIPTS_SERVER_URL=http://localhost:8001
```

**방법 B – backend/smartbudget/.env 또는 application.properties**

```properties
ml.server.url=http://localhost:8000
recommendation.ai.server.url=http://localhost:8000/recommendation
ocr.receipts.server.url=http://localhost:8001
```

## 5. 개별 실행 (기존 방식)

- **ml-server만** (Recommendation 마운트 포함):  
  `cd AI-service/ml-server` 후 `python -m uvicorn app.main:app --host 0.0.0.0 --port 8000`
- **Recommendation만** (Flask 단독):  
  `cd AI-service/recommandation` 후 `python app.py` (기본 포트 5001)
- **OCR_receipts만**:  
  `cd AI-service/OCR_receipts` 후 `python run.py` (기본 포트 8000)

## 6. 의존성 설치 (한 폴더에서 한 번에)

**AI-service 폴더**에 통합 `requirements.txt`가 있습니다. 여기서 한 번만 설치하면 됩니다.

```bash
cd AI-service
pip install -r requirements.txt
```

- ml-server, recommendation, OCR_receipts 의존성이 모두 포함되어 있습니다.
- 가상환경 사용 권장: `python -m venv .venv` → 활성화 후 `pip install -r requirements.txt`
- 설치 후 `python run_all.py`로 통합 실행하면 됩니다.

기존처럼 서비스별로 따로 설치해도 됩니다 (각 디렉터리의 `requirements.txt` / `requirement.txt` 유지).
