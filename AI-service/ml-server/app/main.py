"""
SmartBudget ML Server
- OCR: 영수증 이미지에서 텍스트 추출
- Classification: 거래 내역 카테고리 분류
- AI: 임베딩, 벡터 검색, LLM 호출, 프롬프트 빌드
"""
from fastapi import FastAPI, HTTPException, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional, Dict, Any
import base64
import logging


# 2026-02-06 21:10 김윤호 수정 - 필요할 때만 import할 수 있도록 수정
def _new_ocr_service():
    from app.ocr import OCRService 
    return OCRService()

def _new_classifier():
    from app.classifier import CategoryClassifier
    return CategoryClassifier()

def _new_embedding_service():
    from app.embedding import EmbeddingService
    return EmbeddingService()

def _new_llm_service():
    from app.llm import LLMService
    return LLMService()

def _new_rag_service():
    from app.rag import RAGService
    return RAGService()



# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="SmartBudget ML/AI Server",
    description="OCR, 카테고리 분류, 임베딩, 벡터 검색, LLM 호출 서버",
    version="2.0.0"
)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 서비스 인스턴스
# 2026-02-06 21:10 김윤호 수정 - 필요할 때만 import할 수 있도록 수정
class LazyService:
    def __init__(self, factory, name: str):
        self._factory = factory
        self._name = name
        self._instance = None

    @property
    def initialized(self) -> bool:
        return self._instance is not None

    def _get(self):
        if self._instance is None:
            logger.info(f"{self._name} lazy initialization start")
            self._instance = self._factory()
            logger.info(f"{self._name} lazy initialization done")
        return self._instance

    def __getattr__(self, item):
        return getattr(self._get(), item)

ocr_service = LazyService(_new_ocr_service, "OCRService")
classifier = LazyService(_new_classifier, "CategoryClassifier")
embedding_service = LazyService(_new_embedding_service, "EmbeddingService")
llm_service = LazyService(_new_llm_service, "LLMService")
rag_service = LazyService(_new_rag_service, "RAGService")




# === Request/Response Models ===

class OcrRequest(BaseModel):
    image: str  # Base64 encoded image
    mime_type: str = "image/jpeg"


class OcrItem(BaseModel):
    name: str
    quantity: Optional[int] = None
    unit_price: Optional[int] = None
    amount: Optional[int] = None


class OcrResponse(BaseModel):
    success: bool
    error: Optional[str] = None
    store_name: Optional[str] = None
    store_address: Optional[str] = None
    date: Optional[str] = None
    time: Optional[str] = None
    total_amount: Optional[int] = None
    payment_method: Optional[str] = None
    card_info: Optional[str] = None
    items: Optional[List[OcrItem]] = None
    raw_text: Optional[str] = None
    confidence: Optional[float] = None


class ClassifyRequest(BaseModel):
    merchant: str
    memo: Optional[str] = ""
    categories: List[str]


class PredictionCandidate(BaseModel):
    category: str
    confidence: float


class ClassifyResponse(BaseModel):
    category: str
    confidence: float
    reason: str
    top_predictions: Optional[List[PredictionCandidate]] = None


class BatchClassifyRequest(BaseModel):
    transactions: List[dict]
    categories: List[str]


# === AI Service Models ===

class EmbedRequest(BaseModel):
    text: str
    task_type: Optional[str] = None  # "RETRIEVAL_DOCUMENT" | "RETRIEVAL_QUERY"


class EmbedResponse(BaseModel):
    embedding: List[float]
    dimensionality: int


class EmbedBatchRequest(BaseModel):
    texts: List[str]
    task_type: Optional[str] = None


class EmbedBatchResponse(BaseModel):
    embeddings: List[List[float]]


class RagSearchRequest(BaseModel):
    user_id: int
    year_month: str
    query_embedding: List[float]
    top_k: Optional[int] = None


class RagChunk(BaseModel):
    chunk_id: int
    report_id: int
    user_id: int
    year_month: str
    chunk_index: int
    content: str


class RagSearchResponse(BaseModel):
    chunks: List[RagChunk]


class RagAnswerRequest(BaseModel):
    user_id: int
    year_month: str
    question: str


class RagAnswerResponse(BaseModel):
    answer: str


