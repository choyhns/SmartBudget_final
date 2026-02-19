
import React from 'react';
import { UI_COLORS } from '../constants';
import logoImg from '../assets/img/로고.png';

interface LandingProps {
  onStart: () => void;
  onLoginClick: () => void;
}

const Landing: React.FC<LandingProps> = ({ onStart, onLoginClick }) => {
  const features = [
    {
      title: '거래 자동 기록 (OCR)',
      desc: '영수증 촬영 한 번으로 복잡한 지출 내역을 자동으로 분류하고 입력합니다.',
      icon: 'M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z'
    },
    {
      title: '통합 가계부',
      desc: '수입과 지출을 직관적인 달력과 리스트 뷰로 한눈에 파악하세요.',
      icon: 'M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z'
    },
    {
      title: '예산 및 목표 관리',
      desc: '나만의 저축 목표를 설정하고 달성 현황을 시각적으로 확인하세요.',
      icon: 'M13 7h8m0 0v8m0-8l-8 8-4-4-6 6'
    },
    {
      title: 'AI 소비 분석 리포트',
      desc: 'Gemini AI가 당신의 지출 패턴을 분석해 맞춤형 절약 팁을 제안합니다.',
      icon: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z'
    }
  ];

  return (
    <div className="min-h-screen bg-slate-950 text-white selection:bg-blue-500 selection:text-white">
      {/* Header */}
      <header className="fixed top-0 left-0 right-0 z-50 bg-slate-950/50 backdrop-blur-md border-b border-white/5">
        <div className="max-w-7xl mx-auto px-6 h-20 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img src={logoImg} alt="SmartBudget" className="w-10 h-10 object-contain" />
            <span className="font-bold text-2xl tracking-tighter">SmartBudget</span>
          </div>
          <button 
            onClick={onLoginClick}
            className="px-6 py-2 rounded-xl text-sm font-bold bg-white/5 border border-white/10 hover:bg-white/10 transition-all"
          >
            로그인
          </button>
        </div>
      </header>

      {/* Hero Section */}
      <section className="relative pt-48 pb-32 px-6 overflow-hidden">
        {/* Background Glows */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[1000px] h-[600px] bg-blue-600/20 blur-[120px] rounded-full -z-10 pointer-events-none"></div>
        <div className="absolute bottom-0 right-0 w-[500px] h-[500px] bg-indigo-600/10 blur-[120px] rounded-full -z-10 pointer-events-none"></div>

        <div className="max-w-5xl mx-auto text-center">
          <div className="inline-block px-4 py-1.5 mb-8 rounded-full bg-blue-600/10 border border-blue-600/20 text-blue-400 text-sm font-bold tracking-wide uppercase">
            Next Generation Wealth Management
          </div>
          <h1 className="text-5xl md:text-7xl font-extrabold tracking-tighter mb-8 leading-[1.1]">
            영수증 한 장으로 시작하는<br />
            <span className="bg-gradient-to-r from-blue-400 to-indigo-500 bg-clip-text text-transparent italic">AI 자산관리</span>
          </h1>
          <p className="text-xl md:text-2xl text-slate-400 mb-12 max-w-2xl mx-auto font-light leading-relaxed">
            지출, 수입, 예산, 목표를 한 번에 관리하세요.<br className="hidden md:block" />
            당신의 금융 생활이 똑똑해집니다.
          </p>
          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <button 
              onClick={onStart}
              className={`px-10 py-5 rounded-2xl text-lg font-bold shadow-2xl shadow-blue-600/40 hover:scale-105 active:scale-95 transition-all ${UI_COLORS.primary}`}
            >
              무료로 시작하기
            </button>
            <button className="px-10 py-5 rounded-2xl text-lg font-bold bg-white/5 border border-white/10 hover:bg-white/10 transition-all">
              기능 미리보기
            </button>
          </div>
        </div>
      </section>

      {/* Features Grid */}
      <section className="py-32 px-6 bg-slate-900/30">
        <div className="max-w-7xl mx-auto">
          <div className="text-center mb-20">
            <h2 className="text-3xl md:text-5xl font-bold tracking-tight mb-4">금융 관리의 모든 것</h2>
            <p className="text-slate-500 text-lg">SmartBudget이 제공하는 특별한 기능들을 만나보세요.</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {features.map((feature, i) => (
              <div 
                key={i} 
                className="p-8 rounded-[32px] bg-slate-900/40 border border-white/5 hover:border-blue-500/30 hover:bg-slate-900/60 transition-all group"
              >
                <div className="w-14 h-14 rounded-2xl bg-blue-600/10 flex items-center justify-center text-blue-500 mb-6 group-hover:scale-110 transition-transform">
                  <svg xmlns="http://www.w3.org/2000/svg" className="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d={feature.icon} />
                  </svg>
                </div>
                <h3 className="text-xl font-bold mb-4">{feature.title}</h3>
                <p className="text-slate-500 leading-relaxed text-sm">{feature.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-20 px-6 border-t border-white/5 text-center">
        <div className="max-w-7xl mx-auto">
          <div className="flex items-center justify-center gap-3 mb-8">
            <img src={logoImg} alt="SmartBudget" className="w-8 h-8 object-contain" />
            <span className="font-bold text-xl tracking-tighter">SmartBudget</span>
          </div>
          <p className="text-slate-600 text-sm">© 2024 SmartBudget Inc. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
};

export default Landing;
