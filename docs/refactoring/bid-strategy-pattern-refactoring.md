# Bid 도메인 전략 패턴 리팩토링

> 📅 작업일: 2026-01-XX
> 🎯 목표: 입찰 유형별 금액 계산 로직을 전략 패턴으로 분리하여 OCP 준수

---

## Before / After 요약

| 항목 | Before | After |
|------|--------|-------|
| 입찰 금액 계산 | if-else 분기 | `BidType` Enum 전략 패턴 |
| 새 입찰 방식 추가 | 코드 수정 필요 (OCP 위반) | Enum 상수만 추가 |
| bidderId 검증 | NPE 발생 가능 | 명시적 null 체크 |

---

## 1. 문제점 (Before)

### 1.1 입찰 금액 계산 - if-else 분기

```java
// Bid.java - 입찰 유형별 분기문
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

**문제:**
- 새로운 입찰 방식 추가 시 if-else 추가 필요 (OCP 위반)
- 입찰 유형 로직이 Bid 클래스에 분산

### 1.2 입찰자 검증 - NPE 가능

```java
// Auction.java - bidderId null 시 NPE
public void validateBidEligibility(Long bidderId) {
    if (isEnded()) {
        throw AuctionEndedException.forBid(this.id);
    }
    // bidderId가 null이면 여기서 NPE 발생!
    if (this.sellerId.equals(bidderId)) {
        throw SelfBidNotAllowedException.forAuction(this.id, this.sellerId);
    }
}
```

---

## 2. 해결책 (After)

### 2.1 BidType Enum - 전략 패턴 적용

각 입찰 유형이 자신의 금액 계산 로직을 직접 구현:

```java
public enum BidType {

    ONE_TOUCH {
        @Override
        public Long calculateAmount(Long requestedAmount, Auction auction) {
            return auction.getMinBidAmount();
        }
    },

    DIRECT {
        @Override
        public Long calculateAmount(Long requestedAmount, Auction auction) {
            if (requestedAmount == null) {
                throw InvalidBidException.amountRequiredForDirectBid();
            }
            return requestedAmount;
        }
    },

    INSTANT_BUY {
        @Override
        public Long calculateAmount(Long requestedAmount, Auction auction) {
            Long instantBuyPrice = auction.getInstantBuyPrice();
            if (instantBuyPrice == null) {
                throw InstantBuyException.notAvailable(auction.getId());
            }
            return instantBuyPrice;
        }
    };

    public abstract Long calculateAmount(Long requestedAmount, Auction auction);
}
```

### 2.2 Bid.determineBidAmount() - 단순화

```java
// After - 한 줄로 위임
public static Long determineBidAmount(BidType bidType, Long requestedAmount, Auction auction) {
    return bidType.calculateAmount(requestedAmount, auction);
}
```

### 2.3 Auction.validateBidEligibility() - null 체크 추가

```java
// After - 명시적 null 검증
public void validateBidEligibility(Long bidderId) {
    if (bidderId == null) {
        throw InvalidBidException.bidderIdRequired();
    }

    if (isEnded()) {
        throw AuctionEndedException.forBid(this.id);
    }

    if (this.sellerId.equals(bidderId)) {
        throw SelfBidNotAllowedException.forAuction(this.id, this.sellerId);
    }
}
```

---

## 3. 개선 효과

| 측면 | 개선 내용 |
|------|----------|
| **OCP** | 새 입찰 방식 추가 시 Enum 상수만 추가, 기존 코드 수정 없음 |
| **캡슐화** | 각 유형의 로직이 해당 Enum에 캡슐화 |
| **안정성** | NPE 대신 명시적 예외로 디버깅 용이 |

---

## 4. 확장 예시

새로운 입찰 방식 추가 시 (예: 자동 상향 입찰):

```java
public enum BidType {
    ONE_TOUCH { ... },
    DIRECT { ... },
    INSTANT_BUY { ... },

    // 새로운 입찰 방식 - Enum 상수만 추가
    AUTO_INCREMENT {
        @Override
        public Long calculateAmount(Long requestedAmount, Auction auction) {
            return auction.getMinBidAmount() + auction.getBidIncrement();
        }
    };
}
```

**기존 코드 수정 없이 확장 완료** ✓

---

## 5. 파일 변경

| 파일 | 변경 내용 |
|------|----------|
| `BidType.java` | `calculateAmount()` 추상 메서드 + 각 유형별 구현 |
| `Bid.java` | if-else 제거, BidType에 위임 |
| `Auction.java` | `validateBidEligibility()`에 null 체크 추가 |
| `InvalidBidException.java` | `bidderIdRequired()` 팩토리 메서드 추가 |
