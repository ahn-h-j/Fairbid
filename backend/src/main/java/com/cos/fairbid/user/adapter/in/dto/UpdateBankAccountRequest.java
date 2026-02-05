package com.cos.fairbid.user.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 계좌 정보 수정 요청 DTO
 *
 * @param bankName      은행명
 * @param accountNumber 계좌번호
 * @param accountHolder 예금주
 */
public record UpdateBankAccountRequest(
        @NotBlank(message = "은행명은 필수입니다.")
        @Size(max = 50, message = "은행명은 50자 이하로 입력해주세요.")
        String bankName,

        @NotBlank(message = "계좌번호는 필수입니다.")
        @Size(max = 50, message = "계좌번호는 50자 이하로 입력해주세요.")
        String accountNumber,

        @NotBlank(message = "예금주는 필수입니다.")
        @Size(max = 50, message = "예금주는 50자 이하로 입력해주세요.")
        String accountHolder
) {
}
