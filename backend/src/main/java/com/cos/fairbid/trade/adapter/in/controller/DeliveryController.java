package com.cos.fairbid.trade.adapter.in.controller;

import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.trade.adapter.in.dto.AddressRequest;
import com.cos.fairbid.trade.adapter.in.dto.DeliveryInfoResponse;
import com.cos.fairbid.trade.adapter.in.dto.ShippingRequest;
import com.cos.fairbid.trade.application.port.in.DeliveryUseCase;
import com.cos.fairbid.trade.domain.DeliveryInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 택배 배송 API 컨트롤러
 *
 * - POST /api/v1/trades/{tradeId}/delivery/address - 배송지 입력 (구매자)
 * - POST /api/v1/trades/{tradeId}/delivery/ship - 송장 입력 (판매자)
 * - POST /api/v1/trades/{tradeId}/delivery/confirm - 수령 확인 (구매자)
 */
@RestController
@RequestMapping("/api/v1/trades/{tradeId}/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryUseCase deliveryUseCase;

    /**
     * 배송지 입력 (구매자)
     */
    @PostMapping("/address")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> submitAddress(
            @PathVariable Long tradeId,
            @Valid @RequestBody AddressRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.submitAddress(
                tradeId,
                userId,
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getPostalCode(),
                request.getAddress(),
                request.getAddressDetail()
        );
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }

    /**
     * 송장 입력 (판매자)
     */
    @PostMapping("/ship")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> ship(
            @PathVariable Long tradeId,
            @Valid @RequestBody ShippingRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.ship(
                tradeId,
                userId,
                request.getCourierCompany(),
                request.getTrackingNumber()
        );
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }

    /**
     * 수령 확인 (구매자)
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> confirmDelivery(
            @PathVariable Long tradeId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        DeliveryInfo info = deliveryUseCase.confirmDelivery(tradeId, userId);
        return ResponseEntity.ok(ApiResponse.success(DeliveryInfoResponse.from(info)));
    }
}
