# src/parse/receipt_mapping.py
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

# -----------------------------
# Regex: money / date / time
# -----------------------------
MONEY_RE = re.compile(r"(?<!\d)(\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{2})?(?!\d)")
DATE_RE = re.compile(r"(20\d{2})[-./](\d{1,2})[-./](\d{1,2})")  # ✅ \b 제거
BIZ_RE = re.compile(r"(?<!\d)\d{3}\s*[-–—]\s*\d{2}\s*[-–—]\s*\d{5}(?!\d)")
TEL_RE = re.compile(r"\b0\d{1,2}-\d{3,4}-\d{4}\b")
TIME_COLON_RE = re.compile(r"\b(\d{1,2}):(\d{2})(?::(\d{2}))?\b")
TIME_DIGITS_RE = re.compile(r"(?<!\d)(\d{4,6})(?!\d)")  # 12451, 125132 등
# 상호/지점 끝 패턴 (원하는 형태)
MERCHANT_END_RE = re.compile(r"(점|지점|점포|매장)$")
# 가맹점명 라인: "가맹점명 xxxxx" 에서 xxxxx 추출용
GAMYEONGMYEONG_RE = re.compile(r"가맹점\s*명?\s*[:\s]*", re.IGNORECASE)

# -----------------------------
# Keywords
# -----------------------------
PAYMENT_KEYS = [
    "신용카드", "체크카드", "카드", "현금", "계좌", "이체", "페이", "PAY", "CASH", "CARD"
]
STOPWORDS_MERCHANT = [
    "영수증", "교환권", "승인", "번호", "TEL", "전화", "주소", "사업자", "단말", "POS",
    "주문", "일시", "상품명", "수량", "공급가액", "부가세", "합계", "결제", "카드"
]
# 문서 헤더/OCR 오인식: 가맹점으로 채택 금지 (매출전표(고객용) → 마손전 고객용 등)
DOC_HEADER_MERCHANT = [
    "매출전표", "고객용", "마손전", "전표", "매출",
]

# -----------------------------
@dataclass
class ReceiptResult:
    merchant: Optional[str]
    date: Optional[str]         # YYYY-MM-DD
    datetime: Optional[str]     # YYYY-MM-DD HH:MM:SS
    items: List[str]
    total: Optional[float]
    total_confidence: float


def _money_candidates_in_text(s: str) -> List[float]:
    vals = []
    for m in MONEY_RE.finditer(s or ""):
        try:
            vals.append(float(m.group(1).replace(",", "")))
        except:
            pass
    return vals

TOTAL_KEYS = [
    "합계", "합계금액", "총액", "결제금액", "승인금액", "판매금액", "받을금액", "청구금액"
]

def _best_total_from_keywords(lines: List[Dict[str, Any]]) -> Tuple[Optional[float], float]:
    best_val, best_score = None, 0.0

    for ln in lines:
        txt = ln.get("line_text", "") or ""
        if not any(k in txt for k in TOTAL_KEYS):
            continue

        cands = _money_candidates_in_text(txt)
        if not cands:
            # tokens에서 숫자 후보도 탐색
            for t in (ln.get("tokens") or []):
                cands += _money_candidates_in_text(t.get("text", ""))

        if not cands:
            continue

        # 합계/결제 관련 라인은 "가장 큰 금액"이 total일 확률이 큼
        val = max(cands)
        score = 0.95
        if val > best_val if best_val is not None else True:
            best_val, best_score = val, score

    return best_val, best_score

