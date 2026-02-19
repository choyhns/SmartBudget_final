import React from 'react';
import { MenuTab } from '../types';
import logoImg from '../assets/img/로고.png';
import './Sidebar.css';

interface SidebarProps {
  activeTab: MenuTab;
  setActiveTab: (tab: MenuTab) => void;
  onClose?: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab, onClose }) => {
  const menuItems = [
    { id: 'dashboard', label: '대시보드', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
    { id: 'add', label: '거래 추가', icon: 'M12 4v16m8-8H4' },
    { id: 'ledger', label: '가계부', icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01' },
    { id: 'budget', label: '예산 / 목표', icon: 'M13 7h8m0 0v8m0-8l-8 8-4-4-6 6' },
    { id: 'recommendations', label: '금융상품 추천', icon: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
    { id: 'ai-report', label: 'AI 리포트', icon: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z' },
    { id: 'settings', label: '마이페이지', icon: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z' },
  ];

  return (
    <aside className="sidebar-card w-64 h-full flex flex-col sticky top-0">
      <div className="p-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <img src={logoImg} alt="SmartBudget" className="w-10 h-10 object-contain" />
          <span className="font-bold text-xl tracking-tight text-slate-50">SmartBudget</span>
        </div>
        {onClose && (
          <button onClick={onClose} className="md:hidden text-gray-400 hover:text-[#3775bb]">
            <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
      </div>

      <nav className="flex-1 flex flex-col py-2">
        {menuItems.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => setActiveTab(item.id as MenuTab)}
            className={`sidebar-element w-full flex items-center gap-3 text-left rounded-r ${activeTab === item.id ? 'active' : ''}`}
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className="h-5 w-5 shrink-0"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={item.icon} />
            </svg>
            <span>{item.label}</span>
          </button>
        ))}
      </nav>

      <div className="p-4 border-t border-slate-700">
        <div className="p-4 rounded-xl bg-black/20 border border-slate-700">
          <p className="text-xs text-gray-400 mb-1">프리미엄 멤버십</p>
          <p className="text-sm font-semibold text-slate-200 mb-3">AI가 관리하는 내 자산</p>
          <div className="h-1.5 w-full bg-slate-700 rounded-full overflow-hidden">
            <div className="h-full w-2/3 bg-[#004d92] rounded-full" />
          </div>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
