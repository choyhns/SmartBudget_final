import io
import os
import shutil
import sys
import zipfile
import urllib.request
from pathlib import Path

# 프로젝트 경로 설정
ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"

# PaddleOCR GitHub 소스코드 다운로드 링크 (2.7 버전 기준)
DOWNLOAD_URL = "https://github.com/PaddlePaddle/PaddleOCR/archive/refs/heads/release/2.7.zip"

def main():
    print(f"🚀 PaddleOCR 소스 코드를 다운로드합니다... ({DOWNLOAD_URL})")
    
    try:
        # 1. Zip 파일 다운로드 (메모리로)
        with urllib.request.urlopen(DOWNLOAD_URL) as response:
            zip_data = response.read()
    except Exception as e:
        print(f"❌ 다운로드 실패: {e}")
        return

    print("📦 압축 해제 및 폴더 추출 중...")
    
    # 2. 필요한 폴더(tools, ppocr)만 골라서 src 폴더로 이동
    with zipfile.ZipFile(io.BytesIO(zip_data)) as z:
        # 압축 파일 내 최상위 폴더 이름 찾기 (예: PaddleOCR-release-2.7/)
        root_folder = z.namelist()[0].split('/')[0]
        
        for target in ["tools", "ppocr"]:
            source_prefix = f"{root_folder}/{target}/"
            target_dir = SRC_DIR / target
            
            # 기존 폴더가 있다면 삭제 (깨끗하게 덮어쓰기 위해)
            if target_dir.exists():
                print(f"   - 기존 {target} 폴더 삭제 중...")
                shutil.rmtree(target_dir)
            
            # 해당 폴더만 추출
            extracted_count = 0
            for file in z.namelist():
                if file.startswith(source_prefix) and not file.endswith('/'):
                    # 파일 경로 재구성 (src/tools/... 형태로)
                    relative_path = file[len(source_prefix):]
                    dest_path = target_dir / relative_path
                    
                    # 폴더 생성 및 파일 쓰기
                    dest_path.parent.mkdir(parents=True, exist_ok=True)
                    with z.open(file) as source_file, open(dest_path, "wb") as target_file:
                        shutil.copyfileobj(source_file, target_file)
                    extracted_count += 1
            
            print(f"✅ {target} 폴더 설치 완료 ({extracted_count}개 파일)")

            # __init__.py 확인 및 생성
            if target == "tools":
                 infer_init = target_dir / "infer" / "__init__.py"
                 if infer_init.parent.exists() and not infer_init.exists():
                     infer_init.write_text("# PaddleOCR tools\n", encoding="utf-8")

    print("\n🎉 모든 설치가 완료되었습니다!")
    print(f"이제 src 폴더 안에 'tools'와 'ppocr' 폴더가 정상적으로 존재합니다.")
    print("다시 'python run.py'를 실행해 보세요.")

if __name__ == "__main__":
    main()