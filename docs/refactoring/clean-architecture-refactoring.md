# 클린 아키텍처 리팩토링

## 1. 개요

Service 클래스에서 private 메서드를 제거하고, 비즈니스 로직을 도메인 객체 또는 도메인 서비스로 이동하는 리팩토링을 수행함.

### 리팩토링 원칙
1. **Service에 private 메서드 금지**
2. **로직은 도메인 객체 또는 협력 객체로 이동**
3. **Service는 객체 간 메시지 전달만 담당**
4. **메서드 10줄 미만**
5. **비즈니스 로직 변경 금지** (Cucumber 테스트 통과 필수)

---

## 2. 리팩토링 전 상태 분석

### 2.1 private 메서드가 있던 Service 클래스

#### BidService.java (2개)
| 메서드 | 역할 |
|--------|------|
| `determineBidAmount()` | 입찰 금액 결정 (ONE_TOUCH vs DIRECT) |
| `publishBidPlacedEvent()` | 입찰 이벤트 발행 |

#### AuctionClosingHelper.java (4개)
| 메서드 | 역할 |
|--------|------|
| `handleNoWinner()` | 유찰 처리 |
| `handleFirstRankWinner()` | 1순위 낙찰자 처리 |
| `saveSecondRankCandidate()` | 2순위 후보 저장 |
| `publishAuctionClosedEvent()` | 경매 종료 이벤트 발행 |

#### NoShowProcessingHelper.java (4개)
| 메서드 | 역할 |
|--------|------|
| `processFirstRankNoShow()` | 1순위 노쇼 처리 |
| `transferToSecondRank()` | 2순위 승계 |
| `processSecondRankExpired()` | 2순위 만료 처리 |
| `handleAuctionFailed()` | 경매 유찰 처리 |

### 2.2 양호한 Service 클래스 (변경 불필요)
- `AuctionService.java` - private 메서드 없음
- `AuctionClosingService.java` - private 메서드 없음
- `NoShowProcessingService.java` - private 메서드 없음

---

## 3. 리팩토링 내용

### 3.1 BidService 리팩토링

#### `determineBidAmount()` → `Bid` 도메인으로 이동

**이유**: 입찰 금액 결정은 Bid 도메인의 책임

**Bid.java에 추가된 메서드**:
```java
public static Long determineBidAmount(BidType bidType, Long requestedAmount, Auction auction) {
    if (bidType == BidType.ONE_TOUCH) {
        return auction.getMinBidAmount();
    }
    if (requestedAmount == null) {
        throw InvalidBidException.amountRequiredForDirectBid();
    }
    return requestedAmount;
}
```

#### `publishBidPlacedEvent()` → Output Port로 분리

**이유**: 이벤트 발행은 외부 인프라 관심사

**생성된 파일**:
- `bid/application/port/out/BidEventPublisher.java` (Port 인터페이스)
- `bid/adapter/out/event/BidEventPublisherAdapter.java` (Adapter 구현체)

---

### 3.2 AuctionClosingHelper 리팩토링

#### 모든 private 메서드 → 도메인 서비스로 이동

**생성된 파일**: `winning/domain/service/AuctionClosingProcessor.java`

| 이동 전 | 이동 후 |
|--------|--------|
| `handleNoWinner()` | `AuctionClosingProcessor.processNoWinner()` |
| `handleFirstRankWinner()` | `AuctionClosingProcessor.processFirstRankWinner()` |
| `saveSecondRankCandidate()` | `AuctionClosingProcessor.saveSecondRankCandidate()` |
| `publishAuctionClosedEvent()` | `AuctionClosedEventPublisher` (Port) |

---

### 3.3 NoShowProcessingHelper 리팩토링

#### 모든 private 메서드 → 도메인 서비스로 이동

**생성된 파일**: `winning/domain/service/NoShowProcessor.java`

| 이동 전 | 이동 후 |
|--------|--------|
| `processFirstRankNoShow()` | `NoShowProcessor.processFirstRankNoShow()` |
| `transferToSecondRank()` | `NoShowProcessor.transferToSecondRank()` |
| `processSecondRankExpired()` | `NoShowProcessor.processSecondRankExpired()` |
| `handleAuctionFailed()` | `NoShowProcessor.failAuction()` |

---

## 4. 파일 변경 요약

### 4.1 신규 파일 (6개)

| 파일 경로 | 역할 |
|----------|------|
| `bid/application/port/out/BidEventPublisher.java` | 입찰 이벤트 발행 Port |
| `bid/adapter/out/event/BidEventPublisherAdapter.java` | 입찰 이벤트 발행 Adapter |
| `winning/application/port/out/AuctionClosedEventPublisher.java` | 경매 종료 이벤트 발행 Port |
| `winning/adapter/out/event/AuctionClosedEventPublisherAdapter.java` | 경매 종료 이벤트 발행 Adapter |
| `winning/domain/service/AuctionClosingProcessor.java` | 경매 종료 처리 도메인 서비스 |
| `winning/domain/service/NoShowProcessor.java` | 노쇼 처리 도메인 서비스 |

### 4.2 수정 파일 (4개)

| 파일 경로 | 변경 내용 |
|----------|----------|
| `bid/domain/Bid.java` | `determineBidAmount()` 정적 메서드 추가 |
| `bid/application/service/BidService.java` | private 메서드 제거, Port/도메인 호출로 대체 |
| `winning/application/service/AuctionClosingHelper.java` | private 메서드 제거, 도메인 서비스 호출로 대체 |
| `winning/application/service/NoShowProcessingHelper.java` | private 메서드 제거, 도메인 서비스 호출로 대체 |

---

## 5. 아키텍처 다이어그램

### 리팩토링 후 의존성 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                      Application Layer                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │   BidService    │  │AuctionClosing   │  │  NoShowProcessing│  │
│  │                 │  │    Helper       │  │     Helper       │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
│           │                    │                    │           │
│           ▼                    ▼                    ▼           │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                     Output Ports                            ││
│  │  BidEventPublisher  │  AuctionClosedEventPublisher          ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Domain Layer                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │      Bid        │  │AuctionClosing   │  │  NoShowProcessor│  │
│  │  (Domain Model) │  │   Processor     │  │ (Domain Service)│  │
│  │                 │  │(Domain Service) │  │                 │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Adapter Layer                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  BidEventPublisherAdapter  │  AuctionClosedEventPublisher   ││
│  │                            │         Adapter                ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. 검증

```bash
# Cucumber 테스트 실행
./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"

# 전체 빌드
./gradlew clean build
```

모든 테스트 통과 확인 완료.
