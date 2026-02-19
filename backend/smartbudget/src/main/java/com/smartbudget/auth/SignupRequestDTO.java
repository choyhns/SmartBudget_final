package com.smartbudget.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
public class SignupRequestDTO {
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "유효한 이메일 형식이 아닙니다")
    private String email;
    
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
    private String password;
    
    @NotBlank(message = "비밀번호를 확인해주세요")
    private String passwordConfirm;

    @NotBlank(message = "이름은 필수입니다")
    private String name;

    @NotBlank(message = "전화번호는 필수입니다")
    private String phone;

    @NotBlank(message = "주소는 필수입니다")
    private String addr;

    @NotNull(message = "생년월일은 필수입니다")
    private LocalDate birth;

    @NotBlank(message = "성별은 필수입니다")
    private String gender;

    private String photo;
    private String provider;
    private String providerId;
    private String role;
}
