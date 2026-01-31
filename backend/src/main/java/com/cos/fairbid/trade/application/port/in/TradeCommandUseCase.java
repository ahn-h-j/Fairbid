package com.cos.fairbid.trade.application.port.in;

import com.cos.fairbid.trade.domain.Trade;
import com.cos.fairbid.trade.domain.TradeMethod;

/**
 * 거래 명령 유스케이스
 * 거래 상태 변경 관련 인바운드 포트
 */
public interface TradeCommandUseCase {

    /**
     * 거래 방식을 선택한다 (둘 다 가능한 경우 구매자가 호출)
     *
     * @param tradeId 거래 ID
     * @param userId  요청자 ID (구매자)
     * @param method  선택한 거래 방식
     * @return 업데이트된 거래
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 구매자가 아닌 경우
     * @throws com.cos.fairbid.trade.domain.exception.InvalidTradeStatusException 거래 방식 선택 대기 상태가 아닌 경우
     */
    Trade selectMethod(Long tradeId, Long userId, TradeMethod method);

    /**
     * 거래를 완료한다
     *
     * @param tradeId 거래 ID
     * @param userId  요청자 ID (판매자 또는 구매자)
     * @return 업데이트된 거래
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 거래 참여자가 아닌 경우
     * @throws com.cos.fairbid.trade.domain.exception.InvalidTradeStatusException 거래 완료 처리가 불가능한 상태인 경우
     */
    Trade complete(Long tradeId, Long userId);
}
