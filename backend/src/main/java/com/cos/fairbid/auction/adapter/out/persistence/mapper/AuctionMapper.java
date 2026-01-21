package com.cos.fairbid.auction.adapter.out.persistence.mapper;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.domain.Auction;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 경매 Entity ↔ Domain 변환 Mapper
 */
@Component
public class AuctionMapper {

    /**
     * Domain → Entity 변환
     */
    public AuctionEntity toEntity(Auction auction) {
        return AuctionEntity.builder()
                .id(auction.getId())
                .sellerId(auction.getSellerId())
                .title(auction.getTitle())
                .description(auction.getDescription())
                .category(auction.getCategory())
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .instantBuyPrice(auction.getInstantBuyPrice())
                .bidIncrement(auction.getBidIncrement())
                .scheduledEndTime(auction.getScheduledEndTime())
                .actualEndTime(auction.getActualEndTime())
                .extensionCount(auction.getExtensionCount())
                .totalBidCount(auction.getTotalBidCount())
                .status(auction.getStatus())
                .winnerId(auction.getWinnerId())
                .instantBuyerId(auction.getInstantBuyerId())
                .instantBuyActivatedTime(auction.getInstantBuyActivatedTime())
                .createdAt(auction.getCreatedAt())
                .updatedAt(auction.getUpdatedAt())
                .build();
    }

    /**
     * Entity → Domain 변환
     */
    public Auction toDomain(AuctionEntity entity) {
        // 이미지는 모킹 처리 (추후 구현)
        return Auction.reconstitute()
                .id(entity.getId())
                .sellerId(entity.getSellerId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .startPrice(entity.getStartPrice())
                .currentPrice(entity.getCurrentPrice())
                .instantBuyPrice(entity.getInstantBuyPrice())
                .bidIncrement(entity.getBidIncrement())
                .scheduledEndTime(entity.getScheduledEndTime())
                .actualEndTime(entity.getActualEndTime())
                .extensionCount(entity.getExtensionCount())
                .totalBidCount(entity.getTotalBidCount())
                .status(entity.getStatus())
                .winnerId(entity.getWinnerId())
                .instantBuyerId(entity.getInstantBuyerId())
                .instantBuyActivatedTime(entity.getInstantBuyActivatedTime())
                .imageUrls(Collections.emptyList())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
