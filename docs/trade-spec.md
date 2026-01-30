# Trade Feature Specification (거래 연결 기능 스펙)

> Mock 결제 방식을 **판매자-구매자 직접 연결** 방식으로 변경한다.
> 플랫폼은 결제를 처리하지 않고, 거래 방식 협의만 중개한다.

---

## 1. 개요

### 배경
- 기존: Mock 결제로 결제 완료를 시뮬레이션
- 변경: 플랫폼은 **거래 연결**만 담당, 실제 결제는 당사자 간 진행

### 핵심 콘셉트
- 경매로 **가격은 확정**됨 (흥정 없음)
- 플랫폼은 **거래 방식(직거래/택배)만 조율**
- 자유 채팅 없음 → 구조화된 플로우만 제공
- **거래 방식은 경매 등록 시 판매자가 미리 설정** → 구매자는 입찰 전 확인 가능

### 변경 범위
- Transaction 도메인 → **Trade 도메인**으로 리네이밍 및 재설계
- Mock 결제 로직 제거
- **Auction 도메인에 거래 방식 필드 추가**
- 거래 방식 선택 + 정보 교환 플로우 추가
- 노쇼 기준 변경 (미결제 → 미응답)

---

## 2. 거래 방식 설정 (경매 등록 시)

### 2.1 판매자 거래 방식 선택

경매 등록 시 판매자가 **가능한 거래 방식을 미리 설정**한다.

```
거래 방식 (최소 1개 선택 필수):
  ○ 직거래만 가능
  ○ 택배만 가능
  ○ 둘 다 가능

직거래 선택 시 → 직거래 희망 위치 입력 필수
  예: "강남역 근처", "서울 전체", "분당/판교"
```

### 2.2 Auction 도메인 추가 필드

```java
Auction {
    // 기존 필드...

    // 거래 방식 관련 추가 필드
    boolean directTradeAvailable;    // 직거래 가능 여부
    boolean deliveryAvailable;       // 택배 가능 여부
    String directTradeLocation;      // 직거래 희망 위치 (직거래 가능 시 필수)
}
```

### 2.3 입찰 전 구매자 확인

- 경매 상세 페이지에서 거래 방식 노출
- 구매자는 입찰 전 직거래 위치 / 택배 가능 여부 확인 가능

```
┌─────────────────────────────┐
│  거래 방식                    │
│  ✓ 직거래 (강남역 근처)       │
│  ✓ 택배 가능                  │
└─────────────────────────────┘
```

---

## 3. 거래 플로우 (낙찰 후)

### 3.1 전체 플로우

```
낙찰 확정
    ↓
[1] 거래 방식 결정
    ├─ 직거래만 가능 → 직거래 플로우로 바로 진행
    ├─ 택배만 가능 → 택배 플로우로 바로 진행
    └─ 둘 다 가능 → 구매자가 선택
    ↓
[2] 정보 교환
    ├─ 직거래 → 판매자가 시간 제안 → 구매자 수락/역제안
    └─ 택배 → 구매자가 배송지 입력 → 판매자가 송장번호 입력
    ↓
[3] 거래 완료 확인
    └─ 양측 모두 "거래 완료" 확인 시 완료 처리
```

### 3.2 직거래 플로우

```
(직거래로 결정됨 - 위치는 경매 등록 시 이미 설정됨)
    ↓
판매자 → 시간 제안
    - 날짜: 날짜 선택
    - 시간: 시간 선택
    - (장소는 이미 설정된 위치 기준)
    ↓
구매자 → 수락 / 역제안
    - 수락: 약속 확정
    - 역제안: 새로운 날짜·시간 제안
    ↓
(역제안 시 상대방 수락까지 반복)
    ↓
약속 확정
    ↓
거래 완료 확인 (양측)
```

### 3.3 택배 플로우

```
(택배로 결정됨)
    ↓
구매자 → 배송지 입력
    - 수령인 이름
    - 연락처
    - 주소 (우편번호 + 상세주소)
    ↓
판매자 → 배송지 확인 → 상품 발송
    ↓
판매자 → 송장번호 입력
    - 택배사 선택
    - 송장번호 입력
    ↓
구매자 → 상품 수령 확인
    ↓
거래 완료
```

---

## 4. 도메인 설계

### 4.1 Trade 도메인 모델

