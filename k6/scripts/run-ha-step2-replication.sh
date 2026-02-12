#!/bin/bash
# HA Step 2: Replication + 수동 Failover 테스트
#
# k6 부하 중 Master를 죽이고, Slave를 수동 승격하여 서비스를 복구한다.
# Step 1(SPOF)과 동일한 k6 시나리오로 메트릭 비교 가능.
#
# 증명할 것:
#   1. 정상 → 장애 → 복구 패턴 (k6 메트릭)
#   2. Master-Slave 데이터 일치 (Replication 정합성)
#   3. 수동 Failover 소요 시간 (Step 4 Sentinel과 비교 기준)
#
# 타임라인:
#   0~40초  : Baseline (정상 부하)
#   40초    : 데이터 스냅샷 + Master 종료 + 즉시 수동 Failover
#   40~55초 : Failover 과정 (Slave 승격 + 앱 재시작)
#   55~120초: 복구 안정화
#
# 사전 조건: docker compose up -d
# 사용법: bash k6/scripts/run-ha-step2-replication.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.yml"
DC="docker compose -f ${COMPOSE_FILE}"


echo ""
echo "============================================="
echo "  HA Step 2: Replication + 수동 Failover"
echo "============================================="
echo ""
echo "  타임라인:"
echo "    0~40초  : Baseline"
echo "    40초    : Master 종료 + 즉시 Failover"
echo "    ~55초   : 서비스 복구"
echo "    55~120초: 복구 안정화"
echo ""

# ── 1. k6 부하 테스트 시작 ──
echo "  k6 부하 테스트 시작 (백그라운드)..."
k6 run "${PROJECT_DIR}/k6/scenarios/ha-redis-spof.js" &
K6_PID=$!
echo "  k6 PID: ${K6_PID}"

# ── 2. Baseline ──
echo ""
echo "  Baseline 측정 중... (40초)"
sleep 40

# ── 3. 경매 키 탐색 ──
AUCTION_KEY=$($DC exec -T redis redis-cli KEYS "auction:*" 2>&1 | grep -v "closing" | head -1 | tr -d '\r\n')
echo "  경매 키: ${AUCTION_KEY}"

# ── 4. Redis 장애 시뮬레이션 ──
# 백엔드를 먼저 내려서 Redis 쓰기를 중단시킨 뒤 데이터 비교 (정확한 비교를 위해)
echo ""
$DC stop backend > /dev/null 2>&1
sleep 1

# Master 스냅샷 (쓰기 중단 상태이므로 정확)
MASTER_DATA=$($DC exec -T redis redis-cli HMGET "$AUCTION_KEY" currentPrice totalBidCount topBidderId 2>&1 | tr -d '\r')
MASTER_PRICE=$(echo "$MASTER_DATA" | sed -n '1p')
MASTER_BIDS=$(echo "$MASTER_DATA" | sed -n '2p')
MASTER_TOP=$(echo "$MASTER_DATA" | sed -n '3p')

# Master 종료
FAULT_EPOCH=$(date +%s)
FAULT_TS=$(date '+%H:%M:%S')
$DC stop redis
echo "  Redis Master 장애 발생! [${FAULT_TS}]"

# Slave 스냅샷 (Master와 동일해야 함)
SLAVE_DATA=$($DC exec -T redis-slave-1 redis-cli HMGET "$AUCTION_KEY" currentPrice totalBidCount topBidderId 2>&1 | tr -d '\r')
SLAVE_PRICE=$(echo "$SLAVE_DATA" | sed -n '1p')
SLAVE_BIDS=$(echo "$SLAVE_DATA" | sed -n '2p')
SLAVE_TOP=$(echo "$SLAVE_DATA" | sed -n '3p')

echo ""
echo "  장애 직전 Master vs Slave 데이터 비교:"
echo "    currentPrice  — Master: ${MASTER_PRICE}  |  Slave: ${SLAVE_PRICE}"
echo "    totalBidCount — Master: ${MASTER_BIDS}  |  Slave: ${SLAVE_BIDS}"
echo "    topBidderId   — Master: ${MASTER_TOP}  |  Slave: ${SLAVE_TOP}"
if [ "$MASTER_PRICE" = "$SLAVE_PRICE" ] && [ "$MASTER_BIDS" = "$SLAVE_BIDS" ] && [ "$MASTER_TOP" = "$SLAVE_TOP" ]; then
    echo "    → 완전 일치: Replication 정합성 확인"
