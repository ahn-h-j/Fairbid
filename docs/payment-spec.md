# Payment Feature Specification (결제 기능 스펙)

> Mock 결제 기능 구현 스펙. 실제 PG 연동 없이 결제 완료를 시뮬레이션한다.

---

## 1. 개요

### 목적
- 낙찰자가 결제를 완료하여 거래를 확정하는 기능
- 실제 결제 없이 Mock으로 동작 (무조건 성공, 1~2초 지연)

### 범위
- Transaction 도메인 (Trade Context) 신규 구현
- 결제 API 및 프론트엔드 결제 플로우
- 결제 리마인더 알림 (1시간 전)
- 결제 완료 알림 (구매자 + 판매자)
- 기존 Winning/NoShow 로직과 연동

### 범위 외
- User 경고/차단 시스템 (후속 작업)
- 실제 PG 연동
- 환불/취소 로직

---

## 2. 도메인 설계

### Bounded Context 배치
- **Trade Context** (신규): Transaction 도메인
- architecture.md의 설계를 따름
- Winning Context와는 **Orchestrator 패턴**으로 통신

### Transaction 도메인 모델

```java
Transaction {
    Long id;
    Long auctionId;          // FK: AUCTION
    Long sellerId;           // FK: USER (판매자)
    Long buyerId;            // FK: USER (구매자/낙찰자)
    Long finalPrice;         // 최종 낙찰가
    TransactionStatus status; // AWAITING_PAYMENT, PAID, CANCELLED
    LocalDateTime paymentDeadline;  // 결제 마감일시
    LocalDateTime createdAt;        // 거래 생성일시 (낙찰 시점)
    LocalDateTime paidAt;           // 결제 완료일시
}
```

### TransactionStatus 상태 전이

```
AWAITING_PAYMENT → PAID       (결제 완료)
AWAITING_PAYMENT → CANCELLED  (노쇼/유찰로 인한 취소)
```

### 생성 시점
- **낙찰 시 자동 생성**: AuctionClosingProcessor에서 1순위 Winning 생성과 동시에 Transaction 생성
- 2순위 승계 시: 기존 Transaction의 buyerId를 2순위 낙찰자로 변경, paymentDeadline 재설정

---

## 3. 패키지 구조

```
src/main/java/com/cos/fairbid/transaction/
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── PaymentUseCase.java          // 결제 처리
│   │   │   └── TransactionQueryUseCase.java  // 거래 조회
│   │   └── out/
│   │       └── TransactionRepositoryPort.java
│   └── service/
│       ├── PaymentService.java               // 결제 처리 구현
│       ├── TransactionQueryService.java      // 거래 조회 구현
│       └── MockPaymentProcessor.java         // Mock 결제 처리기
├── domain/
│   ├── Transaction.java
│   └── TransactionStatus.java
└── adapter/
    ├── in/
    │   ├── controller/
    │   │   └── TransactionController.java
    │   └── dto/
    │       ├── PaymentRequest.java
    │       ├── PaymentResponse.java
    │       └── TransactionResponse.java
    └── out/
        └── persistence/
            ├── entity/TransactionEntity.java
            ├── repository/TransactionJpaRepository.java
            ├── mapper/TransactionMapper.java
            └── TransactionPersistenceAdapter.java
```

---

## 4. API 설계

### POST /api/v1/transactions/{transactionId}/payment

결제 처리 (Mock)

**Request**
```json
{
  // body 없음 - Mock이므로 결제 수단 선택 불필요
}
```

**Response (성공)**
```json
{
  "success": true,
  "data": {
    "transactionId": 1,
    "auctionId": 42,
    "status": "PAID",
    "finalPrice": 150000,
    "paidAt": "2026-01-24T14:30:00"
  },
  "serverTime": "2026-01-24T14:30:00",
  "error": null
}
```

**에러 케이스**
| 상황 | Error Code | HTTP Status |
|------|-----------|-------------|
| 결제 기한 만료 | PAYMENT_EXPIRED | 400 |
| 이미 결제 완료 | ALREADY_PAID | 400 |
| 거래 없음 | TRANSACTION_NOT_FOUND | 404 |
| 본인 거래 아님 | NOT_TRANSACTION_BUYER | 403 |