```java
Trade {
    Long id;
    Long auctionId;              // FK: AUCTION
    Long sellerId;               // FK: USER (판매자)
    Long buyerId;                // FK: USER (구매자/낙찰자)
    Long finalPrice;             // 최종 낙찰가
    TradeStatus status;          // 거래 상태
    TradeMethod method;          // 거래 방식 (DIRECT/DELIVERY)
    LocalDateTime responseDeadline;  // 응답 마감일시
    LocalDateTime createdAt;     // 거래 생성일시 (낙찰 시점)
    LocalDateTime completedAt;   // 거래 완료일시
}

// 직거래 정보
DirectTradeInfo {
    Long id;
    Long tradeId;                // FK: TRADE
    String location;             // 거래 장소
    LocalDate meetingDate;       // 만남 날짜
    LocalTime meetingTime;       // 만남 시간
    DirectTradeStatus status;    // PROPOSED / ACCEPTED / COUNTER_PROPOSED
    Long proposedBy;             // 제안자 ID
}

// 택배 정보
DeliveryInfo {
    Long id;
    Long tradeId;                // FK: TRADE
    String recipientName;        // 수령인
    String recipientPhone;       // 연락처
    String postalCode;           // 우편번호
    String address;              // 주소
    String addressDetail;        // 상세주소
    String courierCompany;       // 택배사
    String trackingNumber;       // 송장번호
    DeliveryStatus status;       // AWAITING_ADDRESS / ADDRESS_SUBMITTED / SHIPPED / DELIVERED
}
```

### 4.2 TradeStatus 상태 전이

```
AWAITING_METHOD_SELECTION   // 판매자 거래 방식 선택 대기
    ↓
AWAITING_ARRANGEMENT        // 거래 조율 중 (직거래: 약속 협의 / 택배: 주소 입력 대기)
    ↓
ARRANGED                    // 거래 조율 완료 (직거래: 약속 확정 / 택배: 발송 완료)
    ↓
COMPLETED                   // 거래 완료

CANCELLED                   // 거래 취소 (노쇼/유찰)
```

### 4.3 TradeMethod (거래 방식)

```java
enum TradeMethod {
    DIRECT,    // 직거래
    DELIVERY   // 택배
}
```

### 4.4 DirectTradeStatus (직거래 상태)

```java
enum DirectTradeStatus {
    PROPOSED,          // 제안됨
    COUNTER_PROPOSED,  // 역제안됨
    ACCEPTED           // 수락됨 (약속 확정)
}
```

### 4.5 DeliveryStatus (택배 상태)

```java
enum DeliveryStatus {
    AWAITING_ADDRESS,   // 배송지 입력 대기
    ADDRESS_SUBMITTED,  // 배송지 입력 완료
    SHIPPED,            // 발송 완료 (송장 입력됨)
    DELIVERED           // 배송 완료 (수령 확인됨)
}
```

---

## 5. 패키지 구조

```
src/main/java/com/cos/fairbid/trade/
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── TradeCommandUseCase.java       // 거래 상태 변경
│   │   │   ├── TradeQueryUseCase.java         // 거래 조회
│   │   │   ├── DirectTradeUseCase.java        // 직거래 조율
│   │   │   └── DeliveryUseCase.java           // 택배 거래
│   │   └── out/
│   │       ├── TradeRepositoryPort.java
│   │       ├── DirectTradeInfoRepositoryPort.java
│   │       └── DeliveryInfoRepositoryPort.java
│   └── service/
│       ├── TradeCommandService.java
│       ├── TradeQueryService.java
│       ├── DirectTradeService.java
│       └── DeliveryService.java
├── domain/
│   ├── Trade.java
│   ├── TradeStatus.java
│   ├── TradeMethod.java
│   ├── DirectTradeInfo.java
│   ├── DirectTradeStatus.java
│   ├── DeliveryInfo.java
│   └── DeliveryStatus.java
└── adapter/
    ├── in/
    │   ├── controller/
    │   │   ├── TradeController.java
    │   │   ├── DirectTradeController.java
    │   │   └── DeliveryController.java
    │   └── dto/
    │       ├── TradeResponse.java
    │       ├── SelectMethodRequest.java
    │       ├── DirectTradeProposalRequest.java
    │       ├── DirectTradeProposalResponse.java
    │       ├── DeliveryAddressRequest.java
    │       ├── ShippingInfoRequest.java
    │       └── DeliveryInfoResponse.java
    └── out/
        └── persistence/
            ├── entity/
            │   ├── TradeEntity.java
            │   ├── DirectTradeInfoEntity.java
            │   └── DeliveryInfoEntity.java
            ├── repository/
            │   ├── TradeJpaRepository.java
            │   ├── DirectTradeInfoJpaRepository.java
            │   └── DeliveryInfoJpaRepository.java
            ├── mapper/
            │   ├── TradeMapper.java
            │   ├── DirectTradeInfoMapper.java
            │   └── DeliveryInfoMapper.java
            └── TradePersistenceAdapter.java
```

