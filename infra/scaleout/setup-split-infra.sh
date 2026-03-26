#!/bin/bash
# ============================================================
# Step 5-2: REST/WebSocket 분리 인프라 구성 스크립트
# ============================================================
#
# 사전 조건:
#   - 기존 ALB (fairbid-alb), API Target Group (fairbid-app-tg), API ASG 존재
#   - AWS CLI 설정 완료 (aws configure)
#   - 현재 AMI에 최신 코드 반영 (SERVER_ROLE 환경변수 지원)
#
# 이 스크립트가 하는 것:
#   1. WS용 Target Group 생성
#   2. ALB 리스너 규칙 추가 (/ws* → WS Target Group)
#   3. WS용 Launch Template 생성
#   4. WS용 ASG 생성
#
# 사용법:
#   chmod +x setup-split-infra.sh
#   ./setup-split-infra.sh
# ============================================================

set -euo pipefail

# ===== 설정값 (환경에 맞게 수정) =====
REGION="ap-northeast-2"
VPC_ID="vpc-0b8e1c77e4ef2a090"              # 기존 VPC
SUBNET_A="subnet-02c25e2301a94e8da"          # ap-northeast-2a
SUBNET_C="subnet-067f45ab54ac16862"          # ap-northeast-2c
SECURITY_GROUP="sg-05a8de14fbc998bb6"        # 기존 App 보안그룹
ALB_ARN=""                                   # 기존 ALB ARN (아래에서 조회)
LISTENER_ARN=""                              # 기존 HTTP 리스너 ARN (아래에서 조회)
API_TG_ARN=""                                # 기존 API Target Group ARN
AMI_ID=""                                    # 최신 AMI ID (아래에서 조회)
KEY_NAME="fairbid-key"                       # EC2 키페어
INSTANCE_TYPE="t3.small"
INFRA_HOST="172.31.34.73"                    # 인프라 서버 IP

echo "================================================"
echo "Step 5-2: REST/WebSocket 분리 인프라 구성"
echo "================================================"

# ===== 기존 리소스 ARN 조회 =====
echo ""
echo "[1/6] 기존 리소스 조회..."

ALB_ARN=$(aws elbv2 describe-load-balancers \
  --names fairbid-alb \
  --query 'LoadBalancers[0].LoadBalancerArn' \
  --output text --region $REGION)
echo "  ALB: $ALB_ARN"

LISTENER_ARN=$(aws elbv2 describe-listeners \
  --load-balancer-arn $ALB_ARN \
  --query 'Listeners[0].ListenerArn' \
  --output text --region $REGION)
echo "  Listener: $LISTENER_ARN"

API_TG_ARN=$(aws elbv2 describe-target-groups \
  --names fairbid-app-tg \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text --region $REGION)
echo "  API TG: $API_TG_ARN"

# ===== 1. WS Target Group 생성 =====
echo ""
echo "[2/6] WS Target Group 생성..."

WS_TG_ARN=$(aws elbv2 create-target-group \
  --name fairbid-ws-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id $VPC_ID \
  --target-type instance \
  --health-check-path /actuator/health \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text --region $REGION)
echo "  WS TG: $WS_TG_ARN"

# Stickiness 비활성화 (WS 커넥션은 ALB가 아닌 TCP 수준에서 유지)
aws elbv2 modify-target-group-attributes \
  --target-group-arn $WS_TG_ARN \
  --attributes Key=stickiness.enabled,Value=false \
  --region $REGION > /dev/null

# ===== 2. ALB 리스너 규칙 추가 =====
echo ""
echo "[3/6] ALB 리스너 규칙 추가 (/ws* → WS TG)..."

# /ws 경로 패턴 → WS Target Group으로 라우팅
aws elbv2 create-rule \
  --listener-arn $LISTENER_ARN \
  --priority 10 \
  --conditions Field=path-pattern,Values='/ws,/ws/*' \
  --actions Type=forward,TargetGroupArn=$WS_TG_ARN \
  --region $REGION > /dev/null
echo "  규칙 추가 완료: /ws* → WS TG"

# ===== 3. 기존 API ASG에 SERVER_ROLE=api 반영 =====
echo ""
echo "[4/6] API Launch Template 업데이트 (SERVER_ROLE=api)..."

# 기존 Launch Template 이름 확인
API_LT_NAME="fairbid-app-lt"

# 최신 AMI 조회 (기존 Launch Template에서)
AMI_ID=$(aws ec2 describe-launch-template-versions \
  --launch-template-name $API_LT_NAME \
  --versions '$Latest' \
  --query 'LaunchTemplateVersions[0].LaunchTemplateData.ImageId' \
  --output text --region $REGION)
