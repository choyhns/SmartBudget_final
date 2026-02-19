import { Category } from '../types';
import { getAuthHeaders } from './authService';

const API_BASE_URL = '/api/categories';

export const categoryService = {
  /**
   * 모든 카테고리 조회 (플랫 리스트)
   */
  async getAllCategories(): Promise<Category[]> {
    const response = await fetch(API_BASE_URL, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 카테고리를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 카테고리 트리 구조로 조회
   */
  async getCategoryTree(): Promise<Category[]> {
    const response = await fetch(`${API_BASE_URL}/tree`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 카테고리를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 카테고리 생성
   */
  async createCategory(category: { name: string; parentId?: number }): Promise<Category> {
    const response = await fetch(API_BASE_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify(category),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 카테고리를 생성하는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 기본 카테고리 초기화
   */
  async initDefaultCategories(): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/init`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 카테고리 초기화에 실패했습니다.`);
    }
  },

  /**
   * 텍스트 파일 기반 카테고리 초기화 (계층 구조)
   */
  async initCategoriesFromTextFile(force: boolean = false): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/init-from-file?force=${force}`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 카테고리 초기화에 실패했습니다.`);
    }
  },
};
