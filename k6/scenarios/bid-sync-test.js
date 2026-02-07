/**
 * 동기 방식 RDB 동기화 테스트 (Issue #62 - Phase 1)
 *
 * 시나리오: 50명이 90초간 지속적으로 입찰
 * 용도: Baseline(30초) → 장애 주입(30초) → 복구(30초) 3단계 측정
 *
 * 장애 주입은 k6 외부에서 수동으로 실행:
 *   docker pause fairbid-mysql    (k6 시작 30초 후)
 *   docker unpause fairbid-mysql  (pause 후 30초)
 *
 * 실행: k6 run k6/scenarios/bid-sync-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, getHeaders, randomUserId, generateBidAmount } from './config.js';

// 커스텀 메트릭
const bidSuccess = new Counter('bid_success');
const bidFailed = new Counter('bid_failed');
const bidErrorRate = new Rate('bid_error_rate');
const bidDuration = new Trend('bid_duration', true);
const httpErrors = new Counter('http_5xx_errors');

// 테스트 설정: 2분간 1000 VUs 일정 부하
export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        sync_test: {
            executor: 'constant-vus',
            vus: 1000,
            duration: '120s',
        },
    },
    // 장애 주입 테스트이므로 임계값을 느슨하게 설정 (실패가 예상됨)
    thresholds: {
        http_req_duration: ['p(95)<5000'],
        bid_error_rate: ['rate<1.0'],  // 100%까지 허용 (장애 테스트)
    },
};

/**
 * 셋업: 테스트용 경매 생성
 * 판매자 ID 9999로 경매를 생성하고, 입찰 대상 auctionId를 반환한다.
 */
export function setup() {
    console.log('🚀 동기 RDB 동기화 테스트 셋업...');
    console.log('⏱️ 타임라인: 0~30초(Baseline) → 30~60초(장애 주입) → 60~90초(복구)');

    const sellerId = 9999;
    const headers = getHeaders(sellerId);

    // 경매 생성
    const auctionPayload = JSON.stringify({
        title: `동기테스트 경매 ${Date.now()}`,
        description: 'Phase 1 동기 RDB 동기화 문제점 증명용 경매',
        category: 'ELECTRONICS',
        startPrice: 10000,
        instantBuyPrice: 10000000,  // 즉시구매 비활성화 목적으로 높게 설정
        duration: 'HOURS_24',
        directTradeAvailable: false,  // 직거래 비활성화 (위치 입력 불필요)
        deliveryAvailable: true,      // 택배만 활성화
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

// 메인 테스트 함수: 지속적으로 입찰 요청
export default function (data) {
    const auctionId = data.auctionId;
    const userId = randomUserId();
    const headers = getHeaders(userId);

    // 현재 경매 정보 조회 → 입찰 금액 결정
    const infoRes = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, {
        headers,
        tags: { name: 'get_auction' },
    });

    let bidAmount = 10000;
    if (infoRes.status === 200) {
        const info = JSON.parse(infoRes.body);
        if (info.success && info.data) {
            bidAmount = generateBidAmount(info.data.currentPrice, info.data.bidIncrement);
        }
    }

    // 입찰 요청
    const bidPayload = JSON.stringify({
        amount: bidAmount,
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

    // 결과 체크
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

    sleep(Math.random() * 0.5 + 0.3);  // 0.3~0.8초 간격
}

// 테스트 종료 후 요약
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

    return {
        'stdout': textSummary(data) + consistencyText,
        'k6/results/bid-sync-test-result.json': JSON.stringify(data, null, 2),
    };
}

function textSummary(data) {
    const metrics = data.metrics;
    return `
=============================================
📊 동기 RDB 동기화 테스트 결과 (1000 VUs, 90초)
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
`;
}