---

## 6. API 설계

### 6.1 거래 조회

#### GET /api/v1/trades/{tradeId}

거래 상세 조회

**Response**
```json
{
  "success": true,
  "data": {
    "tradeId": 1,
    "auctionId": 42,
    "auctionTitle": "아이폰 15 Pro",
    "auctionImageUrl": "https://...",
    "sellerId": 10,
    "sellerNickname": "판매자닉네임",
    "buyerId": 20,
    "buyerNickname": "구매자닉네임",
    "finalPrice": 150000,
    "status": "AWAITING_ARRANGEMENT",
    "method": "DELIVERY",
    "responseDeadline": "2026-01-25T14:00:00",
    "createdAt": "2026-01-24T14:00:00",
    "completedAt": null,
    "directTradeInfo": null,
    "deliveryInfo": {
      "recipientName": "홍길동",
      "recipientPhone": "010-1234-5678",
      "address": "서울시 강남구...",
      "status": "AWAITING_ADDRESS"
    }
  }
}
```

#### GET /api/v1/trades/my

내 거래 목록 조회 (구매/판매 모두)

---

### 6.2 구매자 거래 방식 선택 (둘 다 가능인 경우)

#### POST /api/v1/trades/{tradeId}/method

구매자가 거래 방식 선택 (둘 다 가능한 경매의 경우에만)

**Request**
```json
{
  "method": "DIRECT"  // DIRECT 또는 DELIVERY
}
```

**Response**
```json
{
  "success": true,
  "data": {
    "tradeId": 1,
    "method": "DIRECT",
    "status": "AWAITING_ARRANGEMENT"
  }
}
```

---

### 6.3 직거래 API

#### POST /api/v1/trades/{tradeId}/direct/propose

직거래 장소·시간 제안 (판매자/구매자 모두 사용)

**Request**
```json
{
  "location": "강남역 11번 출구",
  "meetingDate": "2026-01-26",
  "meetingTime": "14:00"
}
```

#### POST /api/v1/trades/{tradeId}/direct/accept

제안 수락 (약속 확정)

#### POST /api/v1/trades/{tradeId}/direct/counter

역제안 (새로운 장소·시간 제안)

**Request**
```json
{
  "location": "선릉역 1번 출구",
  "meetingDate": "2026-01-27",
  "meetingTime": "15:00"
}
```

---

### 6.4 택배 API

#### POST /api/v1/trades/{tradeId}/delivery/address

구매자가 배송지 입력

**Request**
```json
{
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "postalCode": "06234",
  "address": "서울특별시 강남구 테헤란로 123",
  "addressDetail": "456호"
}
```

#### POST /api/v1/trades/{tradeId}/delivery/ship

판매자가 송장번호 입력 (발송 완료)

**Request**
```json
{
  "courierCompany": "CJ대한통운",
  "trackingNumber": "1234567890123"
}
```

#### POST /api/v1/trades/{tradeId}/delivery/confirm

구매자가 수령 확인

---

### 6.5 거래 완료

#### POST /api/v1/trades/{tradeId}/complete

거래 완료 확인 (직거래 시 양측 모두 호출 필요)

---

### 6.6 에러 코드

| 상황 | Error Code | HTTP Status |
|------|-----------|-------------|
| 거래 없음 | TRADE_NOT_FOUND | 404 |
| 권한 없음 | NOT_TRADE_PARTICIPANT | 403 |
| 이미 방식 선택됨 | METHOD_ALREADY_SELECTED | 400 |
| 잘못된 상태 | INVALID_TRADE_STATUS | 400 |
| 응답 기한 만료 | RESPONSE_DEADLINE_EXPIRED | 400 |

