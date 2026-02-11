#!/bin/bash
# Phase 1 전체 테스트 오케스트레이터
#
# 터미널 하나로 Baseline → 장애 주입 → 복구까지 자동 실행
#
# 타임라인:
#   0초  : k6 시작 (백그라운드)
#   60초 : Grafana Annotation + docker stop mysql
#   80초 : Grafana Annotation + docker start mysql
#   90초 : k6 종료 → 결과 출력
#   91초 : Redis vs RDB 정합성 비교
#
# 사용법: bash k6/scripts/run-phase1-test.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
GRAFANA_AUTH="admin:admin"
PAUSE_DURATION="${1:-20}"
BASELINE_DURATION="${2:-60}"

# MySQL 컨테이너 이름 자동 감지
MYSQL_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i mysql | head -1)
if [ -z "$MYSQL_CONTAINER" ]; then
    echo "❌ MySQL 컨테이너를 찾을 수 없습니다."
    exit 1
fi
echo "📦 MySQL 컨테이너: ${MYSQL_CONTAINER}"

# Grafana Annotation 함수
annotate() {
    local text="$1"
    local tag="$2"
    curl -s -X POST "${GRAFANA_URL}/api/annotations" \
      -u "${GRAFANA_AUTH}" \
      -H "Content-Type: application/json" \
      -d "{\"text\":\"${text}\",\"tags\":[\"${tag}\"]}" > /dev/null 2>&1
    echo "  📝 Annotation: ${text}"
}

echo ""
echo "============================================="
echo "🧪 Phase 1: 동기 RDB 동기화 문제점 증명"
echo "============================================="
echo ""
echo "⏱️ 타임라인:"
echo "  0~${BASELINE_DURATION}초  : Baseline (정상 부하)"
echo "  ${BASELINE_DURATION}~$((BASELINE_DURATION + PAUSE_DURATION))초 : DB 장애 (docker stop)"
echo "  $((BASELINE_DURATION + PAUSE_DURATION))~120초 : 복구 후 안정화"
echo ""

# 1. k6 백그라운드 실행
echo "🚀 k6 부하 테스트 시작 (백그라운드)..."
k6 run "${PROJECT_DIR}/k6/scenarios/bid-sync-test.js" &
K6_PID=$!
echo "  k6 PID: ${K6_PID}"

# 2. Baseline 대기
echo ""
echo "⏳ Baseline 측정 중... (${BASELINE_DURATION}초 대기)"
sleep "${BASELINE_DURATION}"

# 3. 장애 주입 (docker stop으로 커넥션 완전 끊기)
echo ""
echo "🔴 DB 장애 주입!"
annotate "DB 장애 주입 (docker stop ${MYSQL_CONTAINER})" "fault-injection"
docker stop "${MYSQL_CONTAINER}"
echo "  ✅ ${MYSQL_CONTAINER} stopped"

# 4. 장애 유지
echo "⏳ 장애 유지 중... (${PAUSE_DURATION}초)"
sleep "${PAUSE_DURATION}"

# 5. 복구
echo ""
echo "🟢 DB 복구!"
docker start "${MYSQL_CONTAINER}"
echo "  ✅ ${MYSQL_CONTAINER} started"
echo "⏳ DB 기동 대기 중... (10초)"
sleep 10
annotate "DB 복구 (docker start ${MYSQL_CONTAINER})" "recovery"

# 6. k6 종료 대기
echo ""
echo "⏳ k6 종료 대기 중..."
wait "${K6_PID}" || true

# 7. 정합성 비교
echo ""
echo "============================================="
echo "🔍 Redis-RDB 정합성 비교"
echo "============================================="

# Redis 입찰 수 (Prometheus 메트릭에서 가져오기)
PROM_DATA=$(curl -s "http://localhost:8080/actuator/prometheus")
REDIS_COUNT=$(echo "$PROM_DATA" | grep 'fairbid_bid_redis_count{' | awk '{print $NF}' | head -1 | cut -d'.' -f1)
RDB_COUNT=$(echo "$PROM_DATA" | grep 'fairbid_bid_rdb_count{' | awk '{print $NF}' | head -1 | cut -d'.' -f1)

echo "  Redis 입찰 수: ${REDIS_COUNT:-N/A}"
echo "  RDB 입찰 수:   ${RDB_COUNT:-N/A}"

if [ -n "$REDIS_COUNT" ] && [ -n "$RDB_COUNT" ]; then
    DIFF=$((REDIS_COUNT - RDB_COUNT))
    echo "  차이 (불일치): ${DIFF}"
    if [ "$DIFF" -eq 0 ]; then
        echo "  ✅ 정합성 일치"
    else
        echo "  ❌ 불일치 감지!"
    fi
fi

echo ""
echo "============================================="
echo "📊 Grafana 확인: ${GRAFANA_URL}"
echo "  → 대시보드에서 Annotation 포함 그래프 스크린샷 찍기"
echo "============================================="
