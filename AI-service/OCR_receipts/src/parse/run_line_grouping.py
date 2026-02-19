import json
import sys
import os
from pathlib import Path

# ==========================================
# [추가됨] 프로젝트 루트 경로 강제 설정
# ==========================================
# 현재 파일 위치: src/parse/run_line_grouping.py
# parents[0]: src/parse
# parents[1]: src
# parents[2]: OCR_receipts (프로젝트 루트)
FILE = Path(__file__).resolve()
ROOT = FILE.parents[2]

if str(ROOT) not in sys.path:
    sys.path.append(str(ROOT))  # 시스템 경로에 프로젝트 루트 추가
# ==========================================

# 이제 src 모듈을 정상적으로 찾을 수 있습니다.
from src.parse.line_grouping import build_tokens, group_tokens_into_lines, lines_to_json

BASE_DIR = ROOT / "data"
IN_JSON_DIR = BASE_DIR / "json"          # OCR 결과 json들이 있는 폴더
OUT_LINES_DIR = BASE_DIR / "outputs" / "lines"

OUT_LINES_DIR.mkdir(parents=True, exist_ok=True)

def main():
    IN_JSON_DIR.mkdir(parents=True, exist_ok=True)
    json_files = sorted(IN_JSON_DIR.glob("*.json"))
    
    if not json_files:
        print(f"[!] 입력 JSON이 없습니다: {IN_JSON_DIR}")
        print("    OCR 결과 json을 위 경로에 넣은 뒤 다시 실행하거나, API POST /debug/ocr_lines 로 lines_json 확인.")
        return

    print(f"📂 입력 폴더: {IN_JSON_DIR}")
    print(f"📂 출력 폴더: {OUT_LINES_DIR}")

    for jf in json_files:
        try:
            data = json.loads(jf.read_text(encoding="utf-8"))
            image_name = data.get("image", jf.stem)
            items = data.get("items", [])

            # 1) 토큰 정규화
            tokens = build_tokens(items, min_score=0.3)

            # 2) 줄 묶기 (y_thresh 0.55 정도가 영수증에 적절)
            lines = group_tokens_into_lines(tokens, y_threshold_ratio=0.55)

            # 3) 저장용 변환
            # join_tokens_with_spacing 함수 내부 로직(3번 함수) 수정이 필요할 수도 있음 (매개변수 확인)
            # line_grouping.py 파일의 join_tokens_with_spacing 함수를 lines_to_json 내부에서 잘 쓰고 있는지 확인 필요
            # (작성해주신 line_grouping.py 코드상으로는 문제없음)
            
            lines_json_data = lines_to_json(lines)

            out = {
                "image": image_name,
                "line_count": len(lines),
                "lines": lines_json_data,
            }

            out_path = OUT_LINES_DIR / f"{jf.stem}.lines.json"
            out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")

            print(f"[OK] {jf.name} -> {out_path.name} (lines={len(lines)})")
            
        except Exception as e:
            print(f"[ERROR] {jf.name} 처리 중 오류 발생: {e}")

if __name__ == "__main__":
    main()