def _best_total_from_payment_context(lines: List[Dict[str, Any]]) -> Tuple[Optional[float], float]:
    """
    키워드(합계/결제금액 등)로 total을 못 찾았을 때의 2차 fallback.

    전략:
    - '신용카드/현금/카드/페이' 같은 결제수단 라인이 있으면
      그 줄(또는 다음 줄)의 금액 후보 중 "가장 큰 값"을 total로 본다.
    - 너무 작은 값(세금, 포인트 등)은 어느 정도 제외한다.
    """
    best_val: Optional[float] = None
    best_score: float = 0.0

    for i, ln in enumerate(lines):
        txt = ln.get("line_text", "") or ""

        # 결제수단 키워드가 있는 줄인지 확인
        if not any(k in txt for k in PAYMENT_KEYS):
            continue

        # 1) 같은 줄에서 금액 후보 찾기
        cands = _money_candidates_in_text(txt)

        # tokens에서도 추가로 찾기 (line_text가 붙어서 깨지는 경우 대비)
        for t in (ln.get("tokens") or []):
            cands += _money_candidates_in_text(t.get("text", ""))

        # 2) 없으면 다음 줄도 살짝 본다 (결제수단 다음 줄에 금액만 있는 케이스)
        if not cands and (i + 1) < len(lines):
            nxt = lines[i + 1]
            nxt_txt = nxt.get("line_text", "") or ""
            cands = _money_candidates_in_text(nxt_txt)
            for t in (nxt.get("tokens") or []):
                cands += _money_candidates_in_text(t.get("text", ""))

        if not cands:
            continue

        # 보통 결제금액이 가장 큼
        val = max(cands)

        # 너무 작은 값(세금/GST/할인 등) 배제용 (너 상황에 맞게 조정 가능)
        if val < 100 and val != 0:  # 0은 "거스름돈 0" 같은 경우도 있어 일단 허용
            continue

        score = 0.75  # payment fallback이므로 키워드보다 낮게

        if best_val is None or score > best_score or (abs(score - best_score) < 1e-9 and val > best_val):
            best_val, best_score = val, score

    return best_val, best_score


def _extract_date_time(lines: List[Dict[str, Any]]) -> Tuple[Optional[str], Optional[str]]:
    """
    - line_text가 붙어도 DATE_RE가 잡히게 함(\b 제거)
    - 더 중요: line.tokens에서 date/time 토큰을 직접 찾음
    """
    date = None
    dt = None

    for i, ln in enumerate(lines):
        # 1) tokens 기반 우선
        tokens = ln.get("tokens") or []
        token_texts = [t.get("text", "") for t in tokens]

        # date token 찾기
        for t in token_texts:
            m = DATE_RE.search(t)
            if m:
                y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
                date = f"{y:04d}-{mo:02d}-{d:02d}"

                # 같은 라인에서 time token 찾기
                time_hms = None
                for tt in token_texts:
                    mc = TIME_COLON_RE.search(tt)
                    if mc:
                        hh = int(mc.group(1)); mm = int(mc.group(2))
                        ss = int(mc.group(3)) if mc.group(3) else 0
                        time_hms = f"{hh:02d}:{mm:02d}:{ss:02d}"
                        break

                dt = f"{date} {time_hms}" if time_hms else None
                return date, dt

        # 2) fallback: line_text에서 찾기(붙어도 잡히게 DATE_RE 수정됨)
        txt = ln.get("line_text", "") or ""
        m = DATE_RE.search(txt)
        if m:
            y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
            date = f"{y:04d}-{mo:02d}-{d:02d}"
            mc = TIME_COLON_RE.search(txt)
            if mc:
                hh = int(mc.group(1)); mm = int(mc.group(2))
                ss = int(mc.group(3)) if mc.group(3) else 0
                dt = f"{date} {hh:02d}:{mm:02d}:{ss:02d}"
            return date, dt

    return None, None


def _extract_items(lines: List[Dict[str, Any]]) -> List[str]:
    """
    '상품명' 라인 이후부터 결제/합계/카드 섹션 전까지를 item 후보로 추출.
    """
    start = None
    for i, ln in enumerate(lines):
        if "상품명" in (ln.get("line_text") or ""):
            start = i + 1
            break
    if start is None:
        return []

    items = []
    for ln in lines[start:]:
        txt = (ln.get("line_text") or "").strip()
        if not txt:
            continue

        # 결제 섹션/합계 섹션 시작하면 중단
        if any(k in txt for k in ["공급가액", "부가세", "합계", "결제", "신용카드", "현금", "카드"]):
            break

        # 너무 숫자 위주 라인은 제외(수량/코드 등)
        if len(re.sub(r"[0-9\s\-:./]", "", txt)) < 2:
            continue

        items.append(txt)

    # 중복 제거(보존 순서)
    seen = set()
    out = []
    for x in items:
        if x not in seen:
            out.append(x)
            seen.add(x)
    return out

