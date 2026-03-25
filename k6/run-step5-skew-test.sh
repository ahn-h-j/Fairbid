#!/bin/bash -l
# =============================================================================
# Step 5-1 시나리오 B: 스케일아웃 후 WebSocket 커넥션 쏠림 테스트
#
# 모놀리스 환경에서 스케일아웃 후에도 기존 WebSocket 커넥션이
# 새 서버로 이동하지 않는 것을 증명한다.
#
# 사용법:
#   bash k6/run-step5-skew-test.sh
#
# 흐름:
#   1. ASG 1대 확인 (desired=1)
#   2. k6 WebSocket 연결 시작 (백그라운드)
#   3. 30초 후 ASG desired=2로 증가
#   4. 서버B healthy 후 각 인스턴스의 커넥션 수 직접 조회
#   5. 서버A=N명, 서버B=0명 확인
# =============================================================================

ALB_URL="http://fairbid-alb-490283096.ap-northeast-2.elb.amazonaws.com"
ASG_NAME="fairbid-app-asg"
REGION="ap-northeast-2"
DURATION=300

echo ""
echo "================================================================"
echo "  Step 5-1 시나리오 B: 스케일아웃 후 WebSocket 커넥션 쏠림 테스트"
echo "================================================================"
echo ""
echo "  목적: 스케일아웃 후에도 WebSocket 커넥션이 새 서버로 안 옮겨가는 것을 증명"
echo ""


# ── 1. ASG 상태 확인 (1대여야 함) ──
echo "────────────────────────────────────────"
echo "  [1/8] 현재 ASG 상태 확인"
echo "────────────────────────────────────────"
echo ""

CURRENT_DESIRED=$(aws autoscaling describe-auto-scaling-groups \
    --auto-scaling-group-names $ASG_NAME \
    --region $REGION \
    --query "AutoScalingGroups[0].DesiredCapacity" \
    --output text 2>/dev/null)

