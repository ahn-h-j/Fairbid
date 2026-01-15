/**
 * WebSocket 동시 연결 테스트
 *
 * 시나리오: 많은 클라이언트가 경매 실시간 업데이트 구독
 * 목적: WebSocket 서버 연결 수용량, 메시지 브로드캐스트 성능 측정
 *
 * 실행: k6 run k6/scenarios/websocket-load.js
 * (경매는 자동 생성됨)
 */

import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { BASE_URL, WS_URL, getHeaders } from './config.js';

// 커스텀 메트릭
const wsConnections = new Counter('ws_connections');
const wsMessages = new Counter('ws_messages_received');
const wsErrors = new Counter('ws_errors');
const wsConnectTime = new Trend('ws_connect_time', true);
const wsMessageLatency = new Trend('ws_message_latency', true);

// 테스트 설정
export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        // WebSocket 연결 유지 시나리오
        ws_connections: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },   // 30초 동안 50명 연결
                { duration: '1m', target: 100 },   // 1분 동안 100명까지
                { duration: '2m', target: 200 },   // 2분 동안 200명까지
                { duration: '3m', target: 200 },   // 3분 동안 200명 유지 (메시지 수신 관찰)
                { duration: '30s', target: 0 },    // 30초 동안 종료
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        ws_connect_time: ['p(95)<2000'],  // WebSocket 연결 95%가 2초 이내
        ws_errors: ['count<10'],           // 에러 10개 미만
    },
};

// STOMP CONNECT 프레임 생성
function stompConnect() {
    return 'CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\x00';
}

// STOMP SUBSCRIBE 프레임 생성
function stompSubscribe(destination, id) {
    return `SUBSCRIBE\nid:sub-${id}\ndestination:${destination}\n\n\x00`;
}

/**
 * 테스트 전 셋업: 테스트용 경매 생성
 */
export function setup() {
    console.log('🚀 WebSocket 테스트 셋업 시작...');

    const sellerId = 9999;
    const headers = getHeaders(sellerId);

    // 테스트용 경매 생성
    const auctionPayload = JSON.stringify({
        title: `WebSocket테스트 경매 ${Date.now()}`,
        description: 'WebSocket 부하테스트용 경매입니다.',
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

// STOMP 메시지 파싱
function parseStompMessage(data) {
    const lines = data.split('\n');
    const command = lines[0];

    if (command === 'MESSAGE') {
        // 헤더 파싱 (콜론이 값에 포함될 수 있으므로 첫 번째 콜론만 분리)
        const headers = {};
        let i = 1;
        while (lines[i] && lines[i] !== '') {
            const colonIndex = lines[i].indexOf(':');
            if (colonIndex > 0) {
                const key = lines[i].substring(0, colonIndex);
                const value = lines[i].substring(colonIndex + 1);
                headers[key] = value;
            }
            i++;
        }
        // 본문
        const body = lines.slice(i + 1).join('\n').replace('\x00', '');
        return { command, headers, body };
    }

    return { command, headers: {}, body: '' };
}

// 메인 테스트 함수
export default function (data) {
    const auctionId = data.auctionId;
    const url = `${WS_URL}/websocket`;
    const destination = `/topic/auctions/${auctionId}`;

    const startTime = Date.now();

    const res = ws.connect(url, {}, function (socket) {
        const connectTime = Date.now() - startTime;
        wsConnectTime.add(connectTime);
        wsConnections.add(1);

        // STOMP CONNECT
        socket.on('open', function () {
            socket.send(stompConnect());
        });

        socket.on('message', function (message) {
            const msg = parseStompMessage(message);

            if (msg.command === 'CONNECTED') {
                // STOMP 연결 성공, 경매 구독
                socket.send(stompSubscribe(destination, __VU));
            } else if (msg.command === 'MESSAGE') {
                // 메시지 수신
                wsMessages.add(1);

                // 메시지 내 타임스탬프가 있다면 지연 시간 계산
                try {
                    const body = JSON.parse(msg.body);
                    if (body.timestamp) {
                        const latency = Date.now() - new Date(body.timestamp).getTime();
                        wsMessageLatency.add(latency);
                    }
                } catch {
                    // 파싱 실패 무시
                }
            }
        });

        socket.on('error', function (e) {
            wsErrors.add(1);
            console.error(`WebSocket error: ${e.error()}`);
        });

        socket.on('close', function () {
            // 연결 종료
        });

        // 연결 유지 시간 (메시지 수신 대기)
        socket.setTimeout(function () {
            socket.close();
        }, 60000); // 60초 동안 연결 유지
    });

    check(res, {
        'WebSocket connection successful': (r) => r && r.status === 101,
    });

    // VU간 연결 시작 시간 분산
    sleep(Math.random() * 2);
}

// 테스트 요약
export function handleSummary(data) {
    return {
        'stdout': textSummary(data),
        'k6/results/websocket-load-result.json': JSON.stringify(data, null, 2),
    };
}

function textSummary(data) {
    const metrics = data.metrics;
    return `
========================================
📡 WebSocket 동시 연결 테스트 결과
========================================

🔗 연결 통계
- 총 연결 시도: ${metrics.ws_sessions?.values?.count || 0}
- 성공한 연결: ${metrics.ws_connections?.values?.count || 0}
- 에러 수: ${metrics.ws_errors?.values?.count || 0}

📨 메시지 통계
- 수신한 메시지: ${metrics.ws_messages_received?.values?.count || 0}

⏱️ 연결 시간
- 평균: ${(metrics.ws_connect_time?.values?.avg || 0).toFixed(2)}ms
- p95: ${(metrics.ws_connect_time?.values?.['p(95)'] || 0).toFixed(2)}ms
- 최대: ${(metrics.ws_connect_time?.values?.max || 0).toFixed(2)}ms

📊 메시지 지연 시간
- 평균: ${(metrics.ws_message_latency?.values?.avg || 0).toFixed(2)}ms
- p95: ${(metrics.ws_message_latency?.values?.['p(95)'] || 0).toFixed(2)}ms

========================================
`;
}
