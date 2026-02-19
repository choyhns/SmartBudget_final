package com.smartbudget.monthlyreport;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.smartbudget.rag.QuestionCard;
import com.smartbudget.rag.QuestionCardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 월별 리포트 API. 외부(프론트)는 이 API만 호출.
 * AI 호출은 Spring → Python(내부망)으로만 수행.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MonthlyReportController {

    private final MonthlyReportService monthlyReportService;
    private final QuestionCardService questionCardService;

    @GetMapping("/api/monthly-reports")
    public List<MonthlyReportDTO> getAllReports(@RequestParam(required = false) Long userId) throws Exception {
        // 임시로 userId = 1 사용 (추후 인증에서 가져오기)
        if (userId == null) userId = 1L;
        return monthlyReportService.getAllReports(userId);
    }

    @GetMapping("/api/monthly-reports/current")
    public MonthlyReportDTO getCurrentMonthReport(@RequestParam(required = false) Long userId) throws Exception {
        // 임시로 userId = 1 사용 (추후 인증에서 가져오기)
        if (userId == null) userId = 1L;
        return monthlyReportService.getCurrentMonthReport(userId);
    }

    @GetMapping("/api/monthly-reports/{yearMonth}")
    public MonthlyReportDTO getReportByYearMonth(
            @PathVariable String yearMonth,
            @RequestParam(required = false) Long userId) throws Exception {
        // 임시로 userId = 1 사용 (추후 인증에서 가져오기)
        if (userId == null) userId = 1L;
        return monthlyReportService.getReportByYearMonth(userId, yearMonth);
    }

    @PostMapping("/api/monthly-reports/current/generate")
    public MonthlyReportDTO generateCurrentMonthReport(
            @RequestBody(required = false) MonthlyReportRequestDTO request) throws Exception {
        // 임시로 userId = 1 사용 (추후 인증에서 가져오기)
        Long userId = request != null && request.getUserId() != null ? request.getUserId() : 1L;
        Long monthlyBudget = request != null ? request.getMonthlyBudget() : null;
        return monthlyReportService.generateOrUpdateCurrentMonthReport(userId, monthlyBudget);
    }

    /**
     * 질문 카드: Spring이 DB/메트릭 준비 후 Python AI Service(Ask)만 호출. AI 호출은 Spring → Python으로만.
     */
    @PostMapping("/api/monthly-reports/ask")
    public ResponseEntity<ReportAskResponseDTO> askQuestion(@RequestBody ReportAskRequestDTO request) {
        Long userId = request.getUserId() != null ? request.getUserId() : 1L;
        if (request.getYearMonth() == null || request.getYearMonth().isEmpty()) {
            log.warn("askQuestion: yearMonth is required");
            throw new IllegalArgumentException("yearMonth는 필수입니다.");
        }
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            log.warn("askQuestion: question is required");
            throw new IllegalArgumentException("question은 필수입니다.");
        }
        String yearMonth = request.getYearMonth().trim();
        String question = request.getQuestion().trim();

        try {
            ReportAskResponseDTO dto = monthlyReportService.answerQuestion(userId, yearMonth, question);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.warn("askQuestion validation: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("askQuestion failed: userId={}, yearMonth={}", userId, yearMonth, e);
            throw new RuntimeException("질문 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 월별 리포트 기반 추천 질문 카드 (상황 컨텍스트 임베딩 + 클릭 이력 리랭크).
     */
    @GetMapping("/api/monthly-reports/cards/recommended")
    public List<QuestionCard> getRecommendedCards(
            @RequestParam String yearMonth,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer limit) {
        Long uid = userId != null ? userId : 1L;
        return questionCardService.recommendCards(uid, yearMonth, limit);
    }

    /**
     * 질문 카드 클릭 로그 (다음 추천 시 리랭크에 사용).
     */
    @PostMapping("/api/monthly-reports/ask/card-click")
    public ResponseEntity<Void> logCardClick(@RequestBody ReportCardClickRequestDTO request) {
        Long uid = request.getUserId() != null ? request.getUserId() : 1L;
        if (request.getYearMonth() == null || request.getYearMonth().isEmpty() || request.getCardId() == null) {
            throw new IllegalArgumentException("yearMonth와 cardId는 필수입니다.");
        }
        questionCardService.logClick(uid, request.getYearMonth().trim(), request.getCardId());
        return ResponseEntity.ok().build();
    }
}
