# api/schemas.py
from pydantic import BaseModel
from typing import List, Optional, Dict, Any

class OCRParsed(BaseModel):
    image: str
    merchant: Optional[str] = None
    date: Optional[str] = None
    datetime: Optional[str] = None
    items: List[str] = []
    total: Optional[float] = None
    total_confidence: Optional[float] = None

class OCRResponse(BaseModel):
    raw_json: Dict[str, Any]
    parsed_json: OCRParsed

class ClassifyRequest(BaseModel):
    merchant: Optional[str] = None
    items: List[str] = []

class ClassifyTopK(BaseModel):
    category: str
    score: float

class ClassifyResponse(BaseModel):
    category: str
    confidence: float
    topk: List[ClassifyTopK]
    model_version: str = "rule_seed_v1"

class ProcessResponse(BaseModel):
    raw_json: Dict[str, Any]
    parsed_json: OCRParsed
    classification: ClassifyResponse
