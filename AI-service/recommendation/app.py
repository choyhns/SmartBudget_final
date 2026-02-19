"""
SmartBudget Recommendation AI Service (Python)
카드 추천 및 Q&A를 위한 AI 서비스
"""
import os
import json
import logging
import urllib.request
import urllib.parse
import urllib.error
from typing import List, Dict, Any, Optional
from flask import Flask, request, jsonify
from flask_cors import CORS
from google import genai
from google.genai import types
from dotenv import load_dotenv

log = logging.getLogger(__name__)

# 환경 변수 로드: AI-service 루트의 .env 파일 우선 로드
from pathlib import Path
_root_dir = Path(__file__).resolve().parents[1]  # recommendation/app.py -> AI-service
load_dotenv(_root_dir / ".env")  # AI-service/.env
# 하위 호환성: 기존 경로도 확인
_this_dir = Path(__file__).resolve().parent
load_dotenv(_this_dir / ".env")  # recommendation/.env (있으면)
load_dotenv(_root_dir / ".." / "backend" / "smartbudget" / ".env")  # backend .env (있으면)

app = Flask(__name__)
CORS(app)

# Gemini API 설정
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

# Google Custom Search (직접 질문 시 검색 대안용, 선택)
# 403 등으로 검색을 쓰지 않으려면 .env에 CUSTOM_SEARCH_ENABLED=false 로 끄면 됨
_raw = os.getenv("CUSTOM_SEARCH_ENABLED", "true").lower()
CUSTOM_SEARCH_ENABLED = _raw in ("true", "1", "yes")
CUSTOM_SEARCH_API_KEY = os.getenv("GOOGLE_CUSTOM_SEARCH_API_KEY") or os.getenv("GOOGLE_CUSTOMSEARCH_API_KEY")
CUSTOM_SEARCH_CX = os.getenv("GOOGLE_CUSTOM_SEARCH_CX") or os.getenv("GOOGLE_CUSTOMSEARCH_CX")
CUSTOM_SEARCH_URL = "https://customsearch.googleapis.com/customsearch/v1"

if GEMINI_API_KEY:
    client = genai.Client(api_key=GEMINI_API_KEY)
else:
    print("경고: GEMINI_API_KEY가 설정되지 않았습니다.")
    client = None


def _is_search_configured() -> bool:
    """Google Custom Search API 사용 가능 여부 (꺼져 있으면 False)"""
    return bool(CUSTOM_SEARCH_ENABLED and CUSTOM_SEARCH_API_KEY and CUSTOM_SEARCH_CX)


def _is_asking_for_more_cards(question: str) -> bool:
    """다른/추가 카드 추천 요청인지 여부."""
    if not question or not question.strip():
        return False
    q = question.strip()
    keywords = ("다른 카드", "더 추천", "추가 추천", "다른 추천", "또 추천", "또 다른 카드", "비슷한 카드", "추천해줄 만한 카드", "추천할 카드")
    return any(kw in q for kw in keywords)


def _search_query_for_more_cards(question: str, spending_pattern: Optional[Dict[str, Any]]) -> str:
    """
    '다른 카드 추천' 요청일 때 검색에 쓸 쿼리 생성.
    지출 1위 카테고리가 있으면 '신한카드 {카테고리} 혜택 카드' 형태로 반환.
    """
    top_category = None
    if spending_pattern and isinstance(spending_pattern.get("topCategory"), str):
        top = spending_pattern.get("topCategory", "").strip()
        if top and top != "없음":
            top_category = top
    if top_category:
        return f"신한카드 {top_category} 혜택 카드 추천"
    # 카테고리 없으면 질문에서 키워드 추출 또는 일반 쿼리
    return "신한카드 인기 혜택 카드 추천"


