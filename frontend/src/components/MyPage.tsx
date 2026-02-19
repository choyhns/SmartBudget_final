import React, { useState } from 'react';
import { UI_COLORS } from '../constants';
import { UserInfo } from '../types';
import { categoryService } from '../services/categoryService';

interface MyPageProps {
  onLogout: () => void;
  user?: UserInfo | null;
}

const MyPage: React.FC<MyPageProps> = ({ onLogout, user }) => {
  const [notifications, setNotifications] = useState({
    push: true,
    email: false,
    monthly: true,
  });
  const [isInitializing, setIsInitializing] = useState(false);
  const [initMessage, setInitMessage] = useState<string | null>(null);

  const handleInitCategories = async () => {
    if (!confirm('카테고리를 초기화하시겠습니까? 기존 카테고리가 있어도 누락된 항목만 추가됩니다.')) {
      return;
    }

    setIsInitializing(true);
    setInitMessage(null);

    try {
      await categoryService.initCategoriesFromTextFile(true);
      setInitMessage('✅ 카테고리 초기화가 완료되었습니다!');
      setTimeout(() => setInitMessage(null), 5000);
    } catch (error) {
      setInitMessage('❌ 카테고리 초기화에 실패했습니다: ' + (error instanceof Error ? error.message : '알 수 없는 오류'));
      setTimeout(() => setInitMessage(null), 5000);
    } finally {
      setIsInitializing(false);
    }
  };

  return (
    <div className="space-y-8">
      <h2 className="text-2xl font-bold text-white">마이페이지 / 설정</h2>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Profile Card */}
        <div className={`p-8 rounded-3xl text-center ${UI_COLORS.surface}`}>
          <div className="relative inline-block mb-6">
             <div className="w-24 h-24 rounded-3xl bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-4xl font-bold text-white border-4 border-slate-800 shadow-2xl">
               <img 
                src={user?.photo ?? ""}
                alt="profile"
                className="object-cover object-center"
                style={{borderRadius:'22%'}}
               />
             </div>
             <div className="absolute -bottom-2 -right-2 w-8 h-8 bg-blue-600 rounded-xl flex items-center justify-center border-4 border-slate-900">
                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 text-white" viewBox="0 0 20 20" fill="currentColor">
                  <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                </svg>
             </div>
          </div>
          <h3 className="text-xl font-bold text-white mb-1">
            {user?.name || '사용자'}
          </h3>
          <p className="text-slate-400 text-sm mb-6">{user?.email || 'user@example.com'}</p>
          <div className="flex gap-2">
             <div className="flex-1 bg-slate-950 p-3 rounded-2xl border border-slate-800">
                <p className="text-xs text-slate-500 mb-1">멤버십</p>
                <p className="font-bold text-blue-400">Free</p>
             </div>
             <div className="flex-1 bg-slate-950 p-3 rounded-2xl border border-slate-800">
                <p className="text-xs text-slate-500 mb-1">User ID</p>
                <p className="font-bold text-slate-200">{user?.userId || '-'}</p>
             </div>
          </div>
        </div>

        {/* Categories Setting */}
        <div className={`lg:col-span-2 p-8 rounded-3xl ${UI_COLORS.surface}`}>
           <h4 className="text-lg font-bold text-white mb-6">카테고리 커스터마이징</h4>
           <div className="space-y-6">
             {/* 카테고리 초기화 버튼 */}
             <div className="p-4 bg-slate-950/50 rounded-2xl border border-slate-800">
               <p className="text-sm font-bold text-slate-200 mb-2">카테고리 초기화</p>
               <p className="text-xs text-slate-500 mb-4">
                 텍스트 파일 기반의 계층 구조 카테고리를 DB에 추가합니다.
               </p>
               <button
                 onClick={handleInitCategories}
                 disabled={isInitializing}
                 className={`w-full py-3 rounded-xl font-bold transition-all ${
                   isInitializing 
                     ? 'bg-slate-700 text-slate-400 cursor-not-allowed' 
                     : 'bg-blue-600 hover:bg-blue-700 text-white'
                 }`}
               >
                 {isInitializing ? '초기화 중...' : '카테고리 초기화 실행'}
               </button>
               {initMessage && (
                 <p className={`mt-3 text-sm ${initMessage.startsWith('✅') ? 'text-emerald-400' : 'text-rose-400'}`}>
                   {initMessage}
                 </p>
               )}
             </div>

             <div>
               <p className="text-sm font-medium text-slate-400 mb-4">지출 카테고리</p>
               <div className="flex flex-wrap gap-2">
                 {['식비', '교통', '쇼핑', '주거/통신', '의료', '취미', '+ 추가'].map(c => (
                   <span key={c} className={`px-4 py-2 rounded-xl text-sm font-medium border ${c.startsWith('+') ? 'border-dashed border-slate-700 text-slate-500 cursor-pointer hover:border-slate-500' : 'border-slate-800 bg-slate-950 text-slate-300'}`}>
                     {c}
                   </span>
                 ))}
               </div>
             </div>
             
             <div>
                <p className="text-sm font-medium text-slate-400 mb-4">알림 설정</p>
                <div className="space-y-4">
                  {[
                    { id: 'push', label: '실시간 푸시 알림', desc: '거래 발생 시 즉시 앱 알림을 받습니다.' },
                    { id: 'email', label: '이메일 리포트', desc: '매주 재정 현황 요약을 메일로 받습니다.' },
                    { id: 'monthly', label: '월간 예산 초과 경고', desc: '예산의 80% 사용 시 미리 알려드립니다.' }
                  ].map((item) => (
                    <div key={item.id} className="flex items-center justify-between p-4 bg-slate-950/50 rounded-2xl border border-slate-800">
                      <div>
                        <p className="text-sm font-bold text-slate-200">{item.label}</p>
                        <p className="text-xs text-slate-500">{item.desc}</p>
                      </div>
                      <button 
                        onClick={() => setNotifications({...notifications, [item.id]: !notifications[item.id as keyof typeof notifications]})}
                        className={`w-12 h-6 rounded-full transition-colors relative ${notifications[item.id as keyof typeof notifications] ? 'bg-blue-600' : 'bg-slate-800'}`}
                      >
                        <div className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-all ${notifications[item.id as keyof typeof notifications] ? 'right-1' : 'left-1'}`}></div>
                      </button>
                    </div>
                  ))}
                </div>
             </div>
           </div>
        </div>

        <div className="lg:col-span-3">
          <button 
            onClick={onLogout}
            className={`w-full py-4 rounded-2xl font-bold transition-all shadow-xl shadow-rose-500/10 ${UI_COLORS.danger}`}
          >
            계정 로그아웃
          </button>
        </div>
      </div>
    </div>
  );
};

export default MyPage;
