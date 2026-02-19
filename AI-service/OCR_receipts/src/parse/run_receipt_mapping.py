import json
import sys
import os
from pathlib import Path

# ==========================================
# [추가됨] 프로젝트 루트 경로 강제 설정
# ==========================================
# 현재 파일 위치: src/parse/run_receipt_mapping.py
# parents[0]: src/parse
# parents[1]: src
# parents[2]: OCR_receipts (프로젝트 루트)
FILE = Path(__file__).resolve()
ROOT = FILE.parents[2]

if str(ROOT) not in sys.path:
    sys.path.append(str(ROOT))  # 시스템 경로에 프로젝트 루트 추가
# ==========================================

# 이제 src 모듈을 정상적으로 찾습니다.
from src.parse.receipt_mapping import extract_receipt_fields

# 경로 설정
LINES_DIR = ROOT / "data" / "outputs" / "lines"
OUT_DIR = ROOT / "data" / "outputs" / "parsed"
OUT_DIR.mkdir(parents=True, exist_ok=True)

print(f"📂 입력 폴더: {LINES_DIR}")
print(f"📂 출력 폴더: {OUT_DIR}")

# 파일 처리
found_files = list(sorted(LINES_DIR.glob("*.lines.json")))
if not found_files:
    print(f"⚠️ 처리할 파일이 없습니다. 먼저 run_line_grouping.py를 실행했는지 확인하세요.")
else:
    for p in found_files:
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            image = data.get("image")
            # "lines" 키에 있는 리스트를 가져오거나 없으면 빈 리스트
            lines = data.get("lines") or []

            # 🚀 영수증 파싱 핵심 로직 실행
            res = extract_receipt_fields(lines)

            # 결과 정리
            out = {
                "image": image,
                "merchant": res.merchant,
                "date": res.date,
                "datetime": res.datetime,
                "items": res.items,
                "total": res.total,
                "total_confidence": res.total_confidence,
            }

            # 저장
            out_filename = p.stem.replace('.lines', '') + ".receipt.json"
            out_path = OUT_DIR / out_filename
            out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
            
            print(f"[OK] {p.name} -> {out_filename}")

        except Exception as e:
            print(f"[ERROR] {p.name} 파싱 중 오류: {e}")

    print("\n✅ receipt mapping 완료:", OUT_DIR)