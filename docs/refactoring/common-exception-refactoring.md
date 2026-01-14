# Common 모듈 예외 처리 리팩토링

## 1. 개요

도메인 예외 클래스들의 중복을 제거하고, GlobalExceptionHandler를 개선하여 유지보수성과 안정성을 향상시킴.

---

## 2. 리팩토링 전 문제점

### 2.1 도메인 예외 중복 (GlobalExceptionHandler)

**문제점**:
- 5개 도메인 예외가 거의 동일한 패턴으로 처리됨
- 새로운 도메인 예외 추가 시 핸들러 메서드도 추가해야 함

```java
// Before - 이 패턴이 5번 반복
@ExceptionHandler(InvalidAuctionException.class)
public ResponseEntity<ApiResponse<Void>> handleInvalidAuctionException(InvalidAuctionException e) {
    log.warn("InvalidAuctionException: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
}

@ExceptionHandler(AuctionNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleAuctionNotFoundException(AuctionNotFoundException e) {
    log.warn("AuctionNotFoundException: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)  // 여기만 다름
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
}
// ... 3개 더
```

### 2.2 문자열 기반 Enum 검증 (불안정)

**문제점**:
- `causeMessage.contains("Category")` 방식으로 enum 타입 판별
- 문자열 비교에 의존하여 리팩토링 시 오류 발생 가능

```java
// Before - 문자열 기반 판별 (불안정)
if (causeMessage.contains("Category")) {
    String validValues = Arrays.stream(Category.values())...
} else if (causeMessage.contains("AuctionDuration")) {
    String validValues = Arrays.stream(AuctionDuration.values())...
} else if (causeMessage.contains("BidType")) {
    String validValues = Arrays.stream(BidType.values())...
}
```

### 2.3 응답 생성 코드 반복

**문제점**:
- `ResponseEntity.status().body()` 패턴이 모든 핸들러에서 반복
- 응답 형식 변경 시 모든 메서드 수정 필요

---

## 3. 리팩토링 내용

### 3.1 DomainException 베이스 클래스 생성

모든 도메인 예외가 상속받는 추상 클래스 생성.

```java
// common/exception/DomainException.java
@Getter
public abstract class DomainException extends RuntimeException {
    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * HTTP 상태 코드를 반환한다
     * 각 예외 클래스에서 오버라이드하여 적절한 상태 코드 지정
     */
    public abstract HttpStatus getStatus();
}
```

### 3.2 기존 예외 클래스 수정

각 도메인 예외가 DomainException을 상속하고 `getStatus()` 구현.

```java
// Before
public class InvalidAuctionException extends RuntimeException {
    private final String errorCode;

    private InvalidAuctionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

// After
public class InvalidAuctionException extends DomainException {

    private InvalidAuctionException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
```

### 3.3 GlobalExceptionHandler 단일 핸들러로 통합

5개의 개별 핸들러를 1개로 통합.

```java
// After - 단일 핸들러
@ExceptionHandler(DomainException.class)
public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException e) {
    log.warn("{}: {}", e.getClass().getSimpleName(), e.getMessage());
    return errorResponse(e.getStatus(), e.getErrorCode(), e.getMessage());
}
```

### 3.4 문자열 기반 → 타입 기반 Enum 검증

`InvalidFormatException`에서 타입 정보를 직접 추출.

```java
// Before - 문자열 기반 (불안정)
if (causeMessage.contains("Category")) { ... }

// After - 타입 기반 (안정)
if (cause instanceof InvalidFormatException invalidFormat) {
    Class<?> targetType = invalidFormat.getTargetType();
    if (targetType != null && targetType.isEnum()) {
        String description = ENUM_DESCRIPTIONS.getOrDefault(enumType, "값");
        String validValues = getEnumValidValues(enumType);
        message = "유효하지 않은 " + description + "입니다. 허용 값: " + validValues;
    }
}
```

### 3.5 유틸리티 메서드 추출

Enum 값 조회와 응답 생성 로직을 메서드로 분리.

