import React, { useState, useEffect, useRef } from 'react';
import { SimpleTransaction, AIAnalysis, QuestionCard } from '../types';
import { monthlyReportService } from '../services/monthlyReportService';
import { qaService } from '../services/qaService';
import { UI_COLORS } from '../constants';

interface AIReportProps {
  transactions: SimpleTransaction[];
  budget: number;
}

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  /** PR4: 응답에 포함된 경우 '추가 질문 제안'으로 노출 */
  followUpQuestion?: string;
}

const AIReport: React.FC<AIReportProps> = ({ transactions, budget }) => {
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState<AIAnalysis | null>(null);
  const [selectedYearMonth, setSelectedYearMonth] = useState<string>('');
  const [availableMonths, setAvailableMonths] = useState<string[]>([]);
  const [loadingMonths, setLoadingMonths] = useState(false);

  const [recommendedCards, setRecommendedCards] = useState<QuestionCard[]>([]);
  const [loadingCards, setLoadingCards] = useState(false);
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [loadingAnswer, setLoadingAnswer] = useState(false);
  const [customQuestion, setCustomQuestion] = useState('');
  const chatScrollContainerRef = useRef<HTMLDivElement>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const reportSummaryRef = useRef<HTMLDivElement>(null);
  const nextIdRef = useRef(0);

  useEffect(() => {
    loadAvailableMonths();
  }, []);

  useEffect(() => {
    if (selectedYearMonth) {
      loadReportForMonth(selectedYearMonth);
    } else {
      setReport(null);
      setRecommendedCards([]);
    }
  }, [selectedYearMonth]);

  // 채팅 컨테이너 내부만 맨 아래로 스크롤 (페이지 전체 스크롤 점프 방지)
  useEffect(() => {
    if (report && selectedYearMonth) {
      loadRecommendedCards(selectedYearMonth);
    } else {
      setRecommendedCards([]);
    }
  }, [report, selectedYearMonth]);

  // 리포트가 로드되면 '이번 달 전체 요약' 영역으로 스크롤 (요약을 먼저 보이게)
  useEffect(() => {
    if (report && reportSummaryRef.current) {
      reportSummaryRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, [report]);

  // 채팅 메시지가 추가되거나 답변 로딩 시에만 채팅 끝으로 스크롤 (환영 메시지 1개일 때는 스크롤하지 않음)
  useEffect(() => {
    if (chatMessages.length > 1 || loadingAnswer) {
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [chatMessages, loadingAnswer]);

  const loadAvailableMonths = async () => {
    setLoadingMonths(true);
    try {
      const reports = await monthlyReportService.getAllReports();
      const months = reports.map((r) => r.yearMonth).sort().reverse();
      setAvailableMonths(months);
      const currentReport = reports.find((r) => r.isCurrentMonth);
      if (currentReport) {
        setSelectedYearMonth(currentReport.yearMonth);
      } else if (months.length > 0) {
        setSelectedYearMonth(months[0]);
      }
    } catch (error) {
      console.error('월 목록 로드 실패:', error);
    } finally {
      setLoadingMonths(false);
    }
  };

  const loadReportForMonth = async (yearMonth: string) => {
    setLoading(true);
    setChatMessages([]);
    try {
      const monthlyReport = await monthlyReportService.getReportByYearMonth(yearMonth);
      if (monthlyReport) {
        const analysis = monthlyReportService.parseReportToAnalysis(monthlyReport);
        setReport(analysis);
        setChatMessages([
          {
            id: `welcome-${nextIdRef.current++}`,
            role: 'assistant',
            content: '위에서 이번 달 요약을 확인했어요. 아래 추천 질문을 탭하거나 직접 입력해서 더 궁금한 걸 물어보세요.',
          },
        ]);
      } else {
        setReport(null);
      }
    } catch (error) {
      console.error('리포트 로드 실패:', error);
      setReport(null);
    } finally {
      setLoading(false);
    }
  };

  const loadRecommendedCards = async (yearMonth: string) => {
    setLoadingCards(true);
    try {
      const cards = await monthlyReportService.getRecommendedCards(yearMonth, undefined, 5);
      setRecommendedCards(cards);
    } catch (error) {
      console.error('추천 카드 로드 실패:', error);
      setRecommendedCards([]);
    } finally {
      setLoadingCards(false);
    }
  };

  const generateCurrentMonthReport = async () => {
    setLoading(true);
    try {
      const monthlyReport = await monthlyReportService.generateCurrentMonthReport(budget);
      const analysis = monthlyReportService.parseReportToAnalysis(monthlyReport);
      setReport(analysis);
      setSelectedYearMonth(monthlyReport.yearMonth);
      await loadAvailableMonths();
      setChatMessages([
        {
          id: `welcome-${nextIdRef.current++}`,
          role: 'assistant',
          content: '위에서 이번 달 요약을 확인했어요. 아래 질문을 탭하거나 직접 입력해서 더 궁금한 걸 물어보세요.',
        },
      ]);
    } catch (error) {
      console.error('리포트 생성 실패:', error);
      alert('리포트를 생성하는데 실패했습니다. 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  };

  const getMonthDisplayName = (yearMonth: string) => {
    let year: string, month: string;
    if (yearMonth.includes('-')) {
      [year, month] = yearMonth.split('-');
    } else {
      year = yearMonth.substring(0, 4);
      month = yearMonth.substring(4, 6);
    }
    const monthNames = ['1월', '2월', '3월', '4월', '5월', '6월', '7월', '8월', '9월', '10월', '11월', '12월'];
    return `${year}년 ${monthNames[parseInt(month) - 1]}`;
  };

  const handleRecommendedCardClick = async (card: QuestionCard) => {
    if (!report || !selectedYearMonth) {
      alert('먼저 리포트를 생성해주세요.');
      return;
    }
    const question = card.questionText;
    const userMsg: ChatMessage = { id: `u-${nextIdRef.current++}`, role: 'user', content: question };
    setChatMessages((prev) => [...prev, userMsg]);
    setLoadingAnswer(true);

    try {
      await monthlyReportService.logCardClick(selectedYearMonth, card.cardId);
    } catch (e) {
      console.warn('Card click log failed', e);
    }

    try {
      const res = await monthlyReportService.askQuestion(selectedYearMonth, question);
      const assistantMsg: ChatMessage = {
        id: `a-${nextIdRef.current++}`,
        role: 'assistant',
        content: res.answer || '답변을 생성하지 못했어요.',
      };
      setChatMessages((prev) => [...prev, assistantMsg]);
    } catch (e: unknown) {
      const err = e as { message?: string };
      const assistantMsg: ChatMessage = {
        id: `a-${nextIdRef.current++}`,
        role: 'assistant',
        content: err?.message || '답변을 불러오는데 실패했어요. 다시 시도해주세요.',
      };
      setChatMessages((prev) => [...prev, assistantMsg]);
    } finally {
      setLoadingAnswer(false);
    }
  };

  const handleCustomSubmit = async () => {
    const q = customQuestion.trim();
    if (!q) return;
    if (!report || !selectedYearMonth) {
      alert('먼저 리포트를 생성한 뒤 질문해주세요.');
      return;
    }

    const userMsg: ChatMessage = { id: `u-${nextIdRef.current++}`, role: 'user', content: q };
    setChatMessages((prev) => [...prev, userMsg]);
    setCustomQuestion('');
    setLoadingAnswer(true);

    try {
      const res = await qaService.ask(q, selectedYearMonth);
      const answerText = res.answer ?? res.answerText ?? '답변을 생성하지 못했어요.';
      const assistantMsg: ChatMessage = {
        id: `a-${nextIdRef.current++}`,
        role: 'assistant',
        content: answerText,
        ...(res.followUpQuestion && { followUpQuestion: res.followUpQuestion }),
      };
      setChatMessages((prev) => [...prev, assistantMsg]);
    } catch (e: unknown) {
      const err = e as { message?: string };
      const assistantMsg: ChatMessage = {
        id: `a-${nextIdRef.current++}`,
        role: 'assistant',
        content: err?.message || '답변을 불러오는데 실패했어요. 다시 시도해주세요.',
      };
      setChatMessages((prev) => [...prev, assistantMsg]);
    } finally {
      setLoadingAnswer(false);
    }
  };

  const applyFollowUpSuggestion = (text: string) => {
    setCustomQuestion(text);
  };

  return (
    <div className="space-y-8">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-white mb-1">AI 지능형 리포트</h2>
          <p className="text-slate-400 text-sm">월별 소비 요약과 궁금한 걸 질문해보세요.</p>
        </div>
        <button
          onClick={generateCurrentMonthReport}
          disabled={loading}
          className={`flex items-center gap-2 px-8 py-4 rounded-xl font-bold shadow-2xl transition-all active:scale-95 disabled:opacity-50 ${UI_COLORS.primary} shadow-blue-600/30`}
        >
          {loading ? (
            <>
              <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
              </svg>
              분석 중...
            </>
          ) : (
            <>
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M11.3 1.047a1 1 0 01.897.95l1.046 20.93a1 1 0 01-1.994.1l-1.046-20.93a1 1 0 011.097-.951z" clipRule="evenodd" />
              </svg>
              이번 달 리포트 생성
            </>
          )}
        </button>
      </div>

      {availableMonths.length > 0 && (
        <div className={`p-4 rounded-xl ${UI_COLORS.surface} flex flex-wrap items-center gap-4`}>
          <span className="text-slate-300 font-medium">분석 월:</span>
          <select
            value={selectedYearMonth}
            onChange={(e) => setSelectedYearMonth(e.target.value)}
            disabled={loading || loadingMonths}
            className="px-4 py-2 bg-slate-900 border border-slate-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          >
            {availableMonths.map((m) => (
              <option key={m} value={m}>
                {getMonthDisplayName(m)}
              </option>
            ))}
          </select>
        </div>
      )}

      {!report && !loading && !loadingMonths && (
        <div className={`p-16 rounded-[40px] text-center ${UI_COLORS.surface} border-2 border-dashed border-slate-700`}>
          <div className="w-20 h-20 bg-slate-800 rounded-full flex items-center justify-center mx-auto mb-6 text-4xl">💬</div>
          <h3 className="text-xl font-bold text-white mb-2">분석할 준비가 되었어요</h3>
          <p className="text-slate-400 max-w-md mx-auto">
            {availableMonths.length > 0
              ? '위에서 월을 선택하거나, 상단 버튼으로 이번 달 리포트를 생성해보세요.'
              : '상단 버튼을 눌러 이번 달 소비를 분석한 뒤, 요약과 질문 카드로 궁금한 걸 확인하세요.'}
          </p>
        </div>
      )}

      {loading && (
        <div className="space-y-6">
          <div className="h-48 w-full bg-slate-900 rounded-3xl animate-pulse" />
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-40 bg-slate-900 rounded-3xl animate-pulse" />
            ))}
          </div>
        </div>
      )}

      {report && !loading && (
        <div className="space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">
          {/* 1. 소비·저축 인사이트 형식 (6개 섹션 한 블록) - 리포트 생성 후 이 영역이 보이도록 스크롤됨 */}
          <div ref={reportSummaryRef}>
            <h3 className="text-lg font-bold text-white mb-4">이번 달 전체 요약</h3>
            <div className={`p-8 rounded-3xl border border-blue-600/30 bg-slate-900/80 ${UI_COLORS.surface}`}>
              <h4 className="text-xl font-bold text-white mb-4">소비·저축 인사이트</h4>
              <div className="text-slate-200 whitespace-pre-wrap leading-relaxed">
                {report.riskAssessment || '분석 요약이 없습니다.'}
              </div>
            </div>
          </div>

          {/* 2. 챗봇형 Q&A */}
          <div className={`rounded-2xl ${UI_COLORS.surface} overflow-hidden flex flex-col`}>
            <div className="p-4 border-b border-slate-800">
              <h3 className="text-lg font-bold text-white">궁금한 걸 물어보세요</h3>
              <p className="text-slate-400 text-sm mt-1">질문 카드를 탭하거나 직접 입력해서 더 알아보세요.</p>
            </div>

            <div
              ref={chatScrollContainerRef}
              className="flex-1 min-h-[280px] max-h-[420px] overflow-y-auto p-4 space-y-4"
            >
              {chatMessages.map((m) => (
                <div
                  key={m.id}
                  className={`flex flex-col ${m.role === 'user' ? 'items-end' : 'items-start'}`}
                >
                  <div
                    className={`max-w-[85%] rounded-2xl px-4 py-3 ${
                      m.role === 'user'
                        ? 'bg-blue-600 text-white rounded-br-md'
                        : 'bg-slate-800/80 text-slate-200 border border-slate-700 rounded-bl-md'
                    }`}
                  >
                    <p className="text-sm whitespace-pre-line leading-relaxed">{m.content}</p>
                  </div>
                  {m.role === 'assistant' && m.followUpQuestion && (
                    <div className="mt-2 ml-0 max-w-[85%] rounded-xl px-3 py-2 bg-slate-800/60 border border-slate-600 border-dashed">
                      <p className="text-xs text-slate-400 mb-1.5">추가 질문 제안</p>
                      <p className="text-sm text-slate-300 mb-2">{m.followUpQuestion}</p>
                      <button
                        type="button"
                        onClick={() => applyFollowUpSuggestion(m.followUpQuestion!)}
                        className="text-xs font-medium text-blue-400 hover:text-blue-300"
                      >
                        이걸로 질문하기 →
                      </button>
                    </div>
                  )}
                </div>
              ))}
              {loadingAnswer && (
                <div className="flex justify-start">
                  <div className="bg-slate-800/80 border border-slate-700 rounded-2xl rounded-bl-md px-4 py-3 flex items-center gap-2 text-slate-400">
                    <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                    <span className="text-sm">답변 생성 중...</span>
                  </div>
                </div>
              )}
              <div ref={chatEndRef} />
            </div>

            <div className="p-4 border-t border-slate-800 space-y-4">
              {loadingCards ? (
                <div className="flex flex-wrap gap-2">
                  {[1, 2, 3, 4, 5].map((i) => (
                    <div key={i} className="h-10 w-32 rounded-xl bg-slate-800 animate-pulse" />
                  ))}
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {recommendedCards.map((card) => (
                    <button
                      key={card.cardId}
                      type="button"
                      onClick={() => handleRecommendedCardClick(card)}
                      disabled={loadingAnswer}
                      className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-sm font-medium transition-colors disabled:opacity-50 text-left"
                    >
                      <span className="shrink-0 px-1.5 py-0.5 rounded text-xs bg-slate-600 text-slate-300">
                        {card.categoryLabel}
                      </span>
                      <span className="min-w-0">{card.questionText}</span>
                    </button>
                  ))}
                </div>
              )}
              <div className="space-y-2">
                <p className="text-xs text-slate-400">
                  추가 질문 (기준 월: {selectedYearMonth ? getMonthDisplayName(selectedYearMonth) : '-'})
                </p>
                <div className="flex flex-col sm:flex-row gap-3">
                  <input
                    type="text"
                    value={customQuestion}
                    onChange={(e) => setCustomQuestion(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleCustomSubmit()}
                    placeholder="예: 식비가 왜 늘었는지, 전월 대비 요약해줘"
                    className="flex-1 px-4 py-3 bg-slate-950 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    disabled={loadingAnswer}
                  />
                  <button
                    onClick={handleCustomSubmit}
                    disabled={loadingAnswer || !customQuestion.trim()}
                    className={`px-5 py-3 rounded-xl font-bold disabled:opacity-50 shrink-0 ${UI_COLORS.primary}`}
                  >
                    {loadingAnswer ? '전송 중...' : '보내기'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AIReport;
