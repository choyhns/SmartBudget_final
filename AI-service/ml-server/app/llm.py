"""
Gemini LLM API를 사용한 텍스트 생성 및 프롬프트 빌드
"""
import os
import logging
from typing import List, Dict, Any, Optional, Tuple
from pathlib import Path
from datetime import datetime
from calendar import monthrange
import google.generativeai as genai
from dotenv import load_dotenv

# AI-service 루트의 .env 파일 로드
_root_dir = Path(__file__).resolve().parents[3]  # ml-server/app/llm.py -> AI-service
load_dotenv(_root_dir / ".env")
load_dotenv()  # 하위 호환성: 현재 디렉토리 .env도 확인
logger = logging.getLogger(__name__)


def _is_card_related_question(question: str) -> bool:
    """
    질문이 카드/혜택/실적 등 카드 상담 맥락인지 판별.
    - '카드', '연회비', '실적', '혜택', '포인트', '적립', '캐시백', '할인', '한도', '결제일' 등의 키워드 포함 여부만 본다.
    - 여러 줄이 들어오는 경우 마지막 줄만 기준으로 판단한다.
    """
    if not question:
        return False
    # 여러 줄로 들어올 수 있으므로, 사용자가 마지막에 입력한 줄만 사용
    lines = [ln.strip() for ln in str(question).splitlines() if ln.strip()]
    q = lines[-1] if lines else ""
    if not q:
        return False
    keywords = (
        "카드",
        "연회비",
        "실적",
        "혜택",
        "포인트",
        "적립",
        "캐시백",
        "할인",
        "한도",
        "결제일",
    )
    return any(kw in q for kw in keywords)



def _compute_month_context(year_month: str) -> Tuple[int, int]:
    """Spring에서 내려준 yearMonth(YYYYMM) 기준으로 해당 월 일수·경과 일수 결정.
    - 현재월이면 days_elapsed = 오늘 날짜
    - 과거월이면 days_elapsed = days_in_month (확정 처리, 월말 기준 환산)
    """
    now = datetime.now()
    current_ym = now.strftime("%Y%m")
    yyyymm = (year_month or "").replace("-", "").strip()
    if len(yyyymm) != 6:
        yyyymm = current_ym
    year = int(yyyymm[:4])
    month = int(yyyymm[4:6])
    days_in_month = monthrange(year, month)[1]
    if yyyymm == current_ym:
        days_elapsed = min(now.day, days_in_month)
    else:
        days_elapsed = days_in_month  # 과거월: 전부 경과로 처리
    return days_in_month, days_elapsed


def _is_current_month(year_month: str) -> bool:
    """리포트 대상 월이 현재월인지 여부 (예측 vs 월말 기준 환산 표현용)."""
    yyyymm = (year_month or "").replace("-", "").strip()
    if len(yyyymm) != 6:
        return True
    return yyyymm == datetime.now().strftime("%Y%m")


def _compute_projection(
    total_spent: int,
    monthly_budget: int,
    days_elapsed: int,
    days_in_month: int,
) -> Dict[str, Any]:
    """경과일·월 일수·총 지출·예산으로 일평균·월말 예상 지출·예상 초과/절약·사용률 계산."""
    daily_average_spend = int(total_spent / days_elapsed) if days_elapsed else 0
    projected_spend = daily_average_spend * days_in_month
    projected_over_amount = (projected_spend - monthly_budget) if monthly_budget else 0
    budget_usage_rate = (total_spent / monthly_budget) if monthly_budget else 0.0
    projected_usage_rate = (projected_spend / monthly_budget) if monthly_budget else 0.0
    return {
        "daily_average_spend": daily_average_spend,
        "projected_spend": projected_spend,
        "projected_over_amount": projected_over_amount,
        "budget_usage_rate": budget_usage_rate,
        "projected_usage_rate": projected_usage_rate,
    }


