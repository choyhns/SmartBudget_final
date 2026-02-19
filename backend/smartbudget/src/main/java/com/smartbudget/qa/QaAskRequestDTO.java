package com.smartbudget.qa;

import lombok.Data;

@Data
public class QaAskRequestDTO {
    private Long userId;
    /** 연월 (yyyyMM 또는 yyyy-MM). 없으면 이번 달로 기본 */
    private String yearMonth;
    private String question;
}
