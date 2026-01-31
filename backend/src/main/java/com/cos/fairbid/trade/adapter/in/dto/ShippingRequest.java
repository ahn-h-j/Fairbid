package com.cos.fairbid.trade.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

/**
 * 발송 정보 입력 요청 DTO
 */
@Getter
public class ShippingRequest {

    @NotBlank(message = "택배사는 필수입니다.")
    private String courierCompany;

    @NotBlank(message = "송장번호는 필수입니다.")
    private String trackingNumber;
}
