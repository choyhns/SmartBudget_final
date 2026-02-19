"""
RAG: 벡터 검색 및 RAG 기반 답변 생성.
- RAG_USE_CHROMA=true: Chroma 벡터 저장소 사용 (PostgreSQL embedding 불필요).
- RAG_USE_CHROMA=false: PostgreSQL (pgvector 또는 JSONB) 사용.
"""
import os
import logging
from typing import List, Dict, Any, Optional
from pathlib import Path

from dotenv import load_dotenv
from app.embedding import EmbeddingService
from app.llm import LLMService

# AI-service 루트의 .env 파일 로드 (있으면), 없으면 하위 .env 사용
_root_dir = Path(__file__).resolve().parents[3]  # ml-server/app/rag.py -> AI-service
_root_env = _root_dir / ".env"
_ml_server_env = _root_dir / "ml-server" / ".env"
_current_env = Path(__file__).resolve().parent.parent.parent / ".env"  # ml-server/.env
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# 1) 루트 .env 우선 로드
if _root_env.exists():
    load_dotenv(_root_env)
    logger.info(f"AI-service/.env를 로드했습니다: {_root_env}")
# 2) 루트 .env가 없으면 ml-server/.env 로드 (하위 호환성)
elif _ml_server_env.exists():
    load_dotenv(_ml_server_env)
    logger.info(f"ml-server/.env를 로드했습니다 (루트 .env 없음): {_ml_server_env}")
# 3) 둘 다 없으면 현재 디렉토리 .env 확인 (하위 호환성)
elif _current_env.exists():
    load_dotenv(_current_env)
    logger.info(f"현재 디렉토리 .env를 로드했습니다: {_current_env}")
# 4) 모두 없으면 경고
else:
    logger.warning(f"환경 변수 파일을 찾을 수 없습니다. 다음 경로를 확인하세요:")
    logger.warning(f"  1) {_root_env}")
    logger.warning(f"  2) {_ml_server_env}")
    logger.warning(f"  3) {_current_env}")
logger = logging.getLogger(__name__)

USE_CHROMA = os.getenv("RAG_USE_CHROMA", "true").lower() in ("1", "true", "yes")
USE_JSONB = os.getenv("RAG_USE_JSONB", "true").lower() in ("1", "true", "yes")

# Chroma 사용 시 PostgreSQL 검색 비활성
if USE_CHROMA:
    USE_JSONB = False

_chroma_client = None
_chroma_collection = None
_chroma_init_failed = False


def _get_chroma_collection():
    """Chroma 컬렉션 반환. 초기화 실패 시 None (NumPy 2.0 등 호환 문제 시 서버는 계속 동작)."""
    global _chroma_client, _chroma_collection, _chroma_init_failed
    if _chroma_init_failed:
        return None
    if _chroma_collection is not None:
        return _chroma_collection
    try:
        import chromadb
        persist_dir = os.getenv("CHROMA_PERSIST_DIR", "./chroma_data")
        _chroma_client = chromadb.PersistentClient(path=persist_dir)
        _chroma_collection = _chroma_client.get_or_create_collection(
            name="report_chunks",
            metadata={"hnsw:space": "cosine"},
        )
        logger.info("Chroma collection 'report_chunks' ready at %s", persist_dir)
        return _chroma_collection
    except Exception as e:
        logger.error("Chroma init failed (RAG without Chroma): %s", e)
        _chroma_init_failed = True
        return None


