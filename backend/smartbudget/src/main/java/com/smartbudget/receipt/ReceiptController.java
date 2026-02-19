package com.smartbudget.receipt;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.smartbudget.auth.CustomUserPrincipal;
import com.smartbudget.transaction.TransactionDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    /**
     * OCR만 수행 (S3/DB 저장 없음). 화면 미리보기 + 폼 자동 입력 후, 거래 저장 시 S3에 저장.
     */
    @PostMapping("/ocr-only")
    public ReceiptProcessResult ocrOnly(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserPrincipal principal) throws Exception {
        return receiptService.processOcrOnly(file);
    }

    /**
     * 영수증 이미지 업로드 및 OCR 처리 (S3+DB 저장 포함, 기존 동작)
     */
    @PostMapping("/upload")
    public ReceiptProcessResult uploadReceipt(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserPrincipal principal) throws Exception {
        // JWT에서 사용자 ID 가져오기 (없으면 요청 파라미터 사용, 둘 다 없으면 1)
        Long actualUserId = principal != null ? principal.getUserId() : (userId != null ? userId : 1L);
        return receiptService.processReceiptImage(actualUserId, file);
    }

    /**
     * OCR 결과로 거래 내역 생성
     */
    @PostMapping("/{fileId}/create-transaction")
    public TransactionDTO createTransactionFromReceipt(
            @PathVariable Long fileId,
            @RequestParam(value = "userId", required = false) Long userId,
            @AuthenticationPrincipal CustomUserPrincipal principal) throws Exception {
        Long actualUserId = principal != null ? principal.getUserId() : (userId != null ? userId : 1L);
        return receiptService.createTransactionFromReceipt(actualUserId, fileId);
    }

    /**
     * 텍스트로 거래 내역 입력 (자동 카테고리 분류)
     */
    @PostMapping("/text-input")
    public TransactionDTO createTransactionFromText(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestBody TransactionInputDTO input,
            @AuthenticationPrincipal CustomUserPrincipal principal) throws Exception {
        Long actualUserId = principal != null ? principal.getUserId() : (userId != null ? userId : 1L);
        return receiptService.createTransactionFromText(actualUserId, input);
    }

    /**
     * 영수증 목록 조회
     */
    @GetMapping
    public List<ReceiptFileDTO> getReceipts(
            @RequestParam(value = "userId", required = false) Long userId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long actualUserId = principal != null ? principal.getUserId() : (userId != null ? userId : 1L);
        return receiptService.getReceipts(actualUserId);
    }

    /**
     * 영수증 상세 조회
     */
    @GetMapping("/{fileId}")
    public ReceiptFileDTO getReceipt(@PathVariable Long fileId) {
        return receiptService.getReceiptById(fileId);
    }

    /**
     * 영수증 이미지 S3 저장 + 거래 생성 (거래 저장 버튼 시 호출).
     * - user_id는 JWT 토큰에서만 추출 (보안).
     * - S3 경로는 프론트에서 입력한 날짜 기준 user_id/연도/월/영수증이미지 로 저장.
     * - FormData: "data" (JSON), "file" (이미지).
     */
    @PostMapping(value = "/save-and-create-transaction", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TransactionDTO saveReceiptAndCreateTransaction(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestPart("data") ReceiptSaveRequestDto data,
            @RequestPart(value = "file", required = false) MultipartFile file) throws Exception {
        if (principal == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        Long userId = principal.getUserId();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("영수증 이미지 파일을 첨부해 주세요.");
        }

        String amtStr = data.getAmount() != null ? data.getAmount().trim() : "";
        if (amtStr.isEmpty()) {
            throw new IllegalArgumentException("금액을 입력해 주세요.");
        }
        BigDecimal amt;
        try {
            amt = new BigDecimal(amtStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("금액을 올바른 숫자로 입력해 주세요.");
        }
        if (data.getDate() == null) {
            throw new IllegalArgumentException("거래 날짜를 입력해 주세요.");
        }

        String merchantVal = (data.getMerchant() != null && !data.getMerchant().isBlank())
            ? data.getMerchant().trim() : "영수증";
        TransactionDTO dto = new TransactionDTO();
        dto.setTxDatetime(data.getDate().atStartOfDay());
        dto.setAmount("EXPENSE".equalsIgnoreCase(data.getType()) ? amt.negate() : amt.abs());
        dto.setMerchant(merchantVal);
        dto.setMemo(data.getMemo());
        dto.setSource("MANUAL");
        dto.setCategoryId(data.getCategoryId());
        dto.setMethodId(data.getMethodId());

        return receiptService.saveReceiptAndCreateTransaction(userId, file, dto);
    }

    /**
     * 영수증 이미지 조회용 Presigned URL (S3 사용 시)
     */
    @GetMapping("/{fileId}/presigned-url")
    public java.util.Map<String, String> getPresignedUrl(
            @PathVariable Long fileId,
            @RequestParam(value = "userId", required = false) Long userId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long actualUserId = principal != null ? principal.getUserId() : (userId != null ? userId : null);
        String url = receiptService.getPresignedUrlForReceipt(fileId, actualUserId);
        return java.util.Map.of("url", url != null ? url : "");
    }
}
