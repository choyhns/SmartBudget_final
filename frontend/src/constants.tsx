
import React from 'react';
import { SimpleTransaction } from './types';

export const CATEGORIES = {
  EXPENSE: ['식비', '교통', '쇼핑', '주거/통신', '금융/보험', '취미/여가', '의료/건강', '기타'],
  INCOME: ['급여', '보너스', '부업', '이자/배당', '중고판매', '기타']
};

export const INITIAL_TRANSACTIONS: SimpleTransaction[] = [
  { id: '1', date: '2024-05-15', type: 'EXPENSE', category: '식비', merchant: '스타벅스', amount: 5500 },
  { id: '2', date: '2024-05-14', type: 'EXPENSE', category: '교통', merchant: '카카오택시', amount: 12400 },
  { id: '3', date: '2024-05-13', type: 'INCOME', category: '급여', merchant: '회사 월급', amount: 3500000 },
  { id: '4', date: '2024-05-12', type: 'EXPENSE', category: '쇼핑', merchant: '무신사', amount: 89000 },
  { id: '5', date: '2024-05-11', type: 'EXPENSE', category: '주거/통신', merchant: 'SKT 요금', amount: 65000 },
  { id: '6', date: '2024-05-10', type: 'EXPENSE', category: '취미/여가', merchant: '넷플릭스', amount: 17000 },
  { id: '7', date: '2024-05-10', type: 'EXPENSE', category: '식비', merchant: '배달의민족', amount: 28000 },
  { id: '8', date: '2024-05-09', type: 'EXPENSE', category: '의료/건강', merchant: '올리브영 약국', amount: 12000 },
  { id: '9', date: '2024-05-08', type: 'INCOME', category: '중고판매', merchant: '당근마켓', amount: 45000 },
  { id: '10', date: '2024-05-07', type: 'EXPENSE', category: '금융/보험', merchant: '삼성화재', amount: 42000 },
  { id: '11', date: '2024-05-06', type: 'EXPENSE', category: '식비', merchant: '서점 카페', amount: 8000 },
  { id: '12', date: '2024-05-05', type: 'EXPENSE', category: '기타', merchant: '편의점', amount: 4500 },
];

export const UI_COLORS = {
  bg: 'bg-slate-950',
  surface: 'bg-slate-900/60 backdrop-blur-xl border border-slate-800',
  primary: 'bg-blue-600 hover:bg-blue-700 text-white',
  secondary: 'bg-slate-800 hover:bg-slate-700 text-slate-200',
  danger: 'bg-rose-500 hover:bg-rose-600 text-white',
  successText: 'text-emerald-400',
  dangerText: 'text-rose-400',
  warningText: 'text-amber-400',
};
