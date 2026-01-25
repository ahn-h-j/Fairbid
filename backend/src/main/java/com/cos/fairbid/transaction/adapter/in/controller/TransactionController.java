package com.cos.fairbid.transaction.adapter.in.controller;

import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.transaction.adapter.in.dto.PaymentResponse;
import com.cos.fairbid.transaction.adapter.in.dto.TransactionDetailResponse;
import com.cos.fairbid.transaction.adapter.in.dto.TransactionSummaryResponse;
import com.cos.fairbid.transaction.application.port.in.PaymentUseCase;
import com.cos.fairbid.transaction.application.port.in.TransactionQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 거래(결제) 컨트롤러
 * 결제 처리 및 거래 조회 API를 제공한다
 *
 * <ul>
 *   <li>POST /api/v1/transactions/{transactionId}/payment - 결제 처리</li>
 *   <li>GET /api/v1/transactions/{transactionId} - 거래 상세 조회</li>
 *   <li>GET /api/v1/transactions/my-sales - 내 판매 내역 조회</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final PaymentUseCase paymentUseCase;
    private final TransactionQueryUseCase transactionQueryUseCase;

    /**
     * 결제를 처리한다
     * 인증된 사용자(구매자)만 자신의 거래에 대해 결제할 수 있다
     *
     * @param transactionId 거래 ID
     * @return 결제 처리 결과
     */
    @PostMapping("/{transactionId}/payment")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @PathVariable Long transactionId
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        PaymentResponse response = paymentUseCase.processPayment(transactionId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 거래 상세 정보를 조회한다
     *
     * @param transactionId 거래 ID
     * @return 거래 상세 정보
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionDetailResponse>> getTransaction(
            @PathVariable Long transactionId
    ) {
        TransactionDetailResponse response = transactionQueryUseCase.getTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 내 판매 내역을 조회한다
     * 인증된 사용자의 판매자 ID로 거래 목록을 조회한다
     *
     * @return 판매 내역 요약 목록
     */
    @GetMapping("/my-sales")
    public ResponseEntity<ApiResponse<List<TransactionSummaryResponse>>> getMySales() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<TransactionSummaryResponse> response = transactionQueryUseCase.getMySales(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
