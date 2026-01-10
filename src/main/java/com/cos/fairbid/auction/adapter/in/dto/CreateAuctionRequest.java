package com.cos.fairbid.auction.adapter.in.dto;

import com.cos.fairbid.auction.application.port.in.CreateAuctionUseCase.CreateAuctionCommand;
import com.cos.fairbid.auction.domain.AuctionDuration;
import com.cos.fairbid.auction.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.util.List;

/**
 * 경매 생성 요청 DTO
 */
@Builder
public record CreateAuctionRequest(
        @NotBlank(message = "제목은 필수입니다")
        String title,

        String description,

        Category category,

        @NotNull(message = "시작가는 필수입니다")
        @Positive(message = "시작가는 0보다 커야 합니다")
        Long startPrice,

        @Positive(message = "즉시구매가는 0보다 커야 합니다")
        Long instantBuyPrice,

        @NotNull(message = "경매 기간은 필수입니다")
        AuctionDuration duration,

        List<String> imageUrls
) {
    /**
     * Request DTO → Command 변환
     *
     * @param sellerId 판매자 ID (인증 정보에서 추출)
     * @return CreateAuctionCommand
     */
    public CreateAuctionCommand toCommand(Long sellerId) {
        return CreateAuctionCommand.builder()
                .sellerId(sellerId)
                .title(title)
                .description(description)
                .category(category)
                .startPrice(startPrice)
                .instantBuyPrice(instantBuyPrice)
                .duration(duration)
                .imageUrls(imageUrls != null ? imageUrls : List.of())
                .build();
    }
}