def _fetch_search_snippets(query: str, num: int = 5) -> Optional[str]:
    """
    Google Custom Search API로 검색 후 스니펫 텍스트 반환.
    실패 시 None, 성공 시 'title\\n\\nsnippet'들을 \\n\\n으로 이어붙인 문자열.
    """
    if not _is_search_configured() or not query or not query.strip():
        return None
    try:
        encoded = urllib.parse.quote(query.strip(), encoding="utf-8")
        url = f"{CUSTOM_SEARCH_URL}?key={CUSTOM_SEARCH_API_KEY}&cx={CUSTOM_SEARCH_CX}&q={encoded}&num={num}"
        req = urllib.request.Request(url, headers={"User-Agent": "SmartBudget/1.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        items = data.get("items") or []
        snippets = []
        for item in items:
            title = item.get("title") or ""
            snippet = item.get("snippet") or ""
            if not title and not snippet:
                continue
            snippets.append(title + ("\n" + snippet if snippet else ""))
        if not snippets:
            return None
        return "\n\n".join(snippets)
    except urllib.error.HTTPError as e:
        body = ""
        try:
            body = e.read().decode("utf-8", errors="replace")[:500]
        except Exception:
            pass
        if e.code == 403:
            log.warning(
                "Custom Search 403 Forbidden. Google 응답: %s. 쿼리: %s",
                body or str(e),
                query[:50],
            )
        else:
            log.warning("Custom Search HTTP %s: %s - %s", e.code, query[:50], body or e)
        return None
    except Exception as e:
        log.warning("Custom Search 실패: %s - %s", query[:50], e)
        return None


def remove_prompt_instructions(text: str) -> str:
    """프롬프트 지시사항 제거"""
    if not text:
        return text
    
    # "1. 먼저 3줄로 주요 근거를 요약해주세요" 같은 패턴 제거
    lines = text.split('\n')
    filtered = []
    skip_next = False
    
    for line in lines:
        if any(keyword in line for keyword in [
            "먼저 3줄로", "각 줄은 한 문장으로", "요약해주세요",
            "반드시 다음을 포함", "응답 형식"
        ]):
            skip_next = True
            continue
        if skip_next and line.strip() == "":
            skip_next = False
            continue
        skip_next = False
        filtered.append(line)
    
    return '\n'.join(filtered).strip()


@app.route('/health', methods=['GET'])
def health():
    """헬스 체크"""
    return jsonify({"status": "ok", "model": GEMINI_MODEL})


@app.route('/generate-card-suitable-reason', methods=['POST'])
def generate_card_suitable_reason():
    """
    단일 카드에 대한 적합 이유 생성
    Request body:
    {
        "cardName": "카드명",
        "cardCompany": "카드사",
        "cardBenefitsJson": "혜택 JSON 문자열",
        "spendingPattern": {
            "yearMonth": "202401",
            "categoryExpenses": {"식비": 100000, ...},
            "topCategory": "식비",
            "totalExpense": 500000
        },
        "monthlyReportSummary": "월별 분석 요약"
    }
    """
    if not client:
        return jsonify({"error": "Gemini API가 설정되지 않았습니다."}), 500
    
    try:
        data = request.json
        card_name = data.get("cardName", "")
        card_company = data.get("cardCompany", "")
        card_benefits = data.get("cardBenefitsJson", "없음")
        spending_pattern = data.get("spendingPattern", {})
        monthly_summary = data.get("monthlyReportSummary", "")
        
        # spendingPattern 압축 (상위 5개 카테고리만)
        compact_pattern = {
            "yearMonth": spending_pattern.get("yearMonth", ""),
            "topCategory": spending_pattern.get("topCategory", "없음"),
            "totalExpense": spending_pattern.get("totalExpense", 0)
        }
        
        category_expenses = spending_pattern.get("categoryExpenses", {})
        if category_expenses:
            top5 = sorted(
                category_expenses.items(),
                key=lambda x: float(x[1]) if isinstance(x[1], (int, float)) else 0,
                reverse=True
            )[:5]
            compact_pattern["categoryExpenses"] = dict(top5)
        
        spending_str = json.dumps(compact_pattern, ensure_ascii=False, indent=2)
        
        prompt = f"""당신은 카드 추천 AI 어시스턴트입니다. 사용자 소비 데이터를 분석해 왜 이 카드를 추천했는지 **간단하고 명확하게** 설명해주세요.

[카드 정보]
- 카드명: {card_name} ({card_company})
- 혜택 요약: {card_benefits}

[사용자 소비 패턴]
{spending_str}

[월별 분석 요약(있으면)]
{monthly_summary if monthly_summary else "없음"}

[작성 지침 - 반드시 준수]
- **정확히 3줄**로 요약해주세요.
- 1줄: 사용자의 주요 지출 카테고리를 언급하세요. (예: "회원님의 주요 지출은 [카테고리명]입니다.")
- 2줄: 이 카드의 관련 혜택을 구체적으로 설명하세요. (예: "이 카드는 [혜택 요약에서 언급된 키워드, 예: 배달/주유/교통] 혜택이 있어 해당 지출에 도움이 됩니다.")
- 3줄: 실질적인 도움이나 추가 혜택을 언급하세요. (예: "월간 지출 절감과 함께 다양한 혜택까지 받을 수 있어요.")
- 식비·배달/외식·주유·통신 등 실제 카테고리명을 그대로 사용하세요.
- "매칭해 추천합니다", "적합한 카드입니다" 같은 막연한 문구는 사용하지 마세요.
- 이모지 사용 가능하지만 간결함을 우선하세요."""

        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=prompt
        )
        reason = response.text.strip()
        
        # 프롬프트 지시사항 제거
        reason = remove_prompt_instructions(reason)
        
        return jsonify({"reason": reason})
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/generate-card-suitable-reasons-batch', methods=['POST'])
def generate_card_suitable_reasons_batch():
    """
    여러 카드에 대한 적합 이유를 한 번에 생성 (배치)
    Request body:
    {
        "cardsInfo": [
            {
                "name": "카드명",
                "company": "카드사",
                "benefitsJson": "혜택 JSON"
            },
            ...
        ],
        "spendingPattern": {...},
        "monthlyReportSummary": "..."
    }
    """
    if not client:
        return jsonify({"error": "Gemini API가 설정되지 않았습니다."}), 500
    
    try:
        data = request.json
        cards_info = data.get("cardsInfo", [])
        spending_pattern = data.get("spendingPattern", {})
        monthly_summary = data.get("monthlyReportSummary", "")
        
        if not cards_info:
            return jsonify({"reasons": []})
        
        # spendingPattern 압축
        compact_pattern = {
            "yearMonth": spending_pattern.get("yearMonth", ""),
            "topCategory": spending_pattern.get("topCategory", "없음"),
            "totalExpense": spending_pattern.get("totalExpense", 0)
        }
        
        category_expenses = spending_pattern.get("categoryExpenses", {})
        if category_expenses:
            top5 = sorted(
                category_expenses.items(),
                key=lambda x: float(x[1]) if isinstance(x[1], (int, float)) else 0,
                reverse=True
            )[:5]
            compact_pattern["categoryExpenses"] = dict(top5)
        
        spending_str = json.dumps(compact_pattern, ensure_ascii=False, indent=2)
        
        # 카드 정보 문자열 구성
        cards_info_str = ""
        for i, card in enumerate(cards_info, 1):
            cards_info_str += f"""[카드 {i}]
- 카드명: {card.get('name', '')} ({card.get('company', '')})
- 혜택 요약: {card.get('benefitsJson', '없음')}

"""
        
        prompt = f"""당신은 카드 추천 AI 어시스턴트입니다. 각 카드마다 **서로 다른 구체적인 추천 이유**를 작성해주세요.

[카드 정보 목록]
{cards_info_str}

[사용자 소비 패턴]
{spending_str}

[월별 분석 요약(있으면)]
{monthly_summary if monthly_summary else "없음"}

[작성 형식 - 반드시 준수]
각 카드마다 "[카드 N]" 라인으로 시작하고, 그 다음 **정확히 3줄**로 요약해주세요.

**중요**: 각 카드의 이유는 **서로 달라야 합니다**. 같은 문장을 반복하지 마세요.

예시 (각 카드마다 다르게):
[카드 1]
회원님의 주요 지출은 자동차 관련 항목입니다.
이 카드는 주유 할인과 복지 혜택을 제공해 운전자에게 실질적인 도움이 됩니다.
월간 주유비 절감과 함께 다양한 복지 혜택까지 받을 수 있어요.

[카드 2]
자동차 관련 지출이 많으시네요.
주유소에서의 캐시백과 각종 복지 혜택으로 월 지출을 줄일 수 있습니다.
특히 주유비와 정비비에서 실질적인 혜택을 받을 수 있어요.

[카드 3]
운전자분을 위한 특화 카드입니다.
주유비 절감과 함께 다양한 복지 혜택까지 받을 수 있습니다.
자동차 관련 지출이 많은 회원님께 최적화된 혜택을 제공합니다.

- 각 카드는 "[카드 N]" 라인으로 시작하고, 그 다음 정확히 3줄로 작성하세요.
- 카드마다 문장 구조와 표현을 다르게 하세요 (예: "주요 지출은...", "지출이 많으시네요", "특화 카드입니다" 등).
- 실제 카테고리명(자동차, 식비, 주유 등)과 혜택 키워드를 구체적으로 언급하세요.
- "매칭해 추천합니다", "적합한 카드입니다" 같은 막연한 문구는 사용하지 마세요."""

        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=prompt
        )
        full_response = response.text.strip()
        log.info(f"배치 AI 원본 응답 길이: {len(full_response)}자")
        
        # 프롬프트 지시사항 제거
        full_response = remove_prompt_instructions(full_response)
        log.info(f"지시사항 제거 후 응답 길이: {len(full_response)}자")
        
        # 응답을 카드별로 분리
        reasons = parse_batch_reasons(full_response, len(cards_info))
        log.info(f"파싱된 이유 개수: {len(reasons)} (예상: {len(cards_info)})")
        for i, r in enumerate(reasons, 1):
            if r and len(r) > 0:
                log.info(f"카드 {i} 이유 길이: {len(r)}자, 내용: {r[:100]}...")
            else:
                log.warn(f"카드 {i} 이유가 비어있음!")
        
        # 빈 이유가 있으면 경고
        empty_count = sum(1 for r in reasons if not r or len(r.strip()) < 10)
        if empty_count > 0:
            log.warn(f"파싱 실패: {empty_count}개 카드의 이유가 비어있음. 원본 응답: {full_response[:500]}")
        
        return jsonify({"reasons": reasons})
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def parse_batch_reasons(full_response: str, expected_count: int) -> List[str]:
    """배치 응답을 파싱하여 각 카드별 이유 리스트로 변환"""
    reasons = []
    if not full_response:
        log.warn("full_response가 비어있음")
        return [""] * expected_count
    
    import re
    # "[카드 N]" 패턴으로 분리 (대소문자, 공백 유연하게)
    # 예: "[카드 1]", "[카드1]", "[카드  1]" 모두 매칭
    parts = re.split(r'\[카드\s*\d+\]', full_response, flags=re.IGNORECASE)
    log.info(f"파싱: '[카드 N]' 패턴으로 분리 결과 {len(parts)}개 부분")
    
    if len(parts) < 2:
        # "[카드 N]" 패턴이 없으면 줄바꿈으로 분리 시도
        log.warn("'[카드 N]' 패턴이 없어 줄바꿈으로 분리 시도")
        lines = full_response.split('\n')
        current_reason = []
        for line in lines:
            line = line.strip()
            if not line:
                if current_reason:
                    # 줄바꿈 유지하여 3줄 형식으로 저장
                    reasons.append('\n'.join(current_reason))
                    current_reason = []
            else:
                current_reason.append(line)
        if current_reason:
            reasons.append('\n'.join(current_reason))
    else:
        # 첫 번째 부분은 보통 프롬프트 지시사항이므로 제외하고, 나머지 파싱
        for i in range(1, min(len(parts), expected_count + 1)):
            reason = parts[i].strip() if i < len(parts) else ""
            # 연속된 빈 줄을 하나의 줄바꿈으로 정리 (3줄 형식 유지)
            reason = re.sub(r'\n\s*\n+', '\n', reason).strip()
            # 각 줄의 앞뒤 공백만 제거 (줄바꿈은 유지)
            reason = '\n'.join(line.strip() for line in reason.split('\n') if line.strip())
            # 너무 짧거나 의미 없는 경우 스킵
            if len(reason) < 10:
                log.warn(f"카드 {i} 이유가 너무 짧음: '{reason}'")
                reason = ""
            reasons.append(reason)
    
    # 부족한 만큼 기본 메시지 추가 (템플릿)
    while len(reasons) < expected_count:
        reasons.append("이 카드의 혜택과 사용자의 상위 지출 카테고리를 매칭해 추천합니다.")
    
    return reasons[:expected_count]


