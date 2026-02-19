package com.smartbudget.recommendation;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RecommendationQaService recommendationQaService;

    // ========== 카드 API ==========
    
    /**
     * 모든 카드 목록 조회
     */
    @GetMapping("/cards")
    public List<CardDTO> getAllCards() {
        return recommendationService.getAllCards();
    }

    /**
     * 사용자 보유 카드 목록
     */
    @GetMapping("/cards/user")
    public List<CardDTO> getUserCards(@RequestParam(defaultValue = "1") Long userId) {
        return recommendationService.getUserCards(userId);
    }

    /**
     * 사용자 카드 추가
     */
    @PostMapping("/cards/user/{cardId}")
    public void addUserCard(
            @PathVariable Long cardId,
            @RequestParam(defaultValue = "1") Long userId) {
        recommendationService.addUserCard(userId, cardId);
    }

    /**
     * 사용자 카드 삭제
     */
    @DeleteMapping("/cards/user/{cardId}")
    public void removeUserCard(
            @PathVariable Long cardId,
            @RequestParam(defaultValue = "1") Long userId) {
        recommendationService.removeUserCard(userId, cardId);
    }

    /**
     * 상위 지출 카테고리·카드 혜택 매칭 추천 카드 상위 3장
     */
    @GetMapping("/cards/recommended")
    public List<CardDTO> getRecommendedCards(@RequestParam(defaultValue = "1") Long userId) {
        return recommendationService.getRecommendedCards(userId);
    }

    /**
     * 신한카드 JSON(credit + check) 기준으로 DB 카드의 image_url만 일괄 업데이트.
     * 기존 카드명/혜택 등은 변경하지 않고 이미지 URL만 갱신.
     */
    @PostMapping("/cards/sync-images")
    public java.util.Map<String, Object> syncCardImages() {
        int updated = recommendationService.syncCardImagesFromJson();
        return java.util.Map.of("updated", updated, "message", "image_url " + updated + "건 업데이트 완료");
    }

    /**
     * 신한카드 JSON(체크+신용)의 link를 DB cards 테이블에 일괄 반영.
     * 카드명·회사 일치 행만 link 컬럼 갱신.
     */
    @PostMapping("/cards/sync-links")
    public java.util.Map<String, Object> syncCardLinks() {
        int updated = recommendationService.syncCardLinksFromJson();
        return java.util.Map.of("updated", updated, "message", "link " + updated + "건 업데이트 완료");
    }

    /**
     * 카드별 "이 카드가 적합한 이유" LLM 생성 (추천 페이지)
     */
    @GetMapping("/cards/{cardId}/suitable-reason")
    public java.util.Map<String, String> getCardSuitableReason(
            @PathVariable Long cardId,
            @RequestParam(defaultValue = "1") Long userId) {
        String reason = recommendationService.getCardSuitableReason(cardId, userId);
        return java.util.Map.of("reason", reason != null ? reason : "");
    }

    /**
     * 여러 카드에 대한 적합 이유를 한 번에 조회 (배치)
     */
    @PostMapping("/cards/suitable-reasons-batch")
    public java.util.Map<String, Object> getCardSuitableReasonsBatch(
            @RequestBody java.util.Map<String, Object> request,
            @RequestParam(defaultValue = "1") Long userId) {
        @SuppressWarnings("unchecked")
        List<Number> cardIdsRaw = (List<Number>) request.get("cardIds");
        List<Long> cardIds = cardIdsRaw != null 
            ? cardIdsRaw.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList())
            : java.util.Collections.emptyList();
        return recommendationService.getCardSuitableReasonsBatch(cardIds, userId);
    }

    // ========== 금융상품 API ==========
    
    /**
     * 모든 금융상품 목록 조회
     */
    @GetMapping("/products")
    public List<ProductDTO> getAllProducts() {
        return recommendationService.getAllProducts();
    }

    /**
     * 타입별 금융상품 조회
     */
    @GetMapping("/products/type/{type}")
    public List<ProductDTO> getProductsByType(@PathVariable String type) {
        return recommendationService.getProductsByType(type);
    }

    // ========== 추천 API ==========
    
    /**
     * 추천 내역 조회
     */
    @GetMapping
    public List<RecommendationDTO> getRecommendations(@RequestParam(defaultValue = "1") Long userId) {
        return recommendationService.getRecommendations(userId);
    }

    /**
     * 월별 추천 내역 조회
     */
    @GetMapping("/month/{yearMonth}")
    public List<RecommendationDTO> getRecommendationsByYearMonth(
            @PathVariable String yearMonth,
            @RequestParam(defaultValue = "1") Long userId) {
        return recommendationService.getRecommendationsByYearMonth(userId, yearMonth);
    }

    /**
     * 소비 패턴 기반 추천 생성
     */
    @PostMapping("/generate")
    public RecommendationResultDTO generateRecommendations(
            @RequestParam(defaultValue = "1") Long userId) throws Exception {
        return recommendationService.generateRecommendations(userId);
    }

    // ========== Q&A (선택지 기반 RAG) ==========

    /**
     * 선택지(질문) 목록
     */
    @GetMapping("/qa/options")
    public List<QaOptionDTO> getQaOptions() {
        return recommendationQaService.getOptions();
    }

    /**
     * 선택지에 대한 답변
     */
    @PostMapping("/qa/answer")
    public QaAnswerResponseDTO answerQa(@RequestBody QaAnswerRequestDTO request) throws Exception {
        return recommendationQaService.answer(request);
    }
}
