# src/parse/pipeline.py
from __future__ import annotations

from pathlib import Path
import uuid

# ocr한거 json으로 변환하는 함수들 다 임포트하기.
from .run_ocr_to_json import ocr_image_to_raw_json
from .line_grouping import build_tokens, group_tokens_into_lines, lines_to_json
from .receipt_mapping import extract_receipt_fields

# 폴더 경로 설정 및 만들기
ROOT = Path(__file__).resolve().parents[2]
TMP_DIR = ROOT / "data" / "tmp"
TMP_DIR.mkdir(parents=True, exist_ok=True)


def image_bytes_to_tmp_path(image_bytes: bytes, filename: str) -> Path:
    ext = Path(filename).suffix.lower() or ".png"
    tmp_name = f"{uuid.uuid4().hex}{ext}"
    tmp_path = TMP_DIR / tmp_name
    tmp_path.write_bytes(image_bytes)
    return tmp_path


def raw_json_to_lines_json(raw_json: dict, min_score: float = 0.3, y_ratio: float = 0.55) -> dict:
    image_name = raw_json.get("image")
    items = raw_json.get("items", [])

    tokens = build_tokens(items, min_score=min_score)
    lines = group_tokens_into_lines(tokens, y_threshold_ratio=y_ratio)

    return {
        "image": image_name,
        "line_count": len(lines),
        "lines": lines_to_json(lines),
    }


def lines_json_to_parsed_json(lines_json: dict) -> dict:
    """
    receipt_mapping.extract_receipt_fields() 결과를
    네가 원하는 parsed_json 형태(dict)로 변환
    """
    image_name = lines_json.get("image")
    lines = lines_json.get("lines", [])

    r = extract_receipt_fields(lines)  # ReceiptResult dataclass

    return {
        "image": image_name,
        "merchant": r.merchant,
        "date": r.date,
        "datetime": r.datetime,
        "items": r.items,
        "total": r.total,
        "total_confidence": float(r.total_confidence or 0.0),
    }


def process_ocr_lines_parsed(image_bytes: bytes, filename: str) -> tuple[dict, dict, dict]:
    """
    returns (raw_json, lines_json, parsed_json)
    """
    tmp_path = image_bytes_to_tmp_path(image_bytes, filename)

    raw_json = ocr_image_to_raw_json(tmp_path)
    lines_json = raw_json_to_lines_json(raw_json)
    parsed_json = lines_json_to_parsed_json(lines_json)

    return raw_json, lines_json, parsed_json
