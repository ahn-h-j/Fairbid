# FairBid 배포 가이드

> EC2 기반 배포 환경 구축 및 CI/CD 설정 가이드

---

## 1. EC2 인스턴스 생성

### 1-1. EC2 대시보드 이동
- AWS 콘솔 접속 → EC2 → **인스턴스 시작**

### 1-2. 인스턴스 설정

| 항목 | 값 |
|------|-----|
| **이름** | `fairbid-server` |
| **AMI** | Ubuntu Server 24.04 LTS |
| **인스턴스 유형** | t3.small |
| **키 페어** | 새로 생성 → `fairbid-key` → .pem 다운로드 |
| **스토리지** | 20GB gp3 |

### 1-3. 보안 그룹 설정

**새 보안 그룹 생성** 선택 후 인바운드 규칙 추가:

| 유형 | 포트 | 소스 | 용도 |
|------|------|------|------|
| SSH | 22 | 내 IP | SSH 접속 |
| HTTP | 80 | 0.0.0.0/0 | 웹 (nginx) |
| 사용자 지정 TCP | 3000 | 0.0.0.0/0 | 프론트엔드 |
| 사용자 지정 TCP | 8080 | 0.0.0.0/0 | 백엔드 API |

### 1-4. 인스턴스 시작
- **인스턴스 시작** 버튼 클릭
- 생성 완료 후 **퍼블릭 IPv4 주소** 확인

---

## 2. EC2 초기 설정

### 2-1. SSH 접속

```bash
# 키 파일 권한 설정 (Linux/Mac)
chmod 400 fairbid-key.pem

# SSH 접속
ssh -i fairbid-key.pem ubuntu@<EC2_PUBLIC_IP>
```

### 2-2. Docker 설치

```bash
# 패키지 업데이트
sudo apt update && sudo apt upgrade -y

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh

# 현재 사용자를 docker 그룹에 추가 (sudo 없이 docker 사용 가능)
sudo usermod -aG docker $USER

# Docker Compose 설치
sudo apt install docker-compose-plugin -y

# 변경사항 적용을 위해 재접속
exit
```

### 2-3. 설치 확인

```bash
# 재접속
ssh -i fairbid-key.pem ubuntu@<EC2_PUBLIC_IP>

# 버전 확인
docker --version
docker compose version
```

---

## 3. 프로젝트 배포

### 3-1. 프로젝트 클론

```bash
cd ~
git clone https://github.com/ahn-h-j/Fairbid.git
cd Fairbid
```

### 3-2. 환경 변수 설정

```bash
# .env 파일 생성
cat > .env << 'EOF'
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/fairbid?allowPublicKeyRetrieval=true&useSSL=false
SPRING_DATASOURCE_ROOT_PASSWORD=<ROOT_PASSWORD>
SPRING_DATASOURCE_DATABASE=fairbid
SPRING_DATASOURCE_USER=fairbid
SPRING_DATASOURCE_PASSWORD=<DB_PASSWORD>
EOF
```

### 3-3. 애플리케이션 실행

```bash
# 빌드 및 실행
docker compose up --build -d

# 로그 확인
docker compose logs -f

# 상태 확인
docker compose ps
```

### 3-4. 접속 확인

| 서비스 | URL |
|--------|-----|
| 프론트엔드 | http://<EC2_PUBLIC_IP>:3000 |
| 백엔드 API | http://<EC2_PUBLIC_IP>:8080 |

---

## 4. CI/CD 설정

### 4-1. GitHub Secrets 설정

GitHub 레포지토리 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

| Name | Value |
|------|-------|
| `EC2_HOST` | EC2 퍼블릭 IP 주소 |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | fairbid-key.pem 파일 내용 전체 (`-----BEGIN`부터 `-----END`까지) |

### 4-2. 자동 배포

main 브랜치에 push 또는 PR merge 시 자동으로 EC2에 배포됩니다.

워크플로우 파일: `.github/workflows/cd.yml`

---

## 5. 운영 명령어

### 서비스 관리

```bash
# 서비스 시작
docker compose up -d

# 서비스 중지
docker compose down

# 서비스 재시작
docker compose restart

# 로그 확인
docker compose logs -f <서비스명>
```

### 업데이트 배포

```bash
cd ~/Fairbid
git pull origin main
docker compose up --build -d
```

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|----------|
| 1.0 | 2025-01-15 | 최초 작성 |