def trim_merchant_after_jum(s: str) -> str:
    """
    가맹점명이 "한국 피자헛 호매실점(0735)" 처럼 나오면 ~점까지만 사용.
    점 뒤의 괄호·숫자 등을 제거한다.
    """
    s = (s or "").strip()
    # 끝의 괄호 부분 제거: "호매실점(0735)" -> "호매실점"
    s = re.sub(r"\s*\([^)]*\)\s*$", "", s)
    # 끝의 공백+숫자 제거: "호매실점 0735" -> "호매실점"
    s = re.sub(r"\s+\d+\s*$", "", s)
    return s.strip()


def clean_merchant(s: str) -> str:
    s = (s or "").strip()
    s = re.sub(r"[|]", " ", s)

    # 슬래시가 있으면 보통 '상호 / 사업자번호 / 대표자' 형태라서 앞부분만
    if "/" in s:
        s = s.split("/")[0].strip()

    had_biz = bool(BIZ_RE.search(s))

    # 사업자/전화 제거
    s = BIZ_RE.sub("", s)
    s = TEL_RE.sub("", s)

    s = re.sub(r"\s+", " ", s).strip()

    # 사업자번호가 "같은 라인에 있었던 경우"에만 대표자명(끝 한글 2~4) 제거
    if had_biz:
        s = re.sub(r"\s*[가-힣]{2,4}\s*$", "", s).strip()

    return s

def _is_doc_header_merchant(cand: str) -> bool:
    """문서 헤더/전표 문구(매출전표, 고객용, OCR오독 마손전 등)면 가맹점 후보에서 제외."""
    if not cand or len(cand) < 2:
        return True
    c = cand.replace(" ", "")
    for kw in DOC_HEADER_MERCHANT:
        if kw in c:
            return True
    return False


def _merchant_from_gamyeongmyeong_line(txt: str, next_line_txt: Optional[str] = None) -> Optional[str]:
    """
    '가맹점명 xxxxx' 또는 '가맹점 명 xxxxx' 형태에서 xxxxx를 가맹점으로 추출.
    같은 줄에 값이 없으면 next_line_txt를 가맹점 후보로 사용(가맹점명만 있는 라인 + 다음 줄 케이스).
    """
    if not txt or "가맹점" not in txt:
        return None
    # "가맹점명" / "가맹점 명" / "가맹점명 :" 등 제거 후 뒷부분
    rest = GAMYEONGMYEONG_RE.split(txt, 1)
    if len(rest) < 2:
        return None
    cand = rest[-1].strip()
    # 같은 줄에 가맹점명만 있고 값이 없으면 다음 줄 사용
    if not cand and next_line_txt:
        cand = next_line_txt.strip()
    if not cand or len(cand) < 2:
        return None
    cand = clean_merchant(cand)
    cand = trim_merchant_after_jum(cand)
    if not cand or len(cand) < 2:
        return None
    if any(sw in cand for sw in STOPWORDS_MERCHANT):
        return None
    if _is_doc_header_merchant(cand):
        return None
    return cand.replace(" ", "")


def _merchant_from_biz_line(txt: str) -> Optional[str]:
    """
    '상호 ... 123-45-67890 ...' 형태면 사업자번호 앞부분을 상호로 채택
    """
    if not txt:
        return None
    m = BIZ_RE.search(txt)
    if not m:
        return None

    left = txt[:m.start()].strip()          # 사업자번호 왼쪽만
    left = clean_merchant(left)             # 전화/사업자 제거 등 공통 정리
    left = left.replace(" ", "")
    if left and len(left) >= 3:
        # 헤더 방지
        if any(sw in left for sw in STOPWORDS_MERCHANT):
            return None
        return left
    return None


