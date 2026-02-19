# 카드·금융상품 추천 설정

## 1. 카드 데이터 (신한카드)

- **소스**: `shinhancard_credit_cards.json`(신용카드), `shinhancard_check_cards.json`(체크카드)
- **위치**: 기본값은 `src/main/resources/data/` (classpath). 프로젝트 루트 JSON을 쓰려면 `.env`에 경로 설정 가능.
- **동작**: DB에 카드가 없을 때 위 JSON을 읽어 `cards` 테이블에 넣은 뒤, 추천/목록에 사용합니다.
- **카드 이미지**: JSON에 `image_url`이 있으면 추천 페이지에 카드 이미지가 표시됩니다. 기존 DB를 쓰는 경우 아래 마이그레이션을 실행하세요.

### cards 테이블에 image_url 추가 (선택)

```sql
ALTER TABLE projectdb.cards ADD COLUMN IF NOT EXISTS image_url TEXT;
```

### 선택 설정 (.env)

```env
# 파일 경로로 지정 시 (프로젝트 루트 기준 상대경로 예시)
# APP_SHINHAN_CARDS_CREDIT_PATH=../../shinhancard_cards.json
# APP_SHINHAN_CARDS_CHECK_PATH=../../shinhancard_check_cards.json
```

## 2. 금융상품 (공공데이터포털 API)

- **API**: 공공데이터포털 → [한국산업은행 예금상품 정보](https://www.data.go.kr/data/15060634/openapi.do) (B190030 GetDepositProductInfoService)
- **동작**: DB에 금융상품이 없고 API 키가 설정되어 있으면, 예금/적금 상품을 조회해 `products` 테이블에 넣고 추천에 사용합니다.
- **상품 수가 적은 이유**: 이 API는 **한국산업은행 한 곳**만 제공하는 정책은행용 예금/적금이라, 상품 종류가 수~수십 개 수준입니다. 조회 기간은 2020-06-23(제공 시작일)~현재로 넓혀 두었고, API 결과가 15건 미만이면 더미 상품을 붙여 목록을 보강합니다. 더 많은 실계좌 상품이 필요하면 [우체국 예금상품](https://www.data.go.kr) 등 다른 API를 활용신청해 추가 연동할 수 있습니다.

### API 키 설정 (.env)

1. [공공데이터포털](https://www.data.go.kr) 로그인 → **한국산업은행 예금상품 정보** API 활용신청
2. 인증키(일반인증키) 발급
3. `backend/smartbudget/.env`에 추가:

```env
# 공공데이터포털 오픈 API 키 (금융상품 조회)
DATA_GO_KR_API_KEY=발급받은_인증키_문자열
```

Spring Boot는 `DATA_GO_KR_API_KEY`를 `app.finance.api.key`로 바인딩합니다.

## 3. 추천 흐름

1. **카드**: `GET /api/recommendations/cards` → DB 비어 있으면 신한카드 JSON 로드 후 DB 적재 → 목록 반환
2. **금융상품**: `GET /api/recommendations/products` → DB 비어 있고 API 키 있으면 공공데이터 API 호출 후 DB 적재 → 목록 반환
3. **추천 생성**: `POST /api/recommendations/generate` → 소비 패턴 + 위 카드/상품으로 LLM 추천 생성
