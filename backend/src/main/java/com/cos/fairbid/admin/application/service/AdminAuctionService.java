package com.cos.fairbid.admin.application.service;

import com.cos.fairbid.admin.adapter.in.dto.AdminAuctionResponse;
import com.cos.fairbid.admin.application.port.in.ManageAuctionUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionListUseCase;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.user.application.port.out.LoadUserPort;
import com.cos.fairbid.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자 경매 관리 서비스
 * 경매 목록 조회 시 판매자 정보를 함께 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuctionService implements ManageAuctionUseCase {

    private final GetAuctionListUseCase getAuctionListUseCase;
    private final LoadUserPort loadUserPort;

    @Override
    public Page<AdminAuctionResponse> getAuctionList(AuctionStatus status, String keyword, Pageable pageable) {
        // 1. 경매 목록 조회
        Page<Auction> auctions = getAuctionListUseCase.getAuctionList(status, keyword, pageable);

        // 2. 판매자 ID 수집
        Set<Long> sellerIds = auctions.getContent().stream()
                .map(Auction::getSellerId)
                .collect(Collectors.toSet());

        // 3. 판매자 정보 일괄 조회 (N+1 방지)
        Map<Long, String> sellerNicknameMap = sellerIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> loadUserPort.findById(id)
                                .map(User::getNickname)
                                .orElse("탈퇴한 사용자")
                ));

        // 4. AdminAuctionResponse로 변환
        return auctions.map(auction ->
                AdminAuctionResponse.from(auction, sellerNicknameMap.get(auction.getSellerId()))
        );
    }
}
