/**
 * FairBid WebSocket 클라이언트
 * STOMP over SockJS를 사용한 실시간 통신
 */

let stompClient = null;
let currentSubscription = null;

/**
 * WebSocket 연결
 * @param {function} onConnected - 연결 성공 콜백
 * @param {function} onError - 에러 콜백
 */
function connectWebSocket(onConnected, onError) {
    // SockJS 엔드포인트
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    // 디버그 로그 비활성화 (프로덕션용)
    stompClient.debug = null;

    stompClient.connect({},
        // 연결 성공
        function(frame) {
            console.log('WebSocket 연결 성공');
            if (onConnected) onConnected(frame);
        },
        // 연결 실패
        function(error) {
            console.error('WebSocket 연결 실패:', error);
            if (onError) onError(error);

            // 5초 후 재연결 시도
            setTimeout(() => {
                console.log('WebSocket 재연결 시도...');
                connectWebSocket(onConnected, onError);
            }, 5000);
        }
    );
}

/**
 * 경매 토픽 구독
 * @param {number} auctionId - 경매 ID
 * @param {function} onMessage - 메시지 수신 콜백
 */
function subscribeToAuction(auctionId, onMessage) {
    if (!stompClient || !stompClient.connected) {
        console.error('WebSocket이 연결되지 않았습니다.');
        return;
    }

    // 기존 구독 해제
    if (currentSubscription) {
        currentSubscription.unsubscribe();
    }

    // 새 구독
    const topic = `/topic/auctions/${auctionId}`;
    currentSubscription = stompClient.subscribe(topic, function(message) {
        const data = JSON.parse(message.body);
        console.log('WebSocket 메시지 수신:', data);

        if (onMessage) onMessage(data);
    });

    console.log(`경매 ${auctionId} 구독 시작`);
}

/**
 * 구독 해제
 */
function unsubscribeFromAuction() {
    if (currentSubscription) {
        currentSubscription.unsubscribe();
        currentSubscription = null;
        console.log('구독 해제됨');
    }
}

/**
 * WebSocket 연결 해제
 */
function disconnectWebSocket() {
    if (stompClient) {
        stompClient.disconnect(function() {
            console.log('WebSocket 연결 해제됨');
        });
        stompClient = null;
    }
}

/**
 * 메시지 타입 확인 (경매 종료 여부)
 * @param {object} message - WebSocket 메시지
 * @returns {boolean} 경매 종료 메시지인지 여부
 */
function isAuctionClosedMessage(message) {
    return message.type === 'AUCTION_CLOSED';
}

/**
 * 메시지 타입 확인 (입찰 업데이트)
 * @param {object} message - WebSocket 메시지
 * @returns {boolean} 입찰 업데이트 메시지인지 여부
 */
function isBidUpdateMessage(message) {
    return message.currentPrice !== undefined && message.type !== 'AUCTION_CLOSED';
}
