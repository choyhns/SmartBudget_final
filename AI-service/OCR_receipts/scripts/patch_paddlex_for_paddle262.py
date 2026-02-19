"""
Paddle 2.6.2 + PaddleX 호환 패치.
PaddleX가 set_optimization_level(3)를 호출하는데 Paddle 2.6.2 AnalysisConfig에는 없어서
AttributeError가 나는 경우, 이 스크립트로 venv 내 파일을 수정합니다.

사용: AI-service 폴더에서
  .venv310\\Scripts\\python.exe OCR_receipts\\scripts\\patch_paddlex_for_paddle262.py
또는 가상환경 활성화 후
  python OCR_receipts/scripts/patch_paddlex_for_paddle262.py
"""
from pathlib import Path

def main():
    # AI-service 기준 venv 경로
    base = Path(__file__).resolve().parents[2]  # OCR_receipts
    base = base.parent  # AI-service
    venv = base / ".venv310"
    target = venv / "lib" / "site-packages" / "paddlex" / "inference" / "models" / "common" / "static_infer.py"
    if not target.exists():
        print(f"대상 파일 없음: {target}")
        return 1
    text = target.read_text(encoding="utf-8")
    old = "                config.set_optimization_level(3)"
    new = '                if hasattr(config, "set_optimization_level"):\n                    config.set_optimization_level(3)'
    if new in text:
        print("이미 패치 적용됨.")
        return 0
    if old not in text:
        print("패치할 문자열을 찾을 수 없습니다. PaddleX 버전이 다를 수 있습니다.")
        return 1
    text = text.replace(old, new)
    target.write_text(text, encoding="utf-8")
    print("패치 적용 완료:", target)
    return 0

if __name__ == "__main__":
    exit(main())
