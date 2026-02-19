package com.smartbudget.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * 회원가입
     */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDTO> signup(@Valid @RequestBody SignupRequestDTO request) {
        AuthResponseDTO response = authService.signup(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        AuthResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 토큰 갱신
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        AuthResponseDTO response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 현재 사용자 정보 조회
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponseDTO.UserInfoDTO> getCurrentUser(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        AuthResponseDTO.UserInfoDTO user = authService.getCurrentUser(principal.getUserId());
        return ResponseEntity.ok(user);
    }
    
    /**
     * 이메일 중복 확인
     */
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        // UserMapper를 통해 이메일 존재 여부 확인
        // 여기서는 AuthService에 메서드 추가 필요
        return ResponseEntity.ok(Map.of("available", true));
    }


    @PatchMapping("/me/profile")
    public ResponseEntity<AuthResponseDTO.UserInfoDTO> completeProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CompleteProfileRequestDTO request
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        authService.completeProfile(principal.getUserId(), request);
        AuthResponseDTO.UserInfoDTO user = authService.getCurrentUser(principal.getUserId());
        return ResponseEntity.ok(user);
    }


}
