package com.smartbudget.auth;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
public class UserDTO {
    private Long userId;
    private String email;
    private String passwordHash;
    private LocalDateTime createdAt;
    private String name;
    private String phone;
    private String addr;
    private LocalDate birth;
    private String gender;
    private String photo;
    private String provider;
    private String providerId;
    private String role;
}
