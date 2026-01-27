package com.cos.fairbid.user.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * User 도메인 모델
 * Identity Context의 핵심 도메인 객체로, 사용자의 인증/프로필/상태를 관리한다.
 * JPA와 무관한 순수 POJO로 구현되어 비즈니스 로직만 포함한다.
 */
@Getter
@Builder
public class User {

    private Long id;
    private String email;
    private String nickname;
    private String phoneNumber;
    private OAuthProvider provider;
    private String providerId;
    private int warningCount;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private static final int MAX_WARNING_COUNT = 3;

    // ========== 팩토리 메서드 ==========

    /**
     * 신규 사용자를 생성한다.
     * OAuth 인증 성공 후 최초 가입 시 호출된다.
     * nickname, phoneNumber는 null로 생성되어 온보딩에서 설정한다.
     *
     * @param email      OAuth Provider에서 제공한 이메일
     * @param provider   OAuth Provider 종류
     * @param providerId Provider 고유 사용자 ID
     * @return 생성된 User 도메인 객체
     */
    public static User create(String email, OAuthProvider provider, String providerId) {
        return User.builder()
                .email(email)
                .provider(provider)
                .providerId(providerId)
                .warningCount(0)
                .isActive(true)
                .build();
    }

    /**
     * DB에서 복원된 User를 재구성한다.
     * Mapper에서 Entity → Domain 변환 시 사용한다.
     *
     * @return Builder 인스턴스 (모든 필드를 직접 설정)
     */
    public static UserBuilder reconstitute() {
        return User.builder();
    }

    // ========== 비즈니스 메서드 ==========

    /**
     * 사용자가 차단 상태인지 확인한다.
     * 경고 3회 이상이거나 비활성화된 사용자는 차단된다.
     *
     * @return 차단 여부
     */
    public boolean isBlocked() {
        return warningCount >= MAX_WARNING_COUNT || !isActive;
    }

    /**
     * 온보딩 완료 여부를 확인한다.
     * 닉네임과 전화번호가 모두 설정되어야 온보딩 완료이다.
     *
     * @return 온보딩 완료 여부
     */
    public boolean isOnboarded() {
        return nickname != null && phoneNumber != null;
    }

    /**
     * 온보딩을 완료한다.
     * 닉네임과 전화번호를 설정하여 사용자 프로필을 완성한다.
     *
     * @param nickname    닉네임 (2~20자, UK)
     * @param phoneNumber 전화번호 (010-XXXX-XXXX, UK, 변경 불가)
     */
    public void completeOnboarding(String nickname, String phoneNumber) {
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
    }

    /**
     * 닉네임을 변경한다.
     * 변경 횟수 제한 없음. UK 중복 검사는 서비스 레이어에서 처리한다.
     *
     * @param nickname 새로운 닉네임
     */
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 계정을 비활성화한다. (Soft Delete)
     * 비활성화된 계정으로는 재로그인이 차단된다.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 경고를 부여한다.
     * 노쇼(결제 미이행) 시 호출된다.
     * 3회 이상 경고 시 isBlocked() = true가 된다.
     */
    public void addWarning() {
        this.warningCount++;
    }
}
