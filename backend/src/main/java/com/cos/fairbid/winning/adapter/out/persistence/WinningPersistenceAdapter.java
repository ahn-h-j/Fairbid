package com.cos.fairbid.winning.adapter.out.persistence;

import com.cos.fairbid.winning.adapter.out.persistence.entity.WinningEntity;
import com.cos.fairbid.winning.adapter.out.persistence.mapper.WinningMapper;
import com.cos.fairbid.winning.adapter.out.persistence.repository.JpaWinningRepository;
import com.cos.fairbid.winning.application.port.out.WinningRepository;
import com.cos.fairbid.winning.domain.Winning;
import com.cos.fairbid.winning.domain.WinningStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 낙찰 영속성 어댑터
 * WinningRepository 포트 구현체
 */
@Repository
@RequiredArgsConstructor
public class WinningPersistenceAdapter implements WinningRepository {

    private final JpaWinningRepository jpaWinningRepository;
    private final WinningMapper winningMapper;

    @Override
    public Winning save(Winning winning) {
        WinningEntity entity = winningMapper.toEntity(winning);
        WinningEntity savedEntity = jpaWinningRepository.save(entity);
        return winningMapper.toDomain(savedEntity);
    }

    @Override
    public List<Winning> findByAuctionId(Long auctionId) {
        return jpaWinningRepository.findByAuctionId(auctionId)
                .stream()
                .map(winningMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Winning> findByAuctionIdAndRank(Long auctionId, Integer rank) {
        return jpaWinningRepository.findByAuctionIdAndRank(auctionId, rank)
                .map(winningMapper::toDomain);
    }

    @Override
    public List<Winning> findExpiredPendingPayments() {
        return jpaWinningRepository.findExpiredPendingPayments(
                        WinningStatus.PENDING_PAYMENT,
                        LocalDateTime.now()
                )
                .stream()
                .map(winningMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Winning> findById(Long id) {
        return jpaWinningRepository.findById(id)
                .map(winningMapper::toDomain);
    }
}
