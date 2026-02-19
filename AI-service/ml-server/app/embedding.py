"""
Gemini Embedding API를 사용한 텍스트 임베딩 생성 (REST API 직접 호출)
"""
import os
import logging
import json
from typing import List, Optional
from pathlib import Path
import httpx
from dotenv import load_dotenv

# AI-service 루트의 .env 파일 로드 (있으면), 없으면 하위 .env 사용
_root_dir = Path(__file__).resolve().parents[3]  # ml-server/app/embedding.py -> AI-service
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

class EmbeddingService:
    def __init__(self):
        self.api_key = os.getenv("GEMINI_API_KEY", "")
        if not self.api_key:
            logger.warning("GEMINI_API_KEY not set")
        # text-embedding-004는 일부 API 키/리전에서 404 → gemini-embedding-001 사용 (768/1536/3072 차원 지원)
        self.model_name = os.getenv("RAG_EMBEDDING_MODEL", "gemini-embedding-001")
        self.output_dimensionality = 768
        self.base_url = "https://generativelanguage.googleapis.com/v1beta/models"
    
    def embed(self, text: str, task_type: Optional[str] = None) -> Optional[List[float]]:
        """단일 텍스트 임베딩 생성"""
        if not text or not text.strip():
            return None
        if not self.api_key:
            return None
        
        try:
            # base_url already ends with /v1beta/models → use model name only (avoid .../models/models/...)
            model_for_url = self.model_name.replace("models/", "") if self.model_name.startswith("models/") else self.model_name
            url = f"{self.base_url}/{model_for_url}:embedContent?key={self.api_key}"
            model_for_payload = f"models/{model_for_url}" if not model_for_url.startswith("models/") else model_for_url

            payload = {
                "model": model_for_payload,
                "content": {"parts": [{"text": text}]},
                "outputDimensionality": self.output_dimensionality
            }
            if task_type:
                payload["taskType"] = task_type
            
            with httpx.Client(timeout=30.0) as client:
                response = client.post(url, json=payload)
                response.raise_for_status()
                data = response.json()
                
                embedding = data.get("embedding", {})
                values = embedding.get("values", [])
                return values if values else None
        except Exception as e:
            logger.error(f"Embedding failed: {e}")
            return None
    
    def embed_batch(self, texts: List[str], task_type: Optional[str] = None) -> List[List[float]]:
        """여러 텍스트 배치 임베딩"""
        results = []
        for text in texts:
            emb = self.embed(text, task_type)
            results.append(emb if emb else [])
        return results
    
    def get_output_dimensionality(self) -> int:
        return self.output_dimensionality
