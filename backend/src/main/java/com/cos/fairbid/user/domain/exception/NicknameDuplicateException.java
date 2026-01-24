package com.cos.fairbid.user.domain.exception;

import com.cos.fairbid.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * 닉네임 중복 예외
 */
public class NicknameDuplicateException extends DomainException {

    private NicknameDuplicateException(String nickname) {
        super("NICKNAME_DUPLICATE", "이미 사용 중인 닉네임입니다: " + nickname);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    public static NicknameDuplicateException of(String nickname) {
        return new NicknameDuplicateException(nickname);
    }
}