else
    echo "    → 불일치 발생"
fi

# ── 6. 즉시 수동 Failover ──
echo ""
echo "  수동 Failover 시작 [$(date '+%H:%M:%S')]"

echo "  [1/4] Slave-1 승격 (REPLICAOF NO ONE)"
$DC exec -T redis-slave-1 redis-cli REPLICAOF NO ONE

echo "  [2/4] Slave-2 → Slave-1에 연결"
$DC exec -T redis-slave-2 redis-cli REPLICAOF redis-slave-1 6379

echo "  [3/4] 앱 Redis 설정 변경 (redis → redis-slave-1)"
sed -i 's/SPRING_DATA_REDIS_HOST: redis/SPRING_DATA_REDIS_HOST: redis-slave-1/' "${COMPOSE_FILE}"

echo "  [4/4] 앱 재시작 (새 Master로 연결)"
$DC up -d backend

echo "  앱 기동 대기..."
RECOVERY_TS="N/A"
RECOVERY_EPOCH=0
for i in $(seq 1 30); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/v1/auctions?page=0&size=1" 2>/dev/null)
    if [ "$HTTP_CODE" = "200" ]; then
        RECOVERY_EPOCH=$(date +%s)
        RECOVERY_TS=$(date '+%H:%M:%S')
        echo "  앱 복구 확인! [${RECOVERY_TS}]"
        break
    fi
    sleep 1
done

# Failover 소요 시간 계산
if [ "$RECOVERY_EPOCH" -gt 0 ]; then
    DOWNTIME=$((RECOVERY_EPOCH - FAULT_EPOCH))
else
    DOWNTIME="측정 실패"
fi

# ── 7. k6 종료 대기 ──
echo ""
echo "  k6 종료 대기 중..."
wait "${K6_PID}" || true

# ══════════════════════════════════════════════════════════════
# 최종 결과 요약
# ══════════════════════════════════════════════════════════════
echo ""
echo "============================================="
echo "  HA Step 2: 최종 결과"
echo "============================================="
echo ""
echo "  1. Replication 데이터 정합성 (auction:${AUCTION_KEY})"
echo "  ─────────────────────────────"
echo "                   Master(종료 직전)  Slave-1(승격 직전)"
echo "    currentPrice : ${MASTER_PRICE}              ${SLAVE_PRICE}"
echo "    totalBidCount: ${MASTER_BIDS}              ${SLAVE_BIDS}"
echo "    topBidderId  : ${MASTER_TOP}              ${SLAVE_TOP}"
if [ "$MASTER_PRICE" = "$SLAVE_PRICE" ] && [ "$MASTER_BIDS" = "$SLAVE_BIDS" ]; then
    echo "    → 일치: 입찰 데이터 Replication 정합성 확인"
else
    echo "    → 차이 발생: 비동기 복제 지연 (async replication lag)"
fi
echo ""
echo "  2. 수동 Failover 다운타임"
echo "  ─────────────────────────────"
echo "    장애 발생 : ${FAULT_TS}"
echo "    서비스 복구: ${RECOVERY_TS}"
echo "    다운타임   : ${DOWNTIME}초"
echo "    (스크립트 자동 실행 기준 — 사람이 하면 감지+대응 시간 추가)"
echo ""
echo "  → Step 4(Sentinel)에서 같은 테스트로 자동 failover 다운타임 측정 후 비교"
echo ""
echo "============================================="

# ── docker-compose.yml 원복 (redis-slave-1 → redis) ──
sed -i 's/SPRING_DATA_REDIS_HOST: redis-slave-1/SPRING_DATA_REDIS_HOST: redis/' "${COMPOSE_FILE}"
echo ""
echo "  docker-compose.yml 원복 완료"