class RagRagAnswerRequest(BaseModel):
    """Spring이 facts + user_id/year_month/top_k만 전달. AI Service가 벡터 검색·프롬프트·LLM 수행."""
    question: str
    facts: Optional[str] = ""
    user_id: int
    year_month: str
    top_k: Optional[int] = 5


class RagRagAnswerResponse(BaseModel):
    answer: str


class RagIndexChunkRequest(BaseModel):
    """Chroma 인덱싱: Spring이 청크 한 건 추가 요청."""
    chunk_id: int
    report_id: int
    user_id: int
    year_month: str
    content: str
    embedding: List[float]


class RagDeleteReportChunksRequest(BaseModel):
    """Chroma에서 해당 report_id 청크 전부 삭제."""
    report_id: int


class AnalyzeRequest(BaseModel):
    transactions: List[Dict[str, Any]]
    metrics: Dict[str, Any]
    monthly_budget: Optional[int] = None


class AnalyzeResponse(BaseModel):
    summary: str


class AnswerRequest(BaseModel):
    question: str
    metrics: Dict[str, Any]
    llm_summary: Optional[str] = None


class AnswerResponse(BaseModel):
    answer: str


class BudgetInsightRequest(BaseModel):
    """Spring이 예산·지출·저축 목표 등 집계 데이터 + 추가 분석 데이터 전달"""
    year_month: str = ""
    monthly_budget: int = 0
    total_spent: int = 0
    categories: List[Dict[str, Any]] = []
    saving_goals: List[Dict[str, Any]] = []
    last_months: List[Dict[str, Any]] = []
    days_elapsed: int = 0
    days_in_month: int = 30
    daily_average_spend: int = 0
    projected_spend: int = 0
    projected_over_amount: int = 0
    budget_usage_rate: float = 0.0
    projected_usage_rate: float = 0.0


class BudgetInsightResponse(BaseModel):
    insight: str


class EvidenceAnswerRequest(BaseModel):
    """근거 기반 QA: Spring이 DB 분석 결과(evidence_text) + 선택적 RAG 청크 전달"""
    question: str
    evidence_text: str = ""
    rag_chunks: Optional[List[str]] = None


class EvidenceAnswerResponse(BaseModel):
    answer: str


class ClassifyIntentRequest(BaseModel):
    question: str


class ClassifyIntentResponse(BaseModel):
    """PR2: Intent 분류 결과. JSON만 반환."""
    intent: str
    confidence: float = 0.0
    year_month: Optional[str] = None
    baseline_year_month: Optional[str] = None
    category_id: Optional[int] = None
    dimension: Optional[str] = None
    keywords: Optional[List[str]] = None
    needs_db: bool = False
    needs_rag: bool = False
    follow_up_question: Optional[str] = None


# === API Endpoints ===

@app.get("/health")
async def health_check():
    """서버 상태 확인"""
    return {
        "status": "healthy",
        # 2026-02-06 21:10 김윤호 수정 - 실제 init 여부만 표시 (health 호출 때문에 초기화되지 않게)
        "ocr_initialized": ocr_service.initialized,
        "classifier_initialized": classifier.initialized,
        "embedding_initialized": embedding_service.initialized,
        "llm_initialized": llm_service.initialized,
        "rag_initialized": rag_service.initialized,
    }


@app.post("/api/ocr", response_model=OcrResponse)
async def extract_text_from_receipt(request: OcrRequest):
    """
    영수증 이미지에서 텍스트 추출 (OCR)
    """
    try:
        logger.info("OCR 요청 수신")
        result = ocr_service.process_receipt(request.image, request.mime_type)
        return OcrResponse(**result)
    except Exception as e:
        logger.error(f"OCR 처리 오류: {e}")
        return OcrResponse(success=False, error=str(e))


# 백엔드 OcrReceiptsService.processReceipt() 호출용: multipart 파일 → OCR + 카테고리 분류
DEFAULT_CATEGORIES = ["식비", "교통", "쇼핑", "생활", "문화", "의료", "교육", "기타"]


