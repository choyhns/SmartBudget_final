package com.smartbudget.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri; // 프론트로 보낼 URL

    @Value("${app.oauth2.frontend-port:5173}")
    private int frontendPort;

    @Value("${app.oauth2.allowed-hosts:localhost,127.0.0.1}")
    private String allowedHostsRaw;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth2 authentication required");
            return;
        }

        String provider = oauthToken.getAuthorizedClientRegistrationId(); // "kakao"
        OAuth2User oauthUser = oauthToken.getPrincipal();

        if (!"kakao".equals(provider)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported provider: " + provider);
            return;
        }

        KakaoOAuth2UserInfo info = KakaoOAuth2UserInfo.from(oauthUser.getAttributes());

        // 이메일은 동의(scope) 없으면 null일 수 있습니다. (account_email 동의 필요)
        if (info.getEmail() == null || info.getEmail().isBlank()) {
            // 필요 시: 프론트에 에러로 redirect 하도록 변경 가능
            throw new RuntimeException("카카오 이메일 제공 동의가 필요합니다. (account_email)");
        }
        if (info.getProviderId() == null || info.getProviderId().isBlank()) {
            throw new RuntimeException("카카오 사용자 식별자(id)를 확인할 수 없습니다.");
        }

        // 1) provider+providerId로 사용자 조회
        UserDTO user = userMapper.selectUserByProviderAndProviderId(provider, info.getProviderId());

        // 2) 없으면 신규 생성
        if (user == null) {
            user = new UserDTO();
            user.setEmail(info.getEmail());
            user.setName(info.getNickname());
            user.setPhoto(info.getProfileImageUrl());
            user.setProvider(provider);
            user.setProviderId(info.getProviderId());
            user.setRole("user");

            // password_hash NOT NULL 제약이 있을 수 있어 랜덤 비번을 해시로 저장 (로컬 로그인용이 아님)
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString())); //

            userMapper.insertUser(user);
            user = userMapper.selectUserByProviderAndProviderId(provider, info.getProviderId());

            log.info("New kakao user registered: email={}, providerId={}", user.getEmail(), user.getProviderId());
        } else {
            log.info("Kakao user login: email={}, providerId={}", user.getEmail(), user.getProviderId());
        }

        // 토큰 발급 전에 userId가 null인 경우를 미리 예방/방어
        if (user == null || user.getUserId() == null) {
            log.error("Cannot issue JWT: userId is null. provider={}, providerId={}", provider, info.getProviderId()); // 🆕✅
            throw new IllegalStateException("SSO 로그인 처리 중 userId를 확인할 수 없습니다."); // 🆕✅
        }

        // 3) 우리 JWT 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        boolean needsProfile =
            isBlank(user.getPhone()) ||
            isBlank(user.getAddr()) ||
            user.getBirth() == null ||
            isBlank(user.getGender());

        String baseRedirectUri = resolveRedirectUri(request);


        // 4) 프론트로 redirect (query로 토큰 전달)
        String targetUrl = UriComponentsBuilder.fromUriString(baseRedirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("needsProfile", needsProfile)
                .build(true)
                .toUriString();

        response.sendRedirect(targetUrl);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String resolveRedirectUri(HttpServletRequest request) {
        String host = extractHost(request);

        Set<String> allowedHosts = Arrays.stream(allowedHostsRaw.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toSet());

        if (!allowedHosts.contains(host)) {
            log.warn("Unallowed host '{}', fallback redirectUri={}", host, redirectUri);
            return redirectUri; // 안전 fallback
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String scheme = (forwardedProto != null && !forwardedProto.isBlank())
                ? forwardedProto
                : request.getScheme();

        return UriComponentsBuilder.newInstance()
                .scheme(scheme)
                .host(host)
                .port(frontendPort)
                .path("/login")
                .build()
                .toUriString();
    }

    private String extractHost(HttpServletRequest request) {
        String xfh = request.getHeader("X-Forwarded-Host");
        String hostPort = (xfh != null && !xfh.isBlank())
                ? xfh.split(",")[0].trim()
                : request.getServerName();

        int idx = hostPort.indexOf(':');
        return (idx > -1) ? hostPort.substring(0, idx) : hostPort;
    }

}
