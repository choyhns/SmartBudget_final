# src/parse/run_total_mapping.py
import json
from pathlib import Path

from src.parse.total_mapping import extract_total_from_lines

BASE_DIR = Path(r"C:\Python310\OCR_receipts\data")  # 프로젝트 데이터 폴더
IN_LINES_DIR = BASE_DIR / "outputs" / "lines"
OUT_PARSED_DIR = BASE_DIR / "outputs" / "parsed"

OUT_PARSED_DIR.mkdir(parents=True, exist_ok=True)

def main():
    files = sorted(IN_LINES_DIR.glob("*.lines.json"))
    if not files:
        print(f"[!] lines json이 없습니다: {IN_LINES_DIR}")
        return

    for f in files:
        data = json.loads(f.read_text(encoding="utf-8"))
        image = data.get("image", f.stem)
        lines = data.get("lines", [])

        total = extract_total_from_lines(lines)

        out = {
            "image": image,
            "total": None if total is None else {
                "amount": total.amount,
                "raw_text": total.raw_text,
                "line_index": total.line_index,
                "method": total.method,
                "confidence": total.confidence,
            }
        }

        out_path = OUT_PARSED_DIR / f"{f.stem}.parsed.json"
        out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[OK] {f.name} -> {out_path.name} | total={out['total']}")

if __name__ == "__main__":
    main()
