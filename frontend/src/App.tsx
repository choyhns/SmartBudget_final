import React, { useMemo, useState, useEffect } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";

import { SimpleTransaction,Transaction, BudgetGoal, MenuTab } from "./types";
import { INITIAL_TRANSACTIONS } from "./constants";
import { transactionService } from "./services/transactionService";
import { authService } from "./services/authService";
import { budgetService } from "./services/budgetService";
import logoImg from "./assets/img/로고.png";

import Sidebar from "./components/Sidebar";
import Dashboard from "./components/Dashboard";
import TransactionForm from "./components/TransactionForm";
import Ledger from "./components/Ledger";
import BudgetGoals from "./components/BudgetGoals";
import AIReport from "./components/AIReport";
import MyPage from "./components/MyPage";
import Landing from "./components/Landing";
import Login from "./components/Login";
import Recommendations from "./components/Recommendations";
import "./App.css";

// /app 이하에서 탭(path) ↔ MenuTab 매핑
function tabFromPath(pathname: string): MenuTab {
  if (pathname.includes("/app/add")) return "add";
  if (pathname.includes("/app/ledger")) return "ledger";
  if (pathname.includes("/app/budget")) return "budget";
  if (pathname.includes("/app/recommendations")) return "recommendations";
  if (pathname.includes("/app/ai-report")) return "ai-report";
  if (pathname.includes("/app/settings")) return "settings";
  return "dashboard";
}

