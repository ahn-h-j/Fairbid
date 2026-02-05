# 리팩토링 작업 가이드

> 📅 마지막 업데이트: 2026-01-29
> 🎯 목표: Service private 메서드 제거, 클린 아키텍처 준수

---

## 1. 리팩토링 원칙

```text
1. Service에 private 메서드 금지 → 도메인 또는 협력 객체로 이동
2. 메서드 10줄 미만
3. 비즈니스 로직 변경 금지 → Cucumber 테스트 통과 필수
4. 헥사고날 아키텍처 준수 → Port/Adapter 패턴
```

---

## 2. 적용된 패턴 요약

| 패턴 | 적용 위치 | 설명 |
|------|----------|------|
| **Policy 패턴** | `auction/domain/policy/` | 입찰 단위, 연장 규칙 분리 |
| **전략 패턴** | `BidType`, `NotificationType` | 유형별 로직 캡슐화 |
| **Domain Service** | `winning/domain/service/` | 복잡한 도메인 로직 분리 |
| **Port/Adapter** | `*/port/out/`, `*/adapter/out/` | 이벤트 발행 분리 |

---

## 3. 완료된 작업

| 문서 | 모듈 | 핵심 개선 |
|------|------|----------|
| [auction-policy-refactoring.md](./auction-policy-refactoring.md) | Auction | if-else 7개 → Enum 테이블화 |
| [bid-strategy-pattern-refactoring.md](./bid-strategy-pattern-refactoring.md) | Bid | 입찰 유형별 전략 패턴 |
| [clean-architecture-refactoring.md](./clean-architecture-refactoring.md) | Bid, Winning | Service private 메서드 제거 |
| [common-exception-refactoring.md](./common-exception-refactoring.md) | Common | 예외 핸들러 6개 → 1개 통합 |
| [notification-refactoring.md](./notification-refactoring.md) | Notification | Adapter 88줄 → 36줄 |

---

## 4. 테스트 실행

```bash
# Cucumber 테스트 (회귀 테스트)
cd backend
./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"

# 전체 빌드
./gradlew clean build
```

---

## 5. 관련 문서

- `docs/architecture.md` - 아키텍처 설명
- `docs/convention.md` - 코딩 컨벤션
