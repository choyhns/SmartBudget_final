package com.smartbudget.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.security.Principal;

@Data
@AllArgsConstructor
public class CustomUserPrincipal implements Principal {
    private Long userId;
    private String email;
    
    @Override
    public String getName() {
        return email;
    }
}
