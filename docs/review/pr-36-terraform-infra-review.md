# PR #36 리뷰 분석

> **PR**: [CHORE] Terraform 인프라 코드화 및 Route 53 도메인 연결 (#35)
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/36
> **리뷰어**: Copilot
> **분석일**: 2026-01-24

---

## Copilot 리뷰

### 1. 8080 포트 전체 인터넷 노출
- **파일**: `infra/main.tf:53-60`
- **내용**: Nginx 프록시가 있는데 8080 포트를 전체 인터넷에 노출하는 것은 보안 혼란을 야기함. 본인 IP로 제한하거나 제거 권장
- **판정**: ✅ 수용
- **AI 분석**: Nginx가 80/443으로 프론트를 서빙하고, 프론트 Nginx가 /api를 8080으로 프록시하므로 외부에서 8080 직접 접근은 디버깅 외 불필요. 본인 IP로 제한하는 것이 합리적
- **결정**: 수용
- **의견**: ai 동의

### 2. user-data 헬스체크 부재
- **파일**: `infra/user-data.sh`
- **내용**: Docker/Nginx 설치 실패 시 EC2가 여전히 healthy로 표시됨. 상태 검증 로직 추가 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 1인 포트폴리오 프로젝트에서 CloudWatch나 Systems Manager 연동은 과도함. SSH 접속해서 직접 확인하는 것으로 충분함. `set -e`로 에러 시 중단은 이미 적용됨
- **결정**: 수용
- **의견**: 과하면 빼버리는 걸로

### 3. SSH public key 하드코딩
- **파일**: `infra/main.tf:24`
- **내용**: public key가 코드에 직접 커밋됨. `file()` 함수로 외부 파일에서 로드하는 것이 보안상 적절
- **판정**: ✅ 수용
- **AI 분석**: public key 자체는 노출되어도 SSH 접속은 불가하지만(private key 필요), 코드에 하드코딩하면 키 교체 시 코드 수정이 필요하고 다른 사용자가 fork할 때 불편함. 변수로 분리하는 것이 관리상 적절
- **결정**: 수용
- **의견**: infra/.env 파일이 존재

### 4. terraform.tfvars.example 제공 권장
- **파일**: `infra/README.md`
- **내용**: .gitignore에 포함된 terraform.tfvars의 예시 파일을 제공해야 새 사용자가 쉽게 설정 가능
- **판정**: ❌ 거부
- **AI 분석**: README에 필요한 변수와 형식이 이미 상세히 문서화되어 있음. example 파일은 초기에 만들었다가 제거한 이력이 있으며, 문서로 충분히 대체됨
- **결정**: 거부
- **의견**: ai 동의

### 5. prevent_destroy 변수화
- **파일**: `infra/main.tf:96-98`
- **내용**: prevent_destroy가 하드코딩되어 정당한 삭제(개발환경 정리 등)에도 에러 발생. 변수로 제어 가능하게 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: 1인 프로젝트에서 Hosted Zone을 실수로 삭제하는 것을 방지하는 용도로 충분함. 전체 삭제 시 코드에서 제거하는 방법이 README에 문서화되어 있음
- **결정**: 거부
- **의견**: ai 동의

### 6. EC2 태깅 전략
- **파일**: `infra/main.tf:84-86`
- **내용**: Name 외에 Environment, ManagedBy, Project 등 태그 추가 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: EC2 1대짜리 1인 프로젝트에서 태깅 전략은 과도함. 리소스 추적이 복잡해질 규모가 아님
- **결정**: 거부
- **의견**: ai 동의

### 7. SSL heredoc 변수 치환 문제
- **파일**: `infra/user-data.sh:69`
- **내용**: 'SSL_SCRIPT' (quoted heredoc)에서 ${domain_name}이 literal로 처리되어 SSL 발급 실패할 것이라는 지적
- **판정**: ❌ 거부
- **AI 분석**: 이 파일은 Terraform의 `templatefile()` 함수로 처리됨. templatefile은 파일 전체의 `${}`를 Terraform 변수로 치환한 후 user_data에 전달하므로, bash heredoc의 quoting과 무관하게 정상 치환됨. 리뷰어가 Terraform templatefile 동작을 간과한 것
- **결정**: 거부
- **의견**: ai 동의. templatefile()이 먼저 치환하므로 bash heredoc quoting과 무관

### 8. destroy-ec2 리소스 순서 의존성
- **파일**: `infra/docker-compose.yml:22-30`
- **내용**: A Record 삭제 후 EC2 삭제까지 DNS가 없는 IP를 가리킬 수 있음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: Terraform이 내부적으로 의존성 그래프를 계산하여 올바른 순서로 삭제함. destroy-ec2로 내리는 것은 비용 절약 목적이므로 짧은 DNS 불일치는 무시 가능
- **결정**: 거부
- **의견**: ai 동의. Terraform이 순서 자동 관리

### 9. A Record TTL 300초
- **파일**: `infra/main.tf:108,128,142`
- **내용**: IP 변경 시 최대 5분간 구 IP 캐싱으로 서비스 중단 가능. TTL 축소 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 포트폴리오 프로젝트에서 5분 DNS 캐싱은 실질적 문제 아님. 데모/면접 전에 미리 apply하면 충분
- **결정**: 거부
- **의견**: ai 동의. 포트폴리오 규모에서 5분은 무관

### 10. user-data 에러 핸들링/로깅
- **파일**: `infra/user-data.sh`
- **내용**: `set -x`와 로그 파일 리디렉션, 상태 체크 추가 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: 디버깅 편의성은 향상되지만 현재 규모에서는 SSH 접속 후 직접 확인으로 충분. `set -e`로 에러 시 중단은 이미 적용됨
- **결정**: 거부
- **의견**: ai 동의. set -e로 충분, 과하면 빼는 방향

### 11. variables validation 규칙 부재
- **파일**: `infra/variables.tf`
- **내용**: my_ip CIDR 포맷 검증, key_name 비어있지 않은지 validation 추가 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 1인 프로젝트에서 본인이 직접 값을 넣으므로 validation 필요성이 낮음. plan 단계에서 오류가 잡힘
- **결정**: 거부
- **의견**: ai 동의. plan에서 잡히므로 불필요

### 12. AMI 필터 패턴 변경 권장
- **파일**: `infra/main.tf:10`
- **내용**: hvm-ssd-gp3 대신 hvm-ssd 패턴 사용 권장 (일부 AMI가 누락될 수 있음)
- **판정**: ❌ 거부
- **AI 분석**: Ubuntu 24.04(Noble)는 실제로 `hvm-ssd-gp3` 패턴의 AMI를 제공하며, `terraform apply`에서 정상 동작 확인됨 (ami-0130d8d35bcd2d433). 기존 패턴이 정확함
- **결정**: 거부
- **의견**: ai 동의. 실제 동작 확인됨

---

## 요약

| 결정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 3개 | 8080 포트 제한, user-data 헬스체크, SSH public key 변수화 |
| ❌ 거부 | 9개 | tfvars.example, prevent_destroy 변수화, EC2 태깅, SSL heredoc, destroy 순서, TTL, 에러 핸들링, validation, AMI 필터 |
