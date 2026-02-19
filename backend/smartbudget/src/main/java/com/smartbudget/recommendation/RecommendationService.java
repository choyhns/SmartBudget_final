package com.smartbudget.recommendation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbudget.llm.GeminiService;
import com.smartbudget.llm.RecommendationAIService;
import com.smartbudget.monthlyreport.MonthlyReportDTO;
import com.smartbudget.monthlyreport.MonthlyReportMapper;
import com.smartbudget.transaction.TransactionDTO;
import com.smartbudget.transaction.TransactionMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RecommendationService {
    
    @Autowired
    private RecommendationMapper recommendationMapper;
    
    @Autowired
    private TransactionMapper transactionMapper;
    
    @Autowired(required = false)
    private GeminiService geminiService;

    @Autowired(required = false)
    private RecommendationAIService recommendationAIService;

    @Autowired
    private MonthlyReportMapper monthlyReportMapper;

    @Autowired(required = false)
    private ShinhanCardLoader shinhanCardLoader;

    @Autowired(required = false)
    private FinancialProductApiService financialProductApiService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 카드명 비교용 정규화: trim, 유니코드 공백을 일반 공백으로, 연속 공백 축소 */
    private static String normalizeCardName(String raw) {
        if (raw == null) return null;
        return raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    /** 매칭용 company 정규화: 신한 계열(shinhancard, shinhancard_check, 신한카드) 동일 취급 */
    private static String normalizeCompanyForMatch(String company) {
        if (company == null || company.isBlank()) return null;
        String c = company.trim().toLowerCase();
        if (c.contains("shinhan") || "신한카드".equals(c) || c.contains("신한")) return "shinhan";
        return c;
    }

    /** DB에 카드가 없을 때 신한카드 JSON 로드 후 DB에 적재 (최초 1회). DB 카드는 JSON과 병합해 imageUrl·혜택 보강. */
    public List<CardDTO> getAllCards() {
        try {
            List<CardDTO> cards = recommendationMapper.selectAllCards();
            if ((cards == null || cards.isEmpty()) && shinhanCardLoader != null) {
                List<CardDTO> loaded = shinhanCardLoader.loadAllCards();
                for (CardDTO c : loaded) {
                    c.setCardId(null);
                    try {
                        recommendationMapper.insertCard(c);
                    } catch (Exception ex) { }
                }
                List<CardDTO> fromDb = recommendationMapper.selectAllCards();
                if (fromDb != null && !fromDb.isEmpty()) {
                    cards = fromDb;
                } else {
                    cards = loaded;
                }
            }
            // DB 카드는 image_url/benefits_json이 비어 있을 수 있음 → JSON 로드 결과와 병합
            if (cards != null && !cards.isEmpty() && shinhanCardLoader != null) {
                List<CardDTO> fromJson = shinhanCardLoader.loadAllCards();
                if (fromJson != null && !fromJson.isEmpty()) {
                    int matchedCount = 0;
                    int imageUrlSetCount = 0;
                    int benefitsSetCount = 0;
                    for (CardDTO dbCard : cards) {
                        String name = normalizeCardName(dbCard.getName());
                        if (name == null || name.isEmpty()) continue;
                        String company = dbCard.getCompany() != null ? dbCard.getCompany().trim() : null;
                        
                        CardDTO match = null;
                        String companyKey = normalizeCompanyForMatch(company);
                        // 1순위: 정확 일치 (company: 신한 계열 통일 비교)
                        for (CardDTO j : fromJson) {
                            String jName = normalizeCardName(j.getName());
                            if (jName == null) jName = "";
                            String jCompanyKey = normalizeCompanyForMatch(j.getCompany());
                            if (name.equals(jName) && java.util.Objects.equals(companyKey, jCompanyKey)) {
                                match = j;
                                break;
                            }
                        }
                        // 2순위: 정규화 후 일치 (공백/특수문자 제거)
                        if (match == null) {
                            String normalizedName = name.replaceAll("\\s+", "").replaceAll("[·•]", "");
                            for (CardDTO j : fromJson) {
                                String jName = normalizeCardName(j.getName());
                                if (jName == null) jName = "";
                                String jCompanyKey = normalizeCompanyForMatch(j.getCompany());
                                String normalizedJName = jName.replaceAll("\\s+", "").replaceAll("[·•]", "");
                                if (normalizedName.equals(normalizedJName) && java.util.Objects.equals(companyKey, jCompanyKey)) {
                                    match = j;
                                    break;
                                }
                            }
                        }
                        // 3순위: 포함 관계 (카드명이 서로 포함)
                        if (match == null && name.length() >= 5) {
                            for (CardDTO j : fromJson) {
                                String jName = normalizeCardName(j.getName());
                                if (jName == null) jName = "";
                                String jCompanyKey = normalizeCompanyForMatch(j.getCompany());
                                if (jName.length() >= 5 && (name.contains(jName) || jName.contains(name))) {
                                    if (companyKey == null || jCompanyKey == null || companyKey.equals(jCompanyKey)) {
                                        match = j;
                                        break;
                                    }
                                }
                            }
                        }
                        if (match != null) {
                            matchedCount++;
                            if ((dbCard.getImageUrl() == null || dbCard.getImageUrl().isBlank()) && match.getImageUrl() != null && !match.getImageUrl().isBlank()) {
                                dbCard.setImageUrl(match.getImageUrl());
                                imageUrlSetCount++;
                                try {
                                    recommendationMapper.updateCardImage(dbCard.getCardId(), dbCard.getImageUrl());
                                } catch (Exception ex) { }
                            }
                            boolean matchHasBenefits = match.getBenefitsJson() != null && !match.getBenefitsJson().isBlank();
                            if (matchHasBenefits) {
                                // JSON에 benefitsJson이 있으면 항상 업데이트 (최신 데이터 보장)
                                dbCard.setBenefitsJson(match.getBenefitsJson());
                                benefitsSetCount++;
                            }
                        }
                    }
                    // imageUrl/benefitsJson이 비어 있는 카드에 대해 부분 매칭(폴백) 시도
                    int fallbackImageCount = 0;
                    int fallbackBenefitsCount = 0;
                    for (CardDTO dbCard : cards) {
                        boolean needsImage = dbCard.getImageUrl() == null || dbCard.getImageUrl().isBlank();
                        boolean needsBenefits = dbCard.getBenefitsJson() == null || dbCard.getBenefitsJson().isBlank();
                        if (!needsImage && !needsBenefits) continue;
                        
                        String name = normalizeCardName(dbCard.getName());
                        if (name == null || name.isEmpty()) continue;
                        
                        // 카드명의 핵심 키워드 추출 (예: "신한카드 집" -> "집")
                        String[] keywords = name.split("\\s+");
                        String mainKeyword = keywords.length > 1 ? keywords[keywords.length - 1] : name;
                        
                        // 키워드로 부분 매칭 시도
                        CardDTO fallbackMatch = fromJson.stream()
                            .filter(j -> {
                                String jName = normalizeCardName(j.getName());
                                if (jName == null) jName = "";
                                return jName.contains(mainKeyword) || mainKeyword.length() >= 2 && jName.contains(mainKeyword.substring(0, Math.min(2, mainKeyword.length())));
                            })
                            .findFirst()
                            .orElse(null);
                        
                        if (fallbackMatch != null) {
                            if (needsImage && fallbackMatch.getImageUrl() != null && !fallbackMatch.getImageUrl().isBlank()) {
                                dbCard.setImageUrl(fallbackMatch.getImageUrl());
                                fallbackImageCount++;
                                try {
                                    recommendationMapper.updateCardImage(dbCard.getCardId(), dbCard.getImageUrl());
                                } catch (Exception ex) { }
                            }
                            if (needsBenefits && fallbackMatch.getBenefitsJson() != null && !fallbackMatch.getBenefitsJson().isBlank()) {
                                dbCard.setBenefitsJson(fallbackMatch.getBenefitsJson());
                                fallbackBenefitsCount++;
                            }
                        }
                    }
                }
            }
            return (cards == null || cards.isEmpty()) ? List.of() : cards;
        } catch (Exception e) {
            if (shinhanCardLoader != null) {
                try {
                    List<CardDTO> loaded = shinhanCardLoader.loadAllCards();
                    if (loaded != null && !loaded.isEmpty()) return loaded;
                } catch (Exception ex) {
                    log.warn("JSON 로드도 실패: {}", ex.getMessage());
                }
            }
            return List.of();
        }
    }

    /**
     * 신한카드 JSON(credit + check) 기준으로 DB 카드의 image_url만 일괄 업데이트.
     * 카드명·company 정규화 매칭 사용. 기존 카드 데이터는 변경하지 않음.
     *
     * @return 업데이트된 카드 수
     */
    @Transactional
    public int syncCardImagesFromJson() {
        if (shinhanCardLoader == null) {
            return 0;
        }
        List<CardDTO> dbCards = recommendationMapper.selectAllCards();
        if (dbCards == null || dbCards.isEmpty()) {
            return 0;
        }
        List<CardDTO> fromJson = shinhanCardLoader.loadAllCards();
        if (fromJson == null || fromJson.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (CardDTO dbCard : dbCards) {
            String name = normalizeCardName(dbCard.getName());
            if (name == null || name.isEmpty()) continue;
            String companyKey = normalizeCompanyForMatch(dbCard.getCompany());
            CardDTO match = null;
            // 1순위: 정확 일치
            for (CardDTO j : fromJson) {
                String jName = normalizeCardName(j.getName());
                if (jName == null) jName = "";
                if (name.equals(jName) && java.util.Objects.equals(companyKey, normalizeCompanyForMatch(j.getCompany()))) {
                    match = j;
                    break;
                }
            }
            // 2순위: 정규화 이름 일치
            if (match == null) {
                String normalizedName = name.replaceAll("\\s+", "").replaceAll("[·•]", "");
                for (CardDTO j : fromJson) {
                    String jName = normalizeCardName(j.getName());
                    if (jName == null) jName = "";
                    String normalizedJName = jName.replaceAll("\\s+", "").replaceAll("[·•]", "");
                    if (normalizedName.equals(normalizedJName) && java.util.Objects.equals(companyKey, normalizeCompanyForMatch(j.getCompany()))) {
                        match = j;
                        break;
                    }
                }
            }
            // 3순위: 포함 관계
            if (match == null && name.length() >= 5) {
                for (CardDTO j : fromJson) {
                    String jName = normalizeCardName(j.getName());
                    if (jName == null) jName = "";
                    String jCompanyKey = normalizeCompanyForMatch(j.getCompany());
                    if (jName.length() >= 5 && (name.contains(jName) || jName.contains(name))
                            && (companyKey == null || jCompanyKey == null || companyKey.equals(jCompanyKey))) {
                        match = j;
                        break;
                    }
                }
            }
            if (match != null && match.getImageUrl() != null && !match.getImageUrl().isBlank()) {
                try {
                    recommendationMapper.updateCardImage(dbCard.getCardId(), match.getImageUrl());
                    updated++;
                } catch (Exception ex) {
                }
            }
        }
        return updated;
    }

    /**
     * 신한카드 JSON(credit + check)에 있는 link를 DB cards 테이블에 일괄 반영.
     * 카드명(name) + 회사(company)가 일치하는 행의 link만 갱신.
     *
     * @return 업데이트된 행 수
     */
    @Transactional
    public int syncCardLinksFromJson() {
        if (shinhanCardLoader == null) {
            return 0;
        }
        List<CardDTO> dbCards = recommendationMapper.selectAllCards();
        if (dbCards == null || dbCards.isEmpty()) {
            return 0;
        }

        List<CardDTO> fromJson = shinhanCardLoader.loadAllCards();
        if (fromJson == null || fromJson.isEmpty()) {
            return 0;
        }

        int updated = 0;
        int skippedDbNoName = 0;
        int skippedJsonNoLink = 0;
        int noMatch = 0;
        int sampleLogged = 0;

        // sync-images와 동일한 매칭 규칙(정확/정규화/포함)으로 link 반영
        for (CardDTO dbCard : dbCards) {
            String name = normalizeCardName(dbCard.getName());
            if (name == null || name.isEmpty()) {
                skippedDbNoName++;
                continue;
            }
            String companyKey = normalizeCompanyForMatch(dbCard.getCompany());

            CardDTO match = null;
            // 1순위: 정확 일치 (company: 신한 계열 통일 비교)
            for (CardDTO j : fromJson) {
                String jName = normalizeCardName(j.getName());
                if (jName == null) jName = "";
                if (name.equals(jName) && java.util.Objects.equals(companyKey, normalizeCompanyForMatch(j.getCompany()))) {
                    match = j;
                    break;
                }
            }
            // 2순위: 정규화 이름 일치 (공백/특수문자 제거)
            if (match == null) {
                String normalizedName = name.replaceAll("\\s+", "").replaceAll("[·•]", "");
                for (CardDTO j : fromJson) {
                    String jName = normalizeCardName(j.getName());
                    if (jName == null) jName = "";
                    String normalizedJName = jName.replaceAll("\\s+", "").replaceAll("[·•]", "");
                    if (normalizedName.equals(normalizedJName)
                            && java.util.Objects.equals(companyKey, normalizeCompanyForMatch(j.getCompany()))) {
                        match = j;
                        break;
                    }
                }
            }
            // 3순위: 포함 관계 (카드명이 서로 포함)
            if (match == null && name.length() >= 5) {
                for (CardDTO j : fromJson) {
                    String jName = normalizeCardName(j.getName());
                    if (jName == null) jName = "";
                    String jCompanyKey = normalizeCompanyForMatch(j.getCompany());
                    if (jName.length() >= 5 && (name.contains(jName) || jName.contains(name))) {
                        if (companyKey == null || jCompanyKey == null || companyKey.equals(jCompanyKey)) {
                            match = j;
                            break;
                        }
                    }
                }
            }

            if (match == null) {
                noMatch++;
                if (sampleLogged < 5) {
                    sampleLogged++;
                }
                continue;
            }

            String link = match.getLink();
            if (link == null || link.isBlank()) {
                skippedJsonNoLink++;
                continue;
            }

            try {
                int n = recommendationMapper.updateCardLinkById(dbCard.getCardId(), link);
                if (n > 0) {
                    updated++;
                }
            } catch (Exception ex) {
            }
        }

        return updated;
    }

    /**
     * 상위 지출 카테고리와 DB 카드 혜택을 매칭해, 관련 혜택이 있는 카드 상위 3장 추천
     * 최근 3개월 데이터 사용
     */
    public List<CardDTO> getRecommendedCards(Long userId) {
        List<CardDTO> allCards = getAllCards();
        if (allCards == null || allCards.isEmpty()) return List.of();

        List<String> topCategoryNames;
        try {
            Map<String, BigDecimal> categoryExpenses = analyzeSpendingPatternLast3Months(userId != null ? userId : 1L);
            if (categoryExpenses == null || categoryExpenses.isEmpty()) {
                topCategoryNames = List.of();
            } else {
                topCategoryNames = categoryExpenses.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("최근 3개월 소비 패턴 분석 실패: {}", e.getMessage());
            topCategoryNames = List.of();
        }

        if (topCategoryNames.isEmpty()) {
            return allCards.stream().limit(3).collect(Collectors.toList());
        }

        final List<String> categories = topCategoryNames;
        // 매칭 점수가 0보다 큰 카드만 필터링
        List<CardDTO> scored = allCards.stream()
            .filter(card -> scoreCardBenefitMatch(card, categories) > 0)
            .collect(Collectors.toList());

        // 카테고리별(도메인별)로 다른 카드가 나오도록: 상위 3개 카테고리 각각에 대해 가장 잘 맞는 카드 1장씩 선정
        List<CardDTO> recommended = pickDiverseRecommendedCards(scored, categories, 3);
        
        // 최소 3개를 보장: 3개 미만이면 점수순으로 나머지 채우기
        if (recommended.size() < 3 && !scored.isEmpty()) {
            Set<Long> chosenIds = recommended.stream().map(CardDTO::getCardId).collect(Collectors.toSet());
            List<CardDTO> remaining = scored.stream()
                .filter(c -> !chosenIds.contains(c.getCardId()))
                .sorted((a, b) -> Integer.compare(scoreCardBenefitMatch(b, categories), scoreCardBenefitMatch(a, categories)))
                .limit(3 - recommended.size())
                .collect(Collectors.toList());
            recommended.addAll(remaining);
        }
        
        // recommended가 비어있거나 3개 미만인 경우 fallback
        if (recommended.isEmpty()) {
            if (!scored.isEmpty()) {
                scored.sort((a, b) -> Integer.compare(scoreCardBenefitMatch(b, categories), scoreCardBenefitMatch(a, categories)));
                recommended = scored.stream().limit(3).collect(Collectors.toList());
            } else {
                // 매칭된 카드가 없으면 전체 중 점수순 상위 3장 (0점 포함)
                List<CardDTO> allScored = new ArrayList<>(allCards);
                allScored.sort((a, b) -> Integer.compare(scoreCardBenefitMatch(b, categories), scoreCardBenefitMatch(a, categories)));
                recommended = allScored.stream().limit(3).collect(Collectors.toList());
            }
        }
        
        // 최종적으로 정확히 3개로 제한 (3개 이상이면 상위 3개만)
        recommended = recommended.stream().limit(3).collect(Collectors.toList());

        // 전체 카드 목록(allCards)에서 cardId로 다시 조회해 동일한 카드(이미지·혜택 포함) 반환 (추천 카드 이미지가 실제 카드와 다르게 나오는 문제 방지)
        Map<Long, CardDTO> allById = allCards.stream().collect(Collectors.toMap(CardDTO::getCardId, c -> c, (a, b) -> a));
        List<CardDTO> resolved = new ArrayList<>();
        for (CardDTO ref : recommended) {
            CardDTO same = ref.getCardId() != null ? allById.get(ref.getCardId()) : null;
            resolved.add(same != null ? same : ref);
        }
        recommended = resolved;

        return recommended;
    }

    /** 사용자 카테고리(계층 포함) → 카드 혜택 키워드 매핑 */
    private static final Map<String, Set<String>> USER_CATEGORY_TO_BENEFIT_KEYWORDS = buildUserCategoryToBenefitKeywords();

    private static Map<String, Set<String>> buildUserCategoryToBenefitKeywords() {
        Map<String, Set<String>> m = new HashMap<>();
        // 주거(52): 관리비, 도시가스, 대출이자
        m.put("주거", Set.of("주거", "관리비", "전기세", "가스비", "수도세", "월세", "전세", "도시가스", "대출이자"));
        m.put("관리비", Set.of("주거", "관리비", "전기세", "가스비", "수도세", "도시가스"));
        m.put("도시가스", Set.of("주거", "가스비", "도시가스", "관리비"));
        m.put("대출이자", Set.of("대출", "이자", "주거", "관리비"));
        // 보험(56): 종합, 실비
        m.put("보험", Set.of("보험", "종합", "실비"));
        m.put("종합", Set.of("보험", "종합"));
        m.put("실비", Set.of("보험", "실비"));
        // 통신비(59): 휴대폰, 인터넷/TV, 수리/Acc
        m.put("통신비", Set.of("통신", "통신비", "휴대폰", "인터넷", "TV", "전화"));
        m.put("휴대폰", Set.of("통신", "휴대폰", "통신비"));
        m.put("인터넷/TV", Set.of("통신", "인터넷", "TV", "통신비"));
        m.put("수리/Acc", Set.of("통신", "수리"));
        // 식비(63): 식자재, 배달/외식, 간식/음료
        m.put("식비", Set.of("식비", "외식", "배달", "카페", "음식점", "레스토랑", "패스트푸드", "치킨", "피자", "식자재", "간식", "음료", "배민", "배달앱"));
        m.put("식자재", Set.of("식비", "식자재", "마트", "편의점", "장보기"));
        m.put("배달/외식", Set.of("식비", "외식", "배달", "배민", "배달앱", "치킨", "피자", "카페", "음식점"));
        m.put("간식/음료", Set.of("식비", "간식", "음료", "카페", "편의점"));
        // 생활용품(67): 생활소모품, 가전/가구, 침구/인테리어
        m.put("생활용품", Set.of("생활", "마트", "편의점", "가전", "가구", "인테리어"));
        m.put("생활소모품", Set.of("생활", "마트", "편의점"));
        m.put("가전/가구", Set.of("가전", "가구", "생활"));
        m.put("침구/인테리어", Set.of("인테리어", "가구", "생활"));
        // 꾸밈비(71): 의류/잡화, 미용/헤어, 세탁/수선
        m.put("꾸밈비", Set.of("의류", "잡화", "미용", "헤어", "쇼핑", "백화점"));
        m.put("의류/잡화", Set.of("의류", "잡화", "쇼핑", "백화점"));
        m.put("미용/헤어", Set.of("미용", "헤어", "쇼핑"));
        m.put("세탁/수선", Set.of("세탁", "의류"));
        // 건강(75): 병원/약국, 건강보조식품, 예방/검진
        m.put("건강", Set.of("의료", "병원", "약국", "건강", "보험"));
        m.put("병원/약국", Set.of("병원", "약국", "의료", "건강"));
        m.put("건강보조식품", Set.of("건강", "보조", "약국"));
        m.put("예방/검진", Set.of("건강", "검진", "병원"));
        // 자기계발(79): 운동, 도서
        m.put("자기계발", Set.of("교육", "도서", "운동", "학원"));
        m.put("운동", Set.of("운동", "스포츠", "헬스"));
        m.put("도서", Set.of("도서", "서적", "교육", "책"));
        // 자동차(82): 주유, 주차/통행료, 대중교통, 택시, 소모품/수리, 보험/세금, 세차
        m.put("자동차", Set.of("교통", "주유", "주차", "통행료", "대중교통", "택시", "하이패스", "세차"));
        m.put("주유", Set.of("주유", "주유소", "교통", "자동차"));
        m.put("주차/통행료", Set.of("주차", "통행료", "하이패스", "고속도로"));
        m.put("대중교통", Set.of("대중교통", "지하철", "버스", "교통"));
        m.put("택시", Set.of("택시", "교통"));
        m.put("소모품/수리", Set.of("자동차", "수리"));
        m.put("보험/세금", Set.of("자동차", "보험", "세금"));
        m.put("세차", Set.of("세차", "자동차"));
        // 여행(90): 국내여행, 해외여행
        m.put("여행", Set.of("여행", "국내", "해외", "숙박", "리조트", "항공"));
        m.put("국내여행", Set.of("국내여행", "여행", "숙박"));
        m.put("해외여행", Set.of("해외여행", "여행", "항공", "공항"));
        // 문화(93): OTT, 기타 입장료
        m.put("문화", Set.of("문화", "영화", "OTT", "공연", "콘서트", "전시"));
        m.put("OTT", Set.of("OTT", "영화", "스트리밍"));
        m.put("기타 입장료", Set.of("영화", "공연", "전시"));
        // 경조사(96): 가족/친척, 지인/동료
        m.put("경조사", Set.of("경조사", "선물"));
        m.put("가족/친척", Set.of("경조사", "선물"));
        m.put("지인/동료", Set.of("경조사", "선물"));
        // 기타(99)
        m.put("기타", Set.of("기타", "세금", "회비"));
        m.put("세금", Set.of("세금"));
        m.put("회비", Set.of("회비"));
        return m;
    }

    /** 카드 혜택이 사용자 상위 카테고리와 얼마나 맞는지 점수 (맞는 카테고리 수) */
    private int scoreCardBenefitMatch(CardDTO card, List<String> topCategoryNames) {
        String json = card.getBenefitsJson();
        if (json == null || json.isBlank()) return 0;

        String summaryLower = "";
        final Set<String> benefitKeywords = new HashSet<>();
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            if (node.has("summary") && !node.get("summary").asText().isBlank()) {
                summaryLower = node.get("summary").asText().toLowerCase();
                // summary에서 키워드 추출 (예: "배달 결제 연동" -> 배달, "주유·교통" -> 주유, 교통)
                extractKeywordsFromSummary(summaryLower, benefitKeywords);
            }
            if (node.has("benefits") && node.get("benefits").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode b : node.get("benefits")) {
                    if (b.has("category")) {
                        String cat = b.path("category").asText("").trim();
                        if (!cat.isEmpty()) benefitKeywords.add(cat.toLowerCase());
                    }
                }
            }
        } catch (Exception ignored) {
            // summaryLower는 이미 ""로 초기화되어 있음
        }
        
        final String finalSummaryLower = summaryLower;

        int score = 0;
        for (String userCat : topCategoryNames) {
            String lowerUserCat = userCat.toLowerCase();
            Set<String> keywordsToMatch = new HashSet<>();
            keywordsToMatch.add(lowerUserCat);
            Set<String> mapped = USER_CATEGORY_TO_BENEFIT_KEYWORDS.get(userCat);
            if (mapped != null) keywordsToMatch.addAll(mapped.stream().map(String::toLowerCase).collect(Collectors.toSet()));
            // 부모 카테고리명이 키에 있으면 해당 키워드도 추가 (예: "배달/외식" -> 식비 계열)
            for (Map.Entry<String, Set<String>> e : USER_CATEGORY_TO_BENEFIT_KEYWORDS.entrySet()) {
                if (e.getValue().stream().anyMatch(k -> k.equalsIgnoreCase(userCat))) {
                    keywordsToMatch.add(e.getKey().toLowerCase());
                    keywordsToMatch.addAll(e.getValue().stream().map(String::toLowerCase).collect(Collectors.toSet()));
                }
            }
            boolean matched = keywordsToMatch.stream().anyMatch(kw ->
                finalSummaryLower.contains(kw) || benefitKeywords.stream().anyMatch(bk -> bk.contains(kw) || kw.contains(bk)));
            if (matched) score++;
        }
        return score;
    }

    /** summary 텍스트에서 카테고리 관련 키워드 추출 (예: "배달 결제 연동" -> 배달, 배민, 외식) */
    private void extractKeywordsFromSummary(String summaryLower, Set<String> keywords) {
        // summary에서 직접 키워드 추출
        if (summaryLower.contains("배달") || summaryLower.contains("배민") || summaryLower.contains("커넥트")) {
            keywords.add("배달"); keywords.add("배민"); keywords.add("외식"); keywords.add("식비");
        }
        if (summaryLower.contains("주유") || summaryLower.contains("충전")) {
            keywords.add("주유"); keywords.add("자동차"); keywords.add("교통");
        }
        if (summaryLower.contains("교통") || summaryLower.contains("대중교통") || summaryLower.contains("지하철") || summaryLower.contains("버스") || summaryLower.contains("택시")) {
            keywords.add("교통"); keywords.add("대중교통"); keywords.add("자동차");
        }
        if (summaryLower.contains("통신") || summaryLower.contains("휴대폰") || summaryLower.contains("인터넷")) {
            keywords.add("통신"); keywords.add("통신비"); keywords.add("휴대폰");
        }
        if (summaryLower.contains("주거") || summaryLower.contains("관리비") || summaryLower.contains("전기") || summaryLower.contains("가스") || summaryLower.contains("수도")) {
            keywords.add("주거"); keywords.add("관리비");
        }
        if (summaryLower.contains("여행") || summaryLower.contains("해외") || summaryLower.contains("리조트") || summaryLower.contains("숙박")) {
            keywords.add("여행"); keywords.add("해외여행"); keywords.add("국내여행");
        }
        if (summaryLower.contains("교육") || summaryLower.contains("학원") || summaryLower.contains("도서")) {
            keywords.add("교육"); keywords.add("자기계발"); keywords.add("도서");
        }
        if (summaryLower.contains("쇼핑") || summaryLower.contains("마트") || summaryLower.contains("편의점") || summaryLower.contains("백화점")) {
            keywords.add("쇼핑"); keywords.add("생활용품");
        }
        if (summaryLower.contains("일상") || summaryLower.contains("생활")) {
            // 일상/생활은 일반적인 카테고리들에 매칭
            keywords.add("식비"); keywords.add("생활용품"); keywords.add("쇼핑");
        }
        if (summaryLower.contains("외식") || summaryLower.contains("음식") || summaryLower.contains("카페") || summaryLower.contains("레스토랑")) {
            keywords.add("외식"); keywords.add("식비"); keywords.add("배달");
        }
        if (summaryLower.contains("문화") || summaryLower.contains("영화") || summaryLower.contains("공연") || summaryLower.contains("OTT")) {
            keywords.add("문화"); keywords.add("영화");
        }
        if (summaryLower.contains("건강") || summaryLower.contains("병원") || summaryLower.contains("약국")) {
            keywords.add("건강"); keywords.add("의료");
        }
    }

    /** 단일 카테고리에 대한 카드 매칭 점수 (다양한 추천용) */
    private int scoreCardBenefitMatchForOneCategory(CardDTO card, String categoryName) {
        return scoreCardBenefitMatch(card, List.of(categoryName));
    }

    /** 상위 카테고리별로 서로 다른 카드 1장씩 선정해 최대 3장 반환 (배민만 나오는 것 방지) */
    private List<CardDTO> pickDiverseRecommendedCards(List<CardDTO> scored, List<String> topCategories, int maxCards) {
        if (scored.isEmpty() || topCategories.isEmpty()) return List.of();
        List<String> categoriesToUse = topCategories.stream().limit(maxCards).collect(Collectors.toList());
        List<CardDTO> result = new ArrayList<>();
        Set<Long> chosenIds = new HashSet<>();
        for (String cat : categoriesToUse) {
            CardDTO best = scored.stream()
                .filter(c -> !chosenIds.contains(c.getCardId()))
                .max(Comparator.comparingInt(c -> scoreCardBenefitMatchForOneCategory(c, cat)))
                .orElse(null);
            if (best != null && scoreCardBenefitMatchForOneCategory(best, cat) > 0) {
                result.add(best);
                chosenIds.add(best.getCardId());
            } else {
            }
        }
        return result;
    }

    /** DB에 금융상품이 없고 API 키가 설정된 경우 공공데이터포털 API로 조회 후 DB에 적재 */
    public List<ProductDTO> getAllProducts() {
        try {
            List<ProductDTO> products = recommendationMapper.selectAllProducts();
            if ((products == null || products.isEmpty()) && financialProductApiService != null && financialProductApiService.isApiKeyConfigured()) {
                List<ProductDTO> fetched = financialProductApiService.fetchDepositProducts(500);
                for (ProductDTO p : fetched) {
                    p.setProductId(null);
                    try {
                        recommendationMapper.insertProduct(p);
                    } catch (Exception ex) {
                        log.warn("금융상품 insert 실패(무시): {} - {}", p.getName(), ex.getMessage());
                    }
                }
                products = recommendationMapper.selectAllProducts();
            }
            return (products == null || products.isEmpty()) ? List.of() : products;
        } catch (Exception e) {
            log.warn("금융상품 목록 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 타입별 금융상품 조회
     */
    public List<ProductDTO> getProductsByType(String type) {
        try {
            List<ProductDTO> products = recommendationMapper.selectProductsByType(type);
            return (products == null || products.isEmpty()) ? List.of() : products;
        } catch (Exception e) {
            log.warn("금융상품(type={}) 조회 실패: {}", type, e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 사용자 보유 카드 목록
     */
    public List<CardDTO> getUserCards(Long userId) {
        try {
            List<CardDTO> cards = recommendationMapper.selectUserCards(userId);
            return (cards == null) ? List.of() : cards;
        } catch (Exception e) {
            return List.of();
        }
    }
    
    /**
     * 사용자 카드 추가
     */
    public void addUserCard(Long userId, Long cardId) {
        recommendationMapper.insertUserCard(userId, cardId);
    }
    
    /**
     * 사용자 카드 삭제
     */
    public void removeUserCard(Long userId, Long cardId) {
        recommendationMapper.deleteUserCard(userId, cardId);
    }
    
    /**
     * 추천 내역 조회
     */
    public List<RecommendationDTO> getRecommendations(Long userId) {
        try {
            List<RecommendationDTO> recs = recommendationMapper.selectRecommendationsByUser(userId);
            return (recs == null) ? List.of() : recs;
        } catch (Exception e) {
            log.warn("추천 내역 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 월별 추천 내역 조회
     */
    public List<RecommendationDTO> getRecommendationsByYearMonth(Long userId, String yearMonth) {
        try {
            List<RecommendationDTO> recs = recommendationMapper.selectRecommendationsByYearMonth(userId, yearMonth);
            if (recs == null || recs.isEmpty()) return List.of();
            return recs;
        } catch (Exception e) {
            log.warn("월별 추천 내역 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * 소비 패턴 기반 추천 생성
     */
    @Transactional
    public RecommendationResultDTO generateRecommendations(Long userId) throws Exception {
        String yearMonth = LocalDate.now().format(YEAR_MONTH_FORMATTER);
        
        // 1. 소비 패턴 분석
        Map<String, Object> spendingPattern;
        try {
            spendingPattern = analyzeSpendingPattern(userId, yearMonth);
        } catch (Exception e) {
            log.warn("소비 패턴 분석 실패 → 빈 패턴 사용: {}", e.getMessage());
            spendingPattern = new HashMap<>();
            spendingPattern.put("yearMonth", yearMonth);
            spendingPattern.put("categoryExpenses", new HashMap<String, BigDecimal>());
            spendingPattern.put("topCategory", "없음");
            spendingPattern.put("totalExpense", BigDecimal.ZERO);
        }

        // 1-1. 월별 분석 결과(있으면) 추가: metricsJson / llmSummaryText를 LLM 컨텍스트로 활용
        try {
            MonthlyReportDTO report = monthlyReportMapper.selectReportByYearMonth(userId, toDashedYearMonth(yearMonth));
            if (report != null) {
                spendingPattern.put("monthlyReportMetricsJson", report.getMetricsJson());
                spendingPattern.put("monthlyReportSummary", report.getLlmSummaryText());
            }
        } catch (Exception ignored) {}
        
        // 2. 이용 가능한 카드/상품 조회
        List<CardDTO> cards = getAllCards();
        List<ProductDTO> products = getAllProducts();
        
        // 3. LLM 추천 생성
        List<Map<String, Object>> cardMaps = cards.stream()
            .map(c -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", c.getCardId());
                map.put("name", c.getName());
                map.put("company", c.getCompany());
                map.put("benefits", c.getBenefitsJson());
                return map;
            })
            .collect(Collectors.toList());
        
        List<Map<String, Object>> productMaps = products.stream()
            .map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getProductId());
                map.put("type", p.getType());
                map.put("name", p.getName());
                map.put("bank", p.getBank());
                map.put("rate", p.getRate());
                return map;
            })
            .collect(Collectors.toList());
        
        String llmRecommendation;
        try {
            llmRecommendation = geminiService.generateRecommendations(spendingPattern, cardMaps, productMaps);
        } catch (Exception e) {
            log.warn("LLM 추천 생성 실패 → 더미 분석 문구 사용: {}", e.getMessage());
            llmRecommendation = "현재 LLM 분석을 수행할 수 없어 기본 추천 로직으로 결과를 제공합니다.";
        }
        
        // 4. 기존 추천 삭제 및 새 추천 저장
        try {
            recommendationMapper.deleteOldRecommendations(userId, yearMonth);
        } catch (Exception e) {
            log.warn("기존 추천 삭제 실패(무시): {}", e.getMessage());
        }
        
        // 간단한 점수 기반 추천 생성 (실제로는 더 정교한 알고리즘 필요)
        List<RecommendationDTO> savedRecommendations = new ArrayList<>();
        
        // 카테고리별 지출 비중에 따른 카드 추천
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> categoryExpenses = (Map<String, BigDecimal>) spendingPattern.get("categoryExpenses");
        
        if (cards != null && !cards.isEmpty()) {
            // 상위 카드 추천
            CardDTO topCard = cards.get(0);
            RecommendationDTO cardRec = new RecommendationDTO();
            cardRec.setUserId(userId);
            cardRec.setYearMonth(yearMonth);
            cardRec.setRecType("CARD");
            cardRec.setItemId(topCard.getCardId());
            cardRec.setScore(BigDecimal.valueOf(0.85));
            cardRec.setReasonText("소비 패턴에 적합한 카드입니다.");
            try {
                recommendationMapper.insertRecommendation(cardRec);
            } catch (Exception e) {
                // DB 저장 실패해도 응답은 정상 반환
            }
            savedRecommendations.add(cardRec);
        }
        
        if (products != null && !products.isEmpty()) {
            // 저축 상품 추천
            ProductDTO topProduct = products.stream()
                .filter(p -> "적금".equals(p.getType()) || "예금".equals(p.getType()))
                .findFirst()
                .orElse(products.get(0));
            
            RecommendationDTO productRec = new RecommendationDTO();
            productRec.setUserId(userId);
            productRec.setYearMonth(yearMonth);
            productRec.setRecType("PRODUCT");
            productRec.setItemId(topProduct.getProductId());
            productRec.setScore(BigDecimal.valueOf(0.80));
            productRec.setReasonText("저축 목표 달성에 도움이 되는 상품입니다.");
            try {
                recommendationMapper.insertRecommendation(productRec);
            } catch (Exception e) {
                log.warn("상품 추천 저장 실패(무시): {}", e.getMessage());
            }
            savedRecommendations.add(productRec);
        }
        
        RecommendationResultDTO result = new RecommendationResultDTO();
        result.setSpendingPattern(spendingPattern);
        result.setLlmAnalysis(llmRecommendation);
        result.setRecommendations(savedRecommendations);
        
        return result;
    }

    /**
     * 카드별 "이 카드가 적합한 이유" LLM 생성 (추천 페이지용)
     */
    public String getCardSuitableReason(Long cardId, Long userId) {
        if (cardId == null) return "회원님의 소비 패턴과 이 카드 혜택을 맞춰봤어요. 잘 어울리실 거예요.";
        CardDTO card;
        try {
            card = recommendationMapper.selectCardById(cardId);
            if (card == null) {
                List<CardDTO> all = getAllCards();
                card = all.stream().filter(c -> java.util.Objects.equals(c.getCardId(), cardId)).findFirst().orElse(null);
            }
        } catch (Exception e) {
            card = getAllCards().stream().filter(c -> java.util.Objects.equals(c.getCardId(), cardId)).findFirst().orElse(null);
        }
        if (card == null) return "회원님의 소비 패턴과 이 카드 혜택을 맞춰봤어요. 잘 어울리실 거예요.";

        String yearMonth = LocalDate.now().format(YEAR_MONTH_FORMATTER);
        Map<String, Object> spendingPattern;
        try {
            spendingPattern = analyzeSpendingPattern(userId != null ? userId : 1L, yearMonth);
        } catch (Exception e) {
            spendingPattern = new HashMap<>();
            spendingPattern.put("yearMonth", yearMonth);
            spendingPattern.put("topCategory", "없음");
            spendingPattern.put("categoryExpenses", new HashMap<String, BigDecimal>());
        }
        String monthlySummary = null;
        try {
            MonthlyReportDTO report = monthlyReportMapper.selectReportByYearMonth(userId != null ? userId : 1L, toDashedYearMonth(yearMonth));
            if (report != null) monthlySummary = report.getLlmSummaryText();
        } catch (Exception ignored) {}

        // 1. Python AI 서비스 우선 시도 (LLM 형태로 추천 이유 생성)
        if (recommendationAIService != null) {
            try {
                String reason = recommendationAIService.generateCardSuitableReason(
                    card.getName(),
                    card.getCompany(),
                    card.getBenefitsJson(),
                    spendingPattern,
                    monthlySummary
                );
                if (reason != null && !reason.isBlank()) {
                    reason = removePromptInstructions(reason);
                    return reason;
                }
            } catch (Exception e) {
                log.debug("Python AI 서비스 호출 실패, Java GeminiService로 폴백: {}", e.getMessage());
            }
        }

        // 2. Java GeminiService 폴백
        if (geminiService != null) {
            try {
                String reason = geminiService.generateCardSuitableReason(
                    card.getName(),
                    card.getCompany(),
                    card.getBenefitsJson(),
                    spendingPattern,
                    monthlySummary
                );
                if (reason != null && !reason.isBlank()) {
                    reason = removePromptInstructions(reason);
                    return reason;
                }
            } catch (Exception e) {
                log.warn("LLM 적합 이유 생성 실패, 템플릿 사용: {}", e.getMessage());
            }
        }
        return buildTemplateCardReason(card, spendingPattern);
    }

    /**
     * 여러 카드에 대한 적합 이유를 한 번의 Gemini 호출로 생성 (할당량 429 방지)
     * @param cardIds 카드 ID 목록 (최대 3개 권장)
     * @return Map with "reasons" (cardId -> reason), "templateUsed" (true when LLM failed and template was used)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCardSuitableReasonsBatch(List<Long> cardIds, Long userId) {
        Map<Long, String> result = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        response.put("reasons", result);
        response.put("templateUsed", Boolean.FALSE);
        if (cardIds == null || cardIds.isEmpty()) {
            return response;
        }

        String yearMonth = LocalDate.now().format(YEAR_MONTH_FORMATTER);
        Map<String, Object> spendingPattern;
        try {
            spendingPattern = analyzeSpendingPattern(userId != null ? userId : 1L, yearMonth);
        } catch (Exception e) {
            spendingPattern = new HashMap<>();
            spendingPattern.put("yearMonth", yearMonth);
            spendingPattern.put("topCategory", "없음");
            spendingPattern.put("categoryExpenses", new HashMap<String, BigDecimal>());
        }
        String monthlySummary = null;
        try {
            MonthlyReportDTO report = monthlyReportMapper.selectReportByYearMonth(userId != null ? userId : 1L, toDashedYearMonth(yearMonth));
            if (report != null) monthlySummary = report.getLlmSummaryText();
        } catch (Exception ignored) {}

        List<CardDTO> cards = new ArrayList<>();
        for (Long cardId : cardIds) {
            CardDTO card;
            try {
                card = recommendationMapper.selectCardById(cardId);
                if (card == null) {
                    List<CardDTO> all = getAllCards();
                    card = all.stream().filter(c -> java.util.Objects.equals(c.getCardId(), cardId)).findFirst().orElse(null);
                }
            } catch (Exception e) {
                card = getAllCards().stream().filter(c -> java.util.Objects.equals(c.getCardId(), cardId)).findFirst().orElse(null);
            }
            if (card != null) cards.add(card);
        }

        if (cards.isEmpty()) {
            response.put("templateUsed", Boolean.TRUE);
            return response;
        }

        List<Map<String, Object>> cardsInfo = cards.stream()
            .map(c -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", c.getName());
                m.put("company", c.getCompany());
                m.put("benefitsJson", c.getBenefitsJson());
                return m;
            })
            .collect(Collectors.toList());

        boolean usedTemplate = false;
        List<String> reasons = null;
        
        // 1. Python AI 서비스 우선 시도
        if (recommendationAIService != null) {
            try {
                reasons = recommendationAIService.generateCardSuitableReasonsBatch(cardsInfo, spendingPattern, monthlySummary);
                if (reasons != null) {
                    if (reasons.size() == cards.size()) {
                        // 각 이유가 비어있지 않은지 확인
                        long nonEmptyCount = reasons.stream().filter(r -> r != null && !r.isBlank()).count();
                    } else {
                    }
                } else {
                }
            } catch (Exception e) {
            }
        } else {
        }
        
        // 2. Java GeminiService 폴백
        if (reasons == null && geminiService != null) {
            try {
                reasons = geminiService.generateCardSuitableReasonsBatch(cardsInfo, spendingPattern, monthlySummary);
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    try {
                        Thread.sleep(20_000);
                        reasons = geminiService.generateCardSuitableReasonsBatch(cardsInfo, spendingPattern, monthlySummary);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } catch (Exception retryEx) {
                    }
                }
                if (reasons == null) {
                }
            } catch (Exception e) {
            }
        }

        if (reasons != null && reasons.size() == cards.size()) {
            for (int i = 0; i < cards.size(); i++) {
                String reason = reasons.get(i);
                if (reason != null && !reason.isBlank()) {
                    reason = removePromptInstructions(reason);
                    result.put(cards.get(i).getCardId(), reason);
                } else {
                    result.put(cards.get(i).getCardId(), buildTemplateCardReason(cards.get(i), spendingPattern));
                    usedTemplate = true;
                }
            }
            response.put("templateUsed", usedTemplate);
            return response;
        }
        if (reasons != null) {
        }

        for (CardDTO card : cards) {
            result.put(card.getCardId(), buildTemplateCardReason(card, spendingPattern));
        }
        response.put("templateUsed", Boolean.TRUE);
        return response;
    }

    /** Gemini 실패 시: 상위 지출 카테고리와 카드 혜택을 비교 분석해 신뢰감 있는 추천 근거 생성 */
    private String buildTemplateCardReason(CardDTO card, Map<String, Object> spendingPattern) {
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> categoryExpenses = (Map<String, BigDecimal>) spendingPattern.get("categoryExpenses");
        List<String> topCategories = (categoryExpenses == null || categoryExpenses.isEmpty())
            ? List.of()
            : categoryExpenses.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 카테고리별 지출 금액도 함께 추출 (비중 설명용)
        Map<String, BigDecimal> topCategoryAmounts = new HashMap<>();
        if (categoryExpenses != null) {
            for (String cat : topCategories) {
                BigDecimal amount = categoryExpenses.get(cat);
                if (amount != null) topCategoryAmounts.put(cat, amount);
            }
        }
        BigDecimal totalExpense = (BigDecimal) spendingPattern.getOrDefault("totalExpense", BigDecimal.ZERO);

        // 카드 혜택 파싱 (카테고리별로 정리)
        Map<String, String> categoryBenefits = new HashMap<>(); // 카테고리 -> 혜택 설명
        List<String> allBenefitLines = new ArrayList<>();
        String json = card.getBenefitsJson();
        if (json != null && !json.isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
                if (node.has("benefits") && node.get("benefits").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode b : node.get("benefits")) {
                        String cat = b.has("category") ? b.path("category").asText("").trim() : "";
                        String type = b.has("type") ? b.path("type").asText("") : "";
                        String benefitDesc = null;
                        if (b.has("value")) {
                            int v = b.get("value").asInt(0);
                            if ("percent".equals(type)) {
                                benefitDesc = cat + " " + v + "% 캐시백";
                                categoryBenefits.put(cat, benefitDesc);
                            } else if ("fixed".equals(type)) {
                                benefitDesc = cat + " " + String.format("%,d", v) + "원 할인";
                                categoryBenefits.put(cat, benefitDesc);
                            } else if (!cat.isEmpty()) {
                                benefitDesc = cat;
                                categoryBenefits.put(cat, cat);
                            }
                        } else if (!cat.isEmpty()) {
                            benefitDesc = cat;
                            categoryBenefits.put(cat, cat);
                        }
                        if (benefitDesc != null) allBenefitLines.add(benefitDesc);
                    }
                }
                if (allBenefitLines.isEmpty() && node.has("summary") && !node.get("summary").asText().isBlank()) {
                    String summary = node.get("summary").asText().trim();
                    allBenefitLines.add(summary);
                }
            } catch (Exception ignored) {}
        }
        if (allBenefitLines.isEmpty()) allBenefitLines.add("다양한 혜택");

        // 상단 요약 3줄 생성
        List<String> summaryLines = new ArrayList<>();
        StringBuilder detailSb = new StringBuilder();
        
        if (!topCategories.isEmpty()) {
            // 요약 1줄: 상위 지출 카테고리
            List<String> categoryDescs = new ArrayList<>();
            for (int i = 0; i < Math.min(3, topCategories.size()); i++) {
                String cat = topCategories.get(i);
                BigDecimal amount = topCategoryAmounts.get(cat);
                if (amount != null && totalExpense.compareTo(BigDecimal.ZERO) > 0) {
                    int percent = amount.multiply(BigDecimal.valueOf(100)).divide(totalExpense, 0, java.math.RoundingMode.HALF_UP).intValue();
                    categoryDescs.add(cat + "(" + percent + "%)");
                } else {
                    categoryDescs.add(cat);
                }
            }
            summaryLines.add("회원님의 주요 지출은 " + String.join(", ", categoryDescs) + (topCategories.size() > 3 ? " 등" : "") + "입니다.");
            
            // 카드 혜택과 매칭 분석
            List<String> matchedPairs = new ArrayList<>();
            for (String cat : topCategories) {
                String benefit = categoryBenefits.entrySet().stream()
                    .filter(e -> {
                        String benefitCat = e.getKey();
                        return benefitCat.contains(cat) || cat.contains(benefitCat) 
                            || (cat.contains("주거") && benefitCat.contains("주거"))
                            || (cat.contains("식비") && benefitCat.contains("식비") || benefitCat.contains("외식"))
                            || (cat.contains("교통") && benefitCat.contains("교통") || benefitCat.contains("대중교통"))
                            || (cat.contains("통신") && benefitCat.contains("통신"));
                    })
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
                if (benefit != null) {
                    matchedPairs.add(cat + " 지출 → " + benefit);
                }
            }
            
            if (!matchedPairs.isEmpty()) {
                // 요약 2줄: 매칭된 혜택
                summaryLines.add("이 카드는 " + matchedPairs.get(0) + " 혜택을 제공합니다.");
                if (matchedPairs.size() > 1) {
                    summaryLines.add("또한 " + matchedPairs.get(1) + " 등 추가 혜택이 있습니다.");
                } else {
                    summaryLines.add("회원님의 소비 패턴과 높은 적합도를 보입니다.");
                }
                
                // 상세 설명
                detailSb.append("회원님의 소비 패턴을 분석해보니 ");
                detailSb.append(String.join(", ", categoryDescs));
                if (topCategories.size() > 3) detailSb.append(" 등");
                detailSb.append("에 집중적으로 지출하시는 패턴이에요. ");
                detailSb.append("이 카드는 ");
                for (int i = 0; i < matchedPairs.size(); i++) {
                    if (i > 0) detailSb.append(i == matchedPairs.size() - 1 ? " 그리고 " : ", ");
                    detailSb.append(matchedPairs.get(i));
                }
                detailSb.append(" 혜택을 제공해요. ");
                detailSb.append("회원님처럼 ");
                if (topCategories.size() >= 2) {
                    detailSb.append(topCategories.get(0)).append("와 ").append(topCategories.get(1));
                    if (topCategories.size() > 2) detailSb.append(" 등");
                } else {
                    detailSb.append(topCategories.get(0));
                }
                detailSb.append("에 집중적으로 소비하시는 분께 특히 적합한 카드예요.");
            } else {
                // 매칭되는 혜택이 없어도 전체 혜택 소개
                summaryLines.add("이 카드는 " + String.join(", ", allBenefitLines.subList(0, Math.min(2, allBenefitLines.size()))) + " 혜택이 있습니다.");
                summaryLines.add("회원님의 소비 패턴과 비교해보니 적합한 카드입니다.");
                
                detailSb.append("회원님의 소비 패턴을 분석해보니 ");
                detailSb.append(String.join(", ", categoryDescs));
                if (topCategories.size() > 3) detailSb.append(" 등");
                detailSb.append("에 집중적으로 지출하시는 패턴이에요. ");
                detailSb.append("이 카드는 ").append(String.join(", ", allBenefitLines.subList(0, Math.min(3, allBenefitLines.size()))));
                if (allBenefitLines.size() > 3) detailSb.append(" 등");
                detailSb.append(" 혜택이 있어요. ");
                detailSb.append("회원님의 소비 패턴과 비교해보니, ");
                detailSb.append(topCategories.get(0));
                if (topCategories.size() > 1) detailSb.append("·").append(topCategories.get(1));
                detailSb.append(" 지출에서 이 카드의 혜택을 활용하실 수 있어요.");
            }
        } else {
            // 지출 데이터가 없는 경우
            summaryLines.add("이 카드는 " + String.join(", ", allBenefitLines.subList(0, Math.min(2, allBenefitLines.size()))) + " 혜택을 제공합니다.");
            summaryLines.add("일상 생활 전반에서 활용할 수 있는 혜택이 많습니다.");
            summaryLines.add("회원님께 추천드리는 카드입니다.");
            
            detailSb.append("이 카드는 ").append(String.join(", ", allBenefitLines.subList(0, Math.min(3, allBenefitLines.size()))));
            if (allBenefitLines.size() > 3) detailSb.append(" 등");
            detailSb.append(" 혜택을 제공해요. ");
            detailSb.append("일상 생활 전반에서 활용할 수 있는 혜택이 많아서 추천해 드렸어요.");
        }
        
        // 요약 3줄이 안 채워졌으면 채우기
        while (summaryLines.size() < 3) {
            summaryLines.add("회원님께 적합한 카드입니다.");
        }
        
        // 형식: 요약 3줄 + 구분자 + 상세 설명
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(3, summaryLines.size()); i++) {
            result.append(summaryLines.get(i));
            if (i < 2) result.append("\n");
        }
        result.append("\n\n---\n\n");
        result.append(detailSb.toString());
        
        return result.toString();
    }

    /**
     * Gemini 응답에서 프롬프트 지시사항 제거
     * 예: "1. 먼저 3줄로 주요 근거를 요약해주세요..." 같은 지시사항 제거
     */
    private String removePromptInstructions(String text) {
        if (text == null || text.isBlank()) return text;
        
        // 여러 줄로 나누기
        String[] lines = text.split("\n");
        StringBuilder cleaned = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // 지시사항 패턴 감지 (더 포괄적으로)
            boolean isInstruction = 
                // 번호로 시작하는 지시사항 (예: "1. 먼저 3줄로...")
                trimmed.matches("^\\d+\\.\\s*.*(요약|작성|나열|적어).*") ||
                // "먼저", "그 다음", "마지막" 등으로 시작하는 지시사항
                trimmed.matches("^(먼저|그\\s*다음|마지막|그리고).*요약.*") ||
                trimmed.matches("^(먼저|그\\s*다음|마지막|그리고).*작성.*") ||
                // 형식 관련 지시사항
                trimmed.matches("^\\[형식\\].*") ||
                trimmed.matches("^\\[.*\\].*") ||
                // "요약해주세요", "작성해주세요" 등이 포함된 줄
                (trimmed.contains("요약해주세요") && (trimmed.contains("줄") || trimmed.contains("문장"))) ||
                (trimmed.contains("작성해주세요") && (trimmed.contains("줄") || trimmed.contains("문장"))) ||
                (trimmed.contains("나열해주세요")) ||
                // "각 줄은" 같은 지시사항
                trimmed.matches("^각\\s*줄은.*") ||
                trimmed.matches("^각\\s*문장은.*") ||
                // "예시:" 같은 지시사항
                trimmed.matches("^예시:.*") ||
                trimmed.matches("^[-•]\\s*예시.*");
            
            // 지시사항이 아니면 추가
            if (!isInstruction && !trimmed.isEmpty()) {
                if (cleaned.length() > 0) cleaned.append("\n");
                cleaned.append(line);
            }
        }
        
        return cleaned.toString().trim();
    }

    private String toDashedYearMonth(String yyyymm) {
        if (yyyymm == null) return null;
        String s = yyyymm.trim();
        if (s.contains("-")) return s;
        if (s.length() == 6) return s.substring(0, 4) + "-" + s.substring(4, 6);
        return s;
    }

    /**
     * 최근 3개월 소비 패턴 분석 (카테고리별 지출만 반환)
     */
    private Map<String, BigDecimal> analyzeSpendingPatternLast3Months(Long userId) throws Exception {
        LocalDate now = LocalDate.now();
        Map<String, BigDecimal> categoryExpenses = new HashMap<>();
        
        // 최근 3개월 데이터 조회
        for (int i = 0; i < 3; i++) {
            LocalDate month = now.minusMonths(i);
            String yearMonth = month.format(YEAR_MONTH_FORMATTER);
            List<TransactionDTO> transactions = transactionMapper.selectTransactionsByYearMonth(userId, yearMonth);
            
            // 카테고리별 지출 누적
            transactions.stream()
                .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) < 0)
                .forEach(t -> {
                    String category = t.getCategoryName() != null ? t.getCategoryName() : "미분류";
                    BigDecimal amount = t.getAmount().abs();
                    categoryExpenses.put(category, categoryExpenses.getOrDefault(category, BigDecimal.ZERO).add(amount));
                });
        }
        
        return categoryExpenses;
    }

    /**
     * 소비 패턴 분석
     */
    private Map<String, Object> analyzeSpendingPattern(Long userId, String yearMonth) throws Exception {
        List<TransactionDTO> transactions = transactionMapper.selectTransactionsByYearMonth(userId, yearMonth);
        
        Map<String, Object> pattern = new HashMap<>();
        
        // 총 지출
        BigDecimal totalExpense = transactions.stream()
            .map(TransactionDTO::getAmount)
            .filter(a -> a != null && a.compareTo(BigDecimal.ZERO) < 0)
            .map(BigDecimal::abs)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 카테고리별 지출
        Map<String, BigDecimal> categoryExpenses = transactions.stream()
            .filter(t -> t.getAmount() != null && t.getAmount().compareTo(BigDecimal.ZERO) < 0)
            .collect(Collectors.groupingBy(
                t -> t.getCategoryName() != null ? t.getCategoryName() : "미분류",
                Collectors.reducing(BigDecimal.ZERO, 
                    t -> t.getAmount().abs(), 
                    BigDecimal::add)
            ));
        
        // 거래 건수
        int transactionCount = transactions.size();
        
        // 평균 거래 금액
        BigDecimal avgAmount = transactionCount > 0 
            ? totalExpense.divide(BigDecimal.valueOf(transactionCount), 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        pattern.put("yearMonth", yearMonth);
        pattern.put("totalExpense", totalExpense);
        pattern.put("categoryExpenses", categoryExpenses);
        pattern.put("transactionCount", transactionCount);
        pattern.put("averageAmount", avgAmount);
        
        // 가장 많이 사용한 카테고리
        String topCategory = categoryExpenses.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("없음");
        pattern.put("topCategory", topCategory);
        
        return pattern;
    }
}
