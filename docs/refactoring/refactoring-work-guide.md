# 리팩토링 작업 가이드

> 작업 브랜치: `refactor/clean-architecture`
> 마지막 업데이트: 2026-01-14

---

## 0. 리팩토링 원칙 및 작업 방식

### 0.1 리팩토링 원칙

1. **Service에 private 메서드 금지**
   - 로직은 도메인 객체 또는 협력 객체(도메인 서비스, Policy)로 이동
   - Service는 객체 간 메시지 전달만 담당

2. **메서드 10줄 미만**
   - 긴 메서드는 책임 분리 신호

3. **비즈니스 로직 변경 금지**
   - Cucumber 테스트 통과 필수
   - 기능은 그대로, 구조만 개선

4. **헥사고날 아키텍처 준수**
   - Port/Adapter 패턴으로 외부 의존성 분리
   - 의존성 방향: External → Application → Domain

5. **기존 패턴 유지**
   - 프로젝트 전체에서 일관된 패턴은 유지 (예: DTO → Command 변환)

### 0.2 작업 패턴

```
1. 분석   → Task(Explore) 에이전트로 모듈 탐색
2. 논의   → 사용자와 어떤 것을 할지 결정
3. 구현   → 코드 작성/수정
4. 테스트 → Cucumber 테스트 실행
5. 문서화 → docs/refactoring/에 문서 작성
6. 커밋   → commit-convention.md 준수
```

### 0.3 모듈 분석 시 확인 관점

| 관점 | 확인 내용 |
|------|----------|
| 성능 | N+1 쿼리, 불필요한 조회, 비효율적 로직 |
| 가독성 | 긴 메서드, 복잡한 조건문(if-else 체인), 매직 넘버 |
| 코드 중복 | 반복되는 패턴, 유사한 로직 |
| 설계 | 책임 분리, 의존성 방향, 확장성 |
| 잠재적 버그 | null 체크 누락, 동시성 문제 |

### 0.4 사용자 선호 및 결정 사항

| 항목 | 결정 |
|------|------|
| DTO → Command 변환 | 기존 패턴 유지 (DTO.toCommand()) |
| 하드코딩된 bidderId | User 모킹 전까지 유지 |
| 불필요한 save() 호출 | 유지 (문제없음) |
| 예외 처리 | GlobalExceptionHandler + 도메인별 Exception 활용 |
| 즉시구매 기능 | 현재 비활성화 상태, 검증 로직 추가 불필요 |
| 이미지 기능 | 모킹 상태 유지 |

### 0.5 적용된 패턴 요약

| 패턴 | 적용 위치 | 설명 |
|------|----------|------|
| Policy 패턴 | `auction/domain/policy/` | 입찰 단위, 연장 규칙 분리 |
| 전략 패턴 | `BidType` enum | 입찰 유형별 금액 계산 |
| 도메인 서비스 | `winning/domain/service/` | 복잡한 도메인 로직 분리 |
| Port/Adapter | `*/port/out/`, `*/adapter/out/` | 이벤트 발행 분리 |

---

## 1. 완료된 작업

### 1.1 클린 아키텍처 리팩토링 (Service private 메서드 제거)

| 커밋 | 모듈 | 내용 |
|------|------|------|
| `8df261e` | bidding | BidService private 메서드를 도메인과 Port로 분리 |
| `949ac28` | trade | 경매 종료/노쇼 처리 로직을 도메인 서비스로 분리 |

**생성된 파일**:
- `bid/application/port/out/BidEventPublisher.java`
- `bid/adapter/out/event/BidEventPublisherAdapter.java`
- `winning/application/port/out/AuctionClosedEventPublisher.java`
- `winning/adapter/out/event/AuctionClosedEventPublisherAdapter.java`
- `winning/domain/service/AuctionClosingProcessor.java`
- `winning/domain/service/NoShowProcessor.java`

**문서**: `docs/refactoring/clean-architecture-refactoring.md`

---

### 1.2 Auction 도메인 Policy 분리

| 커밋 | 내용 |
|------|------|
| `dccf61d` | 입찰 단위/연장 로직을 Policy 클래스로 분리 |

**생성된 파일**:
- `auction/domain/policy/PriceBracket.java` - 가격 구간별 입찰 단위 Enum
- `auction/domain/policy/BidIncrementPolicy.java` - 입찰 단위 + 할증 계산
- `auction/domain/policy/AuctionExtensionPolicy.java` - 연장 구간/시간 계산

**문서**: `docs/refactoring/auction-policy-refactoring.md`

---

### 1.3 Bid 도메인 전략 패턴 적용

| 커밋 | 내용 |
|------|------|
| `379c4ef` | BidType 전략 패턴 적용 및 입찰 검증 강화 |

**변경 사항**:
- `BidType.java` - `calculateAmount()` 추상 메서드 추가
- `Bid.java` - if-else 제거, BidType에 위임
- `InvalidBidException.java` - `bidderIdRequired()` 팩토리 메서드 추가
- `Auction.java` - `validateBidEligibility()`에 null 체크 추가

**문서**: `docs/refactoring/bid-strategy-pattern-refactoring.md`

---

### 1.4 Common 모듈 예외 처리 통합

**변경 사항**:
- `DomainException.java` (신규) - 모든 도메인 예외의 베이스 클래스
- 7개 도메인 예외 클래스 - DomainException 상속, `getStatus()` 구현
- `GlobalExceptionHandler.java` - 단일 핸들러로 통합, 타입 기반 enum 검증

**문서**: `docs/refactoring/common-exception-refactoring.md`

---

### 1.5 기타

| 커밋 | 내용 |
|------|------|
| `4b264bf` | 클린 아키텍처 리팩토링 문서 추가 |
| `f29a7c5` | 리팩토링 이슈 템플릿에 상세 계획 섹션 추가 |

---

## 2. 남은 작업

### 2.1 추가 분석 필요 모듈

| 모듈 | 상태 | 비고 |
|------|------|------|
| `winning` | 미분석 | 낙찰/노쇼 처리 로직 |
| `notification` | 미분석 | 알림 처리 |

---

## 3. 작업 재개 방법

### 3.1 브랜치 확인
```bash
git checkout refactor/clean-architecture
git log --oneline -10
```

### 3.2 현재 상태 확인
```bash
git status
```

### 3.3 테스트 실행
```bash
cd backend
./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"
```

---

## 4. 커밋 컨벤션 (참고)

```
<type>(<scope>): <subject>

- type: refactor, docs, fix, feat
- scope: auction, bidding, trade, common, docs
- subject: 한글, 명령문, 50자 이내
```

**예시**:
```
refactor(common): 도메인 예외 BaseBusinessException으로 통합
```

---

## 5. 관련 문서

- `docs/refactoring/clean-architecture-refactoring.md`
- `docs/refactoring/auction-policy-refactoring.md`
- `docs/refactoring/bid-strategy-pattern-refactoring.md`
- `docs/refactoring/common-exception-refactoring.md`
- `docs/todo/Todo.md` - 성능 최적화 TODO 항목
- `docs/commit-convention.md`
- `.github/ISSUE_TEMPLATE/refactor.md`

---

## 6. GitHub Issue

- [#20 Service 클래스 클린 아키텍처 리팩토링](https://github.com/ahn-h-j/Fairbid/issues/20)