### GET /api/v1/transactions/{transactionId}

거래 상세 조회

**Response**
```json
{
  "success": true,
  "data": {
    "transactionId": 1,
    "auctionId": 42,
    "auctionTitle": "아이폰 15 Pro",
    "auctionImageUrl": "https://...",
    "sellerId": 10,
    "buyerId": 20,
    "finalPrice": 150000,
    "status": "AWAITING_PAYMENT",
    "paymentDeadline": "2026-01-24T17:00:00",
    "createdAt": "2026-01-24T14:00:00",
    "paidAt": null
  }
}
```

### GET /api/v1/transactions/my-sales

판매자의 거래 목록 조회 (마이페이지에서 사용)

**Response**
```json
{
  "success": true,
  "data": [
    {
      "transactionId": 1,
      "auctionId": 42,
      "auctionTitle": "아이폰 15 Pro",
      "auctionImageUrl": "https://...",
      "finalPrice": 150000,
      "status": "PAID",
      "createdAt": "2026-01-24T14:00:00"
    }
  ]
}
```

---

## 5. 비즈니스 로직 상세

### 5.1 결제 처리 플로우

```
1. 사용자가 결제 버튼 클릭
2. 프론트엔드: 로딩 상태 표시
3. 서버: 결제 유효성 검증
   - Transaction 존재 확인
   - 본인 거래 확인 (buyerId == 요청자)
   - 결제 기한 미만료 확인
   - 상태가 AWAITING_PAYMENT인지 확인
4. 서버: Mock 결제 처리 (1~2초 지연)
5. 서버: 같은 트랜잭션 내에서 상태 변경
   - Transaction.status → PAID
   - Transaction.paidAt → now
   - Winning.status → PAID
6. 서버: 알림 발송
   - 구매자: "결제가 완료되었습니다"
   - 판매자: "구매자가 결제를 완료했습니다"
7. 프론트엔드: 결제 완료 페이지로 이동
```

### 5.2 Orchestrator 패턴 적용

```java
// AuctionClosingOrchestrator (또는 기존 AuctionClosingProcessor 확장)
// 낙찰 시: Winning 생성 + Transaction 생성을 조합

// PaymentOrchestrator
// 결제 시: Transaction 상태 변경 + Winning 상태 변경을 같은 트랜잭션으로 조합
```

### 5.3 낙찰 시 Transaction 생성

```
경매 종료 → AuctionClosingProcessor.processFirstRankWinner()
  → Winning 생성 (기존)
  → Transaction 생성 (신규)
    - auctionId: 경매 ID
    - sellerId: 판매자 ID
    - buyerId: 1순위 낙찰자 ID
    - finalPrice: 낙찰 금액
    - status: AWAITING_PAYMENT
    - paymentDeadline: Winning의 paymentDeadline과 동일 (now + 3시간)
```

### 5.4 노쇼 시 Transaction 처리

```
1순위 노쇼 발생 시:
  - 기존 Transaction.status → CANCELLED

2순위 승계 시:
  - 기존 Transaction 업데이트:
    - buyerId → 2순위 입찰자 ID
    - finalPrice → 2순위 입찰 금액
    - paymentDeadline → now + 1시간
    - status: AWAITING_PAYMENT 유지

2순위도 미결제 시:
  - Transaction.status → CANCELLED
```

### 5.5 결제 리마인더 알림

```
- 시점: 결제 마감 1시간 전
- 대상: 낙찰자 (구매자)
- 타입: PAYMENT_REMINDER
- 제목: "결제 기한이 임박했습니다"
- 본문: "[상품명] 결제 마감까지 1시간 남았습니다. 기한 내 미결제 시 노쇼 처리됩니다."
- 구현: 스케줄러에서 paymentDeadline - 1hour <= now인 건 조회 후 발송
  - 중복 발송 방지 필요 (reminderSent 플래그 또는 별도 추적)
```

---

## 6. 프론트엔드 설계

### 6.1 라우트 구조

