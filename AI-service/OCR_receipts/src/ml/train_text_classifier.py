"""ml/train_text_classifier.py

더미 JSON(또는 실데이터 JSON)로 텍스트 분류 모델을 학습한다.

입력 데이터 형식(각 레코드):
{
  "merchant": "KFC수원역점",
  "items": ["징거버거 6400 12800", "치킨12조각 20900 20900"],
  "label": "식비"   # 또는 category
}

지원 입력:
- 단일 JSONL 파일(--data)
- 폴더 내 *.json / *.jsonl(--data_dir)

아티팩트 출력:
- src/ml/artifacts/model.joblib
- src/ml/artifacts/meta.json

모델:
- FeatureUnion(TF-IDF word + TF-IDF char_wb)
- LinearSVC + CalibratedClassifierCV (확률 출력)

실행 예:
python -m ml.train_text_classifier --data_dir data/train_json --out_dir src/ml/artifacts
python -m ml.train_text_classifier --data data/train.jsonl --out_dir src/ml/artifacts
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple

import joblib
import numpy as np

from sklearn.calibration import CalibratedClassifierCV
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import classification_report, f1_score
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC
from sklearn.pipeline import FeatureUnion


def _read_jsonl(path: Path) -> List[Dict[str, Any]]:
    rows: List[Dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _iter_records(data_path: Optional[Path], data_dir: Optional[Path]) -> Iterable[Dict[str, Any]]:
    if data_path:
        if data_path.suffix.lower() in [".jsonl", ".jsonl.txt"]:
            for r in _read_jsonl(data_path):
                yield r
        elif data_path.suffix.lower() == ".json":
            obj = _read_json(data_path)
            if isinstance(obj, list):
                for r in obj:
                    yield r
            elif isinstance(obj, dict):
                # dict 안에 records 키가 있을 수도
                if "records" in obj and isinstance(obj["records"], list):
                    for r in obj["records"]:
                        yield r
                else:
                    yield obj
        else:
            raise ValueError(f"Unsupported file: {data_path}")

    if data_dir:
        for p in sorted(data_dir.rglob("*")):
            if p.is_dir():
                continue
            suf = p.suffix.lower()
            if suf == ".jsonl":
                for r in _read_jsonl(p):
                    yield r
            elif suf == ".json":
                obj = _read_json(p)
                if isinstance(obj, list):
                    for r in obj:
                        yield r
                elif isinstance(obj, dict):
                    yield obj


def _normalize(s: Any) -> str:
    return str(s or "").strip()


def build_text(merchant: str, items: List[str]) -> str:
    merchant = _normalize(merchant)
    items = items or []
    if isinstance(items, str):
        items = [items]
    items = [_normalize(x) for x in items if _normalize(x)]
    return (merchant + " " + " ".join(items)).strip()


def build_group_id(merchant: str, strategy: str = "merchant") -> str:
    """GroupSplit용 그룹키.

    - merchant: 동일 merchant는 같은 그룹
    - merchant_norm: 공백/특수문자 일부 제거 (템플릿이 조금 달라도 묶이게)
    """
    m = _normalize(merchant)
    if strategy == "merchant_norm":
        # 너무 빡세게 정규화하면 다른 가게도 섞일 수 있으니 적당히
        return "".join(ch for ch in m.lower() if ch.isalnum() or ch in "가나다라마바사아자차카타파하")
    return m


def load_dataset(data_path: Optional[Path], data_dir: Optional[Path]) -> Tuple[List[str], List[str], List[str]]:
    X: List[str] = []
    y: List[str] = []
    g: List[str] = []

    def _ends_food(s: str) -> bool:
        n = _normalize(s)
        return n.endswith("밥") or n.endswith("탕") or n.endswith("국")

    for r in _iter_records(data_path, data_dir):
        merchant = r.get("merchant") or r.get("MERCHANT") or ""
        items = r.get("items") or r.get("ITEMS") or []
        label = r.get("label") or r.get("category") or r.get("CATEGORY")

        merchant_s = _normalize(merchant)
        if isinstance(items, str):
            items_list = [items]
        else:
            items_list = list(items) if isinstance(items, list) else []

        # ~밥, ~탕, ~국으로 끝나면 카테고리를 식비로 변경
        if any(_ends_food(it) for it in items_list) or _ends_food(merchant):
            label = "식비"

        text = build_text(merchant_s, items_list)
        if not text:
            continue
        if not label:
            continue

        X.append(text)
        y.append(_normalize(label))
        g.append(build_group_id(merchant_s, strategy="merchant_norm"))

    if len(X) < 50:
        raise ValueError(f"Too few samples: {len(X)} (need at least ~50)")

    return X, y, g


def make_model(random_state: int = 42) -> Pipeline:
    word = TfidfVectorizer(
        analyzer="word",
        ngram_range=(1, 2),
        min_df=2,
        max_features=120_000,
        sublinear_tf=True,
    )
    char = TfidfVectorizer(
        analyzer="char_wb",
        ngram_range=(2, 5),
        min_df=2,
        max_features=200_000,
        sublinear_tf=True,
    )

    feats = FeatureUnion([
        ("word", word),
        ("char", char),
    ])

    base = LinearSVC(random_state=random_state)

    return Pipeline([
        ("feats", feats),
        ("clf", base),
    ])


def group_split(
    X: List[str],
    y: List[str],
    groups: List[str],
    test_size: float,
    random_state: int,
) -> Tuple[np.ndarray, np.ndarray]:
    splitter = GroupShuffleSplit(n_splits=1, test_size=test_size, random_state=random_state)
    idx = np.arange(len(X))
    train_idx, test_idx = next(splitter.split(idx, y=y, groups=groups))
    return train_idx, test_idx


def evaluate(name: str, y_true: List[str], y_pred: List[str]) -> float:
    macro = float(f1_score(y_true, y_pred, average="macro"))
    print(f"\n[{name}] macro_f1 = {macro:.4f}")
    print(classification_report(y_true, y_pred, digits=4))
    return macro


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", type=str, default=None, help="JSONL/JSON file path")
    ap.add_argument("--data_dir", type=str, default=None, help="directory containing json/jsonl")
    ap.add_argument("--out_dir", type=str, default="src/ml/artifacts")
    ap.add_argument("--random_state", type=int, default=42)
    ap.add_argument("--test_size", type=float, default=0.10)
    ap.add_argument("--val_size", type=float, default=0.10)
    args = ap.parse_args()

    data_path = Path(args.data) if args.data else None
    data_dir = Path(args.data_dir) if args.data_dir else None

    X, y, g = load_dataset(data_path, data_dir)
    print(f"Loaded samples: {len(X)}")

    # 8:1:1 split (group-aware)
    trainval_idx, test_idx = group_split(X, y, g, test_size=args.test_size, random_state=args.random_state)

    X_trainval = [X[i] for i in trainval_idx]
    y_trainval = [y[i] for i in trainval_idx]
    g_trainval = [g[i] for i in trainval_idx]

    val_rel = args.val_size / (1.0 - args.test_size)
    train_idx2, val_idx2 = group_split(X_trainval, y_trainval, g_trainval, test_size=val_rel, random_state=args.random_state + 1)

    X_train = [X_trainval[i] for i in train_idx2]
    y_train = [y_trainval[i] for i in train_idx2]

    X_val = [X_trainval[i] for i in val_idx2]
    y_val = [y_trainval[i] for i in val_idx2]

    X_test = [X[i] for i in test_idx]
    y_test = [y[i] for i in test_idx]

    print(f"Split sizes -> train: {len(X_train)}, val: {len(X_val)}, test: {len(X_test)}")


    base = make_model(random_state=args.random_state)

    # 1) base는 train으로 학습
    base.fit(X_train, y_train)

    # 2) val로 확률 보정 (cv="prefit" 핵심)
    model = CalibratedClassifierCV(estimator=base, cv="prefit", method="sigmoid")
    model.fit(X_val, y_val)

    # validation (calibrated 모델로 평가)
    y_val_pred = model.predict(X_val)
    val_macro = evaluate("VAL", y_val, list(y_val_pred))

    # test 평가
    y_test_pred = model.predict(X_test)
    test_macro = evaluate("TEST", y_test, list(y_test_pred))

    # ----------------------------
    # 저장 경로 (코드와 산출물 분리)
    # ----------------------------
    out_dir = Path(args.out_dir)  # 예: models/textclf
    out_dir.mkdir(parents=True, exist_ok=True)

    model_path = out_dir / "model.joblib"
    meta_path  = out_dir / "meta.json"

    # 저장
    joblib.dump(model, model_path)

    # CalibratedClassifierCV는 classes_를 가짐
    classes = [str(c) for c in getattr(model, "classes_", [])]

    meta = {
        "model_version": "text_tfidf_svc_calibrated_v1",
        "n_samples": len(X),
        "split": {"train": len(X_train), "val": len(X_val), "test": len(X_test)},
        "macro_f1": {"val": float(val_macro), "test": float(test_macro)},
        "classes": classes,
        # (선택) 학습 옵션 기록해두면 나중에 디버깅/재현에 좋음
        "train_args": {
            "random_state": args.random_state,
            "test_size": args.test_size,
            "val_size": args.val_size,
            "out_dir": str(out_dir),
        },
    }
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\nSaved model -> {model_path}")
    print(f"Saved meta  -> {meta_path}")



if __name__ == "__main__":
    main()
