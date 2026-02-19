import React, { useMemo } from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import { SimpleTransaction } from '../types';
import { UI_COLORS } from '../constants';
import { authService } from '../services/authService';

interface DashboardProps {
  transactions: SimpleTransaction[];
  budget: number;
  onAddClick: () => void;
}

const Dashboard: React.FC<DashboardProps> = ({ transactions, budget, onAddClick }) => {
  const user = authService.getUser();
  const userName = user?.email?.split('@')[0] || '사용자';

  // 이번 달(YYYY-MM) 거래만 필터
  const currentMonthTransactions = useMemo(() => {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const prefix = `${year}-${month}`;
    return transactions.filter(t => t.date && t.date.startsWith(prefix));
  }, [transactions]);

  const currentMonthExpenses = useMemo(() => {
    return currentMonthTransactions
      .filter(t => t.type === 'EXPENSE')
      .reduce((sum, t) => sum + t.amount, 0);
  }, [currentMonthTransactions]);

  const hasBudget = budget > 0;

  const usagePercent = useMemo(() => {
    if (!hasBudget) return 0;
    return Math.min(Math.round((currentMonthExpenses / budget) * 100), 100);
  }, [currentMonthExpenses, budget, hasBudget]);

  const categoryData = useMemo(() => {
    const data: Record<string, number> = {};
    currentMonthTransactions
      .filter(t => t.type === 'EXPENSE')
      .forEach(t => {
        data[t.category] = (data[t.category] || 0) + t.amount;
      });
    return Object.entries(data).map(([name, value]) => ({ name, value }));
  }, [currentMonthTransactions]);

  const trendData = useMemo(() => {
    const last7Days = Array.from({ length: 7 }, (_, i) => {
      const d = new Date();
      d.setDate(d.getDate() - i);
      return d.toISOString().split('T')[0];
    }).reverse();

    return last7Days.map(date => {
      const amount = transactions
        .filter(t => t.date === date && t.type === 'EXPENSE')
        .reduce((sum, t) => sum + t.amount, 0);
      return { date: date.slice(5), amount };
    });
  }, [transactions]);

  // 소비패턴 기반 경고 메시지 (이번 달 지출 총액 카드 위에 표시)
  const spendingAlerts = useMemo(() => {
    const alerts: { type: 'error' | 'warning' | 'info'; message: string }[] = [];
    if (!hasBudget) return alerts;

    // 1) 예산 초과
    if (usagePercent >= 100) {
      alerts.push({ type: 'error', message: '이번 달 예산을 이미 초과했습니다. 지출을 점검해 보세요.' });
    } else if (usagePercent >= 80) {
      alerts.push({ type: 'warning', message: `예산의 ${usagePercent}%를 사용했습니다. 남은 기간 지출을 조절해 보세요.` });
    }

    // 2) 현재 페이스로 가면 월 예산 초과 가능성
    const now = new Date();
    const dayOfMonth = now.getDate();
    const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
    if (dayOfMonth >= 3 && usagePercent < 100) {
      const dailyAvg = currentMonthExpenses / dayOfMonth;
      const projectedTotal = dailyAvg * daysInMonth;
      if (projectedTotal > budget * 1.05) {
        alerts.push({
          type: 'warning',
          message: `현재 지출 페이스라면 월말에 예산을 초과할 수 있습니다. (예상: ₩${Math.round(projectedTotal).toLocaleString()})`,
        });
      }
    }

    // 3) 한 카테고리 집중 (50% 초과)
    if (currentMonthExpenses > 0 && categoryData.length > 0) {
      const top = categoryData[0];
      const pct = (top.value / currentMonthExpenses) * 100;
      if (pct > 50) {
        alerts.push({
          type: 'info',
          message: `'${top.name}' 지출이 전체의 ${Math.round(pct)}%를 차지합니다. 다른 항목과 균형을 확인해 보세요.`,
        });
      }
    }

    // 4) 최근 7일 중 최근 3일 지출이 이전 4일보다 50% 이상 많을 때
    if (trendData.length >= 7) {
      const recent3 = trendData.slice(-3).reduce((s, d) => s + d.amount, 0);
      const prior4 = trendData.slice(0, 4).reduce((s, d) => s + d.amount, 0);
      if (prior4 > 0 && recent3 >= prior4 * 1.5) {
        alerts.push({
          type: 'warning',
          message: '최근 3일 지출이 이전보다 크게 늘었습니다. 소비 패턴을 확인해 보세요.',
        });
      }
    }

    return alerts;
  }, [budget, hasBudget, usagePercent, currentMonthExpenses, categoryData, trendData]);

  const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];

  return (
    <div className="space-y-8">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-white mb-1">환영합니다, {userName}님</h1>
          <p className="text-slate-400">이번 달 재정 현황을 한눈에 확인하세요.</p>
        </div>
        <button 
          onClick={onAddClick}
          className={`flex items-center gap-2 px-6 py-3 rounded-xl font-semibold shadow-xl shadow-blue-600/20 transition-all active:scale-95 ${UI_COLORS.primary}`}
        >
          <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clipRule="evenodd" />
          </svg>
          거래 추가
        </button>
      </div>

      {/* 소비패턴 기반 경고 (이번 달 지출 총액 카드 위) */}
      {spendingAlerts.length > 0 && (
        <div className="space-y-2">
          {spendingAlerts.map((alert, idx) => (
            <div
              key={idx}
              role="alert"
              className={`flex items-start gap-3 px-4 py-3 rounded-xl border ${
                alert.type === 'error'
                  ? 'bg-rose-500/10 border-rose-500/30 text-rose-200'
                  : alert.type === 'warning'
                  ? 'bg-amber-500/10 border-amber-500/30 text-amber-200'
                  : 'bg-blue-500/10 border-blue-500/30 text-blue-200'
              }`}
            >
              {alert.type === 'error' && (
                <svg className="w-5 h-5 shrink-0 text-rose-400 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                </svg>
              )}
              {alert.type === 'warning' && (
                <svg className="w-5 h-5 shrink-0 text-amber-400 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                </svg>
              )}
              {alert.type === 'info' && (
                <svg className="w-5 h-5 shrink-0 text-blue-400 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                </svg>
              )}
              <span className="text-sm font-medium">{alert.message}</span>
            </div>
          ))}
        </div>
      )}

      {/* Hero Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className={`p-8 rounded-3xl ${UI_COLORS.surface}`}>
          <div className="flex items-center justify-between mb-6">
            <span className="text-slate-400 font-medium">이번 달 지출 총액</span>
            <span className="px-3 py-1 bg-rose-500/10 text-rose-400 text-xs font-bold rounded-full border border-rose-500/20">
              EXPENSE
            </span>
          </div>
          <div className="mb-6">
            <span className="text-4xl font-extrabold text-white">₩{currentMonthExpenses.toLocaleString()}</span>
            {hasBudget ? (
              <span className="text-slate-500 ml-2"> / ₩{budget.toLocaleString()}</span>
            ) : (
              <span className="text-slate-500 ml-2 text-sm">예산을 설정해 주세요</span>
            )}
          </div>
          {hasBudget && (
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-slate-400">예산 대비 사용률</span>
                <span className={`font-bold ${usagePercent > 80 ? 'text-rose-400' : 'text-blue-400'}`}>{usagePercent}%</span>
              </div>
              <div className="h-3 w-full bg-slate-800 rounded-full overflow-hidden">
                <div 
                  className={`h-full transition-all duration-1000 ${usagePercent > 80 ? 'bg-rose-500' : 'bg-blue-500'}`} 
                  style={{ width: `${usagePercent}%` }}
                ></div>
              </div>
            </div>
          )}
        </div>

        <div className={`p-8 rounded-3xl ${UI_COLORS.surface}`}>
          <div className="flex items-center justify-between mb-4">
            <span className="text-slate-400 font-medium">최근 7일 지출 트렌드</span>
            <div className="w-8 h-8 rounded-full bg-slate-800 flex items-center justify-center">
               <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 text-slate-400" viewBox="0 0 20 20" fill="currentColor">
                <path d="M2 11a1 1 0 011-1h2a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1v-5zM8 7a1 1 0 011-1h2a1 1 0 011 1v9a1 1 0 01-1 1H9a1 1 0 01-1-1V7zM14 4a1 1 0 011-1h2a1 1 0 011 1v12a1 1 0 01-1 1h-2a1 1 0 01-1-1V4z" />
              </svg>
            </div>
          </div>
          <div className="h-[140px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trendData}>
                <defs>
                  <linearGradient id="colorAmount" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#1e293b" />
                <XAxis dataKey="date" hide />
                <YAxis hide />
                <Tooltip 
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b', borderRadius: '12px' }}
                  itemStyle={{ color: '#f8fafc' }}
                  labelStyle={{ color: '#94a3b8' }}
                  formatter={(v: number | undefined) => v ? `₩${v.toLocaleString()}` : '₩0'}
                />
                <Area type="monotone" dataKey="amount" stroke="#3b82f6" strokeWidth={3} fillOpacity={1} fill="url(#colorAmount)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Category Breakdown */}
      <div className={`p-8 rounded-3xl ${UI_COLORS.surface}`}>
        <h3 className="text-xl font-bold text-white mb-6">카테고리별 지출 비중</h3>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-center">
          <div className="h-[240px]">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={categoryData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={100}
                  paddingAngle={5}
                  dataKey="value"
                >
                  {categoryData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip 
                   contentStyle={{ backgroundColor: '#0f172a', borderColor: '#1e293b', borderRadius: '12px' }}
                   itemStyle={{ color: '#f8fafc' }}
                   formatter={(v: number | undefined) => v ? `₩${v.toLocaleString()}` : '₩0'}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="space-y-4">
            {categoryData.length === 0 ? (
              <p className="text-slate-500 text-center py-8">아직 지출 데이터가 없습니다.</p>
            ) : (
              categoryData.slice(0, 5).map((item, idx) => (
                <div key={item.name} className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[idx % COLORS.length] }}></div>
                    <span className="text-slate-300 font-medium">{item.name}</span>
                  </div>
                  <div className="text-right">
                    <span className="text-slate-50 font-bold">₩{item.value.toLocaleString()}</span>
                    <p className="text-[10px] text-slate-500 uppercase">
                      {currentMonthExpenses > 0 ? Math.round((item.value / currentMonthExpenses) * 100) : 0}%
                    </p>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
