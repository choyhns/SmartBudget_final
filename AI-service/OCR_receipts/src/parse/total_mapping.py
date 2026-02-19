# src/parse/total_mapping.py
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

# ============================================================
# 0) OCR 오인식 보정용 (Total -> Tota1 같은 케이스)
# ============================================================
# [각주] 영수증 OCR에서 자주 혼동되는 문자들을 "키워드 탐지용"으로만 정규화한다.
#       (금액 파싱에는 영향 최소화)
CONFUSION_MAP = str.maketrans({
    "1": "l",
    "I": "l",
    "|": "l",
    "0": "o",
    "O": "o",
})

def normalize_ocr_text(s: str) -> str:
    """
    [각주] '키워드 매칭'을 안정화하기 위한 정규화.
    - tota1, totaI -> total 처럼 보이게 만듦
    - 영어/숫자/한글만 남기고 나머지 특수문자 제거
    """
    s = (s or "").strip()
    s = s.translate(CONFUSION_MAP)
    s = s.lower()
    s = re.sub(r"[^a-z0-9가-힣\s\.]", " ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


# ============================================================
# 1) TOTAL/합계 키워드 사전
# ============================================================
# [각주] 'total'을 엄격하게 찾으면 tota1 같은 오타에서 바로 실패하므로
#       오타까지 허용하는 패턴을 사용한다.
TOTAL_KEY_PATTERNS = [
    # English (오타 허용 total)
    r"\bt[o0]ta[l1i\|]\b",               # total / tota1 / totaI / tota|
    r"\bgrand\s*t[o0]ta[l1i\|]\b",
    r"\bamount\s*due\b",
    r"\bbalance\s*due\b",
    r"\bnet\s*t[o0]ta[l1i\|]\b",
    r"\bamount\b",  # (선택) 너무 넓을 수 있어 필요하면 제거

    # Korean
    r"총\s*액",
    r"합\s*계",
    r"총\s*합\s*계",
    r"결\s*제\s*금\s*액",
    r"총\s*결\s*제\s*금\s*액",
    r"결\s*제\s*액",
    r"청\s*구\s*금\s*액",
    r"받\s*을\s*금\s*액",
]

# [각주] SUBTOTAL(소계)는 TOTAL과 헷갈리는 대표 키워드라 감점용으로 둔다.
SUBTOTAL_NEG_PATTERNS = [
    r"\bsubtotal\b",
    r"소\s*계",
]

TOTAL_KEY_RE = re.compile("|".join(TOTAL_KEY_PATTERNS), re.IGNORECASE)
SUBTOTAL_NEG_RE = re.compile("|".join(SUBTOTAL_NEG_PATTERNS), re.IGNORECASE)


# ============================================================
# 2) 결제/거스름돈 키워드 (Cash/Change fallback에 사용)
# ============================================================
# [각주] TOTAL 라인이 깨져서 못 잡히는 경우,
#       'Cash', 'Card', 'Change' 근처에서 TOTAL을 역추정한다.
PAYMENT_KEY_RE = re.compile(r"\b(cash|card|visa|master|amex|payment|paid)\b", re.IGNORECASE)
CHANGE_KEY_RE = re.compile(r"\b(change)\b", re.IGNORECASE)

# [각주] TOTAL 라인에 자주 같이 등장하는 맥락 단어들 (inc gst, vat 등)
TOTAL_CONTEXT_RE = re.compile(r"\b(inc|incl|gst|vat|tax|rm|krw|usd|eur|jpy)\b", re.IGNORECASE)


# ============================================================
# 3) 금액 파싱
# ============================================================
# [각주] prefix/suffix로 통화 표기가 붙거나 공백이 있어도 숫자만 뽑아서 float로 변환한다.
MONEY_RE = re.compile(
    r"""
    (?P<prefix>[\₩￦$€¥]|KRW|USD|SAR|RM|JPY|CNY|EUR)?\s*
    (?P<num>
        (?:\d{1,3}(?:,\d{3})+|\d+)
        (?:\.\d{2})?
    )
    \s*(?P<suffix>원|KRW|USD|SAR|RM|JPY|CNY|EUR)?
    """,
    re.VERBOSE | re.IGNORECASE,
)

def normalize_money_str(s: str) -> Optional[float]:
    """
    [각주] 토큰 문자열에서 금액 숫자를 float로 변환한다.
    예) "RM 4.90" / "11,700원" / "$ 19.30" -> 4.9 / 11700.0 / 19.3
    """
    s = (s or "").strip()
    m = MONEY_RE.search(s)
    if not m:
        return None
    num = m.group("num").replace(",", "")
    try:
        return float(num)
    except Exception:
        return None

def find_money_candidates_in_line(line: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    [각주] 한 줄에서 '금액으로 파싱 가능한 토큰'만 후보로 모은다.
    - bbox가 있어야 "오른쪽 끝 금액" 같은 레이아웃 규칙을 적용할 수 있다.
    """
    cands: List[Dict[str, Any]] = []
    for tok in line.get("tokens", []):
        txt = (tok.get("text") or "").strip()
        val = normalize_money_str(txt)
        if val is None:
            continue

        bbox = tok.get("bbox") or []
        if len(bbox) != 4:
            continue

        cands.append({
            "text": txt,
            "value": val,
            "bbox": bbox,                    # [x1,y1,x2,y2]
            "score": float(tok.get("score", 0.0)),  # OCR confidence
        })
    return cands

def rightmost_money(cands: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    """
    [각주] 영수증은 보통 "항목(왼쪽) + 금액(오른쪽)" 구조라
    같은 줄 후보가 여러개면 가장 오른쪽(x2 최대)을 우선한다.
    """
    if not cands:
        return None
    return max(cands, key=lambda x: x["bbox"][2])


# ============================================================
# 4) 키워드 탐지 helpers
# ============================================================
def line_contains_total_key(line_text: str) -> bool:
    """
    [각주] OCR 오인식(1/I/|/0/O)을 normalize해서 TOTAL 키워드를 잡는다.
    """
    if not line_text:
        return False
    norm = normalize_ocr_text(line_text)
    return TOTAL_KEY_RE.search(norm) is not None

def subtotal_like(line_text: str) -> bool:
    """
    [각주] 소계/subtotal 라인은 TOTAL과 혼동되므로 감점 대상으로 판단한다.
    """
    if not line_text:
        return False
    norm = normalize_ocr_text(line_text)
    return SUBTOTAL_NEG_RE.search(norm) is not None

def context_bonus(line_text: str) -> float:
    """
    [각주] TOTAL 라인 주변에 자주 등장하는 단어가 있으면 가중치 보너스.
    ex) 'inc gst', 'rm', 'vat' 등이 있으면 TOTAL일 가능성이 올라감
    """
    if not line_text:
        return 1.0
    norm = normalize_ocr_text(line_text)
    return 1.12 if TOTAL_CONTEXT_RE.search(norm) else 1.0


# ============================================================
# 5) TOTAL 추출 결과 타입
# ============================================================
@dataclass
class TotalResult:
    amount: float
    raw_text: str
    line_index: int
    method: str        # "same_line" | "next_line" | "near_payment" | "paid_minus_change"
    confidence: float


def pick_better(a: Optional[TotalResult], b: TotalResult) -> TotalResult:
    """
    [각주] 후보가 여러개면 'confidence'가 높은 것을 우선.
    동점이면 보통 TOTAL이 더 큰 금액인 경우가 많아서 큰 금액을 우선.
    """
    if a is None:
        return b
    if b.confidence > a.confidence + 1e-9:
        return b
    if abs(b.confidence - a.confidence) <= 1e-9 and b.amount >= a.amount:
        return b
    return a


# ============================================================
# 6) (Fallback) Cash/Change 근처에서 TOTAL 찾기
# ============================================================
def extract_total_near_payment(lines: List[Dict[str, Any]]) -> Optional[TotalResult]:
    """
    [각주] 1차: TOTAL 키워드 탐지가 실패했을 때를 대비한 fallback.
    - 'Cash', 'Card', 'Paid', 'Change' 같은 줄을 찾는다.
    - 그 줄 근처(위쪽 몇 줄)에서 TOTAL로 보이는 금액을 찾는다.
    - Change가 있으면 paid - change = total 관계를 이용할 수도 있다.

    전략:
    1) change 라인이 있으면: (paid 라인) - (change) 로 total 계산 시도
    2) payment/paid 라인이 있으면: 그 위쪽 1~3줄에서 "오른쪽 금액" 후보를 찾아 total로 채택
    """
    if not lines:
        return None

    # --- 6-1) change / paid 금액을 먼저 모아둠 ---
    paid_candidates: List[tuple[int, Dict[str, Any]]] = []
    change_candidates: List[tuple[int, Dict[str, Any]]] = []

    for i, line in enumerate(lines):
        lt = (line.get("line_text") or "")
        norm = normalize_ocr_text(lt)

        cands = find_money_candidates_in_line(line)
        best_money = rightmost_money(cands)

        if best_money is None:
            continue

        if CHANGE_KEY_RE.search(norm):
            change_candidates.append((i, best_money))
        if PAYMENT_KEY_RE.search(norm):
            paid_candidates.append((i, best_money))

    # --- 6-2) paid - change로 total 역산 (가능하면 가장 신뢰도 높음) ---
    # [각주] 영수증에서 paid(현금/카드)와 change가 같이 있으면 total = paid - change 인 경우가 흔함.
    best: Optional[TotalResult] = None
    if change_candidates and paid_candidates:
        for (ci, cchg) in change_candidates:
            # change 라인 근처 아래/위에서 paid를 찾되, 보통 paid가 change 위에 있음
            # 여기서는 단순히 가장 가까운 paid를 선택
            closest_paid = min(paid_candidates, key=lambda x: abs(x[0] - ci))
            pi, cpaid = closest_paid

            # paid가 change보다 위/아래 어디든 있을 수 있지만,
            # 계산 결과가 음수면 버림
            total_val = cpaid["value"] - cchg["value"]
            if total_val <= 0:
                continue

            # confidence: OCR score 둘 다 반영 + 관계식 보너스
            conf = (cpaid["score"] * 0.5 + cchg["score"] * 0.4 + 0.2)
            cand_res = TotalResult(
                amount=float(total_val),
                raw_text=f"{cpaid['text']} - {cchg['text']}",
                line_index=ci,
                method="paid_minus_change",
                confidence=conf,
            )
            best = pick_better(best, cand_res)

    # --- 6-3) payment 라인 위쪽에서 total 후보 찾기 ---
    # [각주] total 라인이 깨졌어도 보통 payment 바로 위쪽에 total이 존재한다.
    #       예: "Total ...", "Cash ...", "Change ..."
    LOOKBACK = 3
    for (pi, p_money) in paid_candidates:
        for j in range(max(0, pi - LOOKBACK), pi):
            line_j = lines[j]
            cands = find_money_candidates_in_line(line_j)
            m = rightmost_money(cands)
            if not m:
                continue

            # 너무 큰 숫자(전화번호/사업자번호) 등 오탐 방지용 간단 필터
            # [각주] total은 보통 수백만 단위 이상은 드뭄(프로젝트 상황에 맞게 조절)
            if m["value"] > 100000000:
                continue

            # payment 근처에서 발견된 후보는 fallback이라 confidence는 다소 낮게 시작
            # 대신 context(inc gst/rm 등) 있으면 보너스
            lt = (line_j.get("line_text") or "")
            bonus = context_bonus(lt)

            conf = (m["score"] * 0.6 + 0.2) * bonus
            cand_res = TotalResult(
                amount=m["value"],
                raw_text=m["text"],
                line_index=j,
                method="near_payment",
                confidence=conf,
            )
            best = pick_better(best, cand_res)

    return best


# ============================================================
# 7) TOTAL 추출 메인
# ============================================================
def extract_total_from_lines(lines: List[Dict[str, Any]]) -> Optional[TotalResult]:
    """
    [각주] 최종 TOTAL 추출 파이프라인(우선순위)
    1) TOTAL 키워드 라인 기반 (같은 줄 / 다음 줄)
    2) 실패 시: Cash/Change 근처 fallback

    반환: TotalResult 1개(가장 그럴듯한 후보)
    """
    best: Optional[TotalResult] = None

    # ---- 7-1) 정상 루트: TOTAL 키워드 라인 기반 ----
    for i, line in enumerate(lines):
        line_text = (line.get("line_text") or "").strip()

        if not line_contains_total_key(line_text):
            continue

        # 키 라인 점수: subtotal이면 감점
        key_bonus = 1.0
        if subtotal_like(line_text):
            key_bonus = 0.4

        # 키 라인 맥락 보너스 (GST/RM/inc 등)
        key_bonus *= context_bonus(line_text)

        # (A) 같은 줄에서 값 찾기
        same_cands = find_money_candidates_in_line(line)
        same_best = rightmost_money(same_cands)
        if same_best:
            conf = (same_best["score"] * 0.7 + 0.3) * key_bonus
            cand_res = TotalResult(
                amount=same_best["value"],
                raw_text=same_best["text"],
                line_index=i,
                method="same_line",
                confidence=conf,
            )
            best = pick_better(best, cand_res)

        # (B) 다음 줄에서 값 찾기 (키:TOTAL, 값은 next line)
        if i + 1 < len(lines):
            next_line = lines[i + 1]
            next_cands = find_money_candidates_in_line(next_line)
            next_best = rightmost_money(next_cands)
            if next_best:
                conf = (next_best["score"] * 0.65 + 0.25) * key_bonus
                cand_res = TotalResult(
                    amount=next_best["value"],
                    raw_text=next_best["text"],
                    line_index=i + 1,
                    method="next_line",
                    confidence=conf,
                )
                best = pick_better(best, cand_res)

    # ---- 7-2) fallback: 결제/거스름돈 근처에서 TOTAL 찾기 ----
    if best is None:
        best = extract_total_near_payment(lines)

    return best
