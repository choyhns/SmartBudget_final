import os
import shutil
import tarfile
import urllib.request
import subprocess
import sys
from pathlib import Path

# 프로젝트 루트 경로
ROOT = Path(__file__).resolve().parents[1]
MODELS_DIR = ROOT / "models"
ONNX_DIR = MODELS_DIR / "onnx"
TEMP_DIR = MODELS_DIR / "temp_download_v4"

# ✅ 핵심 수정: PaddleOCR 기본값과 동일한 모델 URL로 변경
URLS = {
    # Detection: 다국어 v3 (기존 Paddle 기본값)
    "det": "https://paddleocr.bj.bcebos.com/PP-OCRv3/multilingual/Multilingual_PP-OCRv3_det_infer.tar",
    
    # Recognition: 한국어 v4 (인식률 대폭 개선된 최신 버전)
    "rec": "https://paddleocr.bj.bcebos.com/PP-OCRv4/multilingual/korean_PP-OCRv4_rec_infer.tar"
}

def download_and_extract(url, dest_dir):
    filename = url.split("/")[-1]
    filepath = dest_dir / filename
    
    print(f"⬇️ 다운로드 중: {filename}...")
    try:
        urllib.request.urlretrieve(url, filepath)
    except Exception as e:
        print(f"❌ 다운로드 실패: {e}")
        sys.exit(1)

    print(f"📦 압축 해제 중...")
    with tarfile.open(filepath, "r") as tar:
        tar.extractall(path=dest_dir)
    
    # 압축 해제된 폴더 이름 반환
    return dest_dir / filename.replace(".tar", "")

def convert_to_onnx(model_dir, save_file):
    print(f"🔄 ONNX 변환 시작: {save_file.name}")
    
    cmd = [
        "paddle2onnx",
        f"--model_dir={model_dir}",
        "--model_filename=inference.pdmodel",
        "--params_filename=inference.pdiparams",
        f"--save_file={save_file}",
        "--opset_version=11",
        "--enable_onnx_checker=True"
    ]
    
    result = subprocess.run(cmd, capture_output=True, text=True)
    
    if result.returncode != 0:
        print(f"❌ 변환 실패:\n{result.stderr}")
        sys.exit(1)
    
    print(f"✅ 변환 성공: {save_file}")

def main():
    # paddle2onnx 설치 확인
    try:
        subprocess.run(["paddle2onnx", "--version"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("❌ paddle2onnx가 없습니다. 'pip install paddle2onnx'를 실행하세요.")
        sys.exit(1)

    # 기존 ONNX 폴더가 있다면 삭제 (구버전 모델과 섞이지 않도록)
    if ONNX_DIR.exists():
        print("🗑️ 기존 ONNX 모델 삭제 중 (새 버전으로 교체)...")
        shutil.rmtree(ONNX_DIR)
    
    ONNX_DIR.mkdir(parents=True, exist_ok=True)
    TEMP_DIR.mkdir(parents=True, exist_ok=True)

    print(f"🚀 OCR 모델(v4 Rec + Multi Det) 변환 작업을 시작합니다.")

    # 1. Detection 모델 처리
    det_model_dir = download_and_extract(URLS["det"], TEMP_DIR)
    convert_to_onnx(det_model_dir, ONNX_DIR / "det.onnx")

    # 2. Recognition 모델 처리
    rec_model_dir = download_and_extract(URLS["rec"], TEMP_DIR)
    convert_to_onnx(rec_model_dir, ONNX_DIR / "rec.onnx")

    # 3. 임시 파일 청소
    print("🧹 임시 파일 정리 중...")
    try:
        shutil.rmtree(TEMP_DIR)
    except Exception as e:
        print(f"⚠️ 임시 폴더 삭제 실패 (무시 가능): {e}")

    print("\n🎉 모든 작업 완료!")
    print(f"이제 'models/onnx' 폴더에 v4 기반 모델이 설치되었습니다.")
    print("서버를 재시작하면 인식률이 정상화될 것입니다.")

if __name__ == "__main__":
    main()