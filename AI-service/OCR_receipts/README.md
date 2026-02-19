# 전체 파이프라인 정리
이미지
 └─ Detection (박스 찾기)
     └─ Recognition (글자 읽기)
         └─ Line grouping / merge (후처리)
             └─ 정보추출 모델(KIE / NER)
                 └─ DB 저장

# 프로젝트구조
OCR_bills/
  data/
    raw_images/            # 원본 영수증 이미지
    det_crops/             # detection 결과 crop 이미지(자동 생성)
    rec_labels/            # recognition 학습 라벨(csv)
    splits/                # train/val/test split 파일
  src/
    det/                   # detection wrapper (기존 모델/라이브러리)
      run_detection.py     #  
    rec/                   # recognition (CRNN + CTC)
    extract/               # JSON 추출(규칙 + 옵션 NER)
    parse/
      run_ocr_to_json.py   # 원본 영수증 이미지를 json형태로 변환로직
      line_grouping.py     # 핵심 함수 모음
      run_line_grouping.py # 실제로 json/*.json 읽어서 outputs/lines/*.json로 저장하는 실행 스크립트
      total_mapping.py     # 총액 추출 함수들 / 후처리/정보추출(rule-based IE) 
                           # 후처리/정보추출(rule-based IE)
      run_total_mapping.py # outputs/lines 읽어서 outputs/parsed로 결과 저장
      pipeline.py          # (기존 parse 모듈을 “함수로” 묶는 얇은 래퍼)
    api/
      app.py           # (FastAPI 엔트리포인트) FASTAPI 본체
      schemas.py       # (요청/응답 모델)
    ml/
      rule_seed.py                  # (초기 룰 기반 분류기)
      train_text_classifier.py      # 학습 스크립트
      text_model.py          # 모델 로더/추론
      model.joblib                  # 저장 모델
      label_encoder.joblib          # 라벨 매핑
      artifacts.gitkeep          # 모델 저장 폴더자리
    utils/
  models/                  # 학습된 모델 가중치/체크포인트
  outputs/
    ocr_text/              # 이미지별 OCR 결과 텍스트
    json/                  # 최종 JSON 결과
  notebooks/               # EDA/실험 노트북(선택)
  requirements.txt
  requirements-dev.txt
  README.md

# 사전설치

# 현재 numpy/imgaug가 있으면 제거 (numpy version 2.x.x 는 호환불가)
pip uninstall -y numpy imgaug
# numpy 1.26.4 강제설치(deps 차단)
pip install numpy==1.26.4 --no-deps --force-reinstall
# imgaug 재설치 (paddleocr이 import할때 필요)
pip install imgaug==0.4.0 --no-deps
# numpy 버전 확인(필수) 우리 수업때 2버전으로 해서 버전 다운그래이드됐는지 확인해야함. 여기서 반드시 1.26.4
python -c "import numpy; print(numpy.__version__)"
# paddleOCR import 테스트
python -c "from paddleocr import PaddleOCR; PaddleOCR(lang='en'); print('PaddleOCR OK')"
# 전체환경 최종 확인
python -c "import numpy, cv2; from paddleocr import PaddleOCR; print('numpy:', numpy.__version__); print('cv2:', cv2.__version__); PaddleOCR(lang='en'); print('ALL OK')"

# 기대출력
numpy: 1.26.4
cv2: 4.6.0
ALL OK

# 검증 통과하면 바로 
run_detection.py 실행
det_crops/생성 확인

# FASTAPI app실행
pip install fastapi uvicorn
uvicorn api.app:app --reload --host 0.0.0.0 --port 8000

# requirement, dev
requirement.txt - 배포 / 서버용
requirement-dev.txt = 로컬 개발용

# 노트북 실행 시 NumPy 2.x 오류 (RuntimeError: ABI 0x1000009 vs 0x2000000)
# 원인 1) 노트북이 venv가 아닌 다른 Python(커널)으로 실행됨 → 그 환경에 numpy 2가 설치되어 있음.
# 원인 2) pip install jupyter 또는 requirement-dev 시 일부 패키지가 numpy 2를 의존성으로 올려버림.
# 해결:
#   1) venv를 Jupyter 커널로 등록 (가상환경 활성화 후 한 번만):
#      pip install ipykernel
#      python -m ipykernel install --user --name ocr_receipts
#   2) Cursor/VS Code에서 노트북 열고 우측 상단 커널 선택 → Python 환경 → "ocr_receipts" 선택.
#      (또는 "인터프리터 경로 입력"으로 venv\Scripts\python.exe 지정)
#   3) venv 활성화 후 설치 순서: pip install -r requirement.txt → pip install jupyter → pip install numpy==1.26.4 --force-reinstall
#   4) 확인: 터미널에서 .\venv\Scripts\python -c "import numpy; print(numpy.__version__)"  → 1.26.4 여야 함.

<!-- ------------------------------------------------------------------------------------------ -->
# 실행방법

# 가상환경 venv (최초 1회만 생성)
cd d:\VScode\smartBudget\smartbudget\OCR_receipts
python -m venv venv

# 가상환경 venv 켜기 (PowerShell)
.\venv\Scripts\Activate.ps1
# CMD 사용 시: venv\Scripts\activate.bat

# OCR_bills
영수증 이미지 → OCR → 라인그룹핑 → 필드 파싱(MERCHANT/DATE/ITEMS/TOTAL) → 카테고리 분류(룰/ML fallback) 파이프라인.

## Quickstart
### 1) 설치
```bash
pip install -r requirement.txt
```

### 1-2) 오류 시
- **`np.sctypes was removed in NumPy 2.0`** → paddleocr/imgaug는 numpy 2 미지원. `requirement.txt`대로 numpy 1.26.4 사용 후 `pip install -r requirement.txt` 재설치.
- **cv2 ABI 오류** (numpy 2 vs opencv 4.6) → uvicorn 종료 후 `pip uninstall opencv-python opencv-contrib-python -y` 또는 `.\fix_opencv.ps1`.

### 2) (선택) 더미데이터 학습
데이터 예시(JSONL):
```jsonl
{"merchant":"KFC수원역점","items":["징거버거 6400 12800"],"label":"식비"}
{"merchant":"SKT","items":["휴대폰요금 89000"],"label":"통신비"}
```

학습:
```bash
python -m src.ml.train_text_classifier --data data/train.jsonl --out_dir src/ml/artifacts
```

### 3) API 실행
```bash
uvicorn src.api.app:app --reload --host 0.0.0.0 --port 8000
```

- `src/ml/artifacts/model.joblib`가 있으면 ML로 분류
- 없으면 `src/ml/rule_seed.py` 룰 기반 분류로 fallback