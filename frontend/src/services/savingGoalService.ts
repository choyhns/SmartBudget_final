import { SavingGoal } from '../types';
import { authService, getAuthHeaders } from './authService';

const API_BASE_URL = '/api/saving-goals';

export const savingGoalService = {
  /**
   * 모든 저축 목표 조회
   */
  async getAllGoals(userId?: number): Promise<SavingGoal[]> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 저축 목표를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 저축 목표 상세 조회
   */
  async getGoal(goalId: number): Promise<SavingGoal> {
    const response = await fetch(`${API_BASE_URL}/${goalId}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 저축 목표를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 저축 목표 생성
   */
  async createGoal(goal: {
    goalTitle?: string;
    goalAmount: number;
    startDate?: string;
    targetDate?: string;
    monthlyTarget?: number;
  }, userId?: number): Promise<SavingGoal> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}?userId=${uid}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify(goal),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 저축 목표를 생성하는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 저축 목표 수정
   */
  async updateGoal(goalId: number, goal: Partial<SavingGoal>): Promise<SavingGoal> {
    const response = await fetch(`${API_BASE_URL}/${goalId}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify(goal),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 저축 목표를 수정하는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 저축 목표 삭제
   */
  async deleteGoal(goalId: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/${goalId}`, {
      method: 'DELETE',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 저축 목표를 삭제하는데 실패했습니다.`);
    }
  },

  /**
   * 저축 추가
   */
  async addSaving(goalId: number, amount: number, txId?: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/${goalId}/savings`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ amount, txId }),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 저축을 추가하는데 실패했습니다.`);
    }
  },
};
