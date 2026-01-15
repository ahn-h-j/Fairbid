package com.cos.fairbid.bid.adapter.in.controller;

import com.cos.fairbid.bid.adapter.in.dto.BidResponse;
import com.cos.fairbid.bid.adapter.in.dto.PlaceBidRequest;
import com.cos.fairbid.bid.application.port.in.PlaceBidUseCase;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 입찰 REST Controller
 */
@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class BidController {

    private final PlaceBidUseCase placeBidUseCase;

    /**
     * 입찰 API
     *
     * @param auctionId 경매 ID
     * @param request   입찰 요청
     * @param userIdHeader 테스트용 사용자 ID 헤더 (X-User-Id)
     * @return 생성된 입찰 정보
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(
            @PathVariable Long auctionId,
            @Valid @RequestBody PlaceBidRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "2") Long userIdHeader
    ) {
        // TODO: 인증 구현 후 실제 사용자 ID로 변경
        // 현재는 테스트용으로 X-User-Id 헤더에서 사용자 ID를 받음
        Long bidderId = userIdHeader;

        Bid bid = placeBidUseCase.placeBid(request.toCommand(auctionId, bidderId));
        BidResponse response = BidResponse.from(bid);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
