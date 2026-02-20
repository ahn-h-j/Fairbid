#!/bin/bash
# HA Step 3: Split Brain 발생 테스트 (Before — 방지 설정 없음)
#
# min-replicas-to-write=0 상태에서 네트워크 파티션 발생 시
# 격리된 Master가 쓰기를 허용하고, 경매 데이터가 달라지는 것을 증명한다.
#
# 사전 조건: docker compose up -d
# 사용법: bash k6/scripts/run-ha-step3-split-brain-before.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.yml"
ENV_FILE="${PROJECT_DIR}/.env"
DC="docker compose -f ${COMPOSE_FILE}"
NETWORK="fairbid_default"

# sentinel 프로필 활성화
if grep -q "^SPRING_PROFILES_ACTIVE=" "$ENV_FILE" 2>/dev/null; then
    ORIGINAL_PROFILES=$(grep "^SPRING_PROFILES_ACTIVE=" "$ENV_FILE" | cut -d= -f2)
    if ! echo "$ORIGINAL_PROFILES" | grep -q "sentinel"; then
        sed -i "s/^SPRING_PROFILES_ACTIVE=.*/SPRING_PROFILES_ACTIVE=${ORIGINAL_PROFILES},sentinel/" "$ENV_FILE"
    fi
else
    ORIGINAL_PROFILES=""
    echo "SPRING_PROFILES_ACTIVE=default,sentinel" >> "$ENV_FILE"
fi
$DC up -d --no-deps backend
echo "  sentinel 프로필 활성화 → 백엔드 재시작"
sleep 10

# split brain 방지 해제
$DC exec -T redis redis-cli CONFIG SET min-replicas-to-write 0 > /dev/null 2>&1

MASTER_IP=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>&1 | head -1 | tr -d '\r')
MASTER_CONTAINER=$($DC ps -q redis)

echo ""
echo "=========================================="
echo " Split Brain 발생 테스트 (Before)"
echo " 설정: min-replicas-to-write = 0"
echo "=========================================="
echo ""
echo " 현재 Master: ${MASTER_IP}"
echo ""

# 1. k6 부하 시작
k6 run "${PROJECT_DIR}/k6/scenarios/ha-redis-spof.js" &
K6_PID=$!
echo " k6 시작 (PID: ${K6_PID})"
echo ""

# 2. Baseline 40초
echo " [0~40초] Baseline 측정 중..."
sleep 40

# 3. 경매 키 확인
AUCTION_KEY=$($DC exec -T redis redis-cli KEYS "auction:*" 2>&1 | grep -v "closing" | head -1 | tr -d '\r\n')

# 4. 네트워크 파티션
PARTITION_TS=$(date '+%H:%M:%S')
docker network disconnect ${NETWORK} ${MASTER_CONTAINER}
echo " [${PARTITION_TS}] Master 네트워크 분리!"

# 파티션 직후 구 Master 스냅샷 (docker exec로 접근 가능)
BEFORE_DATA=$(docker exec ${MASTER_CONTAINER} redis-cli HMGET "$AUCTION_KEY" currentPrice totalBidCount topBidderId 2>&1 | tr -d '\r')
BEFORE_PRICE=$(echo "$BEFORE_DATA" | sed -n '1p')
BEFORE_BIDS=$(echo "$BEFORE_DATA" | sed -n '2p')
BEFORE_TOP=$(echo "$BEFORE_DATA" | sed -n '3p')

# 5. Sentinel failover 대기
echo " Sentinel failover 대기 중..."
NEW_MASTER=""
NEW_MASTER_SVC=""
for i in $(seq 1 60); do
    NEW_MASTER=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>&1 | head -1 | tr -d '\r')
    if [ -n "$NEW_MASTER" ] && [ "$NEW_MASTER" != "$MASTER_IP" ]; then
        FAILOVER_TS=$(date '+%H:%M:%S')
        echo " [${FAILOVER_TS}] Failover 완료 → 새 Master: ${NEW_MASTER}"
        break
    fi
    sleep 1
