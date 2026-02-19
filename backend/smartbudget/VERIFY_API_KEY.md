# 인증키 구동 확인 체크리스트

## 1. 설정 확인

- [x] `.env`에 `DATA_GO_KR_API_KEY=...` 입력됨
- [x] `application.properties`에서 `app.finance.api.key=${DATA_GO_KR_API_KEY:}` 로 바인딩
- [x] 인증키가 **이미 URL 인코딩**(`%2F`, `%3D` 등)된 형태면 그대로 사용하도록 코드 수정됨 (이중 인코딩 방지)

## 2. 로컬에서 확인 순서

1. **백엔드 기동**
   ```bash
   cd backend/smartbudget
   ./gradlew bootRun
   ```
   (Windows: `gradlew.bat bootRun`)

2. **카드 목록** (신한카드 JSON 자동 로드)
   ```bash
   curl -s http://localhost:8080/api/recommendations/cards | head -c 500
   ```
   → DB에 카드가 없으면 `data/shinhancard_cards.json` + 체크카드 JSON이 DB에 적재된 뒤 목록 반환

3. **금융상품 목록** (공공데이터 API 호출)
   ```bash
   curl -s http://localhost:8080/api/recommendations/products | head -c 500
   ```
   → DB에 상품이 없고 API 키가 설정되어 있으면 한국산업은행 예금상품 API 호출 후 DB 적재 후 목록 반환

4. **에러 시**
   - API가 `30`(인증키 오류) 또는 **401 UNAUTHORIZED**:  
     1) 로그에 찍힌 **응답 본문**을 확인 (서버가 내려주는 오류 메시지).  
     2) 공공데이터포털 → 마이페이지 → 인증키 관리에서 **한국산업은행 예금상품 정보** API가 **활용승인** 상태인지 확인.  
     3) **일반인증키(비인코딩)**를 넣었는데 401이면: `.env`에 `DATA_GO_KR_API_KEY_ENCODED=true` 추가 후, **인코딩된 인증키**를 복사해 `DATA_GO_KR_API_KEY`에 넣어 보세요.  
     4) **인코딩된 인증키**를 넣었는데 401이면: `DATA_GO_KR_API_KEY_ENCODED`를 제거하거나 false로 두고, **일반인증키**를 넣어 보세요.  
     5) 키 앞뒤 공백·줄바꿈이 없어야 합니다.  
   - 빈 배열 `[]`만 나오면: 해당 API 활용신청·승인 여부, 요청 일자(`sBseDt`, `eBseDt`) 범위 확인.

## 3. 코드 경로 요약

| 역할 | 파일 | 내용 |
|------|------|------|
| 인증키 주입 | `application.properties` | `app.finance.api.key=${DATA_GO_KR_API_KEY:}` |
| API 호출 | `FinancialProductApiService.java` | `DATA_GO_KR_API_KEY` → 쿼리 `serviceKey`에 그대로 사용 (이중 인코딩 없음) |
| 상품 사용 | `RecommendationService.getAllProducts()` | DB 비어 있으면 API 호출 결과 DB 적재 후 반환 |

이 순서대로 실행해 보시고, 2·3번에서 카드/금융상품이 내려오면 구동에 문제 없는 상태입니다.
