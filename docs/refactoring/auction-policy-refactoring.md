# Auction 도메인 Policy 분리 리팩토링

## 1. 개요

Auction 도메인의 책임 과다 문제를 해결하기 위해 입찰 단위 계산과 연장 로직을 별도 Policy 클래스로 분리함.

---

## 2. 리팩토링 전 문제점

### 2.1 입찰 단위 계산 (Auction.java:106-120)

**문제점**:
- 7개 구간의 if-else 체인
- 매직 넘버 하드코딩
- 가격 구간 변경 시 코드 수정 필요

```java
// Before
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

### 2.2 연장 로직

**문제점**:
- 연장 구간(5분), 연장 시간(5분) 등 상수가 분산
- 할증 계산 로직이 Auction에 직접 구현

---

## 3. 리팩토링 내용

### 3.1 PriceBracket Enum 생성

가격 구간과 입찰 단위를 Enum으로 추상화.

```java
public enum PriceBracket {
    UNDER_10K(10_000L, 500L),
    UNDER_50K(50_000L, 1_000L),
    UNDER_100K(100_000L, 3_000L),
    UNDER_500K(500_000L, 5_000L),
    UNDER_1M(1_000_000L, 10_000L),
    OVER_1M(Long.MAX_VALUE, 30_000L);

    public static Long getIncrementForPrice(Long price) {
        return Arrays.stream(values())
                .filter(bracket -> price < bracket.upperBound)
                .findFirst()
                .map(PriceBracket::getIncrement)
                .orElse(OVER_1M.increment);
    }
}
```

**장점**:
- 가격 구간 추가/수정 시 Enum만 변경
- 테이블 형태로 구간 한눈에 파악 가능

### 3.2 BidIncrementPolicy 생성

입찰 단위 계산 및 할증 로직을 담당하는 정책 클래스.

```java
public class BidIncrementPolicy {
    private static final int EXTENSION_SURCHARGE_INTERVAL = 3;
    private static final double SURCHARGE_RATE = 0.5;

    // 기본 입찰 단위 계산
    public static Long calculateBaseIncrement(Long currentPrice);

    // 할증 적용된 입찰 단위 계산
    public static Long calculateAdjustedIncrement(Long baseIncrement, int extensionCount);

    // 최종 입찰 단위 계산 (기본 + 할증)
    public static Long calculateFinalIncrement(Long currentPrice, int extensionCount);
}
```

### 3.3 AuctionExtensionPolicy 생성

연장 관련 규칙을 담당하는 정책 클래스.

```java
public class AuctionExtensionPolicy {
    private static final int EXTENSION_THRESHOLD_MINUTES = 5;
    private static final int EXTENSION_DURATION_MINUTES = 5;

    // 연장 구간 여부 확인
    public static boolean isInExtensionPeriod(LocalDateTime scheduledEndTime, LocalDateTime now);

    // 연장된 종료 시간 계산
    public static LocalDateTime calculateExtendedEndTime(LocalDateTime now);
}
```

### 3.4 Auction 도메인 수정

Policy 클래스에 위임하도록 변경.

```java
// Before
public static Long calculateBidIncrement(Long price) {
    if (price < 10_000L) return 500L;
    // ... 7개 분기
}

// After
public static Long calculateBidIncrement(Long price) {
    return BidIncrementPolicy.calculateBaseIncrement(price);
}
```

```java
// Before
public boolean isInExtensionPeriod() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime extensionThreshold = scheduledEndTime.minusMinutes(5);
    return now.isAfter(extensionThreshold) && now.isBefore(scheduledEndTime);
}

// After
public boolean isInExtensionPeriod() {
    return AuctionExtensionPolicy.isInExtensionPeriod(scheduledEndTime, LocalDateTime.now());
}
```

---

## 4. 파일 변경 요약

### 4.1 신규 파일 (3개)

| 파일 경로 | 역할 |
|----------|------|
| `auction/domain/policy/PriceBracket.java` | 가격 구간별 입찰 단위 Enum |
| `auction/domain/policy/BidIncrementPolicy.java` | 입찰 단위 + 할증 계산 정책 |
| `auction/domain/policy/AuctionExtensionPolicy.java` | 연장 구간/시간 계산 정책 |

### 4.2 수정 파일 (1개)

| 파일 경로 | 변경 내용 |
|----------|----------|
| `auction/domain/Auction.java` | Policy에 위임, if-else 체인 제거 |

---

## 5. 구조 다이어그램

```
auction/domain/
├── Auction.java
│   ├── calculateBidIncrement() ──→ BidIncrementPolicy
│   ├── getAdjustedBidIncrement() ──→ BidIncrementPolicy
│   ├── isInExtensionPeriod() ──→ AuctionExtensionPolicy
│   └── extend() ──→ AuctionExtensionPolicy
│
└── policy/
    ├── PriceBracket.java (Enum)
    ├── BidIncrementPolicy.java
    └── AuctionExtensionPolicy.java
```

---

## 6. 비즈니스 규칙 상수 정리

### 입찰 단위 (PriceBracket)

| 현재 가격 구간 | 입찰 단위 |
|--------------|----------|
| 1만 원 미만 | +500원 |
| 1만 ~ 5만 원 미만 | +1,000원 |
| 5만 ~ 10만 원 미만 | +3,000원 |
| 10만 ~ 50만 원 미만 | +5,000원 |
| 50만 ~ 100만 원 미만 | +10,000원 |
| 100만 원 이상 | +30,000원 |

### 할증 규칙 (BidIncrementPolicy)

| 상수 | 값 | 설명 |
|-----|---|------|
| EXTENSION_SURCHARGE_INTERVAL | 3 | N회마다 할증 적용 |
| SURCHARGE_RATE | 0.5 | 할증 비율 (50%) |

### 연장 규칙 (AuctionExtensionPolicy)

| 상수 | 값 | 설명 |
|-----|---|------|
| EXTENSION_THRESHOLD_MINUTES | 5 | 종료 N분 전부터 연장 구간 |
| EXTENSION_DURATION_MINUTES | 5 | 연장 시간 |

---

## 7. 검토 후 적용하지 않은 항목

분석 과정에서 발견되었으나 적용하지 않기로 결정한 항목들.

| 항목 | 사유 |
|-----|------|
| 이미지 기능 리팩토링 | 현재 모킹 상태로 유지. 실제 이미지 기능 구현 전까지 변경 불필요 |
| DTO Projection 적용 | Todo에 추가하여 별도 작업으로 분리. 현재 리팩토링 범위 외 |
| 동시성 처리 변경 | 분석 결과, 락 + 트랜잭션 구조가 이미 정확함. 변경 불필요 |
| `LocalDateTime.now()` 주입 | 테스트 용이성을 위해 고려했으나, 현재 테스트에서 문제 없음 |

---

## 8. 검증

```bash
# Cucumber 테스트 실행
./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"

# 전체 빌드
./gradlew build
```

모든 테스트 통과 확인 완료.
