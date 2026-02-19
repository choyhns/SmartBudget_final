import { Card, Product, Recommendation } from '../types';
import { authService, getAuthHeaders } from './authService';

const API_BASE_URL = '/api/recommendations';

export const recommendationService = {
  /**
   * Q&A 선택지 목록
   */
  async getQaOptions(): Promise<Array<{ id: string; title: string; targetType: string; needsItemSelection: boolean }>> {
    try {
      const response = await fetch(`${API_BASE_URL}/qa/options`, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          ...getAuthHeaders(),
        },
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } catch {
      return [];
    }
  },

  /**
   * 카드별 "이 카드가 적합한 이유" LLM 생성 (추천 페이지)
   */
  async getCardSuitableReason(cardId: number): Promise<string> {
    try {
      const uid = authService.getUserId() || 1;
      const response = await fetch(`${API_BASE_URL}/cards/${cardId}/suitable-reason?userId=${uid}`, {
        method: 'GET',
        headers: { 'Accept': 'application/json', ...getAuthHeaders() },
      });
      if (!response.ok) return '회원님의 소비 패턴과 이 카드 혜택을 맞춰봤어요. 잘 어울리실 거예요.';
      const data = await response.json() as { reason?: string };
      return data.reason ?? '회원님의 소비 패턴과 이 카드 혜택을 맞춰봤어요. 잘 어울리실 거예요.';
    } catch {
      return '회원님의 소비 패턴과 이 카드 혜택을 맞춰봤어요. 잘 어울리실 거예요.';
    }
  },

  /**
   * 여러 카드에 대한 적합 이유를 한 번의 API 호출로 조회 (할당량 절약)
   * 응답: { reasons: Record<id, string>, templateUsed?: boolean }
   */
  async getCardSuitableReasonsBatch(cardIds: number[]): Promise<{ reasons: Record<number, string>; templateUsed?: boolean }> {
    const fallback = '회원님의 소비 패턴과 이 카드 혜택을 맞춰봤어요. 잘 어울리실 거예요.';
    const fallbackResult = { reasons: Object.fromEntries(cardIds.map(id => [id, fallback])) as Record<number, string>, templateUsed: true };
    try {
      const uid = authService.getUserId() || 1;
      console.log('[배치 API] 요청:', { cardIds, userId: uid });
      const response = await fetch(`${API_BASE_URL}/cards/suitable-reasons-batch?userId=${uid}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json', ...getAuthHeaders() },
        body: JSON.stringify({ cardIds }),
      });
      if (!response.ok) {
        console.warn('[배치 API] HTTP 오류:', response.status, response.statusText);
        return fallbackResult;
      }
      const data = await response.json() as { reasons?: Record<string, string>; templateUsed?: boolean };
      console.log('[배치 API] 응답:', { reasonsCount: Object.keys(data.reasons ?? {}).length, templateUsed: data.templateUsed });
      const reasonsMap = data.reasons ?? {};
      const result: Record<number, string> = {};
      for (const id of cardIds) {
        const reason = reasonsMap[String(id)];
        result[id] = reason && reason.trim() ? reason : fallback;
        if (!reason || !reason.trim()) {
          console.warn(`[배치 API] 카드 ${id} 이유가 비어있음, 폴백 사용`);
        }
      }
      return { reasons: result, templateUsed: Boolean(data.templateUsed) };
    } catch (error) {
      console.error('[배치 API] 예외:', error);
      return fallbackResult;
    }
  },

  /**
   * Q&A 답변 요청 (customQuestion: 직접 입력 시 사용)
   */
  async answerQa(params: { questionId: string; cardId?: number; productId?: number; yearMonth?: string; userId?: number; customQuestion?: string; recommendedCardIds?: number[] }): Promise<{ answer: string; sources?: Array<{ type: string; label: string }> }> {
    const uid = params.userId || authService.getUserId() || 1;
    const recommendedCardIds = Array.isArray(params.recommendedCardIds) ? [...params.recommendedCardIds] : undefined;
    try {
      const response = await fetch(`${API_BASE_URL}/qa/answer`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          ...getAuthHeaders(),
        },
        body: JSON.stringify({
          questionId: params.questionId,
          userId: uid,
          yearMonth: params.yearMonth,
          cardId: params.cardId,
          productId: params.productId,
          customQuestion: params.customQuestion,
          ...(recommendedCardIds != null && recommendedCardIds.length > 0 && { recommendedCardIds }),
        }),
      });

      if (!response.ok) {
        if (response.status === 401) {
          return {
            answer: '로그인이 만료되었어요. 다시 로그인해 주세요.',
            sources: [],
          };
        }
        throw new Error(`HTTP ${response.status}`);
      }
      return await response.json();
    } catch {
      return {
        answer: 'Q&A 서비스를 일시적으로 사용할 수 없어요. 잠시 후 다시 시도해주세요.',
        sources: [],
      };
    }
  },

  /**
   * 모든 카드 목록 조회
   */
  async getAllCards(): Promise<Card[]> {
    try {
      const response = await fetch(`${API_BASE_URL}/cards`, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          ...getAuthHeaders(),
        },
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data: Card[] = await response.json();
      // API가 snake_case(image_url)로 올 수 있으므로 imageUrl로 통일
      return Array.isArray(data)
        ? data.map((c: Card & { image_url?: string }) => ({
            ...c,
            imageUrl: c.imageUrl ?? c.image_url,
          }))
        : data;
    } catch {
      return [];
    }
  },

  /**
   * 상위 지출 카테고리·카드 혜택 매칭 추천 카드 상위 3장
   */
  async getRecommendedCards(userId?: number): Promise<Card[]> {
    const uid = userId ?? authService.getUserId() ?? 1;
    try {
      const response = await fetch(`${API_BASE_URL}/cards/recommended?userId=${uid}`, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          ...getAuthHeaders(),
        },
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data: Card[] = await response.json();
      return Array.isArray(data)
        ? data.map((c: Card & { image_url?: string }) => ({
            ...c,
            imageUrl: c.imageUrl ?? c.image_url,
          }))
        : data;
    } catch {
      return [];
    }
  },

  /**
   * 모든 금융상품 목록 조회
   */
  async getAllProducts(): Promise<Product[]> {
    try {
      const response = await fetch(`${API_BASE_URL}/products`, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          ...getAuthHeaders(),
        },
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } catch {
      return [];
    }
  },

  /**
   * 사용자 보유 카드 목록
   */
  async getUserCards(userId?: number): Promise<Card[]> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/cards/user?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 보유 카드를 불러오는데 실패했습니다.`);
    }

    return await response.json();
  },

  /**
   * 추천 내역 조회
   */
  async getRecommendations(userId?: number): Promise<Recommendation[]> {
    const uid = userId || authService.getUserId() || 1;
    try {
      const response = await fetch(`${API_BASE_URL}?userId=${uid}`, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          ...getAuthHeaders(),
        },
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return await response.json();
    } catch {
      return [];
    }
  },

  /**
   * 소비 패턴 기반 추천 생성
   */
  async generateRecommendations(userId?: number): Promise<{
    spendingPattern: Record<string, any>;
    llmAnalysis: string;
    recommendations: Recommendation[];
  }> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/generate?userId=${uid}`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 추천을 생성하는데 실패했습니다.`);
    }

    return await response.json();
  },
};
