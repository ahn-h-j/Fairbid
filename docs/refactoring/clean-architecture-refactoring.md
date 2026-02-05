# Service 클래스 클린 아키텍처 리팩토링

> 📅 작업일: 2026-01-XX
> 🎯 목표: Service의 private 메서드 제거, 비즈니스 로직을 도메인/Port로 분리

---

## Before / After 요약

| 항목 | Before | After |
|------|--------|-------|
| Service private 메서드 | 10개 | 0개 |
| 이벤트 발행 | Service 내부에서 직접 | Output Port로 분리 |
| 도메인 로직 | Helper에 분산 | Domain Service로 집중 |

---

## 리팩토링 원칙

```text
1. Service에 private 메서드 금지
2. 로직은 도메인 객체 또는 협력 객체로 이동
3. Service는 객체 간 메시지 전달만 담당
4. 메서드 10줄 미만
```

---

## 1. BidService 리팩토링

### Before - private 메서드 2개

```java
public class BidService {

    public BidResponse placeBid(PlaceBidCommand command) {
        // ... 로직
        Long amount = determineBidAmount(command.getBidType(), ...);  // private
        publishBidPlacedEvent(bid, auction);  // private
    }

    // private 메서드들
    private Long determineBidAmount(BidType bidType, Long amount, Auction auction) {
        if (bidType == BidType.ONE_TOUCH) {
            return auction.getMinBidAmount();
        }
        // ...
    }

    private void publishBidPlacedEvent(Bid bid, Auction auction) {
        BidPlacedEvent event = new BidPlacedEvent(...);
        eventPublisher.publishEvent(event);
    }
}
```

### After - 도메인 + Port로 분리

```java
public class BidService {

    private final BidEventPublisher bidEventPublisher;  // Output Port

    public BidResponse placeBid(PlaceBidCommand command) {
        // 도메인에 위임
        Long amount = Bid.determineBidAmount(command.getBidType(), ...);

        // Port에 위임
        bidEventPublisher.publish(bid, auction);
    }
}
```

**생성된 파일:**
- `bid/application/port/out/BidEventPublisher.java` (Port)
- `bid/adapter/out/event/BidEventPublisherAdapter.java` (Adapter)

---

## 2. AuctionClosingHelper 리팩토링

### Before - private 메서드 4개

```java
public class AuctionClosingHelper {

    public void processAuctionClosing(Long auctionId) {
        // ...
        handleNoWinner(auction);           // private
        handleFirstRankWinner(auction);    // private
        saveSecondRankCandidate(auction);  // private
        publishAuctionClosedEvent(event);  // private
    }

    private void handleNoWinner(Auction auction) { /* 30줄 */ }
    private void handleFirstRankWinner(Auction auction) { /* 40줄 */ }
    private void saveSecondRankCandidate(Auction auction) { /* 20줄 */ }
    private void publishAuctionClosedEvent(AuctionClosedEvent event) { /* 10줄 */ }
}
```

### After - Domain Service로 분리

```java
public class AuctionClosingHelper {

    private final AuctionClosingProcessor processor;     // Domain Service
    private final AuctionClosedEventPublisher publisher; // Output Port

    public void processAuctionClosing(Long auctionId) {
        // Domain Service에 위임
        processor.processNoWinner(auction);
        processor.processFirstRankWinner(auction);
        processor.saveSecondRankCandidate(auction);

        // Port에 위임
        publisher.publish(event);
    }
}
```

**생성된 파일:**
- `winning/domain/service/AuctionClosingProcessor.java` (Domain Service)
- `winning/application/port/out/AuctionClosedEventPublisher.java` (Port)
- `winning/adapter/out/event/AuctionClosedEventPublisherAdapter.java` (Adapter)

---

## 3. NoShowProcessingHelper 리팩토링

### Before - private 메서드 4개

```java
public class NoShowProcessingHelper {

    private void processFirstRankNoShow(Winning winning) { /* ... */ }
    private void transferToSecondRank(Winning secondRank) { /* ... */ }
    private void processSecondRankExpired(Winning winning) { /* ... */ }
    private void handleAuctionFailed(Long auctionId) { /* ... */ }
}
```

### After - Domain Service로 분리

```java
public class NoShowProcessingHelper {

    private final NoShowProcessor processor;  // Domain Service

    public void processExpiredWinning(Long winningId) {
        processor.processFirstRankNoShow(winning);
        processor.transferToSecondRank(secondRank);
        processor.failAuction(auctionId);
    }
}
```

**생성된 파일:**
- `winning/domain/service/NoShowProcessor.java` (Domain Service)

---

## 4. 개선 효과

| 측면 | 개선 내용 |
|------|----------|
| **테스트 용이성** | Domain Service 단위 테스트 가능 |
| **SRP** | Service는 조율만, 로직은 도메인에 |
| **의존성 역전** | Port 인터페이스로 외부 의존성 분리 |

---

## 5. 아키텍처 다이어그램

```text
┌─────────────────────────────────────────────────────┐
│                 Application Layer                    │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ BidService  │  │ Closing      │  │ NoShow     │  │
│  │             │  │   Helper     │  │   Helper   │  │
│  └──────┬──────┘  └──────┬───────┘  └─────┬──────┘  │
│         │                │                │          │
│         ▼                ▼                ▼          │
│  ┌───────────────────────────────────────────────┐  │
│  │              Output Ports                      │  │
│  │  BidEventPublisher │ AuctionClosedEventPublisher│
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│                   Domain Layer                       │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │    Bid      │  │ Closing      │  │ NoShow     │  │
│  │  (Entity)   │  │  Processor   │  │ Processor  │  │
│  └─────────────┘  └──────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│                  Adapter Layer                       │
│  BidEventPublisherAdapter │ AuctionClosedEvent...   │
└─────────────────────────────────────────────────────┘
```

---

## 6. 파일 변경 요약

### 신규 파일 (6개)

| 파일 | 역할 |
|------|------|
| `bid/port/out/BidEventPublisher.java` | 입찰 이벤트 발행 Port |
| `bid/adapter/out/event/BidEventPublisherAdapter.java` | Port 구현체 |
| `winning/port/out/AuctionClosedEventPublisher.java` | 경매 종료 이벤트 Port |
| `winning/adapter/out/event/AuctionClosedEventPublisherAdapter.java` | Port 구현체 |
| `winning/domain/service/AuctionClosingProcessor.java` | 경매 종료 Domain Service |
| `winning/domain/service/NoShowProcessor.java` | 노쇼 처리 Domain Service |

### 수정 파일 (4개)

| 파일 | 변경 |
|------|------|
| `Bid.java` | `determineBidAmount()` 정적 메서드 추가 |
| `BidService.java` | private 메서드 제거, Port/도메인 호출로 대체 |
| `AuctionClosingHelper.java` | private 메서드 제거, Domain Service 호출 |
| `NoShowProcessingHelper.java` | private 메서드 제거, Domain Service 호출 |
