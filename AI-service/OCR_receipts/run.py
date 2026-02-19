"""OCR API 서버 실행: python run.py
백엔드 연동: 포트 8000, 경로 /receipts (예: http://127.0.0.1:8000/receipts/health, /receipts/process)
"""
import os

# Paddle CPU 추론 시 oneDNN 관련 NotImplementedError 방지 (Paddle 3.x 이슈)
os.environ.setdefault("FLAGS_use_mkldnn", "0")
# 첫 OCR 요청 시 모델 호스터 연결 체크 스킵(지연 방지)
os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")

import uvicorn
from fastapi import FastAPI
from src.api.app import app as ocr_app

# 단독 실행 시에도 백엔드와 동일하게 8000 + /receipts 로 노출
app = FastAPI(title="OCR Receipts (standalone)")
app.mount("/receipts", ocr_app)

if __name__ == "__main__":
    port = int(os.getenv("OCR_PORT", "8000"))
    use_reload = os.getenv("OCR_RELOAD", "0").strip() == "1"
    uvicorn.run(
        "run:app",
        host="0.0.0.0",
        port=port,
        reload=use_reload,
    )
