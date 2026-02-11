/**
 * 현실적 트래픽 시나리오 테스트 (Issue #62 - Phase 3 보조)
 *
 * 시나리오: 15명이 60초간 입찰 (초당 약 15~30건)
 * 목적: 실제 운영 수준 트래픽에서 MQ Consumer가 실시간 동기화하여
 *        수렴 지연이 거의 0임을 증명
 *
 * 실행: k6 run k6/scenarios/bid-realistic-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, getHeaders, randomUserId } from './config.js';

// 커스텀 메트릭
const bidSuccess = new Counter('bid_success');
const bidFailed = new Counter('bid_failed');
const bidErrorRate = new Rate('bid_error_rate');
const httpErrors = new Counter('http_5xx_errors');

// 테스트 설정: 15 VUs, 60초
export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        realistic_test: {
            executor: 'constant-vus',
            vus: 15,
            duration: '60s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        bid_error_rate: ['rate<0.1'],
    },
};

export function setup() {
    console.log('현실적 트래픽 테스트 셋업 (15 VUs, 60초)');

    const sellerId = 9999;
    const headers = getHeaders(sellerId);

    const auctionPayload = JSON.stringify({
        title: `현실트래픽 경매 ${Date.now()}`,
        description: '현실적 트래픽에서 MQ 실시간 동기화 증명용',
        category: 'ELECTRONICS',
        startPrice: 10000,
        instantBuyPrice: 10000000,
        duration: 'HOURS_24',
        directTradeAvailable: false,
        deliveryAvailable: true,
    });

    const res = http.post(`${BASE_URL}/api/v1/auctions`, auctionPayload, { headers });

    if (res.status === 200 || res.status === 201) {
        const body = JSON.parse(res.body);
        if (body.success && body.data) {
            console.log(`경매 생성 완료: ID=${body.data.id}`);
            return { auctionId: body.data.id };
        }
    }

    const listRes = http.get(`${BASE_URL}/api/v1/auctions?status=BIDDING&page=0&size=1`);
    if (listRes.status === 200) {
        const listBody = JSON.parse(listRes.body);
        if (listBody.success && listBody.data?.content?.length > 0) {
            const auctionId = listBody.data.content[0].id;
            console.log(`기존 경매 사용: ID=${auctionId}`);
            return { auctionId };
        }
    }

    throw new Error('테스트용 경매를 생성하거나 찾을 수 없습니다.');
}

export default function (data) {
    const auctionId = data.auctionId;
    const userId = randomUserId();
    const headers = getHeaders(userId);

    const bidPayload = JSON.stringify({
        bidType: 'ONE_TOUCH',
    });

    const res = http.post(
        `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
        bidPayload,
        {
            headers,
            tags: { name: 'place_bid' },
        }
    );

    const success = check(res, {
        'status is 2xx': (r) => r.status >= 200 && r.status < 300,
    });

    if (res.status >= 500) {
        httpErrors.add(1);
    }

    if (success) {
        try {
            const body = JSON.parse(res.body);
            if (body.success) {
                bidSuccess.add(1);
                bidErrorRate.add(0);
            } else {
                bidFailed.add(1);
                bidErrorRate.add(1);
            }
        } catch (e) {
            bidFailed.add(1);
            bidErrorRate.add(1);
        }
    } else {
        bidFailed.add(1);
        bidErrorRate.add(1);
    }

    // 현실적 입찰 간격: 1~3초 (사람이 실제로 입찰하는 속도)
    sleep(Math.random() * 2 + 1);
}

export function handleSummary(data) {
    // 정합성 비교를 위해 Prometheus에서 데이터 가져오기
    let consistencyText = '';
    try {
        const res = http.get(`${BASE_URL}/actuator/prometheus`);
        if (res.status === 200) {
            const body = res.body;
            const redisMatch = body.match(/fairbid_bid_redis_count\{[^}]*\}\s+([\d.]+)/);
            const rdbMatch = body.match(/fairbid_bid_rdb_count\{[^}]*\}\s+([\d.]+)/);

            if (redisMatch && rdbMatch) {
                const redisCount = parseInt(redisMatch[1]);
                const rdbCount = parseInt(rdbMatch[1]);
                const diff = redisCount - rdbCount;

                consistencyText = `
=============================================
🔍 Redis-RDB 정합성 비교
=============================================
  Redis 입찰 수: ${redisCount}
  RDB 입찰 수:   ${rdbCount}
  차이 (불일치): ${diff}
  ${diff === 0 ? '✅ 정합성 일치' : '❌ 불일치 감지!'}
=============================================
`;
            }
        }
    } catch (e) {
        consistencyText = '\n[정합성 비교 실패]\n';
    }

    const metrics = data.metrics;
    return {
        'stdout': `
=============================================
📊 현실적 트래픽 테스트 결과 (15 VUs, 60초)
=============================================

📈 요청 통계
- 총 요청 수: ${metrics.http_reqs?.values?.count || 0}
- 성공한 입찰: ${metrics.bid_success?.values?.count || 0}
- 실패한 입찰: ${metrics.bid_failed?.values?.count || 0}
- 5xx 에러: ${metrics.http_5xx_errors?.values?.count || 0}

⏱️ 응답 시간
- 평균: ${(metrics.http_req_duration?.values?.avg || 0).toFixed(2)}ms
- p90: ${(metrics.http_req_duration?.values?.['p(90)'] || 0).toFixed(2)}ms
- p95: ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
- p99: ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms
- 최대: ${(metrics.http_req_duration?.values?.max || 0).toFixed(2)}ms

❌ 에러율
- HTTP 에러율: ${((metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%
- 입찰 에러율: ${((metrics.bid_error_rate?.values?.rate || 0) * 100).toFixed(2)}%

=============================================
` + consistencyText,
        'k6/results/bid-realistic-test-result.json': JSON.stringify(data, null, 2),
    };
}
