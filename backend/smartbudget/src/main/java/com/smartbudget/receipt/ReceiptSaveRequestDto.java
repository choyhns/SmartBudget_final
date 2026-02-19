package com.smartbudget.receipt;

import lombok.Data;

import java.time.LocalDate;

/**
 * 거래 저장(영수증 + 거래) API에서 프론트가 FormData의 "data"로 보내는 JSON.
 * 사용자가 입력한 날짜를 기준으로 S3 경로(user_id/연도/월)를 만든다.
 */
@Data
public class ReceiptSaveRequestDto {
    /** 프론트에서 사용자가 확정한 거래 날짜 (S3 폴더 및 거래 일시에 사용) */
    private LocalDate date;
    private String amount;
    private String merchant;
    private Long categoryId;
    /** "EXPENSE" | "INCOME" 등 */
    private String type;
    private String memo;
    private Long methodId;
}
