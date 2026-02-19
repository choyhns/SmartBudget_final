package com.smartbudget.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    /**
     * 회원가입
     */
    @Transactional
    public AuthResponseDTO signup(SignupRequestDTO request) {
        // 이메일 중복 확인
        if (userMapper.existsByEmail(request.getEmail())) {
            throw new RuntimeException("이미 사용 중인 이메일입니다.");
        }
        
        // 비밀번호 확인
        if (request.getPasswordConfirm() != null && 
            !request.getPassword().equals(request.getPasswordConfirm())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }
        
        // 사용자 생성
        UserDTO user = new UserDTO();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setAddr(request.getAddr());
        user.setBirth(request.getBirth());
        user.setGender(request.getGender());
        user.setPhoto(request.getPhoto());
        user.setProvider(request.getProvider());
        user.setProviderId(request.getProviderId());
        user.setRole(request.getRole());

        
        userMapper.insertUser(user);
        log.info("New user registered: {}", user.getEmail());
        
        // 토큰 생성
        return generateAuthResponse(user);
    }
    
    /**
     * 로그인
     */
    public AuthResponseDTO login(LoginRequestDTO request) {
        // 사용자 조회
        UserDTO user = userMapper.selectUserByEmail(request.getEmail());
        if (user == null) {
            throw new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        
        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        
        log.info("User logged in: {}", user.getEmail());
        
        // 토큰 생성
        return generateAuthResponse(user);
    }
    
    /**
     * 토큰 갱신
     */
    public AuthResponseDTO refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("유효하지 않은 리프레시 토큰입니다.");
        }
        
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        UserDTO user = userMapper.selectUserById(userId);
        
        if (user == null) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        
        return generateAuthResponse(user);
    }
    
    /**
     * 현재 사용자 정보 조회
     */
    public AuthResponseDTO.UserInfoDTO getCurrentUser(Long userId) {
        UserDTO user = userMapper.selectUserById(userId);
        if (user == null) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }
        
        return AuthResponseDTO.UserInfoDTO.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .photo(user.getPhoto())
                .build();
    }
    
    /**
     * 인증 응답 생성
     */
    private AuthResponseDTO generateAuthResponse(UserDTO user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());
        
        return AuthResponseDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .user(AuthResponseDTO.UserInfoDTO.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .build())
                .build();
    }


    @Transactional
    public void completeProfile(Long userId, CompleteProfileRequestDTO request) {
        UserDTO user = userMapper.selectUserById(userId);
        if (user == null) {
            throw new RuntimeException("사용자를 찾을 수 없습니다.");
        }

        // 필수 필드 업데이트
        user.setPhone(request.getPhone());
        user.setAddr(request.getAddr());
        user.setBirth(request.getBirth());
        user.setGender(request.getGender());

        userMapper.updateRequiredProfileByUserId(user);
    }


}