@app.post("/process")
async def process_receipt_file(file: UploadFile = File(..., alias="file")):
    """
    영수증 이미지 파일 업로드 → OCR + 카테고리 분류 한 번에 처리.
    백엔드에서 multipart/form-data, name=\"file\" 로 호출.
    """
    try:
        content = await file.read()
        mime_type = file.content_type or "image/jpeg"
        b64 = base64.b64encode(content).decode("utf-8")
        ocr_result = ocr_service.process_receipt(b64, mime_type)
        if not ocr_result.get("success"):
            return {
                "parsed_json": {
                    "merchant": None,
                    "date": None,
                    "datetime": None,
                    "total": None,
                    "items": [],
                },
                "classification": {
                    "category": "기타",
                    "confidence": 0.0,
                    "topk": [],
                    "model_version": "ml-server",
                },
                "error": ocr_result.get("error", "OCR 실패"),
            }
        # 백엔드가 기대하는 parsed_json 형식
        items_names = []
        if ocr_result.get("items"):
            items_names = [it.get("name") or str(it) for it in ocr_result["items"]]
        parsed_json = {
            "merchant": ocr_result.get("store_name"),
            "date": ocr_result.get("date"),
            "datetime": ocr_result.get("time"),
            "total": ocr_result.get("total_amount"),
            "items": items_names,
        }
        # 카테고리 분류 (상호명 + 메모로 분류)
        merchant = ocr_result.get("store_name") or ""
        memo = ocr_result.get("raw_text") or ""
        classify_result = classifier.classify(
            merchant=merchant,
            memo=memo,
            categories=DEFAULT_CATEGORIES,
        )
        topk = []
        if classify_result.get("top_predictions"):
            for p in classify_result["top_predictions"]:
                topk.append({
                    "category": p.get("category", "기타"),
                    "score": p.get("confidence", 0.0),
                })
        classification = {
            "category": classify_result.get("category", "기타"),
            "confidence": classify_result.get("confidence", 0.0),
            "topk": topk,
            "model_version": "ml-server",
        }
        return {"parsed_json": parsed_json, "classification": classification}
    except Exception as e:
        logger.exception("Process 오류: %s", e)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/classify", response_model=ClassifyResponse)
async def classify_transaction(request: ClassifyRequest):
    """
    거래 내역 카테고리 분류
    """
    try:
        logger.info(f"분류 요청: merchant={request.merchant}")
        result = classifier.classify(
            merchant=request.merchant,
            memo=request.memo,
            categories=request.categories
        )
        return ClassifyResponse(**result)
    except Exception as e:
        logger.error(f"분류 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/classify/batch", response_model=List[ClassifyResponse])
async def classify_transactions_batch(request: BatchClassifyRequest):
    """
    여러 거래 내역 일괄 분류
    """
    try:
        logger.info(f"배치 분류 요청: {len(request.transactions)}건")
        results = []
        for tx in request.transactions:
            result = classifier.classify(
                merchant=tx.get("merchant", ""),
                memo=tx.get("memo", ""),
                categories=request.categories
            )
            results.append(ClassifyResponse(**result))
        return results
    except Exception as e:
        logger.error(f"배치 분류 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/train")