done

if [ "$NEW_MASTER" = "172.22.0.11" ]; then
    NEW_MASTER_SVC="redis-slave-1"
elif [ "$NEW_MASTER" = "172.22.0.12" ]; then
    NEW_MASTER_SVC="redis-slave-2"
fi

# 6. k6 종료 대기
echo ""
echo " k6 종료 대기 중..."
wait "${K6_PID}" || true

# ──────────────────────────────────────────
# k6 완료 후 검증 시작
# ──────────────────────────────────────────

echo ""
echo "=========================================="
echo " Split Brain 검증"
echo "=========================================="

# 7. 구 Master(스냅샷) vs 새 Master 비교
echo ""
echo " [구 Master - ${MASTER_IP}] (파티션 직후 스냅샷)"
echo " currentPrice  = ${BEFORE_PRICE}"
echo " totalBidCount = ${BEFORE_BIDS}"

echo ""
echo " [새 Master - ${NEW_MASTER}]"
echo " \$ redis-cli -h ${NEW_MASTER} HMGET ${AUCTION_KEY} currentPrice totalBidCount"
NEW_DATA=$($DC exec -T ${NEW_MASTER_SVC} redis-cli HMGET "$AUCTION_KEY" currentPrice totalBidCount 2>&1 | tr -d '\r')
NEW_PRICE=$(echo "$NEW_DATA" | sed -n '1p')
NEW_BIDS=$(echo "$NEW_DATA" | sed -n '2p')
echo " currentPrice  = ${NEW_PRICE}"
echo " totalBidCount = ${NEW_BIDS}"

echo ""
echo " ───────────────────────────────"
echo "               구 Master  새 Master"
echo " currentPrice  ${BEFORE_PRICE}    ${NEW_PRICE}"
echo " totalBidCount ${BEFORE_BIDS}    ${NEW_BIDS}"
echo " ───────────────────────────────"

if [ "$BEFORE_PRICE" != "$NEW_PRICE" ] || [ "$BEFORE_BIDS" != "$NEW_BIDS" ]; then
    echo ""
    echo " → 같은 경매의 가격/입찰수가 다름 = SPLIT BRAIN"
fi

# 8. 격리된 구 Master 쓰기 시도 (before → after)
echo ""
echo " [격리된 구 Master - ${MASTER_IP}] 경매 데이터 쓰기 시도"
echo " \$ redis-cli -h ${MASTER_IP} HINCRBY ${AUCTION_KEY} totalBidCount 1"
WRITE_RESULT=$(docker exec ${MASTER_CONTAINER} redis-cli HINCRBY "$AUCTION_KEY" totalBidCount 1 2>&1 | tr -d '\r')
echo " before: totalBidCount = ${BEFORE_BIDS}"
echo " after:  totalBidCount = ${WRITE_RESULT}"
echo ""

if echo "$WRITE_RESULT" | grep -qE "^[0-9]+$"; then
    echo " → 쓰기 성공 (${BEFORE_BIDS} → ${WRITE_RESULT}) = split brain 가능"
else
    echo " → 쓰기 거부됨"
fi

# 9. 네트워크 복구
echo ""
docker network connect --ip ${MASTER_IP} ${NETWORK} ${MASTER_CONTAINER}
echo " [$(date '+%H:%M:%S')] 구 Master 네트워크 재연결"

# 원복
$DC exec -T redis redis-cli CONFIG SET min-replicas-to-write 1 > /dev/null 2>&1
if [ -n "$ORIGINAL_PROFILES" ]; then
    sed -i "s/^SPRING_PROFILES_ACTIVE=.*/SPRING_PROFILES_ACTIVE=${ORIGINAL_PROFILES}/" "$ENV_FILE"
else
    sed -i '/^SPRING_PROFILES_ACTIVE=default,sentinel$/d' "$ENV_FILE"
fi
echo ""
echo " 설정 원복 완료"
