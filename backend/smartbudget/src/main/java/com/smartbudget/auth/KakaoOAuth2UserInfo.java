package com.smartbudget.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class KakaoOAuth2UserInfo {
    private String providerId;
    private String email;
    private String nickname;
    private String profileImageUrl;

    @SuppressWarnings("unchecked")
    public static KakaoOAuth2UserInfo from(Map<String, Object> attributes) {
        // Kakao: { id: 123, kakao_account: { email: "...", profile: { nickname: "...", profile_image_url: "..." } } }
        Object idObj = attributes.get("id");
        String providerId = (idObj == null) ? null : String.valueOf(idObj);

        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        String email = null;
        String nickname = null;
        String profileImageUrl = null;

        if (account != null) {
            email = (String) account.get("email");

            Map<String, Object> profile = (Map<String, Object>) account.get("profile");
            if (profile != null) {
                nickname = (String) profile.get("nickname");
                profileImageUrl = (String) profile.get("profile_image_url");
            }
        }

        return new KakaoOAuth2UserInfo(providerId, email, nickname, profileImageUrl);
    }
}
