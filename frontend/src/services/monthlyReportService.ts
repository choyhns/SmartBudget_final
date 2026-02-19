import { MonthlyReport, AIAnalysis, QuestionCard } from '../types';
import { authService, getAuthHeaders } from './authService';

const API_BASE_URL = '/api/monthly-reports';

export const monthlyReportService = {
  /**
   * 모든 월별 리포트 조회
   */
  async getAllReports(userId?: number): Promise<MonthlyReport[]> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 월별 리포트를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 특정 월 리포트 조회
   */
  async getReportByYearMonth(yearMonth: string, userId?: number): Promise<MonthlyReport | null> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/${yearMonth}?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      if (response.status === 404) return null;
      throw new Error(`HTTP ${response.status}: 리포트를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 이번 달 리포트 조회
   */
  async getCurrentMonthReport(userId?: number): Promise<MonthlyReport | null> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/current?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      if (response.status === 404) return null;
      throw new Error(`HTTP ${response.status}: 이번 달 리포트를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 이번 달 리포트 생성/업데이트
   */
  async generateCurrentMonthReport(monthlyBudget?: number, userId?: number): Promise<MonthlyReport> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/current/generate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ userId: uid, monthlyBudget }),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 리포트를 생성하는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 리포트를 AIAnalysis 형식으로 변환
   */
  parseReportToAnalysis(report: MonthlyReport): AIAnalysis {
    let metrics: any = {};
    try {
      metrics = JSON.parse(report.metricsJson || '{}');
    } catch (e) {
      console.error('Failed to parse metricsJson:', e);
    }

    const categoryExpenses = metrics.categoryExpenses || {};
    const topPatterns = Object.entries(categoryExpenses)
      .sort((a: any, b: any) => b[1] - a[1])
      .slice(0, 3)
      .map(([category, amount]: [string, any]) => `${category}: ${Number(amount).toLocaleString()}원`);

    const savingTips = this.extractSavingTips(report.llmSummaryText || '');

    return {
      riskAssessment: report.llmSummaryText || '',
      topPatterns: topPatterns.length > 0 ? topPatterns : ['지출 데이터가 없습니다.'],
      savingTips: savingTips.length > 0 ? savingTips : ['규칙적인 예산 관리를 권장합니다.'],
      totalExpense: metrics.totalExpense,
      totalIncome: metrics.totalIncome,
      categoryExpenses,
    };
  },

  /**
   * LLM 요약 텍스트에서 절약 팁 추출
   * - 먼저 "6. 실행 가능한 절약 전략" 섹션 본문을 찾아 문장 단위로 쪼개 1~3개 사용
   * - 없으면 키워드(절약/팁/권장/개선) 포함 문장 중 길이 완화하여 사용
   */
  extractSavingTips(summaryText: string): string[] {
    const defaultTips = [
      '규칙적인 예산 관리를 통해 재정 상태를 개선하세요.',
      '카테고리별 지출을 분석하여 불필요한 지출을 줄이세요.',
      '월말에 지출 내역을 검토하고 다음 달 계획을 수립하세요.',
    ];
    if (!summaryText?.trim()) return defaultTips;

    // 1) "**6. 실행 가능한 절약 전략**" 또는 "6. 실행 가능한 절약 전략" 섹션 본문 추출
    const section6Markers = [
      /\*\*6\.\s*실행 가능한 절약 전략\*\*[\s\n]+([\s\S]*?)(?=\n\s*\*\*\d\.|$)/i,
      /6\.\s*실행 가능한 절약 전략[\s\n]+([\s\S]*?)(?=\n\s*\*\*\d\.|$)/i,
    ];
    for (const re of section6Markers) {
      const m = summaryText.match(re);
      if (m && m[1]) {
        const paragraph = m[1].trim().replace(/\n+/g, ' ');
        const sentences = paragraph
          .split(/(?<=[.!?。])\s+/)
          .map((s) => s.trim())
          .filter((s) => s.length > 15 && s.length < 300);
        if (sentences.length > 0) {
          const tips = sentences.slice(0, 3);
          while (tips.length < 3) tips.push(defaultTips[tips.length]);
          return tips;
        }
        // 문장으로 안 쪼개지면(한 덩어리 문단) 첫 팁으로 사용
        if (paragraph.length > 20) {
          const first = paragraph.length > 250 ? paragraph.slice(0, 247) + '…' : paragraph;
          return [first, defaultTips[1], defaultTips[2]];
        }
      }
    }

    // 2) 폴백: 절약/팁/권장/개선 포함 문장 (길이 10~200자로 완화)
    const tips: string[] = [];
    const lines = summaryText.split(/[.\n]/);
    for (const line of lines) {
      if (line.includes('절약') || line.includes('팁') || line.includes('권장') || line.includes('개선')) {
        const trimmed = line.trim().replace(/\*\*/g, '');
        if (trimmed.length > 10 && trimmed.length < 200) {
          tips.push(trimmed);
          if (tips.length >= 3) break;
        }
      }
    }
    if (tips.length > 0) {
      while (tips.length < 3) tips.push(defaultTips[tips.length]);
      return tips.slice(0, 3);
    }

    return defaultTips;
  },

  /**
   * 질문 카드: 리포트 맥락 기반 질문에 대한 LLM 답변
   */
  async askQuestion(yearMonth: string, question: string, userId?: number): Promise<{ answer: string }> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/ask`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ userId: uid, yearMonth, question }),
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.message || `HTTP ${response.status}: 답변을 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 월별 리포트 기반 추천 질문 카드 (상황 컨텍스트 + 클릭 이력 리랭크)
   */
  async getRecommendedCards(yearMonth: string, userId?: number, limit?: number): Promise<QuestionCard[]> {
    const uid = userId || authService.getUserId() || 1;
    const params = new URLSearchParams({ yearMonth });
    params.set('userId', String(uid));
    if (limit != null && limit > 0) params.set('limit', String(limit));
    const response = await fetch(`${API_BASE_URL}/cards/recommended?${params}`, {
      method: 'GET',
      headers: { Accept: 'application/json', ...getAuthHeaders() },
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 추천 카드를 불러오는데 실패했습니다.`);
    }
    return await response.json();
  },

  /**
   * 질문 카드 클릭 로그 (다음 추천 시 리랭크에 사용)
   */
  async logCardClick(yearMonth: string, cardId: number, userId?: number): Promise<void> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/ask/card-click`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ userId: uid, yearMonth, cardId }),
    });
    if (!response.ok) {
      console.warn('Card click log failed:', response.status);
    }
  },
};
