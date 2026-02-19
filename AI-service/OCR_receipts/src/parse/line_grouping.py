# src/parse/line_grouping.py
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Tuple, Optional


# ============================================================
# Token 타입
# ============================================================
@dataclass
class Token:
    text: str
    score: float
    box: List[List[float]]  # 4 points [[x,y],...]
    x1: float
    y1: float
    x2: float
    y2: float
    xc: float
    yc: float
    h: float
    w: float


# ============================================================
# Box 유틸
# ============================================================
def box_to_bbox(box: List[List[float]]) -> Tuple[float, float, float, float]:
    """4점 box를 (x1,y1,x2,y2) bbox로 변환"""
    xs = [p[0] for p in box]
    ys = [p[1] for p in box]
    x1, x2 = float(min(xs)), float(max(xs))
    y1, y2 = float(min(ys)), float(max(ys))
    return x1, y1, x2, y2


def median(values: List[float]) -> float:
    if not values:
        return 0.0
    v = sorted(values)
    n = len(v)
    mid = n // 2
    if n % 2 == 1:
        return v[mid]
    return (v[mid - 1] + v[mid]) / 2.0


# ============================================================
# 1) items(JSON) -> Token 리스트 (노이즈 제거 포함)
# ============================================================
def build_tokens(
    items: List[Dict[str, Any]],
    min_score: float = 0.0,
    min_w: float = 2.0,
    min_h: float = 8.0,
) -> List[Token]:
    """
    OCR items(JSON) -> Token 리스트로 정규화

    - min_score: rec 신뢰도 기준
    - min_w/min_h: 너무 작은 박스(점선/노이즈) 제거
    """
    tokens: List[Token] = []
    for it in items:
        text = (it.get("text") or "").strip()
        if not text:
            continue

        score = float(it.get("score", 0.0))
        if score < min_score:
            continue

        box = it.get("box")
        if not box or len(box) != 4:
            continue

        x1, y1, x2, y2 = box_to_bbox(box)
        w, h = (x2 - x1), (y2 - y1)

        # ✅ 노이즈 박스 제거
        if w < min_w or h < min_h:
            continue

        xc = x1 + w / 2.0
        yc = y1 + h / 2.0

        tokens.append(
            Token(
                text=text,
                score=score,
                box=box,
                x1=x1, y1=y1, x2=x2, y2=y2,
                xc=xc, yc=yc,
                h=h, w=w,
            )
        )
    return tokens


# ============================================================
# 2) 라인 그룹핑 (Windows OCR 핵심: 라인별 y_thresh 적응)
# ============================================================
def group_tokens_into_lines(
    tokens: List[Token],
    y_threshold_ratio: float = 0.55,
) -> List[List[Token]]:
    """
    좌표 기반 line grouping.

    핵심:
    - 전체 median 높이(med_h)로 기본 임계값을 잡고,
    - 현재 라인의 높이(line_h)에 따라 y_thresh를 "매 토큰마다" 적응시킨다.
      (영수증은 헤더/본문/합계에서 글자 크기가 달라서 이게 중요)
    """
    if not tokens:
        return []

    # 위→아래 정렬, 같은 높이대에서는 좌→우
    tokens_sorted = sorted(tokens, key=lambda t: (t.yc, t.x1))

    # 전체 높이 중앙값(기본)
    med_h = median([t.h for t in tokens_sorted])
    if med_h <= 0:
        med_h = 10.0

    lines: List[List[Token]] = []
    current_line: List[Token] = []
    current_y: Optional[float] = None

    for t in tokens_sorted:
        if not current_line:
            current_line = [t]
            current_y = t.yc
            continue

        assert current_y is not None

        # 라인별 높이 기반으로 y_thresh를 매번 업데이트
        line_h = median([x.h for x in current_line]) or med_h
        y_thresh = line_h * y_threshold_ratio

        # 현재 줄의 대표 y(평균)에 가까우면 같은 줄로 묶음
        if abs(t.yc - current_y) <= y_thresh:
            current_line.append(t)
            # 줄 대표 y 업데이트(평균)
            current_y = sum(x.yc for x in current_line) / len(current_line)
        else:
            # 줄 종료 -> x 기준 정렬 후 저장
            current_line = sorted(current_line, key=lambda x: x.x1)
            lines.append(current_line)

            # 새 줄 시작
            current_line = [t]
            current_y = t.yc

    # 마지막 줄 처리
    if current_line:
        current_line = sorted(current_line, key=lambda x: x.x1)
        lines.append(current_line)

    return lines


# ============================================================
# 3) Windows OCR 느낌: 토큰 간격 기반 공백 처리
# ============================================================
def join_tokens_with_spacing(line: List[Token], gap_ratio: float = 0.9) -> str:
    """
    token.w 대신 "문자당 폭" 기반으로 gap 임계값을 잡는다.
    긴 토큰(가게명 등)이 섞여도 공백이 제대로 들어가게 됨.
    """
    if not line:
        return ""

    line = sorted(line, key=lambda t: t.x1)

    # 문자당 폭(char_width) 중앙값
    char_widths = []
    for t in line:
        n = max(len(t.text), 1)
        char_widths.append(t.w / n)
    med_char_w = median(char_widths) or 10.0

    gap_thresh = med_char_w * gap_ratio  # ✅ 훨씬 안정적

    parts = [line[0].text]
    prev = line[0]
    for t in line[1:]:
        gap = t.x1 - prev.x2
        if gap >= gap_thresh:
            parts.append(" ")
        parts.append(t.text)
        prev = t

    return "".join(parts).strip()


# ============================================================
# 4) 라인 JSON 변환 (+ 라인 bbox 포함)
# ============================================================
def lines_to_json(lines: List[List[Token]]) -> List[Dict[str, Any]]:
    """
    line grouping 결과를 저장하기 좋은 JSON 형태로 변환

    - line_text: spacing 기반 조합
    - line_bbox: 라인 영역 bbox(후처리, total 추출 등에 매우 유용)
    """
    out: List[Dict[str, Any]] = []
    for idx, line in enumerate(lines):
        if not line:
            continue

        line_text = join_tokens_with_spacing(line)

        line_x1 = min(t.x1 for t in line)
        line_y1 = min(t.y1 for t in line)
        line_x2 = max(t.x2 for t in line)
        line_y2 = max(t.y2 for t in line)

        out.append(
            {
                "line_index": idx,
                "line_text": line_text,
                "line_bbox": [line_x1, line_y1, line_x2, line_y2],
                "tokens": [
                    {
                        "text": tok.text,
                        "score": tok.score,
                        "box": tok.box,
                        "bbox": [tok.x1, tok.y1, tok.x2, tok.y2],
                        "center": [tok.xc, tok.yc],
                    }
                    for tok in sorted(line, key=lambda x: x.x1)
                ],
            }
        )
    return out
