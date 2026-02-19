package com.smartbudget.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smartbudget.llm.PythonAIService;

/**
 * PR2: Intent classifier 룰 기반 경로 검증. LLM 미호출 시나리오.
 */
@ExtendWith(MockitoExtension.class)
class IntentClassifierServiceTest {

    @Mock
    private PythonAIService pythonAIService;

    @InjectMocks
    private IntentClassifierService service;

    private static final String DEFAULT_YM = "202502";
    private static final String DEFAULT_BASELINE = "202501";

    @Test
    void classify_comparisonKeyword_returnsComparisonIntent() {
        ClassifierOutputDTO out = service.classify("지난달 대비 얼마나 썼어?", DEFAULT_YM, DEFAULT_BASELINE);

        assertThat(out.getIntent()).isEqualTo(Intent.COMPARISON);
        assertThat(out.getConfidence()).isGreaterThanOrEqualTo(0.8);
        assertThat(out.isNeedsDb()).isTrue();
        assertThat(out.getYearMonth()).isEqualTo(DEFAULT_YM);
        assertThat(out.getBaselineYearMonth()).isNotNull();
    }

    @Test
    void classify_causeKeyword_returnsCauseAnalysis() {
        ClassifierOutputDTO out = service.classify("식비가 왜 늘었어?", DEFAULT_YM, DEFAULT_BASELINE);

        assertThat(out.getIntent()).isEqualTo(Intent.CAUSE_ANALYSIS);
        assertThat(out.isNeedsDb()).isTrue();
    }

    @Test
    void classify_budgetKeyword_returnsBudgetStatus() {
        ClassifierOutputDTO out = service.classify("예산 초과했어?", DEFAULT_YM, DEFAULT_BASELINE);

        assertThat(out.getIntent()).isEqualTo(Intent.BUDGET_STATUS);
        assertThat(out.isNeedsDb()).isTrue();
    }

    @Test
    void classify_patternKeyword_returnsPatternDeepdive() {
        ClassifierOutputDTO out = service.classify("야식 시간대별로 봐줘", DEFAULT_YM, DEFAULT_BASELINE);

        assertThat(out.getIntent()).isEqualTo(Intent.PATTERN_DEEPDIVE);
        assertThat(out.getDimension()).isEqualTo("timeband");
    }

    @Test
    void classify_emptyQuestion_returnsDefaultWithFollowUp() {
        ClassifierOutputDTO out = service.classify("", DEFAULT_YM, DEFAULT_BASELINE);

        assertThat(out.getIntent()).isEqualTo(Intent.SUMMARY);
        assertThat(out.getYearMonth()).isEqualTo(DEFAULT_YM);
        assertThat(out.getFollowUpQuestion()).isNotBlank();
    }
}
