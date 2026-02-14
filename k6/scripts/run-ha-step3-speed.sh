#!/bin/bash
# HA Step 3: Sentinel - 자동 Failover 속도 테스트
#
# 백엔드를 중단하지 않고, Master 장애 후 Sentinel 자동 Failover 소요 시간만 측정한다.
# 데이터 정합성 테스트는 run-ha-step3-sentinel.sh 참조.
#
# Step 3는 Sentinel이 자동으로 Slave를 승격하고, Lettuce가 자동 재연결하므로
# 백엔드 재시작이 필요 없다. → Step 2보다 빨라야 한다.
# 다운타임 = Master 장애 ~ Lettuce 자동 재연결 완료
#
# 사전 조건: docker compose up -d
# 사용법: bash k6/scripts/run-ha-step3-speed.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.yml"
ENV_FILE="${PROJECT_DIR}/.env"
DC="docker compose -f ${COMPOSE_FILE}"

# ── sentinel 프로필 활성화 ──
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
echo "  HA Step 3: Sentinel 자동 Failover 속도 테스트"
echo "============================================="
echo ""
echo "  백엔드를 중단하지 않고 순수 자동 Failover 속도만 측정"
echo "  Sentinel 감지 + Lettuce 자동 재연결 (백엔드 재시작 없음)"
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

# ── 3. Master 장애 발생 (백엔드는 계속 실행) ──
echo ""
FAULT_EPOCH=$(date +%s)
FAULT_TS=$(date '+%H:%M:%S')
# docker kill: 컨테이너를 즉시 종료하여 실제 서버 장애를 시뮬레이션한다.
# 고정 IP 네트워크(172.22.0.0/24)를 사용하므로 컨테이너가 죽어도 IP는 유지된다.
# Sentinel이 connection refused로 sdown 감지 → failover 정상 진행.
$DC kill redis
echo "  Redis Master 장애 발생! (kill) [${FAULT_TS}]"
echo "  백엔드는 계속 실행 중 — Sentinel + Lettuce 자동 복구 대기"

# ── 4. 쓰기 복구 확인 (Sentinel failover 완료 = 새 Master 쓰기 가능) ──
# ReadFrom.MASTER 설정이므로 읽기도 Master를 거친다.
# 하지만 이 테스트의 목적은 "Slave→Master 승격 후 쓰기가 가능해진 시점"을 측정하는 것이므로
# Sentinel에게 새 Master를 질의한 뒤, 해당 노드에 직접 SET 명령으로 쓰기 가능 여부를 확인한다.
echo ""
echo "  쓰기 복구 대기 중 (Sentinel failover → 새 Master 쓰기 가능)..."
RECOVERY_TS="N/A"
RECOVERY_EPOCH=0
for i in $(seq 1 90); do
    # Sentinel에게 현재 Master 주소를 질의
    NEW_MASTER_IP=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>&1 | head -1 | tr -d '\r')

    # 새 Master가 기존과 다르면 (failover 발생) 쓰기 테스트
    if [ -n "$NEW_MASTER_IP" ] && [ "$NEW_MASTER_IP" != "$MASTER_HOST" ]; then
        # 새 Master IP에 해당하는 컨테이너에서 SET 명령으로 쓰기 가능 여부 확인
        WRITE_RESULT=""
        if [ "$NEW_MASTER_IP" = "172.22.0.11" ]; then
            WRITE_RESULT=$($DC exec -T redis-slave-1 redis-cli SET failover-test ok 2>&1 | tr -d '\r')
        elif [ "$NEW_MASTER_IP" = "172.22.0.12" ]; then
            WRITE_RESULT=$($DC exec -T redis-slave-2 redis-cli SET failover-test ok 2>&1 | tr -d '\r')
        fi

        if [ "$WRITE_RESULT" = "OK" ]; then
            RECOVERY_EPOCH=$(date +%s)
            RECOVERY_TS=$(date '+%H:%M:%S')
            echo "  쓰기 복구 확인! 새 Master(${NEW_MASTER_IP})에 쓰기 성공 [${RECOVERY_TS}]"
            break
        fi
    fi
    sleep 1
done

# 다운타임 계산
if [ "$RECOVERY_EPOCH" -gt 0 ]; then
    DOWNTIME=$((RECOVERY_EPOCH - FAULT_EPOCH))
else
    DOWNTIME="측정 실패"
fi

# ── 5. k6 종료 대기 ──
echo ""
echo "  k6 종료 대기 중..."
wait "${K6_PID}" || true

# ══════════════════════════════════════════════════════════════
# 최종 결과 요약
# ══════════════════════════════════════════════════════════════
echo ""
echo "============================================="
echo "  HA Step 3: 속도 테스트 결과"
echo "============================================="
echo ""
NEW_MASTER=$($DC exec -T sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster 2>&1 | head -1 | tr -d '\r')
echo "  Sentinel 자동 Failover 다운타임 (쓰기 복구 기준)"
echo "  ─────────────────────────────"
echo "    장애 발생   : ${FAULT_TS}"
echo "    쓰기 복구   : ${RECOVERY_TS}"
echo "    새 Master   : ${NEW_MASTER:-확인 실패}"
echo "    다운타임    : ${DOWNTIME}초"
echo "    (Sentinel 감지 + Slave→Master 승격 + 쓰기 가능)"
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
