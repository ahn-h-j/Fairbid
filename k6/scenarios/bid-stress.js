/**
 * 동시 입찰 경합 테스트
 *
 * 시나리오: 여러 사용자가 하나의 경매에 동시 입찰
 * 목적: DB 락 경합, 동시성 제어 성능 측정
 *
 * 실행: k6 run k6/scenarios/bid-stress.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, getHeaders, randomUserId } from './config.js';

// 커스텀 메트릭
const bidSuccess = new Counter('bid_success');
const bidFailed = new Counter('bid_failed');
const bidErrorRate = new Rate('bid_error_rate');
const bidDuration = new Trend('bid_duration', true);

// 테스트 설정
export const options = {
    // p(99) 등 percentile 계산을 위해 명시
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        // 점진적 부하 증가 (총 3분)
        ramp_up: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 50 },   // 20초 동안 50명까지
                { duration: '30s', target: 100 },  // 30초 동안 100명까지
                { duration: '1m', target: 150 },   // 1분 동안 150명까지
                { duration: '50s', target: 150 },  // 50초 동안 150명 유지
                { duration: '20s', target: 0 },    // 20초 동안 종료
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.05'],  // 입찰 경합이므로 5%까지 허용
        bid_error_rate: ['rate<0.1'],     // 비즈니스 에러(경합 패배 등) 10%까지 허용
    },
};

/**
 * 테스트 전 셋업: 테스트용 경매 생성
 */
export function setup() {
    console.log('🚀 테스트 셋업 시작: 경매 생성 중...');

    const sellerId = 9999; // 판매자 ID (입찰자와 다른 ID)
    const headers = getHeaders(sellerId);

    // 테스트용 경매 생성
    const auctionPayload = JSON.stringify({
        title: `부하테스트 경매 ${Date.now()}`,
        description: '동시 입찰 경합 테스트용 경매입니다.',
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

    console.error(`❌ 경매 생성 실패: ${res.status} - ${res.body}`);
    // 실패 시 기존 경매 목록에서 가져오기 시도
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

// 메인 테스트 함수
export default function (data) {
    const auctionId = data.auctionId;
    const userId = randomUserId();
    const headers = getHeaders(userId);

    // 현재 경매 정보 조회 (입찰 금액 계산용)
    const infoRes = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, { headers });

    let bidAmount = 10000; // 기본값
    if (infoRes.status === 200) {
        const info = JSON.parse(infoRes.body);
        if (info.success && info.data) {
            // 현재가 + 입찰단위
            bidAmount = info.data.currentPrice + info.data.bidIncrement;
        }
    }

    // 입찰 요청 (ONE_TOUCH: 서버가 최소 입찰가 자동 계산)
    const bidPayload = JSON.stringify({
        amount: bidAmount,  // ONE_TOUCH에서는 무시됨
        bidType: 'ONE_TOUCH',
    });

    const startTime = Date.now();
    const res = http.post(
        `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
        bidPayload,
        {
            headers,
            tags: { name: 'place_bid' },
        }
    );
    const duration = Date.now() - startTime;
    bidDuration.add(duration);

    // 결과 체크
    const success = check(res, {
        'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
        'response has success field': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.hasOwnProperty('success');
            } catch {
                return false;
            }
        },
    });

    if (success && res.status === 200 || res.status === 201) {
        const body = JSON.parse(res.body);
        if (body.success) {
            bidSuccess.add(1);
            bidErrorRate.add(0);
        } else {
            // 비즈니스 에러 (입찰 경합 패배 등)
            bidFailed.add(1);
            bidErrorRate.add(1);
        }
    } else {
        bidFailed.add(1);
        bidErrorRate.add(1);
    }

    // 입찰 간 짧은 대기 (실제 사용자 시뮬레이션)
    sleep(Math.random() * 2 + 0.5); // 0.5~2.5초
}

// 테스트 요약
export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'k6/results/bid-stress-result.json': JSON.stringify(data, null, 2),
    };
}

function textSummary(data, options) {
    const metrics = data.metrics;
    return `
========================================
📊 동시 입찰 경합 테스트 결과
========================================

📈 요청 통계
- 총 요청 수: ${metrics.http_reqs?.values?.count || 0}
- 성공한 입찰: ${metrics.bid_success?.values?.count || 0}
- 실패한 입찰: ${metrics.bid_failed?.values?.count || 0}

⏱️ 응답 시간
- 평균: ${(metrics.http_req_duration?.values?.avg || 0).toFixed(2)}ms
- p95: ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
- p99: ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms
- 최대: ${(metrics.http_req_duration?.values?.max || 0).toFixed(2)}ms

❌ 에러율
- HTTP 에러율: ${((metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%
- 입찰 에러율: ${((metrics.bid_error_rate?.values?.rate || 0) * 100).toFixed(2)}%

========================================
`;
}
