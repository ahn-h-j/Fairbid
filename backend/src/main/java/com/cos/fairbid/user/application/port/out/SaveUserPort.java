package com.cos.fairbid.user.application.port.out;

import com.cos.fairbid.user.domain.User;

/**
 * 사용자 저장 아웃바운드 포트
 * 영속성 계층에 사용자를 저장하는 인터페이스를 정의한다.
 */
public interface SaveUserPort {

    /**
     * 사용자를 저장한다. (생성 및 수정)
     *
     * @param user 저장할 User 도메인 객체
     * @return 저장된 User 도메인 객체 (ID 포함)
     */
    User save(User user);
}
