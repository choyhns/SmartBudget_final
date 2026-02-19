"""
소비패턴 Q&A Intent 분류 - LangChain fallback.
룰에서 분류가 안 될 때 Spring이 호출하는 /api/llm/classify-intent 에서 사용.
ChatGoogleGenerativeAI + structured output으로 Intent/슬롯 JSON 반환.
"""
import os
import logging
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)

# LangChain 사용 가능 여부
try:
    from langchain_google_genai import ChatGoogleGenerativeAI
    LANGCHAIN_AVAILABLE = True
except ImportError:
    LANGCHAIN_AVAILABLE = False
    logger.warning("langchain-google-genai not installed; LangChain intent classifier disabled")


# Intent 분류 결과 스키마 (Spring/기존 API와 호환되는 camelCase 키로 반환)
class IntentClassificationSchema(BaseModel):
    """LLM이 채울 Intent + 슬롯. 반환 시 camelCase dict로 변환."""
    intent: str = Field(
        default="SUMMARY",
        description="SUMMARY|CAUSE_ANALYSIS|COMPARISON|BUDGET_STATUS|PATTERN_DEEPDIVE|ADVICE_ACTION|DEFINITION_HELP|DATA_FIX"
    )
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)
    yearMonth: Optional[str] = Field(default=None, description="YYYYMM or null")
    baselineYearMonth: Optional[str] = Field(default=None, description="비교 기준월 YYYYMM or null")
    categoryId: Optional[int] = Field(default=None)
    dimension: Optional[str] = Field(default=None, description="timeband|dow|merchant|keyword or null")
    keywords: Optional[List[str]] = Field(default_factory=list)
    needsDb: bool = Field(default=False)
    needsRag: bool = Field(default=False)
    followUpQuestion: Optional[str] = Field(default=None)


SYSTEM_PROMPT = """당신은 소비/가계부 앱의 사용자 질문을 분류하는 모듈입니다.
아래 [질문]에 대해 Intent와 슬롯을 분류하세요.

Intent 의미:
- SUMMARY: 월 요약(총 지출/수입/TOP 카테고리)
- CAUSE_ANALYSIS: 원인 분석(왜 늘었는지, 급증 원인)
- COMPARISON: 기간 비교(전월 대비, 지난달 vs 이번달)
- BUDGET_STATUS: 예산 상태(한도, 초과, 잔여)
- PATTERN_DEEPDIVE: 패턴 심화(야식, 시간대, 요일, 가맹점, 키워드)
- ADVICE_ACTION: 조언/절약 팁(어떻게 줄이지)
- DEFINITION_HELP: 정의/도움말(~이 뭐야)
- DATA_FIX: 데이터 정정 요청(틀렸어, 수정, 누락)

needsDb: DB 분석 쿼리가 필요하면 true (SUMMARY, CAUSE_ANALYSIS, COMPARISON, BUDGET_STATUS, PATTERN_DEEPDIVE)
needsRag: RAG/리포트 맥락이 필요하면 true (ADVICE_ACTION, DEFINITION_HELP)
연월을 질문에서 추출할 수 없으면 yearMonth, baselineYearMonth는 null로 두세요.
카테고리 ID를 알 수 없으면 categoryId=null로 두고, followUpQuestion에 "어떤 카테고리가 궁금하신가요?" 등 되묻기를 넣으세요."""


def _to_response_dict(obj: IntentClassificationSchema) -> Dict[str, Any]:
    """Spring/main.py에서 기대하는 camelCase 키 dict."""
    return {
        "intent": obj.intent,
        "confidence": obj.confidence,
        "yearMonth": obj.yearMonth,
        "baselineYearMonth": obj.baselineYearMonth,
        "categoryId": obj.categoryId,
        "dimension": obj.dimension,
        "keywords": obj.keywords or [],
        "needsDb": obj.needsDb,
        "needsRag": obj.needsRag,
        "followUpQuestion": obj.followUpQuestion,
    }


def classify_intent_with_langchain(question: str) -> Optional[Dict[str, Any]]:
    """
    LangChain + Gemini structured output으로 Intent 분류.
    반환 dict는 기존 llm_service.classify_intent()와 동일 형식(camelCase).
    """
    if not LANGCHAIN_AVAILABLE or not question or not question.strip():
        return None

    api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
    if not api_key:
        logger.warning("GEMINI_API_KEY not set; LangChain classifier skip")
        return None

    model_name = os.getenv("GEMINI_LLM_MODEL", "gemini-2.5-flash")
    try:
        llm = ChatGoogleGenerativeAI(
            model=model_name,
            temperature=0.1,
            api_key=api_key,
        )
        structured_llm = llm.with_structured_output(
            IntentClassificationSchema,
            method="json_schema",
        )
        # HumanMessage 형태로 질문 전달
        from langchain_core.messages import HumanMessage
        messages = [
            HumanMessage(content=SYSTEM_PROMPT + "\n\n[질문]\n" + question.strip()),
        ]
        result = structured_llm.invoke(messages)
        if result is None:
            return None
        if isinstance(result, dict):
            # LangChain이 snake_case로 줄 수 있음 → camelCase로 통일
            if "year_month" in result and "yearMonth" not in result:
                return {
                    "intent": result.get("intent", "SUMMARY"),
                    "confidence": float(result.get("confidence", 0.5)),
                    "yearMonth": result.get("year_month"),
                    "baselineYearMonth": result.get("baseline_year_month"),
                    "categoryId": result.get("category_id"),
                    "dimension": result.get("dimension"),
                    "keywords": result.get("keywords") or [],
                    "needsDb": bool(result.get("needs_db", False)),
                    "needsRag": bool(result.get("needs_rag", False)),
                    "followUpQuestion": result.get("follow_up_question"),
                }
            return result
        return _to_response_dict(result)
    except Exception as e:
        logger.exception("LangChain classify_intent failed: %s", e)
        return None
