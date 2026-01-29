package com.cos.fairbid.user.domain;

/**
 * 사용자 역할
 * 권한 기반 접근 제어에 사용된다.
 */
public enum UserRole {

    /** 일반 사용자 */
    USER,

    /** 관리자 (통계 조회, 경매/유저 관리 권한) */
    ADMIN
}
