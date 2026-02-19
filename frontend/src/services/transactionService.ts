import { Transaction, SimpleTransaction } from '../types';
import { authService, getAuthHeaders } from './authService';

const API_BASE_URL = '/api/transactions';

// 현재 년월 가져오기 (yyyyMM 형식)
function getCurrentYearMonth(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  return `${year}${month}`;
}

// Transaction을 SimpleTransaction으로 변환
function toSimpleTransaction(t: Transaction): SimpleTransaction {
  return {
    id: String(t.txId),
    date: t.txDatetime ? t.txDatetime.split('T')[0] : '',
    type: t.amount >= 0 ? 'INCOME' : 'EXPENSE',
    category: t.categoryName || '미분류',
    merchant: t.merchant || '',
    amount: Math.abs(t.amount),
  };
}

export const transactionService = {
  /**
   * 모든 거래 내역 가져오기
   */
  async getAllTransactions(userId?: number): Promise<SimpleTransaction[]> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}?userId=${uid}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 거래 내역을 불러오는데 실패했습니다.`);
    }

    const data: Transaction[] = await response.json();
    return data.map(toSimpleTransaction);
  },

  /**
   * 월별 거래 내역 가져오기
   */
  async getTransactionsByMonth(yearMonth: string, userId?: number): Promise<SimpleTransaction[]> {
    const uid = userId || authService.getUserId() || 1;
    const response = await fetch(`${API_BASE_URL}/month?userId=${uid}&yearMonth=${yearMonth}`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 거래 내역을 불러오는데 실패했습니다.`);
    }

    const data: Transaction[] = await response.json();
    return data.map(toSimpleTransaction);
  },

  /**
   * 거래 내역 생성
   */
  async createTransaction(transaction: Omit<SimpleTransaction, 'id'>, userId?: number): Promise<SimpleTransaction> {
    const uid = userId || authService.getUserId() || 1;
    
    // SimpleTransaction을 백엔드 형식으로 변환
    const requestBody = {
      txDatetime: `${transaction.date}T00:00:00`,
      amount: transaction.type === 'EXPENSE' ? -Math.abs(transaction.amount) : Math.abs(transaction.amount),
      merchant: transaction.merchant,
      memo: null,
      source: 'MANUAL',
      userId: uid,
      methodId: null,
      categoryId: transaction.categoryId || null, // 카테고리 ID 전달
      receiptFileId: transaction.receiptFileId || null, // OCR receipt fileId 전달
    };

    const response = await fetch(API_BASE_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify(requestBody),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 거래 내역을 저장하는데 실패했습니다.`);
    }

    const data: Transaction = await response.json();
    return toSimpleTransaction(data);
  },

  /**
   * 거래 내역 수정
   */
  async updateTransaction(transaction: SimpleTransaction): Promise<void> {
    const requestBody = {
      txId: Number(transaction.id),
      txDatetime: `${transaction.date}T00:00:00`,
      amount: transaction.type === 'EXPENSE' ? -Math.abs(transaction.amount) : Math.abs(transaction.amount),
      merchant: transaction.merchant,
    };

    const response = await fetch(`${API_BASE_URL}/${transaction.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify(requestBody),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 거래 내역을 수정하는데 실패했습니다.`);
    }
  },

  /**
   * 거래 내역 삭제
   */
  async deleteTransaction(id: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/${id}`, {
      method: 'DELETE',
      headers: {
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 거래 내역을 삭제하는데 실패했습니다.`);
    }
  },

  /**
   * 텍스트로 거래 내역 입력 (자동 카테고리 분류)
   */
  async createTransactionFromText(input: {
    txDatetime?: string;
    amount: number;
    merchant: string;
    memo?: string;
    methodId?: number;
    categoryId?: number;
  }, userId?: number): Promise<Transaction> {
    const uid = userId || authService.getUserId() || 1;

    const response = await fetch('/api/receipts/text-input', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...getAuthHeaders(),
      },
      body: JSON.stringify({ ...input, userId: uid }),
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: 거래 내역을 저장하는데 실패했습니다.`);
    }

    return await response.json();
  },
};
