# OCR·ML 실행 가이드 (han 브랜치 vs main 통합 실행)

## 왜 main에서 문제가 생겼는지

### han 브랜치 (AI 폴더 따로 실행)
- **OCR_receipts**만 단독 실행 (예: `cd OCR_receipts && python run.py`) → 포트 8001
- **ml-server**만 단독 실행 (예: `cd ml-server && python -m uvicorn app.main:app --port 8000`)
- 백엔드가 **어느 서버를 호출할지** 설정만 맞으면 됨 (예: ocr.receipts.server.url=8001)
- 서버가 **한 개씩** 떠 있어서 포트 충돌 없음

### main 브랜치 (run_all.py로 3개 통합)
- **run_all.py**가 **ml-server(8000)** 과 **OCR_receipts(8001)** 를 **동시에** 기동
- **문제 1 – 포트**: Ctrl+C 없이 터미널만 닫으면 프로세스가 남아 8000/8001이 계속 점유됨 → 다음 실행 시 "소켓 주소는 하나만 사용 가능" 에러
- **문제 2 – 8001이 늦게 준비됨**: OCR_receipts 워커가 **앱 로드 시점에 PaddleOCR**을 생성해서, 기동 후 수십 초 동안 **/health가 응답하지 못함** → 백엔드가 "8001 불가"로 보고 **8000(ml-server)으로 폴백** → 8000은 OCR 스텁이라 **금액·가맹점 0/빈값**
- **문제 3 – 버전 호환**: Paddle 2.6.2 + PaddleX에서 `set_optimization_level` 등 API 차이로 500 에러 → venv 패치로 해결

---

## 수정 사항 요약

| 항목 | 수정 내용 |
|------|-----------|
| **PaddleOCR 로드 시점** | 모듈 로드 시가 아니라 **첫 /process 요청 시**에만 로드 (지연 로드). `/health`는 pipeline을 안 타서 **즉시 200** 반환. |
| **pipeline import** | app에서 `process_ocr_lines_parsed`를 **상단이 아니라** `/process`, `/ocr` 등에서 **첫 호출 시** import. |
| **백엔드 기본 URL** | `OcrReceiptsService` 기본값을 `http://localhost:8001`로 변경. |
| **Paddle 2.6.2 + PaddleX** | `set_optimization_level` 호출을 `hasattr`로 감싼 패치 적용 (venv 또는 `patch_paddlex_for_paddle262.py`). |
| **oneDNN 에러** | `FLAGS_use_mkldnn=0` 설정 (run_all.py·run.py). |
| **cls TypeError** | `PaddleOCR.predict`에 `cls` 제거 패치 (run_ocr_to_json.py). |
| **포트 정리** | `kill_ports_8000_8001.ps1`로 8000/8001 사용 프로세스 종료 후 재실행. |

### 타임아웃 / Connection reset 근본 원인 및 조치

| 근본 원인 | 현상 | 조치 |
|-----------|------|------|
| **동기 OCR가 이벤트 루프 블로킹** | 서버가 응답을 못 보내 연결 끊김 → "HTTP/1.1 header parser received no bytes", "Connection reset" | OCR를 **`asyncio.to_thread()`**로 스레드 풀에서 실행해 이벤트 루프는 블로킹하지 않음. |
| **Uvicorn reload=True** | 자식 프로세스에서 Paddle 로딩 시 Windows에서 불안정·크래시 가능 | **`reload=False`** 기본 (run.py). 개발 시에만 `OCR_RELOAD=1`로 reload 사용. |
| **첫 요청에서만 Paddle 로딩** | 첫 /process가 60초 이상 걸려 클라이언트 타임아웃 | **startup 시 백그라운드 스레드에서 Paddle 프리로드** → 첫 요청 시에도 대기 최소화. |
| **OCR 예외 시 응답 없음** | 예외 발생 시 응답 미전송 → 클라이언트 "no bytes" | **/process에서 try/except** 후 **HTTP 500 + JSON** 반환해 항상 응답 전송. |

---

## 실행 순서 (권장)

1. **기존 8000/8001 사용 프로세스 정리**  
   `AI-service`에서:
   ```powershell
   .\kill_ports_8000_8001.ps1
   ```
2. **Python 서비스 기동**  
   ```powershell
   python run_all.py
   ```
3. **"Uvicorn running on http://0.0.0.0:8001"** 로그가 보이면, **바로** 브라우저에서 영수증 업로드 가능 (첫 업로드 시에만 Paddle 로딩으로 몇십 초 걸릴 수 있음).
4. **백엔드**는 `application.properties`에서 `ocr.receipts.server.url=http://localhost:8001`(기본값 8001)로 **8001 /process**만 호출하므로, OCR·라인 그룹핑·매핑·ML 분류가 모두 8001에서 실행됨.

---

## 흐름 정리

- **프론트** → `POST /api/receipts/ocr-only` (Spring)
- **Spring** → `ocrReceiptsService.isServerHealthy()` (8001/health)  
  - **성공** → `ocrReceiptsService.processReceipt()` → **8001 POST /process** (PaddleOCR + line_grouping + mapping + ML)
  - **실패** → `pythonMLService.extractTextFromReceipt()` → 8000 /api/ocr (스텁이라 금액·가맹점 없음)

지연 로드로 **8001 /health가 기동 직후부터 200**을 주므로, Spring이 8001을 쓰고, OCR·ML이 정상 동작하게 됨.