---

## 7. 노쇼 처리 변경

### 기존 (Mock 결제)
- 3시간 내 미결제 시 노쇼

### 변경 (거래 연결)

| 상황 | 기한 | 노쇼 처리 |
|------|------|----------|
| 구매자 거래 방식 미선택 (둘 다 가능 시) | 24시간 | 구매자 노쇼 |
| 구매자 미응답 (택배: 주소 미입력) | 24시간 | 구매자 노쇼 |
| 구매자 미응답 (직거래: 시간 미수락) | 24시간 | 구매자 노쇼 |
| 판매자 미응답 (직거래: 시간 미제안) | 24시간 | 판매자 노쇼 (신규) |
| 판매자 미발송 (택배: 송장 미입력) | 72시간 | 판매자 노쇼 (신규) |

### 2순위 승계
- 기존과 동일: 1순위 노쇼 시 2순위에게 기회 부여
- 2순위 응답 기한: 12시간

---

## 8. 알림

### 신규/변경 알림 타입

| 타입 | 대상 | 제목 | 시점 |
|------|------|------|------|
| TRADE_CREATED_SELLER | 판매자 | "낙찰자가 결정되었습니다" | 낙찰 시 |
| TRADE_CREATED_BUYER | 구매자 | "축하합니다! 낙찰되었습니다" | 낙찰 시 |
| METHOD_SELECTED | 판매자 | "구매자가 거래 방식을 선택했습니다" | 둘 다 가능 시 구매자 선택 |
| DIRECT_TIME_PROPOSAL | 구매자 | "직거래 시간이 제안되었습니다" | 판매자 시간 제안 시 |
| DIRECT_COUNTER_PROPOSAL | 판매자 | "직거래 시간 역제안이 도착했습니다" | 구매자 역제안 시 |
| DIRECT_ACCEPTED | 양측 | "직거래 약속이 확정되었습니다" | 수락 시 |
| ADDRESS_SUBMITTED | 판매자 | "배송지가 입력되었습니다" | 주소 입력 시 |
| SHIPPED | 구매자 | "상품이 발송되었습니다" | 송장 입력 시 |
| TRADE_COMPLETED | 양측 | "거래가 완료되었습니다" | 완료 시 |
| RESPONSE_REMINDER | 해당자 | "거래 응답 기한이 임박했습니다" | 기한 12시간 전 |

---

## 9. 프론트엔드 설계

### 9.1 라우트 구조

| 경로 | 페이지 | 설명 |
|------|--------|------|
| `/trades/{id}` | 거래 상세 | 거래 현황 및 조율 |
| `/trades` | 거래 목록 | 내 거래 내역 |

### 9.2 거래 상세 페이지 UI

**판매자 - 거래 방식 선택 전**
```
┌──────────────────────────────────┐
│  [상품 이미지]  아이폰 15 Pro      │
│  낙찰가: 150,000원                │
│  구매자: buyer_nick               │
├──────────────────────────────────┤
│  거래 방식을 선택해주세요           │
│                                   │
│  ┌─────────┐  ┌─────────┐        │
│  │  직거래  │  │   택배   │        │
│  └─────────┘  └─────────┘        │
│                                   │
│  선택 기한: 2026-01-25 14:00      │
└──────────────────────────────────┘
```

**판매자 - 직거래 제안**
```
┌──────────────────────────────────┐
│  거래 방식: 직거래                 │
├──────────────────────────────────┤
│  만남 장소·시간을 제안해주세요      │
│                                   │
│  장소: [강남역 11번 출구      ]    │
│  날짜: [2026-01-26 ▼]            │
│  시간: [14:00 ▼]                 │
│                                   │
│  ┌────────────────────────────┐  │
│  │         제안하기            │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

**구매자 - 직거래 제안 수락/역제안**
```
┌──────────────────────────────────┐
│  거래 방식: 직거래                 │
├──────────────────────────────────┤
│  판매자 제안                      │
│  장소: 강남역 11번 출구            │
│  날짜: 2026-01-26                 │
│  시간: 14:00                      │
│                                   │
│  ┌──────────┐  ┌──────────┐     │
│  │   수락    │  │  역제안   │     │
│  └──────────┘  └──────────┘     │
└──────────────────────────────────┘
```

**구매자 - 택배 배송지 입력**
```
┌──────────────────────────────────┐
│  거래 방식: 택배                   │
├──────────────────────────────────┤
│  배송지 정보를 입력해주세요         │
│                                   │
│  수령인: [홍길동            ]     │
│  연락처: [010-1234-5678    ]     │
│  우편번호: [06234] [검색]         │
│  주소: [서울특별시 강남구...]      │
│  상세주소: [456호           ]     │
│                                   │
│  ┌────────────────────────────┐  │
│  │        배송지 입력          │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

