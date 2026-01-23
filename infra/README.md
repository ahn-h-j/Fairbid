# FairBid 인프라 (Terraform)

AWS에 FairBid 서비스를 배포하기 위한 Terraform 코드입니다.
Docker만 설치되어 있으면 Terraform, AWS CLI 설치 없이 실행 가능합니다.

---

## 생성되는 리소스

| 리소스 | 설명 | 비용 |
|--------|------|------|
| EC2 (t3.small) | Ubuntu 24.04, Docker + Nginx 자동 설치 | $0.0208/hr |
| Security Group | SSH(22), HTTP(80), HTTPS(443), API(8080) | 무료 |
| Key Pair | SSH 접속용 키페어 등록 | 무료 |
| Route 53 Hosted Zone | DNS 관리 | $0.50/월 |
| Route 53 A Record | 루트, www, api 서브도메인 → EC2 연결 | 무료 |

---

## 사전 준비

1. **Docker** 설치
2. **AWS 계정** 생성 및 IAM Access Key 발급
   - AWS 콘솔 → IAM → 사용자 → 보안 자격증명 → 액세스 키 생성
3. **AWS 키페어** 생성
   - AWS 콘솔 → EC2 → 키 페어 → 키 페어 생성 → `.pem` 파일 보관
4. **도메인** 구매 (가비아 등)

---

## 설정 파일 작성

```bash
cd infra/
```

`terraform.tfvars` 파일 생성:

```hcl
key_name    = "your-key-name"      # AWS에서 만든 키페어 이름
domain_name = "your-domain.com"    # 구매한 도메인
my_ip       = "123.45.67.89/32"    # 본인 IP (https://checkip.amazonaws.com 에서 확인)
```

`.env` 파일 생성:

```
AWS_ACCESS_KEY_ID=본인_액세스_키
AWS_SECRET_ACCESS_KEY=본인_시크릿_키
```

---

## 명령어 정리

모든 명령어는 `infra/` 디렉토리에서 실행합니다.

### 초기 세팅 (최초 1회)

```bash
# Terraform 초기화 (프로바이더 다운로드)
docker compose run --rm terraform init
```

### 인프라 생성

```bash
# 생성될 리소스 미리보기 (실제 생성 안 함)
docker compose run --rm terraform plan

# 인프라 생성 (EC2 + SG + Route 53)
docker compose run --rm terraform apply -auto-approve
```

### EC2만 내리기 (비용 절약, 네임서버 유지)

```bash
docker compose run --rm destroy-ec2
```

Hosted Zone은 유지되므로 네임서버를 다시 넣을 필요 없습니다.
다시 올릴 때는 `apply`만 하면 됩니다.

### 다시 올리기

```bash
docker compose run --rm terraform apply -auto-approve
```

새 EC2 IP가 출력되고, A Record도 자동 업데이트됩니다.
GitHub Secrets의 `EC2_HOST`를 새 IP로 변경하세요:

```bash
gh secret set EC2_HOST --body "새_EC2_IP"
```

### 현재 상태 확인

```bash
# 현재 생성된 리소스 목록
docker compose run --rm terraform state list

# EC2 IP, 네임서버 등 출력값 확인
docker compose run --rm terraform output
```

### 전체 삭제 (Hosted Zone 포함)

Hosted Zone까지 완전히 삭제하려면 `main.tf`에서 `prevent_destroy`를 제거한 후:

```bash
docker compose run --rm terraform destroy -auto-approve
```

---

## 도메인 네임서버 변경 (최초 1회)

`apply` 완료 후 출력되는 `nameservers` 4개를 도메인 등록업체에 입력합니다.

> 가비아 기준: My가비아 → 도메인 관리 → 네임서버 → 타사 네임서버 사용 → 4개 입력

DNS 전파에 수 분 ~ 수 시간 소요될 수 있습니다.

---

## SSH 접속 및 앱 배포

```bash
# EC2 접속
ssh -i your-key.pem ubuntu@EC2_PUBLIC_IP

# 앱 클론 및 실행
git clone https://github.com/ahn-h-j/Fairbid.git
cd Fairbid

# .env 작성 후
docker compose up -d
```

또는 GitHub Actions CD가 main push 시 자동 배포합니다.

---

## HTTPS 설정 (DNS 전파 후)

```bash
# EC2에 SSH 접속한 상태에서
sudo ./setup-ssl.sh your-email@example.com
```

SSL 인증서가 발급되고 자동 갱신이 설정됩니다.

---

## 주의사항

- `.env`, `terraform.tfvars`, `*.tfstate` 파일은 **절대 git에 올리지 마세요**
- EC2는 사용하지 않을 때 `destroy-ec2`로 삭제하여 비용 절약
- Route 53 Hosted Zone은 월 $0.50 (유지 권장, 네임서버 변경 방지)

---

## 파일 구조

```
infra/
├── provider.tf          # AWS Provider 설정
├── variables.tf         # 변수 정의
├── main.tf              # EC2 + Security Group + Route 53
├── outputs.tf           # 출력값 (IP, 네임서버)
├── user-data.sh         # EC2 부팅 시 자동 실행 스크립트 (Docker, Nginx, Certbot)
├── docker-compose.yml   # Terraform Docker 실행 환경
├── .gitignore           # tfstate, tfvars, .env 등 제외
└── README.md            # 이 문서
```