async def train_classifier(data: dict):
    """
    분류 모델 학습 (관리자용)
    """
    try:
        training_data = data.get("training_data", [])
        if not training_data:
            raise HTTPException(status_code=400, detail="학습 데이터가 없습니다")
        
        result = classifier.train(training_data)
        return {"success": True, "result": result}
    except Exception as e:
        logger.error(f"학습 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# === AI Service Endpoints ===

@app.post("/api/embed", response_model=EmbedResponse)
async def embed_text(request: EmbedRequest):
    """텍스트 임베딩 생성"""
    try:
        embedding = embedding_service.embed(request.text, request.task_type)
        if not embedding:
            raise HTTPException(status_code=500, detail="임베딩 생성 실패")
        return EmbedResponse(
            embedding=embedding,
            dimensionality=embedding_service.get_output_dimensionality()
        )
    except Exception as e:
        logger.error(f"임베딩 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/embed/batch", response_model=EmbedBatchResponse)
async def embed_batch(request: EmbedBatchRequest):
    """여러 텍스트 배치 임베딩"""
    try:
        embeddings = embedding_service.embed_batch(request.texts, request.task_type)
        return EmbedBatchResponse(embeddings=embeddings)
    except Exception as e:
        logger.error(f"배치 임베딩 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/rag/search", response_model=RagSearchResponse)
async def rag_search(request: RagSearchRequest):
    """벡터 유사도 검색 (Chroma 또는 PostgreSQL)"""
    try:
        chunks = rag_service.search_chunks(
            request.user_id,
            request.year_month,
            request.query_embedding,
            request.top_k
        )
        return RagSearchResponse(chunks=[RagChunk(**c) for c in chunks])
    except Exception as e:
        logger.error(f"벡터 검색 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/rag/index-chunk")
async def rag_index_chunk(request: RagIndexChunkRequest):
    """Chroma에 청크 한 건 인덱싱 (Spring 인덱싱 시 호출)"""
    try:
        rag_service.index_chunk(
            chunk_id=request.chunk_id,
            report_id=request.report_id,
            user_id=request.user_id,
            year_month=request.year_month,
            content=request.content,
            embedding=request.embedding,
        )
        return {"status": "indexed", "chunk_id": request.chunk_id}
    except Exception as e:
        logger.error(f"Chroma 인덱싱 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/rag/delete-report-chunks")
async def rag_delete_report_chunks(request: RagDeleteReportChunksRequest):
    """Chroma에서 해당 report_id 청크 전부 삭제"""
    try:
        rag_service.delete_by_report_id(request.report_id)
        return {"status": "deleted", "report_id": request.report_id}
    except Exception as e:
        logger.error(f"Chroma 삭제 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/rag/answer", response_model=RagAnswerResponse)
async def rag_answer(request: RagAnswerRequest):
    """RAG 기반 질문 답변 (Facts 없이, AI Service가 검색·LLM 수행)"""
    try:
        answer = rag_service.answer_with_rag(
            request.user_id,
            request.year_month,
            request.question
        )
        if not answer:
            raise HTTPException(status_code=404, detail="검색 결과 없음")
        return RagAnswerResponse(answer=answer)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"RAG 답변 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/llm/rag-answer", response_model=RagRagAnswerResponse)
async def rag_rag_answer(request: RagRagAnswerRequest):
    """RAG 질문 답변: Spring이 facts + user_id/year_month/top_k 전달. 여기서 쿼리 임베딩·벡터 검색·프롬프트·LLM 수행."""
    try:
        answer = rag_service.answer_with_rag_and_facts(
            question=request.question,
            facts=request.facts or "",
            user_id=request.user_id,
            year_month=request.year_month,
            top_k=request.top_k
        )
        if not answer:
            raise HTTPException(status_code=404, detail="검색/답변 생성 실패")
        return RagRagAnswerResponse(answer=answer)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"RAG rag-answer 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/llm/analyze", response_model=AnalyzeResponse)
async def analyze_report(request: AnalyzeRequest):
    """월별 리포트 분석 요약 생성 (metrics.yearMonth 기준으로 월·예측 계산)"""
    try:
        year_month = (request.metrics or {}).get("yearMonth") or ""
        summary = llm_service.generate_analysis_summary(
            request.transactions,
            request.metrics,
            request.monthly_budget,
            year_month=year_month,
        )
        return AnalyzeResponse(summary=summary)
    except Exception as e:
        logger.error(f"분석 요약 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/llm/answer", response_model=AnswerResponse)
async def answer_question(request: AnswerRequest):
    """리포트 맥락 기반 질문 답변 (전체 맥락)"""
    try:
        answer = llm_service.answer_report_question(
            request.question,
            request.metrics,
            request.llm_summary
        )
        return AnswerResponse(answer=answer)
    except Exception as e:
        logger.error(f"질문 답변 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/llm/budget-insight", response_model=BudgetInsightResponse)
async def budget_insight(request: BudgetInsightRequest):
    """예산/목표 인사이트: 저축 추천, 예산 대비 사용, 추세·이번 달 전망"""
    try:
        payload = {
            "year_month": request.year_month,
            "monthly_budget": request.monthly_budget,
            "total_spent": request.total_spent,
            "categories": request.categories,
            "saving_goals": request.saving_goals,
            "last_months": request.last_months,
            "days_elapsed": request.days_elapsed,
            "days_in_month": request.days_in_month,
            "daily_average_spend": request.daily_average_spend,
            "projected_spend": request.projected_spend,
            "projected_over_amount": request.projected_over_amount,
            "budget_usage_rate": request.budget_usage_rate,
            "projected_usage_rate": request.projected_usage_rate,
        }
        insight = llm_service.generate_budget_insight(payload)
        return BudgetInsightResponse(insight=insight)
    except Exception as e:
        logger.error(f"예산 인사이트 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/llm/evidence-answer", response_model=EvidenceAnswerResponse)
