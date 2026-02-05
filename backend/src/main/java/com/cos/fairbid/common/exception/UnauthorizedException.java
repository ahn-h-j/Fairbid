package com.cos.fairbid.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 인증되지 않은 사용자 접근 시 발생하는 예외
 * SecurityUtils에서 인증 정보가 없을 때 사용한다.
 */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }

    public static UnauthorizedException notAuthenticated() {
        return new UnauthorizedException("인증된 사용자 정보를 찾을 수 없습니다.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
