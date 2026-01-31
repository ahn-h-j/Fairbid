package com.cos.fairbid.user.application.port.in;

import com.cos.fairbid.user.domain.User;

/**
 * 배송지 수정 유스케이스
 */
public interface UpdateShippingAddressUseCase {

    /**
     * 배송지 정보를 수정한다.
     *
     * @param userId        사용자 ID
     * @param recipientName 수령인 이름
     * @param phone         연락처
     * @param postalCode    우편번호
     * @param address       주소
     * @param addressDetail 상세주소
     * @return 수정된 User
     */
    User updateShippingAddress(Long userId, String recipientName, String phone,
                               String postalCode, String address, String addressDetail);
}
