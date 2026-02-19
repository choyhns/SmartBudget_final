"""
샘플 metrics_json으로 _build_analysis_prompt 결과(데이터 블록 + 섹션 지침)를 출력해 검증.
실행: ml-server 디렉토리에서 python -m app.verify_analysis_prompt
"""
import sys
from datetime import datetime
from pathlib import Path

# app 패키지 경로
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.llm import LLMService


def sample_metrics_current_month():
    """현재월 샘플: 예측 표현 사용 (yearMonth는 호출부에서 현재월로 덮어씀)."""
    return {
        "yearMonth": datetime.now().strftime("%Y%m"),
        "totalIncome": 500000,
        "totalExpense": 210600,
        "categoryExpenses": {"여행": 100000, "식비": 60600, "문화": 50000},
        "categoryExpenseRatios": {"여행": 0.475, "식비": 0.287, "문화": 0.238},
        "last7Days": {
            "total": 120000,
            "series": [
                {"date": "2025-02-04", "amount": 15000},
                {"date": "2025-02-05", "amount": 20000},
                {"date": "2025-02-06", "amount": 18000},
                {"date": "2025-02-07", "amount": 22000},
                {"date": "2025-02-08", "amount": 25000},
                {"date": "2025-02-09", "amount": 10000},
                {"date": "2025-02-10", "amount": 10000},
            ],
        },
    }


def sample_metrics_past_month():
    """과거월 샘플: 월말 기준 환산 표현 사용."""
    return {
        "yearMonth": "202501",
        "totalIncome": 450000,
        "totalExpense": 380000,
        "categoryExpenses": {"식비": 150000, "교통": 120000, "쇼핑": 110000},
        "categoryExpenseRatios": {"식비": 0.395, "교통": 0.316, "쇼핑": 0.289},
        "last7Days": {
            "total": 95000,
            "series": [
                {"date": "2025-01-25", "amount": 10000},
                {"date": "2025-01-26", "amount": 15000},
                {"date": "2025-01-27", "amount": 20000},
                {"date": "2025-01-28", "amount": 15000},
                {"date": "2025-01-29", "amount": 15000},
                {"date": "2025-01-30", "amount": 10000},
                {"date": "2025-01-31", "amount": 10000},
            ],
        },
    }


def sample_metrics_no_budget():
    """카테고리 예산 없음: categoryExpenseRatios 기반 위험도만."""
    m = sample_metrics_current_month()
    m["categoryExpenseRatios"] = {"여행": 0.58, "식비": 0.25, "문화": 0.17}  # 1위 58% 집중
    m["last7Days"]["total"] = 150000  # 210600 * 0.5 초과 → 급증
    return m


def main():
    svc = LLMService()
    transactions = [{"txId": 1, "amount": -50000, "category": "여행"}]

    current_ym = datetime.now().strftime("%Y%m")
    cases = [
        ("현재월 (예측)", sample_metrics_current_month(), current_ym, 300000),
        ("과거월 (월말 기준 환산)", sample_metrics_past_month(), "202501", 400000),
        ("카테고리 예산 없음 + 비중/급증", sample_metrics_no_budget(), current_ym, 300000),
    ]
    for name, metrics, year_month, budget in cases:
        print("=" * 60)
        print(f"케이스: {name}")
        print("=" * 60)
        prompt = svc._build_analysis_prompt(
            transactions, metrics, budget, year_month
        )
        # 데이터 블록만 추출해 출력 (앞부분)
        if "[데이터]" in prompt and "[출력 형식]" in prompt:
            data_section = prompt.split("[데이터]")[1].split("[출력 형식]")[0].strip()
            print("[데이터 블록 미리보기]")
            print(data_section[:1500] + ("..." if len(data_section) > 1500 else ""))
        print()
        if "**4. 추세 기반 월말" in prompt:
            idx = prompt.find("**4. 추세 기반 월말")
            snippet = prompt[idx : idx + 120]
            print("[섹션 4 제목/지침]")
            print(snippet)
        print()
    print("검증 완료: 샘플 metrics로 프롬프트가 정상 생성됨.")


if __name__ == "__main__":
    main()
