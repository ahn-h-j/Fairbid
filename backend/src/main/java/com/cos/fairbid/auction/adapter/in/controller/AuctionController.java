package com.cos.fairbid.auction.adapter.in.controller;

import com.cos.fairbid.auction.adapter.in.dto.AuctionListResponse;
import com.cos.fairbid.auction.adapter.in.dto.AuctionResponse;
import com.cos.fairbid.auction.adapter.in.dto.CreateAuctionRequest;
import com.cos.fairbid.auction.application.port.in.CreateAuctionUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionDetailUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionListUseCase;
import com.cos.fairbid.auction.application.port.in.GetUserWinningInfoUseCase;
import com.cos.fairbid.auction.application.port.in.GetUserWinningInfoUseCase.UserWinningInfo;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.common.annotation.RequireOnboarding;
import com.cos.fairbid.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    private final GetAuctionDetailUseCase getAuctionDetailUseCase;
    private final GetAuctionListUseCase getAuctionListUseCase;
    private final GetUserWinningInfoUseCase getUserWinningInfoUseCase;

    /**
     * 경매 등록 API
     * 온보딩 완료한 사용자만 경매를 등록할 수 있다.
     *
     * @param request 경매 생성 요청
     * @return 생성된 경매 정보
     */
    @PostMapping
    @RequireOnboarding
    public ResponseEntity<ApiResponse<AuctionResponse>> createAuction(
            @Valid @RequestBody CreateAuctionRequest request
    ) {
        Long sellerId = SecurityUtils.getCurrentUserId();

        Auction auction = createAuctionUseCase.createAuction(request.toCommand(sellerId));
        AuctionResponse response = AuctionResponse.from(auction);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * 경매 목록 조회 API
     *
     * @param status   경매 상태 필터 (선택)
     * @param keyword  검색어 - 상품명 (선택)
     * @param pageable 페이지네이션 정보
     * @return 경매 목록 (페이지)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuctionListResponse>>> getAuctionList(
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<Auction> auctions = getAuctionListUseCase.getAuctionList(status, keyword, pageable);
        Page<AuctionListResponse> response = auctions.map(AuctionListResponse::from);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 경매 상세 조회 API
     * 인증된 사용자가 있으면 해당 사용자의 낙찰 순위 정보도 포함
     *
     * @param auctionId 조회할 경매 ID
     * @return 경매 상세 정보
     */
    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionResponse>> getAuctionDetail(
            @PathVariable Long auctionId
    ) {
        Auction auction = getAuctionDetailUseCase.getAuctionDetail(auctionId);

        // 인증된 사용자가 있고 종료된 경매면 낙찰 정보 조회
        Integer userWinningRank = null;
        String userWinningStatus = null;

        if (auction.getStatus() == AuctionStatus.ENDED) {
            Long currentUserId = SecurityUtils.getCurrentUserIdOrNull();
            UserWinningInfo winningInfo = getUserWinningInfoUseCase.getUserWinningInfo(auctionId, currentUserId);

            if (winningInfo != null) {
                userWinningRank = winningInfo.rank();
                userWinningStatus = winningInfo.status();
            }
        }

        AuctionResponse response = AuctionResponse.from(auction, userWinningRank, userWinningStatus);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
