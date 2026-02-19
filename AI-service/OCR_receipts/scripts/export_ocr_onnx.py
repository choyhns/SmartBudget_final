# scripts/export_ocr_onnx.py
"""
PaddleOCR Detection / Recognition 모델을 ONNX로 변환하여 저장합니다.
한 번 실행 후, run_ocr_to_json에서 ONNX 엔진을 사용할 수 있습니다.

사용법:
  cd OCR_receipts
  python scripts/export_ocr_onnx.py

필요: paddleocr로 한 번이라도 OCR을 실행해 두어 모델이 ~/.paddleocr/whl/ 에 있어야 합니다.
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

# 프로젝트 루트 (OCR_receipts)
ROOT = Path(__file__).resolve().parents[1]
ONNX_DIR = ROOT / "models" / "onnx"
ONNX_DIR.mkdir(parents=True, exist_ok=True)

DET_ONNX = ONNX_DIR / "det.onnx"
REC_ONNX = ONNX_DIR / "rec.onnx"


def get_paddle_model_dirs():
    """PaddleOCR 한국어 모델 경로를 반환 (다운로드되어 있어야 함)."""
    base = os.path.expanduser("~/.paddleocr/whl")
    det_dir = os.path.join(base, "det", "ml", "Multilingual_PP-OCRv3_det_infer")
    rec_dir = os.path.join(base, "rec", "korean", "korean_PP-OCRv4_rec_infer")
    if not os.path.isdir(det_dir) or not os.path.isdir(rec_dir):
        # 한 번 PaddleOCR을 실행해 모델 다운로드 유도
        from paddleocr import PaddleOCR
        PaddleOCR(lang="korean", use_angle_cls=False, show_log=False)
        if not os.path.isdir(det_dir):
            det_dir = os.path.join(base, "det", "ml", "Multilingual_PP-OCRv3_det_infer")
        if not os.path.isdir(rec_dir):
            rec_dir = os.path.join(base, "rec", "korean", "korean_PP-OCRv4_rec_infer")
    return det_dir, rec_dir


def run_paddle2onnx(
    model_dir: str,
    model_filename: str,
    params_filename: str,
    save_file: str,
    opset_version: int = 11,
):
    """paddle2onnx Python API로 변환 (동적 shape 기본 지원)."""
    import paddle2onnx
    model_path = os.path.join(model_dir, model_filename)
    params_path = os.path.join(model_dir, params_filename)
    if not os.path.isfile(model_path) or not os.path.isfile(params_path):
        raise FileNotFoundError(f"모델 파일 없음: {model_path}, {params_path}")
    print("  변환 중:", model_path)
    paddle2onnx.export(
        model_path,
        params_file=params_path,
        save_file=save_file,
        opset_version=opset_version,
        enable_onnx_checker=True,
        verbose=False,
    )


def main():
    print("PaddleOCR 모델 경로 확인 중...")
    det_dir, rec_dir = get_paddle_model_dirs()
    print(f"  det: {det_dir}")
    print(f"  rec: {rec_dir}")

    # Detection
    det_pdmodel = os.path.join(det_dir, "inference.pdmodel")
    det_pdiparams = os.path.join(det_dir, "inference.pdiparams")
    if not os.path.isfile(det_pdmodel) or not os.path.isfile(det_pdiparams):
        print("Detection 모델 파일이 없습니다. 먼저 PaddleOCR로 OCR을 한 번 실행해 주세요.")
        sys.exit(1)

    print("\n[1/2] Detection 모델 ONNX 변환 중...")
    run_paddle2onnx(
        model_dir=det_dir,
        model_filename="inference.pdmodel",
        params_filename="inference.pdiparams",
        save_file=str(DET_ONNX),
    )
    print(f"  저장: {DET_ONNX}")

    # Recognition
    rec_pdmodel = os.path.join(rec_dir, "inference.pdmodel")
    rec_pdiparams = os.path.join(rec_dir, "inference.pdiparams")
    if not os.path.isfile(rec_pdmodel) or not os.path.isfile(rec_pdiparams):
        print("Recognition 모델 파일이 없습니다.")
        sys.exit(1)

    print("\n[2/2] Recognition 모델 ONNX 변환 중...")
    run_paddle2onnx(
        model_dir=rec_dir,
        model_filename="inference.pdmodel",
        params_filename="inference.pdiparams",
        save_file=str(REC_ONNX),
    )
    print(f"  저장: {REC_ONNX}")

    print("\n완료. 이제 run_ocr_to_json에서 ONNX 엔진이 자동으로 사용됩니다.")


if __name__ == "__main__":
    main()