// 로그인 가드
function RequireAuth({ authed, children }: { authed: boolean; children: React.ReactNode }) {
  if (!authed) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

const App: React.FC = () => {
  const nav = useNavigate();
  const location = useLocation();

  // 인증 상태 - localStorage에서 초기화
  const [authed, setAuthed] = useState(() => authService.isAuthenticated());
  const [user, setUser] = useState(() => authService.getUser());

  // 거래 내역 - DB에서 로드
  const [transactions, setTransactions] = useState<SimpleTransaction[]>([]);
  const [transactionsLoading, setTransactionsLoading] = useState(false);
  const [monthlyBudget, setMonthlyBudget] = useState<number | null>(null);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const qs = new URLSearchParams(location.search);
  const oauthNeedsProfile = sessionStorage.getItem('OAUTH_NEEDS_PROFILE') === '1';
  const hasOAuthParams = qs.has('accessToken') || qs.has('refreshToken') || qs.has('needsProfile');


  // 앱 시작 시 인증 상태 확인
  useEffect(() => {
    if (authService.isAuthenticated()) {
      setAuthed(true);
      setUser(authService.getUser());
    }
  }, []);

  // DB에서 거래 내역 및 이번 달 예산 로드
  useEffect(() => {
    if (authed) {
      loadTransactions();
      loadMonthlyBudget();
    }
  }, [authed]);

  const loadTransactions = async () => {
    setTransactionsLoading(true);
    try {
      const data = await transactionService.getAllTransactions();
      setTransactions(data);
    } catch (error) {
      console.error('거래 내역 로드 실패:', error);
      // 에러 발생 시 빈 배열
      setTransactions([]);
    } finally {
      setTransactionsLoading(false);
    }
  };

  const loadMonthlyBudget = async () => {
    try {
      const ym = budgetService.getCurrentYearMonth();
      const b = await budgetService.getBudget(ym);
      setMonthlyBudget(b?.totalBudget ?? null);
    } catch (error) {
      console.error('월 예산 로드 실패:', error);
      setMonthlyBudget(null);
    }
  };

  const activeTab = useMemo(() => tabFromPath(location.pathname), [location.pathname]);

  const addTransaction = async (t: Omit<SimpleTransaction, "id">) => {
    try {
      const newTransaction = await transactionService.createTransaction(t);
      setTransactions((prev) => [newTransaction, ...prev]);
    } catch (error) {
      console.error('거래 내역 저장 실패:', error);
      alert('거래 내역을 저장하는데 실패했습니다. 다시 시도해주세요.');
    }
  };

  const updateTransaction = async (updated: SimpleTransaction) => {
    try {
      await transactionService.updateTransaction(updated);
      setTransactions((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
    } catch (error) {
      console.error('거래 내역 수정 실패:', error);
      alert('거래 내역을 수정하는데 실패했습니다. 다시 시도해주세요.');
    }
  };

  const deleteTransaction = async (id: string) => {
    try {
      await transactionService.deleteTransaction(id);
      setTransactions((prev) => prev.filter((t) => t.id !== id));
    } catch (error) {
      console.error('거래 내역 삭제 실패:', error);
      alert('거래 내역을 삭제하는데 실패했습니다. 다시 시도해주세요.');
    }
  };

  // 로그인 성공 핸들러
  const handleLogin = () => {
    setAuthed(true);
    setUser(authService.getUser());
    nav("/app/dashboard", { replace: true });
  };

  // 로그아웃 핸들러
  const handleLogout = () => {
    authService.logout();
    sessionStorage.removeItem('OAUTH_NEEDS_PROFILE');
    
    setAuthed(false);
    setUser(null);
    setTransactions([]);
    nav("/", { replace: true });
  };

  // 사이드바 클릭 시 라우팅으로 이동
  const goTab = (tab: MenuTab) => {
    const map: Record<MenuTab, string> = {
      dashboard: "/app/dashboard",
      add: "/app/add",
      ledger: "/app/ledger",
      budget: "/app/budget",
      recommendations: "/app/recommendations",
      "ai-report": "/app/ai-report",
      settings: "/app/settings",
    };
    nav(map[tab]);
  };

  return (
    <Routes>
      {/* 랜딩 */}
      <Route
        path="/"
        element={
          authed 
            ? <Navigate to="/app/dashboard" replace />
            : <Landing onStart={() => nav("/login")} onLoginClick={() => nav("/login")} />
        }
      />

      {/* 로그인 */}
      <Route 
        path="/login" 
        element={
          (hasOAuthParams)
          ? <Login onLogin={handleLogin} onBack={() => nav("/")} />
          : authed 
            ? <Navigate to="/app/dashboard" replace />
            : <Login onLogin={handleLogin} onBack={() => nav("/")} />
        } 
      />

      {/* 앱(로그인 필요) */}
      <Route
        path="/app/*"
        element={
          <RequireAuth authed={authed}>
            <div className="grid-wrapper flex text-slate-50">
              {/* 그리드 배경 레이어 */}
              <div className="grid-background" aria-hidden />
              {/* Sidebar - Desktop */}
              <div className="hidden md:block relative z-10">
                <Sidebar
                  activeTab={activeTab}
                  setActiveTab={(tab) => goTab(tab)}
                />
              </div>

              {/* Sidebar - Mobile Overlay */}
              {isMobileMenuOpen && (
                <div className="fixed inset-0 z-50 md:hidden bg-slate-950/80 backdrop-blur-sm">
                  <div className="w-64 h-full">
                    <Sidebar
                      activeTab={activeTab}
                      setActiveTab={(tab) => {
                        goTab(tab);
                        setIsMobileMenuOpen(false);
                      }}
                      onClose={() => setIsMobileMenuOpen(false)}
                    />
                  </div>
                </div>
              )}

              {/* Main Content */}
              <main className="flex-1 flex flex-col min-w-0 relative z-10">
                {/* Topbar Mobile Only */}
                <header className="md:hidden flex items-center justify-between p-4 border-b border-slate-800 bg-slate-900/50">
                  <div className="flex items-center gap-2">
                    <img src={logoImg} alt="SmartBudget" className="w-8 h-8 object-contain" />
                    <span className="font-bold text-lg tracking-tight">SmartBudget</span>
                  </div>
                  <button onClick={() => setIsMobileMenuOpen(true)} className="p-2 text-slate-400">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16m-7 6h7" />
                    </svg>
                  </button>
                </header>

                <div className="flex-1 overflow-y-auto p-4 md:p-8 lg:p-12">
                  <div className="max-w-6xl mx-auto animate-in fade-in slide-in-from-bottom-4 duration-500">
                    <Routes>
                      <Route index element={<Navigate to="dashboard" replace />} />

                      <Route
                        path="dashboard"
                        element={
                          <Dashboard
                            transactions={transactions}
                            budget={monthlyBudget ?? 0}
                            onAddClick={() => nav("/app/add")}
                          />
                        }
                      />
                      <Route
                        path="add"
                        element={
                          <TransactionForm
                            onSave={(t) => {
                              addTransaction(t);
                              nav("/app/dashboard");
                            }}
                            onCancel={() => nav("/app/dashboard")}
                          />
                        }
                      />
                      <Route
                        path="ledger"
                        element={<Ledger transactions={transactions} onUpdate={updateTransaction} onDelete={deleteTransaction} />}
                      />
                      <Route
                        path="budget"
                        element={<BudgetGoals />}
                      />
                      <Route
                        path="recommendations"
                        element={<Recommendations transactions={transactions} />}
                      />
                      <Route
                        path="ai-report"
                        element={<AIReport transactions={transactions} budget={monthlyBudget ?? 0} />}
                      />
                      <Route
                        path="settings"
                        element={<MyPage onLogout={handleLogout} user={user} />}
                      />

                      {/* 알 수 없는 경로 */}
                      <Route path="*" element={<Navigate to="dashboard" replace />} />
                    </Routes>
                  </div>
                </div>
              </main>
            </div>
          </RequireAuth>
        }
      />

      {/* 전체 404 */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export default App;
