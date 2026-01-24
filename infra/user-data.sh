#!/bin/bash
set -e

# =============================================================================
# Docker 설치
# =============================================================================
apt-get update
apt-get install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# ubuntu 유저에게 docker 권한 부여
usermod -aG docker ubuntu

# Docker 서비스 활성화
systemctl enable docker
systemctl start docker

# =============================================================================
# Nginx 설치 및 리버스 프록시 설정
# =============================================================================
apt-get install -y nginx

# Nginx 설정: HTTP 80 → Frontend Docker (3000)
# 프론트 Nginx가 /api, /ws를 백엔드(8080)로 프록시함
cat > /etc/nginx/sites-available/fairbid <<'NGINX'
server {
    listen 80;
    server_name ${domain_name} www.${domain_name};

    # Let's Encrypt 인증용
    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 지원
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
NGINX

# 기본 설정 비활성화, fairbid 설정 활성화
rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/fairbid /etc/nginx/sites-enabled/fairbid

systemctl enable nginx
systemctl restart nginx

# =============================================================================
# Certbot 설치 및 SSL 발급 스크립트 생성
# =============================================================================
apt-get install -y certbot python3-certbot-nginx

# DNS 전파 후 실행할 SSL 발급 스크립트
cat > /home/ubuntu/setup-ssl.sh <<'SSL_SCRIPT'
#!/bin/bash
# DNS 전파 확인 후 이 스크립트를 실행하세요
# 사용법: sudo ./setup-ssl.sh your-email@example.com

if [ -z "$1" ]; then
    echo "Usage: sudo ./setup-ssl.sh your-email@example.com"
    exit 1
fi

EMAIL=$1
DOMAIN="${domain_name}"

echo "SSL 인증서 발급 시작: $DOMAIN, www.$DOMAIN, api.$DOMAIN"

certbot --nginx \
    -d "$DOMAIN" \
    -d "www.$DOMAIN" \
    -d "api.$DOMAIN" \
    --non-interactive \
    --agree-tos \
    --email "$EMAIL" \
    --redirect

echo "SSL 인증서 발급 완료!"
echo "자동 갱신 테스트: sudo certbot renew --dry-run"
SSL_SCRIPT

chmod +x /home/ubuntu/setup-ssl.sh
chown ubuntu:ubuntu /home/ubuntu/setup-ssl.sh

# =============================================================================
# 설치 검증
# =============================================================================
echo "=== 설치 검증 시작 ==="
docker --version && echo "[OK] Docker 설치 완료" || echo "[FAIL] Docker 설치 실패"
nginx -t && echo "[OK] Nginx 설정 정상" || echo "[FAIL] Nginx 설정 오류"
certbot --version && echo "[OK] Certbot 설치 완료" || echo "[FAIL] Certbot 설치 실패"
echo "=== 설치 검증 완료 ==="