def _format_projected_over_text(
    projected_over_amount: int, has_budget: bool, use_prediction: bool = True
) -> str:
    """예상 초과/절약액을 사용자 친화 문구로 변환. use_prediction=False면 '월말 기준 환산' 표현."""
    if not has_budget:
        return "월말 예상 초과/절약액: -"
    label = "예측" if use_prediction else "월말 기준 환산"
    if projected_over_amount > 0:
        return f"월말 {label} 초과: {projected_over_amount:,}원"
    if projected_over_amount < 0:
        return f"월말 {label} 절약: {-projected_over_amount:,}원"
    return f"월말 {label}: 예산과 동일"


class LLMService:
    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY", "")
        if not api_key:
            logger.warning("GEMINI_API_KEY not set")
        genai.configure(api_key=api_key)
        # v1beta 404 방지: gemini-1.5-flash 대신 -latest 또는 gemini-pro 사용
        model_name = os.getenv("GEMINI_LLM_MODEL", "gemini-2.5-flash")
        self.model = genai.GenerativeModel(model_name)
    
    def generate_analysis_summary(
        self,
        transactions: List[Dict[str, Any]],
        metrics: Dict[str, Any],
        monthly_budget: Optional[int] = None,
        year_month: Optional[str] = None,
    ) -> str:
        """월별 리포트 분석 요약 생성. year_month는 Spring metrics의 yearMonth(YYYYMM) 기준."""
        try:
            ym = year_month or (metrics.get("yearMonth") if isinstance(metrics.get("yearMonth"), str) else None) or ""
            prompt = self._build_analysis_prompt(transactions, metrics, monthly_budget, ym)
            response = self.model.generate_content(prompt)
            return response.text
        except Exception as e:
            logger.error(f"LLM analysis failed: {e}")
            return self._generate_default_summary(metrics, monthly_budget)
    
    def answer_report_question(
        self,
        question: str,
        metrics: Dict[str, Any],
        llm_summary: Optional[str] = None
    ) -> str:
        """리포트 맥락 기반 질문 답변 (전체 맥락)"""
        try:
            context = self._build_report_context(metrics, llm_summary)
            prompt = f"""당신은 사용자의 소비 리포트를 분석해 알려주는 AI 어시스턴트입니다.
                아래 [리포트 맥락]을 바탕으로 사용자의 [질문]에 친근하고 간결하게 답변해주세요.
                답변은 2~4문장, 150자 이내로 핵심만 전달하세요. 이모지 사용 가능.

                [리포트 맥락]
                {context}

                [사용자 질문]
                {question}

                [답변]
                """
            response = self.model.generate_content(prompt)
            return response.text
        except Exception as e:
            logger.error(f"LLM answer failed: {e}")
            return self._generate_default_qa_answer(question, metrics)
    
    def answer_from_rag_context(
        self,
        question: str,
        chunk_contents: List[str]
    ) -> str:
        """RAG: 검색된 청크만 컨텍스트로 사용해 답변"""
        if not chunk_contents:
            return "검색된 리포트 내용이 없어 답변할 수 없어요. 해당 월 리포트를 생성했는지 확인해 주세요."
        
        try:
            context = "\n\n---\n\n".join(chunk_contents)
            prompt = f"""당신은 사용자의 소비 리포트를 분석해 알려주는 AI 어시스턴트입니다.
                아래 [검색된 리포트 내용]만을 바탕으로 사용자의 [질문]에 친근하고 간결하게 답변해주세요.
                답변은 2~4문장, 150자 이내로 핵심만 전달하세요. 이모지 사용 가능.

                [검색된 리포트 내용]
                {context}

                [사용자 질문]
                {question}

                [답변]
                """
            response = self.model.generate_content(prompt)
            return response.text
        except Exception as e:
            logger.error(f"RAG answer failed: {e}")
            return "답변 생성 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요."

    def answer_from_rag_with_facts(
        self,
        question: str,
        facts: str,
        chunk_contents: List[str]
    ) -> str:
        """RAG: Spring이 준 Facts + 검색된 청크를 컨텍스트로 사용해 답변"""
        try:
            context_parts = []
            if facts and facts.strip():
                context_parts.append(f"[Facts]\n{facts.strip()}")
            if chunk_contents:
                context_parts.append("[검색된 리포트 내용]\n" + "\n\n---\n\n".join(chunk_contents))
            context = "\n\n".join(context_parts) if context_parts else "제공된 맥락이 없습니다."
            prompt = f"""당신은 사용자의 소비 리포트를 분석해 알려주는 AI 어시스턴트입니다.
                아래 맥락을 바탕으로 사용자의 [질문]에 친근하고 간결하게 답변해주세요.
                답변은 2~4문장, 150자 이내로 핵심만 전달하세요. 이모지 사용 가능.

                {context}

                [사용자 질문]
                {question}

                [답변]
                """
            response = self.model.generate_content(prompt)
            return response.text
        except Exception as e:
            logger.error(f"RAG answer (with facts) failed: {e}")
            return "답변 생성 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요."
    
    def _build_analysis_prompt(
        self,
        transactions: List[Dict[str, Any]],
        metrics: Dict[str, Any],
        monthly_budget: Optional[int],
        year_month: str = "",
    ) -> str:
        """리포트 분석 프롬프트 빌드 (6개 섹션, 숫자 기반). Spring metrics의 yearMonth 기준으로 월 정보·예측 계산."""
        days_in_month, days_elapsed = _compute_month_context(year_month)
        total_expense_raw = metrics.get("totalExpense", 0)
        total_spent = abs(int(total_expense_raw)) if total_expense_raw else 0
        monthly_budget_val = int(monthly_budget) if monthly_budget else 0
        is_current = _is_current_month(year_month)
        proj = _compute_projection(total_spent, monthly_budget_val, days_elapsed, days_in_month)
        daily_average_spend = proj["daily_average_spend"]
        projected_spend = proj["projected_spend"]
        budget_usage_rate = proj["budget_usage_rate"]
        projected_usage_rate = proj["projected_usage_rate"]
        projected_over_amount = proj["projected_over_amount"]
        over_text = _format_projected_over_text(
            projected_over_amount, bool(monthly_budget_val), use_prediction=is_current
        )
        trend_label = "예측" if is_current else "월말 기준 환산"
        lines = [
            "=== 기본 통계 ===",
            f"총 거래 건수: {len(transactions)}건",
            f"총 수입: {metrics.get('totalIncome', 0):,}원",
            f"총 지출: {total_spent:,}원",
            f"월 예산: {monthly_budget_val:,}원" if monthly_budget_val else "월 예산: 미설정",
            "",
            f"=== 추세 기반 월말 {trend_label} (계산값) ===",
            f"경과 일수: {days_elapsed}일 / 해당 월 총 {days_in_month}일",
            f"일평균 지출: {daily_average_spend:,}원",
            f"현재 페이스 기준 월말 {trend_label} 지출: {projected_spend:,}원",
            f"예산 사용률: {budget_usage_rate * 100:.1f}%" if monthly_budget_val else "예산 사용률: -",
            f"월말 {trend_label} 사용률: {projected_usage_rate * 100:.1f}%" if monthly_budget_val else f"월말 {trend_label} 사용률: -",
            over_text,
            "",
            "=== 카테고리별 지출 ===",
        ]
        if "categoryExpenses" in metrics:
            for cat, amt in metrics["categoryExpenses"].items():
                lines.append(f"- {cat}: {abs(int(amt)):,}원")
        # 카테고리별 지출 비중 (예산 없을 때 위험도 분석용)
        ratios = metrics.get("categoryExpenseRatios")
        if ratios and isinstance(ratios, dict):
            lines.append("")
            lines.append("=== 카테고리별 지출 비중 (0~1) ===")
            for cat, r in sorted(ratios.items(), key=lambda x: -x[1])[:10]:
                pct = float(r) * 100 if r is not None else 0
                lines.append(f"- {cat}: {pct:.1f}%")
        # 최근 7일 지출 (트렌드·급증 판단용)
        last7 = metrics.get("last7Days")
        if last7 and isinstance(last7, dict):
            l7_total = last7.get("total")
            if l7_total is not None:
                l7_total = int(l7_total)
                lines.append("")
                lines.append("=== 최근 7일 지출 ===")
                lines.append(f"최근 7일 합계: {l7_total:,}원 (월 누적 대비: {total_spent:,}원)")
                if total_spent > 0 and l7_total > total_spent * 0.5:
                    lines.append("※ 최근 7일이 월 누적의 50% 초과 → 최근 소비 급증 가능성")
        # 카테고리 예산 있으면 사용률 함께 표시 (위험도 80% 기준용)
        cat_budgets = metrics.get("categoryBudgets")
        if cat_budgets and isinstance(cat_budgets, dict) and (metrics.get("categoryExpenses") or {}):
            lines.append("")
            lines.append("=== 카테고리 예산 대비 사용 ===")
            for cat, bud in cat_budgets.items():
                bud_val = int(bud) if bud is not None else 0
                spent_val = 0
                if "categoryExpenses" in metrics and isinstance(metrics["categoryExpenses"], dict):
                    raw = metrics["categoryExpenses"].get(cat)
                    spent_val = abs(int(raw)) if raw is not None else 0
                use_pct = (spent_val / bud_val * 100) if bud_val else 0
                risk = " [위험: 80% 이상]" if bud_val and use_pct >= 80 else ""
                lines.append(f"- {cat}: 예산 {bud_val:,}원 / 사용 {spent_val:,}원 / 사용률 {use_pct:.1f}%{risk}")
        data_block = "\n".join(lines)

        prompt = f"""당신은 가계부·예산 관리 전문 AI 어시스턴트입니다.
- **데이터에 없는 내용은 추측하지 마세요.** 제시된 숫자(금액·비율·일수)만 인용하고, 계산값은 과장 없이 그대로 사용하세요.
- **데이터가 부족하거나 판단하기 어려운 경우 해당 섹션에 반드시 "데이터가 부족해 단정하기 어렵습니다"를 포함하세요.**
- **아래 6개 섹션을 반드시 모두 출력하세요.**

[데이터]
{data_block}

[출력 형식] 반드시 아래 6개 섹션 제목을 그대로 쓰고, 각 섹션당 2~4문장으로 이어서 작성하세요. 금액은 원(원) 단위, 비율은 %로 표기하고, 데이터에 제시된 수치만 사용하세요. (소비·저축 인사이트와 동일한 형식)

---
## 1. 재정 상태 요약
(이번 달 지출·수입·예산 요약, 숫자로 간단히)

## 2. 예산 대비 사용 현황
(총 지출 대비 예산 사용률, 남은 예산 또는 초과 여부)

## 3. 소비 속도 분석 (Burn Rate)
(경과 일수, 일평균 지출 금액을 반드시 명시)

## 4. 추세 기반 월말 {trend_label}
(데이터의 "월말 {trend_label} 지출·사용률·초과/절약" 값을 그대로 사용. 현재월이면 예측, 과거월이면 월말 기준 환산 의미)

## 5. 카테고리 위험도 분석
- **카테고리 예산(categoryBudgets)이 데이터에 있으면**: 사용률 80% 이상인 카테고리를 위험으로 강조.
- **카테고리 예산이 없으면** 비중(categoryExpenseRatios) 기반 편중 위험도 규칙 사용: 1위 비중 >= 55% → 집중 위험, 상위 2개 합 >= 75% → 편중 위험, 최근 7일이 월 누적 50% 초과 → 최근 소비 급증 언급.
- **최근 7일(last7Days) 데이터**를 1~2문장으로 해석해 포함 (규모·추세 요약).

## 6. 실행 가능한 절약 전략
(지출 속도 조정·카테고리별 절감 등 구체적 행동 제안)
---
"""
        return prompt
    
    def _build_report_context(self, metrics: Dict[str, Any], llm_summary: Optional[str]) -> str:
        """리포트 맥락 텍스트 빌드"""
        context = f"=== 기존 AI 분석 요약 ===\n{llm_summary or ''}\n\n"
        context += "=== 월별 통계 ===\n"
        context += f"총 수입: {metrics.get('totalIncome', 0)}원\n"
        context += f"총 지출: {metrics.get('totalExpense', 0)}원\n"
        if "categoryExpenses" in metrics:
            context += f"카테고리별 지출: {metrics['categoryExpenses']}\n"
        return context
    
    def _generate_default_summary(self, metrics: Dict[str, Any], monthly_budget: Optional[int]) -> str:
        """기본 요약 생성 (API 실패 시)"""
        total_expense = metrics.get("totalExpense", 0)
        total_income = metrics.get("totalIncome", 0)
        summary = f"이번 달 총 수입은 {total_income}원, 총 지출은 {total_expense}원입니다. "
        if monthly_budget and total_expense > monthly_budget:
            summary += f"예산을 {total_expense - monthly_budget}원 초과했습니다. "
        else:
            summary += "예산 내에서 지출을 관리하고 있습니다. "
        summary += "규칙적인 예산 관리를 통해 더 나은 재정 상태를 유지하시기 바랍니다."
        return summary
    
    def _generate_default_qa_answer(self, question: str, metrics: Dict[str, Any]) -> str:
        """기본 Q&A 답변 (API 실패 시)"""
        total_expense = metrics.get("totalExpense", 0)
        total_income = metrics.get("totalIncome", 0)
        return f"이번 달 총 지출은 {total_expense}원, 총 수입은 {total_income}원입니다. " \
               "리포트를 생성한 뒤 질문해 주시면 더 자세히 답변드릴 수 있어요."

    def generate_budget_insight(self, payload: Dict[str, Any]) -> str:
        """예산/목표 인사이트: 저축 추천, 예산 대비 사용, 추세·이번 달 전망."""
        try:
            prompt = self._build_budget_insight_prompt(payload)
            response = self.model.generate_content(prompt)
            return response.text
        except Exception as e:
            logger.error(f"Budget insight LLM failed: {e}")
            return self._default_budget_insight(payload)

    def _build_budget_insight_prompt(self, payload: Dict[str, Any]) -> str:
        year_month = payload.get("year_month", "")
        monthly_budget = payload.get("monthly_budget") or 0
        total_spent = payload.get("total_spent") or 0
        categories = payload.get("categories") or []
        saving_goals = payload.get("saving_goals") or []
        last_months = payload.get("last_months") or []
        days_elapsed = payload.get("days_elapsed", 0)
        days_in_month = payload.get("days_in_month", 30)
        daily_average_spend = payload.get("daily_average_spend") or 0
        projected_spend = payload.get("projected_spend") or 0
        projected_over_amount = payload.get("projected_over_amount") or 0
        budget_usage_rate = payload.get("budget_usage_rate")
        projected_usage_rate = payload.get("projected_usage_rate")

        # YYYYMM → YYYY년 MM월
        if len(year_month) == 6:
            y, m = year_month[:4], year_month[4:6]
            label = f"{y}년 {m}월"
        else:
            label = year_month

        br = float(budget_usage_rate or 0)
        pr = float(projected_usage_rate or 0)

        lines = [
            f"대상 월: {label}",
            f"월 예산(총): {monthly_budget:,}원",
            f"실제 지출 합계: {total_spent:,}원",
            "",
            "=== 추세 기반 예산 초과 예측 (계산값) ===",
            f"경과 일수: {days_elapsed}일 / 해당 월 총 {days_in_month}일",
            f"일평균 지출: {int(daily_average_spend):,}원",
            f"현재 페이스 기준 월말 예상 지출: {int(projected_spend):,}원",
            f"예산 사용률: {br * 100:.1f}%",
            f"월말 예상 사용률: {pr * 100:.1f}%",
            f"월말 예상 초과/절약액: {int(projected_over_amount):,}원 (양수=초과, 음수=절약)",
            "",
            "=== 카테고리별 예산 vs 사용액 ===",
        ]
        for c in categories:
            name = c.get("category_name", "기타")
            budget_amt = c.get("budget_amount") or 0
            spent = c.get("spent") or 0
            usage_pct = (spent / budget_amt * 100) if budget_amt else 0
            diff = spent - budget_amt if budget_amt else 0
            status = "초과" if diff > 0 else ("절약" if diff < 0 else "적정")
            risk = " [위험: 예산 80% 이상 사용]" if budget_amt and usage_pct >= 80 else ""
            lines.append(f"- {name}: 예산 {budget_amt:,}원 / 사용 {spent:,}원 / 사용률 {usage_pct:.1f}% ({status}){risk}")
        lines.append("")
        lines.append("=== 저축 목표 ===")
        for g in saving_goals:
            title = g.get("goal_title", "저축 목표")
            goal_amt = g.get("goal_amount") or 0
            current = g.get("current_amount") or 0
            lines.append(f"- {title}: 목표 {goal_amt:,}원 / 현재 {current:,}원")
        if last_months:
            lines.append("")
            lines.append("=== 최근 2개월 지출 합계(추세용) ===")
            for lm in last_months:
                ym = lm.get("year_month", "")
                sp = lm.get("total_spent", 0)
                if len(ym) == 6:
                    lines.append(f"- {ym[:4]}년 {ym[4:6]}월: {sp:,}원")
                else:
                    lines.append(f"- {ym}: {sp:,}원")

        data_block = "\n".join(lines)
        prompt = f"""당신은 가계부·예산 관리 전문 AI 어시스턴트입니다.
            아래 [데이터]에 있는 **숫자(금액·비율·일수)**를 우선하여 인용하고, 각 섹션을 2~4문장으로 작성하세요. 
            계산값은 과장 없이 그대로 사용하세요.

            [데이터]
            {data_block}

            [출력 형식] 반드시 아래 6개 섹션 제목을 그대로 쓰고, 각 섹션당 2~4문장으로 이어서 작성하세요. 
            금액·비율은 반드시 원(원) 단위·%로 표기하고, 데이터에 제시된 수치를 그대로 사용하세요.

            ---
            ## 1. 재정 상태 요약
            (대상 월, 월 예산, 현재까지 지출 합계, 경과 일수 등 핵심 수치를 한 줄로 요약. 2~4문장)

            ## 2. 예산 대비 사용 현황
            (월 예산 대비 현재 사용률·잔여 예산 또는 초과액 등 숫자로 현황 정리. 2~4문장)

            ## 3. 소비 속도 분석 (Burn Rate)
            (일 평균 지출 금액을 원 단위로 명시하고, 경과 일수 대비 소비 속도가 빠른지/적정인지 수치 기반으로 평가. 2~4문장)

            ## 4. 추세 기반 월말 예측
            (현재 페이스 기준 월말 예상 지출·월말 예상 사용률·예상 초과/절약액을 데이터의 계산값 그대로 원 단위로 제시. 2~4문장)

            ## 5. 카테고리 위험도 분석
            (예산 대비 80% 이상 사용한 카테고리를 카테고리명·사용액·사용률(%)로 나열. 해당 없으면 "위험 수준 카테고리 없음" 등으로 명시. 2~4문장)

            ## 6. 실행 가능한 절약 전략
            (위험 카테고리·초과 예측을 바탕으로 일평균 목표액 또는 카테고리별 절감 목표 등 구체적 숫자와 함께 2~4문장 제안)
            ---

            [규칙] 숫자 기반 분석을 우선하세요. 모든 금액은 원(원), 비율은 %로 표기. 
            추세·예측 수치는 제공된 계산값을 그대로 사용하고 과장하지 마세요. 이모지는 필요 시만 사용."""
        return prompt

    def _default_budget_insight(self, payload: Dict[str, Any]) -> str:
        total_spent = payload.get("total_spent") or 0
        monthly_budget = payload.get("monthly_budget") or 0
        diff = total_spent - monthly_budget
        if monthly_budget and diff > 0:
            over = f"예산을 {diff:,}원 초과했어요. 다음 달엔 카테고리별 지출을 조절해 보세요."
        elif monthly_budget and diff < 0:
            over = f"예산보다 {-diff:,}원 덜 쓰고 있어요. 여유 자금으로 저축을 늘려 보세요."
        else:
            over = "예산과 지출을 꾸준히 기록하면 더 정확한 인사이트를 드릴 수 있어요."
        return f"이번 달 총 지출은 {total_spent:,}원입니다. {over}"

    def answer_from_evidence(
        self,
        question: str,
        evidence_text: str,
        rag_chunks: Optional[List[str]] = None,
    ) -> str:
        """PR3: 근거 기반 답변. 출력 형식 강제 + 안전 규칙(야식 등 단정 금지).
        AI 리포트(소비/리포트 Q&A)와 추천(카드 Q&A) 양쪽에서 호출되므로,
        카드 전용으로 막지 않고 근거가 있으면 소비·예산·카드 질문 모두 답변한다."""
        context_parts = []
        if evidence_text and evidence_text.strip():
            context_parts.append(f"[DB 분석 근거]\n{evidence_text.strip()}")
        if rag_chunks:
            context_parts.append("[리포트 요약 참고]\n" + "\n\n---\n\n".join(rag_chunks))
        context = "\n\n".join(context_parts) if context_parts else "제공된 근거가 없습니다."

        has_evidence = bool(context.strip() and context.strip() != "제공된 근거가 없습니다.")
        no_shortage_note = " [근거]에 지출·카테고리·리포트 내용이 있으므로 '데이터가 부족하여'로 시작하지 말고 바로 결론과 수치를 제시하세요." if has_evidence else ""
        prompt = f"""당신은 사용자의 소비 데이터를 분석해 알려주는 AI 어시스턴트입니다.
            아래 [근거]에 있는 수치와 랭킹만 사용해서 답변하세요.

            [출력 형식 강제]
            1. 첫 줄: 결론 1줄 (핵심 메시지).
            2. 그 다음: 근거 2~3개를 숫자/랭킹을 포함해 나열 (예: "총 지출 50만 원", "1위 식비 20만 원", "전월 대비 +10%").
            3. 데이터 부족 시 "데이터가 부족해 단정하기 어렵습니다" 등 단정 금지 문구를 사용하세요.
            4. 2~4문장, 150자 이내. 이모지 사용 가능.

            [안전 규칙]
            - "야식", "배달 습관" 등 원인/습관 단어는 DB 근거가 있을 때만 사용하세요.
            - LATE_NIGHT(21~03시) 시간대 지출 증가 또는 키워드(배달, 야식 등) 통계가 [근거]에 있을 때만 "야식/야간 지출"이라고 말할 수 있습니다.
            - 해당 근거가 없으면 "단정하기 어렵고, 대신 야간 시간대(21~03시) 지출 증가가 확인됩니다"처럼 완곡하게만 표현하세요.
            - 근거에 없는 원인은 추측하지 마세요.
            - [근거]에 "이번달 X원 전달 Y원 증감 Z원" 형식이 있으면: "이번달"이 현재 월 해당 카테고리 지출액, "전달"이 전월 지출액, "증감"이 전달 대비 차이(Z=이번달-전달)입니다. 증감 0원은 "현재 지출 0원"이 아니라 "전달과 동일"을 의미하므로, 이번달/전달 금액을 그대로 인용해 답변하세요.
            - [근거]에 "기간 A", "기간 B"가 있으면: 기간 A = 이번달(분석 기준월), 기간 B = 전달(비교 기준월)입니다. "저번달/전달 카테고리" 질문에는 기간 B(전달)의 카테고리별 데이터를 사용해 답하세요.

            [근거]
            {context}

            [사용자 질문]
            {question}

            [답변]
            """
        try:
            response = self.model.generate_content(prompt)
            return response.text or "답변을 생성하지 못했어요."
        except Exception as e:
            logger.error(f"Evidence answer failed: {e}")
            return "답변 생성 중 오류가 났어요. 잠시 뒤 다시 시도해 주세요."

    def classify_intent(self, question: str) -> Optional[Dict[str, Any]]:
        """PR2: 질문 Intent + 슬롯 JSON 분류. USE_LANGCHAIN_INTENT=true면 LangChain fallback 사용."""
        use_langchain = os.getenv("USE_LANGCHAIN_INTENT", "").strip().lower() in ("1", "true", "yes")
        if use_langchain:
            try:
                from app.intent_classifier_langchain import classify_intent_with_langchain
                out = classify_intent_with_langchain(question or "")
                if out is not None:
                    return out
            except Exception as e:
                logger.warning("LangChain intent classifier failed, falling back to raw LLM: %s", e)

        prompt = """당신은 소비/가계부 앱의 사용자 질문을 분류하는 모듈입니다.
            아래 [질문]에 대해 다음 JSON만 출력하세요. 다른 설명 없이 JSON 한 덩어리만 출력합니다.

            JSON 스키마:
            {
            "intent": "SUMMARY|CAUSE_ANALYSIS|COMPARISON|BUDGET_STATUS|PATTERN_DEEPDIVE|ADVICE_ACTION|DEFINITION_HELP|DATA_FIX",
            "confidence": 0.0~1.0,
            "yearMonth": "YYYYMM 또는 null(없으면 null, 백엔드에서 이번달 기본)",
            "baselineYearMonth": "YYYYMM 또는 null(비교 기준, 없으면 null→전월 기본)",
            "categoryId": number 또는 null(카테고리 ID를 질문에서 알 수 없으면 반드시 null, followUpQuestion에 카테고리 재질문)",
            "dimension": "timeband|dow|merchant|keyword 또는 null",
            "keywords": ["키워드1", "키워드2"] 또는 [],
            "needsDb": true|false,
            "needsRag": true|false,
            "followUpQuestion": "슬롯 부족 시 되묻기 1개만, 없으면 null"
            }

            Intent 의미: SUMMARY=요약, CAUSE_ANALYSIS=원인분석, COMPARISON=비교, BUDGET_STATUS=예산상태, PATTERN_DEEPDIVE=패턴심화(야식/요일/가맹점), ADVICE_ACTION=조언/절약팁, DEFINITION_HELP=정의/도움말, DATA_FIX=데이터정정요청.
            needsDb: DB 분석 쿼리 필요 여부(SUMMARY/CAUSE_ANALYSIS/COMPARISON/BUDGET_STATUS/PATTERN_DEEPDIVE면 true).
            needsRag: RAG/리포트 맥락 필요 여부(ADVICE_ACTION, DEFINITION_HELP면 true).
            질문에서 연월을 추출할 수 없으면 yearMonth, baselineYearMonth는 null로 두세요.
            카테고리는 DB의 category_id가 아니면 추측하지 말고 categoryId=null로 두고, "어떤 카테고리가 궁금하신가요?" 같은 followUpQuestion을 넣으세요.

            [질문]
            """
        prompt += question.strip() + "\n\n[JSON]\n"
        try:
            response = self.model.generate_content(prompt)
            text = (response.text or "").strip()
            if not text:
                return None
            # JSON 블록만 추출 (```json ... ``` 또는 첫 { ~ 마지막 })
            if "```" in text:
                start = text.find("{")
                end = text.rfind("}") + 1
                if start >= 0 and end > start:
                    text = text[start:end]
            import json
            return json.loads(text)
        except Exception as e:
            logger.error(f"Classify intent failed: {e}")
            return None