IN_SERVICE=$(aws autoscaling describe-auto-scaling-groups \
    --auto-scaling-group-names $ASG_NAME \
    --region $REGION \
    --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | length(@)" \
    --output text 2>/dev/null)

echo "  DesiredCapacity : $CURRENT_DESIRED"
echo "  InService       : $IN_SERVICE"
echo ""

if [ "$IN_SERVICE" -ne 1 ] 2>/dev/null; then
    echo "  ⚠️  InService가 1대가 아닙니다. desired=1로 조정 후 다시 시도하세요."
    echo "  aws autoscaling set-desired-capacity --auto-scaling-group-name $ASG_NAME --desired-capacity 1 --region $REGION"
    exit 1
fi

# 서버A 인스턴스 ID 기록
SERVER_A_ID=$(aws autoscaling describe-auto-scaling-groups \
    --auto-scaling-group-names $ASG_NAME \
    --region $REGION \
    --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | [0].InstanceId" \
    --output text 2>/dev/null)

SERVER_A_PRIVATE_IP=$(aws ec2 describe-instances \
    --instance-ids $SERVER_A_ID \
    --region $REGION \
    --query "Reservations[0].Instances[0].PrivateIpAddress" \
    --output text 2>/dev/null)

echo "  서버A : $SERVER_A_ID ($SERVER_A_PRIVATE_IP)"
echo ""


# ── 2. k6 시작 (백그라운드) ──
echo "────────────────────────────────────────"
echo "  [2/8] k6 WebSocket 연결 시작"
echo "────────────────────────────────────────"
echo ""

k6 run \
    --env BASE_URL=$ALB_URL \
    --env DURATION=$DURATION \
    k6/scenarios/ws-connection-skew-test.js &
K6_PID=$!

echo "  k6 PID : $K6_PID"
echo ""


# ── 3. 구독자 연결 대기 ──
echo "────────────────────────────────────────"
echo "  [3/8] 구독자 연결 대기 (30초)"
echo "────────────────────────────────────────"
echo ""

sleep 30


# ── 4. 스케일아웃 전 커넥션 수 확인 ──
echo "────────────────────────────────────────"
echo "  [4/8] 스케일아웃 전 커넥션 수 확인"
echo "────────────────────────────────────────"
echo ""

echo -n "  서버A : "
curl -s "$ALB_URL/actuator/wsconnections" 2>/dev/null || echo "(조회 실패)"
echo ""
echo ""


# ── 5. 스케일아웃: desired=2 ──
echo "────────────────────────────────────────"
echo "  [5/8] ASG 스케일아웃 (desired=2)"
echo "────────────────────────────────────────"
echo ""

aws autoscaling set-desired-capacity \
    --auto-scaling-group-name $ASG_NAME \
    --desired-capacity 2 \
    --region $REGION 2>/dev/null

echo "  ✅ desired=2 설정 완료"
echo ""


# ── 6. 서버B InService + ALB healthy 대기 ──
echo "────────────────────────────────────────"
echo "  [6/8] 서버B InService + ALB healthy 대기"
echo "────────────────────────────────────────"
echo ""

# 6-1. ASG InService 대기
echo "  [ASG InService 대기]"
while true; do
    CURRENT_IN_SERVICE=$(aws autoscaling describe-auto-scaling-groups \
        --auto-scaling-group-names $ASG_NAME \
        --region $REGION \
        --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'] | length(@)" \
        --output text 2>/dev/null)

    echo "  [$(date '+%H:%M:%S')] InService: $CURRENT_IN_SERVICE / 2"

    if [ "$CURRENT_IN_SERVICE" -ge 2 ] 2>/dev/null; then
        echo ""
        echo "  ✅ 서버B InService 완료!"
        break
    fi

    if ! kill -0 $K6_PID 2>/dev/null; then
        echo ""
        echo "  ⚠️ k6가 먼저 종료됨 (서버B가 뜨기 전에 시간 초과)"
        break
    fi

    sleep 15
done
echo ""

# 6-2. ALB healthy 대기
TG_ARN=$(aws elbv2 describe-target-groups --names fairbid-app-tg \
    --region $REGION --query "TargetGroups[0].TargetGroupArn" --output text 2>/dev/null)

echo "  [ALB healthy 대기]"
while true; do
    HEALTHY=$(aws elbv2 describe-target-health --target-group-arn "$TG_ARN" \
        --region $REGION \
        --query "TargetHealthDescriptions[?TargetHealth.State=='healthy'] | length(@)" \
        --output text 2>/dev/null)

    echo "  [$(date '+%H:%M:%S')] ALB healthy: $HEALTHY / 2"

    if [ "$HEALTHY" -ge 2 ] 2>/dev/null; then
        echo ""
        echo "  ✅ ALB healthy 2대 확인!"
        break
    fi

    if ! kill -0 $K6_PID 2>/dev/null; then
        echo ""
        echo "  ⚠️ k6가 먼저 종료됨"
        break
    fi

    sleep 15
done
echo ""


# ── 7. 서버별 커넥션 수 조회 (핵심 결과) ──
echo "────────────────────────────────────────"
echo "  [7/8] 스케일아웃 후 서버별 커넥션 수 조회"
echo "────────────────────────────────────────"
echo ""

# 인스턴스 목록
INSTANCE_IDS=$(aws autoscaling describe-auto-scaling-groups \
    --auto-scaling-group-names $ASG_NAME \
    --region $REGION \
    --query "AutoScalingGroups[0].Instances[?LifecycleState=='InService'].InstanceId" \
    --output text 2>/dev/null)

echo "  인스턴스 목록:"
for INSTANCE_ID in $INSTANCE_IDS; do
    PRIVATE_IP=$(aws ec2 describe-instances \
        --instance-ids $INSTANCE_ID \
        --region $REGION \
        --query "Reservations[0].Instances[0].PrivateIpAddress" \
        --output text 2>/dev/null)

    if [ "$INSTANCE_ID" = "$SERVER_A_ID" ]; then
        LABEL="서버A (기존)"
    else
        LABEL="서버B (신규)"
    fi

    echo "    $LABEL : $INSTANCE_ID ($PRIVATE_IP)"
done

echo ""
echo "  ALB를 통한 wsconnections 조회 (10회):"
echo "  ┌──────┬──────────────────┬────────────────┐"
echo "  │  #   │  서버 IP         │  커넥션 수     │"
echo "  ├──────┼──────────────────┼────────────────┤"

for i in $(seq 1 10); do
    RESULT=$(curl -s "$ALB_URL/actuator/wsconnections" 2>/dev/null)
    # JSON에서 serverIp와 activeConnections 추출
    IP=$(echo "$RESULT" | grep -o '"serverIp":"[^"]*"' | cut -d'"' -f4 2>/dev/null)
    CONN=$(echo "$RESULT" | grep -o '"activeConnections":[0-9]*' | cut -d: -f2 2>/dev/null)

    if [ -n "$IP" ] && [ -n "$CONN" ]; then
        printf "  │  %-3s │  %-15s │  %-13s │\n" "$i" "$IP" "${CONN}명"
    else
        printf "  │  %-3s │  %-15s │  %-13s │\n" "$i" "(실패)" "-"
    fi
    sleep 1
done

echo "  └──────┴──────────────────┴────────────────┘"
echo ""
echo "  기대 결과: 서버A = 20명, 서버B = 0명"
echo "  → REST는 분산되지만 WebSocket 커넥션은 서버A에 고정"
echo ""


# ── 8. 정리 ──
echo "────────────────────────────────────────"
echo "  [8/8] 정리"
echo "────────────────────────────────────────"
echo ""

echo "  k6 결과 대기..."
wait $K6_PID
echo ""

echo "  ASG desired=1로 복원..."
aws autoscaling set-desired-capacity \
    --auto-scaling-group-name $ASG_NAME \
    --desired-capacity 1 \
    --region $REGION 2>/dev/null
echo "  ✅ 복원 완료"
echo ""

echo "================================================================"
echo "  테스트 완료"
echo "================================================================"
echo ""
