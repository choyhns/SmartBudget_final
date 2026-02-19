import { Budget, CategoryBudget, BudgetStatus, BudgetInsight } from '../types';
import { authService, getAuthHeaders } from './authService';

const API_BASE_URL = '/api/budgets';

// 현재 년월 가져오기 (yyyyMM 형식)
export function getCurrentYearMonth(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  return `${year}${month}`;
}

export const budgetService = {
  /**
   * 현재 년월 (yyyyMM)
   */
  getCurrentYearMonth(): string {
    return getCurrentYearMonth();
  },

  /**
   * 월별 총 예산 조회
   */
  async getBudget(yearMonth: string, userId?: number): Promise<Budget | null> {
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
      throw new Error(`HTTP ${response.status}: 예산을 불러오는데 실패했습니다.`);
    }

    const text = await response.text();
    if (!text || text.trim() === '') return null;
    return JSON.parse(text) as Budget;
  },

  /**
   * 모든 예산 목록 조회
   */
  async getAllBudgets(userId?: number): Promise<Budget[]> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 예산 목록을 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 월별 총 예산 설정
   */
  async setMonthlyBudget(yearMonth: string, totalBudget: number, userId?: number): Promise<Budget> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/${yearMonth}?userId=${uid}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ totalBudget }),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 예산을 설정하는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 카테고리별 예산 목록 조회
   */
  async getCategoryBudgets(yearMonth: string, userId?: number): Promise<CategoryBudget[]> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/${yearMonth}/categories?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 카테고리별 예산을 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 카테고리별 예산 설정
   */
  async setCategoryBudget(yearMonth: string, categoryId: number, budgetAmount: number, userId?: number): Promise<CategoryBudget> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/${yearMonth}/categories/${categoryId}?userId=${uid}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ budgetAmount }),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 카테고리 예산을 설정하는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 예산 대비 지출 현황 조회
   */
  async getBudgetStatus(yearMonth: string, userId?: number): Promise<BudgetStatus> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/${yearMonth}/status?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 예산 현황을 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 예산/목표 인사이트 (저축 추천, 예산 대비, 추세·이번 달 전망)
   */
  async getBudgetInsight(yearMonth: string, userId?: number): Promise<BudgetInsight> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/${yearMonth}/insight?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 인사이트를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },
};