@app.route('/explain-why-recommended', methods=['POST'])
def explain_why_recommended():
    """
    '왜 이 카드들이 추천됐는지' 질문에 대한 AI 상세 설명 (카드별로 구체적 근거)
    Request body:
    {
        "recommendedCards": [
            { "name": "카드명", "company": "카드사", "benefitsJson": "혜택 JSON 또는 요약" }
        ],
        "spendingPattern": { "yearMonth": "", "topCategory": "", "categoryExpenses": {}, "totalExpense": 0 },
        "monthlyReportSummary": ""
    }
    """
    if not client:
        return jsonify({"error": "Gemini API가 설정되지 않았습니다."}), 500

    try:
        data = request.json
        cards = data.get("recommendedCards", [])
        spending_pattern = data.get("spendingPattern", {})
        monthly_summary = data.get("monthlyReportSummary", "")

        if not cards:
            return jsonify({"answer": "추천된 카드가 없어 근거를 설명할 수 없습니다."})

        year_month = spending_pattern.get("yearMonth", "")
        top_category = spending_pattern.get("topCategory", "없음")
        category_expenses = spending_pattern.get("categoryExpenses", {})
        total_expense = spending_pattern.get("totalExpense", 0)
        top5 = []
        if category_expenses:
            top5 = sorted(
                category_expenses.items(),
                key=lambda x: float(x[1]) if isinstance(x[1], (int, float)) else 0,
                reverse=True
            )[:5]
        spending_str = json.dumps({
            "yearMonth": year_month,
            "topCategory": top_category,
            "totalExpense": total_expense,
            "categoryExpenses": dict(top5) if top5 else {}
        }, ensure_ascii=False, indent=2)

        cards_desc = []
        for i, c in enumerate(cards[:5], 1):
            name = c.get("name", "")
            company = c.get("company", "")
            benefits = (c.get("benefitsJson") or "")[:500]
            cards_desc.append(f"[{i}] {name} ({company})\n   혜택: {benefits or '없음'}")

        prompt = f"""당신은 카드 추천 AI 어시스턴트입니다. 사용자가 "왜 이 카드/상품이 추천됐는지 근거를 설명해줘"라고 질문했습니다.

[기준 월] {year_month}
[주요 지출 카테고리] {top_category}
[소비 패턴]
{spending_str}

[월별 분석 요약(있으면)]
{monthly_summary or "없음"}

[추천된 카드 목록]
{chr(10).join(cards_desc)}

[작성 지침 - 반드시 준수]
- **카드마다 서로 다른** 구체적 근거를 작성하세요. 모든 카드에 같은 문장을 반복하지 마세요.
- 각 카드에 대해: (1) 어떤 지출 카테고리와 맞는지, (2) 그 카드만의 혜택(할인/캐시백/포인트 등)을 구체적으로 언급하세요.
- 형식: 먼저 "다음은 {year_month} 기준 상위 지출과 매칭된 추천 근거입니다." 한 문장, 그 다음 카드별로 "• [카드명]: ..." 형태로 2~3문장씩 설명.
- 식비·배달/외식·주유·통신·자동차 등 실제 카테고리명과 혜택 내용을 그대로 사용하세요.
- 전체 400~600자 정도로 읽기 쉽게 작성하세요. 이모지 사용 가능합니다."""

        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=prompt
        )
        answer = response.text.strip()
        answer = remove_prompt_instructions(answer)
        return jsonify({"answer": answer})

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/answer-custom-question', methods=['POST'])
def answer_custom_question():
    """
    사용자 직접 입력 질문에 답변.
    Google Custom Search가 설정되어 있으면 질문으로 검색한 스니펫을 컨텍스트에 넣어 최신 정보 반영.
    Request body:
    {
        "question": "질문",
        "card": {...},
        "spendingPattern": {...},
        "monthlyReportSummary": "..."
    }
    """
    if not client:
        return jsonify({"error": "Gemini API가 설정되지 않았습니다."}), 500
    
    try:
        data = request.json
        question = data.get("question", "").strip()
        card = data.get("card")
        spending_pattern = data.get("spendingPattern", {})
        monthly_summary = data.get("monthlyReportSummary", "")
        
        if not question:
            return jsonify({"answer": "질문을 입력해주세요."})

        # '다른 카드 추천' 요청이면 지출 카테고리로 검색 쿼리 생성 → LLM이 검색 결과에서 카드 추천 문장 생성
        asking_more_cards = _is_asking_for_more_cards(question)
        if _is_search_configured():
            if asking_more_cards:
                search_query = _search_query_for_more_cards(question, spending_pattern)
                search_snippets = _fetch_search_snippets(search_query, num=6)
            else:
                search_snippets = _fetch_search_snippets(question, num=5)
        else:
            search_snippets = None

        # 컨텍스트 구성
        context_parts = []
        if search_snippets:
            context_parts.append(f"[검색 결과]\n{search_snippets}")
        
        if card:
            context_parts.append(
                f"[선택 카드: {card.get('name', '')} ({card.get('company', '')}). "
                f"혜택: {card.get('benefitsJson', '')[:150]}...]"
            )
        
        if spending_pattern:
            context_parts.append(f"[소비 패턴]\n{json.dumps(spending_pattern, ensure_ascii=False, indent=2)}")
        
        if monthly_summary:
            context_parts.append(f"[월별 분석 요약]\n{monthly_summary}")
        
        context = "\n\n".join(context_parts)

        # '다른 카드 추천' + 검색 결과 있음 → 검색 결과에서 카드명·혜택 추려 추천 문장 생성
        if asking_more_cards and search_snippets:
            top_category = (spending_pattern or {}).get("topCategory") or "지출"
            prompt = f"""당신은 카드 추천 AI 어시스턴트입니다. 사용자가 "이 카드 말고 더 추천할 카드 없어?"라고 물었습니다.
아래 [검색 결과]는 "{top_category}" 등과 관련된 카드 검색 결과입니다. 이 결과만 사용해 답변하세요.

{context}

[답변 지침]
- [검색 결과]에서 카드명과 혜택을 추려 2~3개를 "• 카드명: 혜택 요약" 형태로 추천해주세요.
- 검색 결과에 나온 카드·혜택만 언급하고, 없으면 "검색 결과에 따르면 …" 식으로 1~2문장으로 정리하세요.
- 마지막에 "앱 상단의 **추천 생성** 버튼을 누르시면 지출에 맞는 카드 목록을 받을 수 있어요." 안내를 한 줄 넣어주세요.
- 4~6문장, 250자 이내. 친근한 톤, 이모지 가능.

[답변]"""
        elif search_snippets:
            prompt = f"""당신은 사용자의 소비 리포트를 분석해 알려주는 AI 어시스턴트입니다.
아래 [검색 결과]와 [리포트 맥락]을 참고하여 사용자 질문에 답변해주세요.

{context}

[사용자 질문]
{question}

[답변 지침]
- 검색 결과와 리포트 맥락을 종합하여 답변하세요. 검색 결과에 없는 내용은 추측하지 마세요.
- 답변은 2~4문장, 150자 이내로 핵심만 전달하세요.
- 친근하고 간결한 톤으로 작성하세요. 이모지 사용 가능합니다.

[답변]"""
        else:
            prompt = f"""당신은 사용자의 소비 리포트를 분석해 알려주는 AI 어시스턴트입니다.
아래 [리포트 맥락]을 바탕으로 사용자의 [질문]에 친근하고 간결하게 답변해주세요.

{context}

[사용자 질문]
{question}

[답변 지침]
- 답변은 2~4문장, 150자 이내로 핵심만 전달하세요.
- 친근하고 간결한 톤으로 작성하세요. 이모지 사용 가능합니다.

[답변]"""

        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=prompt
        )
        answer = response.text.strip()
        return jsonify({"answer": answer})
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/answer-card-monthly-requirement', methods=['POST'])
def answer_card_monthly_requirement():
    """
    '전월 실적 채워야하는 카드인가?' 질문에 대한 AI 답변 생성
    Request body:
    {
        "cardName": "카드명",
        "cardCompany": "카드사",
        "benefitsJson": "혜택 JSON 또는 요약"
    }
    """
    if not client:
        return jsonify({"error": "Gemini API가 설정되지 않았습니다."}), 500

    try:
        data = request.json
        card_name = data.get("cardName", "")
        card_company = data.get("cardCompany", "")
        benefits = (data.get("benefitsJson") or "")[:400]

        if not card_name:
            return jsonify({"answer": "카드 정보가 없어 전월 실적 조건을 안내할 수 없습니다."})

        prompt = f"""당신은 카드 상담 AI 어시스턴트입니다. 사용자가 「{card_name}」({card_company})에 대해 "전월 실적 채워야 하는 카드인가?"라고 물어봤어요.

[카드 정보]
- 카드명: {card_name} ({card_company})
- 혜택 요약: {benefits or '없음'}

[작성 지침]
- 이 카드가 전월 실적(월 이용금액) 조건이 있는지, 체크카드/신용카드 특성상 보통 어떻게 되는지 2~4문장으로 설명하세요.
- 전월 실적이 있으면 혜택이 적용되는 카드인지, 없어도 되는 카드인지 구분해서 답해주세요.
- 정확한 조건은 신한카드 공식 홈페이지·고객센터·앱에서 확인하라고 한 문장 안내하세요.
- 200자 이내, 친근한 말투. 이모지 사용 가능. "【카드명】" 형태로 카드명 한 번 언급."""

        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=prompt
        )
        answer = response.text.strip()
        answer = remove_prompt_instructions(answer)
        return jsonify({"answer": answer})

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/get-card-benefits-detail', methods=['POST'])
def get_card_benefits_detail():
    """
    카드 자세한 혜택: 검색 스니펫이 있으면 스니펫 기반, 없으면 benefitsJson·카드명 기반으로 AI가 정리
    Request body:
    {
        "cardName": "카드명",
        "cardCompany": "카드사",
        "benefitsJson": "혜택 JSON 또는 요약",
        "snippetsText": "검색 결과 스니펫 (선택)"
    }
    """
    if not client:
        return jsonify({"error": "Gemini API가 설정되지 않았습니다."}), 500

    try:
        data = request.json
        card_name = data.get("cardName", "")
        card_company = data.get("cardCompany", "")
        benefits = (data.get("benefitsJson") or "")[:600]
        snippets_text = (data.get("snippetsText") or "").strip()

        if not card_name:
            return jsonify({"answer": "카드 정보가 없어 혜택을 안내할 수 없습니다."})

        if snippets_text:
            prompt = f"""다음은 「{card_name}」({card_company})에 대한 웹 검색 결과입니다.

[검색 결과]
{snippets_text[:3000]}

위 내용에서 이 카드의 혜택만 추려서 항목별로 정리해 주세요.
- 요약하지 말고, 혜택 항목을 빠짐없이 나열하세요.
- 각 항목은 한 줄로, 예: "• 교통 15% 캐시백", "• 통신비 5,000원 할인" 형식으로 작성하세요.
- 검색 결과에 없는 내용은 만들지 마세요.
- 마지막에 "※ 정확한 혜택·조건은 신한카드 공식 채널에서 확인해주세요." 한 줄만 추가하세요."""
        else:
            prompt = f"""당신은 카드 상담 AI입니다. 「{card_name}」({card_company})의 자세한 혜택을 정리해 주세요.

[보유 정보]
{benefits or '없음'}

[작성 지침]
- 위 JSON/요약을 바탕으로 혜택을 항목별로 정리하세요. 정보가 부족하면 해당 카드 유형에서 일반적으로 제공되는 혜택을 보완해도 됩니다.
- 각 항목은 "• 카테고리 혜택(할인/캐시백/포인트 등)" 형태로 한 줄씩 나열하세요.
- 5~15개 항목으로 읽기 쉽게 작성하세요.
- 마지막에 "※ 정확한 혜택·조건은 신한카드 공식 채널에서 확인해주세요." 한 줄만 추가하세요."""

        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=prompt
        )
        answer = response.text.strip()
        answer = remove_prompt_instructions(answer)
        return jsonify({"answer": answer})

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/extract-card-benefits', methods=['POST'])
def extract_card_benefits():
    """
    검색 스니펫에서 카드 혜택만 추려서 정리
    Request body:
    {
        "cardName": "카드명",
        "snippetsText": "검색 결과 스니펫 텍스트"
    }
    """
    if not client:
        return jsonify({"error": "Gemini API가 설정되지 않았습니다."}), 500
    
    try:
        data = request.json
        card_name = data.get("cardName", "")
        snippets_text = data.get("snippetsText", "")
        
        if not snippets_text:
            return jsonify({"benefits": None})
        
        prompt = f"""다음은 카드 「{card_name}」에 대한 웹 검색 결과 스니펫입니다.

[검색 결과]
{snippets_text}

위 내용에서 이 카드의 혜택만 추려서 항목별로 정리해 주세요.
- 요약하지 말고, 혜택 항목을 빠짐없이 나열하세요.
- 각 항목은 한 줄로, 예: "• 교통 15% 캐시백", "• 통신비 5,000원 할인" 형식으로 작성하세요.
- 검색 결과에 없는 내용은 만들지 마세요.
- 마지막에 "※ 정확한 혜택·조건은 신한카드 공식 채널에서 확인해주세요." 한 줄만 추가하세요."""

        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=prompt
        )
        benefits = response.text.strip()
        
        return jsonify({"benefits": benefits})
    
    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    port = int(os.getenv("PORT", 5001))
    debug = os.getenv("DEBUG", "false").lower() == "true"
    app.run(host='0.0.0.0', port=port, debug=debug)
