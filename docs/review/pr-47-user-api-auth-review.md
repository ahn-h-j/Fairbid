# PR #47 리뷰 분석

> **PR**: feat(identity): User API 및 인증 시스템 구현
> **URL**: <https://github.com/ahn-h-j/Fairbid/pull/47>
> **리뷰어**: CodeRabbit
> **분석일**: 2026-01-27

---

## CodeRabbit 리뷰

### 1. BuildKit 활성화 필수
- **파일**: `backend/Dockerfile:9-14`
- **내용**: `RUN --mount=type=cache` 문법은 BuildKit이 필수인데, CD 워크플로우에 `DOCKER_BUILDKIT=1`이 설정되어 있지 않아 배포 시 빌드 실패 가능
- **판정**: ❌ 거부
- **AI 분석**: CD 파이프라인에서 이미 정상 빌드/배포 중임. GitHub Actions의 docker compose 명령어는 최신 버전에서 BuildKit이 기본 활성화되어 있음.
- **결정**: 거부
- **의견**: ai 동의

---

### 2. DataIntegrityViolationException 파싱 취약성
- **파일**: `GlobalExceptionHandler.java:164-183`
- **내용**: 예외 메시지 substring 파싱이 MySQL 버전/제약조건 명명 규칙 변경에 취약함. Hibernate ConstraintViolationException의 getConstraintName() 활용 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 MySQL만 사용하고 제약조건 명명이 일관되므로 즉시 문제 없음. 다중 DB 지원 시 개선 필요.
- **결정**: 수용
- **의견**: 확장성 있게 고려하여 개발하는 방향이 옳음

---

### 3. CursorPage 공통 패키지 추출
- **파일**: `CursorPageResponse.java:3`
- **내용**: `GetMyAuctionsUseCase`에 정의된 `CursorPage<T>`를 `GetMyBidsUseCase`에서 임포트하여 암묵적 의존성 발생. 공통 패키지로 분리 권장
- **판정**: ✅ 수용
- **AI 분석**: 헥사고날 아키텍처에서 UseCase 간 의존성은 피해야 함. `common.pagination` 패키지로 분리하면 결합도 감소.
- **결정**: 수용
- **의견**: ai 동의

---

### 4. 닉네임/전화번호 중복 검사 TOCTOU 경합 조건
- **파일**: `UserService.java:61-69`
- **내용**: existsBy* 체크와 save 사이에 다른 트랜잭션이 동일 값을 삽입할 수 있음. DataIntegrityViolationException을 도메인 예외로 변환하는 catch 블록 추가 권장
- **판정**: ❌ 거부
- **AI 분석**: GlobalExceptionHandler에서 이미 DataIntegrityViolationException을 처리하여 "이미 사용 중인 닉네임입니다" 등의 메시지 반환. 중복 처리 불필요.
- **결정**: 거부
- **의견**: ai 동의

---

### 5. 테스트 입찰 엔드포인트 판별에 HTTP 메서드 체크 누락
- **파일**: `TestSecurityConfig.java:73-79`
- **내용**: 주석은 "POST 요청" 기준이나 구현은 경로만 확인. GET/DELETE도 구매자로 인증됨
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 테스트 코드이며 현재 GET /bids 엔드포인트 없음. 향후 확장 시 수정하면 됨.
- **결정**: 거부
- **의견**: 테스트 코드임

---

### 6. docker-compose.yml 로컬 전용 설정 분리
- **파일**: `docker-compose.yml:17-23`
- **내용**: OAuth 시크릿 빈 기본값과 `COOKIE_SECURE: "false"`가 고정되어 운영 재사용 시 보안 위험. .env 또는 override.yml 분리 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: 로컬 개발용 docker-compose이며, 운영은 EC2에서 별도 환경변수로 관리. 현재 구조에서 위험도 낮음.
- **결정**: 보류
- **의견**: todo/Todo.md에 기록

---

### 7. Markdown URL 포맷 (pr-46-user-api-review.md)
- **파일**: `docs/review/pr-46-user-api-review.md:3-5`
- **내용**: Bare URL을 Markdown 링크 포맷으로 변경 필요 (markdownlint MD034)
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 린트 규칙 위반이나 기능 영향 없음.
- **결정**: 거부
- **의견**: 문서화에 크게 적용할 생각 없음

---

### 8. Terraform EIP를 인스턴스에 직접 연결
- **파일**: `infra/main.tf:106-116`
- **내용**: `aws_eip`에 `instance`를 직접 연결하면 인스턴스 교체 시 ForceNew로 재생성됨. `aws_eip_association`으로 분리하면 더 안정적
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 인스턴스 교체 계획 없음. 인스턴스 재생성 시에도 EIP는 재연결됨 (다운타임은 발생). 향후 인프라 확장 시 고려.
- **결정**: 보류
- **의견**: docs/todo/Todo.md에 기록

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 1개 | CursorPage 공통 패키지 추출 |
| ⚠️ 선택적 | 5개 | DB 예외 파싱, 테스트 메서드 체크, docker-compose 분리, MD 링크, EIP 연결 |
| ❌ 거부 | 2개 | BuildKit 활성화 (이미 동작), TOCTOU 처리 (이미 GlobalHandler에서 처리) |
