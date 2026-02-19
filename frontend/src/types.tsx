// 거래내역
export interface Transaction {
  txId: number;
  txDatetime: string;
  amount: number;  // 양수: 수입, 음수: 지출
  merchant: string;
  memo: string | null;
  source: string | null;
  createdAt: string;
  userId: number;
  methodId: number | null;
  categoryId: number | null;
  categoryName: string | null;
  methodName: string | null;
}

// 프론트엔드에서 사용하는 간소화된 거래내역 (호환성 유지)
export interface SimpleTransaction {
  id: string;
  date: string;
  type: 'INCOME' | 'EXPENSE';
  category: string;
  merchant: string;
  amount: number;
  categoryId?: number | null; // 선택적: 카테고리 ID
  receiptFileId?: number | null; // 선택적: OCR 업로드한 receipt fileId
}

// 예산
export interface Budget {
  budgetId: number;
  yearMonth: string;
  totalBudget: number;
  createdAt: string;
  updatedAt: string;
  userId: number;
}

export interface CategoryBudget {
  catBudgetId: number;
  yearMonth: string;
  budgetAmount: number;
  createdAt: string;
  userId: number;
  categoryId: number;
  categoryName: string;
}

export interface BudgetStatus {
  yearMonth: string;
  totalBudget: number | null;
  totalSpent: number;
  remainingBudget: number | null;
  budgetUsagePercent: number | null;
  categoryBudgets: CategoryBudget[];
}

// 저축 목표
export interface SavingGoal {
  goalId: number;
  goalTitle: string | null;
  goalAmount: number;
  startDate: string | null;
  targetDate: string | null;
  monthlyTarget: number | null;
  createdAt: string;
  userId: number;
  currentAmount: number;
  progressPercent: number;
  remainingMonths: number | null;
}

// 카테고리
export interface Category {
  categoryId: number;
  name: string;
  parentId: number | null;
  children?: Category[];
}

// 결제수단
export interface PaymentMethod {
  methodId: number;
  name: string;
}

// 인증
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserInfo;
}

export interface UserInfo {
  userId: number;
  email: string;
  name: string;
  photo: string;
}

// 리포트
export interface MonthlyReport {
  reportId: number;
  yearMonth: string;
  metricsJson: string;
  llmSummaryText: string;
  llmModel: string;
  createdAt: string;
  userId: number;
  isCurrentMonth: boolean;
}

export interface AIAnalysis {
  riskAssessment: string;
  topPatterns: string[];
  savingTips: string[];
  totalExpense?: number;
  totalIncome?: number;
  categoryExpenses?: Record<string, number>;
}

/** AI 리포트 추천 질문 카드 (tag = 카테고리, categoryLabel = 화면 표시용) */
export interface QuestionCard {
  cardId: number;
  questionText: string;
  tag: string;
  categoryLabel: string;
  sortOrder?: number;
}

// 추천
export interface Card {
  cardId: number;
  name: string;
  company: string;
  benefitsJson: string;
  /** LLM이 생성한 상세 혜택 설명 텍스트 (옵션) */
  benefitsDetailText?: string;
  /** 전월 실적(월 이용금액) 조건 요약 (옵션) */
  monthlyRequirement?: string;
  imageUrl?: string;
  link?: string;
  tags: string[];
}

export interface Product {
  productId: number;
  type: string;
  name: string;
  bank: string;
  rate: number;
  conditionsJson: string;
  tags: string[];
}

export interface Recommendation {
  recId: number;
  yearMonth: string;
  recType: 'CARD' | 'PRODUCT';
  itemId: number;
  score: number;
  reasonText: string;
  createdAt: string;
  userId: number;
  itemName: string;
  itemDetails: string;
}

// 예산/목표 인사이트 API 응답
export interface BudgetInsight {
  insight: string;
}

// 기존 호환성을 위한 타입
export interface BudgetGoal {
  monthlyBudget: number;
  savingsGoal: number;
  savingsTarget: number;
}

export type MenuTab = 'dashboard' | 'add' | 'ledger' | 'budget' | 'ai-report' | 'recommendations' | 'settings';
