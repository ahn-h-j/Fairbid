#!/bin/bash
# HA Step 1: Redis SPOF 체감 테스트
#
# 단일 Redis 인스턴스가 죽으면 입찰 서비스가 완전 중단되는지 증명한다.
# 기존 Phase 1(DB 장애)과 동일한 오케스트레이터 구조, 대상만 Redis.
#
# 타임라인:
#   0초  : k6 시작 (백그라운드)
#   60초 : Grafana Annotation + docker stop redis
#   80초 : Grafana Annotation + docker start redis
#   120초: k6 종료 → 결과 출력
#
# 사용법: bash k6/scripts/run-ha-step1-spof.sh [장애유지초] [베이스라인초]
#   예: bash k6/scripts/run-ha-step1-spof.sh 20 60

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
GRAFANA_AUTH="admin:admin"
PAUSE_DURATION="${1:-20}"
BASELINE_DURATION="${2:-60}"

# Redis 컨테이너 이름 자동 감지
REDIS_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i redis | head -1)
if [ -z "$REDIS_CONTAINER" ]; then
    echo "  Redis 컨테이너를 찾을 수 없습니다."
    exit 1
fi
echo "  Redis 컨테이너: ${REDIS_CONTAINER}"

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
echo "  HA Step 1: Redis SPOF 체감 테스트"
echo "============================================="
echo ""
echo "  타임라인:"
echo "  0~${BASELINE_DURATION}초  : Baseline (정상 부하)"
echo "  ${BASELINE_DURATION}~$((BASELINE_DURATION + PAUSE_DURATION))초 : Redis 장애 (docker kill)"
echo "  $((BASELINE_DURATION + PAUSE_DURATION))~120초 : 복구 후 안정화"
echo ""

# 1. k6 백그라운드 실행 (HA 전용 시나리오)
echo "  k6 부하 테스트 시작 (백그라운드)..."
k6 run "${PROJECT_DIR}/k6/scenarios/ha-redis-spof.js" &
K6_PID=$!
echo "  k6 PID: ${K6_PID}"

# 2. Baseline 대기
echo ""
echo "  Baseline 측정 중... (${BASELINE_DURATION}초 대기)"
sleep "${BASELINE_DURATION}"

# 3. 장애 주입 (docker kill로 Redis 즉시 종료)
echo ""
echo "  Redis 장애 주입!"
annotate "Redis 장애 주입 (docker kill ${REDIS_CONTAINER})" "fault-injection"
docker kill "${REDIS_CONTAINER}"
echo "  ${REDIS_CONTAINER} killed"

# 4. 장애 유지
echo "  장애 유지 중... (${PAUSE_DURATION}초)"
sleep "${PAUSE_DURATION}"

# 5. 복구
echo ""
echo "  Redis 복구!"
docker start "${REDIS_CONTAINER}"
echo "  ${REDIS_CONTAINER} started"
echo "  Redis 기동 대기 중... (5초)"
sleep 5
annotate "Redis 복구 (docker start ${REDIS_CONTAINER})" "recovery"

# 6. k6 종료 대기
echo ""
echo "  k6 종료 대기 중..."
wait "${K6_PID}" || true

# 7. 결과 출력
echo ""
echo "============================================="
echo "  결과 확인"
echo "============================================="
echo ""
echo "  핵심 확인 포인트 (Grafana에서):"
echo "    1. Baseline 구간: 정상 TPS, 에러율 0%"
echo "    2. 장애 구간: TPS 급락 또는 0, 에러율 100%"
echo "    3. 복구 구간: TPS 복구, 에러율 0%"
echo ""
echo "  Phase 1(DB 장애)과 비교:"
echo "    - DB 장애: p95 급증 (블로킹), 일부 요청은 Redis에서 성공"
echo "    - Redis 장애: 입찰 자체 불가 (Source of Truth 소실)"
echo ""
echo "  Grafana 확인: ${GRAFANA_URL}"
echo "============================================="
