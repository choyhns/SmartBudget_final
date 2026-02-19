package com.smartbudget.analysis;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.smartbudget.llm.PythonAIService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PR2: 1차 룰 기반 quick route, 애매하면 LLM(Gemini) JSON 분류.
 * 슬롯 비면 기본값 1차 적용 + followUpQuestion 1개만.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifierService {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 룰로 확정 가능한 최소 신뢰도. 이하면 LLM 호출 */
    @Value("${app.classifier.rule-confidence-threshold:0.8}")
    private double ruleConfidenceThreshold = 0.8;

    private final PythonAIService pythonAIService;

    // ----- 룰 키워드 (PR2 명세) -----
    private static final String[] COMPARISON_KEYWORDS = {
        "비교", "대비", "vs", "지난달", "전월", "전달", "저번달", "달라", "차이"
    };
    private static final String[] CAUSE_KEYWORDS = {
        "왜", "원인", "늘었", "줄었", "급증", "많아", "나왔", "이유"
    };
    private static final String[] BUDGET_KEYWORDS = {
        "예산", "한도", "초과", "잔여", "썼어", "사용률", "넘쳤"
    };
    private static final String[] PATTERN_KEYWORDS = {
        "야식", "시간대", "주말", "요일", "어디서", "가맹점", "상호", "배달", "치킨", "카페",
        "패턴", "주차", "언제", "가게"
    };
    private static final String[] DEFINITION_KEYWORDS = {
        "뭐야", "뜻", "정의", "무슨 뜻", "이게 뭐"
    };
    private static final String[] DATA_FIX_KEYWORDS = {
        "틀렸", "수정", "정정", "누락", "중복"
    };
    private static final String[] ADVICE_KEYWORDS = {
        "어떻게", "줄이", "절약", "팁", "권장", "대안"
    };
    private static final String[] SUMMARY_KEYWORDS = {
        "요약", "총", "전체", "얼마", "썼어", "지출", "수입"
    };

    /** 연월 추출 */
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile(
        "(\\d{4})[-년]?\\s*(\\d{1,2})월?|(\\d{4})(\\d{2})|(이번\\s*달|이번달)|(지난\\s*달|지난달|전월|전달)"
    );

    /** dimension: 키워드 → timeband/dow/merchant/keyword */
    private static final String[] TIMEBAND_HINTS = { "야식", "시간대", "밤", "새벽", "저녁", "점심", "아침" };
    private static final String[] DOW_HINTS = { "요일", "주말", "평일", "월요일", "금요일" };
    private static final String[] MERCHANT_HINTS = { "가맹점", "상호", "가게", "어디서" };
    private static final String[] KEYWORD_HINTS = { "배달", "치킨", "카페", "키워드" };

    public ClassifierOutputDTO classify(String question, String defaultYearMonth, String defaultBaselineYearMonth) {
        if (question == null || question.isBlank()) {
            return defaultOutput(defaultYearMonth, defaultBaselineYearMonth);
        }
        String q = question.trim();

        // 1) 룰 기반 quick route
        ClassifierOutputDTO ruleResult = classifyByRule(q, defaultYearMonth, defaultBaselineYearMonth);
        if (ruleResult.getConfidence() >= ruleConfidenceThreshold) {
            applyDefaultsAndFollowUp(ruleResult, defaultYearMonth, defaultBaselineYearMonth);
            return ruleResult;
        }

        // 2) 애매하면 LLM 분류
        ClassifierOutputDTO llmResult = pythonAIService.classifyIntent(q);
        if (llmResult != null) {
            applyDefaultsAndFollowUp(llmResult, defaultYearMonth, defaultBaselineYearMonth);
            return llmResult;
        }

        // LLM 실패 시 룰 결과라도 반환
        applyDefaultsAndFollowUp(ruleResult, defaultYearMonth, defaultBaselineYearMonth);
        return ruleResult;
    }

    private ClassifierOutputDTO classifyByRule(String q, String defaultYearMonth, String defaultBaselineYearMonth) {
        String lower = q.toLowerCase();
        int comp = countMatches(lower, COMPARISON_KEYWORDS);
        int cause = countMatches(lower, CAUSE_KEYWORDS);
        int budget = countMatches(lower, BUDGET_KEYWORDS);
        int pattern = countMatches(lower, PATTERN_KEYWORDS);
        int definition = countMatches(lower, DEFINITION_KEYWORDS);
        int dataFix = countMatches(lower, DATA_FIX_KEYWORDS);
        int advice = countMatches(lower, ADVICE_KEYWORDS);
        int summary = countMatches(lower, SUMMARY_KEYWORDS);

        Intent intent = Intent.SUMMARY;
        double confidence = 0.5;

        if (dataFix > 0 && dataFix >= comp && dataFix >= cause && dataFix >= budget && dataFix >= pattern) {
            intent = Intent.DATA_FIX;
            confidence = 0.85;
        } else if (definition > 0 && definition >= advice) {
            intent = Intent.DEFINITION_HELP;
            confidence = 0.85;
        } else if (comp > 0 && comp >= cause && comp >= budget && comp >= pattern) {
            intent = Intent.COMPARISON;
            confidence = 0.9;
        } else if (cause > 0 && cause >= budget && cause >= pattern) {
            intent = Intent.CAUSE_ANALYSIS;
            confidence = 0.9;
        } else if (budget > 0 && budget >= pattern) {
            intent = Intent.BUDGET_STATUS;
            confidence = 0.9;
        } else if (pattern > 0) {
            intent = Intent.PATTERN_DEEPDIVE;
            confidence = 0.85;
        } else if (advice > 0) {
            intent = Intent.ADVICE_ACTION;
            confidence = 0.8;
        } else if (summary > 0) {
            intent = Intent.SUMMARY;
            confidence = 0.8;
        }

        String yearMonth = extractYearMonth(q, defaultYearMonth);
        String baseline = extractBaselineYearMonth(q, defaultBaselineYearMonth);
        String dimension = extractDimension(q);
        List<String> keywords = extractKeywordHints(q);

        return ClassifierOutputDTO.builder()
            .intent(intent)
            .confidence(confidence)
            .yearMonth(yearMonth)
            .baselineYearMonth(baseline)
            .categoryId(null) // 룰에서는 id 추출 불가
            .dimension(dimension)
            .keywords(keywords.isEmpty() ? null : keywords)
            .needsDb(intent.needsDb())
            .needsRag(intent.needsRag())
            .followUpQuestion(null)
            .build();
    }

    private void applyDefaultsAndFollowUp(ClassifierOutputDTO out,
                                          String defaultYearMonth, String defaultBaselineYearMonth) {
        boolean hadNoYearMonth = (out.getYearMonth() == null || out.getYearMonth().isBlank());
        boolean hadNoBaseline = (out.getBaselineYearMonth() == null || out.getBaselineYearMonth().isBlank());

        if (hadNoYearMonth) {
            out.setYearMonth(defaultYearMonth);
        }
        if (hadNoBaseline) {
            out.setBaselineYearMonth(defaultBaselineYearMonth);
        }
        if (out.getIntent() != null) {
            out.setNeedsDb(out.getIntent().needsDb());
            out.setNeedsRag(out.getIntent().needsRag());
        }

        // 슬롯 부족 시 되묻기 1개만
        List<String> followUps = new ArrayList<>();
        if (hadNoYearMonth) {
            followUps.add("어느 달 기준으로 볼까요? (예: 이번 달, 202501)");
        }
        if (out.isNeedsDb() && out.getCategoryId() == null && out.getKeywords().isEmpty()
            && (out.getIntent() == Intent.CAUSE_ANALYSIS || out.getIntent() == Intent.PATTERN_DEEPDIVE)) {
            followUps.add("특정 카테고리(식비, 카페 등)나 키워드(배달, 치킨 등)가 있으면 알려주세요.");
        }
        if (!followUps.isEmpty()) {
            out.setFollowUpQuestion(followUps.get(0));
        }
    }

    private static int countMatches(String text, String[] keywords) {
        int n = 0;
        for (String k : keywords) {
            if (text.contains(k)) n++;
        }
        return n;
    }

    /** 연월 추출. "저번달/전달"만 있으면 null 반환 → 기본값(선택월/이번달) 사용해 이번달 vs 전달 비교가 되도록 함. */
    private String extractYearMonth(String q, String defaultYearMonth) {
        Matcher m = YEAR_MONTH_PATTERN.matcher(q);
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
                return null;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String extractBaselineYearMonth(String q, String defaultBaselineYearMonth) {
        String lower = q.toLowerCase();
        if (lower.contains("전월") || lower.contains("지난달") || lower.contains("전달") || lower.contains("저번달")) {
            return defaultBaselineYearMonth != null ? defaultBaselineYearMonth : YearMonth.now().minusMonths(1).format(YYYYMM);
        }
        return null;
    }

    private String extractDimension(String q) {
        String lower = q.toLowerCase();
        for (String h : TIMEBAND_HINTS) { if (lower.contains(h)) return "timeband"; }
        for (String h : DOW_HINTS) { if (lower.contains(h)) return "dow"; }
        for (String h : MERCHANT_HINTS) { if (lower.contains(h)) return "merchant"; }
        for (String h : KEYWORD_HINTS) { if (lower.contains(h)) return "keyword"; }
        return null;
    }

    private List<String> extractKeywordHints(String q) {
        List<String> out = new ArrayList<>();
        String[] hints = { "배달", "치킨", "카페", "야식", "커피", "배민", "쿠팡" };
        for (String h : hints) {
            if (q.toLowerCase().contains(h)) out.add(h);
        }
        return out;
    }

    private ClassifierOutputDTO defaultOutput(String defaultYearMonth, String defaultBaselineYearMonth) {
        ClassifierOutputDTO out = ClassifierOutputDTO.builder()
            .intent(Intent.SUMMARY)
            .confidence(0.5)
            .yearMonth(defaultYearMonth)
            .baselineYearMonth(defaultBaselineYearMonth)
            .needsDb(true)
            .needsRag(false)
            .followUpQuestion("어느 달 기준으로 볼까요? (예: 이번 달, 202501)")
            .build();
        return out;
    }
}
