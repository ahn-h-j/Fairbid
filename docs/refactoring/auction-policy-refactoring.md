# Auction 도메인 Policy 분리 리팩토링

> 📅 작업일: 2026-01-XX
> 🎯 목표: 입찰 단위/연장 로직을 Policy 클래스로 분리하여 SRP 준수

---

## Before / After 요약

| 항목 | Before | After |
|------|--------|-------|
| 입찰 단위 계산 | Auction 내 7개 if-else 체인 | `PriceBracket` Enum + `BidIncrementPolicy` |
| 연장 로직 | Auction 내 하드코딩 상수 | `AuctionExtensionPolicy` |
| 매직 넘버 | 코드 곳곳에 분산 | Enum/상수로 중앙 집중화 |

---

## 1. 문제점 (Before)

### 입찰 단위 계산 - 7개 if-else 체인

```java
// Auction.java - 매직 넘버 + 긴 분기문
public static Long calculateBidIncrement(Long price) {
    if (price < 10_000L) {
        return 500L;
    } else if (price < 50_000L) {
        return 1_000L;
    } else if (price < 100_000L) {
        return 3_000L;
    } else if (price < 500_000L) {
        return 5_000L;
    } else if (price < 1_000_000L) {
        return 10_000L;
    } else {
        return 30_000L;
    }
}
```

**문제:**
- 가격 구간 추가/수정 시 코드 직접 수정 필요
- 매직 넘버로 의미 파악 어려움
- 테스트하기 어려운 구조

---

## 2. 해결책 (After)

### 2.1 PriceBracket Enum - 가격 구간 테이블화

```java
public enum PriceBracket {
    UNDER_10K(10_000L, 500L),
    UNDER_50K(50_000L, 1_000L),
    UNDER_100K(100_000L, 3_000L),
    UNDER_500K(500_000L, 5_000L),
    UNDER_1M(1_000_000L, 10_000L),
    OVER_1M(Long.MAX_VALUE, 30_000L);

    private final Long upperBound;
    private final Long increment;

    public static Long getIncrementForPrice(Long price) {
        return Arrays.stream(values())
                .filter(bracket -> price < bracket.upperBound)
                .findFirst()
                .map(PriceBracket::getIncrement)
                .orElse(OVER_1M.increment);
    }
}
```

### 2.2 BidIncrementPolicy - 입찰 단위 + 할증 계산

```java
public class BidIncrementPolicy {
    private static final int EXTENSION_SURCHARGE_INTERVAL = 3;  // N회마다 할증
    private static final double SURCHARGE_RATE = 0.5;           // 50% 할증

    public static Long calculateBaseIncrement(Long currentPrice) {
        return PriceBracket.getIncrementForPrice(currentPrice);
    }

    public static Long calculateAdjustedIncrement(Long baseIncrement, int extensionCount) {
        int surchargeMultiplier = extensionCount / EXTENSION_SURCHARGE_INTERVAL;
        double multiplier = 1 + (SURCHARGE_RATE * surchargeMultiplier);
        return Math.round(baseIncrement * multiplier);
    }
}
```

### 2.3 AuctionExtensionPolicy - 연장 규칙

```java
public class AuctionExtensionPolicy {
    private static final int EXTENSION_THRESHOLD_MINUTES = 5;  // 종료 N분 전
    private static final int EXTENSION_DURATION_MINUTES = 5;   // N분 연장

    public static boolean isInExtensionPeriod(LocalDateTime endTime, LocalDateTime now) {
        LocalDateTime threshold = endTime.minusMinutes(EXTENSION_THRESHOLD_MINUTES);
        return now.isAfter(threshold) && now.isBefore(endTime);
    }

    public static LocalDateTime calculateExtendedEndTime(LocalDateTime now) {
        return now.plusMinutes(EXTENSION_DURATION_MINUTES);
    }
}
```

### 2.4 Auction 도메인 - Policy에 위임

```java
// After - 한 줄로 단순화
public static Long calculateBidIncrement(Long price) {
    return BidIncrementPolicy.calculateBaseIncrement(price);
}

public boolean isInExtensionPeriod() {
    return AuctionExtensionPolicy.isInExtensionPeriod(scheduledEndTime, LocalDateTime.now());
}
```

---

## 3. 개선 효과

| 측면 | 개선 내용 |
|------|----------|
| **확장성** | 가격 구간 추가 시 Enum에 한 줄만 추가 |
| **가독성** | 비즈니스 규칙이 테이블 형태로 한눈에 파악 |
| **테스트** | Policy 클래스 단위 테스트 용이 |
| **SRP** | Auction은 경매 상태만, Policy는 계산 규칙만 담당 |

---

## 4. 파일 구조

```
auction/domain/
├── Auction.java              # Policy에 위임
└── policy/
    ├── PriceBracket.java         # 가격 구간 Enum
    ├── BidIncrementPolicy.java   # 입찰 단위 계산
    └── AuctionExtensionPolicy.java # 연장 규칙
```

---

## 5. 비즈니스 규칙 정리

### 입찰 단위

| 현재 가격 | 입찰 단위 |
|----------|----------|
| ~1만 원 | +500원 |
| 1만~5만 원 | +1,000원 |
| 5만~10만 원 | +3,000원 |
| 10만~50만 원 | +5,000원 |
| 50만~100만 원 | +10,000원 |
| 100만 원~ | +30,000원 |

### 연장/할증 규칙

| 규칙 | 값 |
|-----|---|
| 연장 구간 | 종료 5분 전 |
| 연장 시간 | +5분 |
| 할증 주기 | 3회마다 |
| 할증 비율 | +50% |
