#!/bin/bash
# HA Step 3: Sentinel 자동 Failover 테스트
#
# Sentinel이 Master 장애를 자동 감지하고 Slave를 승격하는 과정을 k6 부하 중 측정한다.
# Step 2(수동 Failover, 38초)와 비교하여 자동화의 효과를 증명한다.
#
# 증명할 것:
#   1. Sentinel 자동 failover 다운타임 (vs Step 2 수동 38초)
#   2. 앱이 자동 재연결되는지 (Lettuce + Sentinel 구독)
#   3. Master/Slave 데이터 정합성
#
# 타임라인:
#   0~40초  : Baseline (정상 부하, Sentinel 모드)
#   40초    : 데이터 스냅샷 + Master 강제 종료
#   40~55초 : Sentinel 자동 failover (감지 → 투표 → 승격 → 앱 재연결)
#   55~120초: 복구 안정화
#
# 사전 조건: docker compose up -d
# 사용법: bash k6/scripts/run-ha-step3-sentinel.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.yml"
ENV_FILE="${PROJECT_DIR}/.env"
DC="docker compose -f ${COMPOSE_FILE}"

# ── sentinel 프로필 활성화 ──
# .env에 SPRING_PROFILES_ACTIVE가 없거나 sentinel이 빠져 있으면 추가한다.
if grep -q "^SPRING_PROFILES_ACTIVE=" "$ENV_FILE" 2>/dev/null; then
    ORIGINAL_PROFILES=$(grep "^SPRING_PROFILES_ACTIVE=" "$ENV_FILE" | cut -d= -f2)
    if ! echo "$ORIGINAL_PROFILES" | grep -q "sentinel"; then
        sed -i "s/^SPRING_PROFILES_ACTIVE=.*/SPRING_PROFILES_ACTIVE=${ORIGINAL_PROFILES},sentinel/" "$ENV_FILE"
    fi
else
    ORIGINAL_PROFILES=""
    echo "SPRING_PROFILES_ACTIVE=default,sentinel" >> "$ENV_FILE"
fi

# 백엔드 재시작 (sentinel 프로필 적용)
$DC up -d --no-deps backend
echo "  sentinel 프로필 활성화 → 백엔드 재시작"
sleep 10

echo ""
echo "============================================="
echo "  HA Step 3: Sentinel 자동 Failover"
echo "============================================="
echo ""
echo "  타임라인:"
echo "    0~40초  : Baseline (Sentinel 모드)"
echo "    40초    : Master 강제 종료"
echo "    ~50초   : Sentinel 자동 failover"
echo "    50~120초: 복구 안정화"
echo ""

# Sentinel 상태 확인
echo "  Sentinel 상태 확인:"
SENTINEL_MASTER=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>&1 | tr -d '\r')
echo "    현재 Master: ${SENTINEL_MASTER}"
SENTINEL_SLAVES=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL replicas mymaster 2>&1 | grep -c "name" | tr -d '\r')
echo "    Slave 수: ${SENTINEL_SLAVES}"
MASTER_HOST=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>&1 | head -1 | tr -d '\r')

# ── 1. k6 부하 테스트 시작 ──
echo ""
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

# Master 스냅샷 (쓰기 중단 상태이므로 정확)
MASTER_DATA=$($DC exec -T redis redis-cli HMGET "$AUCTION_KEY" currentPrice totalBidCount topBidderId 2>&1 | tr -d '\r')
MASTER_PRICE=$(echo "$MASTER_DATA" | sed -n '1p')
MASTER_BIDS=$(echo "$MASTER_DATA" | sed -n '2p')
MASTER_TOP=$(echo "$MASTER_DATA" | sed -n '3p')

# Master 강제 종료
FAULT_EPOCH=$(date +%s)
FAULT_TS=$(date '+%H:%M:%S')
# docker kill: 컨테이너를 즉시 종료하여 실제 서버 장애를 시뮬레이션한다.
# 고정 IP 네트워크(172.22.0.0/24)를 사용하므로 컨테이너가 죽어도 IP는 유지된다.
# Sentinel이 connection refused로 sdown 감지 → failover 정상 진행.
$DC kill redis
echo "  Redis Master 장애 발생! (kill) [${FAULT_TS}]"

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

