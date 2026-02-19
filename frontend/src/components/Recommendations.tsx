import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Recommendation, SimpleTransaction } from '../types';
import { UI_COLORS } from '../constants';
import { recommendationService } from '../services/recommendationService';

/** 카드 혜택 JSON → 사용자용 설명 문구 */
function formatCardBenefits(benefitsJson: string): string {
  if (!benefitsJson?.trim()) return '혜택 정보가 없습니다.';
  try {
    const o = JSON.parse(benefitsJson);
    if (typeof o.summary === 'string' && o.summary.trim()) return o.summary.trim();
    if (Array.isArray(o.benefits) && o.benefits.length > 0) {
      return o.benefits
        .map((b: { category?: string; type?: string; value?: number }) => {
          const cat = b.category || '';
          if (b.type === 'percent' && b.value != null) return `${cat} ${b.value}% 캐시백`;
          if (b.type === 'fixed' && b.value != null) return `${cat} ${b.value.toLocaleString()}원 할인`;
          return cat || '혜택';
        })
        .filter(Boolean)
        .join(', ');
    }
  } catch {
    // JSON이 아니면 그대로 반환 (이미 설명문인 경우)
    return benefitsJson;
  }
  return benefitsJson;
}

interface RecommendationsProps {
  transactions: SimpleTransaction[];
}

const SHINHAN_HOME_URL = 'https://www.shinhancard.com/pconts/html/main.html';

