/**
 * 일정 부하 테스트
 *
 * 시나리오: 100명이 동시에 2분간 지속적으로 입찰
 * 목적: 특정 동시 사용자 수에서 안정적 성능 측정
 *
 * 실행: k6 run k6/scenarios/bid-constant.js
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
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        // 일정 부하: 100명이 2분간 동시 입찰
        constant_load: {
            executor: 'constant-vus',
            vus: 100,
            duration: '2m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.05'],
        bid_error_rate: ['rate<0.1'],
    },
};

/**
 * 테스트 전 셋업: 테스트용 경매 생성
 */
export function setup() {
    console.log('🚀 일정 부하 테스트 셋업 시작...');

    const sellerId = 9999;
    const headers = getHeaders(sellerId);

    const auctionPayload = JSON.stringify({
        title: `일정부하테스트 경매 ${Date.now()}`,
        description: '일정 부하 테스트용 경매입니다.',
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

// 메인 테스트 함수
export default function (data) {
    const auctionId = data.auctionId;
    const userId = randomUserId();
    const headers = getHeaders(userId);

    // 현재 경매 정보 조회
    const infoRes = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, { headers });

    let bidAmount = 10000;
    if (infoRes.status === 200) {
        const info = JSON.parse(infoRes.body);
        if (info.success && info.data) {
            bidAmount = info.data.currentPrice + info.data.bidIncrement;
        }
    }

    // 입찰 요청
    const bidPayload = JSON.stringify({
        amount: bidAmount,
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
    });

    if (success && (res.status === 200 || res.status === 201)) {
        const body = JSON.parse(res.body);
        if (body.success) {
            bidSuccess.add(1);
            bidErrorRate.add(0);
        } else {
            bidFailed.add(1);
            bidErrorRate.add(1);
        }
    } else {
        bidFailed.add(1);
        bidErrorRate.add(1);
    }

    sleep(Math.random() * 1 + 0.5); // 0.5~1.5초
}

// 테스트 요약
export function handleSummary(data) {
    return {
        'stdout': textSummary(data),
        'k6/results/bid-constant-result.json': JSON.stringify(data, null, 2),
    };
}

function textSummary(data) {
    const metrics = data.metrics;
    return `
========================================
📊 일정 부하 테스트 결과 (100 VUs, 2분)
========================================

📈 요청 통계
- 총 요청 수: ${metrics.http_reqs?.values?.count || 0}
- 성공한 입찰: ${metrics.bid_success?.values?.count || 0}
- 실패한 입찰: ${metrics.bid_failed?.values?.count || 0}

⏱️ 응답 시간
- 평균: ${(metrics.http_req_duration?.values?.avg || 0).toFixed(2)}ms
- p90: ${(metrics.http_req_duration?.values?.['p(90)'] || 0).toFixed(2)}ms
- p95: ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
- p99: ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms
- 최대: ${(metrics.http_req_duration?.values?.max || 0).toFixed(2)}ms

❌ 에러율
- HTTP 에러율: ${((metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%
- 입찰 에러율: ${((metrics.bid_error_rate?.values?.rate || 0) * 100).toFixed(2)}%

========================================
`;
}
