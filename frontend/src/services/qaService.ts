import { authService, getAuthHeaders } from './authService';

const API_BASE_URL = '/api/qa';

export interface QaAskResponse {
  /** PR3: 동일 값. 하위 호환용 */
  answer: string;
  answerText?: string;
  usedIntent?: string;
  evidence?: {
    db?: Record<string, unknown>;
    rag?: Record<string, unknown>;
  };
  followUpQuestion?: string;
}

export const qaService = {
  /**
   * 자유/추가 질문: Intent 분류 → DB 분석 → (선택) RAG 보조 → 근거 기반 답변.
   * yearMonth 없으면 이번 달 기준으로 1차 답변 후 되묻기 제안 가능.
   */
  async ask(question: string, yearMonth?: string | null, userId?: number): Promise<QaAskResponse> {
    const uid = userId ?? authService.getUserId() ?? 1;
    const response = await fetch(`${API_BASE_URL}/ask`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({
        userId: uid,
        yearMonth: yearMonth ?? undefined,
        question,
      }),
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.message || `HTTP ${response.status}: 답변을 불러오는데 실패했습니다.`);
    }

    return response.json();
  },
};
