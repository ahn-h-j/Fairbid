#!/bin/bash
# Phase 1 장애 주입 스크립트
#
# k6 테스트 시작 후 30초 뒤에 실행한다.
# 타임라인: Annotation(장애) → DB pause 10초 → Annotation(복구) → DB unpause
#
# 사용법:
#   1. k6 run k6/scenarios/bid-sync-test.js   (터미널 1)
#   2. sleep 30 && ./k6/scripts/phase1-fault-inject.sh  (터미널 2)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PAUSE_DURATION="${1:-10}"  # 기본 10초

echo "🔴 DB 장애 주입 시작..."
bash "${SCRIPT_DIR}/grafana-annotation.sh" "DB 장애 주입 (docker pause mysql)" "fault-injection"
docker pause fairbid-mysql-1 2>/dev/null || docker pause fairbid_mysql_1 2>/dev/null || docker pause mysql 2>/dev/null
echo "⏳ ${PAUSE_DURATION}초간 장애 유지..."
sleep "${PAUSE_DURATION}"

echo "🟢 DB 복구..."
docker unpause fairbid-mysql-1 2>/dev/null || docker unpause fairbid_mysql_1 2>/dev/null || docker unpause mysql 2>/dev/null
bash "${SCRIPT_DIR}/grafana-annotation.sh" "DB 복구 (docker unpause mysql)" "recovery"
echo "✅ 장애 주입 완료. Grafana에서 결과를 확인하세요."