const Recommendations: React.FC<RecommendationsProps> = ({ transactions }) => {
  const navigate = useNavigate();
  const [cards, setCards] = useState<Card[]>([]);
  const [recommendedCards, setRecommendedCards] = useState<Card[]>([]);
  const [recs, setRecs] = useState<Recommendation[]>([]);
  const [qaOptions, setQaOptions] = useState<Array<{ id: string; title: string; targetType: string; needsItemSelection: boolean }>>([]);
  const [selectedQa, setSelectedQa] = useState<string | null>(null);
  const [selectedCardId, setSelectedCardId] = useState<number | null>(null);
  const [qaAnswer, setQaAnswer] = useState<string>('');
  const [qaLoading, setQaLoading] = useState(false);
  const [cardReasons, setCardReasons] = useState<Record<number, string>>({});
  const [cardReasonsLoading, setCardReasonsLoading] = useState<Record<number, boolean>>({});
  const [customQuestionOpen, setCustomQuestionOpen] = useState(false);
  const [customQuestionText, setCustomQuestionText] = useState('');
  const [lastCustomQuestion, setLastCustomQuestion] = useState('');
  const [marqueePaused, setMarqueePaused] = useState(false);
  const [hoveredBannerCardKey, setHoveredBannerCardKey] = useState<string | null>(null);
  const [bannerCarouselReady, setBannerCarouselReady] = useState(false);
  const [carouselOffset, setCarouselOffset] = useState(0);
  const [carouselRotation, setCarouselRotation] = useState(0);
  const [carouselDragging, setCarouselDragging] = useState(false);
  const carouselDragStartX = useRef(0);
  const carouselDragStartRotation = useRef(0);
  const carouselDragMoved = useRef(false);
  const carouselRotationBaseTime = useRef(0);
  const carouselRotationBaseAngle = useRef(0);
  const carouselRotationCurrent = useRef(0);
  const carouselDraggingRef = useRef(false);
  const carouselHoverPausedRef = useRef(false);

  // 1. Calculate top spending categories (최근 3개월만)
  const topCategories = useMemo(() => {
    const now = new Date();
    const threeMonthsAgo = new Date(now.getFullYear(), now.getMonth() - 3, now.getDate());
    
    const data: Record<string, number> = {};
    transactions
      .filter(t => {
        if (t.type !== 'EXPENSE') return false;
        const txDate = new Date(t.date);
        return txDate >= threeMonthsAgo;
      })
      .forEach(t => {
        data[t.category] = (data[t.category] || 0) + t.amount;
      });
    return Object.entries(data)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([name, amount]) => ({ name, amount }));
  }, [transactions]);

  const { hasAnyExpense, lastExpenseDate, isExpenseOlderThan3Months } = useMemo(() => {
    const expenseDates = transactions
      .filter((t) => t.type === 'EXPENSE')
      .map((t) => new Date(t.date))
      .filter((d) => !Number.isNaN(d.getTime()));

    const hasAny = expenseDates.length > 0;
    const last = hasAny ? new Date(Math.max(...expenseDates.map((d) => d.getTime()))) : null;

    const now = new Date();
    const threeMonthsAgo = new Date(now.getFullYear(), now.getMonth() - 3, now.getDate());
    const olderThan3Months = Boolean(last && last < threeMonthsAgo);

    return { hasAnyExpense: hasAny, lastExpenseDate: last, isExpenseOlderThan3Months: olderThan3Months };
  }, [transactions]);

  // 최근 3개월 데이터 존재 여부
  const has3MonthData = useMemo(() => {
    const now = new Date();
    const threeMonthsAgo = new Date(now.getFullYear(), now.getMonth() - 3, now.getDate());
    return transactions.some(t => {
      const txDate = new Date(t.date);
      return txDate >= threeMonthsAgo && t.type === 'EXPENSE';
    });
  }, [transactions]);

  useEffect(() => {
    (async () => {
      const [c, recList, q, recCards] = await Promise.all([
        recommendationService.getAllCards(),
        recommendationService.getRecommendations(),
        recommendationService.getQaOptions(),
        recommendationService.getRecommendedCards(),
      ]);
      setCards(c ?? []);
      setRecs(recList ?? []);
      setQaOptions(q ?? []);
      setRecommendedCards(recCards ?? []);
      const firstCardId = c?.[0]?.cardId ?? null;
      setSelectedCardId(firstCardId);
      
      // 5초 로딩 상태
      setTimeout(() => {
        setBannerCarouselReady(true);
      }, 5000);
    })();
  }, []);

  // 카드 목록이 바뀌었을 때 Q&A 선택 카드 동기화: 없거나 목록에 없으면 첫 카드로 설정
  useEffect(() => {
    if (cards.length === 0) return;
    const currentValid = selectedCardId != null && cards.some((c) => c.cardId === selectedCardId);
    if (!currentValid) setSelectedCardId(cards[0].cardId);
  }, [cards]);

  // 지출 데이터·월별 분석 없음 = 처음 가입 상태
  const hasSpendingOrAnalysis = topCategories.length > 0 || (recs != null && recs.length > 0);
  // 추천 섹션에 보여줄 카드: 지출/분석 있으면 API 추천 최대 3장, 없으면 빈 배열(배너만 표시)
  const displayCards = hasSpendingOrAnalysis
    ? (recommendedCards.length > 0 
        ? recommendedCards.slice(0, 3) // 최대 3개로 제한
        : cards.slice(0, 3))
    : [];

  useEffect(() => {
    if (displayCards.length === 0) return;
    // 배치 API로 한 번에 조회 (AI 요약 설명 우선)
    const cardIds = displayCards.map((c) => c.cardId);
    cardIds.forEach((id) => setCardReasonsLoading((prev) => ({ ...prev, [id]: true })));
    recommendationService.getCardSuitableReasonsBatch(cardIds).then(({ reasons }) => {
      setCardReasons((prev) => ({ ...prev, ...reasons }));
      cardIds.forEach((id) => setCardReasonsLoading((prev) => ({ ...prev, [id]: false })));
    }).catch(() => {
      cardIds.forEach((id) => setCardReasonsLoading((prev) => ({ ...prev, [id]: false })));
    });
  }, [displayCards.length, displayCards.map((c) => c.cardId).join(',')]);

  // 배너용: 이미지 있는 카드만, 이미지 URL 기준 중복 제거 후 랜덤 셔플 (같은 이미지가 다른 카드명으로 나오지 않도록)
  const bannerCards = useMemo(() => {
    const url = (c: Card): string | undefined => c.imageUrl ?? (c as { image_url?: string }).image_url;
    const withImage = cards.filter((c) => url(c));
    const seen = new Set<string>();
    const uniqueByImage = withImage.filter((c) => {
      const u = url(c);
      if (!u || seen.has(u)) return false;
      seen.add(u);
      return true;
    });
    const shuffled = [...uniqueByImage].sort(() => Math.random() - 0.5);
    return shuffled.slice(0, 24).map((c) => ({ ...c, imageUrl: url(c) ?? '' }));
  }, [cards]);

  const cardLink = (c?: Card | null): string => {
    const link = c?.link ?? (c as unknown as { link?: string } | undefined)?.link;
    return link && link.trim() ? link.trim() : SHINHAN_HOME_URL;
  };

  // 3D 캐러셀: 20초마다 전체 갱신 (슬롯 고정 key로 리마운트 없이 교체 → 깜빡임 방지)
  useEffect(() => {
    if (bannerCards.length === 0) return;
    const interval = setInterval(() => {
      setCarouselOffset((prev) => (prev + 12) % Math.max(bannerCards.length, 1));
    }, 20000);
    return () => clearInterval(interval);
  }, [bannerCards.length]);

  carouselDraggingRef.current = carouselDragging;
  carouselHoverPausedRef.current = marqueePaused;

  // 캐러셀 자동 회전 (JS 제어, 호버 시 멈춤)
  useEffect(() => {
    if (!bannerCarouselReady || bannerCards.length === 0) return;
    carouselRotationBaseTime.current = Date.now();
    carouselRotationBaseAngle.current = 0;
    let rafId: number;
    const tick = () => {
      if (carouselDraggingRef.current) {
        rafId = requestAnimationFrame(tick);
        return;
      }
      if (carouselHoverPausedRef.current) {
        carouselRotationBaseAngle.current = carouselRotationCurrent.current;
        carouselRotationBaseTime.current = Date.now();
        rafId = requestAnimationFrame(tick);
        return;
      }
      const angle = carouselRotationBaseAngle.current + (Date.now() - carouselRotationBaseTime.current) / 20000 * 360;
      carouselRotationCurrent.current = angle;
      setCarouselRotation(angle);
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafId);
  }, [bannerCarouselReady, bannerCards.length]);

  // 카드 외 영역에서만 드래그 시 회전 (클릭은 카드 링크)
  const bindCarouselDrag = () => {
    const onMove = (e: MouseEvent) => {
      const dx = e.clientX - carouselDragStartX.current;
      if (!carouselDraggingRef.current) {
        if (Math.abs(dx) > 5) {
          carouselDraggingRef.current = true;
          carouselDragMoved.current = true;
          setCarouselDragging(true);
        } else return;
      }
      const angle = carouselDragStartRotation.current + dx * 0.5;
      carouselRotationCurrent.current = angle;
      setCarouselRotation(angle);
    };
    const onUp = () => {
      if (carouselDraggingRef.current) {
        carouselRotationBaseAngle.current = carouselRotationCurrent.current;
        carouselRotationBaseTime.current = Date.now();
        setCarouselDragging(false);
      }
      carouselDraggingRef.current = false;
      carouselDragMoved.current = false;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const handleAsk = async (questionId: string) => {
    setSelectedQa(questionId);
    setCustomQuestionOpen(false);
    setQaLoading(true);
    setQaAnswer('');
    // 추천된 카드(displayCards) 중 첫 번째 카드 사용, 없으면 전체 카드 중 첫 번째
    const cardIdToSend = displayCards.length > 0 
      ? displayCards[0]?.cardId 
      : (recommendedCards.length > 0 ? recommendedCards[0]?.cardId : cards[0]?.cardId);
    try {
      const res = await recommendationService.answerQa({
        questionId,
        cardId: cardIdToSend,
      });
      setQaAnswer(res.answer);
    } finally {
      setQaLoading(false);
    }
  };

  const handleAskCustom = async () => {
    const text = customQuestionText.trim();
    if (!text) return;
    setSelectedQa('CUSTOM');
    setLastCustomQuestion(text);
    setQaLoading(true);
    setQaAnswer('');
    // 추천된 카드 3장 전부 ID로 수집 (cardId / card_id 둘 다 처리)
    const ids = displayCards
      .map((c) => (c as Card & { card_id?: number }).cardId ?? (c as Card & { card_id?: number }).card_id)
      .filter((id): id is number => typeof id === 'number' && Number.isFinite(id));
    try {
      const res = await recommendationService.answerQa({
        questionId: 'CUSTOM',
        customQuestion: text,
        recommendedCardIds: ids.slice(0, 5), // 최대 5개까지 명시적으로 배열 전달
      });
      setQaAnswer(res.answer);
      setCustomQuestionText('');
      setCustomQuestionOpen(false);
    } finally {
      setQaLoading(false);
    }
  };

  return (
    <div className="space-y-10 pb-20">
      {!hasSpendingOrAnalysis ? (
        /* 처음 가입 / 지출·분석 없음: 안내 + 카드 이미지 슬라이드 배너 */
        <>
          <div className="space-y-3">
            <div className={`p-6 rounded-[32px] ${UI_COLORS.surface} text-center`}>
              <p className="text-lg text-slate-200 leading-relaxed">
                {!hasAnyExpense ? (
                  <>
                    지출데이터를 등록하고 첫 분석데이터를 받아보세요.
                    <br />
                    그 데이터를 바탕으로 회원님께 어울리는 카드를 추천해드릴게요.
                  </>
                ) : isExpenseOlderThan3Months ? (
                  <>
                    최근 3개월 내 지출 데이터가 없어 추천을 만들기 어려워요.
                    <br />
                    마지막 지출일: {lastExpenseDate ? lastExpenseDate.toLocaleDateString() : '-'}
                    <br />
                    최근 거래를 추가하면 맞춤 추천을 다시 보여드릴게요.
                  </>
                ) : (
                  <>
                    아직 분석할 수 있는 데이터가 부족해요.
                    <br />
                    거래를 더 등록하면 회원님께 어울리는 카드를 추천해드릴게요.
                  </>
                )}
              </p>
            </div>

            {/* 카드 이미지 자동 슬라이드 배너 */}
            <section 
            className="relative w-full overflow-x-hidden overflow-y-visible pt-0 pb-6"
            aria-label="신한카드 이미지 배너"
            onMouseEnter={() => setMarqueePaused(true)}
            onMouseLeave={() => setMarqueePaused(false)}
          >
            {/* 페이드아웃 그라데이션 */}
            <div className="absolute left-0 top-0 bottom-0 w-32 bg-gradient-to-r from-slate-950 to-transparent z-20 pointer-events-none" />
            <div className="absolute right-0 top-0 bottom-0 w-32 bg-gradient-to-l from-slate-950 to-transparent z-20 pointer-events-none" />
            
            {!bannerCarouselReady ? (
              <div className="flex items-center justify-center py-20">
                <div className="loader">
                    <div className="text"><span>Loading</span></div>
                    <div className="text"><span>Loading</span></div>
                    <div className="text"><span>Loading</span></div>
                    <div className="text"><span>Loading</span></div>
                    <div className="text"><span>Loading</span></div>
                    <div className="text"><span>Loading</span></div>
                    <div className="text"><span>Loading</span></div>
                    <div className="text"><span>Loading</span></div>
                    <div className="text"><span>Loading</span></div>
                    <div className="line" />
                  </div>
                </div>
            ) : bannerCards.length > 0 ? (
              <div
                className="carousel-3d-wrapper"
                onMouseEnter={() => setMarqueePaused(true)}
                onMouseLeave={() => setMarqueePaused(false)}
                onMouseDown={(e) => {
                  if (e.button !== 0) return;
                  if ((e.target as HTMLElement).closest?.('.carousel-3d-card')) return;
                  carouselDragStartX.current = e.clientX;
                  carouselDragStartRotation.current = carouselRotationCurrent.current;
                  carouselDragMoved.current = false;
                  bindCarouselDrag();
                }}
                style={{ cursor: carouselDragging ? 'grabbing' : 'grab' }}
              >
                <div
                  className="carousel-3d-inner carousel-3d-inner-js"
                  style={
                    {
                      '--quantity': Math.min(bannerCards.length, 12),
                      transform: `perspective(1000px) rotateX(-15deg) rotateY(${carouselRotation}deg)`,
                    } as React.CSSProperties
                  }
                >
                  {Array.from({ length: 12 }, (_, i) => {
                    const card = bannerCards[(carouselOffset + i) % Math.max(bannerCards.length, 1)];
                    if (!card) return null;
                    const cardKey = `${card.cardId}-${carouselOffset}-${i}`;
                    const isHovered = hoveredBannerCardKey === cardKey;
                    const cardLabel = [card.company, card.name].filter(Boolean).join(' ') || card.name;
                    return (
                      <div
                        key={i}
                        className="carousel-3d-card"
                        role="button"
                        tabIndex={0}
                        style={
                          {
                            '--index': i,
                            '--color-card': '51, 65, 85',
                          } as React.CSSProperties
                        }
                        onClick={(e) => {
                          if (carouselDragMoved.current) {
                            e.preventDefault();
                            e.stopPropagation();
                            return;
                          }
                          window.open(cardLink(card), '_blank', 'noopener,noreferrer');
                        }}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            window.open(cardLink(card), '_blank', 'noopener,noreferrer');
                          }
                        }}
                        onMouseEnter={() => setHoveredBannerCardKey(cardKey)}
                        onMouseLeave={() => setHoveredBannerCardKey(null)}
                        title={`${cardLabel} - 카드 상세/신청 페이지로 이동`}
                      >
                        {isHovered && (
                          <div
                            className="absolute left-1/2 bottom-full mb-0.5 px-2 py-1 rounded bg-slate-800 text-white text-xs font-medium whitespace-nowrap shadow-md border border-slate-600 z-50 pointer-events-none"
                            style={{ transform: 'translateX(-50%) translateZ(80px)' }}
                          >
                            {cardLabel}
                            <span className="absolute left-1/2 top-full -translate-x-1/2 border-[4px] border-transparent border-t-slate-800" aria-hidden />
                          </div>
                        )}
                        <div className="carousel-3d-img">
                          <img
                            src={card.imageUrl}
                            alt={card.name}
                            referrerPolicy="no-referrer"
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ) : (
              <div className="flex items-center justify-center py-20">
                <div className="loader">
                  <div className="text"><span>Loading</span></div>
                  <div className="text"><span>Loading</span></div>
                  <div className="text"><span>Loading</span></div>
                  <div className="text"><span>Loading</span></div>
                  <div className="text"><span>Loading</span></div>
                  <div className="text"><span>Loading</span></div>
                  <div className="text"><span>Loading</span></div>
                  <div className="text"><span>Loading</span></div>
                  <div className="text"><span>Loading</span></div>
                  <div className="line" />
                </div>
              </div>
            )}
          </section>
          </div>
          
          {/* 하단 거래 추가 버튼 */}
          <div className="text-center pt-4">
            <button
              onClick={() => navigate('/app/add')}
              className="px-8 py-3 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors font-medium"
            >
              거래 추가
            </button>
          </div>
        </>
      ) : (
        <>
      {/* 1. Spending Summary Card */}
      <div className={`p-8 rounded-[32px] ${UI_COLORS.surface} relative overflow-hidden`}>
        <div className="absolute top-0 right-0 p-8 opacity-5">
          <svg xmlns="http://www.w3.org/2000/svg" className="h-32 w-32" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M16 8v8m-4-5v5m-4-2v2m-2 4h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
        </div>
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-white">당신의 소비 패턴 요약</h2>
          <span className="text-xs text-slate-500">최근 3개월 기준</span>
        </div>
        {has3MonthData ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {topCategories.map((cat, i) => (
              <div key={cat.name} className="p-5 bg-slate-950/50 rounded-2xl border border-slate-800 flex flex-col justify-between">
                <span className="text-xs font-bold text-slate-500 mb-1">Rank {i + 1}</span>
                <p className="text-lg font-bold text-white mb-2">{cat.name}</p>
                <p className="text-blue-400 font-black">₩{cat.amount.toLocaleString()}</p>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-8">
            <p className="text-slate-400 mb-4">최근 3개월 지출 데이터가 없습니다.</p>
            <button
              onClick={() => navigate('/app/add')}
              className="px-6 py-2 bg-blue-600 text-white rounded-xl hover:bg-blue-700 transition-colors"
            >
              거래 추가
            </button>
          </div>
        )}
      </div>

      {/* 2. Card Recommendations Section */}
      <section className="space-y-6">
        <div className="flex items-center justify-between">
          <h3 className="text-xl font-bold text-white">회원님께 꼭 맞는 카드 추천</h3>
          <span className="text-xs text-slate-500">AI가 분석한 최근 3개월 소비 데이터 기준</span>
        </div>
        
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {displayCards.map((card) => {
            const fullCard = cards.find((c) => c.cardId === card.cardId);
            const cardImageUrl = fullCard?.imageUrl ?? (fullCard as { image_url?: string })?.image_url ?? card.imageUrl ?? (card as { image_url?: string }).image_url;
            const href = cardLink(fullCard ?? card);
            return (
            <div key={card.cardId} className="flex flex-col group">
              <div className={`p-6 rounded-[32px] ${UI_COLORS.surface} flex-1 flex flex-col border-2 border-transparent hover:border-blue-600/30 transition-all duration-300 shadow-xl`}>
                {cardImageUrl && (
                  <a
                    href={href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="relative mb-6 rounded-2xl overflow-hidden aspect-[1.6/1] bg-slate-900/50 block cursor-pointer hover:opacity-90 transition-opacity"
                    title="카드 상세/신청 페이지로 이동"
                  >
                    <img
                      src={cardImageUrl}
                      alt={card.name}
                      className="w-full h-full object-contain"
                      referrerPolicy="no-referrer"
                    />
                  </a>
                )}
                <div className="mb-6">
                  <p className="text-xs text-slate-500 font-medium mb-1">{card.company}</p>
                  <h4 className="text-lg font-bold text-white mb-2">{card.name}</h4>
                  <p className="text-xs text-blue-400 font-bold mb-4">{(card.tags || []).slice(0, 2).join(' · ')}</p>
                  <p className="text-sm text-slate-300 leading-relaxed">
                    {formatCardBenefits(card.benefitsJson)}
                  </p>
                </div>

                {/* 이 카드가 적합한 이유 (LLM) */}
                <div className="mt-auto p-4 bg-slate-950/80 rounded-2xl border border-blue-900/20">
                  <div className="flex items-center gap-2 mb-2">
                    <div className="w-5 h-5 bg-blue-600 rounded-full flex items-center justify-center">
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-3 w-3 text-white" viewBox="0 0 20 20" fill="currentColor">
                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-[10px] font-bold text-blue-400 uppercase tracking-wider">이 카드가 적합한 이유</span>
                  </div>
                  {cardReasonsLoading[card.cardId] ? (
                    <p className="text-[11px] leading-relaxed text-slate-500">분석 중...</p>
                  ) : (() => {
                    const reason = cardReasons[card.cardId] || '이 카드의 혜택과 사용자의 상위 지출 카테고리를 매칭해 추천합니다.';
                    // 3줄 요약으로 표시 (줄바꿈 유지)
                    const lines = reason.split('\n').filter(line => line.trim().length > 0);
                    const displayLines = lines.length > 3 ? lines.slice(0, 3) : lines;
                    return (
                      <p className="text-[11px] leading-relaxed text-slate-400 whitespace-pre-line">
                        {displayLines.join('\n')}
                      </p>
                    );
                  })()}
                </div>
              </div>
            </div>
          );
          })}
        </div>
      </section>

      {/* 3. 선택지 기반 Q&A (RAG) */}
      <section className="space-y-6">
        <div className="flex items-center justify-between">
          <h3 className="text-xl font-bold text-white">선택지로 질문하기</h3>
          <span className="text-xs text-slate-500">선택 → 답변 생성</span>
        </div>

        {/* 선택지 기반 Q&A */}
        <div className={`p-6 rounded-[24px] ${UI_COLORS.surface} space-y-4`}>
          {displayCards.length > 0 && (
            <div className="text-sm text-slate-400">
              추천된 카드: <span className="text-blue-400 font-medium">
                {displayCards.slice(0, 3).map((c) => `${c.company} · ${c.name}`).join(', ')}
              </span> 기준으로 답변합니다.
            </div>
          )}

          <div className="flex flex-wrap gap-2">
            {qaOptions.filter((opt) => opt.id !== 'BEST_PRODUCT_BY_RETURN').map((opt) => (
              <button
                key={opt.id}
                onClick={() => handleAsk(opt.id)}
                className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${
                  selectedQa === opt.id ? 'bg-blue-600 text-white' : 'bg-slate-950 text-slate-200 border border-slate-800 hover:bg-slate-900'
                }`}
              >
                {opt.title}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setCustomQuestionOpen((v) => !v)}
              className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${
                customQuestionOpen ? 'bg-blue-600 text-white' : 'bg-slate-950 text-slate-200 border border-slate-800 hover:bg-slate-900'
              }`}
            >
              직접 입력
            </button>
          </div>

          {customQuestionOpen && (
            <div className="flex flex-col sm:flex-row gap-2">
              <input
                type="text"
                value={customQuestionText}
                onChange={(e) => setCustomQuestionText(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleAskCustom()}
                placeholder="질문을 입력하세요"
                className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-sm text-slate-200 placeholder-slate-500"
              />
              <button
                type="button"
                onClick={handleAskCustom}
                disabled={qaLoading || !customQuestionText.trim()}
                className="px-4 py-2 bg-blue-600 text-white text-sm font-bold rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                질문하기
              </button>
            </div>
          )}

          {selectedQa === 'CUSTOM' && lastCustomQuestion && (
            <div className="px-4 py-3 bg-slate-900/60 rounded-2xl border border-slate-700">
              <p className="text-xs font-bold text-slate-500 mb-1">질문</p>
              <p className="text-sm text-slate-200">{lastCustomQuestion}</p>
            </div>
          )}

          <div className="p-4 bg-slate-950/60 rounded-2xl border border-slate-800 min-h-[96px]">
            {qaLoading ? (
              <div className="text-slate-400 text-sm">답변 생성 중...</div>
            ) : (
              <div className="text-slate-300 text-sm whitespace-pre-wrap">{qaAnswer || '선택지를 눌러 질문해보세요.'}</div>
            )}
          </div>
        </div>
      </section>
        </>
      )}
    </div>
  );
};

export default Recommendations;
