package com.cos.fairbid.user.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배송지 수정 요청 DTO
 *
 * @param recipientName 수령인 이름
 * @param recipientPhone 연락처 (010-XXXX-XXXX 형식)
 * @param postalCode 우편번호
 * @param address 주소
 * @param addressDetail 상세주소 (선택)
 */
public record UpdateShippingAddressRequest(
        @NotBlank(message = "수령인 이름은 필수입니다.")
        @Size(max = 100, message = "수령인 이름은 100자 이하로 입력해주세요.")
        String recipientName,

        @NotBlank(message = "연락처는 필수입니다.")
        @Pattern(regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        String recipientPhone,

        @Size(max = 10, message = "우편번호는 10자 이하로 입력해주세요.")
        String postalCode,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 500, message = "주소는 500자 이하로 입력해주세요.")
        String address,

        @Size(max = 200, message = "상세주소는 200자 이하로 입력해주세요.")
        String addressDetail
) {
}
