# api/app.py
import asyncio
import logging
import os
from pathlib import Path
from fastapi import FastAPI, UploadFile, File, HTTPException

# pipeline/run_ocr_to_json는 첫 /process·/ocr 요청 시에만 로드 (PaddleOCR 지연 로드로 /health 즉시 200)
from src.ml.rule_seed import classify as rule_classify
from src.ml.text_model import load_text_classifier

app = FastAPI(title="OCR Receipts API", version="1.0.0")
log = logging.getLogger(__name__)

# CWD(작업 디렉터리)에 의존하지 않도록, OCR_receipts 폴더 기준으로 모델 경로를 고정
_OCR_RECEIPTS_ROOT = Path(__file__).resolve().parents[2]  # .../OCR_receipts
clf = load_text_classifier(_OCR_RECEIPTS_ROOT / "models" / "textclf")


def _preload_paddle():
    """기동 직후 백그라운드에서 Paddle 로드 → 첫 요청 시 대기 시간 제거(근본 원인)"""
    try:
        from src.parse.run_ocr_to_json import get_ocr
        get_ocr()
        log.info("PaddleOCR preload done")
    except Exception as e:
        log.warning("PaddleOCR preload failed (will load on first request): %s", e)


@app.on_event("startup")
def startup():
    # Windows/Paddle 환경에서는 preload 중 네이티브 크래시로 프로세스가 죽는 경우가 있어 기본은 OFF.
    # 필요 시 OCR_PRELOAD=1 로 켜기.
    use_preload = os.getenv("OCR_PRELOAD", "0").strip() == "1"
    if use_preload:
        import threading
        t = threading.Thread(target=_preload_paddle, daemon=True)
        t.start()
        log.info("OCR_PRELOAD=1: PaddleOCR preload thread started")
    else:
        log.info("OCR_PRELOAD!=1: skip PaddleOCR preload (lazy load on first request)")


@app.get("/health")
def health():
    return {"ok": True}


def _process_ocr_lines_parsed(image_bytes: bytes, filename: str):
    from src.parse.pipeline import process_ocr_lines_parsed
    return process_ocr_lines_parsed(image_bytes, filename)


# 블로킹 OCR을 스레드에서 실행 → 이벤트 루프가 막히지 않아 Connection reset / 타임아웃 방지
async def _run_ocr_async(image_bytes: bytes, filename: str):
    return await asyncio.to_thread(_process_ocr_lines_parsed, image_bytes, filename)


# 디버그용(원하면 유지): raw_json + lines_json + parsed_json
@app.post("/debug/ocr_lines")
async def debug_ocr_lines(file: UploadFile = File(...)):
    if not file.filename:
        raise HTTPException(status_code=400, detail="filename is required")
    image_bytes = await file.read()
    raw_json, lines_json, parsed_json = await _run_ocr_async(image_bytes, file.filename)
    return {"raw_json": raw_json, "lines_json": lines_json, "parsed_json": parsed_json}


# 서비스용: raw_json + parsed_json
@app.post("/ocr")
async def ocr(file: UploadFile = File(...)):
    if not file.filename:
        raise HTTPException(status_code=400, detail="filename is required")
    image_bytes = await file.read()
    raw_json, _lines_json, parsed_json = await _run_ocr_async(image_bytes, file.filename)
    return {"raw_json": raw_json, "parsed_json": parsed_json}


# merchant/items로 카테고리만 분류
@app.post("/classify")
async def classify(payload: dict):
    merchant = (payload.get("merchant") or "").strip()
    items = payload.get("items") or []
    if isinstance(items, str):
        items = [items]
    items = [str(x).strip() for x in items if str(x).strip()]

    # 1) ML 우선
    if clf is not None:
        category, conf, topk = clf.predict(merchant, items, topk=3)

        # 애매하면(예: confidence 낮음 or 1-2위 차이 작음) 룰로 fallback 가능
        # topk는 [(cat, prob), ...]
        margin = (topk[0][1] - topk[1][1]) if len(topk) >= 2 else topk[0][1]
        if conf >= 0.60 and margin >= 0.10:
            return {
                "category": category,
                "confidence": float(conf),
                "topk": [{"category": c, "score": float(s)} for c, s in topk],
                "model_version": getattr(clf, "model_version", "ml_textclf"),
                "source": "ml",
            }

    # 2) fallback: rule_seed
    category, conf, topk = rule_classify(merchant, items)
    return {
        "category": category,
        "confidence": float(conf),
        "topk": [{"category": c, "score": float(s)} for c, s in topk],
        "model_version": "rule_seed_v1",
        "source": "rule",
    }


# OCR + 분류 한번에 (OCR → line_grouping → mapping → ML/rule 분류)
@app.post("/process")
async def process(file: UploadFile = File(...)):
    if not file.filename:
        raise HTTPException(status_code=400, detail="filename is required")

    try:
        image_bytes = await file.read()
        raw_json, _lines_json, parsed_json = await _run_ocr_async(image_bytes, file.filename)
    except Exception as e:
        log.exception("OCR failed")
        raise HTTPException(status_code=500, detail=f"OCR 실패: {e!s}")

    merchant = parsed_json.get("merchant") or ""
    items = parsed_json.get("items", []) or []
    items = [str(x).strip() for x in items if str(x).strip()]

    # 1) ML 분류기 우선 (가맹점/품목 기반)
    if clf is not None:
        category, conf, topk = clf.predict(merchant, items, topk=3)
        margin = (topk[0][1] - topk[1][1]) if len(topk) >= 2 else topk[0][1]
        if conf >= 0.60 and margin >= 0.10:
            return {
                "raw_json": raw_json,
                "parsed_json": parsed_json,
                "classification": {
                    "category": category,
                    "confidence": float(conf),
                    "topk": [{"category": c, "score": float(s)} for c, s in topk],
                    "model_version": getattr(clf, "model_version", "ml_textclf"),
                },
            }

    # 2) fallback: 룰 기반 분류
    category, conf, topk = rule_classify(merchant, items)
    return {
        "raw_json": raw_json,
        "parsed_json": parsed_json,
        "classification": {
            "category": category,
            "confidence": float(conf),
            "topk": [{"category": c, "score": float(s)} for c, s in topk],
            "model_version": "rule_seed_v1",
        },
    }
