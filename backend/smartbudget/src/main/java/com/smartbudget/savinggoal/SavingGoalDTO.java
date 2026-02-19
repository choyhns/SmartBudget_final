package com.smartbudget.savinggoal;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SavingGoalDTO {
    private Long goalId;
    private String goalTitle;             // 목표 제목 (예: 내 집 마련, 여행 자금)
    private BigDecimal goalAmount;       // 목표 금액
    private LocalDate startDate;          // 시작일
    private LocalDate targetDate;         // 목표일
    private BigDecimal monthlyTarget;     // 월 저축 목표액
    private LocalDateTime createdAt;
    private Long userId;

    // 조회용 추가 필드
    private BigDecimal currentAmount;     // 현재까지 저축한 금액
    private BigDecimal progressPercent;   // 달성률 (0-100)
    private Integer remainingMonths;      // 남은 개월 수
}