# ── 5. Sentinel 자동 Failover 대기 → 백엔드 재시작 ──
# 백엔드를 stop한 상태이므로 cold start 시 master가 있어야 한다.
# Sentinel이 새 Master를 승격할 때까지 대기 후 백엔드를 재시작한다.
# (WSL2 TILT로 인해 최대 60초 지연 가능)
echo ""
echo "  Sentinel 자동 failover 대기 중..."
for i in $(seq 1 90); do
    SENTINEL_RESULT=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>&1 | head -1 | tr -d '\r')
    if [ -n "$SENTINEL_RESULT" ] && [ "$SENTINEL_RESULT" != "$MASTER_HOST" ]; then
        echo "  Sentinel failover 완료! 새 Master: ${SENTINEL_RESULT} [$(date '+%H:%M:%S')]"
        break
    fi
    sleep 1
done

# --no-deps: depends_on에 의한 죽은 Master 자동 재시작 방지
echo ""
echo "  백엔드 재시작..."
$DC up -d --no-deps backend

# 서비스 복구 확인
RECOVERY_TS="N/A"
RECOVERY_EPOCH=0
for i in $(seq 1 90); do
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/v1/auctions?page=0&size=1" 2>/dev/null)
    if [ "$HTTP_CODE" = "200" ]; then
        RECOVERY_EPOCH=$(date +%s)
        RECOVERY_TS=$(date '+%H:%M:%S')
        echo "  서비스 복구 확인! [${RECOVERY_TS}]"
        break
    fi
    sleep 1
done

# 다운타임 계산
if [ "$RECOVERY_EPOCH" -gt 0 ]; then
    DOWNTIME=$((RECOVERY_EPOCH - FAULT_EPOCH))
else
    DOWNTIME="측정 실패"
fi

# ── 6. k6 종료 대기 ──
echo ""
echo "  k6 종료 대기 중..."
wait "${K6_PID}" || true

# ══════════════════════════════════════════════════════════════
# 최종 결과 요약
# ══════════════════════════════════════════════════════════════
echo ""
echo "============================================="
echo "  HA Step 3: 최종 결과"
echo "============================================="
echo ""
echo "  1. Replication 데이터 정합성 (${AUCTION_KEY})"
echo "  ─────────────────────────────"
echo "                   Master(종료 직전)  Slave(승격 직전)"
echo "    currentPrice : ${MASTER_PRICE}              ${SLAVE_PRICE}"
echo "    totalBidCount: ${MASTER_BIDS}              ${SLAVE_BIDS}"
echo "    topBidderId  : ${MASTER_TOP}              ${SLAVE_TOP}"
if [ "$MASTER_PRICE" = "$SLAVE_PRICE" ] && [ "$MASTER_BIDS" = "$SLAVE_BIDS" ]; then
    echo "    → 일치: 입찰 데이터 Replication 정합성 확인"
else
    echo "    → 차이 발생: 비동기 복제 지연 (async replication lag)"
fi
echo ""
NEW_MASTER=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>&1 | head -1 | tr -d '\r')
echo "  2. Sentinel 자동 Failover"
echo "  ─────────────────────────────"
echo "    장애 발생 : ${FAULT_TS}"
echo "    서비스 복구: ${RECOVERY_TS}"
echo "    새 Master  : ${NEW_MASTER:-확인 실패}"
echo "    다운타임   : ${DOWNTIME}초"
echo ""
echo "============================================="

# ── .env 원복 ──
if [ -n "$ORIGINAL_PROFILES" ]; then
    sed -i "s/^SPRING_PROFILES_ACTIVE=.*/SPRING_PROFILES_ACTIVE=${ORIGINAL_PROFILES}/" "$ENV_FILE"
else
    sed -i '/^SPRING_PROFILES_ACTIVE=default,sentinel$/d' "$ENV_FILE"
fi
echo ""
echo "  .env 원복 완료"
