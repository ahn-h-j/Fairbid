package com.cos.fairbid.bid.application.port.out;

import com.cos.fairbid.bid.domain.Bid;

/**
 * 입찰 RDB 동기화 스트림 발행 포트
 *
 * 입찰 처리 후 RDB 동기화가 필요한 데이터를 메시지 큐(Redis Stream)에 발행한다.
 * Consumer가 이 메시지를 소비하여 RDB에 저장하므로, 입찰 API 응답 경로에서
 * DB I/O가 완전히 분리된다.
 *
 * @Async 대비 장점:
 * - 디스크 기반 영속성 (앱 종료 시에도 메시지 유실 없음)
 * - DB 장애 시 호출 스레드 블로킹 없음 (CallerRunsPolicy 문제 해결)
 * - 실패 메시지 자동 재처리 (PENDING 메커니즘)
 */
public interface BidStreamPort {

    /**
     * 입찰 이력 저장 메시지를 스트림에 발행한다.
     *
     * @param bid 저장할 입찰 도메인 객체
     * @return 발행된 메시지의 Redis Stream Record ID (null이면 발행 실패)
     */
    String publishBidSave(Bid bid);

    /**
     * 즉시 구매 활성화 업데이트 메시지를 스트림에 발행한다.
     *
     * @param auctionId          경매 ID
     * @param currentPrice       새 현재가
     * @param totalBidCount      새 총 입찰수
     * @param bidIncrement       새 입찰 단위
     * @param bidderId           즉시 구매 요청자 ID
     * @param currentTimeMs      즉시 구매 활성화 시간 (밀리초)
     * @param scheduledEndTimeMs 새 종료 예정 시간 (밀리초)
     * @return 발행된 메시지의 Redis Stream Record ID (null이면 발행 실패)
     */
    String publishInstantBuyUpdate(
            Long auctionId, Long currentPrice, Integer totalBidCount,
            Long bidIncrement, Long bidderId, Long currentTimeMs, Long scheduledEndTimeMs
    );
}
