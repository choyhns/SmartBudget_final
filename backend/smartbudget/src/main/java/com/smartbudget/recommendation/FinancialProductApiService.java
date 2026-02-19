package com.smartbudget.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터포털(금융공공데이터) 금융상품 API 연동
 * - 한국산업은행 예금상품 정보 (B190030 GetDepositProductInfoService)
 * - 오픈 API 키: 공공데이터포털(data.go.kr) 활용신청 후 발급
 */
@Slf4j
@Service
public class FinancialProductApiService {

    private static final String BASE_URL = "http://apis.data.go.kr/B190030/GetDepositProductInfoService";
    private static final String LIST_PATH = "/getDepositProductList";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WebClient webClient;
    private final String apiKey;

    /** true면 .env 키를 URL 인코딩 없이 그대로 전송 (포털에서 복사한 '인코딩된 인증키'용) */
    private final boolean keyAlreadyEncoded;

    public FinancialProductApiService(
            WebClient.Builder webClientBuilder,
            @Value("${app.finance.api.key:}") String apiKey,
            @Value("${app.finance.api.key.encoded:false}") boolean keyAlreadyEncoded) {
        this.webClient = webClientBuilder.build();
        this.apiKey = cleanApiKey(apiKey);
        this.keyAlreadyEncoded = keyAlreadyEncoded;
    }

    public boolean isApiKeyConfigured() {
        return !apiKey.isEmpty();
    }

    /** 앞뒤 공백·줄바꿈 제거, 보이지 않는 문자 제거 */
    private static String cleanApiKey(String key) {
        if (key == null) return "";
        String s = key.trim().replace("\r", "").replace("\n", "").replace("\t", " ");
        return s.trim();
    }

    /** 인코딩된 키(%)면 디코딩해 원본으로, 아니면 그대로 반환 */
    private static String normalizeApiKey(String key) {
        if (key == null || key.isBlank()) return key;
        String cleaned = cleanApiKey(key);
        if (cleaned.contains("%")) {
            try {
                return URLDecoder.decode(cleaned, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return cleaned;
            }
        }
        return cleaned;
    }

    /** 공공데이터포털은 공백을 %20으로 보내는 경우가 많음 */
    private static String encodeKeyForQuery(String rawKey) {
        String encoded = URLEncoder.encode(rawKey, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20");
    }

    /**
     * 예금/적금 상품 목록 조회 (한국산업은행 API)
     * - API 키가 없으면 빈 목록 반환
     */
    public List<ProductDTO> fetchDepositProducts(int maxItems) {
        if (!isApiKeyConfigured()) {
            log.debug("공공데이터포털 API 키 미설정 → 금융상품 API 스킵");
            return List.of();
        }

        // API는 기준일자(bseDt)별로 조회 → 기간을 넓혀야 상품 누락 방지 (2020-06-23 제공 시작)
        String endDate = LocalDate.now().format(DATE_FORMAT);
        String startDate = "20200623";

        List<ProductDTO> all = new ArrayList<>();
        int pageNo = 1;
        int pageSize = Math.min(100, Math.max(10, maxItems));

        try {
            while (all.size() < maxItems) {
                // 인코딩된 키로 설정했으면 그대로, 아니면 일반인증키로 인코딩 후 전송
                String keyForUrl = keyAlreadyEncoded
                        ? apiKey
                        : encodeKeyForQuery(normalizeApiKey(apiKey));
                String query = String.format("serviceKey=%s&pageNo=%d&numOfRows=%d&sBseDt=%s&eBseDt=%s",
                        keyForUrl, pageNo, pageSize, startDate, endDate);
                String url = "http://apis.data.go.kr/B190030/GetDepositProductInfoService/getDepositProductList?" + query;
                String xml = webClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_XML)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (xml == null || xml.isBlank()) break;

                List<ProductDTO> page = parseDepositProductList(xml);
                if (page.isEmpty()) break;

                for (ProductDTO p : page) {
                    if (all.size() >= maxItems) break;
                    all.add(p);
                }

                if (page.size() < pageSize) break;
                pageNo++;
            }
        } catch (WebClientResponseException e) {
            String body = e.getResponseBodyAsString();
            if (body != null && body.length() > 500) body = body.substring(0, 500) + "...";
            log.warn("금융상품 API 호출 실패: {} - {}", e.getStatusCode(), body);
            return List.of();
        } catch (Exception e) {
            log.warn("금융상품 API 오류: {}", e.getMessage());
            return List.of();
        }

        log.info("금융상품 API 조회: {}건", all.size());
        return all;
    }

    /**
     * XML 응답에서 item 목록 파싱 (prdNm, prdOtl, prdJinPpo, hitIrtCndCone 등)
     */
    private List<ProductDTO> parseDepositProductList(String xml) {
        List<ProductDTO> list = new ArrayList<>();
        // <item> ... </item> 블록 단위로 파싱
        Pattern itemBlock = Pattern.compile("<item>([\\s\\S]*?)</item>");
        Matcher itemMatcher = itemBlock.matcher(xml);

        long id = 1L;
        while (itemMatcher.find()) {
            String block = itemMatcher.group(1);
            ProductDTO dto = parseItemBlock(block, id++);
            if (dto != null) list.add(dto);
        }

        return list;
    }

    private static final Pattern TAG = Pattern.compile("<([^>]+)>([^<]*)</\\1>");

    private ProductDTO parseItemBlock(String block, long temporaryId) {
        String prdNm = extractTag(block, "prdNm");
        String prdOtl = extractTag(block, "prdOtl");
        String prdJinPpo = extractTag(block, "prdJinPpo");
        String hitIrtCndCone = extractTag(block, "hitIrtCndCone");
        String prdJinTrmCone = extractTag(block, "prdJinTrmCone");

        if (prdNm == null || prdNm.isBlank()) return null;

        ProductDTO dto = new ProductDTO();
        dto.setProductId(temporaryId);
        dto.setName(prdNm.trim());
        dto.setBank("한국산업은행");
        dto.setType(mapProductType(prdJinPpo));
        dto.setRate(parseRate(hitIrtCndCone));
        dto.setConditionsJson(String.format(
                "{\"description\":\"%s\",\"purpose\":\"%s\",\"term\":\"%s\"}",
                escapeJson(prdOtl != null ? prdOtl : ""),
                escapeJson(prdJinPpo != null ? prdJinPpo : ""),
                escapeJson(prdJinTrmCone != null ? prdJinTrmCone : "")));
        dto.setTags(new String[] { "공공데이터", "예금상품" });
        return dto;
    }

    private String extractTag(String block, String tagName) {
        Pattern p = Pattern.compile("<" + tagName + ">([^<]*)</" + tagName + ">");
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String mapProductType(String prdJinPpo) {
        if (prdJinPpo == null) return "예금";
        if (prdJinPpo.contains("목돈모으기") || prdJinPpo.contains("적금")) return "적금";
        if (prdJinPpo.contains("목돈굴리기") || prdJinPpo.contains("예금")) return "예금";
        return "예금";
    }

    private static BigDecimal parseRate(String hitIrtCndCone) {
        if (hitIrtCndCone == null || hitIrtCndCone.isBlank()) return BigDecimal.ZERO;
        Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%").matcher(hitIrtCndCone);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1)).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {}
        }
        return BigDecimal.ZERO;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
