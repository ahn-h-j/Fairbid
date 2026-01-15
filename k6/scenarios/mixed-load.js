/**
 * 복합 부하 테스트
 *
 * 시나리오: 경매 목록 조회 + 입찰 + WebSocket 구독을 동시에
 * 목적: 실제 사용 패턴 시뮬레이션, 전체 시스템 성능 측정
 *
 * 실행: k6 run k6/scenarios/mixed-load.js
 */

import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, WS_URL, getHeaders, randomUserId, generateBidAmount } from './config.js';

// 커스텀 메트릭
const auctionListRequests = new Counter('auction_list_requests');
const auctionDetailRequests = new Counter('auction_detail_requests');
const bidRequests = new Counter('bid_requests');
const bidSuccess = new Counter('bid_success');

// 테스트 설정
export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        // 경매 목록 조회 (읽기 부하) - 가장 많은 트래픽
        browse_auctions: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 30 },
                { duration: '2m', target: 100 },
                { duration: '3m', target: 100 },
                { duration: '30s', target: 0 },
            ],
            exec: 'browseAuctions',
            gracefulRampDown: '10s',
        },

        // 입찰 (쓰기 부하) - 적은 트래픽이지만 높은 경합
        place_bids: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 5 },
                { duration: '2m', target: 20 },
                { duration: '3m', target: 20 },
                { duration: '30s', target: 0 },
            ],
            exec: 'placeBids',
            gracefulRampDown: '10s',
        },

        // WebSocket 연결 (실시간 구독)
        websocket_subscribe: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },
                { duration: '2m', target: 50 },
                { duration: '3m', target: 50 },
                { duration: '30s', target: 0 },
            ],
            exec: 'subscribeAuction',
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        // 전체 HTTP 요청
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.05'],

        // 경매 목록 조회
        'http_req_duration{scenario:browse_auctions}': ['p(95)<300'],

        // 입찰
        'http_req_duration{scenario:place_bids}': ['p(95)<500'],

        // WebSocket
        ws_connecting: ['p(95)<2000'],
    },
};

/**
 * 테스트 전 셋업: 테스트용 경매 생성
 */
export function setup() {
    console.log('🚀 복합 테스트 셋업 시작...');

    const sellerId = 9999;
    const headers = getHeaders(sellerId);

    // 테스트용 경매 생성
    const auctionPayload = JSON.stringify({
        title: `복합테스트 경매 ${Date.now()}`,
        description: '복합 부하테스트용 경매입니다.',
        category: 'ELECTRONICS',
        startPrice: 10000,
        instantBuyPrice: 1000000,
        duration: 'HOURS_24',
    });

    const res = http.post(`${BASE_URL}/api/v1/auctions`, auctionPayload, { headers });

    if (res.status === 200 || res.status === 201) {
        const body = JSON.parse(res.body);
        if (body.success && body.data) {
            console.log(`✅ 경매 생성 완료: ID=${body.data.id}`);
            return { auctionId: body.data.id };
        }
    }

    // 실패 시 기존 경매 사용
    const listRes = http.get(`${BASE_URL}/api/v1/auctions?status=BIDDING&page=0&size=1`);
    if (listRes.status === 200) {
        const listBody = JSON.parse(listRes.body);
        if (listBody.success && listBody.data?.content?.length > 0) {
            const auctionId = listBody.data.content[0].id;
            console.log(`📌 기존 경매 사용: ID=${auctionId}`);
            return { auctionId };
        }
    }

    throw new Error('테스트용 경매를 생성하거나 찾을 수 없습니다.');
}

/**
 * 시나리오 1: 경매 목록 브라우징
 * 실제 사용자가 경매 목록을 조회하고 상세 페이지를 보는 패턴
 */
export function browseAuctions(data) {
    const userId = randomUserId();
    const headers = getHeaders(userId);

    group('경매 목록 조회', function () {
        // 경매 목록 조회
        const listRes = http.get(`${BASE_URL}/api/v1/auctions?status=BIDDING&page=0&size=20`, {
            headers,
            tags: { name: 'get_auctions' },
        });
        auctionListRequests.add(1);

        check(listRes, {
            'auction list status 200': (r) => r.status === 200,
        });

        // 목록에서 랜덤하게 상세 조회
        if (listRes.status === 200) {
            try {
                const body = JSON.parse(listRes.body);
                if (body.success && body.data && body.data.content && body.data.content.length > 0) {
                    const auctions = body.data.content;
                    const randomAuction = auctions[Math.floor(Math.random() * auctions.length)];

                    sleep(Math.random() * 2 + 1); // 1-3초 대기 (사용자 행동 시뮬레이션)

                    // 경매 상세 조회
                    const detailRes = http.get(`${BASE_URL}/api/v1/auctions/${randomAuction.id}`, {
                        headers,
                        tags: { name: 'get_auction_detail' },
                    });
                    auctionDetailRequests.add(1);

                    check(detailRes, {
                        'auction detail status 200': (r) => r.status === 200,
                    });
                }
            } catch {
                // 파싱 실패 무시
            }
        }
    });

    sleep(Math.random() * 3 + 2); // 2-5초 대기
}

