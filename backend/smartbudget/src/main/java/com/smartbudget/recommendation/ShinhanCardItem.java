package com.smartbudget.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 신한카드 JSON (shinhancard_cards.json / shinhancard_check_cards.json) 항목
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShinhanCardItem {
    @JsonProperty("card_name")
    private String cardName;

    @JsonProperty("benefit_summary")
    private String benefitSummary;

    @JsonProperty("image_url")
    private String imageUrl;

    /** 카드 상세/신청 페이지 URL (신한카드 공식) */
    @JsonProperty("link")
    private String link;

    private String source;
}
