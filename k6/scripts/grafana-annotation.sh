#!/bin/bash
# Grafana Annotation 생성 스크립트
#
# 장애 주입/복구 시점을 Grafana 그래프에 수직선으로 표시한다.
# 사용법:
#   ./grafana-annotation.sh "DB 장애 주입" fault-injection
#   ./grafana-annotation.sh "DB 복구" recovery

GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASS="${GRAFANA_PASS:-admin}"

TEXT="${1:?Usage: $0 <text> [tag]}"
TAG="${2:-event}"

curl -s -X POST "${GRAFANA_URL}/api/annotations" \
  -u "${GRAFANA_USER}:${GRAFANA_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"text\":\"${TEXT}\",\"tags\":[\"${TAG}\"]}" \
  && echo " ✅ Annotation 생성: ${TEXT}" \
  || echo " ❌ Annotation 실패"