def _extract_merchant(lines: List[Dict[str, Any]]) -> Optional[str]:
    # ✅ 0) 가맹점명 xxxxx 형태가 있으면 (위/아래 어디든) 그 값을 최우선 사용
    #     같은 줄에 값 없으면 다음 줄을 가맹점으로 사용 (가맹점명 / 이상구베이커리 분리된 경우)
    for i, ln in enumerate(lines):
        txt = (ln.get("line_text") or "").strip()
        next_txt = (lines[i + 1].get("line_text") or "").strip() if (i + 1) < len(lines) else None
        got = _merchant_from_gamyeongmyeong_line(txt, next_line_txt=next_txt)
        if got:
            return got

    top = lines[:10]

    # ✅ 1) 사업자번호 있는 라인 → 상호 확정
    for ln in top:
        txt = (ln.get("line_text") or "").strip()
        got = _merchant_from_biz_line(txt)
        if got:
            return got

    # ✅ 2) 상단에서 '...점/지점/점포/매장'으로 끝나는 후보를 찾기
    for ln in top:
        # tokens 우선으로 합치기
        parts = []
        for t in (ln.get("tokens") or []):
            tx = (t.get("text") or "").strip()
            if not tx:
                continue

            # 날짜 나오면 상단 종료 느낌이라 break(선택)
            if DATE_RE.search(tx):
                break

            # 상호에 방해되는 키(주소/영수증/주문 등)는 토큰 단위로 제외
            if any(sw in tx for sw in STOPWORDS_MERCHANT):
                continue

            parts.append(tx)

        cand = clean_merchant(" ".join(parts)).replace(" ", "")

        # ✅ "...점"으로 끝나면 확정 (단, 문서 헤더는 제외)
        if cand and not _is_doc_header_merchant(cand) and MERCHANT_END_RE.search(cand):
            return cand

    # ✅ 3) 못 찾으면: 상단에서 가장 그럴듯한 라인(기존 스코어링) fallback
    best = None
    best_score = -1.0

    for ln in top[:6]:
        txt = (ln.get("line_text") or "").strip()
        if not txt:
            continue
        if DATE_RE.search(txt):
            continue
        
        # 상호에 방해되는 키(주소/영수증/주문 등)는 제외
        if any(sw in txt for sw in STOPWORDS_MERCHANT):
            continue

        cand = clean_merchant(txt)
        
        if any(sw in cand for sw in STOPWORDS_MERCHANT):
            continue
        if _is_doc_header_merchant(cand):
            continue

        # 주소 느낌 강하면 패널티(“경기/서울/시/구/동/로/길” 같은 주소 토큰)
        addr_penalty = 0
        if any(x in cand for x in ["시", "구", "동", "로", "길", "번길", "도로", "우편", "경기", "서울"]):
            addr_penalty = 5

        hangul = sum("가" <= ch <= "힣" for ch in cand)
        digits = sum(ch.isdigit() for ch in cand)

        score = len(cand) + hangul * 0.5 - digits * 2.0 - addr_penalty

        if score > best_score and len(cand) >= 3:
            best_score = score
            best = cand

    return best

def extract_receipt_fields(lines: List[Dict[str, Any]]) -> ReceiptResult:
    # date/datetime
    date, dt = _extract_date_time(lines)

    # items
    items = _extract_items(lines)

    # “합계 금액 56,000” 같은 영수증은 1순위 키워드 룰에서 잡힘
    # 여기서는 fallback만 제공: 결제수단 기반
    total, total_conf = _best_total_from_keywords(lines)
    if total is None:
        total, total_conf = _best_total_from_payment_context(lines)


    # merchant (추출 후 '~점' 뒤 괄호/숫자 제거)
    merchant = _extract_merchant(lines)
    if merchant:
        merchant = trim_merchant_after_jum(merchant) or merchant

    return ReceiptResult(
        merchant=merchant,
        date=date,
        datetime=dt,
        items=items,
        total=total,
        total_confidence=total_conf,
    )


