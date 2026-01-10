package com.cos.fairbid.auction.adapter.out.persistence.repository;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 경매 JPA Repository
 * Spring Data JPA 인터페이스
 */
public interface JpaAuctionRepository extends JpaRepository<AuctionEntity, Long> {
}
