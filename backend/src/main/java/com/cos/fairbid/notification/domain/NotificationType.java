package com.cos.fairbid.notification.domain;

/**
 * 알림 유형 enum
 * 각 유형별 제목과 본문 생성 책임을 가짐 (전략 패턴)
 */
public enum NotificationType {

    WINNING {
        @Override
        public String getTitle() {
            return "축하합니다! 낙찰되었습니다 🎉";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] %,d원에 낙찰되었습니다. 24시간 내에 거래를 진행해주세요.", auctionTitle, amount);
        }
    },

    TRANSFER {
        @Override
        public String getTitle() {
            return "낙찰 기회가 생겼습니다!";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 2순위로 낙찰 권한이 승계되었습니다. 12시간 내에 응답해주세요.", auctionTitle);
        }
    },

    FAILED {
        @Override
        public String getTitle() {
            return "경매가 유찰되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 경매가 유찰되었습니다. 재등록을 고려해보세요.", auctionTitle);
        }
    },

    PAYMENT_COMPLETED {
        @Override
        public String getTitle() {
            return "결제가 완료되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("%,d원 결제가 완료되었습니다.", amount);
        }
    },

    PAYMENT_REMINDER {
        @Override
        public String getTitle() {
            return "결제 마감이 임박했습니다!";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 결제 마감까지 1시간 남았습니다. %,d원을 결제해주세요.", auctionTitle, amount);
        }
    },

    RESPONSE_REMINDER {
        @Override
        public String getTitle() {
            return "거래 응답 기한이 임박했습니다!";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 거래 응답 마감까지 12시간 남았습니다. 거래를 진행해주세요.", auctionTitle);
        }
    },

    SECOND_RANK_STANDBY {
        @Override
        public String getTitle() {
            return "2순위 후보로 등록되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 경매가 종료되었습니다. 귀하는 %,d원으로 2순위입니다. 1순위 미응답 시 자동 승계됩니다.", auctionTitle, amount);
        }
    },

    /**
     * 노쇼 처리됨 (노쇼 당한 사람에게)
     */
    NO_SHOW_PENALTY {
        @Override
        public String getTitle() {
            return "노쇼 처리되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 응답 기한이 만료되어 노쇼 처리되었습니다. 경고 1회가 부여되었습니다.", auctionTitle);
        }
    },

    /**
     * 거래 방식 선택됨 (판매자에게)
     */
    METHOD_SELECTED {
        @Override
        public String getTitle() {
            return "구매자가 거래 방식을 선택했습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            // amount 파라미터를 method 구분용으로 사용 (1=직거래, 2=택배)
            String method = (amount != null && amount == 1) ? "직거래" : "택배";
            return String.format("[%s] 구매자가 %s 거래를 선택했습니다. 거래를 진행해주세요.", auctionTitle, method);
        }
    },

    /**
     * 거래 일정 제안됨 (상대방에게)
     */
    ARRANGEMENT_PROPOSED {
        @Override
        public String getTitle() {
            return "거래 일정이 제안되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 새로운 거래 일정이 제안되었습니다. 확인해주세요.", auctionTitle);
        }
    },

    /**
     * 거래 일정 역제안됨 (상대방에게)
     */
    ARRANGEMENT_COUNTER_PROPOSED {
        @Override
        public String getTitle() {
            return "거래 일정 역제안이 있습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 상대방이 다른 일정을 제안했습니다. 확인해주세요.", auctionTitle);
        }
    },

    /**
     * 거래 일정 수락됨 (상대방에게)
     */
    ARRANGEMENT_ACCEPTED {
        @Override
        public String getTitle() {
            return "거래 일정이 확정되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 거래 일정이 확정되었습니다. 약속 시간에 만나주세요.", auctionTitle);
        }
    },

    /**
     * 배송지 입력됨 (판매자에게)
     */
    DELIVERY_ADDRESS_SUBMITTED {
        @Override
        public String getTitle() {
            return "배송지가 입력되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 구매자가 배송지를 입력했습니다. 발송을 진행해주세요.", auctionTitle);
        }
    },

    /**
     * 발송 완료됨 (구매자에게)
     */
    DELIVERY_SHIPPED {
        @Override
        public String getTitle() {
            return "상품이 발송되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 판매자가 상품을 발송했습니다. 송장 정보를 확인해주세요.", auctionTitle);
        }
    },

    /**
     * 거래 완료됨 (양쪽에게)
     */
    TRADE_COMPLETED {
        @Override
        public String getTitle() {
            return "거래가 완료되었습니다";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 거래가 성공적으로 완료되었습니다. 이용해주셔서 감사합니다.", auctionTitle);
        }
    };

    /**
     * 알림 제목 반환
     */
    public abstract String getTitle();

    /**
     * 알림 본문 생성
     *
     * @param auctionTitle 경매 제목
     * @param amount       금액 (유형에 따라 사용 여부 다름)
     * @return 포맷된 본문 문자열
     */
    public abstract String formatBody(String auctionTitle, Long amount);
}
