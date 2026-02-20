/**
 * HA Step 1: Redis SPOF 체감 테스트
 *
 * Redis 단일 인스턴스가 죽으면 입찰 서비스가 완전 중단되는지 데이터로 증명한다.
 *
 * 기존 bid-sync-test.js와 차이점:
 *   - 커넥션 실패(status=0), 타임아웃, 5xx를 모두 에러로 카운트
 *   - 요청별 타임아웃 설정 (5초) → 장애 시 빠른 실패
 *   - 결과 요약에서 장애 구간 에러 명확히 표시
 *
 * 장애 주입은 오케스트레이터 스크립트에서 실행:
 *   bash k6/scripts/run-ha-step1-spof.sh
 *
 * 실행: k6 run k6/scenarios/ha-redis-spof.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { BASE_URL, getHeaders, randomUserId } from './config.js';

// 커스텀 메트릭
const bidSuccess = new Counter('bid_success');        // 입찰 성공
const bidFailed = new Counter('bid_failed');          // 입찰 실패 (비즈니스 에러)
const httpErrors = new Counter('http_5xx_errors');    // 5xx 에러
const connErrors = new Counter('conn_errors');        // 커넥션 실패 (status=0, 타임아웃)
const totalErrors = new Counter('total_errors');      // 전체 에러 (5xx + 커넥션 실패 + 비즈니스 에러)
const errorRate = new Rate('error_rate');             // 전체 에러율

// 테스트 설정: 2분간 1000 VUs 일정 부하
export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        ha_spof_test: {
            executor: 'constant-vus',
            vus: 1000,
            duration: '120s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<10000'],  // 장애 테스트이므로 느슨하게
        error_rate: ['rate<1.0'],            // 100%까지 허용
    },
};

/**
 * 셋업: 테스트용 경매 생성
 */
export function setup() {
    console.log('🚀 HA Step 1: Redis SPOF 체감 테스트');
    console.log('⏱️ 타임라인: 0~60초(Baseline) → 60~80초(Redis 장애) → 80~120초(복구)');

    const sellerId = 9999;
    const headers = getHeaders(sellerId);

    const auctionPayload = JSON.stringify({
        title: `HA SPOF 테스트 ${Date.now()}`,
        description: 'Redis SPOF 증명용 경매',
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
            console.log(`✅ 경매 생성 완료: ID=${body.data.id}`);
            return { auctionId: body.data.id };
        }
    }

    // 실패 시 기존 BIDDING 경매 사용
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

    const bidPayload = JSON.stringify({
        bidType: 'ONE_TOUCH',
    });

    const res = http.post(
        `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
        bidPayload,
        {
            headers,
            tags: { name: 'place_bid' },
            timeout: '5s',  // 요청별 타임아웃 5초 (장애 시 빠른 실패)
        }
    );

    // ─── 에러 분류: 커넥션 실패 / 5xx / 비즈니스 에러 / 성공 ───

    // 1) 커넥션 실패 (status=0): 서버에 도달 못함 (타임아웃, connection refused)
    if (res.status === 0) {
        connErrors.add(1);
        totalErrors.add(1);
        errorRate.add(1);
        bidFailed.add(1);

    // 2) 5xx 서버 에러
    } else if (res.status >= 500) {
        httpErrors.add(1);
        totalErrors.add(1);
        errorRate.add(1);
        bidFailed.add(1);

    // 3) 2xx 응답
    } else if (res.status >= 200 && res.status < 300) {
        try {
            const body = JSON.parse(res.body);
            if (body.success) {
                bidSuccess.add(1);
                errorRate.add(0);
            } else {
                bidFailed.add(1);
                totalErrors.add(1);
                errorRate.add(1);
            }
        } catch (e) {
            bidFailed.add(1);
            totalErrors.add(1);
            errorRate.add(1);
        }

    // 4) 그 외 (4xx 등)
    } else {
        bidFailed.add(1);
        totalErrors.add(1);
        errorRate.add(1);
    }

    sleep(Math.random() * 0.5 + 0.3);  // 0.3~0.8초 간격 (Phase 1과 동일 기준)
}

// 테스트 종료 후 요약
export function handleSummary(data) {
    const metrics = data.metrics;

    const summary = `
=============================================
📊 HA Step 1: Redis SPOF 테스트 결과 (1000 VUs, 120초)
=============================================

📈 요청 통계
- 총 요청 수: ${metrics.http_reqs?.values?.count || 0}
- 성공한 입찰: ${metrics.bid_success?.values?.count || 0}
- 실패한 입찰: ${metrics.bid_failed?.values?.count || 0}

🔴 에러 분류
- 커넥션 실패 (status=0): ${metrics.conn_errors?.values?.count || 0}
- 5xx 서버 에러:          ${metrics.http_5xx_errors?.values?.count || 0}
- 전체 에러:              ${metrics.total_errors?.values?.count || 0}

⏱️ 응답 시간
- 평균: ${(metrics.http_req_duration?.values?.avg || 0).toFixed(2)}ms
- p90: ${(metrics.http_req_duration?.values?.['p(90)'] || 0).toFixed(2)}ms
- p95: ${(metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
- p99: ${(metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms
- 최대: ${(metrics.http_req_duration?.values?.max || 0).toFixed(2)}ms

❌ 에러율
- 전체 에러율: ${((metrics.error_rate?.values?.rate || 0) * 100).toFixed(2)}%
- HTTP 에러율: ${((metrics.http_req_failed?.values?.rate || 0) * 100).toFixed(2)}%

=============================================
`;

    return {
        'stdout': summary,
        'k6/results/ha-step1-spof-result.json': JSON.stringify(data, null, 2),
    };
}
