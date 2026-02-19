package com.smartbudget.recommendation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 신한카드 신용/체크카드 JSON 로드 후 CardDTO 목록으로 변환
 * - 설정 경로(file) 또는 classpath:data/ 파일 사용
 */
@Slf4j
@Component
public class ShinhanCardLoader {

    private static final String DEFAULT_CREDIT_RESOURCE = "data/shinhancard_credit_cards.json";
    private static final String DEFAULT_CHECK_RESOURCE = "data/shinhancard_check_cards.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.shinhan.cards.credit-path:}")
    private String creditPath;

    @Value("${app.shinhan.cards.check-path:}")
    private String checkPath;

    /**
     * 신한 신용카드 + 체크카드 통합 목록을 CardDTO로 반환 (cardId는 미설정, DB insert 시 부여)
     */
    public List<CardDTO> loadAllCards() {
        List<CardDTO> result = new ArrayList<>();
        long index = 1L;

        List<ShinhanCardItem> credit = loadJson(creditPath, DEFAULT_CREDIT_RESOURCE, "shinhancard_credit_cards.json");
        int creditWithLink = 0;
        for (ShinhanCardItem item : credit) {
            if (item.getLink() != null && !item.getLink().isBlank()) {
                creditWithLink++;
                if (creditWithLink <= 3) {
                }
            }
            result.add(toCardDTO(index++, item, "신한카드"));
        }

        List<ShinhanCardItem> check = loadJson(checkPath, DEFAULT_CHECK_RESOURCE, "shinhancard_check_cards.json");
        int checkWithLink = 0;
        for (ShinhanCardItem item : check) {
            if (item.getLink() != null && !item.getLink().isBlank()) {
                checkWithLink++;
                if (checkWithLink <= 3) {
                }
            }
            result.add(toCardDTO(index++, item, "신한카드"));
        }

        return result;
    }

    private List<ShinhanCardItem> loadJson(String configuredPath, String classpathResource, String fallbackFileName) {
        try {
            if (configuredPath != null && !configuredPath.isBlank()) {
                Path path = Paths.get(configuredPath.trim());
                if (Files.exists(path)) {
                    String json = Files.readString(path, StandardCharsets.UTF_8);
                    return objectMapper.readValue(json, new TypeReference<>() {});
                }
            }
        } catch (Exception e) {
            log.warn("설정 경로에서 로드 실패: {} - {}", configuredPath, e.getMessage());
        }

        try {
            Resource resource = new ClassPathResource(classpathResource);
            if (!resource.exists()) {
                resource = new ClassPathResource("data/" + fallbackFileName);
            }
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String json = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                    return objectMapper.readValue(json, new TypeReference<>() {});
                }
            }
        } catch (IOException e) {
            log.warn("classpath 로드 실패: {} - {}", classpathResource, e.getMessage());
        }

        return List.of();
    }

    private static CardDTO toCardDTO(long temporaryId, ShinhanCardItem item, String defaultCompany) {
        CardDTO dto = new CardDTO();
        dto.setCardId(temporaryId);
        dto.setName(item.getCardName() != null ? item.getCardName() : "");
        dto.setCompany(defaultCompany);
        String summary = item.getBenefitSummary() != null ? item.getBenefitSummary() : "";
        dto.setBenefitsJson("{\"summary\":\"" + escapeJson(summary) + "\"}");
        dto.setImageUrl(item.getImageUrl());
        dto.setLink(item.getLink());
        dto.setTags(new String[] { "신한카드", item.getSource() != null ? item.getSource() : "shinhancard" });
        return dto;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
