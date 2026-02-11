# Testing Strategy

> 테스트 작성 규칙 및 환경

---

## 1. 레이어별 테스트

### Unit Test
- 대상: Domain (복잡한 계산 로직)
- 예시: 입찰 단위 계산, 경매 연장 로직

### 인수 테스트 (BDD 기반)
- 대상: Service, Controller
- API 레벨 시나리오 검증
- 핵심 Happy Path 위주

---

## 2. 테스트 작성 규칙

- Given-When-Then 주석 필수
- Unhappy Path(예외 케이스) 최소 1개 이상 포함
- 커버리지 숫자보다 핵심 비즈니스 로직 커버 우선

---

## 3. 테스트 환경

- TestContainers 사용 (MySQL, Redis)
- H2 사용 금지
- AssertJ 활용 (assertThat())
- 각 테스트는 독립적으로 실행 가능해야 함

---

## 4. Mock 사용

- 외부 API (결제 등) → Mock 허용
- 그 외 → 실제 객체 사용