| 경로 | 페이지 | 설명 |
|------|--------|------|
| `/auctions/{id}` | 경매 상세 (기존) | 낙찰자에게 결제 버튼 노출 |
| `/auctions/{id}/payment` | 결제 확인 | 경매 정보 확인 후 결제 |
| `/auctions/{id}/payment/complete` | 결제 완료 | 성공 정보 표시 |

### 6.2 경매 상세 페이지 변경사항

**낙찰자 본인일 때:**
- 결제 대기 중: "결제하기" 버튼 활성화 + 결제 기한 표시 (정적: "결제 기한: 2026-01-24 15:00")
- 결제 기한 만료: 버튼 비활성화(회색) + "결제 기한이 만료되었습니다" 메시지
- 결제 완료: "결제 완료" 배지 표시

**다른 사용자:**
- "이 경매는 종료되었습니다" 표시만

**판매자:**
- 거래 상태 표시 (결제대기중/결제완료/노쇼)

### 6.3 결제 확인 페이지 (`/auctions/{id}/payment`)

```
┌──────────────────────────────────┐
│         결제 확인                  │
├──────────────────────────────────┤
│  [상품 이미지]                     │
│                                    │
│  상품명: 아이폰 15 Pro             │
│  낙찰가: 150,000원                 │
│  결제 기한: 2026-01-24 17:00       │
│                                    │
│  ┌────────────────────────────┐   │
│  │       결제하기              │   │
│  └────────────────────────────┘   │
└──────────────────────────────────┘
```

- 결제 기한 만료 시 이 페이지 접근 불가 (경매 상세로 리다이렉트)

### 6.4 결제 완료 페이지 (`/auctions/{id}/payment/complete`)

```
┌──────────────────────────────────┐
│         결제 완료 ✓                │
├──────────────────────────────────┤
│                                    │
│  상품명: 아이폰 15 Pro             │
│  결제금액: 150,000원               │
│  거래번호: TXN-20260124-001       │
│                                    │
│  ┌────────────────────────────┐   │
│  │    경매 상세로 돌아가기      │   │
│  └────────────────────────────┘   │
└──────────────────────────────────┘
```

### 6.5 결제 버튼 클릭 시 UX 플로우

```
1. "결제하기" 버튼 클릭
2. 버튼 비활성화 + 스피너 표시 ("결제 처리중...")
3. API 호출 (서버에서 1~2초 mock 지연)
4. 성공 응답 수신
5. /auctions/{id}/payment/complete로 이동
```

### 6.6 2순위 승계 UX

```
1. 2순위 사용자에게 승계 알림 수신
2. 알림 클릭 → 경매 상세 페이지로 이동
3. 경매 상세에서 "결제하기" 버튼 확인 (결제 기한: 1시간)
4. 결제하기 클릭 → 결제 확인 페이지 → 결제 완료
```

---

## 7. 알림 추가사항

### 신규 알림 타입

| 타입 | 대상 | 제목 | 본문 |
|------|------|------|------|
| PAYMENT_COMPLETED_BUYER | 구매자 | "결제가 완료되었습니다" | "[상품명] {금액}원 결제가 완료되었습니다." |
| PAYMENT_COMPLETED_SELLER | 판매자 | "구매자가 결제를 완료했습니다" | "[상품명] {금액}원 결제가 완료되었습니다. 거래를 진행해주세요." |
| PAYMENT_REMINDER | 구매자 | "결제 기한이 임박했습니다" | "[상품명] 결제 마감까지 1시간 남았습니다." |

---

## 8. Winning/NoShow 연동 변경사항

### AuctionClosingProcessor 변경
- `processFirstRankWinner()`: Transaction 생성 로직 추가

### NoShowProcessor 변경
- `processFirstRankNoShow()`: Transaction.status → CANCELLED
- `transferToSecondRank()`: Transaction의 buyerId, finalPrice, paymentDeadline 업데이트
- `processSecondRankExpired()`: Transaction.status → CANCELLED
- `failAuction()`: Transaction.status → CANCELLED

### PaymentTimeoutScheduler 변경
- 결제 마감 1시간 전 리마인더 발송 로직 추가

