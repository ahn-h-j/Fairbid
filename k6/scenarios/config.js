/**
 * k6 부하테스트 공통 설정
 * FairBid 프로젝트
 */

// 환경 변수 또는 기본값
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws';

// 테스트용 사용자 ID 범위
export const USER_ID_START = 1;
export const USER_ID_END = 1000;

// 공통 헤더
export function getHeaders(userId) {
    return {
        'Content-Type': 'application/json',
        'X-User-Id': String(userId),  // 테스트용 인증 헤더
    };
}

// 랜덤 사용자 ID 생성
export function randomUserId() {
    return Math.floor(Math.random() * (USER_ID_END - USER_ID_START + 1)) + USER_ID_START;
}

// 입찰 금액 생성 (현재가 기준 증가)
export function generateBidAmount(currentPrice, bidIncrement) {
    // 최소 입찰 단위의 1~3배 랜덤 증가
    const multiplier = Math.floor(Math.random() * 3) + 1;
    return currentPrice + (bidIncrement * multiplier);
}

// 성능 임계값 (SLA)
export const thresholds = {
    // HTTP 요청 성능
    http_req_duration: ['p(95)<500', 'p(99)<1000'],  // 95%가 500ms 이내, 99%가 1s 이내
    http_req_failed: ['rate<0.01'],                   // 에러율 1% 미만

    // 입찰 API 성능
    'http_req_duration{name:place_bid}': ['p(95)<200', 'p(99)<500'],

    // 경매 목록 조회 성능
    'http_req_duration{name:get_auctions}': ['p(95)<300', 'p(99)<500'],

    // WebSocket 연결 성능
    'ws_connecting': ['p(95)<1000'],
};
