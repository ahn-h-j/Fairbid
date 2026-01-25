package com.cos.fairbid.user.adapter.in.controller;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auth.infrastructure.security.CookieUtils;
import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.annotation.RequireOnboarding;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.user.adapter.in.dto.*;
import com.cos.fairbid.user.application.port.in.*;
import com.cos.fairbid.user.application.port.in.CompleteOnboardingUseCase.OnboardingResult;
import com.cos.fairbid.user.application.port.in.GetMyAuctionsUseCase.CursorPage;
import com.cos.fairbid.user.application.port.in.GetMyAuctionsUseCase.MyAuctionItem;
import com.cos.fairbid.user.application.port.in.GetMyBidsUseCase.MyBidItem;
import com.cos.fairbid.user.application.port.in.UpdateNicknameUseCase.UpdateResult;
import com.cos.fairbid.user.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 REST Controller
 *
 * 엔드포인트:
 * - POST /api/v1/users/me/onboarding → 온보딩 완료 + JWT 재발급
 * - GET  /api/v1/users/check-nickname → 닉네임 중복 확인 (비인증)
 * - GET  /api/v1/users/me → 내 프로필 조회
 * - PUT  /api/v1/users/me → 닉네임 수정 + JWT 재발급
 * - DELETE /api/v1/users/me → 회원 탈퇴
 * - GET  /api/v1/users/me/auctions → 내 판매 목록 (커서 페이지네이션)
 * - GET  /api/v1/users/me/bids → 내 입찰 목록 (커서 페이지네이션)
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final CompleteOnboardingUseCase completeOnboardingUseCase;
    private final CheckNicknameUseCase checkNicknameUseCase;
    private final GetMyProfileUseCase getMyProfileUseCase;
    private final UpdateNicknameUseCase updateNicknameUseCase;
    private final DeactivateAccountUseCase deactivateAccountUseCase;
    private final GetMyAuctionsUseCase getMyAuctionsUseCase;
    private final GetMyBidsUseCase getMyBidsUseCase;
    private final CookieUtils cookieUtils;

    /**
     * 온보딩을 완료한다.
     * 닉네임과 전화번호를 등록하고, onboarded=true가 반영된 새 JWT를 발급한다.
     * Refresh Token은 HttpOnly 쿠키로, Access Token은 응답 본문으로 전달한다.
     *
     * @param request  온보딩 요청 (닉네임, 전화번호)
     * @param response HTTP 응답 (쿠키 설정용)
     * @return 새 Access Token
     */
    @PostMapping("/me/onboarding")
    public ResponseEntity<ApiResponse<TokenReissueResponse>> completeOnboarding(
            @Valid @RequestBody OnboardingRequest request,
            HttpServletResponse response) {

        Long userId = SecurityUtils.getCurrentUserId();
        OnboardingResult result = completeOnboardingUseCase.completeOnboarding(
                userId, request.nickname(), request.phoneNumber());

        // Refresh Token을 HttpOnly 쿠키에 설정
        cookieUtils.setRefreshTokenCookie(response, result.refreshToken());

        return ResponseEntity.ok(ApiResponse.success(new TokenReissueResponse(result.accessToken())));
    }

    /**
     * 닉네임 사용 가능 여부를 확인한다.
     * 비인증 사용자도 호출 가능하다 (SecurityConfig에서 permitAll).
     *
     * @param nickname 확인할 닉네임
     * @return 사용 가능 여부
     */
    @GetMapping("/check-nickname")
    public ResponseEntity<ApiResponse<NicknameCheckResponse>> checkNickname(
            @RequestParam String nickname) {

        boolean available = checkNicknameUseCase.isAvailable(nickname);
        return ResponseEntity.ok(ApiResponse.success(new NicknameCheckResponse(available)));
    }

    /**
     * 내 프로필 정보를 조회한다.
     *
     * @return 사용자 프로필 정보
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        User user = getMyProfileUseCase.getMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(UserProfileResponse.from(user)));
    }

    /**
     * 닉네임을 수정한다.
     * JWT의 nickname 클레임이 변경되므로 새 토큰을 발급한다.
     *
     * @param request  닉네임 수정 요청
     * @param response HTTP 응답 (쿠키 설정용)
     * @return 새 Access Token
     */
    @PutMapping("/me")
    @RequireOnboarding
    public ResponseEntity<ApiResponse<TokenReissueResponse>> updateNickname(
            @Valid @RequestBody UpdateNicknameRequest request,
            HttpServletResponse response) {

        Long userId = SecurityUtils.getCurrentUserId();
        UpdateResult result = updateNicknameUseCase.updateNickname(userId, request.nickname());

        // Refresh Token을 HttpOnly 쿠키에 설정
        cookieUtils.setRefreshTokenCookie(response, result.refreshToken());

        return ResponseEntity.ok(ApiResponse.success(new TokenReissueResponse(result.accessToken())));
    }

    /**
     * 회원 탈퇴를 수행한다. (Soft Delete)
     * 계정을 비활성화하고 Refresh Token을 삭제한다.
     *
     * @param response HTTP 응답 (쿠키 제거용)
     */
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deactivateAccount(HttpServletResponse response) {
        Long userId = SecurityUtils.getCurrentUserId();
        deactivateAccountUseCase.deactivate(userId);

        // Refresh Token 쿠키 제거
        cookieUtils.clearRefreshTokenCookie(response);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 내 판매 경매 목록을 조회한다. (커서 기반 페이지네이션)
     *
     * @param status 경매 상태 필터 (선택, null이면 전체)
     * @param cursor 마지막으로 조회한 경매 ID (선택, null이면 처음부터)
     * @param size   페이지 크기 (기본 20)
     * @return 내 판매 경매 목록 (커서 페이지)
     */
    @GetMapping("/me/auctions")
    @RequireOnboarding
    public ResponseEntity<ApiResponse<CursorPageResponse<MyAuctionResponse>>> getMyAuctions(
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = SecurityUtils.getCurrentUserId();
        CursorPage<MyAuctionItem> page = getMyAuctionsUseCase.getMyAuctions(userId, status, cursor, size);

        CursorPageResponse<MyAuctionResponse> response =
                CursorPageResponse.from(page, MyAuctionResponse::from);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 내 입찰 경매 목록을 조회한다. (커서 기반 페이지네이션)
     *
     * @param cursor 마지막으로 조회한 경매 ID (선택, null이면 처음부터)
     * @param size   페이지 크기 (기본 20)
     * @return 내 입찰 경매 목록 (커서 페이지)
     */
    @GetMapping("/me/bids")
    @RequireOnboarding
    public ResponseEntity<ApiResponse<CursorPageResponse<MyBidResponse>>> getMyBids(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = SecurityUtils.getCurrentUserId();
        CursorPage<MyBidItem> page = getMyBidsUseCase.getMyBids(userId, cursor, size);

        CursorPageResponse<MyBidResponse> response =
                CursorPageResponse.from(page, MyBidResponse::from);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