```java
// Enum 값 조회 유틸리티
private String getEnumValidValues(Class<? extends Enum<?>> enumClass) {
    return Arrays.stream(enumClass.getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.joining(", "));
}

// 응답 생성 헬퍼
private ResponseEntity<ApiResponse<Void>> errorResponse(
        HttpStatus status, String errorCode, String message) {
    return ResponseEntity
            .status(status)
            .body(ApiResponse.error(errorCode, message));
}
```

---

## 4. 파일 변경 요약

### 4.1 신규 파일 (1개)

| 파일 경로 | 역할 |
|----------|------|
| `common/exception/DomainException.java` | 도메인 예외 베이스 클래스 |

### 4.2 수정 파일 (8개)

| 파일 경로 | 변경 내용 |
|----------|----------|
| `auction/domain/exception/InvalidAuctionException.java` | DomainException 상속, `getStatus()` 구현 |
| `auction/domain/exception/AuctionNotFoundException.java` | DomainException 상속, `getStatus()` 구현 |
| `bid/domain/exception/AuctionEndedException.java` | DomainException 상속, `getStatus()` 구현 |
| `bid/domain/exception/BidTooLowException.java` | DomainException 상속, `getStatus()` 구현 |
| `bid/domain/exception/InvalidBidException.java` | DomainException 상속, `getStatus()` 구현 |
| `bid/domain/exception/SelfBidNotAllowedException.java` | DomainException 상속, `getStatus()` 구현 |
| `winning/domain/exception/WinningNotFoundException.java` | DomainException 상속, `getStatus()` 구현, errorCode 추가 |
| `common/exception/GlobalExceptionHandler.java` | 단일 핸들러 통합, 유틸리티 메서드 추출 |

---

## 5. 예외별 HTTP 상태 코드 매핑

| 예외 클래스 | HTTP 상태 코드 | 설명 |
|------------|---------------|------|
| `InvalidAuctionException` | 400 BAD_REQUEST | 경매 검증 실패 |
| `AuctionNotFoundException` | 404 NOT_FOUND | 경매 없음 |
| `AuctionEndedException` | 400 BAD_REQUEST | 종료된 경매 |
| `BidTooLowException` | 400 BAD_REQUEST | 입찰가 부족 |
| `InvalidBidException` | 400 BAD_REQUEST | 입찰 검증 실패 |
| `SelfBidNotAllowedException` | 403 FORBIDDEN | 본인 경매 입찰 |
| `WinningNotFoundException` | 404 NOT_FOUND | 낙찰 정보 없음 |

---

## 6. 구조 다이어그램

### 리팩토링 전

```
GlobalExceptionHandler
    ├── handleInvalidAuctionException()     → BAD_REQUEST
    ├── handleAuctionNotFoundException()    → NOT_FOUND
    ├── handleAuctionEndedException()       → BAD_REQUEST
    ├── handleBidTooLowException()          → BAD_REQUEST
    ├── handleInvalidBidException()         → BAD_REQUEST
    └── handleSelfBidNotAllowedException()  → FORBIDDEN
```

### 리팩토링 후

```
DomainException (abstract)
    ├── getErrorCode()
    └── getStatus() ← 각 예외가 구현
            │
            ├── InvalidAuctionException    → BAD_REQUEST
            ├── AuctionNotFoundException   → NOT_FOUND
            ├── AuctionEndedException      → BAD_REQUEST
            ├── BidTooLowException         → BAD_REQUEST
            ├── InvalidBidException        → BAD_REQUEST
            ├── SelfBidNotAllowedException → FORBIDDEN
            └── WinningNotFoundException   → NOT_FOUND

GlobalExceptionHandler
    └── handleDomainException(DomainException e)
            → e.getStatus() 사용
```

---

## 7. 확장성

새로운 도메인 예외 추가 시:

```java
// 1. DomainException 상속한 예외 클래스 생성
public class PaymentFailedException extends DomainException {

    private PaymentFailedException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.PAYMENT_REQUIRED;  // 402
    }

    public static PaymentFailedException insufficientBalance() {
        return new PaymentFailedException("INSUFFICIENT_BALANCE", "잔액이 부족합니다.");
    }
}

// 2. GlobalExceptionHandler 수정 불필요 - 자동으로 처리됨
```

---

## 8. 검증

```bash
# Cucumber 테스트 실행
./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"

# 전체 빌드
./gradlew build
```

모든 테스트 통과 확인 완료.
