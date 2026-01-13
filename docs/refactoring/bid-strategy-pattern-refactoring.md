# Bid 모듈 전략 패턴 리팩토링

## 1. 개요

Bid 도메인의 입찰 금액 계산 로직을 BidType enum에 전략 패턴으로 분리하고, 입찰 검증 로직을 강화함.

---

## 2. 리팩토링 전 문제점

### 2.1 입찰 금액 계산 (Bid.java:63-74)

**문제점**:
- if-else 분기로 입찰 유형 처리
- 새로운 입찰 방식 추가 시 코드 수정 필요 (OCP 위반)

```java
// Before
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

### 2.2 입찰 검증 (Auction.validateBidEligibility)

**문제점**:
- bidderId가 null일 경우 NPE 발생 가능
- 명시적인 예외 처리 부재

```java
// Before
public void validateBidEligibility(Long bidderId) {
    if (isEnded()) {
        throw AuctionEndedException.forBid(this.id);
    }
    // bidderId가 null이면 여기서 NPE 발생
    if (this.sellerId.equals(bidderId)) {
        throw SelfBidNotAllowedException.forAuction(this.id, this.sellerId);
    }
}
```

---

## 3. 리팩토링 내용

### 3.1 BidType 전략 패턴 적용

각 입찰 유형이 자신의 금액 계산 로직을 직접 구현.

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
    };

    public abstract Long calculateAmount(Long requestedAmount, Auction auction);
}
```

**장점**:
- 새로운 입찰 방식 추가 시 enum 상수만 추가
- 각 유형의 로직이 해당 enum에 캡슐화
- if-else 분기 제거

### 3.2 Bid.determineBidAmount() 단순화

BidType에 위임하여 한 줄로 단순화.

```java
// After
public static Long determineBidAmount(BidType bidType, Long requestedAmount, Auction auction) {
    return bidType.calculateAmount(requestedAmount, auction);
}
```

### 3.3 InvalidBidException 팩토리 메서드 추가

입찰자 ID 누락 시 사용할 예외 메서드 추가.

```java
public static InvalidBidException bidderIdRequired() {
    return new InvalidBidException(
            "BIDDER_ID_REQUIRED",
            "입찰자 ID는 필수입니다."
    );
}
```

### 3.4 Auction.validateBidEligibility() null 체크 추가

bidderId null 검사를 명시적으로 추가.

```java
// After
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

## 4. 파일 변경 요약

### 4.1 수정 파일 (4개)

| 파일 경로 | 변경 내용 |
|----------|----------|
| `bid/domain/BidType.java` | 전략 패턴 적용, `calculateAmount()` 추상 메서드 추가 |
| `bid/domain/Bid.java` | if-else 제거, BidType에 위임 |
| `bid/domain/exception/InvalidBidException.java` | `bidderIdRequired()` 팩토리 메서드 추가 |
| `auction/domain/Auction.java` | `validateBidEligibility()`에 null 체크 추가 |

---

## 5. 구조 다이어그램

### 리팩토링 전

```
BidService
    └── Bid.determineBidAmount()
            ├── if (ONE_TOUCH) → auction.getMinBidAmount()
            └── if (DIRECT) → requestedAmount
```

### 리팩토링 후

```
BidService
    └── Bid.determineBidAmount()
            └── BidType.calculateAmount()  // 전략 패턴
                    ├── ONE_TOUCH.calculateAmount()
                    └── DIRECT.calculateAmount()
```

---

## 6. 확장 예시

새로운 입찰 방식 (예: 자동 상향 입찰) 추가 시:

```java
public enum BidType {
    ONE_TOUCH { ... },
    DIRECT { ... },

    // 새로운 입찰 방식 추가
    AUTO_INCREMENT {
        @Override
        public Long calculateAmount(Long requestedAmount, Auction auction) {
            // 자동 상향 입찰 로직
            Long minBid = auction.getMinBidAmount();
            Long increment = auction.getBidIncrement();
            return minBid + increment;  // 예시
        }
    };
}
```

기존 코드 수정 없이 enum 상수만 추가하면 됨.

---

## 7. 검증

```bash
# Cucumber 테스트 실행
./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"

# 전체 빌드
./gradlew build
```

모든 테스트 통과 확인 완료.
