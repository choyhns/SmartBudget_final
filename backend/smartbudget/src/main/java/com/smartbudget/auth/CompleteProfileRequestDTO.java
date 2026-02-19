package com.smartbudget.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CompleteProfileRequestDTO {
    @NotBlank(message = "이름은 필수입니다")
    private String name;

    @NotBlank(message = "휴대폰 번호는 필수입니다")
    private String phone;

    @NotBlank(message = "주소는 필수입니다")
    private String addr;

    @NotNull(message = "생년월일은 필수입니다")
    private LocalDate birth;

    @NotBlank(message = "성별은 필수입니다")
    private String gender;

    private String photo;
}
