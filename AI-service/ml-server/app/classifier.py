"""
카테고리 분류기 - ML 모델로 거래 내역 분류
"""
import os
import re
import logging
from typing import Dict, Any, List, Optional
import joblib

logger = logging.getLogger(__name__)

# 형태소 분석기 (KoNLPy)
try:
    from konlpy.tag import Okt
    KONLPY_AVAILABLE = True
except ImportError:
    KONLPY_AVAILABLE = False
    logger.warning("KoNLPy not available, using simple tokenizer")

# ML 모델
try:
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.naive_bayes import MultinomialNB
    from sklearn.pipeline import Pipeline
    SKLEARN_AVAILABLE = True
except ImportError:
    SKLEARN_AVAILABLE = False
    logger.warning("scikit-learn not available")


class CategoryClassifier:
    """카테고리 분류기"""
    
    MODEL_PATH = "models/category_classifier.pkl"
    
    def __init__(self):
        self.model = None
        self.okt = None
        
        # 형태소 분석기 초기화
        if KONLPY_AVAILABLE:
            try:
                self.okt = Okt()
                logger.info("KoNLPy Okt 초기화 완료")
            except Exception as e:
                logger.warning(f"Okt 초기화 실패: {e}")
        
        # 저장된 모델 로드
        self._load_model()
        
        # 기본 키워드 기반 규칙 (모델이 없을 때 fallback)
        self.keyword_rules = {
            "식비": ["스타벅스", "카페", "커피", "맥도날드", "롯데리아", "버거킹", 
                   "치킨", "피자", "배달", "음식", "식당", "레스토랑", "분식",
                   "편의점", "마트", "이마트", "홈플러스", "GS25", "CU", "세븐일레븐"],
            "교통": ["택시", "카카오택시", "버스", "지하철", "주유", "SK에너지",
                   "GS칼텍스", "주차", "톨게이트", "KTX", "SRT", "항공", "기차"],
            "쇼핑": ["무신사", "쿠팡", "네이버", "옥션", "지마켓", "11번가",
                   "백화점", "현대백화점", "신세계", "아울렛", "다이소", "올리브영"],
            "주거/통신": ["SKT", "KT", "LG", "통신", "전기", "가스", "수도",
                       "관리비", "월세", "보험료", "인터넷"],
            "금융/보험": ["보험", "삼성화재", "현대해상", "은행", "이자", "수수료",
                       "카드", "대출"],
            "취미/여가": ["넷플릭스", "왓챠", "영화", "CGV", "롯데시네마", "메가박스",
                       "게임", "PC방", "노래방", "헬스", "운동", "피트니스", "요가"],
            "의료/건강": ["병원", "약국", "의원", "치과", "안과", "약", "진료",
                       "건강검진", "정형외과"],
            "교육": ["학원", "인강", "강의", "책", "교재", "시험", "자격증"],
            "기타": []
        }
    
    def is_ready(self) -> bool:
        """분류기 준비 상태"""
        return self.model is not None or len(self.keyword_rules) > 0
    
    def classify(self, merchant: str, memo: Optional[str], 
                 categories: List[str]) -> Dict[str, Any]:
        """
        거래 내역 카테고리 분류
        """
        text = f"{merchant} {memo or ''}"
        
        # 1. 학습된 모델이 있으면 사용
        if self.model and SKLEARN_AVAILABLE:
            try:
                prediction = self._predict_with_model(text, categories)
                if prediction["confidence"] > 0.5:
                    return prediction
            except Exception as e:
                logger.warning(f"모델 예측 실패: {e}")
        
        # 2. 키워드 기반 규칙 사용 (fallback)
        return self._predict_with_rules(text, categories)
    
    def train(self, training_data: List[Dict]) -> Dict[str, Any]:
        """
        분류 모델 학습
        
        training_data: [
            {"text": "스타벅스 아메리카노", "category": "식비"},
            {"text": "카카오택시", "category": "교통"},
            ...
        ]
        """
        if not SKLEARN_AVAILABLE:
            return {"success": False, "error": "scikit-learn이 설치되지 않았습니다"}
        
        if len(training_data) < 10:
            return {"success": False, "error": "학습 데이터가 부족합니다 (최소 10개)"}
        
        try:
            texts = [item["text"] for item in training_data]
            labels = [item["category"] for item in training_data]
            
            # 전처리
            processed_texts = [self._preprocess(t) for t in texts]
            
            # 파이프라인 구성
            self.model = Pipeline([
                ('tfidf', TfidfVectorizer(
                    tokenizer=self._tokenize,
                    max_features=5000,
                    ngram_range=(1, 2)
                )),
                ('clf', MultinomialNB(alpha=0.1))
            ])
            
            # 학습
            self.model.fit(processed_texts, labels)
            
            # 모델 저장
            self._save_model()
            
            return {
                "success": True,
                "samples": len(training_data),
                "categories": list(set(labels))
            }
            
        except Exception as e:
            logger.error(f"학습 실패: {e}")
            return {"success": False, "error": str(e)}
    
    def _predict_with_model(self, text: str, categories: List[str]) -> Dict[str, Any]:
        """ML 모델로 예측"""
        processed = self._preprocess(text)
        
        # 예측
        prediction = self.model.predict([processed])[0]
        probabilities = self.model.predict_proba([processed])[0]
        
        # 신뢰도
        classes = self.model.classes_
        confidence = float(max(probabilities))
        
        # 상위 예측 결과
        top_indices = probabilities.argsort()[-3:][::-1]
        top_predictions = [
            {"category": classes[i], "confidence": float(probabilities[i])}
            for i in top_indices
        ]
        
        # 예측 카테고리가 사용 가능한 카테고리에 있는지 확인
        if prediction not in categories:
            # 가장 가까운 카테고리 찾기
            prediction = self._find_closest_category(prediction, categories)
        
        return {
            "category": prediction,
            "confidence": confidence,
            "reason": f"ML 모델 예측 (신뢰도: {confidence:.1%})",
            "top_predictions": top_predictions
        }
    
    def _predict_with_rules(self, text: str, categories: List[str]) -> Dict[str, Any]:
        """키워드 규칙 기반 예측"""
        text_lower = text.lower()
        
        best_category = "기타"
        best_score = 0
        scores = {}
        
        for category, keywords in self.keyword_rules.items():
            if category not in categories:
                continue
                
            score = 0
            for keyword in keywords:
                if keyword.lower() in text_lower:
                    score += 1
            
            scores[category] = score
            if score > best_score:
                best_score = score
                best_category = category
        
        # 신뢰도 계산
        total_score = sum(scores.values()) or 1
        confidence = best_score / total_score if best_score > 0 else 0.3
        
        # 결과가 없으면 기본값
        if best_score == 0:
            best_category = categories[-1] if categories else "기타"
            confidence = 0.3
        
        return {
            "category": best_category,
            "confidence": min(confidence, 0.9),
            "reason": f"키워드 매칭 (매칭 수: {best_score})",
            "top_predictions": None
        }
    
    def _preprocess(self, text: str) -> str:
        """텍스트 전처리"""
        # 특수문자 제거
        text = re.sub(r'[^\w\s가-힣]', ' ', text)
        # 연속 공백 제거
        text = re.sub(r'\s+', ' ', text)
        return text.strip()
    
    def _tokenize(self, text: str) -> List[str]:
        """토큰화"""
        if self.okt:
            # 형태소 분석
            return self.okt.morphs(text)
        else:
            # 단순 공백 분리
            return text.split()
    
    def _find_closest_category(self, predicted: str, available: List[str]) -> str:
        """가장 유사한 카테고리 찾기"""
        predicted_lower = predicted.lower()
        for cat in available:
            if cat.lower() in predicted_lower or predicted_lower in cat.lower():
                return cat
        return available[-1] if available else predicted
    
    def _load_model(self):
        """저장된 모델 로드"""
        if os.path.exists(self.MODEL_PATH):
            try:
                self.model = joblib.load(self.MODEL_PATH)
                logger.info("분류 모델 로드 완료")
            except Exception as e:
                logger.warning(f"모델 로드 실패: {e}")
    
    def _save_model(self):
        """모델 저장"""
        try:
            os.makedirs(os.path.dirname(self.MODEL_PATH), exist_ok=True)
            joblib.dump(self.model, self.MODEL_PATH)
            logger.info("분류 모델 저장 완료")
        except Exception as e:
            logger.warning(f"모델 저장 실패: {e}")


# 테스트용
if __name__ == "__main__":
    classifier = CategoryClassifier()
    print(f"Classifier Ready: {classifier.is_ready()}")
    
    # 테스트
    result = classifier.classify(
        merchant="스타벅스 강남점",
        memo="아메리카노",
        categories=["식비", "교통", "쇼핑", "기타"]
    )
    print(f"Result: {result}")
