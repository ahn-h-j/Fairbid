package com.cos.fairbid.auction.adapter.out.persistence;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.adapter.out.persistence.mapper.AuctionMapper;
import com.cos.fairbid.auction.adapter.out.persistence.repository.JpaAuctionRepository;
import com.cos.fairbid.auction.application.port.out.AuctionRepository;
import com.cos.fairbid.auction.domain.Auction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 경매 영속성 어댑터
 * AuctionRepository 포트 구현체
 */
@Repository
@RequiredArgsConstructor
public class AuctionPersistenceAdapter implements AuctionRepository {

    private final JpaAuctionRepository jpaAuctionRepository;
    private final AuctionMapper auctionMapper;

    @Override
    public Auction save(Auction auction) {
        AuctionEntity entity = auctionMapper.toEntity(auction);
        AuctionEntity savedEntity = jpaAuctionRepository.save(entity);
        return auctionMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Auction> findById(Long id) {
        return jpaAuctionRepository.findById(id)
                .map(auctionMapper::toDomain);
    }

    @Override
    public Optional<Auction> findByIdWithLock(Long id) {
        return jpaAuctionRepository.findByIdWithLock(id)
                .map(auctionMapper::toDomain);
    }
}
