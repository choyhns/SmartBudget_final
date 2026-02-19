"""
OCR 스텁: 실제 영수증 OCR은 8000/receipts(PaddleOCR)에서 처리합니다.
ml-server(8000)에서는 OCR 엔진을 로드하지 않으며, /api/ocr·/process 호출 시
8000/receipts 사용을 안내합니다.
"""
import logging
from typing import Dict, Any

logger = logging.getLogger(__name__)

OCR_RECEIPTS_MESSAGE = (
    "영수증 OCR은 8000/receipts(PaddleOCR)를 사용하세요. "
    "application.properties에서 ocr.receipts.server.url=http://127.0.0.1:8000/receipts 로 설정 후 해당 서버의 /process 를 호출하세요."
)


class OCRService:
    """OCR 스텁. 실제 OCR은 8000/receipts에서만 수행."""

    def __init__(self):
        logger.info("OCR 스텁 로드됨 (실제 OCR는 8000/receipts 사용)")

    def is_ready(self) -> bool:
        return False

    def process_receipt(self, base64_image: str, mime_type: str) -> Dict[str, Any]:
        return {
            "success": False,
            "error": OCR_RECEIPTS_MESSAGE,
            "store_name": None,
            "date": None,
            "time": None,
            "total_amount": None,
            "items": [],
            "confidence": 0.0,
        }
