#!/bin/bash
# Phase 3 현실적 트래픽 테스트
#
# 15 VUs, 60초 — 실제 운영 수준 트래픽에서
# MQ Consumer가 실시간 동기화하여 수렴 지연이 거의 0임을 증명
#
# 사용법: bash k6/scripts/run-phase3-realistic.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# MySQL 접속 정보
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-fairbid}"

echo ""
echo "============================================="
echo "  Phase 3: 현실적 트래픽 테스트 (15 VUs)"
echo "============================================="
echo ""

# 1. k6 실행
echo "  k6 부하 테스트 시작..."
k6 run "${PROJECT_DIR}/k6/scenarios/bid-realistic-test.js"

# 2. k6 종료 시점 기록
echo ""
echo "  k6 종료 시점: $(date '+%H:%M:%S')"

# 3. 1초 폴링으로 정합성 수렴 대기
echo ""
echo "============================================="
echo "  수렴 대기 (1초 간격 폴링)"
echo "============================================="

MAX_WAIT=120
START_EPOCH=$(date +%s)

while true; do
    NOW_EPOCH=$(date +%s)
    ELAPSED=$((NOW_EPOCH - START_EPOCH))

    if [ $ELAPSED -ge $MAX_WAIT ]; then
        echo ""
        echo "  ${MAX_WAIT}초 내 수렴 실패!"
        break
    fi

    # Redis 입찰 수 집계
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

    # RDB 입찰 수
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

# 4. 결과
CONVERGENCE_SEC=$ELAPSED
echo ""
echo "============================================="
echo "  결과"
echo "============================================="
echo ""
echo "  k6 종료 -> 정합성 수렴: ${CONVERGENCE_SEC}초"
echo "  Redis 입찰 수: ${REDIS_COUNT}"
echo "  RDB 입찰 수:  ${RDB_COUNT}"
echo ""

# 5. Stream 상태
STREAM_LEN=$(docker exec fairbid-redis-1 redis-cli XLEN stream:bid-rdb-sync 2>/dev/null || echo "0")
PENDING_INFO=$(docker exec fairbid-redis-1 redis-cli XPENDING stream:bid-rdb-sync bid-rdb-sync-group 2>/dev/null)
PENDING_COUNT=$(echo "$PENDING_INFO" | head -1)

echo "  Stream 총 메시지: ${STREAM_LEN}"
echo "  PENDING (미처리): ${PENDING_COUNT}"
echo "============================================="
