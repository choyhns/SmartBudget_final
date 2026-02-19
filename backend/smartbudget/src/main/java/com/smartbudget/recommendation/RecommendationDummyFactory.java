package com.smartbudget.recommendation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * DB가 비어있거나 조회 실패 시 사용할 더미 데이터 생성기
 * - "데이터가 생겼을 때 문제 없도록" 실제 DTO 형태를 그대로 유지
 */
public final class RecommendationDummyFactory {
  private RecommendationDummyFactory() {}

  public static List<CardDTO> dummyCards() {
    CardDTO c1 = new CardDTO();
    c1.setCardId(1L);
    c1.setName("스마트 교통/통신 카드");
    c1.setCompany("SmartBank");
    c1.setBenefitsJson("{\"benefits\":[{\"category\":\"교통\",\"type\":\"percent\",\"value\":15},{\"category\":\"통신\",\"type\":\"fixed\",\"value\":5000}]}");
    c1.setTags(new String[]{"AI추천", "교통", "통신"});

    CardDTO c2 = new CardDTO();
    c2.setCardId(2L);
    c2.setName("데일리 식비 캐시백 카드");
    c2.setCompany("GourmetCard");
    c2.setBenefitsJson("{\"benefits\":[{\"category\":\"식비\",\"type\":\"percent\",\"value\":10},{\"category\":\"카페\",\"type\":\"percent\",\"value\":20}]}");
    c2.setTags(new String[]{"실속", "식비", "카페"});

    CardDTO c3 = new CardDTO();
    c3.setCardId(3L);
    c3.setName("온라인 쇼핑 적립 카드");
    c3.setCompany("DigitalFirst");
    c3.setBenefitsJson("{\"benefits\":[{\"category\":\"쇼핑\",\"type\":\"percent\",\"value\":5},{\"category\":\"해외\",\"type\":\"fee_waive\",\"value\":100}]}");
    c3.setTags(new String[]{"쇼핑", "적립"});

    return Arrays.asList(c1, c2, c3);
  }

  public static List<ProductDTO> dummyProducts() {
    ProductDTO p1 = new ProductDTO();
    p1.setProductId(1L);
    p1.setType("적금");
    p1.setName("SmartBudget 스마트 적금");
    p1.setBank("SmartBank");
    p1.setRate(new BigDecimal("4.5"));
    p1.setConditionsJson("{\"minMonths\":12,\"note\":\"자동이체 우대\"}");
    p1.setTags(new String[]{"추천", "우대금리"});

    ProductDTO p2 = new ProductDTO();
    p2.setProductId(2L);
    p2.setType("CMA");
    p2.setName("SmartBudget 플러스 CMA");
    p2.setBank("WealthCMA");
    p2.setRate(new BigDecimal("3.2"));
    p2.setConditionsJson("{\"note\":\"수시입출금\"}");
    p2.setTags(new String[]{"유동성", "생활비"});

    return Arrays.asList(p1, p2);
  }

  public static List<RecommendationDTO> dummyRecommendations(Long userId, String yearMonth) {
    List<RecommendationDTO> list = new ArrayList<>();

    RecommendationDTO r1 = new RecommendationDTO();
    r1.setRecId(1L);
    r1.setUserId(userId);
    r1.setYearMonth(yearMonth);
    r1.setRecType("CARD");
    r1.setItemId(1L);
    r1.setScore(new BigDecimal("0.85"));
    r1.setReasonText("최근 지출에서 교통/통신 비중이 높아 해당 혜택을 최대로 받을 수 있어요.");
    r1.setCreatedAt(LocalDateTime.now());
    r1.setItemName("스마트 교통/통신 카드");
    r1.setItemDetails("SmartBank");
    list.add(r1);

    RecommendationDTO r2 = new RecommendationDTO();
    r2.setRecId(2L);
    r2.setUserId(userId);
    r2.setYearMonth(yearMonth);
    r2.setRecType("PRODUCT");
    r2.setItemId(1L);
    r2.setScore(new BigDecimal("0.80"));
    r2.setReasonText("남는 금액을 자동이체로 굳히기 좋은 적금이에요.");
    r2.setCreatedAt(LocalDateTime.now());
    r2.setItemName("SmartBudget 스마트 적금");
    r2.setItemDetails("SmartBank");
    list.add(r2);

    return list;
  }

  public static Map<String, Object> dummySpendingPattern(String yearMonth) {
    Map<String, Object> m = new HashMap<>();
    m.put("yearMonth", yearMonth);
    m.put("totalExpense", new BigDecimal("350000"));

    Map<String, BigDecimal> categoryExpenses = new LinkedHashMap<>();
    categoryExpenses.put("식비", new BigDecimal("120000"));
    categoryExpenses.put("교통", new BigDecimal("60000"));
    categoryExpenses.put("쇼핑", new BigDecimal("80000"));
    categoryExpenses.put("주거/통신", new BigDecimal("50000"));
    m.put("categoryExpenses", categoryExpenses);

    m.put("transactionCount", 18);
    m.put("averageAmount", new BigDecimal("19444"));
    m.put("topCategory", "식비");
    return m;
  }
}

