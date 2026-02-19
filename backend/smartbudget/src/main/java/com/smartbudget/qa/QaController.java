package com.smartbudget.qa;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자유/추가 질문 QA: Intent 분류 → DB 분석 → (선택) RAG 보조 → 근거 기반 답변.
 * 카드 클릭은 기존 POST /api/monthly-reports/ask (RAG) 유지.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;

    @PostMapping("/api/qa/ask")
    public ResponseEntity<QaAskResponseDTO> ask(@RequestBody QaAskRequestDTO request) {
        Long userId = request.getUserId() != null ? request.getUserId() : 1L;
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(QaAskResponseDTO.builder().answerText("question은 필수입니다.").build());
        }

        try {
            QaAskResponseDTO dto = qaService.ask(userId, request.getYearMonth(), request.getQuestion());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("QA ask failed: userId={}", userId, e);
            throw new RuntimeException("질문 처리 중 오류가 발생했습니다.", e);
        }
    }
}
