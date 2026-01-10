package com.cos.fairbid.bid.adapter.out.persistence.repository;

import com.cos.fairbid.bid.adapter.out.persistence.entity.BidEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 입찰 JPA Repository
 * Spring Data JPA 인터페이스
 */
public interface JpaBidRepository extends JpaRepository<BidEntity, Long> {
}
