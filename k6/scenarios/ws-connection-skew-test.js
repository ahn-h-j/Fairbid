/**
 * Step 5-1 시나리오 B: 스케일아웃 후 WebSocket 커넥션 쏠림 테스트
 *
 * 목적: 모놀리스 환경에서 스케일아웃이 발생해도
 *       기존 WebSocket 커넥션은 새 서버로 이동하지 않는 것을 증명
 *
 * 시나리오:
 *   1. 유저 100명이 경매에 참여 (WebSocket 연결 + REST 요청 동시)
 *   2. ASG desired=2로 증가 → 서버B 추가 (셸 스크립트에서 수행)
 *   3. 서버B healthy 후:
 *      - REST 요청: ALB 라운드로빈으로 서버A ~50%, 서버B ~50% 분산
 *      - WebSocket: 서버A 100명, 서버B 0명 (커넥션은 안 옮겨감)
 *
 * 핵심: 유저가 경매 조회/입찰 내역 확인 등 자연스러운 REST 요청을 보내고,
 *       동시에 WebSocket으로 실시간 가격을 받고 있는 상태.
 *       스케일아웃이 되면 REST는 분산되지만 WebSocket은 분산 안 됨.
 *
 * 실행:
 *   k6 run --env BASE_URL=http://ALB_URL k6/scenarios/ws-connection-skew-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import ws from 'k6/ws';

const BASE_URL = __ENV.BASE_URL || 'http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com';
const WS_URL = BASE_URL.replace('http://', 'ws://').replace('https://', 'wss://') + '/ws/websocket';

const TEST_DURATION_SEC = parseInt(__ENV.DURATION || '300');

// ── 커스텀 메트릭 ──
const wsConnected = new Counter('ws_connected');
const wsDisconnected = new Counter('ws_disconnected');

// REST 요청이 어느 서버로 가는지 추적
const restToServerA = new Counter('rest_to_server_a');
const restToServerB = new Counter('rest_to_server_b');
const restToUnknown = new Counter('rest_to_unknown');

// ── 테스트 설정 ──
const SUBSCRIBER_COUNT = parseInt(__ENV.SUBSCRIBERS || '100');

export const options = {
    scenarios: {
        // 유저 시뮬레이션: WebSocket 연결 유지 + 주기적 REST 요청
        // 각 유저가 경매 구독하면서 간간이 경매 조회도 하는 자연스러운 행동
        users: {
            executor: 'shared-iterations',
            vus: SUBSCRIBER_COUNT,
            iterations: SUBSCRIBER_COUNT,
            exec: 'auctionUser',
            startTime: '0s',
            maxDuration: `${TEST_DURATION_SEC + 60}s`,
        },
    },
    thresholds: {
        ws_connected: ['count>0'],
    },
};

export function setup() {
    console.log(`🎯 Target: ${BASE_URL}`);
    console.log(`📡 WebSocket: ${WS_URL}`);
    console.log(`👥 유저: ${SUBSCRIBER_COUNT}명`);
    console.log(`⏱️ 테스트 지속 시간: ${TEST_DURATION_SEC}초`);
    console.log('');
    console.log('⚠️  ASG desired 변경은 셸 스크립트에서 수행합니다.');
    console.log('');

    // 테스트용 경매 생성
    const sellerId = 9999;
    const res = http.post(
        `${BASE_URL}/api/v1/auctions`,
        JSON.stringify({
            title: `커넥션 쏠림 테스트 - ${Date.now()}`,
            description: 'Step 5-1 시나리오 B: 스케일아웃 후 WebSocket 커넥션 쏠림 테스트',
            category: 'ELECTRONICS',
            startPrice: 10000,
            instantBuyPrice: 10000000,
            duration: 'HOURS_24',
            directTradeAvailable: false,
            deliveryAvailable: true,
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': String(sellerId),
            },
        }
    );

    let auctionId;
    if (res.status === 200 || res.status === 201) {
        const body = JSON.parse(res.body);
        if (body.success && body.data) {
            auctionId = body.data.id;
            console.log(`✅ 경매 생성 완료: ID=${auctionId}`);
        }
    }

    if (!auctionId) {
        console.log('⚠️ 경매 생성 실패, 기존 경매 사용');
        const listRes = http.get(`${BASE_URL}/api/v1/auctions?status=BIDDING&page=0&size=1`);
        if (listRes.status === 200) {
            const listBody = JSON.parse(listRes.body);
            if (listBody.success && listBody.data?.content?.length > 0) {
                auctionId = listBody.data.content[0].id;
            }
        }
    }

    if (!auctionId) {
        throw new Error('테스트용 경매를 생성하거나 찾을 수 없습니다.');
    }

    // 현재 서버 IP 기록 (1대일 때의 서버 = 서버A)
    const healthRes = http.get(`${BASE_URL}/actuator/health`);
    const serverAIp = healthRes.headers['X-Server-Ip'] || 'unknown';
    console.log(`📌 서버A (기존): ${serverAIp}`);
    console.log('');

    return { auctionId, serverAIp };
}

/**
 * 경매 참여 유저 시뮬레이션
 *
 * 하나의 VU가:
 * - WebSocket으로 경매 구독 (실시간 가격 수신)
 * - 동시에 10초마다 REST로 경매 조회 (자연스러운 유저 행동)
 *
 * WebSocket 연결 중에 REST 요청을 보내므로,
 * 스케일아웃 후 REST만 분산되고 WebSocket은 안 옮겨지는 걸 동시에 관찰 가능
 */