---

## 9. Mock 결제 처리기

```java
/**
 * MockPaymentProcessor
 * - 실제 PG 연동 없이 결제 성공을 시뮬레이션
 * - 1~2초 랜덤 지연 후 무조건 성공 반환
 * - 추후 실제 PG 연동 시 이 클래스만 교체
 */
public class MockPaymentProcessor {
    public PaymentResult processPayment(Long transactionId, Long amount) {
        // 1~2초 랜덤 지연
        Thread.sleep(randomBetween(1000, 2000));
        // 무조건 성공
        return PaymentResult.success(transactionId);
    }
}
```

- Port/Adapter 패턴 적용: `PaymentGatewayPort` 인터페이스로 추상화
- Mock 구현체를 Adapter로 배치
- 추후 실제 PG 연동 시 Adapter만 교체

---

## 10. 테스트

### Cucumber 시나리오

```gherkin
Feature: 결제 처리

  Scenario: 1순위 낙찰자가 기한 내 결제 성공
    Given 경매가 종료되어 낙찰자가 결정됨
    And 낙찰자에게 결제 대기 Transaction이 생성됨
    When 낙찰자가 결제를 요청함
    Then 결제가 성공적으로 처리됨
    And Transaction 상태가 PAID로 변경됨
    And Winning 상태가 PAID로 변경됨
    And 구매자와 판매자에게 결제 완료 알림이 발송됨

  Scenario: 결제 기한 만료 후 결제 시도
    Given 경매가 종료되어 낙찰자가 결정됨
    And 결제 기한이 만료됨
    When 낙찰자가 결제를 요청함
    Then PAYMENT_EXPIRED 에러가 반환됨

  Scenario: 1순위 노쇼 후 2순위 승계 및 결제
    Given 1순위 낙찰자가 결제 기한을 초과함
    And 2순위 입찰가가 1순위의 90% 이상임
    When 노쇼 처리가 실행됨
    Then 2순위에게 결제 권한이 승계됨 (1시간)
    And Transaction의 구매자가 2순위로 변경됨
    When 2순위가 결제를 요청함
    Then 결제가 성공적으로 처리됨

  Scenario: 결제 리마인더 알림 발송
    Given 낙찰자의 결제 기한이 1시간 남음
    When 리마인더 스케줄러가 실행됨
    Then 낙찰자에게 결제 기한 임박 알림이 발송됨
```

---

## 11. 구현 우선순위

1. **Transaction 도메인** (Domain, Entity, Repository, Mapper)
2. **결제 API** (PaymentUseCase, PaymentService, MockPaymentProcessor, Controller)
3. **Winning 연동** (AuctionClosingProcessor, NoShowProcessor 변경)
4. **결제 리마인더** (PaymentTimeoutScheduler 변경)
5. **알림 추가** (PAYMENT_COMPLETED, PAYMENT_REMINDER)
6. **프론트엔드 결제 플로우** (결제 확인 → 로딩 → 완료)
7. **프론트엔드 경매 상세 변경** (낙찰자 결제 버튼, 상태 표시)
8. **Cucumber 테스트**

---

## 12. 트레이드오프 & 결정 사항

| 결정 | 선택 | 근거 |
|------|------|------|
| Mock 범위 | 무조건 성공 | 결제 자체보다 플로우 완성이 목적 |
| BC 분리 | Trade Context 별도 | architecture.md 설계 준수 |
| BC간 통신 | Orchestrator 패턴 | 이벤트보다 명시적, 트랜잭션 관리 용이 |
| 결제 시 상태 변경 | 같은 트랜잭션 | 정합성 보장 (Transaction + Winning) |
| Transaction 생성 시점 | 낙찰 시 자동 | 판매자 즉시 확인, 노쇼 이력 보존 |
| Transaction 상태 | 별도 관리 | BC 분리에 따른 독립성 |
| 카운트다운 | 정적 표시 | 결제 기한 3시간으로 실시간 불필요 |
| 경고/차단 | 후속 작업 | 결제 기능 우선, User 도메인 변경 최소화 |
