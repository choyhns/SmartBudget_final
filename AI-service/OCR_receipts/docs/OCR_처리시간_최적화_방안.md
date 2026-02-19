# OCR 처리 시간 최적화 방안

> 현재 평균 처리 시간: **13.11초/이미지**  
> 목표: **5초 이하** (약 60% 감소)

---

## 현재 병목 구간 분석

로그 기준:
- **Recognition (rec_res)**: 11~14초 (전체의 85~90%)
- **Detection (dt_boxes)**: 0.15~0.89초
- **Classification (cls)**: 0.27~0.38초

→ **Recognition 단계가 가장 느림**. 이 부분 최적화가 핵심.

---

## 최적화 방안 (우선순위 순)

### 1. 이미지 리사이징 (예상 효과: 30~50% 감소)

**문제**: 원본 이미지가 너무 크면 Recognition이 느려짐.

**해결**: OCR 전에 이미지 크기를 적절히 축소.

```python
# src/parse/run_ocr_to_json.py 수정
import cv2

def resize_for_ocr(image, max_width=1920, max_height=1920):
    """OCR 속도 향상을 위한 이미지 리사이징"""
    h, w = image.shape[:2]
    if w > max_width or h > max_height:
        scale = min(max_width / w, max_height / h)
        new_w, new_h = int(w * scale), int(h * scale)
        image = cv2.resize(image, (new_w, new_h), interpolation=cv2.INTER_AREA)
    return image

def ocr_image_to_raw_json(img_path: Path) -> dict:
    image = cv2.imread(str(img_path))
    if image is None:
        return {"image": img_path.name, "items": []}
    
    # 리사이징 추가
    image = resize_for_ocr(image, max_width=1920, max_height=1920)
    
    result = ocr.ocr(image, cls=True)
    # ... 나머지 동일
```

**예상 시간**: 13초 → **7~9초**

---

### 2. 각도 분류 비활성화 (예상 효과: 10~15% 감소)

**문제**: `use_angle_cls=True`는 정확도는 높지만 속도가 느림.

**해결**: 영수증은 대부분 정방향이므로 각도 분류 생략 가능.

```python
# src/parse/run_ocr_to_json.py
ocr = PaddleOCR(lang="korean", use_angle_cls=False)  # True → False

# 호출 시에도 cls=False
result = ocr.ocr(image, cls=False)  # cls=True → False
```

**주의**: 기울어진 영수증은 정확도가 떨어질 수 있음.  
**예상 시간**: 13초 → **11~12초**

---

### 3. GPU 사용 (예상 효과: 50~70% 감소, GPU 있을 때만)

**문제**: CPU로만 실행 중.

**해결**: CUDA GPU가 있으면 자동 사용.

```python
# src/parse/run_ocr_to_json.py
import paddle
use_gpu = paddle.device.is_compiled_with_cuda() and paddle.device.get_device() == 'gpu:0'

ocr = PaddleOCR(
    lang="korean", 
    use_angle_cls=True,  # GPU 있으면 True 유지 가능
    use_gpu=use_gpu  # GPU 사용
)
```

**예상 시간**: 13초 → **4~6초** (GPU 있을 때)

---

### 4. 더 작은 모델 사용 (예상 효과: 20~30% 감소)

**문제**: 기본 모델이 무거움.

**해결**: 경량 모델 사용 (정확도 약간 하락 가능).

```python
# src/parse/run_ocr_to_json.py
ocr = PaddleOCR(
    lang="korean",
    use_angle_cls=False,
    det_model_dir=None,  # 기본 모델
    rec_model_dir=None,  # 기본 모델
    # 또는 경량 모델 경로 지정
    # rec_model_dir='path/to/lite_model'
)
```

**참고**: PaddleOCR 경량 모델은 별도 다운로드 필요.  
**예상 시간**: 13초 → **9~10초**

---

### 5. 이미지 전처리 최적화 (예상 효과: 5~10% 감소)

**문제**: 불필요한 전처리 또는 최적화되지 않은 이미지.

**해결**: 그레이스케일 변환, 노이즈 제거 등.

```python
def preprocess_for_ocr(image):
    """OCR 전 이미지 전처리"""
    # 그레이스케일 (색상 정보 불필요 시)
    if len(image.shape) == 3:
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        # 다시 3채널로 (PaddleOCR이 BGR 기대)
        image = cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR)
    
    # 대비 향상 (선택)
    # clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8,8))
    # image = clahe.apply(image)
    
    return image

def ocr_image_to_raw_json(img_path: Path) -> dict:
    image = cv2.imread(str(img_path))
    image = resize_for_ocr(image)
    image = preprocess_for_ocr(image)  # 전처리 추가
    result = ocr.ocr(image, cls=False)
    # ...
```

