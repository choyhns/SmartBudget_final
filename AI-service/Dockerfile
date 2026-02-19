# AI-service 통합 컨테이너용
FROM python:3.10-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# OCR/ML 런타임에서 자주 필요한 시스템 패키지
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libglib2.0-0 \
    libgl1 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# requirements만 먼저 복사해서 pip install 레이어 캐시 활용
COPY ml-server/requirements.txt /tmp/ml-requirements.txt
COPY OCR_receipts/requirement.txt /tmp/ocr-requirement.txt
COPY recommendation/requirements.txt /tmp/rec-requirements.txt

# requirements 기준으로만 설치 (requirements 바뀔 때만 재실행)
RUN pip install --upgrade pip && \
    if [ -f /tmp/ml-requirements.txt ]; then pip install -r /tmp/ml-requirements.txt; fi && \
    if [ -f /tmp/ocr-requirement.txt ]; then pip install -r /tmp/ocr-requirement.txt; fi && \
    if [ -f /tmp/rec-requirements.txt ]; then pip install -r /tmp/rec-requirements.txt; fi

# 소스 전체 복사는 마지막
COPY . /app

EXPOSE 8000 8001

# run_all.py로 8000/8001 동시 기동
CMD ["python", "run_all.py"]
