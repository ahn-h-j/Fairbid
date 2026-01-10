package com.cos.fairbid.bid.adapter.out.persistence;

import com.cos.fairbid.bid.adapter.out.persistence.entity.BidEntity;
import com.cos.fairbid.bid.adapter.out.persistence.mapper.BidMapper;
import com.cos.fairbid.bid.adapter.out.persistence.repository.JpaBidRepository;
import com.cos.fairbid.bid.application.port.out.BidRepository;
import com.cos.fairbid.bid.domain.Bid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 입찰 영속성 어댑터
 * BidRepository 포트 구현체
 */
@Repository
@RequiredArgsConstructor
public class BidPersistenceAdapter implements BidRepository {

    private final JpaBidRepository jpaBidRepository;
    private final BidMapper bidMapper;

    @Override
    public Bid save(Bid bid) {
        BidEntity entity = bidMapper.toEntity(bid);
        BidEntity savedEntity = jpaBidRepository.save(entity);
        return bidMapper.toDomain(savedEntity);
    }
}
