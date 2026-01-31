package com.cos.fairbid.trade.adapter.out.persistence.repository;

import com.cos.fairbid.trade.adapter.out.persistence.entity.DeliveryInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 택배 배송 정보 JPA Repository
 */
public interface DeliveryInfoJpaRepository extends JpaRepository<DeliveryInfoEntity, Long> {

    /**
     * 거래 ID로 배송 정보 조회
     */
    Optional<DeliveryInfoEntity> findByTradeId(Long tradeId);
}
