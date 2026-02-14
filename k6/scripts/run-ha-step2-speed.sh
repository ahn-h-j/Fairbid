#!/bin/bash
# HA Step 2: Replication - 수동 Failover 속도 테스트
#
# Master 장애 후 수동 Failover 소요 시간만 측정한다.
# 데이터 정합성 테스트는 run-ha-step2-replication.sh 참조.
#
# Step 2는 Sentinel이 없으므로 수동으로 Slave 승격 + 백엔드 재시작이 필요하다.
# 다운타임 = Master 장애 ~ 수동 Failover + 백엔드 재시작 완료
#
# Step 3 speed 테스트와 비교하여 Sentinel 자동 Failover의 이점을 수치로 증명한다.
#
# 사전 조건: docker compose up -d
# 사용법: bash k6/scripts/run-ha-step2-speed.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.yml"
DC="docker compose -f ${COMPOSE_FILE}"


echo ""
echo "============================================="
echo "  HA Step 2: 수동 Failover 속도 테스트"
echo "============================================="
echo ""
echo "  Slave 승격 + docker-compose 설정 변경 + 백엔드 재시작"
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

# ── 3. Master 장애 발생 (백엔드는 계속 실행 중) ──
echo ""
FAULT_EPOCH=$(date +%s)
FAULT_TS=$(date '+%H:%M:%S')
# docker kill: 컨테이너를 즉시 종료하여 실제 서버 장애를 시뮬레이션한다.
$DC kill redis
echo "  Redis Master 장애 발생! (kill) [${FAULT_TS}]"

# ── 4. 즉시 수동 Failover ──
# Sentinel이 없으므로 운영자가 직접 수행하는 절차를 스크립트로 자동화한다.
# 백엔드는 에러를 뱉으며 돌아가는 상태 — 실제 운영 시나리오와 동일
echo ""
echo "  수동 Failover 시작 [$(date '+%H:%M:%S')]"

echo "  [1/4] Slave-1 승격 (REPLICAOF NO ONE)"
$DC exec -T redis-slave-1 redis-cli REPLICAOF NO ONE

echo "  [2/4] Slave-2 → Slave-1에 연결"
$DC exec -T redis-slave-2 redis-cli REPLICAOF redis-slave-1 6379

echo "  [3/4] 앱 Redis 설정 변경 (redis → redis-slave-1)"
sed -i 's/SPRING_DATA_REDIS_HOST: redis/SPRING_DATA_REDIS_HOST: redis-slave-1/' "${COMPOSE_FILE}"

echo "  [4/4] 앱 재시작 (새 Master로 연결)"
# docker compose가 config 변경 감지 → 자동 컨테이너 재생성
# 죽은 redis도 자동 재시작됨 (backend는 redis-slave-1에 연결하므로 무관)
$DC up -d backend

# ── 5. 서비스 복구 확인 (백엔드 재시작 대기) ──
echo ""
echo "  서비스 복구 대기 중 (백엔드 재시작)..."
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
echo "  HA Step 2: 속도 테스트 결과"
echo "============================================="
echo ""
echo "  수동 Failover 다운타임"
echo "  ─────────────────────────────"
echo "    장애 발생   : ${FAULT_TS}"
echo "    서비스 복구 : ${RECOVERY_TS}"
echo "    다운타임    : ${DOWNTIME}초"
echo "    (백엔드 중단 + Slave 승격 + 설정 변경 + 백엔드 재시작)"
echo ""
echo "  → Step 3 speed 테스트와 비교:"
echo "    Step 2: 수동 승격 + 백엔드 재시작 필요"
echo "    Step 3: Sentinel 자동 감지 + Lettuce 자동 재연결 (재시작 불필요)"
echo ""
echo "============================================="

# ── docker-compose.yml 원복 ──
sed -i 's/SPRING_DATA_REDIS_HOST: redis-slave-1/SPRING_DATA_REDIS_HOST: redis/' "${COMPOSE_FILE}"
echo ""
echo "  docker-compose.yml 원복 완료"