**판매자 - 송장번호 입력**
```
┌──────────────────────────────────┐
│  거래 방식: 택배                   │
├──────────────────────────────────┤
│  배송지                          │
│  홍길동 / 010-1234-5678          │
│  서울특별시 강남구... 456호        │
├──────────────────────────────────┤
│  발송 정보를 입력해주세요          │
│                                   │
│  택배사: [CJ대한통운 ▼]           │
│  송장번호: [1234567890123  ]     │
│                                   │
│  ┌────────────────────────────┐  │
│  │        발송 완료            │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

---

## 10. 기존 코드 변경사항

### 10.1 Transaction → Trade 마이그레이션

| 기존 | 변경 |
|------|------|
| Transaction 도메인 | Trade 도메인 |
| TransactionStatus | TradeStatus |
| PaymentService | TradeCommandService |
| MockPaymentProcessor | 삭제 |

### 10.2 Auction 도메인 변경 (거래 방식 필드 추가)

```java
// AuctionEntity에 추가
@Column(nullable = false)
private Boolean directTradeAvailable;

@Column(nullable = false)
private Boolean deliveryAvailable;

@Column
private String directTradeLocation;  // 직거래 가능 시 필수
```

### 10.3 Winning/NoShow 연동 변경

- AuctionClosingProcessor: Transaction 생성 → Trade 생성
- NoShowProcessor: 결제 기한 체크 → 응답 기한 체크
- NoShowProcessor: 판매자 노쇼 로직 추가

### 10.4 알림 변경

- 결제 관련 알림 → 거래 조율 알림으로 변경
- PAYMENT_COMPLETED → TRADE_COMPLETED
- PAYMENT_REMINDER → METHOD_SELECTION_REMINDER 등

---

## 11. 구현 우선순위

1. **Auction 도메인 변경** (거래 방식 필드 추가: directTradeAvailable, deliveryAvailable, directTradeLocation)
2. **경매 등록 UI 변경** (거래 방식 선택, 직거래 위치 입력)
3. **Trade 도메인 재설계** (Trade, DirectTradeInfo, DeliveryInfo)
4. **택배 플로우** (주소 입력 → 송장 입력 → 수령 확인)
5. **직거래 플로우** (시간 제안 → 수락/역제안 → 확정)
6. **노쇼 로직 변경** (응답 기한 기반, 판매자 노쇼 추가)
7. **알림 변경**
8. **프론트엔드 거래 페이지**
9. **테스트**

---

## 12. 트레이드오프 & 결정 사항

| 결정 | 선택 | 근거 |
|------|------|------|
| 거래 방식 결정 시점 | 경매 등록 시 판매자가 설정 | 구매자가 입찰 전 확인 가능, 낙찰 후 갈등 방지 |
| 거래 방식 선택 주체 | 둘 다 가능 시 구매자가 선택 | 판매자는 이미 등록 시 허용 범위 설정함 |
| 채팅 여부 | 채팅 없음 | 경매로 가격 확정, 흥정 방지 |
| 거래 조율 | 구조화된 플로우 | 필요한 정보만 교환 |
| 주소 관리 | 판매자에게 직접 공개 | MVP 단순화, 택배 발송에 필수 |
| 편의점 픽업 | 미지원 (추후 고도화) | 택배사 API 연동 필요 |
| 직거래 역제안 | 시간만 역제안 (위치는 고정) | 위치는 경매 등록 시 확정, 시간만 조율 |
| 거래 완료 확인 | 직거래: 양측 / 택배: 구매자만 | 직거래는 상호 확인 필요 |

---

## 13. 삭제 대상 (기존 Mock 결제)

- `MockPaymentProcessor.java`
- `PaymentUseCase.java`
- `PaymentService.java`
- 결제 관련 API 엔드포인트
- 결제 관련 프론트엔드 페이지 (`/payment`, `/payment/complete`)