async def evidence_answer(request: EvidenceAnswerRequest):
    """근거 기반 QA: DB 분석 결과 + 선택적 RAG 청크로만 답변. 추측 금지, 최소 2개 수치/랭킹 포함."""
    try:
        answer = llm_service.answer_from_evidence(
            question=request.question,
            evidence_text=request.evidence_text or "",
            rag_chunks=request.rag_chunks,
        )
        return EvidenceAnswerResponse(answer=answer)
    except Exception as e:
        logger.error(f"Evidence answer 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/llm/classify-intent", response_model=ClassifyIntentResponse)
async def classify_intent(request: ClassifyIntentRequest):
    """PR2: 질문 Intent + 슬롯 분류. JSON만 반환. yearMonth 없으면 null, categoryId 못 뽑으면 null + followUpQuestion."""
    try:
        raw = llm_service.classify_intent(request.question or "")
        if not raw:
            return ClassifyIntentResponse(intent="SUMMARY", confidence=0.5)
        return ClassifyIntentResponse(
            intent=raw.get("intent", "SUMMARY"),
            confidence=float(raw.get("confidence", 0.5)),
            year_month=raw.get("yearMonth"),
            baseline_year_month=raw.get("baselineYearMonth"),
            category_id=raw.get("categoryId"),
            dimension=raw.get("dimension"),
            keywords=raw.get("keywords"),
            needs_db=bool(raw.get("needsDb", False)),
            needs_rag=bool(raw.get("needsRag", False)),
            follow_up_question=raw.get("followUpQuestion"),
        )
    except Exception as e:
        logger.error(f"Classify intent 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# === Recommendation 서비스 통합 (Flask 앱 마운트) ===
# ml-server와 같은 상위 폴더(AI-service 등)의 recommendation이 있으면 /recommendation 경로로 마운트
def _mount_recommendation():
    import sys
    from pathlib import Path
    _root = Path(__file__).resolve().parents[2]  # .../AI-service/ml-server/app/main.py -> AI-service
    _reco = _root / "recommendation"
    if not _reco.is_dir():
        return
    try:
        if str(_reco) not in sys.path:
            sys.path.insert(0, str(_reco))
        # recommendation/app.py에서 Flask app import
        import importlib.util
        spec = importlib.util.spec_from_file_location("recommendation_app", str(_reco / "app.py"))
        recommendation_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(recommendation_module)
        recommendation_flask_app = recommendation_module.app
        from fastapi.middleware.wsgi import WSGIMiddleware
        app.mount("/recommendation", WSGIMiddleware(recommendation_flask_app))
        logger.info("Recommendation Flask app mounted at /recommendation")
    except Exception as e:
        logger.warning("Recommendation app not mounted: %s", e)


_mount_recommendation()


# === OCR_receipts 서비스 통합 (FastAPI 앱 마운트) ===
# AI-service/OCR_receipts가 있으면 /receipts 경로로 마운트
def _mount_ocr_receipts():
    import sys
    from pathlib import Path
    _root = Path(__file__).resolve().parents[2]  # .../AI-service/ml-server/app/main.py -> AI-service
    _ocr_root = _root / "OCR_receipts"
    if not _ocr_root.is_dir():
        return

    try:
        # OCR_receipts 내부의 `src` 패키지를 import 할 수 있게 경로 추가
        if str(_ocr_root) not in sys.path:
            sys.path.insert(0, str(_ocr_root))

        # OCR_receipts/src/api/app.py 에서 FastAPI app 로드
        import importlib.util
        ocr_app_path = _ocr_root / "src" / "api" / "app.py"
        spec = importlib.util.spec_from_file_location("ocr_receipts_api", str(ocr_app_path))
        ocr_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(ocr_module)
        ocr_fastapi_app = getattr(ocr_module, "app", None)
        if ocr_fastapi_app is None:
            raise RuntimeError("OCR_receipts app 객체를 찾을 수 없습니다. (OCR_receipts/src/api/app.py의 app)")

        app.mount("/receipts", ocr_fastapi_app)
        logger.info("OCR_receipts FastAPI app mounted at /receipts")
    except Exception as e:
        logger.warning("OCR_receipts app not mounted: %s", e)


_mount_ocr_receipts()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
