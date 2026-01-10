package com.cos.fairbid.auction.adapter.in.controller;

import com.cos.fairbid.auction.adapter.in.dto.AuctionResponse;
import com.cos.fairbid.auction.adapter.in.dto.CreateAuctionRequest;
import com.cos.fairbid.auction.application.port.in.CreateAuctionUseCase;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 경매 REST Controller
 */
@RestController
@RequestMapping("/api/v1/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final CreateAuctionUseCase createAuctionUseCase;

    /**
     * 경매 등록 API
     *
     * @param request 경매 생성 요청
     * @return 생성된 경매 정보
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AuctionResponse>> createAuction(
            @Valid @RequestBody CreateAuctionRequest request
    ) {
        // TODO: 인증 구현 후 실제 사용자 ID로 변경
        Long sellerId = 1L;  // 모킹된 판매자 ID

        Auction auction = createAuctionUseCase.createAuction(request.toCommand(sellerId));
        AuctionResponse response = AuctionResponse.from(auction);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
