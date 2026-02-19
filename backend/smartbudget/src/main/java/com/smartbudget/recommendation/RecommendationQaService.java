package com.smartbudget.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbudget.llm.GeminiService;
import com.smartbudget.llm.PythonAIService;
import com.smartbudget.llm.RecommendationAIService;
import com.smartbudget.monthlyreport.MonthlyReportDTO;
import com.smartbudget.rag.ReportChunkRepository;
import com.smartbudget.monthlyreport.MonthlyReportMapper;
import com.smartbudget.transaction.TransactionDTO;
import com.smartbudget.transaction.TransactionMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 선택지 기반 Q&A (간단 RAG)
 *
 * RAG 구성(현재):
 * - Retrieval: 카드/상품/추천내역/월별리포트/거래를 DB에서 조회 (없으면 더미)
 * - Augmentation: 필요한 근거를 텍스트/수치로 구성
 * - Generation: 템플릿 또는 LLM(Gemini)으로 답변 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationQaService {

  private final RecommendationMapper recommendationMapper;
  private final TransactionMapper transactionMapper;
  private final MonthlyReportMapper monthlyReportMapper;

  @Autowired(required = false)
  private GeminiService geminiService;

  @Autowired(required = false)
  private RecommendationAIService recommendationAIService;

  @Autowired(required = false)
  private RecommendationService recommendationService;

  @Autowired(required = false)
  private CardBenefitSearchService cardBenefitSearchService;

  @Autowired(required = false)
  private ReportChunkRepository reportChunkRepository;

  @Autowired(required = false)
  private PythonAIService pythonAIService;

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  /** 지출 카테고리명 → 카드 혜택 키워드 (절약 추정 시 매칭용, RecommendationService와 동일) */
  private static final Map<String, Set<String>> USER_CATEGORY_TO_BENEFIT_KEYWORDS = buildUserCategoryToBenefitKeywords();

  private static Map<String, Set<String>> buildUserCategoryToBenefitKeywords() {
    Map<String, Set<String>> m = new HashMap<>();
    m.put("주거", Set.of("주거", "관리비", "전기세", "가스비", "수도세", "월세", "전세", "도시가스", "대출이자"));
    m.put("관리비", Set.of("주거", "관리비", "전기세", "가스비", "수도세", "도시가스"));
    m.put("도시가스", Set.of("주거", "가스비", "도시가스", "관리비"));
    m.put("대출이자", Set.of("대출", "이자", "주거", "관리비"));
    m.put("보험", Set.of("보험", "종합", "실비"));
    m.put("종합", Set.of("보험", "종합"));
    m.put("실비", Set.of("보험", "실비"));
    m.put("통신비", Set.of("통신", "통신비", "휴대폰", "인터넷", "TV", "전화"));
    m.put("휴대폰", Set.of("통신", "휴대폰", "통신비"));
    m.put("인터넷/TV", Set.of("통신", "인터넷", "TV", "통신비"));
    m.put("수리/Acc", Set.of("통신", "수리"));
    m.put("식비", Set.of("식비", "외식", "배달", "카페", "음식점", "레스토랑", "패스트푸드", "치킨", "피자", "식자재", "간식", "음료", "배민", "배달앱"));
    m.put("식자재", Set.of("식비", "식자재", "마트", "편의점", "장보기"));
    m.put("배달/외식", Set.of("식비", "외식", "배달", "배민", "배달앱", "치킨", "피자", "카페", "음식점"));
    m.put("간식/음료", Set.of("식비", "간식", "음료", "카페", "편의점"));
    m.put("생활용품", Set.of("생활", "마트", "편의점", "가전", "가구", "인테리어"));
    m.put("생활소모품", Set.of("생활", "마트", "편의점"));
    m.put("가전/가구", Set.of("가전", "가구", "생활"));
    m.put("침구/인테리어", Set.of("인테리어", "가구", "생활"));
    m.put("꾸밈비", Set.of("의류", "잡화", "미용", "헤어", "쇼핑", "백화점"));
    m.put("의류/잡화", Set.of("의류", "잡화", "쇼핑", "백화점"));
    m.put("미용/헤어", Set.of("미용", "헤어", "쇼핑"));
    m.put("세탁/수선", Set.of("세탁", "의류"));
    m.put("건강", Set.of("의료", "병원", "약국", "건강", "보험"));
    m.put("병원/약국", Set.of("병원", "약국", "의료", "건강"));
    m.put("건강보조식품", Set.of("건강", "보조", "약국"));
    m.put("예방/검진", Set.of("건강", "검진", "병원"));
    m.put("자기계발", Set.of("교육", "도서", "운동", "학원"));
    m.put("운동", Set.of("운동", "스포츠", "헬스"));
    m.put("도서", Set.of("도서", "서적", "교육", "책"));
    m.put("자동차", Set.of("교통", "주유", "주차", "통행료", "대중교통", "택시", "하이패스", "세차"));
    m.put("주유", Set.of("주유", "주유소", "교통", "자동차"));
    m.put("주차/통행료", Set.of("주차", "통행료", "하이패스", "고속도로"));
    m.put("대중교통", Set.of("대중교통", "지하철", "버스", "교통"));
    m.put("택시", Set.of("택시", "교통"));
    m.put("소모품/수리", Set.of("자동차", "수리"));
    m.put("보험/세금", Set.of("자동차", "보험", "세금"));
    m.put("세차", Set.of("세차", "자동차"));
    m.put("여행", Set.of("여행", "국내", "해외", "숙박", "리조트", "항공"));
    m.put("국내여행", Set.of("국내여행", "여행", "숙박"));
    m.put("해외여행", Set.of("해외여행", "여행", "항공", "공항"));
    m.put("문화", Set.of("문화", "영화", "OTT", "공연", "콘서트", "전시"));
    m.put("OTT", Set.of("OTT", "영화", "스트리밍"));
    m.put("기타 입장료", Set.of("영화", "공연", "전시"));
    m.put("경조사", Set.of("경조사", "선물"));
    m.put("가족/친척", Set.of("경조사", "선물"));
    m.put("지인/동료", Set.of("경조사", "선물"));
    m.put("기타", Set.of("기타", "세금", "회비"));
    m.put("세금", Set.of("세금"));
    m.put("회비", Set.of("회비"));
    return m;
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * 지출 카테고리와 카드 혜택이 매칭되는지 판단 (키워드 기반).
   * 카테고리명 그대로가 혜택 JSON에 없어도, 동의어/관련 키워드가 있으면 매칭.
   */
  private boolean categoryMatchesCardBenefits(String userCategory, String benefitsJson) {
    if (benefitsJson == null || benefitsJson.isBlank()) return false;
    String benefitTextLower = toBenefitTextLower(benefitsJson);
    if (benefitTextLower.isEmpty()) return false;

    // "일상 결제 캐시백·할인 혜택" 등 일반 혜택 카드는 모든 지출 카테고리와 매칭 (전체 지출에 5% 적용)
    if (benefitTextLower.contains("일상")) {
      return true;
    }

    Set<String> keywordsToMatch = new HashSet<>();
    keywordsToMatch.add(userCategory.toLowerCase());
    // 슬래시 기준 분리 (예: "배달/외식" → "배달", "외식")
    for (String part : userCategory.split("/")) {
      String p = part.trim().toLowerCase();
      if (!p.isEmpty()) keywordsToMatch.add(p);
    }
    Set<String> mapped = USER_CATEGORY_TO_BENEFIT_KEYWORDS.get(userCategory);
    if (mapped != null) {
      mapped.stream().map(String::toLowerCase).forEach(keywordsToMatch::add);
    }
    return keywordsToMatch.stream().anyMatch(kw -> !kw.isEmpty() && benefitTextLower.contains(kw));
  }

  /** 카드 혜택 JSON에서 summary + benefits[].category 를 추출해 소문자 문자열로 반환 */
  private String toBenefitTextLower(String benefitsJson) {
    try {
      JsonNode node = OBJECT_MAPPER.readTree(benefitsJson);
      StringBuilder sb = new StringBuilder();
      if (node.has("summary") && !node.get("summary").asText().isBlank()) {
        sb.append(node.get("summary").asText());
      }
      if (node.has("benefits") && node.get("benefits").isArray()) {
        for (JsonNode b : node.get("benefits")) {
          if (b.has("category")) {
            String cat = b.path("category").asText("").trim();
            if (!cat.isEmpty()) sb.append(" ").append(cat);
          }
        }
      }
      return sb.toString().toLowerCase();
    } catch (Exception e) {
      return (benefitsJson != null ? benefitsJson : "").toLowerCase();
    }
  }

  public List<QaOptionDTO> getOptions() {
    List<QaOptionDTO> list = new ArrayList<>();

    list.add(option(
        "CARD_SAVING_ESTIMATE",
        "이 카드를 사용하면 이번 달(또는 최근 월) 얼마나 절약이 가능할까?",
        "CARD",
        true
    ));
    list.add(option(
        "CARD_BENEFITS_DETAIL",
        "이 카드는 자세한 혜택들이 어떤게 있어?",
        "CARD",
        true
    ));
    list.add(option(
        "CARD_MONTHLY_REQUIREMENT",
        "전월 실적 채워야하는 카드인가?",
        "CARD",
        true
    ));
    list.add(option(
        "WHY_RECOMMENDED",
        "왜 이 카드가 추천됐는지 근거를 설명해줘",
        "RECOMMENDATIONS",
        false
    ));

    return list;
  }

  public QaAnswerResponseDTO answer(QaAnswerRequestDTO req) throws Exception {
    Long userId = req.getUserId() != null ? req.getUserId() : 1L;
    String yearMonth = (req.getYearMonth() != null && !req.getYearMonth().isBlank())
        ? req.getYearMonth()
        : LocalDate.now().format(YM);

    Map<String, Object> spending = safeSpendingPattern(userId, yearMonth);
    spending.put("userId", userId); // explainWhyRecommended에서 사용
    MonthlyReportDTO report = safeMonthlyReport(userId, yearMonth);

    String qid = req.getQuestionId() != null ? req.getQuestionId() : "";
    QaAnswerResponseDTO res = new QaAnswerResponseDTO();
    List<QaAnswerResponseDTO.QaSourceDTO> sources = new ArrayList<>();

    switch (qid) {
      case "CARD_SAVING_ESTIMATE" -> {
        // 추천된 상위 카드 여러 장에 대해 한 번에 절약 금액 설명
        List<CardDTO> cards = resolveRecommendedCardsForExplanation(null, userId);
        if (cards == null || cards.isEmpty()) {
          // 추천 카드가 없으면 기존 단일 카드 로직으로 폴백
          CardDTO card = cardForQuestion(req.getCardId(), userId);
          if (card == null) {
            res.setAnswer("카드 목록을 불러올 수 없어요. 잠시 후 다시 시도해주세요.");
          } else {
            Map<String, Object> spendingLast = safeSpendingPattern(userId, previousYearMonth(yearMonth));
            res.setAnswer(estimateCardSaving(card, spending, spendingLast, report));
            sources.add(source("CARD", card.getName() + " / " + card.getCompany()));
            sources.add(source("TRANSACTIONS", "이번 달·지난달 지출 집계"));
            if (report != null) sources.add(source("MONTHLY_REPORT", "월별 분석 결과"));
          }
        } else {
          Map<String, Object> spendingLast = safeSpendingPattern(userId, previousYearMonth(yearMonth));
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < Math.min(3, cards.size()); i++) {
            CardDTO c = cards.get(i);
            sb.append(estimateCardSaving(c, spending, spendingLast, report)).append("\n\n");
            sources.add(source("CARD", c.getName() + " / " + c.getCompany()));
          }
          sources.add(source("TRANSACTIONS", "이번 달·지난달 지출 집계"));
          if (report != null) sources.add(source("MONTHLY_REPORT", "월별 분석 결과"));
          res.setAnswer(sb.toString().trim());
        }
      }
      case "CARD_BENEFITS_DETAIL" -> {
        // 추천된 상위 카드 여러 장의 자세한 혜택을 한 번에 설명
        List<CardDTO> cards = resolveRecommendedCardsForExplanation(null, userId);
        if (cards == null || cards.isEmpty()) {
          CardDTO card = cardForQuestion(req.getCardId(), userId);
          if (card == null) {
            res.setAnswer("카드 목록을 불러올 수 없어요. 잠시 후 다시 시도해주세요.");
          } else {
            res.setAnswer(explainCardBenefitsDetail(card));
            sources.add(source("CARD", card.getName() + " / " + card.getCompany()));
          }
        } else {
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < Math.min(3, cards.size()); i++) {
            CardDTO c = cards.get(i);
            sb.append(explainCardBenefitsDetail(c)).append("\n\n");
            sources.add(source("CARD", c.getName() + " / " + c.getCompany()));
          }
          res.setAnswer(sb.toString().trim());
        }
      }
      case "CARD_MONTHLY_REQUIREMENT" -> {
        // 추천된 상위 카드 여러 장에 대해 '전월 실적 채워야 하는 카드인가?' 답변
        List<CardDTO> cards = resolveRecommendedCardsForExplanation(null, userId);
        if (cards == null || cards.isEmpty()) {
          CardDTO card = cardForQuestion(req.getCardId(), userId);
          if (card == null) {
            res.setAnswer("카드 목록을 불러올 수 없어요. 잠시 후 다시 시도해주세요.");
          } else {
            String answer = explainCardMonthlyRequirement(card);
            res.setAnswer(answer);
            sources.add(source("CARD", card.getName() + " / " + card.getCompany()));
          }
        } else {
          StringBuilder sb = new StringBuilder();
          for (int i = 0; i < Math.min(3, cards.size()); i++) {
            CardDTO c = cards.get(i);
            String block = explainCardMonthlyRequirement(c);
            if (!block.startsWith("【")) {
              sb.append("【").append(safe(c.getName())).append("】\n");
            }
            sb.append(block).append("\n\n");
            sources.add(source("CARD", c.getName() + " / " + c.getCompany()));
          }
          res.setAnswer(sb.toString().trim());
        }
      }
      case "WHY_RECOMMENDED" -> {
        List<RecommendationDTO> recs = safeRecommendations(userId, yearMonth);
        res.setAnswer(explainWhyRecommended(recs, spending, report));
        sources.add(source("RECOMMENDATION", "추천 내역의 reason_text"));
        sources.add(source("TRANSACTIONS", "카테고리별 지출"));
        if (report != null) sources.add(source("MONTHLY_REPORT", "월별 요약/지표"));
      }
      case "CUSTOM" -> {
        CardDTO card = req.getCardId() != null ? safeCard(req.getCardId()) : null;
        String customQ = req.getCustomQuestion() != null ? req.getCustomQuestion().trim() : "";
        List<CardDTO> recommendedCards = resolveCardsByIds(req.getRecommendedCardIds());
        res.setAnswer(answerCustomQuestion(customQ, card, recommendedCards, spending, report, userId, yearMonth));
        if (card != null) sources.add(source("CARD", card.getName() + " / " + card.getCompany()));
        if (recommendedCards != null && !recommendedCards.isEmpty()) sources.add(source("CARD", "추천 카드 " + recommendedCards.size() + "장"));
        if (spending != null && !spending.isEmpty()) sources.add(source("TRANSACTIONS", "카테고리별 지출"));
      }
      default -> {
        res.setAnswer("선택지(questionId)가 올바르지 않습니다.");
      }
    }

    res.setSources(sources);
    return res;
  }

  // -----------------------
  // Retrieval (DB → fallback)
  // -----------------------

  private Map<String, Object> safeSpendingPattern(Long userId, String yearMonth) {
    try {
      List<TransactionDTO> tx = transactionMapper.selectTransactionsByYearMonth(userId, yearMonth);
      if (tx == null || tx.isEmpty()) {
        Map<String, Object> empty = new HashMap<>();
        empty.put("yearMonth", yearMonth);
        empty.put("totalExpense", BigDecimal.ZERO);
        empty.put("categoryExpenses", new HashMap<String, BigDecimal>());
        empty.put("transactionCount", 0);
        empty.put("topCategory", "없음");
        return empty;
      }

      BigDecimal totalExpense = tx.stream()
          .map(TransactionDTO::getAmount)
          .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) < 0)
          .map(BigDecimal::abs)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      Map<String, BigDecimal> categoryExpenses = tx.stream()
          .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) < 0)
          .collect(Collectors.groupingBy(
              t -> t.getCategoryName() != null ? t.getCategoryName() : "미분류",
              Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)
          ));

      Map<String, Object> pattern = new HashMap<>();
      pattern.put("yearMonth", yearMonth);
      pattern.put("totalExpense", totalExpense);
      pattern.put("categoryExpenses", categoryExpenses);
      pattern.put("transactionCount", tx.size());
      pattern.put("topCategory", categoryExpenses.entrySet().stream()
          .max(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey).orElse("없음"));
      return pattern;
    } catch (Exception e) {
      log.warn("지출 데이터 조회 실패 → 빈 패턴 사용: {}", e.getMessage());
      Map<String, Object> empty = new HashMap<>();
      empty.put("yearMonth", yearMonth);
      empty.put("totalExpense", BigDecimal.ZERO);
      empty.put("categoryExpenses", new HashMap<String, BigDecimal>());
      empty.put("transactionCount", 0);
      empty.put("topCategory", "없음");
      return empty;
    }
  }

  private MonthlyReportDTO safeMonthlyReport(Long userId, String yearMonth) {
    try {
      return monthlyReportMapper.selectReportByYearMonth(userId, toDashedYearMonth(yearMonth));
    } catch (Exception e) {
      return null;
    }
  }

  /** 카드 관련 질문용: cardId가 없으면 추천된 카드 중 첫 번째 사용 */
  private CardDTO cardForQuestion(Long cardId, Long userId) {
    CardDTO c = safeCard(cardId);
    if (c != null) return c;
    // cardId가 null이면 추천된 카드 중 첫 번째 사용
    if (recommendationService != null && userId != null) {
      List<CardDTO> recommended = recommendationService.getRecommendedCards(userId);
      if (recommended != null && !recommended.isEmpty()) {
        return recommended.get(0);
      }
    }
    // 추천된 카드가 없으면 전체 카드 중 첫 번째
    if (recommendationService != null) {
      List<CardDTO> all = recommendationService.getAllCards();
      if (all != null && !all.isEmpty()) return all.get(0);
    }
    return null;
  }

  private CardDTO safeCard(Long cardId) {
    try {
      if (cardId == null) return null;
      CardDTO c = recommendationMapper.selectCardById(cardId);
      if (c != null) return c;
      // DB에 없으면 getAllCards()에서 찾기 (loaded 카드일 수 있음)
      if (recommendationService != null) {
        List<CardDTO> all = recommendationService.getAllCards();
        if (all != null) {
          return all.stream()
              .filter(card -> card.getCardId() != null && card.getCardId().equals(cardId))
              .findFirst()
              .orElse(null);
        }
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  private List<RecommendationDTO> safeRecommendations(Long userId, String yearMonth) {
    try {
      List<RecommendationDTO> recs = recommendationMapper.selectRecommendationsByYearMonth(userId, yearMonth);
      if (recs == null || recs.isEmpty()) return List.of();
      return recs;
    } catch (Exception e) {
      return List.of();
    }
  }

  private List<ProductDTO> safeProductsFromRecommendations(List<RecommendationDTO> recs) {
    List<Long> productIds = recs.stream()
        .filter(r -> "PRODUCT".equalsIgnoreCase(r.getRecType()))
        .map(RecommendationDTO::getItemId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    if (productIds.isEmpty()) return List.of();

    List<ProductDTO> products = new ArrayList<>();
    for (Long id : productIds) {
      try {
        ProductDTO p = recommendationMapper.selectProductById(id);
        if (p != null) products.add(p);
      } catch (Exception ignored) {}
    }
    return products;
  }

  // -----------------------
  // Generation (템플릿 기반)
  // -----------------------

  /** 전월 yyyyMM 문자열 반환 */
  private String previousYearMonth(String yearMonth) {
    try {
      return LocalDate.parse(yearMonth + "01", DateTimeFormatter.ofPattern("yyyyMMdd"))
          .minusMonths(1)
          .format(DateTimeFormatter.ofPattern("yyyyMM"));
    } catch (Exception e) {
      return yearMonth;
    }
  }

  private String estimateCardSaving(CardDTO card, Map<String, Object> spendingThis, Map<String, Object> spendingLast, MonthlyReportDTO report) {
    @SuppressWarnings("unchecked")
    Map<String, BigDecimal> categoryThis = (Map<String, BigDecimal>) spendingThis.get("categoryExpenses");
    if (categoryThis == null) categoryThis = new HashMap<>();
    @SuppressWarnings("unchecked")
    Map<String, BigDecimal> categoryLast = spendingLast != null ? (Map<String, BigDecimal>) spendingLast.get("categoryExpenses") : new HashMap<>();
    if (categoryLast == null) categoryLast = new HashMap<>();

    String benefits = card.getBenefitsJson() != null ? card.getBenefitsJson() : "";
    // 카드 혜택과 매칭되는 카테고리 지출에 대해 보수적으로 5% 절약 가정 (키워드 매칭 사용)
    BigDecimal estimatedThis = BigDecimal.ZERO;
    for (Map.Entry<String, BigDecimal> e : categoryThis.entrySet()) {
      if (categoryMatchesCardBenefits(e.getKey(), benefits)) {
        estimatedThis = estimatedThis.add(e.getValue().multiply(new BigDecimal("0.05")));
      }
    }
    BigDecimal estimatedLast = BigDecimal.ZERO;
    for (Map.Entry<String, BigDecimal> e : categoryLast.entrySet()) {
      if (categoryMatchesCardBenefits(e.getKey(), benefits)) {
        estimatedLast = estimatedLast.add(e.getValue().multiply(new BigDecimal("0.05")));
      }
    }

    BigDecimal totalThis = (BigDecimal) spendingThis.getOrDefault("totalExpense", BigDecimal.ZERO);
    BigDecimal totalLast = spendingLast != null ? (BigDecimal) spendingLast.getOrDefault("totalExpense", BigDecimal.ZERO) : BigDecimal.ZERO;
    String ymThis = (String) spendingThis.getOrDefault("yearMonth", "");
    String ymLast = spendingLast != null ? (String) spendingLast.getOrDefault("yearMonth", "") : "";

    StringBuilder sb = new StringBuilder();
    sb.append("【").append(safe(card.getName())).append("】 사용 시 예상 절약액\n\n");
    sb.append("• 이번 달(").append(ymThis).append(") 지출 요약: 총 ").append(money(totalThis)).append("원");
    if (!categoryThis.isEmpty()) {
      sb.append(" (카테고리: ");
      categoryThis.entrySet().stream()
          .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
          .limit(5)
          .forEach(e -> sb.append(e.getKey()).append(" ").append(money(e.getValue())).append(", "));
      sb.setLength(sb.length() - 2);
      sb.append(")");
    }
    sb.append("\n");
    if (totalLast.compareTo(BigDecimal.ZERO) > 0) {
      sb.append("• 지난달(").append(ymLast).append(") 지출 요약: 총 ").append(money(totalLast)).append("원");
      if (!categoryLast.isEmpty()) {
        sb.append(" (카테고리: ");
        categoryLast.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(5)
            .forEach(e -> sb.append(e.getKey()).append(" ").append(money(e.getValue())).append(", "));
        sb.setLength(sb.length() - 2);
        sb.append(")");
      }
      sb.append("\n");
    }
    sb.append("\n→ 이 카드를 사용했을 때 이번 달 예상 절약액: 약 ").append(money(estimatedThis)).append("원");
    if (estimatedLast.compareTo(BigDecimal.ZERO) > 0) {
      sb.append("\n→ 지난달 기준으로는 약 ").append(money(estimatedLast)).append("원 절약 가능했을 것으로 추정돼요.");
    }
    sb.append("\n\n※ 근거: 카드 혜택과 매칭되는 카테고리 지출에 5% 절약 가정. 실제는 전월 실적/한도/가맹점 조건에 따라 달라질 수 있어요.");
    return sb.toString();
  }

  private String pickBestProductByReturn(List<ProductDTO> products, Map<String, Object> spending, MonthlyReportDTO report) {
    // 단순 비교: rate가 가장 높은 상품을 "수익 최대"로 선택
    ProductDTO best = products.stream()
        .filter(p -> p.getRate() != null)
        .max(Comparator.comparing(ProductDTO::getRate))
        .orElse(products.get(0));

    BigDecimal monthly = new BigDecimal("500000"); // 기본 적립액(더미)
    BigDecimal months = new BigDecimal("12");
    BigDecimal principal = monthly.multiply(months);
    BigDecimal rate = best.getRate() != null ? best.getRate() : BigDecimal.ZERO;
    BigDecimal interest = principal.multiply(rate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);

    return """
추천된 금융상품 중 예상 수익(이자)이 가장 큰 상품은 **%s(%s)** 입니다.

- 금리: 연 %s%%
- 간단 시뮬레이션(가정): 월 50만원 × 12개월 납입 시, 단순이자 기준 약 %s원(세전)
- 참고: 실제 이자는 상품 조건(%s)과 과세/우대조건에 따라 달라질 수 있어요.
""".formatted(
        safe(best.getName()),
        safe(best.getBank()),
        rate.toPlainString(),
        money(interest),
        truncate(safe(best.getConditionsJson()), 80)
    );
  }

  /** 추천 근거: DB reason_text + 카드별 지출-혜택 매칭 템플릿 (LLM 호출 없음) */
  private String explainWhyRecommended(List<RecommendationDTO> recs, Map<String, Object> spending, MonthlyReportDTO report) {
    String ym = (String) spending.getOrDefault("yearMonth", "");
    String topCategory = String.valueOf(spending.getOrDefault("topCategory", "없음"));
    Long userId = (Long) spending.getOrDefault("userId", 1L);
    if (userId == null) userId = 1L;

    // DB에 추천 내역이 있으면 reason_text 사용
    if (recs != null && !recs.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("다음은 ").append(ym).append(" 기준 추천 근거 요약입니다.\n\n");
      sb.append("- 주요 지출 카테고리: ").append(topCategory).append("\n\n");
      for (RecommendationDTO r : recs) {
        if (!"CARD".equalsIgnoreCase(r.getRecType())) continue;
        sb.append("• [카드] ")
            .append(safe(r.getItemName())).append(" / ")
            .append(safe(r.getItemDetails())).append("\n");
        sb.append("  - 점수: ").append(r.getScore() != null ? r.getScore().toPlainString() : "-").append("\n");
        sb.append("  - 이유: ").append(safe(r.getReasonText())).append("\n\n");
      }
      if (report != null && report.getLlmSummaryText() != null && !report.getLlmSummaryText().isBlank()) {
        sb.append("월별 분석 요약: ").append(report.getLlmSummaryText()).append("\n");
      }
      return sb.toString();
    }

    // 추천 내역 없으면 추천 카드 목록만 템플릿으로 출력 (카드별로 실제 매칭 카테고리 기반)
    List<CardDTO> cardsToExplain = resolveRecommendedCardsForExplanation(null, userId);
    if (cardsToExplain != null && !cardsToExplain.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("다음은 ").append(ym).append(" 기준 상위 지출 카테고리와 매칭된 추천 카드입니다.\n\n");
      if (!"없음".equals(topCategory)) {
        sb.append("- 주요 지출 카테고리: ").append(topCategory).append("\n\n");
      }
      for (int i = 0; i < Math.min(3, cardsToExplain.size()); i++) {
        CardDTO card = cardsToExplain.get(i);
        sb.append("• [카드] ").append(safe(card.getName())).append(" / ").append(safe(card.getCompany())).append("\n");
        sb.append("  - 이유: ").append(buildQaCardReason(card, spending)).append("\n\n");
      }
      return sb.toString();
    }
    if (recommendationService != null) {
      try {
        List<CardDTO> recommended = recommendationService.getRecommendedCards(userId);
        if (recommended != null && !recommended.isEmpty()) {
          StringBuilder sb = new StringBuilder();
          sb.append("다음은 ").append(ym).append(" 기준 상위 지출 카테고리와 매칭된 추천 카드입니다.\n\n");
          if (!"없음".equals(topCategory)) {
            sb.append("- 주요 지출 카테고리: ").append(topCategory).append("\n\n");
          }
          for (int i = 0; i < Math.min(3, recommended.size()); i++) {
            CardDTO card = recommended.get(i);
            sb.append("• [카드] ").append(safe(card.getName())).append(" / ").append(safe(card.getCompany())).append("\n");
            sb.append("  - 이유: ").append(buildQaCardReason(card, spending)).append("\n\n");
          }
          return sb.toString();
        }
      } catch (Exception e) {
        // ignore
      }
    }
    return "아직 해당 월의 추천 내역이 없어요. 지출 데이터를 등록하고 '추천 생성'을 해보시면, 상위 지출 카테고리와 맞는 카드 추천 근거를 확인할 수 있어요.";
  }

  /** 추천 근거 설명용 카드 목록: recs에서 카드만 추리거나, 없으면 getRecommendedCards(userId) */
  private List<CardDTO> resolveRecommendedCardsForExplanation(List<RecommendationDTO> recs, Long userId) {
    if (recs != null && !recs.isEmpty()) {
      List<CardDTO> list = new ArrayList<>();
      for (RecommendationDTO r : recs) {
        if (!"CARD".equalsIgnoreCase(r.getRecType()) || r.getItemId() == null) continue;
        CardDTO c = safeCard(r.getItemId());
        if (c != null) list.add(c);
      }
      if (!list.isEmpty()) return list;
    }
    if (recommendationService != null && userId != null) {
      List<CardDTO> recommended = recommendationService.getRecommendedCards(userId);
      if (recommended != null && !recommended.isEmpty()) {
        return recommended.size() > 3 ? recommended.subList(0, 3) : recommended;
      }
    }
    return List.of();
  }

  /** 카드 자세한 혜택: DB benefits_detail_text 우선, 없으면 benefitsJson 파싱 (LLM/검색 없음) */
  private String explainCardBenefitsDetail(CardDTO card) {
    String cardName = safe(card.getName());
    String detailText = card.getBenefitsDetailText();
    if (detailText != null && !detailText.isBlank()) {
      return "【" + cardName + "】 자세한 혜택\n\n" + detailText.trim() + "\n\n※ 정확한 혜택·조건은 신한카드 공식 채널에서 확인해주세요.";
    }
    String json = card.getBenefitsJson();
    if (json == null || json.isBlank()) {
      return cardName + "의 상세 혜택 정보는 현재 제공되지 않습니다. 신한카드 공식 홈페이지에서 확인해주세요.";
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode node = om.readTree(json);
      StringBuilder sb = new StringBuilder();
      sb.append("【").append(cardName).append("】 자세한 혜택\n\n");
      if (node.has("benefits") && node.get("benefits").isArray()) {
        sb.append("• 상세 혜택:\n");
        for (com.fasterxml.jackson.databind.JsonNode b : node.get("benefits")) {
          String cat = b.has("category") ? b.path("category").asText("") : "";
          String type = b.has("type") ? b.path("type").asText("") : "";
          if (b.has("value")) {
            if ("percent".equals(type)) sb.append("  - ").append(cat).append(" ").append(b.get("value").asInt()).append("% 캐시백/리워드\n");
            else if ("fixed".equals(type)) sb.append("  - ").append(cat).append(" ").append(b.get("value").asInt()).append("원 할인\n");
            else sb.append("  - ").append(cat).append(" ").append(b.get("value").asText()).append("\n");
          } else {
            sb.append("  - ").append(cat).append("\n");
          }
        }
      } else if (node.has("summary") && !node.get("summary").asText().isBlank()) {
        sb.append("• 요약: ").append(node.get("summary").asText()).append("\n");
      }
      sb.append("\n※ 정확한 혜택·조건은 신한카드 공식 채널에서 확인해주세요.");
      return sb.toString();
    } catch (Exception e) {
      return cardName + "의 혜택 요약: " + truncate(json, 200) + "\n\n※ 상세 내용은 신한카드 공식 홈페이지에서 확인해주세요.";
    }
  }

  /** 전월 실적 채워야 하는 카드인가? (DB monthly_requirement + 템플릿만, LLM 없음) */
  private String explainCardMonthlyRequirement(CardDTO card) {
    String cardName = safe(card.getName());
    String req = card.getMonthlyRequirement();
    if (req != null && !req.isBlank()) {
      return "【" + cardName + "】 전월 실적(월 이용금액) 조건\n\n• " + req.trim()
          + "\n\n※ 신한카드 공식 홈페이지·고객센터(1577-3419)·앱 '내 카드' 메뉴에서도 확인 가능해요.";
    }
    return "【" + cardName + "】 전월 실적 조건 정보는 현재 제공되지 않습니다. 신한카드 공식 홈페이지·고객센터에서 확인해주세요.";
  }

  /** ID 목록으로 카드 DTO 목록 조회 (null/빈 목록이면 빈 리스트) */
  private List<CardDTO> resolveCardsByIds(List<Long> cardIds) {
    if (cardIds == null || cardIds.isEmpty()) return List.of();
    List<CardDTO> list = new ArrayList<>();
    for (Long id : cardIds) {
      if (id == null) continue;
      CardDTO c = safeCard(id);
      if (c != null) list.add(c);
    }
    return list;
  }

  /** 직접 입력 질문 → RAG(리포트 청크) + 카드·지출 맥락으로 근거 기반 답변. 없으면 기존 추천 AI/Gemini 폴백. */
  private String answerCustomQuestion(String question, CardDTO card, List<CardDTO> recommendedCards, Map<String, Object> spending, MonthlyReportDTO report, Long userId, String yearMonth) {
    if (question == null || question.isBlank()) {
      return "질문을 입력해주세요.";
    }

    // 1) 선택 카드 2) 추천 카드 첫 장 3) 질문 키워드로 검색한 카드 순으로 사용
    List<CardDTO> relevantCards = findRelevantCardsByKeywords(question);
    CardDTO cardToUse = card;
    if (cardToUse == null && recommendedCards != null && !recommendedCards.isEmpty()) {
      cardToUse = recommendedCards.get(0);
    }
    if (cardToUse == null && !relevantCards.isEmpty()) {
      cardToUse = relevantCards.get(0);
    }

    // Evidence: 추천 카드(요청으로 넘어온 것) + 선택/관련 카드 + 지출 요약 + 월별 리포트
    StringBuilder evidenceText = new StringBuilder();
    boolean askingAboutAllRecommended = isAskingAboutRecommendedCards(question)
        && recommendedCards != null && !recommendedCards.isEmpty();
    if (askingAboutAllRecommended) {
      evidenceText.append("[답변 지침] 아래 [추천된 카드] 목록에 있는 모든 카드를 답변에서 빠짐없이 각각 언급해주세요.\n\n");
    }
    if (recommendedCards != null && !recommendedCards.isEmpty()) {
      evidenceText.append("[추천된 카드]\n");
      for (int i = 0; i < Math.min(5, recommendedCards.size()); i++) {
        CardDTO c = recommendedCards.get(i);
        evidenceText.append(String.format("- %s (%s): %s\n", safe(c.getName()), safe(c.getCompany()), truncate(safe(c.getBenefitsJson()), 120)));
      }
      evidenceText.append("\n");
    }
    if (cardToUse != null) {
      evidenceText.append("[선택/관련 카드] ").append(safe(cardToUse.getName())).append(" (").append(safe(cardToUse.getCompany())).append("). 혜택 요약: ").append(truncate(safe(cardToUse.getBenefitsJson()), 200)).append("\n\n");
    }
    if (!relevantCards.isEmpty()) {
      evidenceText.append("[질문 키워드 관련 카드]\n");
      for (int i = 0; i < Math.min(3, relevantCards.size()); i++) {
        CardDTO c = relevantCards.get(i);
        evidenceText.append(String.format("- %s (%s): %s\n", safe(c.getName()), safe(c.getCompany()), truncate(safe(c.getBenefitsJson()), 100)));
      }
      evidenceText.append("\n");
    }
    if (spending != null && !spending.isEmpty()) {
      evidenceText.append("[지출 맥락] yearMonth=").append(spending.getOrDefault("yearMonth", "")).append(", topCategory=").append(spending.getOrDefault("topCategory", "")).append(", totalExpense=").append(spending.getOrDefault("totalExpense", "")).append("\n");
      if (spending.get("categoryExpenses") instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> categoryExpenses = (Map<String, BigDecimal>) spending.get("categoryExpenses");
        if (categoryExpenses != null && !categoryExpenses.isEmpty()) {
          categoryExpenses.entrySet().stream()
              .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
              .limit(8)
              .forEach(e -> evidenceText.append("  - ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
        }
      }
      evidenceText.append("\n");
    }
    if (report != null && report.getLlmSummaryText() != null && !report.getLlmSummaryText().isBlank()) {
      evidenceText.append("[월별 리포트 요약]\n").append(report.getLlmSummaryText()).append("\n\n");
    }

    // "더 추천할 카드 없어?" 등 추가 추천 요청 시: (1) 해당 월 이미 추천된 카드 (2) 소비 패턴 키워드로 매칭된 추가 카드
    Set<Long> alreadyRecommendedIds = new HashSet<>();
    if (userId != null && yearMonth != null && isAskingForMoreCards(question)) {
      List<RecommendationDTO> recs = safeRecommendations(userId, yearMonth);
      List<CardDTO> alreadyRecommended = resolveRecommendedCardsForExplanation(recs, userId);
      if (!alreadyRecommended.isEmpty()) {
        alreadyRecommended.forEach(c -> { if (c.getCardId() != null) alreadyRecommendedIds.add(c.getCardId()); });
        StringBuilder alreadyBlock = new StringBuilder("[이미 추천된 카드]\n");
        for (CardDTO c : alreadyRecommended) {
          alreadyBlock.append(String.format("- %s (%s): %s\n",
              safe(c.getName()), safe(c.getCompany()), truncate(safe(c.getBenefitsJson()), 120)));
        }
        alreadyBlock.append("\n");
        evidenceText.insert(0, alreadyBlock.toString());
      }
      // 소비 패턴(지출 상위 카테고리) 키워드로 혜택이 맞는 카드 검색 → 이미 추천된 카드 제외하고 evidence에 추가
      List<CardDTO> spendingBasedCards = findRelevantCardsBySpending(spending, alreadyRecommendedIds);
      if (!spendingBasedCards.isEmpty()) {
        evidenceText.append("[소비 패턴 기반 추가 추천 카드]\n");
        for (int i = 0; i < Math.min(5, spendingBasedCards.size()); i++) {
          CardDTO c = spendingBasedCards.get(i);
          evidenceText.append(String.format("- %s (%s): %s\n",
              safe(c.getName()), safe(c.getCompany()), truncate(safe(c.getBenefitsJson()), 120)));
        }
        evidenceText.append("\n");
      }
    }

    // 검색: Google Custom Search로 쿼리 실행 후 스니펫을 evidence에 추가 (할루시네이션 방지용 검색 근거)
    if (cardBenefitSearchService != null && cardBenefitSearchService.isSearchConfigured()) {
      String searchQuery;
      if (isAskingForMoreCards(question)) {
        String topCat = spending != null ? String.valueOf(spending.get("topCategory")).trim() : "";
        if (topCat.isEmpty() || "없음".equals(topCat)) topCat = "인기";
        searchQuery = "신한카드 " + topCat + " 혜택 카드 추천";
      } else {
        searchQuery = question.length() > 80 ? question.substring(0, 80) : question;
      }
      if (searchQuery != null && !searchQuery.isBlank()) {
        String searchSnippets = cardBenefitSearchService.fetchRawSnippetsForQuery(searchQuery.trim(), 6);
        if (searchSnippets != null && !searchSnippets.isBlank()) {
          evidenceText.append("[검색 결과]\n").append(searchSnippets).append("\n\n");
        }
      }
    }

    List<String> ragChunks = null;
    if (reportChunkRepository != null && userId != null && yearMonth != null && !yearMonth.isBlank()) {
      try {
        ragChunks = reportChunkRepository.selectContentByUserAndYearMonth(userId, yearMonth.replace("-", "").trim());
      } catch (Exception e) {
        log.debug("RAG 청크 조회 실패: {}", e.getMessage());
      }
    }

    // 1. 검색 + RAG + evidence를 한꺼번에 Gemini에 전달 → 근거만 사용해 요약·답변 (할루시네이션 최소화)
    if (pythonAIService != null && (evidenceText.length() > 0 || (ragChunks != null && !ragChunks.isEmpty()))) {
      try {
        String answer = pythonAIService.answerFromEvidence(
          question.trim(),
          evidenceText.toString(),
          (ragChunks != null && !ragChunks.isEmpty()) ? ragChunks : null
        );
        if (answer != null && !answer.isBlank()) {
          return answer;
        }
      } catch (Exception e) {
        log.debug("검색+RAG 기반 답변 실패, 다음 방식으로 폴백: {}", e.getMessage());
      }
    }

    Map<String, Object> cardMap = null;
    if (cardToUse != null) {
      cardMap = new HashMap<>();
      cardMap.put("name", cardToUse.getName());
      cardMap.put("company", cardToUse.getCompany());
      cardMap.put("benefitsJson", cardToUse.getBenefitsJson());
    }
    String cardsContext = evidenceText.length() > 0 ? evidenceText.toString() : "";

    // 2. 추천 AI 서비스 (카드·지출 맥락, Google Search Grounding)
    if (recommendationAIService != null) {
      try {
        String answer = recommendationAIService.answerCustomQuestion(
          question + (cardsContext.isEmpty() ? "" : "\n\n" + cardsContext),
          cardMap,
          spending,
          report != null ? report.getLlmSummaryText() : null
        );
        if (answer != null && !answer.isBlank()) {
          return answer;
        }
      } catch (Exception e) {
        log.debug("Python AI 직접 질문 답변 실패, Java GeminiService로 폴백: {}", e.getMessage());
      }
    }

    // 3. Java GeminiService 폴백
    if (geminiService == null) {
      return "직접 질문 답변은 현재 LLM 설정 후 이용 가능해요. 선택지 질문을 이용해주세요.";
    }
    try {
      String augmentedQuestion = question + (cardsContext.isEmpty() ? "" : "\n\n" + cardsContext);
      return geminiService.answerReportQuestion(augmentedQuestion, spending, report != null ? report.getLlmSummaryText() : null);
    } catch (Exception e) {
      log.warn("직접 질문 답변 실패: {}", e.getMessage());
      return "답변 생성 중 오류가 났어요. 잠시 뒤 다시 시도해주세요.";
    }
  }

  /** '다른 카드 추천', '더 추천할 카드 없어?', '이 카드 말고 다른 추천' 등 추가 추천 요청인지 여부 */
  private boolean isAskingForMoreCards(String question) {
    if (question == null || question.isBlank()) return false;
    String q = question.trim();
    return q.contains("다른 카드") || q.contains("더 추천") || q.contains("추가 추천")
        || q.contains("또 추천") || q.contains("또 다른 카드") || q.contains("비슷한 카드")
        || q.contains("추천해줄 만한 카드") || q.contains("추천할 카드")
        || q.contains("이 카드 말고") || q.contains("추천 더 없") || q.contains("추천할 카드 더 없");
  }

  /** 추천된 카드(여러 장)에 대한 설명·비교 질문인지 여부 → 답변 시 모든 카드 언급 지침 추가용 */
  private boolean isAskingAboutRecommendedCards(String question) {
    if (question == null || question.isBlank()) return false;
    String q = question.trim().toLowerCase();
    return q.contains("추천된 카드") || q.contains("추천 카드") || q.contains("이 카드들") || q.contains("이 카드 모두")
        || q.contains("이 카드 전부") || (q.contains("카드들") && (q.contains("설명") || q.contains("알려") || q.contains("혜택") || q.contains("비교")))
        || (q.contains("전부") && q.contains("카드")) || (q.contains("모두") && q.contains("카드"))
        || q.contains("각 카드") || q.contains("카드 각각") || q.contains("세 카드") || q.contains("3장");
  }

  /** 질문에서 키워드를 추출하여 관련 카드 검색 */
  private List<CardDTO> findRelevantCardsByKeywords(String question) {
    if (question == null || question.isBlank()) return List.of();
    if (recommendationService == null) return List.of();
    
    try {
      List<CardDTO> allCards = recommendationService.getAllCards();
      if (allCards == null || allCards.isEmpty()) return List.of();
      
      String questionLower = question.toLowerCase();
      // 키워드 추출 (카드명, 혜택 관련 키워드)
      Set<String> keywords = new HashSet<>();
      String[] commonKeywords = {"배달", "배민", "주유", "교통", "통신", "식비", "외식", "카페", "여행", "해외", 
        "주거", "관리비", "교육", "쇼핑", "마트", "편의점", "문화", "영화", "건강", "병원"};
      for (String kw : commonKeywords) {
        if (questionLower.contains(kw.toLowerCase())) {
          keywords.add(kw.toLowerCase());
        }
      }
      
      if (keywords.isEmpty()) return List.of();
      
      // 키워드와 매칭되는 카드 찾기
      List<CardDTO> matched = new ArrayList<>();
      for (CardDTO card : allCards) {
        String cardName = (card.getName() != null ? card.getName() : "").toLowerCase();
        String benefitsJson = card.getBenefitsJson() != null ? card.getBenefitsJson().toLowerCase() : "";
        String cardText = cardName + " " + benefitsJson;
        
        boolean matches = keywords.stream().anyMatch(kw -> cardText.contains(kw));
        if (matches) {
          matched.add(card);
        }
      }
      
      // 최대 5개까지만 반환
      return matched.stream().limit(5).collect(Collectors.toList());
    } catch (Exception e) {
      return List.of();
    }
  }

  /** 소비 패턴(지출 상위 카테고리) 키워드로 혜택이 맞는 카드 검색. excludeCardIds에 있는 카드는 제외. */
  private List<CardDTO> findRelevantCardsBySpending(Map<String, Object> spending, Set<Long> excludeCardIds) {
    if (spending == null || recommendationService == null) return List.of();
    try {
      List<CardDTO> allCards = recommendationService.getAllCards();
      if (allCards == null || allCards.isEmpty()) return List.of();

      Set<String> keywords = new HashSet<>();
      String topCategory = spending.get("topCategory") != null ? String.valueOf(spending.get("topCategory")).trim() : "";
      if (!topCategory.isEmpty() && !"없음".equals(topCategory)) {
        Set<String> fromTop = USER_CATEGORY_TO_BENEFIT_KEYWORDS.getOrDefault(topCategory, Set.of(topCategory.toLowerCase()));
        keywords.addAll(fromTop);
        keywords.add(topCategory.toLowerCase());
      }
      if (spending.get("categoryExpenses") instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> categoryExpenses = (Map<String, BigDecimal>) spending.get("categoryExpenses");
        if (categoryExpenses != null && !categoryExpenses.isEmpty()) {
          categoryExpenses.entrySet().stream()
              .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
              .limit(5)
              .map(e -> e.getKey())
              .forEach(cat -> {
                Set<String> kw = USER_CATEGORY_TO_BENEFIT_KEYWORDS.getOrDefault(cat, Set.of(cat.toLowerCase()));
                keywords.addAll(kw);
                keywords.add(cat.toLowerCase());
              });
        }
      }
      if (keywords.isEmpty()) return List.of();

      List<CardDTO> matched = new ArrayList<>();
      for (CardDTO card : allCards) {
        if (excludeCardIds != null && excludeCardIds.contains(card.getCardId())) continue;
        String cardName = (card.getName() != null ? card.getName() : "").toLowerCase();
        String benefitsJson = card.getBenefitsJson() != null ? card.getBenefitsJson().toLowerCase() : "";
        String cardText = cardName + " " + benefitsJson;
        boolean matches = keywords.stream().anyMatch(kw -> cardText.contains(kw.toLowerCase()));
        if (matches) matched.add(card);
      }
      return matched.stream().limit(5).collect(Collectors.toList());
    } catch (Exception e) {
      return List.of();
    }
  }

  /** 추천 이유용: 지출 카테고리와 카드 혜택 매칭으로 간단한 근거 문구 생성 (카드별) */
  @SuppressWarnings("unchecked")
  private String buildQaCardReason(CardDTO card, Map<String, Object> spending) {
    String benefitsJson = card.getBenefitsJson();
    if (benefitsJson == null || benefitsJson.isBlank()) {
      return "상위 지출 카테고리와 전반적인 혜택 구성을 기준으로 추천했습니다.";
    }
    Map<String, BigDecimal> categoryExpenses =
        (Map<String, BigDecimal>) spending.getOrDefault("categoryExpenses", new HashMap<String, BigDecimal>());
    if (categoryExpenses == null || categoryExpenses.isEmpty()) {
      return "기본 소비 패턴과 카드 혜택 구성을 기준으로 추천했습니다.";
    }

    List<Map.Entry<String, BigDecimal>> matched = categoryExpenses.entrySet().stream()
        .filter(e -> categoryMatchesCardBenefits(e.getKey(), benefitsJson))
        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
        .limit(3)
        .collect(Collectors.toList());

    if (matched.isEmpty()) {
      return "상위 지출 카테고리와 전반적인 혜택 구성을 기준으로 추천했습니다.";
    }

    String cats = matched.stream().map(Map.Entry::getKey).collect(Collectors.joining(", "));
    BigDecimal totalMatched = matched.stream()
        .map(Map.Entry::getValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return "최근 " + cats + " 카테고리에서 사용액이 많고, 이 카드가 해당 영역에 혜택을 제공해 추천했습니다. "
        + "(해당 카테고리 합계 약 " + money(totalMatched) + "원 기준)";
  }

  // -----------------------
  // helpers
  // -----------------------

  private QaOptionDTO option(String id, String title, String targetType, boolean needsItem) {
    QaOptionDTO o = new QaOptionDTO();
    o.setId(id);
    o.setTitle(title);
    o.setTargetType(targetType);
    o.setNeedsItemSelection(needsItem);
    return o;
  }

  private QaAnswerResponseDTO.QaSourceDTO source(String type, String label) {
    QaAnswerResponseDTO.QaSourceDTO s = new QaAnswerResponseDTO.QaSourceDTO();
    s.setType(type);
    s.setLabel(label);
    return s;
  }

  private String toDashedYearMonth(String yyyymm) {
    // monthly_reports는 "2024-05" 형태 사용. 안전 변환.
    if (yyyymm == null) return null;
    String s = yyyymm.trim();
    if (s.contains("-")) return s;
    if (s.length() == 6) return s.substring(0, 4) + "-" + s.substring(4, 6);
    return s;
  }

  private String safe(String s) {
    return s == null ? "" : s;
  }

  private String money(BigDecimal v) {
    if (v == null) return "0";
    return v.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
  }

  private String truncate(String s, int max) {
    if (s == null) return "";
    if (s.length() <= max) return s;
    return s.substring(0, max) + "...";
  }
}
