#!/bin/bash
# Phase 2 @Async 앱 강제 종료 테스트
# 메모리 큐 유실 증명용 (임시 스크립트)

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-fairbid}"

BACKEND_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i backend | head -1)
if [ -z "$BACKEND_CONTAINER" ]; then
    echo "  Backend 컨테이너를 찾을 수 없습니다."
    exit 1
fi
echo "  Backend 컨테이너: ${BACKEND_CONTAINER}"

echo ""
echo "============================================="
echo "  Phase 2: @Async 앱 강제 종료 (메모리 큐 유실 증명)"
echo "============================================="
echo ""

# 1. k6 백그라운드 실행
echo "  k6 부하 테스트 시작 (백그라운드)..."
k6 run "${PROJECT_DIR}/k6/scenarios/bid-sync-test.js" &
K6_PID=$!
echo "  k6 PID: ${K6_PID}"

# 2. 정상 부하 대기
echo ""
echo "  정상 부하 측정 중... (60초 대기)"
sleep 60

# 3. 앱 강제 종료
echo ""
echo "  Backend 강제 종료!"
docker kill "${BACKEND_CONTAINER}"
echo "  ${BACKEND_CONTAINER} killed"

# 4. 재시작
echo "  5초 대기..."
sleep 5

echo ""
echo "  Backend 재시작!"
docker start "${BACKEND_CONTAINER}"
echo "  ${BACKEND_CONTAINER} started"
echo "  앱 기동 대기 중... (20초)"
sleep 20

# 5. k6 종료 대기
echo ""
echo "  k6 종료 대기 중..."
wait "${K6_PID}" || true

echo ""
echo "  k6 종료 시점: $(date '+%H:%M:%S')"

# 6. 수렴 대기
echo ""
echo "============================================="
echo "  수렴 대기 (1초 간격 폴링)"
echo "============================================="

MAX_WAIT=300
START_EPOCH=$(date +%s)

while true; do
    NOW_EPOCH=$(date +%s)
    ELAPSED=$((NOW_EPOCH - START_EPOCH))

    if [ $ELAPSED -ge $MAX_WAIT ]; then
        echo ""
        echo "  ${MAX_WAIT}초 내 수렴 실패!"
        break
    fi

    REDIS_COUNT=0
    REDIS_KEYS=$(docker exec fairbid-redis-1 redis-cli KEYS "auction:*" 2>/dev/null)
    while IFS= read -r key; do
        if [[ -z "$key" || "$key" == "auction:closing" ]]; then
            continue
        fi
        count=$(docker exec fairbid-redis-1 redis-cli HGET "$key" totalBidCount 2>/dev/null)
        if [[ -n "$count" && "$count" != "(nil)" ]]; then
            REDIS_COUNT=$((REDIS_COUNT + count))
        fi
    done <<< "$REDIS_KEYS"

    RDB_COUNT=$(docker exec fairbid-mysql-1 mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D"${MYSQL_DB}" -se "SELECT COUNT(*) FROM bid;" 2>/dev/null || echo "0")

    DIFF=$((REDIS_COUNT - RDB_COUNT))

    printf "\r  [%3ds] Redis=%d  RDB=%d  차이=%d    " "$ELAPSED" "$REDIS_COUNT" "$RDB_COUNT" "$DIFF"

    if [ "$DIFF" -eq 0 ] && [ "$REDIS_COUNT" -gt 0 ]; then
        echo ""
        echo ""
        echo "  정합성 수렴 시점: $(date '+%H:%M:%S')"
        break
    fi

    sleep 1
done

CONVERGENCE_SEC=$ELAPSED
echo ""
echo "============================================="
echo "  결과"
echo "============================================="
echo ""
echo "  k6 종료 -> 정합성 수렴: ${CONVERGENCE_SEC}초"
echo "  Redis 입찰 수: ${REDIS_COUNT}"
echo "  RDB 입찰 수:  ${RDB_COUNT}"
echo "  차이 (유실): $((REDIS_COUNT - RDB_COUNT))"
echo "============================================="