echo "  AMI: $AMI_ID"

# API Launch Template 새 버전 생성 (SERVER_ROLE=api 추가)
API_USER_DATA=$(cat <<'USERDATA' | base64 -w 0
#!/bin/bash
git config --system --add safe.directory /home/ubuntu/Fairbid
cd /home/ubuntu/Fairbid
git pull origin main

docker stop $(docker ps -aq) 2>/dev/null || true
docker rm $(docker ps -aq) 2>/dev/null || true

# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 216989097509.dkr.ecr.ap-northeast-2.amazonaws.com

# SERVER_ROLE=api 로 실행
echo "SERVER_ROLE=api" >> .env
echo "INFRA_HOST=172.31.34.73" >> .env

docker compose -f infra/scaleout/docker-compose-app.yml --env-file .env up -d --pull always
USERDATA
)

aws ec2 create-launch-template-version \
  --launch-template-name $API_LT_NAME \
  --source-version '$Latest' \
  --launch-template-data "{\"UserData\":\"$API_USER_DATA\"}" \
  --region $REGION > /dev/null
echo "  API Launch Template 새 버전 생성 완료"

# ===== 4. WS Launch Template + ASG 생성 =====
echo ""
echo "[5/6] WS Launch Template + ASG 생성..."

# WS용 user-data
WS_USER_DATA=$(cat <<'USERDATA' | base64 -w 0
#!/bin/bash
git config --system --add safe.directory /home/ubuntu/Fairbid
cd /home/ubuntu/Fairbid
git pull origin main

docker stop $(docker ps -aq) 2>/dev/null || true
docker rm $(docker ps -aq) 2>/dev/null || true

# ECR 로그인
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin 216989097509.dkr.ecr.ap-northeast-2.amazonaws.com

# SERVER_ROLE=ws 로 실행
echo "SERVER_ROLE=ws" >> .env
echo "INFRA_HOST=172.31.34.73" >> .env

docker compose -f infra/scaleout/docker-compose-app.yml --env-file .env up -d --pull always
USERDATA
)

# WS Launch Template 생성
aws ec2 create-launch-template \
  --launch-template-name fairbid-ws-lt \
  --launch-template-data "{
    \"ImageId\": \"$AMI_ID\",
    \"InstanceType\": \"$INSTANCE_TYPE\",
    \"KeyName\": \"$KEY_NAME\",
    \"SecurityGroupIds\": [\"$SECURITY_GROUP\"],
    \"UserData\": \"$WS_USER_DATA\",
    \"IamInstanceProfile\": {
      \"Name\": \"fairbid-ec2-role\"
    }
  }" \
  --region $REGION > /dev/null
echo "  WS Launch Template 생성 완료"

# WS ASG 생성
aws autoscaling create-auto-scaling-group \
  --auto-scaling-group-name fairbid-ws-asg \
  --launch-template LaunchTemplateName=fairbid-ws-lt,Version='$Latest' \
  --min-size 1 \
  --max-size 4 \
  --desired-capacity 1 \
  --vpc-zone-identifier "$SUBNET_A,$SUBNET_C" \
  --target-group-arns $WS_TG_ARN \
  --health-check-type ELB \
  --health-check-grace-period 300 \
  --region $REGION
echo "  WS ASG 생성 완료 (min:1, max:4)"

# ===== 5. 확인 =====
echo ""
echo "[6/6] 구성 확인..."
echo ""
echo "  ALB 리스너 규칙:"
aws elbv2 describe-rules \
  --listener-arn $LISTENER_ARN \
  --query 'Rules[*].{Priority:Priority,Conditions:Conditions[0].Values[0],TargetGroup:Actions[0].TargetGroupArn}' \
  --output table --region $REGION

echo ""
echo "================================================"
echo "구성 완료!"
echo ""
echo "  API: fairbid-app-tg ← /api/*, default"
echo "  WS:  fairbid-ws-tg  ← /ws*"
echo ""
echo "다음 단계:"
echo "  1. API ASG Instance Refresh (SERVER_ROLE=api 반영)"
echo "     aws autoscaling start-instance-refresh --auto-scaling-group-name fairbid-app-asg"
echo ""
echo "  2. WS ASG 인스턴스 healthy 대기"
echo "     aws elbv2 describe-target-health --target-group-arn $WS_TG_ARN"
echo ""
echo "  3. k6 테스트 실행"
echo "================================================"
