package com.smartbudget.analysis;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 질문 Intent 분류: 룰 기반 키워드. 필요 시 LLM 호출로 확장.
 */
@Component
public class IntentClassifier {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private static final Pattern[] CAUSE_PATTERNS = {
        Pattern.compile("왜\\s*(늘었|많아|썼|나왔|올랐)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(원인|이유|원인 추정|분석)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(늘어난|많아진|늘었어|많아졌)", Pattern.CASE_INSENSITIVE),
    };

    private static final Pattern[] COMPARISON_PATTERNS = {
        Pattern.compile("(전월|지난달|전달|저번달|비교|대비|vs|대조)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(달라|차이|늘었나|줄었나)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(올해|작년|전년)", Pattern.CASE_INSENSITIVE),
    };

    private static final Pattern[] BUDGET_PATTERNS = {
        Pattern.compile("(예산|썼어|쓴 비율|사용률|초과|남았)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(예산 대비|예산 안|넘쳤)", Pattern.CASE_INSENSITIVE),
    };

    private static final Pattern[] PATTERN_PATTERNS = {
        Pattern.compile("(패턴|요일|주차|언제|몇 주|가맹점|가게|어디서)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(집중|많이 쓴|자주)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(요일별|주차별|가맹점별)", Pattern.CASE_INSENSITIVE),
    };

    /** 연월 추출: "2025년 1월", "202501", "1월" 등 */
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile(
        "(\\d{4})[-년]?\\s*(\\d{1,2})월?|(\\d{4})(\\d{2})|(이번\\s*달|이번달|이번 달)|(지난\\s*달|지난달|전월|전달)"
    );

    /** 카테고리 힌트: 식비, 카페, 교통, 쇼핑, 의료 등 */
    private static final Pattern[] CATEGORY_PATTERNS = {
        Pattern.compile("(식비|식사|밥|음식|배달|야식)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(카페|커피|간식|디저트)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(교통|택시|버스|지하철|주유|차)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(쇼핑|의류|잡화|온라인)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(의료|건강|병원|약)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(문화|영화|취미|레저)", Pattern.CASE_INSENSITIVE),
    };

    private static final String[] CATEGORY_MAPPING = {
        "식비", "카페", "교통", "쇼핑", "의료", "문화"
    };

    /**
     * 질문 텍스트로 Intent 분류. 기본 연월은 currentYearMonth 사용.
     */
    public IntentClassificationDTO classify(String question, String defaultYearMonth) {
        if (question == null || question.isBlank()) {
            return IntentClassificationDTO.builder()
                .intent(IntentClassificationDTO.INTENT_GENERAL)
                .yearMonth(defaultYearMonth)
                .needsFollowUp(false)
                .build();
        }

        String q = question.trim();
        String intent = classifyIntent(q);
        String extractedYearMonth = extractYearMonth(q, defaultYearMonth);
        String categoryHint = extractCategoryHint(q);
        // 기간 미지정으로 기본값 사용 시에만 되묻기 1회
        boolean needsFollowUp = (extractedYearMonth == null && defaultYearMonth != null);

        return IntentClassificationDTO.builder()
            .intent(intent)
            .yearMonth(extractedYearMonth != null ? extractedYearMonth : defaultYearMonth)
            .categoryHint(categoryHint)
            .needsFollowUp(needsFollowUp)
            .build();
    }

    private String classifyIntent(String q) {
        int cause = 0, comp = 0, budget = 0, pattern = 0;
        for (Pattern p : CAUSE_PATTERNS) if (p.matcher(q).find()) cause++;
        for (Pattern p : COMPARISON_PATTERNS) if (p.matcher(q).find()) comp++;
        for (Pattern p : BUDGET_PATTERNS) if (p.matcher(q).find()) budget++;
        for (Pattern p : PATTERN_PATTERNS) if (p.matcher(q).find()) pattern++;

        if (cause > 0 && cause >= comp && cause >= budget && cause >= pattern) return IntentClassificationDTO.INTENT_CAUSE;
        if (comp > 0 && comp >= budget && comp >= pattern) return IntentClassificationDTO.INTENT_COMPARISON;
        if (budget > 0 && budget >= pattern) return IntentClassificationDTO.INTENT_BUDGET;
        if (pattern > 0) return IntentClassificationDTO.INTENT_PATTERN;
        return IntentClassificationDTO.INTENT_GENERAL;
    }

    private String extractYearMonth(String q, String defaultYearMonth) {
        var m = YEAR_MONTH_PATTERN.matcher(q);
        if (!m.find()) return null;
        try {
            if (m.group(1) != null && m.group(2) != null) {
                int y = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                return YearMonth.of(y, mo).format(YYYYMM);
            }
            if (m.group(3) != null && m.group(4) != null) {
                return m.group(3) + m.group(4);
            }
            if (m.group(5) != null && m.group(5).contains("이번")) {
                return defaultYearMonth;
            }
            if (m.group(6) != null) {
                YearMonth now = YearMonth.now();
                return now.minusMonths(1).format(YYYYMM);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String extractCategoryHint(String q) {
        for (int i = 0; i < CATEGORY_PATTERNS.length; i++) {
            if (CATEGORY_PATTERNS[i].matcher(q).find()) {
                return CATEGORY_MAPPING[i];
            }
        }
        return null;
    }
}