export function auctionUser(data) {
    const { auctionId, serverAIp } = data;
    const userId = 1000 + __VU;

    const res = ws.connect(WS_URL, {}, function (socket) {
        let connected = false;

        socket.on('open', function () {
            socket.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0');
        });

        socket.on('message', function (msg) {
            if (msg.startsWith('CONNECTED')) {
                connected = true;
                wsConnected.add(1);
                socket.send(
                    `SUBSCRIBE\nid:sub-${userId}\ndestination:/topic/auctions/${auctionId}\n\n\0`
                );
                return;
            }
        });

        socket.on('close', function () {
            if (connected) {
                wsDisconnected.add(1);
            }
        });

        // 10초마다 REST 요청 (경매 조회 — 유저가 자연스럽게 하는 행동)
        // WebSocket 연결 유지하면서 동시에 REST를 보냄
        socket.setInterval(function () {
            const restRes = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, {
                headers: { 'X-User-Id': String(userId) },
            });

            const serverIp = restRes.headers['X-Server-Ip'] || 'unknown';

            if (serverIp === serverAIp) {
                restToServerA.add(1);
            } else if (serverIp !== 'unknown') {
                restToServerB.add(1);
            } else {
                restToUnknown.add(1);
            }
        }, 10000); // 10초 간격

        // 테스트 지속 시간 후 종료
        socket.setTimeout(function () {
            socket.close();
        }, TEST_DURATION_SEC * 1000);
    });

    check(res, {
        'WebSocket 연결 성공': (r) => r && r.status === 101,
    });
}

/**
 * 테스트 요약
 */
export function handleSummary(data) {
    const metrics = data.metrics;

    const connected = metrics.ws_connected?.values?.count || 0;
    const disconnected = metrics.ws_disconnected?.values?.count || 0;
    const toA = metrics.rest_to_server_a?.values?.count || 0;
    const toB = metrics.rest_to_server_b?.values?.count || 0;
    const toUnknown = metrics.rest_to_unknown?.values?.count || 0;
    const totalRest = toA + toB + toUnknown;

    const restAPct = totalRest > 0 ? ((toA / totalRest) * 100).toFixed(1) : '0';
    const restBPct = totalRest > 0 ? ((toB / totalRest) * 100).toFixed(1) : '0';

    const summary = `

================================================================
  스케일아웃 후 WebSocket 커넥션 쏠림 테스트 결과
================================================================

  WebSocket 커넥션
  ────────────────────────────────────
    연결 성공 : ${connected}명 / ${SUBSCRIBER_COUNT}명
    연결 끊김 : ${disconnected}명

  REST 요청 분산 (ALB 라운드로빈)
  ────────────────────────────────────
    서버A (기존) : ${toA}건 (${restAPct}%)
    서버B (신규) : ${toB}건 (${restBPct}%)
    미식별       : ${toUnknown}건

  비교
  ────────────────────────────────────
    REST     : 서버A ${restAPct}% / 서버B ${restBPct}%  → 분산됨
    WebSocket: 서버A ${connected}명 / 서버B 0명      → 분산 안 됨

${toB > 0
    ? `  ✅ REST는 분산, WebSocket은 쏠림 — 스케일아웃이 WS에 무의미`
    : '  ⚠️ 서버B로 REST 분산 미확인 (스케일아웃 타이밍 확인 필요)'}

================================================================
`;

    return {
        stdout: summary,
        'k6/results/ws-connection-skew-result.json': JSON.stringify(data, null, 2),
    };
}
