package com.cos.fairbid.trade.domain;

import lombok.Builder;
import lombok.Getter;

/**
 * 택배 배송 정보 도메인 모델
 * 택배 거래 시 배송 정보를 관리한다.
 */
@Getter
@Builder
public class DeliveryInfo {

    private Long id;
    private Long tradeId;
    private String recipientName;       // 수령인 이름
    private String recipientPhone;      // 수령인 연락처
    private String postalCode;          // 우편번호
    private String address;             // 주소
    private String addressDetail;       // 상세주소
    private String courierCompany;      // 택배사
    private String trackingNumber;      // 송장번호
    private DeliveryStatus status;      // 배송 상태

    /**
     * 새로운 택배 정보 생성 (거래 시작 시)
     *
     * @param tradeId 거래 ID
     * @return 생성된 DeliveryInfo
     */
    public static DeliveryInfo create(Long tradeId) {
        return DeliveryInfo.builder()
                .tradeId(tradeId)
                .status(DeliveryStatus.AWAITING_ADDRESS)
                .build();
    }

    /**
     * 영속성 계층에서 조회한 데이터로 도메인 객체 복원
     */
    public static DeliveryInfoBuilder reconstitute() {
        return DeliveryInfo.builder();
    }

    // =====================================================
    // 비즈니스 로직 메서드
    // =====================================================

    /**
     * 배송지 정보를 입력한다 (구매자)
     *
     * @param recipientName  수령인 이름
     * @param recipientPhone 수령인 연락처
     * @param postalCode     우편번호
     * @param address        주소
     * @param addressDetail  상세주소
     */
    public void submitAddress(
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail
    ) {
        if (this.status != DeliveryStatus.AWAITING_ADDRESS) {
            throw new IllegalStateException("배송지 입력 대기 상태에서만 입력 가능합니다. 현재 상태: " + this.status);
        }
        validateAddress(recipientName, recipientPhone, postalCode, address);

        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.postalCode = postalCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.status = DeliveryStatus.ADDRESS_SUBMITTED;
    }

    /**
     * 발송 정보를 입력한다 (판매자)
     *
     * @param courierCompany 택배사
     * @param trackingNumber 송장번호
     */
    public void ship(String courierCompany, String trackingNumber) {
        if (this.status != DeliveryStatus.ADDRESS_SUBMITTED) {
            throw new IllegalStateException("배송지 입력 완료 상태에서만 발송 가능합니다. 현재 상태: " + this.status);
        }
        if (courierCompany == null || courierCompany.isBlank()) {
            throw new IllegalArgumentException("택배사는 필수입니다.");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("송장번호는 필수입니다.");
        }

        this.courierCompany = courierCompany;
        this.trackingNumber = trackingNumber;
        this.status = DeliveryStatus.SHIPPED;
    }

    /**
     * 수령을 확인한다 (구매자)
     */
    public void confirmDelivery() {
        if (this.status != DeliveryStatus.SHIPPED) {
            throw new IllegalStateException("발송 완료 상태에서만 수령 확인 가능합니다. 현재 상태: " + this.status);
        }
        this.status = DeliveryStatus.DELIVERED;
    }

    /**
     * 발송이 완료되었는지 확인한다
     */
    public boolean isShipped() {
        return this.status == DeliveryStatus.SHIPPED || this.status == DeliveryStatus.DELIVERED;
    }

    /**
     * 배송이 완료되었는지 확인한다
     */
    public boolean isDelivered() {
        return this.status == DeliveryStatus.DELIVERED;
    }

    /**
     * 배송지 입력이 완료되었는지 확인한다
     */
    public boolean isAddressSubmitted() {
        return this.status != DeliveryStatus.AWAITING_ADDRESS;
    }

    /**
     * 배송지 정보 유효성 검증
     */
    private void validateAddress(String name, String phone, String postalCode, String address) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("수령인 이름은 필수입니다.");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("수령인 연락처는 필수입니다.");
        }
        if (postalCode == null || postalCode.isBlank()) {
            throw new IllegalArgumentException("우편번호는 필수입니다.");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }
    }
}
