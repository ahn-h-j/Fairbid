package com.cos.fairbid.user.application.port.in;

import com.cos.fairbid.user.domain.User;

/**
 * 계좌 정보 수정 유스케이스
 * 판매자가 판매 대금을 수령할 계좌를 등록/수정한다.
 */
public interface UpdateBankAccountUseCase {

    /**
     * 계좌 정보를 수정한다.
     *
     * @param userId        사용자 ID
     * @param bankName      은행명
     * @param accountNumber 계좌번호
     * @param accountHolder 예금주
     * @return 수정된 User
     */
    User updateBankAccount(Long userId, String bankName, String accountNumber, String accountHolder);
}
