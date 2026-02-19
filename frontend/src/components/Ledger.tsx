
import React, { useState, useMemo } from 'react';
import { SimpleTransaction } from '../types';
import { UI_COLORS } from '../constants';

interface LedgerProps {
  transactions: SimpleTransaction[];
  onUpdate: (t: SimpleTransaction) => void;
  onDelete: (id: string) => void;
}

const Ledger: React.FC<LedgerProps> = ({ transactions, onUpdate, onDelete }) => {
  const [currentViewDate, setCurrentViewDate] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  const year = currentViewDate.getFullYear();
  const month = currentViewDate.getMonth();

  // Calendar Helpers
  const firstDayOfMonth = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  
  // 로컬 날짜를 YYYY-MM-DD 형식으로 변환하는 헬퍼 함수
  const formatLocalDate = (date: Date): string => {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  };
  
  const today = formatLocalDate(new Date());

  const calendarDays = useMemo(() => {
    const days = [];
    // Padding for first week
    for (let i = 0; i < firstDayOfMonth; i++) {
      days.push(null);
    }
    // Days of the month
    for (let i = 1; i <= daysInMonth; i++) {
      days.push(new Date(year, month, i) as Date);
    }
    return days;
  }, [year, month, firstDayOfMonth, daysInMonth]);

  const getDailyTotals = (dateStr: string) => {
    const dayTransactions = transactions.filter(t => t.date === dateStr);
    const income = dayTransactions.filter(t => t.type === 'INCOME').reduce((s, t) => s + t.amount, 0);
    const expense = dayTransactions.filter(t => t.type === 'EXPENSE').reduce((s, t) => s + t.amount, 0);
    return { income, expense };
  };

  const nextMonth = () => setCurrentViewDate(new Date(year, month + 1, 1));
  const prevMonth = () => setCurrentViewDate(new Date(year, month - 1, 1));

  // Filtering logic
  const filteredList = useMemo(() => {
    return transactions
      .filter(t => {
        const tDate = new Date(t.date);
        const isInMonth = tDate.getFullYear() === year && tDate.getMonth() === month;
        if (selectedDate) {
          return t.date === selectedDate;
        }
        return isInMonth;
      })
      .sort((a, b) => b.date.localeCompare(a.date));
  }, [transactions, year, month, selectedDate]);

  // Grouping by date
  const groupedTransactions = useMemo(() => {
    const groups: Record<string, SimpleTransaction[]> = {};
    filteredList.forEach(t => {
      if (!groups[t.date]) groups[t.date] = [];
      groups[t.date].push(t);
    });
    return Object.entries(groups).sort((a, b) => b[0].localeCompare(a[0]));
  }, [filteredList]);

  return (
    <div className="space-y-8 pb-20">
      <div className="flex flex-col md:flex-row items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-white">가계부 달력</h2>
          <p className="text-slate-400 text-sm">월간 흐름을 파악하고 일자별 내역을 확인하세요.</p>
        </div>
        
        <div className="flex items-center gap-4 bg-slate-900 p-1 rounded-xl border border-slate-800">
          <button onClick={prevMonth} className="p-2 hover:bg-slate-800 rounded-lg text-slate-400">
            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clipRule="evenodd" />
            </svg>
          </button>
          <span className="text-white font-bold min-w-[100px] text-center">
            {year}년 {month + 1}월
          </span>
          <button onClick={nextMonth} className="p-2 hover:bg-slate-800 rounded-lg text-slate-400">
            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
            </svg>
          </button>
        </div>
      </div>

      {/* Calendar Card */}
      <div className={`p-6 rounded-3xl ${UI_COLORS.surface}`}>
        <div className="grid grid-cols-7 gap-2 mb-4">
          {['일', '월', '화', '수', '목', '금', '토'].map((d, i) => (
            <div key={d} className={`text-center text-xs font-bold uppercase tracking-widest ${i === 0 ? 'text-rose-500' : i === 6 ? 'text-blue-500' : 'text-slate-500'}`}>
              {d}
            </div>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-2">
          {calendarDays.map((day, idx) => {
            if (!day) return <div key={`empty-${idx}`} className="aspect-square"></div>;
            
            const dateStr = formatLocalDate(day);
            const isSelected = selectedDate === dateStr;
            const isToday = dateStr === today;
            const { income, expense } = getDailyTotals(dateStr);

            return (
              <button
                key={dateStr}
                onClick={() => setSelectedDate(isSelected ? null : dateStr)}
                className={`aspect-square p-2 rounded-2xl flex flex-col justify-between transition-all border ${
                  isSelected 
                    ? 'bg-blue-600/20 border-blue-500 ring-2 ring-blue-500/20' 
                    : isToday 
                    ? 'bg-slate-800 border-slate-700' 
                    : 'bg-slate-900/40 border-slate-800/50 hover:border-slate-600'
                }`}
              >
                <span className={`text-xs font-bold ${isToday ? 'text-blue-400' : 'text-slate-400'}`}>
                  {day.getDate()}
                </span>
                <div className="space-y-0.5 text-[8px] md:text-[10px] text-right overflow-hidden">
                  {income > 0 && <p className="text-emerald-400 font-bold truncate">+{income.toLocaleString()}</p>}
                  {expense > 0 && <p className="text-rose-400 font-bold truncate">-{expense.toLocaleString()}</p>}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Transaction List */}
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <h3 className="text-xl font-bold text-white">
            {selectedDate ? `${selectedDate} 거래 내역` : '월간 거래 내역'}
          </h3>
          {selectedDate && (
            <button 
              onClick={() => setSelectedDate(null)}
              className="text-xs text-blue-400 hover:underline font-medium"
            >
              전체 보기
            </button>
          )}
        </div>

        <div className="space-y-8">
          {groupedTransactions.map(([date, items]) => (
            <div key={date} className="space-y-3">
              <div className="flex items-center gap-4">
                <div className="h-px flex-1 bg-slate-800"></div>
                <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">{date}</span>
                <div className="h-px flex-1 bg-slate-800"></div>
              </div>
              
              <div className={`overflow-hidden rounded-2xl ${UI_COLORS.surface}`}>
                <div className="divide-y divide-slate-800/50">
                  {items.map(item => (
                    <div key={item.id} className="p-4 flex items-center justify-between hover:bg-slate-800/30 transition-colors group">
                      <div className="flex items-center gap-4">
                        <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
                          item.type === 'INCOME' ? 'bg-emerald-500/10 text-emerald-500' : 'bg-rose-500/10 text-rose-500'
                        }`}>
                          {item.type === 'INCOME' ? (
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                              <path fillRule="evenodd" d="M14.707 12.707a1 1 0 01-1.414 0L10 9.414l-3.293 3.293a1 1 0 01-1.414-1.414l4-4a1 1 0 011.414 0l4 4a1 1 0 010 1.414z" clipRule="evenodd" />
                            </svg>
                          ) : (
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                              <path fillRule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clipRule="evenodd" />
                            </svg>
                          )}
                        </div>
                        <div>
                          <p className="text-sm font-bold text-white">{item.merchant}</p>
                          <p className="text-xs text-slate-500">{item.category}</p>
                        </div>
                      </div>
                      <div className="text-right flex items-center gap-4">
                        <span className={`text-sm font-black ${item.type === 'INCOME' ? 'text-emerald-400' : 'text-rose-400'}`}>
                          {item.type === 'INCOME' ? '+' : '-'} {item.amount.toLocaleString()}원
                        </span>
                        <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                           <button onClick={() => onDelete(item.id)} className="p-2 text-slate-500 hover:text-rose-400">
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ))}

          {groupedTransactions.length === 0 && (
            <div className={`p-20 text-center rounded-3xl ${UI_COLORS.surface} border-dashed`}>
              <p className="text-slate-500">해당 기간의 거래 내역이 없습니다.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Ledger;
