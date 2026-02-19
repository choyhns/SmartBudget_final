"""ml/text_model.py

학습된 텍스트 분류 모델 로더/추론 유틸.

- 입력: merchant(str), items(List[str])
- 출력: (best_category, confidence, topk)

저장 아티팩트 기본 경로:
- src/ml/artifacts/model.joblib
- src/ml/artifacts/meta.json

주의:
- model은 scikit-learn Pipeline이며 predict_proba 지원을 전제로 함.
  (LinearSVC를 쓸 경우 CalibratedClassifierCV로 감싸서 확률을 만들도록 train 스크립트에서 구성)
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import List, Tuple, Optional

import joblib


def _normalize(s: str) -> str:
    return (s or "").strip()


def build_text(merchant: str | None, items: List[str] | None) -> str:
    """merchant + items를 하나의 텍스트로 합친다."""
    merchant = _normalize(merchant or "")
    items = items or []
    if isinstance(items, str):
        items = [items]
    items = [str(x).strip() for x in items if str(x).strip()]
    return (merchant + " " + " ".join(items)).strip()


@dataclass
class TextClassifier:
    model: object
    meta: dict

    @property
    def model_version(self) -> str:
        return str(self.meta.get("model_version") or "text_model")

    @property
    def classes_(self) -> List[str]:
        # scikit-learn estimator의 classes_를 우선 사용
        if hasattr(self.model, "classes_"):
            return [str(c) for c in getattr(self.model, "classes_")]
        return [str(c) for c in self.meta.get("classes", [])]

    def predict(self, merchant: str | None, items: List[str] | None, topk: int = 3) -> Tuple[str, float, List[Tuple[str, float]]]:
        text = build_text(merchant, items)
        if not text:
            return "기타", 0.0, [("기타", 0.0)]

        # predict_proba 지원 모델 가정
        if not hasattr(self.model, "predict_proba"):
            # 확률이 없으면 decision_function으로 softmax 흉내(간단)
            if hasattr(self.model, "decision_function"):
                import numpy as np
                scores = self.model.decision_function([text])
                scores = np.atleast_2d(scores)
                # 안정화 softmax
                exps = np.exp(scores - scores.max(axis=1, keepdims=True))
                probs = exps / exps.sum(axis=1, keepdims=True)
            else:
                pred = self.model.predict([text])[0]
                return str(pred), 0.0, [(str(pred), 0.0)]
        else:
            probs = self.model.predict_proba([text])

        probs = probs[0]
        classes = self.classes_
        pairs = list(zip(classes, [float(p) for p in probs]))
        pairs.sort(key=lambda x: x[1], reverse=True)
        best_cat, best_p = pairs[0]
        return best_cat, float(best_p), pairs[:topk]


def load_text_classifier(
    artifacts_dir: str | Path = "models/textclf",
) -> Optional[TextClassifier]:
    """아티팩트가 존재하면 로드, 없으면 None."""
    artifacts_dir = Path(artifacts_dir)
    model_path = artifacts_dir / "model.joblib"
    meta_path = artifacts_dir / "meta.json"

    if not model_path.exists():
        return None

    model = joblib.load(model_path)
    meta = {}
    if meta_path.exists():
        try:
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
        except Exception:
            meta = {}

    return TextClassifier(model=model, meta=meta)