**예상 시간**: 13초 → **12~12.5초**

---

### 6. ONNX/TensorRT 변환 (예상 효과: 40~60% 감소, 고급)

**문제**: PaddlePaddle 모델이 최적화되지 않음.

**해결**: ONNX 또는 TensorRT로 변환 후 추론.

```python
# 별도 스크립트로 모델 변환 필요
# paddle2onnx --model_dir=... --save_file=...
# 또는 TensorRT 변환

# 추론 시 ONNX Runtime 사용
import onnxruntime as ort
# ...
```

**주의**: 변환 작업이 복잡하고, 환경 설정 필요.  
**예상 시간**: 13초 → **5~8초**

---

### 7. 배치 처리 (예상 효과: 10~20% 감소, 여러 이미지 처리 시)

**문제**: 이미지마다 개별 처리.

**해결**: 여러 이미지를 한 번에 처리 (PaddleOCR 배치 지원).

```python
def batch_ocr_images(image_paths: list[Path]) -> list[dict]:
    """여러 이미지를 배치로 처리"""
    images = [cv2.imread(str(p)) for p in image_paths]
    # PaddleOCR 배치 처리 (API 확인 필요)
    results = ocr.ocr_batch(images)  # 예시
    return results
```

**예상 시간**: 이미지당 **11~12초** (배치 오버헤드 감소)

---

## 권장 조합 (목표: 5초 이하)

### 조합 1: 빠른 구현 (CPU 환경)
1. ✅ **이미지 리사이징** (max_width=1920)
2. ✅ **각도 분류 비활성화** (use_angle_cls=False)
3. ✅ **이미지 전처리** (그레이스케일)

**예상 시간**: 13초 → **6~7초** (약 50% 감소)

---

### 조합 2: 최고 성능 (GPU 환경)
1. ✅ **GPU 사용** (use_gpu=True)
2. ✅ **이미지 리사이징** (max_width=1920)
3. ✅ **각도 분류 유지** (GPU 있으면 True 유지 가능)

**예상 시간**: 13초 → **3~4초** (약 70% 감소)

---

### 조합 3: 극한 최적화 (프로덕션)
1. ✅ **ONNX/TensorRT 변환**
2. ✅ **GPU 사용**
3. ✅ **이미지 리사이징**
4. ✅ **경량 모델**

**예상 시간**: 13초 → **2~3초** (약 80% 감소)

---

## 구현 예시 코드

`src/parse/run_ocr_to_json.py` 수정:

```python
import cv2
from paddleocr import PaddleOCR
import paddle

# GPU 사용 가능 여부 확인
USE_GPU = paddle.device.is_compiled_with_cuda() and paddle.device.get_device() == 'gpu:0'

# OCR 초기화 (최적화 옵션)
ocr = PaddleOCR(
    lang="korean",
    use_angle_cls=False,  # 속도 우선 시 False
    use_gpu=USE_GPU,  # GPU 사용
    enable_mkldnn=True,  # CPU 최적화 (Intel CPU)
)

def resize_for_ocr(image, max_width=1920, max_height=1920):
    """OCR 속도 향상을 위한 이미지 리사이징"""
    h, w = image.shape[:2]
    if w > max_width or h > max_height:
        scale = min(max_width / w, max_height / h)
        new_w, new_h = int(w * scale), int(h * scale)
        image = cv2.resize(image, (new_w, new_h), interpolation=cv2.INTER_AREA)
    return image

def ocr_image_to_raw_json(img_path: Path) -> dict:
    image = cv2.imread(str(img_path))
    if image is None:
        return {"image": img_path.name, "items": []}
    
    # 리사이징 추가
    image = resize_for_ocr(image, max_width=1920, max_height=1920)
    
    result = ocr.ocr(image, cls=False)  # cls=False
    # ... 나머지 동일
```

---

## 측정 방법

노트북에서 최적화 전/후 비교:

```python
# 최적화 전
proc_times_before = measure_processing_time()
avg_before = sum(t["time_sec"] for t in proc_times_before) / len(proc_times_before)

# 코드 수정 후
proc_times_after = measure_processing_time()
avg_after = sum(t["time_sec"] for t in proc_times_after) / len(proc_times_after)

print(f"개선: {avg_before:.2f}초 → {avg_after:.2f}초 ({((avg_before-avg_after)/avg_before*100):.1f}% 감소)")
```

---

## 참고

- PaddleOCR 공식 문서: https://github.com/PaddlePaddle/PaddleOCR
- ONNX 변환 가이드: PaddleOCR → ONNX 변환 스크립트 참고
- GPU 설정: CUDA, cuDNN 설치 필요
