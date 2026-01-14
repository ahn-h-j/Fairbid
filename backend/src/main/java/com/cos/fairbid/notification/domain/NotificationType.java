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
            return String.format("[%s] %,d원에 낙찰되었습니다. 3시간 내에 결제해주세요.", auctionTitle, amount);
        }
    },

    TRANSFER {
        @Override
        public String getTitle() {
            return "낙찰 기회가 생겼습니다!";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 2순위로 낙찰 권한이 승계되었습니다. 1시간 내에 결제해주세요.", auctionTitle);
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
