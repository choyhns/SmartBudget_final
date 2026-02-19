import React, { useState, useEffect, useCallback } from 'react';
import { Budget, CategoryBudget, BudgetStatus, SavingGoal } from '../types';
import { UI_COLORS } from '../constants';
import { budgetService } from '../services/budgetService';
import { savingGoalService } from '../services/savingGoalService';
import { categoryService } from '../services/categoryService';
import { Category } from '../types';

const BudgetGoals: React.FC = () => {
  const [yearMonth, setYearMonth] = useState(() => budgetService.getCurrentYearMonth());
  const [budget, setBudget] = useState<Budget | null>(null);
  const [categoryBudgets, setCategoryBudgets] = useState<CategoryBudget[]>([]);
  const [savingGoals, setSavingGoals] = useState<SavingGoal[]>([]);
  const [status, setStatus] = useState<BudgetStatus | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 월별 예산·카테고리 예산·저축 목표·현황 로드
  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [b, cb, sg, st] = await Promise.all([
        budgetService.getBudget(yearMonth).then(b => b ?? null),
        budgetService.getCategoryBudgets(yearMonth),
        savingGoalService.getAllGoals(),
        budgetService.getBudgetStatus(yearMonth).catch(() => null),
      ]);
      setBudget(b);
      setCategoryBudgets(cb);
      setSavingGoals(sg);
      setStatus(st);
    } catch (e) {
      setError(e instanceof Error ? e.message : '데이터를 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  }, [yearMonth]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    categoryService.getAllCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  const totalBudget = budget?.totalBudget ?? 0;
  const totalSpent = status?.totalSpent ?? 0;
  const displayYm = yearMonth.length === 6 ? `${yearMonth.slice(0, 4)}년 ${yearMonth.slice(4, 6)}월` : yearMonth;

  // 월 예산 설정
  const [monthlyBudgetEdit, setMonthlyBudgetEdit] = useState('');
  const [savingMonthly, setSavingMonthly] = useState(false);
  const handleSetMonthlyBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    const v = parseInt(monthlyBudgetEdit, 10);
    if (isNaN(v) || v < 0) return;
    setSavingMonthly(true);
    try {
      const b = await budgetService.setMonthlyBudget(yearMonth, v);
      setBudget(b);
      setMonthlyBudgetEdit('');
    } finally {
      setSavingMonthly(false);
    }
  };

  // 카테고리별 예산 설정
  const [catCategoryId, setCatCategoryId] = useState<number | ''>('');
  const [catAmount, setCatAmount] = useState('');
  const [savingCat, setSavingCat] = useState(false);
  const handleSetCategoryBudget = async (e: React.FormEvent) => {
    e.preventDefault();
    const cid = typeof catCategoryId === 'number' ? catCategoryId : parseInt(String(catCategoryId), 10);
    const v = parseInt(catAmount, 10);
    if (isNaN(cid) || isNaN(v) || v < 0) return;
    setSavingCat(true);
    try {
      const cb = await budgetService.setCategoryBudget(yearMonth, cid, v);
      const categoryName = categories.find(c => c.categoryId === cid)?.name ?? cb.categoryName ?? '';
      const item = { ...cb, categoryName };
      setCategoryBudgets(prev => {
        const idx = prev.findIndex(x => x.categoryId === cid);
        if (idx >= 0) {
          const next = [...prev];
          next[idx] = item;
          return next;
        }
        return [...prev, item];
      });
      setCatCategoryId('');
      setCatAmount('');
    } finally {
      setSavingCat(false);
    }
  };

  // 저축 목표 추가
  const [goalTitle, setGoalTitle] = useState('');
  const [goalAmount, setGoalAmount] = useState('');
  const [savingGoal, setSavingGoal] = useState(false);
  const handleCreateGoal = async (e: React.FormEvent) => {
    e.preventDefault();
    const amount = parseInt(goalAmount, 10);
    if (isNaN(amount) || amount <= 0) return;
    setSavingGoal(true);
    try {
      const g = await savingGoalService.createGoal(
        { goalTitle: goalTitle.trim() || undefined, goalAmount: amount }
      );
      setSavingGoals(prev => [...prev, g]);
      setGoalTitle('');
      setGoalAmount('');
    } finally {
      setSavingGoal(false);
    }
  };

  const handleUpdateGoal = async (goalId: number, updates: Partial<SavingGoal>) => {
    try {
      const g = await savingGoalService.updateGoal(goalId, updates);
      setSavingGoals(prev => prev.map(x => (x.goalId === goalId ? g : x)));
    } catch (e) {
      console.error(e);
    }
  };

  // 목표에 저축 금액 넣기 (저축 추가)
  const [addAmountByGoalId, setAddAmountByGoalId] = useState<Record<number, string>>({});
  const [addingSavingGoalId, setAddingSavingGoalId] = useState<number | null>(null);
  const handleAddSaving = async (e: React.FormEvent, goalId: number) => {
    e.preventDefault();
    const raw = addAmountByGoalId[goalId]?.trim();
    const amount = raw ? parseInt(raw.replace(/,/g, ''), 10) : 0;
    if (isNaN(amount) || amount <= 0) return;
    setAddingSavingGoalId(goalId);
    try {
      await savingGoalService.addSaving(goalId, amount);
      setAddAmountByGoalId(prev => ({ ...prev, [goalId]: '' }));
      const updated = await savingGoalService.getAllGoals();
      setSavingGoals(updated);
    } catch (err) {
      console.error(err);
      alert('저축 추가에 실패했습니다. 다시 시도해 주세요.');
    } finally {
      setAddingSavingGoalId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[200px]">
        <p className="text-slate-400">예산·목표 데이터를 불러오는 중...</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <h2 className="text-2xl font-bold text-white">예산 및 목표 관리</h2>
        <div className="flex items-center gap-2">
          <label className="text-slate-400 text-sm">대상 월</label>
          <input
            type="month"
            value={yearMonth.length === 6 ? `${yearMonth.slice(0, 4)}-${yearMonth.slice(4, 6)}` : yearMonth}
            onChange={e => {
              const v = e.target.value.replace('-', '').trim();
              if (v.length === 6) setYearMonth(v);
            }}
            className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white"
          />
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* 월 예산 */}
        <div className={`p-8 rounded-3xl flex flex-col justify-between ${UI_COLORS.surface}`}>
          <div>
            <div className="w-12 h-12 rounded-2xl bg-blue-500/20 flex items-center justify-center text-blue-500 mb-4">
              <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h3 className="text-slate-400 font-medium mb-1">월 예산 ({displayYm})</h3>
            <p className="text-3xl font-bold text-white mb-4">₩{(totalBudget || 0).toLocaleString()}</p>
          </div>
          <form onSubmit={handleSetMonthlyBudget} className="flex gap-2 mt-4">
            <input
              type="number"
              min={0}
              placeholder="월 예산 입력"
              value={monthlyBudgetEdit}
              onChange={e => setMonthlyBudgetEdit(e.target.value)}
              className="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-4 py-2 text-white"
            />
            <button type="submit" disabled={savingMonthly} className={`px-4 py-2 rounded-xl font-medium ${UI_COLORS.primary} disabled:opacity-50`}>
              {savingMonthly ? '저장 중…' : '설정'}
            </button>
          </form>
        </div>

        {/* 예산 대비 사용 */}
        <div className={`col-span-1 lg:col-span-2 p-8 rounded-3xl ${UI_COLORS.surface}`}>
          <h3 className="text-xl font-bold text-white mb-4">예산 대비 사용 현황</h3>
          <div className="flex items-end justify-between mb-4">
            <div>
              <p className="text-sm text-slate-400 mb-1">총 지출</p>
              <p className="text-3xl font-extrabold text-blue-400">₩{totalSpent.toLocaleString()}</p>
            </div>
            <div className="text-right">
              <p className="text-sm text-slate-400 mb-1">월 예산</p>
              <p className="text-xl font-bold text-slate-200">₩{totalBudget.toLocaleString()}</p>
            </div>
          </div>
          {status?.budgetUsagePercent != null && totalBudget > 0 && (
            <div className="space-y-2">
              <div className="flex justify-between text-xs font-bold uppercase tracking-wider">
                <span className="text-slate-500">사용률</span>
                <span className={status.budgetUsagePercent > 100 ? 'text-rose-400' : 'text-blue-400'}>
                  {status.budgetUsagePercent.toFixed(1)}%
                </span>
              </div>
              <div className="h-4 w-full bg-slate-800 rounded-full overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all ${status.budgetUsagePercent > 100 ? 'bg-rose-500' : 'bg-gradient-to-r from-blue-600 to-indigo-400'}`}
                  style={{ width: `${Math.min(status.budgetUsagePercent, 100)}%` }}
                />
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 카테고리별 예산 */}
      <div className={`p-8 rounded-3xl ${UI_COLORS.surface}`}>
        <h3 className="text-xl font-bold text-white mb-4">카테고리별 예산</h3>
        {categoryBudgets.length > 0 && (
          <ul className="space-y-2 mb-6">
            {categoryBudgets.map(cb => (
              <li key={cb.catBudgetId} className="flex items-center justify-between py-2 border-b border-slate-700/50">
                <span className="text-slate-200">{cb.categoryName}</span>
                <span className="text-slate-400">₩{(cb.budgetAmount || 0).toLocaleString()}</span>
              </li>
            ))}
          </ul>
        )}
        <form onSubmit={handleSetCategoryBudget} className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[120px]">
            <label className="block text-sm text-slate-400 mb-1">카테고리</label>
            <select
              value={catCategoryId === '' ? '' : catCategoryId}
              onChange={e => setCatCategoryId(e.target.value === '' ? '' : parseInt(e.target.value, 10))}
              className="w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-2 text-white"
            >
              <option value="">선택</option>
              {categories.map(c => (
                <option key={c.categoryId} value={c.categoryId}>{c.name}</option>
              ))}
            </select>
          </div>
          <div className="w-40">
            <label className="block text-sm text-slate-400 mb-1">예산 금액</label>
            <input
              type="number"
              min={0}
              placeholder="금액"
              value={catAmount}
              onChange={e => setCatAmount(e.target.value)}
              className="w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-2 text-white"
            />
          </div>
          <button type="submit" disabled={savingCat} className={`px-4 py-2 rounded-xl font-medium ${UI_COLORS.primary} disabled:opacity-50`}>
            {savingCat ? '저장 중…' : '추가/수정'}
          </button>
        </form>
      </div>

      {/* 저축 목표 */}
      <div className={`p-8 rounded-3xl ${UI_COLORS.surface}`}>
        <h3 className="text-xl font-bold text-white mb-4">저축 목표</h3>
        {savingGoals.length > 0 && (
          <ul className="space-y-4 mb-6">
            {savingGoals.map(g => (
              <li key={g.goalId} className="py-4 border-b border-slate-700/50 last:border-0">
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium text-white">{g.goalTitle || '저축 목표'}</span>
                  <span className="text-slate-400">₩{(g.currentAmount ?? 0).toLocaleString()} / ₩{(g.goalAmount ?? 0).toLocaleString()}</span>
                </div>
                <div className="h-2 bg-slate-800 rounded-full overflow-hidden mb-3">
                  <div
                    className="h-full bg-gradient-to-r from-blue-600 to-indigo-400 rounded-full"
                    style={{ width: `${Math.min(g.progressPercent ?? 0, 100)}%` }}
                  />
                </div>
                <form onSubmit={e => handleAddSaving(e, g.goalId)} className="flex flex-wrap items-center gap-2">
                  <input
                    type="text"
                    inputMode="numeric"
                    placeholder="넣을 금액"
                    value={addAmountByGoalId[g.goalId] ?? ''}
                    onChange={e => setAddAmountByGoalId(prev => ({ ...prev, [g.goalId]: e.target.value }))}
                    className="w-32 bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-white placeholder-slate-500"
                  />
                  <button
                    type="submit"
                    disabled={addingSavingGoalId === g.goalId}
                    className="px-3 py-1.5 rounded-lg text-sm font-medium bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-50"
                  >
                    {addingSavingGoalId === g.goalId ? '저장 중…' : '저축 추가'}
                  </button>
                </form>
              </li>
            ))}
          </ul>
        )}
        <form onSubmit={handleCreateGoal} className="flex flex-wrap items-end gap-3">
          <div className="w-48">
            <label className="block text-sm text-slate-400 mb-1">목표 제목</label>
            <input
              type="text"
              placeholder="예: 내 집 마련"
              value={goalTitle}
              onChange={e => setGoalTitle(e.target.value)}
              className="w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-2 text-white"
            />
          </div>
          <div className="w-40">
            <label className="block text-sm text-slate-400 mb-1">목표 금액</label>
            <input
              type="number"
              min={1}
              placeholder="금액"
              value={goalAmount}
              onChange={e => setGoalAmount(e.target.value)}
              className="w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-2 text-white"
            />
          </div>
          <button type="submit" disabled={savingGoal} className={`px-4 py-2 rounded-xl font-medium ${UI_COLORS.primary} disabled:opacity-50`}>
            {savingGoal ? '저장 중…' : '목표 추가'}
          </button>
        </form>
      </div>
    </div>
  );
};

export default BudgetGoals;
