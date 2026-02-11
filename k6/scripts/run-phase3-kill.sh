#!/bin/bash
# Phase 3 앱 강제 종료 테스트
#
# @Async 메모리 큐와 달리 Redis Stream은 앱이 죽어도 메시지가 남아있음을 증명한다.
#
# 타임라인:
#   0초  : k6 시작 (백그라운드)
#   60초 : docker kill backend (강제 종료, 메모리 큐였으면 유실)
#   65초 : docker start backend (재시작)
#   +대기: k6 종료 → 1초 폴링으로 수렴 대기 → 수렴 시간 측정
#
# 기대 결과: 불일치 0건 (PENDING 메시지가 재시작 후 재처리됨)
#
# 사용법: bash k6/scripts/run-phase3-kill.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
GRAFANA_AUTH="admin:admin"
KILL_AT="${1:-60}"

# MySQL 접속 정보
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-fairbid}"

# Backend 컨테이너 이름 자동 감지
BACKEND_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i backend | head -1)
if [ -z "$BACKEND_CONTAINER" ]; then
    echo "  Backend 컨테이너를 찾을 수 없습니다."
    exit 1
fi
echo "  Backend 컨테이너: ${BACKEND_CONTAINER}"

# Grafana Annotation
annotate() {
    local text="$1"
    local tag="$2"
    curl -s -X POST "${GRAFANA_URL}/api/annotations" \
      -u "${GRAFANA_AUTH}" \
      -H "Content-Type: application/json" \
      -d "{\"text\":\"${text}\",\"tags\":[\"${tag}\"]}" > /dev/null 2>&1
    echo "  Annotation: ${text}"
}

echo ""
echo "============================================="
echo "  Phase 3: 앱 강제 종료 (메모리 유실 없음 증명)"
echo "============================================="
echo ""
echo "  타임라인:"
echo "  0~${KILL_AT}초  : 정상 부하"
echo "  ${KILL_AT}초     : docker kill backend"
echo "  +5초            : docker start backend (재시작)"
echo "  ~120초          : k6 종료 -> 수렴 대기 -> 수렴 시간 측정"
echo ""

# 1. k6 백그라운드 실행
echo "  k6 부하 테스트 시작 (백그라운드)..."
k6 run "${PROJECT_DIR}/k6/scenarios/bid-sync-test.js" &
K6_PID=$!
echo "  k6 PID: ${K6_PID}"

# 2. 정상 부하 대기
echo ""
echo "  정상 부하 측정 중... (${KILL_AT}초 대기)"
sleep "${KILL_AT}"

# 3. 강제 종료 직전 상태 기록
echo ""
echo "  강제 종료 직전 상태:"
STREAM_BEFORE=$(docker exec fairbid-redis-1 redis-cli XLEN stream:bid-rdb-sync 2>/dev/null || echo "0")
echo "  Stream 메시지: ${STREAM_BEFORE}"

# 4. 앱 강제 종료 (SIGKILL)
echo ""
echo "  Backend 강제 종료!"
annotate "Backend 강제 종료 (docker kill)" "app-kill"
docker kill "${BACKEND_CONTAINER}"
echo "  ${BACKEND_CONTAINER} killed"

# 5. 잠시 대기 후 재시작
echo "  5초 대기..."
sleep 5

echo ""
echo "  Backend 재시작!"
docker start "${BACKEND_CONTAINER}"
echo "  ${BACKEND_CONTAINER} started"
echo "  앱 기동 대기 중... (20초)"
sleep 20
annotate "Backend 재시작 완료" "app-restart"

# 6. k6 종료 대기
echo ""
echo "  k6 종료 대기 중..."
wait "${K6_PID}" || true

# 7. k6 종료 시점 기록
echo ""
echo "  k6 종료 시점: $(date '+%H:%M:%S')"

# 8. 1초 폴링으로 정합성 수렴 대기
echo ""
echo "============================================="
echo "  수렴 대기 (1초 간격 폴링)"
echo "============================================="

MAX_WAIT=300  # 최대 5분 대기
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

# 9. 결과
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

# 10. Stream 상태
STREAM_AFTER=$(docker exec fairbid-redis-1 redis-cli XLEN stream:bid-rdb-sync 2>/dev/null || echo "0")
PENDING_INFO=$(docker exec fairbid-redis-1 redis-cli XPENDING stream:bid-rdb-sync bid-rdb-sync-group 2>/dev/null)
PENDING_COUNT=$(echo "$PENDING_INFO" | head -1)

echo "  Stream 총 메시지: ${STREAM_AFTER}"
echo "  PENDING (미처리): ${PENDING_COUNT}"
echo ""
echo "  Grafana 확인: ${GRAFANA_URL}"
echo "============================================="