/**
 * 시나리오 2: 입찰
 * 경매 상세 조회 후 입찰하는 패턴
 */
export function placeBids(data) {
    const auctionId = data.auctionId;
    const userId = randomUserId();
    const headers = getHeaders(userId);

    group('입찰 프로세스', function () {
        // 경매 상세 조회 (현재가 확인)
        const detailRes = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, {
            headers,
            tags: { name: 'get_auction_for_bid' },
        });

        let bidAmount = 10000;
        if (detailRes.status === 200) {
            try {
                const body = JSON.parse(detailRes.body);
                if (body.success && body.data) {
                    bidAmount = generateBidAmount(body.data.currentPrice, body.data.bidIncrement);
                }
            } catch {
                // 기본값 사용
            }
        }

        sleep(Math.random() * 2 + 0.5); // 0.5-2.5초 대기 (입찰 결정 시간)

        // 입찰 요청 (ONE_TOUCH: 서버가 최소 입찰가 자동 계산)
        const bidPayload = JSON.stringify({
            amount: bidAmount,  // ONE_TOUCH에서는 무시됨
            bidType: 'ONE_TOUCH',
        });

        const bidRes = http.post(
            `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
            bidPayload,
            {
                headers,
                tags: { name: 'place_bid' },
            }
        );
        bidRequests.add(1);

        const success = check(bidRes, {
            'bid request completed': (r) => r.status === 200 || r.status === 201 || r.status === 400,
        });

        if (bidRes.status === 200 || bidRes.status === 201) {
            try {
                const body = JSON.parse(bidRes.body);
                if (body.success) {
                    bidSuccess.add(1);
                }
            } catch {
                // 무시
            }
        }
    });

    sleep(Math.random() * 5 + 3); // 3-8초 대기 (다음 입찰까지)
}

/**
 * 시나리오 3: WebSocket 구독
 * 경매 실시간 업데이트 구독
 */
export function subscribeAuction(data) {
    const auctionId = data.auctionId;
    const url = `${WS_URL}/websocket`;
    const destination = `/topic/auctions/${auctionId}`;

    const res = ws.connect(url, {}, function (socket) {
        socket.on('open', function () {
            // STOMP CONNECT
            socket.send('CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\x00');
        });

        socket.on('message', function (message) {
            if (message.startsWith('CONNECTED')) {
                // STOMP SUBSCRIBE
                socket.send(`SUBSCRIBE\nid:sub-${__VU}\ndestination:${destination}\n\n\x00`);
            }
            // 메시지 수신 (로깅만)
        });

        socket.on('error', function (e) {
            console.error(`WebSocket error: ${e.error()}`);
        });

        // 30초간 연결 유지
        socket.setTimeout(function () {
            socket.close();
        }, 30000);
    });

    check(res, {
        'WebSocket connected': (r) => r && r.status === 101,
    });

    sleep(1);
}

// 테스트 요약
export function handleSummary(data) {
    return {
        'stdout': textSummary(data),
        'k6/results/mixed-load-result.json': JSON.stringify(data, null, 2),
    };
}

function textSummary(data) {
    const metrics = data.metrics;
    return `
========================================
🔀 복합 부하 테스트 결과
========================================

📊 요청 통계
- 경매 목록 조회: ${metrics.auction_list_requests?.values?.count || 0}
- 경매 상세 조회: ${metrics.auction_detail_requests?.values?.count || 0}
- 입찰 요청: ${metrics.bid_requests?.values?.count || 0}
- 성공한 입찰: ${metrics.bid_success?.values?.count || 0}

⏱️ 전체 응답 시간
- 평균: ${(metrics.http_req_duration?.values?.avg || 0).toFixed(2)}ms
- p95: ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
- p99: ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms

❌ 에러율
- HTTP 에러율: ${((metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%

🔗 WebSocket
- 연결 시도: ${metrics.ws_sessions?.values?.count || 0}

========================================
`;
}
