
import React, { useState, useRef, useEffect } from 'react';
import { SimpleTransaction, Category } from '../types';
import { CATEGORIES, UI_COLORS } from '../constants';
import { categoryService } from '../services/categoryService';
import { getAuthHeaders, authService } from '../services/authService';

type TransactionType = 'INCOME' | 'EXPENSE';

interface TransactionFormProps {
  onSave: (t: Omit<SimpleTransaction, 'id'>) => void;
  /** 영수증 포함 저장 시 백엔드에서 이미 생성된 거래를 목록에 반영할 때 사용 */
  onSaveCreatedTransaction?: (t: SimpleTransaction) => void;
  onCancel: () => void;
}

interface OcrResult {
  receipt?: {
    fileId: number;
    urlPath: string;
    status: string;
  } | null;
  ocrResult?: {
    storeName?: string;
    date?: string;
    time?: string;
    totalAmount?: number;
    rawText?: string;
  };
  classification: {
    category: string;
    confidence: number;
    reason?: string;
  };
  message: string;
}

const TransactionForm: React.FC<TransactionFormProps> = ({ onSave, onSaveCreatedTransaction, onCancel }) => {
  const [type, setType] = useState<TransactionType>('EXPENSE');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState(CATEGORIES.EXPENSE[0]);
  const [merchant, setMerchant] = useState('');
  const [showToast, setShowToast] = useState(false);
  const [toastMessage, setToastMessage] = useState('');
  const [isProcessingOcr, setIsProcessingOcr] = useState(false);
  const [ocrError, setOcrError] = useState<string | null>(null);
  const [ocrResult, setOcrResult] = useState<OcrResult | null>(null);
  const [dbCategories, setDbCategories] = useState<Category[]>([]);
  const [pendingReceiptFile, setPendingReceiptFile] = useState<File | null>(null); // 거래 저장 시 S3로 보낼 파일
  const [previewObjectUrl, setPreviewObjectUrl] = useState<string | null>(null); // 미리보기용 object URL
  const [isSaving, setIsSaving] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // object URL 정리
  useEffect(() => {
    return () => {
      if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
    };
  }, [previewObjectUrl]);
  
  // DB에서 카테고리 목록 가져오기
  useEffect(() => {
    categoryService.getAllCategories()
      .then(categories => {
        setDbCategories(categories);
        // DB 카테고리가 있으면 첫 번째로 설정
        if (categories.length > 0 && type === 'EXPENSE') {
          const expenseCategories = categories.filter(c => 
            CATEGORIES.EXPENSE.includes(c.name) || !CATEGORIES.INCOME.includes(c.name)
          );
          if (expenseCategories.length > 0 && !expenseCategories.some(c => c.name === category)) {
            setCategory(expenseCategories[0].name);
          }
        }
      })
      .catch(err => {
        console.error('카테고리 로드 실패:', err);
        // 실패 시 기본 카테고리 사용
      });
  }, []);

  // type이 변경될 때 category가 유효한지 확인하고 필요시 수정
  useEffect(() => {
    const currentCategories = type === 'EXPENSE' ? CATEGORIES.EXPENSE : CATEGORIES.INCOME;
    // 현재 선택된 카테고리가 현재 type의 카테고리 목록에 없으면 첫 번째로 설정
    if (!currentCategories.includes(category)) {
      setCategory(currentCategories[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type]); // type이 변경될 때만 실행 (category는 의존성에서 제외하여 무한 루프 방지)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!amount || !merchant) return;

    const selectedCategory = dbCategories.find(c => c.name === category);
    const categoryId = selectedCategory?.categoryId || null;

    // DB 저장은 오직 "거래 저장" 버튼 클릭 시에만 수행 (업로드/OCR 시점에는 저장 안 함)
    if (pendingReceiptFile) {
      if (!amount.trim()) {
        setOcrError('금액을 입력해 주세요.');
        return;
      }
      // 영수증 이미지 있음 → 이 시점에 S3 + receipt + transaction DB 저장
      setIsSaving(true);
      try {
        const formData = new FormData();

        const requestData = {
          date: date,
          amount: amount,
          merchant: merchant,
          type: type,
          categoryId: categoryId,
          // userId는 백엔드 토큰에서 가져오므로 생략 가능
        };
        // ★ 핵심: JSON 문자열로 변환 후 Blob으로 감싸서 'data' 키에 할당
        formData.append(
          'data', 
          new Blob([JSON.stringify(requestData)], { type: "application/json" })
        );

        // 파일은 그대로 'file' 키로 전송
        formData.append('file', pendingReceiptFile);

        const res = await fetch('/api/receipts/save-and-create-transaction', {
          method: 'POST',
          headers: { ...getAuthHeaders() },
          body: formData,
        });
        if (!res.ok) {
          let msg = `저장 실패: ${res.status}`;
          try {
            const errBody = await res.json();
            if (errBody?.message) msg = errBody.message;
          } catch {
            // ignore
          }
          throw new Error(msg);
        }
        const created = await res.json();
        const simpleTx: SimpleTransaction = {
          id: String(created.txId),
          date: created.txDatetime ? created.txDatetime.split('T')[0] : date,
          type: created.amount >= 0 ? 'INCOME' : 'EXPENSE',
          category: created.categoryName || category,
          merchant: created.merchant || merchant,
          amount: Math.abs(Number(created.amount)),
        };
        if (onSaveCreatedTransaction) onSaveCreatedTransaction(simpleTx);
        setToastMessage('거래가 성공적으로 저장되었습니다');
        setShowToast(true);
        setTimeout(() => { setShowToast(false); onCancel(); }, 1500);
      } catch (err) {
        console.error(err);
        setOcrError(err instanceof Error ? err.message : '거래 저장에 실패했습니다.');
      } finally {
        setIsSaving(false);
      }
    } else {
      // 영수증 없음(수동 입력) → 이 시점에만 transaction DB 저장
      onSave({
        date,
        type,
        category,
        merchant,
        amount: parseInt(amount, 10),
        categoryId,
      });
      setToastMessage('거래가 성공적으로 저장되었습니다');
      setShowToast(true);
      setTimeout(() => { setShowToast(false); onCancel(); }, 1500);
    }
  };

  const handleOcrClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // 이미지 파일만 허용
    if (!file.type.startsWith('image/')) {
      setOcrError('이미지 파일만 업로드 가능합니다.');
      return;
    }

    setIsProcessingOcr(true);
    setOcrError(null);

    try {
      const formData = new FormData();
      formData.append('file', file);
      // userId는 백엔드에서 JWT에서 자동으로 가져오므로 전송하지 않음

      // OCR만 수행 (DB/S3 저장 없음. 저장은 "거래 저장" 클릭 시에만)
      const response = await fetch('/api/receipts/ocr-only', {
        method: 'POST',
        headers: { ...getAuthHeaders() },
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`OCR 처리 실패: ${response.status}`);
      }

      const result: OcrResult = await response.json();
      setOcrResult(result);
      setPendingReceiptFile(file);
      if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
      setPreviewObjectUrl(URL.createObjectURL(file));
      
      if (result.ocrResult) {
        // OCR 결과를 폼에 채우기 (금액 0도 반영)
        if (result.ocrResult.storeName != null && result.ocrResult.storeName !== '') {
          setMerchant(result.ocrResult.storeName);
        }
        if (result.ocrResult.totalAmount != null && typeof result.ocrResult.totalAmount === 'number') {
          setAmount(String(result.ocrResult.totalAmount));
        }
        if (result.ocrResult.date) {
          setDate(result.ocrResult.date);
        }
        
        // 카테고리 분류 결과 적용
        if (result.classification?.category) {
          const classifiedCategory = result.classification.category;
          
          // DB 카테고리 목록에서 찾기
          const dbCategory = dbCategories.find(c => c.name === classifiedCategory);
          if (dbCategory) {
            setCategory(classifiedCategory);
          } else {
            // DB에 없으면 프론트엔드 CATEGORIES에서 찾기
            const currentCategories = type === 'EXPENSE' ? CATEGORIES.EXPENSE : CATEGORIES.INCOME;
            if (currentCategories.includes(classifiedCategory)) {
              setCategory(classifiedCategory);
            } else {
              // 둘 다 없으면 "기타"로 설정
              const fallbackCategory = currentCategories.find(cat => cat === '기타') || currentCategories[0];
              setCategory(fallbackCategory);
            }
          }
        }
      }

      // OCR 완료 토스트 (저장 아님)
      setToastMessage('OCR 처리 완료');
      setShowToast(true);
      setTimeout(() => setShowToast(false), 3000);
    } catch (error) {
      console.error('OCR 처리 오류:', error);
      setOcrError(error instanceof Error ? error.message : 'OCR 처리 중 오류가 발생했습니다.');
    } finally {
      setIsProcessingOcr(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const showReceiptPreview = Boolean(ocrResult && (previewObjectUrl || pendingReceiptFile));

  /* 영수증 출력 기준 크기: 320×480px → 폼 전체 높이/레이아웃 기준 */
  const receiptDisplayWidth = 320;
  const receiptDisplayHeight = 480;

  return (
    <div className={`mx-auto space-y-8 ${showReceiptPreview ? 'max-w-4xl' : 'max-w-xl'}`}>
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-white">거래 추가</h2>
        <button onClick={onCancel} className="text-slate-400 hover:text-slate-200">
          <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div
        className={`p-8 rounded-3xl ${UI_COLORS.surface} ${showReceiptPreview ? 'flex flex-col md:flex-row gap-8 md:min-h-[480px]' : ''}`}
      >
        {/* 좌측: 영수증 미리보기 (출력 크기 기준: 320×480) */}
        {showReceiptPreview && (
          <div className="flex-shrink-0 w-full md:w-80">
            <div
              className="sticky top-4 rounded-2xl overflow-hidden border border-slate-700 bg-slate-900/50 flex items-center justify-center w-full aspect-[3/4] md:aspect-auto md:h-[480px]"
              style={{ maxWidth: receiptDisplayWidth }}
            >
              {previewObjectUrl ? (
                <img
                  src={previewObjectUrl}
                  alt="업로드한 영수증"
                  className="w-full h-full object-contain"
                />
              ) : (
                <div className="text-slate-500 text-sm p-4 text-center">미리보기</div>
              )}
            </div>
          </div>
        )}

        {/* 우측: 폼 (영수증 출력 높이 480px에 맞춘 최소 높이) */}
        <div className={`flex-1 min-w-0 ${showReceiptPreview ? 'md:min-w-[280px] md:min-h-[480px]' : ''}`}>
        {/* Type Toggle */}
        <div className="flex p-1 bg-slate-950 rounded-2xl mb-8 border border-slate-800">
          <button
            onClick={() => {
              setType('EXPENSE');
              // useEffect에서 자동으로 카테고리를 검증하고 수정함
            }}
            className={`flex-1 py-3 rounded-xl font-bold transition-all ${
              type === 'EXPENSE' ? 'bg-rose-500 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            지출
          </button>
          <button
            onClick={() => {
              setType('INCOME');
              // useEffect에서 자동으로 카테고리를 검증하고 수정함
            }}
            className={`flex-1 py-3 rounded-xl font-bold transition-all ${
              type === 'INCOME' ? 'bg-emerald-500 text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'
            }`}
          >
            수입
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-400">날짜</label>
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 focus:ring-2 focus:ring-blue-500 outline-none text-slate-200"
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium text-slate-400">카테고리</label>
              <select
                value={category}
                onChange={(e) => {
                  const selectedCategory = e.target.value;
                  setCategory(selectedCategory);
                }}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 focus:ring-2 focus:ring-blue-500 outline-none text-slate-200 appearance-none"
              >
                {/* DB 카테고리가 있으면 사용, 없으면 기본 CATEGORIES 사용 */}
                {dbCategories.length > 0 ? (
                  // DB 카테고리 사용: 부모 카테고리만 표시 (중복 방지)
                  dbCategories
                    .filter(cat => {
                      // 부모 카테고리만 표시 (parentId가 null인 것만)
                      if (cat.parentId !== null) return false;
                      
                      // type에 맞는 카테고리만 필터링
                      if (type === 'EXPENSE') {
                        return !CATEGORIES.INCOME.includes(cat.name) || cat.name === '기타';
                      } else {
                        return CATEGORIES.INCOME.includes(cat.name) || cat.name === '기타';
                      }
                    })
                    .map(cat => (
                      <option key={cat.categoryId} value={cat.name}>
                        {cat.name}
                      </option>
                    ))
                ) : (
                  // 기본 CATEGORIES 사용
                  (type === 'EXPENSE' ? CATEGORIES.EXPENSE : CATEGORIES.INCOME).map((cat, index) => (
                    <option key={`default-${index}-${cat}`} value={cat}>
                      {cat}
                    </option>
                  ))
                )}
              </select>
            </div>
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-400">금액 (원)</label>
            <input
              type="number"
              placeholder="0"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-4 text-2xl font-bold focus:ring-2 focus:ring-blue-500 outline-none text-white placeholder-slate-700"
              required
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium text-slate-400">가맹점 / 메모</label>
            <input
              type="text"
              placeholder="예: 스타벅스, 스타트업 급여..."
              value={merchant}
              onChange={(e) => setMerchant(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 focus:ring-2 focus:ring-blue-500 outline-none text-slate-200 placeholder-slate-700"
              required
            />
          </div>

          <div className="pt-4 flex flex-col gap-3">
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept="image/*"
              className="hidden"
            />
            <button
              type="button"
              onClick={handleOcrClick}
              disabled={isProcessingOcr}
              className={`w-full py-4 rounded-xl font-semibold border border-dashed transition-all flex items-center justify-center gap-2 ${
                isProcessingOcr
                  ? 'border-slate-600 bg-slate-900 text-slate-500 cursor-not-allowed'
                  : 'border-slate-700 hover:border-blue-500 hover:bg-blue-500/5 text-slate-400 hover:text-blue-400'
              }`}
            >
              {isProcessingOcr ? (
                <>
                  <svg className="animate-spin h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  OCR 처리 중...
                </>
              ) : (
                <>
                  <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                  영수증 업로드 (OCR)
                </>
              )}
            </button>
            {ocrError && (
              <div className="text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-xl px-4 py-2">
                {ocrError}
              </div>
            )}
            {ocrResult && ocrResult.classification && (
              <div className="bg-blue-500/10 border border-blue-500/20 rounded-xl px-4 py-3 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-blue-400">OCR 처리 완료</span>
                  <button
                    onClick={() => setOcrResult(null)}
                    className="text-blue-400/60 hover:text-blue-400"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
                {ocrResult.ocrResult?.storeName && (
                  <div className="text-sm text-slate-300">
                    <span className="text-slate-400">가맹점:</span> {ocrResult.ocrResult.storeName}
                  </div>
                )}
                {ocrResult.ocrResult?.totalAmount && (
                  <div className="text-sm text-slate-300">
                    <span className="text-slate-400">금액:</span> {ocrResult.ocrResult.totalAmount.toLocaleString()}원
                  </div>
                )}
                {ocrResult.classification?.category && (
                  <div className="text-sm text-slate-300">
                    <span className="text-slate-400">추천 카테고리:</span>{' '}
                    <span className="font-semibold text-blue-400">{ocrResult.classification.category}</span>
                    {ocrResult.classification?.confidence != null && (
                      <span className="text-slate-500 ml-2">
                        (신뢰도: {Math.round(ocrResult.classification.confidence * 100)}%)
                      </span>
                    )}
                  </div>
                )}
              </div>
            )}
            <button
              type="submit"
              disabled={isSaving}
              className={`w-full py-4 rounded-xl font-bold shadow-2xl shadow-blue-600/30 transition-all active:scale-[0.98] disabled:opacity-60 ${UI_COLORS.primary}`}
            >
              {isSaving ? '저장 중…' : '거래 저장'}
            </button>
          </div>
        </form>
        </div>
      </div>

      {showToast && toastMessage && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 bg-emerald-500 text-white px-6 py-3 rounded-full shadow-2xl animate-in fade-in slide-in-from-bottom-4 duration-300 flex items-center gap-2 font-semibold">
          <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
          {toastMessage}
        </div>
      )}
    </div>
  );
};

export default TransactionForm;
