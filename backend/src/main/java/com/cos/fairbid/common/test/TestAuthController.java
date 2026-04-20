package com.cos.fairbid.common.test;

import com.cos.fairbid.auth.application.port.in.OAuthLoginUseCase;
import com.cos.fairbid.auth.application.port.in.OAuthLoginUseCase.LoginResult;
import com.cos.fairbid.auth.application.port.out.OAuthUserInfo;
import com.cos.fairbid.common.config.serverrole.EnabledOnRole;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.user.application.port.out.SaveUserPort;
import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 시뮬레이션 전용 Mock OAuth 로그인 컨트롤러
 *
 * AI 에이전트 경매 시뮬레이션에서 외부 OAuth Provider 호출 없이
 * 진짜 인증 흐름(User 생성 + JWT 발급 + Refresh Redis 저장)을 통과시키기 위해 사용한다.
 *
 * 활성화 조건:
 * - simulation 프로파일 활성화 시에만 빈 등록 (@Profile)
 * - api 또는 all 서버 역할에서만 동작 (@EnabledOnRole)
 *
 * 주의: 운영/개발 환경에서는 절대 활성화되어서는 안 된다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test/auth")
@RequiredArgsConstructor
@Profile("simulation")
@EnabledOnRole({"api", "all"})
public class TestAuthController {

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final SaveUserPort saveUserPort;

    /**
     * Mock OAuth 로그인을 수행한다.
     *
     * 흐름:
     * 1. email 기반 deterministic providerId 생성 → 같은 email 재로그인 시 같은 User 재사용
     * 2. 가짜 OAuthUserInfo 생성 (provider=KAKAO 고정)
     * 3. AuthService.loginWithUserInfo() 호출 → 진짜 인증 흐름 통과
     * 4. 신규 사용자면 nickname/phoneNumber 자동 설정 (온보딩 완료 처리)
     * 5. accessToken + refreshToken 응답 (에이전트는 헤더 기반이라 본문 노출 필요)
     *
     * @param request email, nickname, phoneNumber
     * @return accessToken, refreshToken, userId, onboarded
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MockLoginResponse>> mockLogin(
            @RequestBody MockLoginRequest request
    ) {
        // 1. email 기반 providerId 생성 (deterministic)
        String providerId = "test-" + sha256Short(request.email());

        // 2. 가짜 OAuthUserInfo 생성 (KAKAO로 고정)
        OAuthUserInfo userInfo = new OAuthUserInfo(
                request.email(),
                providerId,
                OAuthProvider.KAKAO
        );

        // 3. 진짜 인증 흐름 통과 (User 생성/조회 + JWT 발급 + Redis 저장)
        LoginResult result = oAuthLoginUseCase.loginWithUserInfo(userInfo);
        User user = result.user();

        // 4. 온보딩 자동 완료 (신규 사용자만)
        if (!user.isOnboarded()) {
            user.completeOnboarding(request.nickname(), request.phoneNumber());
            user = saveUserPort.save(user);
            log.info("[SIM] 온보딩 자동 완료: userId={}, nickname={}", user.getId(), request.nickname());
        }

        log.info("[SIM] Mock 로그인: userId={}, email={}, isNewUser={}",
                user.getId(), request.email(), result.isNewUser());

        // 5. 응답 (refreshToken도 본문에 포함 — 에이전트는 헤더 기반)
        return ResponseEntity.ok(ApiResponse.success(
                new MockLoginResponse(
                        result.accessToken(),
                        result.refreshToken(),
                        user.getId(),
                        user.isOnboarded()
                )
        ));
    }

    /**
     * SHA-256 해시의 앞 16자리를 반환한다.
     * email → providerId 매핑에 사용되며, 같은 email은 항상 같은 providerId가 된다.
     */
    private String sha256Short(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {  // 16자 (8바이트)
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK에 항상 존재 — 발생 불가능
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    /**
     * Mock 로그인 요청
     *
     * @param email       사용자 이메일 (providerId 결정 키)
     * @param nickname    온보딩 닉네임 (신규 사용자 시 자동 설정)
     * @param phoneNumber 온보딩 전화번호 (신규 사용자 시 자동 설정)
     */
    public record MockLoginRequest(
            String email,
            String nickname,
            String phoneNumber
    ) { }

    /**
     * Mock 로그인 응답
     *
     * @param accessToken  발급된 Access Token (Bearer 헤더로 사용)
     * @param refreshToken 발급된 Refresh Token (시뮬레이션은 본문 노출, 운영 흐름은 쿠키)
     * @param userId       발급된/조회된 사용자 ID
     * @param onboarded    온보딩 완료 여부 (자동 완료되었으므로 항상 true)
     */
    public record MockLoginResponse(
            String accessToken,
            String refreshToken,
            Long userId,
            boolean onboarded
    ) { }
}
