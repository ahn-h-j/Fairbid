#!/bin/bash
# Redis vs RDB 입찰 정합성 비교 스크립트
#
# Redis의 auction:* 해시에서 totalBidCount를 합산하고,
# RDB의 bid 테이블 COUNT(*)와 비교한다.
#
# 사용법: ./k6/scripts/check-consistency.sh

REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
MYSQL_DB="${MYSQL_DB:-fairbid}"

echo "========================================="
echo "🔍 Redis-RDB 입찰 정합성 비교"
echo "========================================="

# Redis: auction:* 키의 totalBidCount 합산
REDIS_TOTAL=$(docker exec fairbid-redis-1 redis-cli KEYS "auction:*" 2>/dev/null \
  || docker exec fairbid_redis_1 redis-cli KEYS "auction:*" 2>/dev/null \
  || redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} KEYS "auction:*")

REDIS_COUNT=0
while IFS= read -r key; do
    # auction:closing (Sorted Set) 건너뛰기
    if [[ "$key" == "auction:closing" ]]; then
        continue
    fi
    count=$(docker exec fairbid-redis-1 redis-cli HGET "$key" totalBidCount 2>/dev/null \
      || docker exec fairbid_redis_1 redis-cli HGET "$key" totalBidCount 2>/dev/null \
      || redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} HGET "$key" totalBidCount)
    if [[ -n "$count" && "$count" != "(nil)" ]]; then
        REDIS_COUNT=$((REDIS_COUNT + count))
    fi
done <<< "$REDIS_TOTAL"

# RDB: bid 테이블 COUNT(*)
RDB_COUNT=$(docker exec fairbid-mysql-1 mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D"${MYSQL_DB}" -se "SELECT COUNT(*) FROM bid;" 2>/dev/null \
  || docker exec fairbid_mysql_1 mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D"${MYSQL_DB}" -se "SELECT COUNT(*) FROM bid;" 2>/dev/null \
  || mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D"${MYSQL_DB}" -se "SELECT COUNT(*) FROM bid;" 2>/dev/null)

DIFF=$((REDIS_COUNT - RDB_COUNT))

echo ""
echo "📊 결과:"
echo "  Redis 입찰 수: ${REDIS_COUNT}"
echo "  RDB 입찰 수:   ${RDB_COUNT}"
echo "  차이 (불일치): ${DIFF}"
echo ""

if [[ "$DIFF" -eq 0 ]]; then
    echo "✅ 정합성 일치"
else
    echo "❌ 불일치 감지! (Redis가 ${DIFF}건 더 많음)"
    if [[ "$REDIS_COUNT" -gt 0 ]]; then
        RATE=$(awk "BEGIN {printf \"%.2f\", ${DIFF} * 100 / ${REDIS_COUNT}}")
        echo "   불일치율: ${RATE}%"
    fi
fi
echo "========================================="
