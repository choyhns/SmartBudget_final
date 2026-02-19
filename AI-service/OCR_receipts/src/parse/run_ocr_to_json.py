# src/parse/run_ocr_to_json.py
from __future__ import annotations

from pathlib import Path
import json
import cv2
from paddleocr import PaddleOCR
import sys
import os

# 현재 파일(run_ocr_to_json.py)의 부모의 부모 폴더(src)를 경로에 추가
current_dir = os.path.dirname(os.path.abspath(__file__))
src_dir = os.path.dirname(current_dir) # ../ (src 폴더)
if src_dir not in sys.path:
    sys.path.append(src_dir)

# PaddleOCR 버전별: predict가 있는 버전만 cls 인자 제거 패치 (없는 버전은 그대로 사용)
if hasattr(PaddleOCR, "predict"):
    _original_predict = PaddleOCR.predict
    def _predict_drop_cls(self, input, *args, **kwargs):
        kwargs.pop("cls", None)
        return _original_predict(self, input, *args, **kwargs)
    PaddleOCR.predict = _predict_drop_cls

ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "data" / "raw_images" / "img"
OUT_DIR = ROOT / "data" / "json"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# ONNX 모델 경로 (scripts/export_ocr_onnx.py 실행 후 생성됨)
ONNX_DIR = ROOT / "models" / "onnx"
DET_ONNX = ONNX_DIR / "det.onnx"
REC_ONNX = ONNX_DIR / "rec.onnx"
USE_ONNX = DET_ONNX.is_file() and REC_ONNX.is_file()

# ✅ 지연 로드: /health가 즉시 200을 반환하도록, 첫 /process 요청 시에만 OCR 엔진 생성
_ocr = None


def _setup_onnx_engine():
    """ONNX 사용 시 프로젝트 src/tools(scripts/sync_paddleocr_tools.py로 복사된)를 사용해 TextSystem 반환."""
    import sys
    import paddleocr
    # 프로젝트 src/tools 사용 (sync_paddleocr_tools.py 실행으로 복사해 두어야 함)
    src_dir = ROOT / "src"
    if not (src_dir / "tools" / "infer").exists():
        raise FileNotFoundError(
            "ONNX 사용을 위해 먼저 실행하세요: python scripts/sync_paddleocr_tools.py"
        )
    src_dir_str = str(src_dir)
    if src_dir_str not in sys.path:
        sys.path.insert(0, src_dir_str)
    from tools.infer import predict_system
    from tools.infer.utility import init_args
    parser = init_args()
    args, _ = parser.parse_known_args([])
    args.use_onnx = True
    args.det_model_dir = str(DET_ONNX)
    args.rec_model_dir = str(REC_ONNX)
    args.use_angle_cls = False
    args.rec_char_dict_path = str(
        Path(paddleocr.__file__).parent / "ppocr" / "utils" / "dict" / "korean_dict.txt"
    )
    args.rec_image_shape = "3, 48, 320"
    args.rec_algorithm = "SVTR_LCNet"
    args.det_algorithm = "DB"
    args.det_limit_side_len = 736
    args.det_limit_type = "max"
    args.det_box_type = "quad"
    args.drop_score = 0.5
    args.show_log = False
    return ("onnx", predict_system.TextSystem(args))


def get_ocr():
    global _ocr
    if _ocr is None:
        if USE_ONNX:
            _ocr = _setup_onnx_engine()
        else:
            _ocr = ("paddle", PaddleOCR(lang="korean", use_angle_cls=False))
    return _ocr

def resize_for_ocr(image, max_width=1024, max_height=1024):
    """OCR 속도 향상을 위한 이미지 리사이징 (너무 큰 이미지 축소)"""
    h, w = image.shape[:2]
    if w > max_width or h > max_height:
        scale = min(max_width / w, max_height / h)
        new_w, new_h = int(w * scale), int(h * scale)
        image = cv2.resize(image, (new_w, new_h), interpolation=cv2.INTER_AREA)
    return image

def _result_to_items(result, image_shape, engine_kind: str):
    """OCR 결과(엔진 종류별)를 items 리스트로 통일."""
    items = []
    if engine_kind == "onnx":
        # result = (filter_boxes, filter_rec_res, time_dict)
        filter_boxes, filter_rec_res, _ = result
        lines = list(zip(filter_boxes, filter_rec_res))
    else:
        # result = [[(box, (text, score)), ...]]
        if not result or not result[0]:
            return items
        lines = result[0]

    h, w = image_shape[:2]
    for line in lines:
        if engine_kind == "onnx":
            box, (text, score) = line
            box = box.tolist() if hasattr(box, "tolist") else box
        else:
            box, text_score = line
            text = (text_score[0] or "").strip()
            score = float(text_score[1])

        text = (text or "").strip() if isinstance(text, str) else ""
        score = float(score)

        xs = [int(p[0]) for p in box]
        ys = [int(p[1]) for p in box]
        x1, x2 = max(min(xs), 0), min(max(xs), w)
        y1, y2 = max(min(ys), 0), min(max(ys), h)

        if not text or score < 0.5 or (y2 - y1) < 12 or (x2 - x1) < 30:
            continue
        items.append({"box": box, "text": text, "score": score})
    return items


def ocr_image_to_raw_json(img_path: Path) -> dict:
    """
    FastAPI에서 호출할 함수.
    - 입력: 이미지 파일 경로(Path)
    - 출력: {"image": "...", "items": [{"box":..., "text":..., "score":...}, ...]}
    """
    image = cv2.imread(str(img_path))
    if image is None:
        return {"image": img_path.name, "items": []}

    image = resize_for_ocr(image, max_width=1920, max_height=1920)
    engine_kind, engine = get_ocr()

    if engine_kind == "onnx":
        filter_boxes, filter_rec_res, _ = engine(image, cls=False)
        result_for_items = (filter_boxes or [], filter_rec_res or [], {})
    else:
        result_for_items = engine.ocr(image, cls=False)

    items = _result_to_items(result_for_items, image.shape, engine_kind)
    return {"image": img_path.name, "items": items}

def batch_ocr_folder_to_json(raw_dir: Path = RAW_DIR, out_dir: Path = OUT_DIR) -> None:
    """
    기존 스크립트 기능(폴더 전체 OCR → json 저장)도 유지.
    """
    out_dir.mkdir(parents=True, exist_ok=True)

    for img_path in raw_dir.iterdir():
        if img_path.suffix.lower() not in [".jpg", ".jpeg", ".png"]:
            continue

        raw_json = ocr_image_to_raw_json(img_path)  # 내부에서 get_ocr() 사용
        out_path = out_dir / f"{img_path.stem}.json"
        out_path.write_text(
            json.dumps(raw_json, ensure_ascii=False, indent=2),
            encoding="utf-8"
        )

    print("✅ OCR -> JSON 완료:", out_dir)

if __name__ == "__main__":
    batch_ocr_folder_to_json()