def _cosine_similarity(a: List[float], b: List[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = sum(x * x for x in a) ** 0.5
    norm_b = sum(x * x for x in b) ** 0.5
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)


class RAGService:
    def __init__(self):
        self.embedding_service = EmbeddingService()
        self.llm_service = LLMService()
        self.top_k = int(os.getenv("RAG_TOP_K", "5"))

        if not USE_CHROMA:
            import psycopg2
            from psycopg2.extras import RealDictCursor
            db_url = os.getenv("DB_URL", "")
            if db_url.startswith("jdbc:postgresql://"):
                db_url = db_url.replace("jdbc:postgresql://", "")
            elif db_url.startswith("postgresql://"):
                db_url = db_url.replace("postgresql://", "")

            if "/" in db_url:
                host_port, database = db_url.split("/", 1)
            else:
                host_port = db_url
                database = os.getenv("DB_NAME", "smartbudget")

            if ":" in host_port:
                host, port_str = host_port.rsplit(":", 1)
                try:
                    port = int(port_str)
                except ValueError:
                    port = 5432
            else:
                host = host_port or "localhost"
                port = 5432

            self.db_config = {
                "host": host,
                "port": port,
                "database": database,
                "user": os.getenv("DB_USERNAME", "postgres"),
                "password": os.getenv("DB_PASSWORD", ""),
            }
            logger.info(f"DB config: host={host}, port={port}, database={database}, use_jsonb={USE_JSONB}")
        else:
            self.db_config = None
            coll = _get_chroma_collection()
            if coll is None and USE_CHROMA:
                logger.warning("Chroma unavailable; RAG will use fallback or empty results.")
        logger.info("RAG vector store: chroma=%s, jsonb=%s", USE_CHROMA, USE_JSONB)

    # ---------- Chroma: 인덱싱 / 삭제 ----------
    def index_chunk(
        self,
        chunk_id: int,
        report_id: int,
        user_id: int,
        year_month: str,
        content: str,
        embedding: List[float],
    ) -> None:
        """Chroma에 청크 한 건 추가."""
        if not USE_CHROMA or not embedding:
            return
        coll = _get_chroma_collection()
        if coll is None:
            return
        try:
            coll.add(
                ids=[str(chunk_id)],
                embeddings=[embedding],
                metadatas=[
                    {
                        "report_id": report_id,
                        "user_id": user_id,
                        "year_month": year_month,
                    }
                ],
                documents=[content or ""],
            )
            logger.debug(f"Chroma indexed chunk_id={chunk_id}")
        except Exception as e:
            logger.error(f"Chroma index_chunk failed: {e}")
            raise

    def delete_by_report_id(self, report_id: int) -> None:
        """해당 report_id의 모든 청크를 Chroma에서 삭제."""
        if not USE_CHROMA:
            return
        coll = _get_chroma_collection()
        if coll is None:
            return
        try:
            result = coll.get(where={"report_id": report_id})
            if result and result["ids"]:
                coll.delete(ids=result["ids"])
                logger.info(f"Chroma deleted {len(result['ids'])} chunks for report_id={report_id}")
        except Exception as e:
            logger.error(f"Chroma delete_by_report_id failed: {e}")
            raise

    def search_chunks(
        self,
        user_id: int,
        year_month: str,
        query_embedding: List[float],
        top_k: Optional[int] = None,
    ) -> List[Dict[str, Any]]:
        """벡터 유사도 검색. Chroma 사용 시 Chroma에서, 아니면 PostgreSQL에서."""
        if not query_embedding or len(query_embedding) == 0:
            return []

        k = top_k or self.top_k

        if USE_CHROMA:
            return self._search_chunks_chroma(user_id, year_month, query_embedding, k)
        return self._search_chunks_postgres(user_id, year_month, query_embedding, k)

    def _search_chunks_chroma(
        self,
        user_id: int,
        year_month: str,
        query_embedding: List[float],
        k: int,
    ) -> List[Dict[str, Any]]:
        """Chroma에서 user_id, year_month 필터로 유사 청크 검색."""
        coll = _get_chroma_collection()
        if coll is None:
            return []
        try:
            results = coll.query(
                query_embeddings=[query_embedding],
                n_results=k,
                where={"$and": [{"user_id": user_id}, {"year_month": year_month}]},
                include=["documents", "metadatas"],
            )
            out = []
            ids = results["ids"][0] if results["ids"] else []
            metadatas = results["metadatas"][0] if results["metadatas"] else []
            documents = results["documents"][0] if results["documents"] else []
            for i, cid in enumerate(ids):
                meta = metadatas[i] if i < len(metadatas) else {}
                doc = documents[i] if i < len(documents) else ""
                out.append({
                    "chunk_id": int(cid),
                    "report_id": meta.get("report_id", 0),
                    "user_id": meta.get("user_id", user_id),
                    "year_month": meta.get("year_month", year_month),
                    "chunk_index": i,
                    "content": doc,
                })
            return out
        except Exception as e:
            logger.error(f"Chroma search failed: {e}")
            return []

    def _search_chunks_postgres(
        self,
        user_id: int,
        year_month: str,
        query_embedding: List[float],
        k: int,
    ) -> List[Dict[str, Any]]:
        """PostgreSQL(pgvector 또는 JSONB)에서 검색."""
        import psycopg2
        from psycopg2.extras import RealDictCursor

        try:
            conn = psycopg2.connect(**self.db_config)
            with conn.cursor(cursor_factory=RealDictCursor) as cur:
                if USE_JSONB:
                    cur.execute("""
                        SELECT chunk_id, report_id, user_id, year_month, chunk_index, content, embedding
                        FROM projectdb.report_chunks
                        WHERE user_id = %s AND year_month = %s AND embedding IS NOT NULL
                    """, (user_id, year_month))
                    rows = [dict(r) for r in cur.fetchall()]
                    scored = []
                    for row in rows:
                        emb = row.get("embedding")
                        if isinstance(emb, list):
                            vec = emb
                        elif isinstance(emb, (str, bytes)):
                            import json
                            vec = json.loads(emb) if isinstance(emb, str) else json.loads(emb.decode())
                        else:
                            vec = list(emb) if emb else []
                        if len(vec) != len(query_embedding):
                            continue
                        sim = _cosine_similarity(query_embedding, vec)
                        scored.append((sim, row))
                    scored.sort(key=lambda x: -x[0])
                    results = [row for _, row in scored[:k]]
                    for r in results:
                        r.pop("embedding", None)
                else:
                    vector_str = "[" + ",".join(str(f) for f in query_embedding) + "]"
                    cur.execute("""
                        SELECT chunk_id, report_id, user_id, year_month, chunk_index, content
                        FROM projectdb.report_chunks
                        WHERE user_id = %s AND year_month = %s
                        ORDER BY embedding <=> %s::vector
                        LIMIT %s
                    """, (user_id, year_month, vector_str, k))
                    results = [dict(row) for row in cur.fetchall()]
            conn.close()
            return results
        except Exception as e:
            logger.error(f"PostgreSQL vector search failed: {e}")
            return []

    def answer_with_rag(
        self,
        user_id: int,
        year_month: str,
        question: str,
    ) -> Optional[str]:
        """RAG 기반 질문 답변 (Facts 없이 검색 청크만 사용)."""
        query_emb = self.embedding_service.embed(question, "RETRIEVAL_QUERY")
        if not query_emb:
            return None
        chunks = self.search_chunks(user_id, year_month, query_emb, self.top_k)
        if not chunks:
            return None
        chunk_contents = [chunk["content"] for chunk in chunks]
        return self.llm_service.answer_from_rag_context(question, chunk_contents)

    def answer_with_rag_and_facts(
        self,
        question: str,
        facts: str,
        user_id: int,
        year_month: str,
        top_k: Optional[int] = None,
    ) -> Optional[str]:
        """RAG 질문 답변: Spring이 준 facts + user_id/year_month/top_k."""
        query_emb = self.embedding_service.embed(question, "RETRIEVAL_QUERY")
        if not query_emb:
            return None
        k = top_k or self.top_k
        chunks = self.search_chunks(user_id, year_month, query_emb, k)
        chunk_contents = [chunk["content"] for chunk in chunks] if chunks else []
        return self.llm_service.answer_from_rag_with_facts(question, facts or "", chunk_contents)

    def build_chunks(
        self,
        llm_summary: Optional[str],
        metrics: Dict[str, Any],
    ) -> List[str]:
        """리포트를 청크로 분할 (텍스트만)."""
        chunks = []
        if llm_summary and llm_summary.strip():
            chunks.append(f"【월별 AI 요약】\n{llm_summary}")
        stats = "【통계】"
        stats += f" 총 수입 {metrics.get('totalIncome', 0)}원"
        stats += f", 총 지출 {metrics.get('totalExpense', 0)}원"
        stats += f", 순액 {metrics.get('netAmount', 0)}원"
        stats += f", 거래 {metrics.get('transactionCount', 0)}건."
        chunks.append(stats)
        if "categoryExpenses" in metrics and metrics["categoryExpenses"]:
            cat_text = "【카테고리별 지출】"
            for cat, amt in metrics["categoryExpenses"].items():
                cat_text += f" {cat} {amt}원,"
            chunks.append(cat_text.rstrip(","))
        return chunks
