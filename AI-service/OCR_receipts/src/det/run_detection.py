# src/det/run_detection.py
from pathlib import Path

import cv2
from paddleocr import PaddleOCR

"""
한 영수증(원본 이미지)마다
- PaddleOCR로 탐지된 모든 박스를 한 장에 그려서
- YOLO 시각화처럼 bbox + (x1, y1, x2, y2) 좌표를 텍스트로 표시한
시각화 이미지를 저장하는 스크립트.
"""

ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "data" / "raw_images" / "img"
VIS_DIR = ROOT / "data" / "det_vis"  # 👉 한 장짜리 시각화 이미지 저장 폴더
VIS_DIR.mkdir(parents=True, exist_ok=True)

ocr = PaddleOCR(lang="korean", use_angle_cls=False)  # 속도 우선: 각도 분류 비활성화

for img_path in RAW_DIR.iterdir():
    if img_path.suffix.lower() not in [".jpg", ".jpeg", ".png"]:
        continue

    image = cv2.imread(str(img_path))
    if image is None:
        continue

    # 시각화용 복사본
    vis = image.copy()

    # ✅ path 말고 image로 넣어서 좌표 일치!
    result = ocr.ocr(image)
    if not result or not result[0]:
        continue

    for i, line in enumerate(result[0]):
        box = line[0]
        text = (line[1][0] or "").strip()
        rec_score = float(line[1][1])

        # 1) box -> axis-aligned bbox 먼저 계산
        xs = [int(p[0]) for p in box]
        ys = [int(p[1]) for p in box]
        x1, x2 = max(min(xs), 0), min(max(xs), image.shape[1])
        y1, y2 = max(min(ys), 0), min(max(ys), image.shape[0])

        # 2) 필터(노이즈 제거)
        if not text:
            continue
        if rec_score < 0.5:
            continue
        if (y2 - y1) < 12 or (x2 - x1) < 30:
            continue

        # 3) YOLO 스타일 박스 그리기 (한 장에 계속 누적)
        # 빨간 박스
        cv2.rectangle(vis, (x1, y1), (x2, y2), (0, 0, 255), 2)

        # 좌표 + 신뢰도 라벨 (위쪽에 작은 글씨)
        label = f"{i}: ({x1},{y1})-({x2},{y2}) s={rec_score:.2f}"
        # 텍스트가 이미지 밖으로 안 나가게 y좌표 보정
        text_org_y = y1 - 5 if y1 - 5 > 10 else y1 + 15
        cv2.putText(
            vis,
            label,
            (x1, text_org_y),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.4,
            (0, 255, 0),
            1,
            cv2.LINE_AA,
        )

    # 한 영수증당 최종 시각화 결과 한 장 저장
    out_name = f"{img_path.stem}_det_vis.png"
    out_path = VIS_DIR / out_name
    cv2.imwrite(str(out_path), vis)
    print(f"✅ saved detection vis: {out_path}")

print("✅ YOLO 스타일 탐지 시각화 완료:", VIS_DIR)
