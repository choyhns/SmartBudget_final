package com.smartbudget.receipt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbudget.category.CategoryDTO;
import com.smartbudget.category.CategoryMapper;
import com.smartbudget.llm.CategoryClassificationResult;
import com.smartbudget.llm.GeminiService;
import com.smartbudget.llm.ReceiptOcrResult;
import com.smartbudget.ml.CategoryPrediction;
import com.smartbudget.ml.OcrResult;
import com.smartbudget.ml.PythonMLService;
import com.smartbudget.ocr.OcrReceiptsService;
import com.smartbudget.transaction.TransactionDTO;
import com.smartbudget.transaction.TransactionMapper;
import com.smartbudget.transaction.TransactionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReceiptService {
    
    // OCR 엔진 선택: "python" 또는 "gemini"
    @Value("${ocr.engine:python}")
    private String ocrEngine;
    
    // 카테고리 분류 엔진 선택: "python" 또는 "gemini"
    @Value("${ml.classifier.engine:python}")
    private String classifierEngine;
    
    @Autowired
    private ReceiptMapper receiptMapper;
    
    @Autowired
    private TransactionMapper transactionMapper;
    
    @Autowired
    private TransactionService transactionService;
    
    @Autowired
    private CategoryMapper categoryMapper;
    
    @Autowired
    private PythonMLService pythonMLService;
    
    @Autowired
    private GeminiService geminiService;
    
    @Autowired(required = false)
    private OcrReceiptsService ocrReceiptsService;
    
    @Autowired(required = false)
    private S3Service s3Service;
    
    @Value("${aws.s3.enabled:false}")
    private boolean awsS3Enabled;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * OCR만 수행 (S3/DB 저장 없음). 화면 미리보기 + 폼 자동 입력용.
     */
    public ReceiptProcessResult processOcrOnly(MultipartFile file) throws Exception {
        byte[] imageData = file.getBytes();
        String mimeType = file.getContentType();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt.jpg";
        OcrRunResult runResult = runOcr(imageData, mimeType, filename);
        if (runResult.ocrResult == null || !runResult.ocrResult.isSuccess()) {
            String errorMsg = runResult.ocrResult != null ? runResult.ocrResult.getError() : "OCR 처리 실패";
            return new ReceiptProcessResult(null, null, null, errorMsg);
        }
        ReceiptOcrResult legacyOcr = convertOcrResultToLegacy(runResult.ocrResult);
        CategoryClassificationResult legacyClass = convertPredictionToLegacy(runResult.classification);
        return new ReceiptProcessResult(null, legacyOcr, legacyClass, "처리 완료");
    }
    
    /**
     * 영수증 이미지 업로드 및 OCR 처리 (S3+DB 저장 포함, 기존 /upload용)
     */
    @Transactional
    public ReceiptProcessResult processReceiptImage(Long userId, MultipartFile file) throws Exception {
        // 1. 파일 저장 (S3 또는 로컬 경로)
        String urlPath = saveFile(userId, file);
        
        // 2. DB에 영수증 레코드 생성
        ReceiptFileDTO receipt = new ReceiptFileDTO();
        receipt.setUserId(userId);
        receipt.setUrlPath(urlPath);
        receiptMapper.insertReceipt(receipt);
        
        byte[] imageData = file.getBytes();
        String mimeType = file.getContentType();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt.jpg";
        OcrRunResult runResult = runOcr(imageData, mimeType, filename);
        
        if (runResult.ocrResult == null || !runResult.ocrResult.isSuccess()) {
            receipt.setStatus("FAILED");
            receiptMapper.updateReceiptStatus(receipt.getFileId(), "FAILED");
            String errorMsg = runResult.ocrResult != null ? runResult.ocrResult.getError() : "OCR 처리 실패";
            return new ReceiptProcessResult(receipt, null, null, errorMsg);
        }
        
        String ocrRawJson = objectMapper.writeValueAsString(runResult.ocrResult);
        receipt.setOcrRawJson(ocrRawJson);
        receipt.setOcrParsedJson(runResult.ocrParsedJson != null ? runResult.ocrParsedJson : ocrRawJson);
        receipt.setStatus("CLASSIFIED");
        receiptMapper.updateReceipt(receipt);
        
        ReceiptOcrResult legacyOcrResult = convertOcrResultToLegacy(runResult.ocrResult);
        CategoryClassificationResult legacyClassification = convertPredictionToLegacy(runResult.classification);
        return new ReceiptProcessResult(receipt, legacyOcrResult, legacyClassification, "처리 완료");
    }
    
    /**
     * OCR 실행 (영수증은 8000/receipts PaddleOCR 전용)
     */
    private OcrRunResult runOcr(byte[] imageData, String mimeType, String filename) {
        OcrResult ocrResult = null;
        CategoryPrediction classification = null;
        String ocrParsedJson = null;

        if (ocrReceiptsService != null) {
            log.info("[OCR] OCR_receipts(8000/receipts) PaddleOCR /process 호출");
            OcrReceiptsService.ProcessResult processResult = ocrReceiptsService.processReceipt(
                imageData, mimeType, filename
            );
            ocrResult = processResult.getOcrResult();
            classification = processResult.getClassification();
            ocrParsedJson = processResult.getParsedJson();
            if (ocrResult != null && ocrResult.isSuccess()) {
                List<CategoryDTO> dbCategories = categoryMapper.selectAllCategories();
                String mappedCategory = mapOcrReceiptsCategoryToDbCategory(
                    classification.getCategory(), dbCategories);
                classification.setCategory(mappedCategory);
            } else {
                log.warn("[OCR] 8000/receipts 처리 실패: {}", ocrResult != null ? ocrResult.getError() : "null");
            }
            return new OcrRunResult(ocrResult, classification, ocrParsedJson);
        }

        // ocrReceiptsService 미설정 시에만 Gemini 등 사용 (일반적으로는 8000/receipts 사용)
        if ("python".equalsIgnoreCase(ocrEngine)) {
            log.info("[OCR] Python OCR 엔진 사용 (ocrReceiptsService 미설정)");
            ocrResult = pythonMLService.extractTextFromReceipt(imageData, mimeType);
            List<CategoryDTO> categories = categoryMapper.selectAllCategories();
            List<String> categoryNames = categories.stream().map(CategoryDTO::getName).toList();
            classification = pythonMLService.classifyTransaction(
                ocrResult.getStoreName(), ocrResult.getRawText(), categoryNames);
        } else {
            log.info("[OCR] Gemini OCR 엔진 사용 (ocrReceiptsService 미설정)");
            ocrResult = convertGeminiOcrToOcrResult(
                geminiService.extractReceiptInfo(imageData, mimeType));
            List<CategoryDTO> categories = categoryMapper.selectAllCategories();
            List<String> categoryNames = categories.stream().map(CategoryDTO::getName).toList();
            CategoryClassificationResult geminiResult = geminiService.classifyTransaction(
                ocrResult.getStoreName(), ocrResult.getRawText(), categoryNames);
            classification = convertGeminiClassificationToPrediction(geminiResult);
        }
        return new OcrRunResult(ocrResult, classification, ocrParsedJson);
    }
    
    private static class OcrRunResult {
        final OcrResult ocrResult;
        final CategoryPrediction classification;
        final String ocrParsedJson;
        OcrRunResult(OcrResult ocrResult, CategoryPrediction classification, String ocrParsedJson) {
            this.ocrResult = ocrResult;
            this.classification = classification;
            this.ocrParsedJson = ocrParsedJson;
        }
    }
    
    /**
     * 영수증 이미지 S3 저장 + receipt DB 저장 + OCR 실행 및 저장 + 거래 생성 (거래 저장 버튼 시 호출).
     * S3 경로는 사용자가 입력한 거래 날짜 기준 user_id/연도/월/파일명 으로 저장한다.
     */
    @Transactional
    public TransactionDTO saveReceiptAndCreateTransaction(Long userId, MultipartFile file, TransactionDTO transaction) throws Exception {
        LocalDate transactionDate = transaction.getTxDatetime() != null
            ? transaction.getTxDatetime().toLocalDate()
            : LocalDate.now();
        String urlPath = saveFile(userId, transactionDate, file);
        ReceiptFileDTO receipt = new ReceiptFileDTO();
        receipt.setUserId(userId);
        receipt.setUrlPath(urlPath);
        receipt.setStatus("UPLOADED");
        receiptMapper.insertReceipt(receipt);

        // OCR 실행 후 ocr_raw_json, ocr_parsed_json 저장 (실패해도 거래는 생성)
        byte[] imageData = file.getBytes();
        String mimeType = file.getContentType();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt.jpg";
        OcrRunResult runResult = runOcr(imageData, mimeType, filename);
        if (runResult.ocrResult != null && runResult.ocrResult.isSuccess()) {
            String ocrRawJson = objectMapper.writeValueAsString(runResult.ocrResult);
            receipt.setOcrRawJson(ocrRawJson);
            receipt.setOcrParsedJson(runResult.ocrParsedJson != null ? runResult.ocrParsedJson : ocrRawJson);
            receipt.setStatus("CONFIRMED");
            receiptMapper.updateReceipt(receipt);
        } else {
            log.warn("saveReceiptAndCreateTransaction: OCR 실패 또는 결과 없음, receipt fileId={} (거래는 생성됨)", receipt.getFileId());
        }

        transaction.setUserId(userId);
        transaction.setReceiptFileId(receipt.getFileId());
        return transactionService.createTransaction(transaction);
    }
    
    /**
     * OCR 결과로 거래 내역 자동 생성
     */
    @Transactional
    public TransactionDTO createTransactionFromReceipt(Long userId, Long fileId) throws Exception {
        ReceiptFileDTO receipt = receiptMapper.selectReceiptById(fileId);
        if (receipt == null) {
            throw new RuntimeException("Receipt not found: " + fileId);
        }
        
        // OCR 결과 파싱
        OcrResult ocrResult = objectMapper.readValue(receipt.getOcrRawJson(), OcrResult.class);
        
        // 카테고리 찾기
        List<CategoryDTO> categories = categoryMapper.selectAllCategories();
        List<String> categoryNames = categories.stream().map(CategoryDTO::getName).toList();
        
        CategoryPrediction classification;
        if ("python".equalsIgnoreCase(classifierEngine)) {
            classification = pythonMLService.classifyTransaction(
                ocrResult.getStoreName(),
                ocrResult.getRawText(),
                categoryNames
            );
        } else {
            CategoryClassificationResult geminiResult = geminiService.classifyTransaction(
                ocrResult.getStoreName(),
                ocrResult.getRawText(),
                categoryNames
            );
            classification = convertGeminiClassificationToPrediction(geminiResult);
        }
        
        Long categoryId = categories.stream()
            .filter(c -> c.getName().equals(classification.getCategory()))
            .map(CategoryDTO::getCategoryId)
            .findFirst()
            .orElse(null);
        
        // 거래 내역 생성
        TransactionDTO tx = new TransactionDTO();
        tx.setUserId(userId);
        tx.setMerchant(ocrResult.getStoreName());
        tx.setAmount(ocrResult.getTotalAmount() != null 
            ? BigDecimal.valueOf(-ocrResult.getTotalAmount()) // 지출은 음수
            : null);
        tx.setMemo("영수증에서 자동 생성 (신뢰도: " + 
            String.format("%.1f%%", classification.getConfidence() * 100) + ")");
        tx.setSource("RECEIPT_OCR");
        tx.setCategoryId(categoryId);
        
        // 날짜 파싱
        if (ocrResult.getDate() != null) {
            try {
                String datetime = ocrResult.getDate();
                if (ocrResult.getTime() != null) {
                    datetime += "T" + ocrResult.getTime();
                } else {
                    datetime += "T00:00:00";
                }
                tx.setTxDatetime(LocalDateTime.parse(datetime, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } catch (Exception e) {
                tx.setTxDatetime(LocalDateTime.now());
            }
        } else {
            tx.setTxDatetime(LocalDateTime.now());
        }
        
        transactionMapper.insertTransaction(tx);
        
        // 영수증과 거래 연결
        receiptMapper.linkReceiptToTransaction(fileId, tx.getTxId());
        
        // 분류 예측 결과 저장 (category_predictions 테이블)
        saveCategoryPrediction(tx.getTxId(), classification, categoryId);
        
        return tx;
    }
    
    /**
     * 텍스트로 거래 내역 입력 및 카테고리 자동 분류
     */
    @Transactional
    public TransactionDTO createTransactionFromText(Long userId, TransactionInputDTO input) throws Exception {
        // 카테고리 자동 분류
        List<CategoryDTO> categories = categoryMapper.selectAllCategories();
        List<String> categoryNames = categories.stream().map(CategoryDTO::getName).toList();
        
        CategoryPrediction classification;
        if ("python".equalsIgnoreCase(classifierEngine)) {
            log.info("Python ML 분류기로 카테고리 분류: merchant={}", input.getMerchant());
            classification = pythonMLService.classifyTransaction(
                input.getMerchant(),
                input.getMemo(),
                categoryNames
            );
        } else {
            CategoryClassificationResult geminiResult = geminiService.classifyTransaction(
                input.getMerchant(),
                input.getMemo(),
                categoryNames
            );
            classification = convertGeminiClassificationToPrediction(geminiResult);
        }
        
        log.info("분류 결과: category={}, confidence={}", 
            classification.getCategory(), classification.getConfidence());
        
        Long categoryId = input.getCategoryId();
        if (categoryId == null) {
            categoryId = categories.stream()
                .filter(c -> c.getName().equals(classification.getCategory()))
                .map(CategoryDTO::getCategoryId)
                .findFirst()
                .orElse(null);
        }
        
        // 거래 내역 생성
        TransactionDTO tx = new TransactionDTO();
        tx.setUserId(userId);
        tx.setTxDatetime(input.getTxDatetime() != null ? input.getTxDatetime() : LocalDateTime.now());
        tx.setAmount(input.getAmount());
        tx.setMerchant(input.getMerchant());
        tx.setMemo(input.getMemo());
        tx.setSource(input.getSource() != null ? input.getSource() : "MANUAL");
        tx.setMethodId(input.getMethodId());
        tx.setCategoryId(categoryId);
        
        transactionMapper.insertTransaction(tx);
        
        // 분류 예측 결과 저장
        saveCategoryPrediction(tx.getTxId(), classification, categoryId);
        
        return transactionMapper.selectTransactionById(tx.getTxId());
    }
    
    /**
     * 영수증 목록 조회
     */
    public List<ReceiptFileDTO> getReceipts(Long userId) {
        return receiptMapper.selectReceiptsByUser(userId);
    }
    
    /**
     * 영수증 상세 조회
     */
    public ReceiptFileDTO getReceiptById(Long fileId) {
        return receiptMapper.selectReceiptById(fileId);
    }
    
    /**
     * ML 서버 상태 확인
     */
    public boolean isMLServerHealthy() {
        return pythonMLService.isServerHealthy();
    }
    
    /**
     * 파일 저장: S3 사용 시 업로드 후 객체 키 반환, 미사용 시 로컬 경로 반환.
     */
    private String saveFile(Long userId, MultipartFile file) throws Exception {
        String filename = System.currentTimeMillis() + "_" + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt.jpg");
        if (awsS3Enabled && s3Service != null) {
            String objectKey = "receipts/" + userId + "/" + filename;
            s3Service.upload(objectKey, file.getBytes(), file.getContentType());
            return objectKey;
        }
        return "/receipts/" + userId + "/" + filename;
    }

    /**
     * 거래 저장용 파일 저장: S3 사용 시 user_id/연도/월/파일명 구조로 업로드.
     * 프론트에서 사용자가 확정한 날짜(transactionDate)를 기준으로 폴더를 만든다.
     */
    private String saveFile(Long userId, LocalDate transactionDate, MultipartFile file) throws Exception {
        if (awsS3Enabled && s3Service != null) {
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt.jpg";
            return s3Service.uploadReceipt(
                String.valueOf(userId),
                transactionDate,
                file.getBytes(),
                originalFilename,
                file.getContentType()
            );
        }
        String filename = System.currentTimeMillis() + "_" + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt.jpg");
        return "/receipts/" + userId + "/" + transactionDate.getYear() + "/" + transactionDate.getMonthValue() + "/" + filename;
    }
    
    /**
     * 영수증 이미지 조회용 Presigned URL 반환 (S3 사용 시에만 유효).
     */
    public String getPresignedUrlForReceipt(Long fileId, Long userId) {
        ReceiptFileDTO receipt = receiptMapper.selectReceiptById(fileId);
        if (receipt == null) return null;
        if (userId != null && !userId.equals(receipt.getUserId())) return null;
        String urlPath = receipt.getUrlPath();
        if (urlPath == null || urlPath.isBlank()) return null;
        if (!awsS3Enabled || s3Service == null) return null;
        // url_path: "receipts/...", "userId/년/월/파일" 등 S3 객체 키로 사용
        String s3Key = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;
        return s3Service.getPresignedUrl(s3Key, 60);
    }
    
    /**
     * 분류 예측 결과 저장
     */
    private void saveCategoryPrediction(Long txId, CategoryPrediction prediction, Long categoryId) {
        try {
            // category_predictions 테이블에 저장
            // 현재는 로그만 남김 (추후 구현)
            log.info("분류 예측 저장: txId={}, category={}, confidence={}, reason={}", 
                txId, prediction.getCategory(), prediction.getConfidence(), prediction.getReason());
        } catch (Exception e) {
            log.warn("분류 예측 저장 실패: {}", e.getMessage());
        }
    }
    
    // === 변환 헬퍼 메서드 ===
    
    private OcrResult convertGeminiOcrToOcrResult(ReceiptOcrResult gemini) {
        if (gemini == null) {
            OcrResult result = new OcrResult();
            result.setSuccess(false);
            result.setError("Gemini OCR 실패");
            return result;
        }
        
        OcrResult result = new OcrResult();
        result.setSuccess(true);
        result.setStoreName(gemini.getStoreName());
        result.setStoreAddress(gemini.getStoreAddress());
        result.setDate(gemini.getDate());
        result.setTime(gemini.getTime());
        result.setTotalAmount(gemini.getTotalAmount());
        result.setPaymentMethod(gemini.getPaymentMethod());
        result.setCardInfo(gemini.getCardInfo());
        result.setRawText(gemini.getRawText());
        result.setConfidence(0.8); // Gemini는 기본 신뢰도
        return result;
    }
    
    private CategoryPrediction convertGeminiClassificationToPrediction(CategoryClassificationResult gemini) {
        CategoryPrediction prediction = new CategoryPrediction();
        if (gemini != null) {
            prediction.setCategory(gemini.getCategory());
            prediction.setConfidence(gemini.getConfidence());
            prediction.setReason(gemini.getReason());
        } else {
            prediction.setCategory("기타");
            prediction.setConfidence(0.0);
            prediction.setReason("분류 실패");
        }
        return prediction;
    }
    
    private ReceiptOcrResult convertOcrResultToLegacy(OcrResult ocr) {
        ReceiptOcrResult legacy = new ReceiptOcrResult();
        legacy.setStoreName(ocr.getStoreName());
        legacy.setStoreAddress(ocr.getStoreAddress());
        legacy.setDate(ocr.getDate());
        legacy.setTime(ocr.getTime());
        legacy.setTotalAmount(ocr.getTotalAmount());
        legacy.setPaymentMethod(ocr.getPaymentMethod());
        legacy.setCardInfo(ocr.getCardInfo());
        legacy.setRawText(ocr.getRawText());
        return legacy;
    }
    
    private CategoryClassificationResult convertPredictionToLegacy(CategoryPrediction prediction) {
        return new CategoryClassificationResult(
            prediction.getCategory(),
            prediction.getConfidence(),
            prediction.getReason()
        );
    }
    
    /**
     * OCR_receipts 서버의 카테고리를 DB 카테고리로 매핑
     * OCR_receipts: "주거", "보험", "통신비", "식비", "생활용품", "꾸밈비", "건강", "자기계발", "자동차", "여행", "문화", "경조사", "기타"
     * DB: "식비", "교통", "쇼핑", "주거/통신", "금융/보험", "취미/여가", "의료/건강", "기타"
     */
    private String mapOcrReceiptsCategoryToDbCategory(String ocrCategory, List<CategoryDTO> dbCategories) {
        if (ocrCategory == null) {
            return "기타";
        }
        
        // 먼저 정확히 일치하는 카테고리가 있는지 확인
        for (CategoryDTO dbCat : dbCategories) {
            if (dbCat.getName().equals(ocrCategory)) {
                return ocrCategory;
            }
        }
        
        // OCR_receipts 카테고리를 DB 카테고리로 매핑
        // Map.of()는 최대 10개 쌍만 지원하므로 HashMap 사용
        Map<String, String> categoryMapping = new HashMap<>();
        categoryMapping.put("식비", "식비");
        categoryMapping.put("생활용품", "쇼핑");
        categoryMapping.put("통신비", "주거/통신");
        categoryMapping.put("주거", "주거/통신");
        categoryMapping.put("보험", "금융/보험");
        categoryMapping.put("건강", "의료/건강");
        categoryMapping.put("문화", "취미/여가");
        categoryMapping.put("여행", "취미/여가");
        categoryMapping.put("자동차", "교통");
        categoryMapping.put("자기계발", "취미/여가");
        categoryMapping.put("꾸밈비", "쇼핑");
        categoryMapping.put("경조사", "경조사");
        categoryMapping.put("기타", "기타");
        
        String mappedCategory = categoryMapping.get(ocrCategory);
        if (mappedCategory != null) {
            // 매핑된 카테고리가 DB에 있는지 확인
            for (CategoryDTO dbCat : dbCategories) {
                if (dbCat.getName().equals(mappedCategory)) {
                    return mappedCategory;
                }
            }
        }
        
        // 매핑 실패 시 "기타" 반환
        log.warn("OCR_receipts 카테고리 매핑 실패: {} -> 기타로 설정", ocrCategory);
        return "기타";
    }
}
