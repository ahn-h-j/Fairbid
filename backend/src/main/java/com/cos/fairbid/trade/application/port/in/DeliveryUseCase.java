package com.cos.fairbid.trade.application.port.in;

import com.cos.fairbid.trade.domain.DeliveryInfo;

import java.util.Optional;

/**
 * 택배 유스케이스
 * 택배 배송 관련 인바운드 포트
 */
public interface DeliveryUseCase {

    /**
     * 배송 정보를 조회한다
     *
     * @param tradeId 거래 ID
     * @return 배송 정보 (Optional)
     */
    Optional<DeliveryInfo> findByTradeId(Long tradeId);

    /**
     * 배송지 정보를 입력한다 (구매자)
     *
     * @param tradeId        거래 ID
     * @param userId         요청자 ID (구매자)
     * @param recipientName  수령인 이름
     * @param recipientPhone 수령인 연락처
     * @param postalCode     우편번호
     * @param address        주소
     * @param addressDetail  상세주소
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 구매자가 아닌 경우
     * @throws com.cos.fairbid.trade.domain.exception.InvalidTradeStatusException 택배 거래가 아닌 경우
     */
    DeliveryInfo submitAddress(
            Long tradeId,
            Long userId,
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail
    );

    /**
     * 발송 정보를 입력한다 (판매자)
     *
     * @param tradeId        거래 ID
     * @param userId         요청자 ID (판매자)
     * @param courierCompany 택배사
     * @param trackingNumber 송장번호
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 판매자가 아닌 경우
     */
    DeliveryInfo ship(Long tradeId, Long userId, String courierCompany, String trackingNumber);

    /**
     * 수령을 확인한다 (구매자)
     *
     * @param tradeId 거래 ID
     * @param userId  요청자 ID (구매자)
     * @return 업데이트된 배송 정보
     * @throws com.cos.fairbid.trade.domain.exception.TradeNotFoundException 거래가 없는 경우
     * @throws com.cos.fairbid.trade.domain.exception.NotTradeParticipantException 구매자가 아닌 경우
     */
    DeliveryInfo confirmDelivery(Long tradeId, Long userId);
}
