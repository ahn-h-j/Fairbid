# Common 모듈 예외 처리 리팩토링

> 📅 작업일: 2026-02-05
> 🎯 목표: 중복된 예외 핸들러 통합, 문자열 기반 검증을 타입 기반으로 개선

---

## Before / After 요약

| 항목 | Before | After |
|------|--------|-------|
| 예외 핸들러 | 6개 (동일 패턴 반복) | 1개 (통합) |
| Enum 검증 | 문자열 비교 (`contains`) | 타입 기반 (`targetType.isEnum()`) |
| HTTP 상태 코드 | Handler에서 결정 | 각 예외 클래스에서 결정 |

---

## 1. 문제점 (Before)

### 1.1 예외 핸들러 중복 - 동일 패턴 6번 반복

```java
// GlobalExceptionHandler.java - 이 패턴이 6번 반복됨
@ExceptionHandler(InvalidAuctionException.class)
public ResponseEntity<ApiResponse<Void>> handleInvalidAuctionException(
        InvalidAuctionException e) {
    log.warn("InvalidAuctionException: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)  // 여기만 다름
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
}

@ExceptionHandler(AuctionNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleAuctionNotFoundException(
        AuctionNotFoundException e) {
    log.warn("AuctionNotFoundException: {}", e.getMessage());
    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)  // 여기만 다름
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
}

// ... 4개 더 (BidTooLowException, InvalidBidException, ...)
```

### 1.2 Enum 검증 - 문자열 비교 (불안정)

```java
// 문자열 포함 여부로 Enum 타입 판별 - 리팩토링 시 오류 가능
if (causeMessage.contains("Category")) {
    String validValues = Arrays.stream(Category.values())...
} else if (causeMessage.contains("AuctionDuration")) {
    String validValues = Arrays.stream(AuctionDuration.values())...
} else if (causeMessage.contains("BidType")) {
    String validValues = Arrays.stream(BidType.values())...
}
```

---

## 2. 해결책 (After)

### 2.1 DomainException 베이스 클래스

모든 도메인 예외가 상속, HTTP 상태 코드를 각 예외가 결정:

```java
@Getter
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // 각 예외 클래스에서 오버라이드
    public abstract HttpStatus getStatus();
}
```

### 2.2 도메인 예외 클래스 - DomainException 상속

```java
// Before
public class InvalidAuctionException extends RuntimeException {
    private final String errorCode;
    // ...
}

// After
public class InvalidAuctionException extends DomainException {

    private InvalidAuctionException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;  // 예외가 자신의 상태 코드 결정
    }
}
```

### 2.3 GlobalExceptionHandler - 단일 핸들러로 통합

```java
// After - 6개 → 1개로 통합
@ExceptionHandler(DomainException.class)
public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException e) {
    log.warn("{}: {}", e.getClass().getSimpleName(), e.getMessage());
    return errorResponse(e.getStatus(), e.getErrorCode(), e.getMessage());
}

private ResponseEntity<ApiResponse<Void>> errorResponse(
        HttpStatus status, String errorCode, String message) {
    return ResponseEntity
            .status(status)
            .body(ApiResponse.error(errorCode, message));
}
```

### 2.4 Enum 검증 - 타입 기반으로 개선

```java
// After - 타입 정보 직접 사용 (안정적)
if (cause instanceof InvalidFormatException invalidFormat) {
    Class<?> targetType = invalidFormat.getTargetType();
    if (targetType != null && targetType.isEnum()) {
        String validValues = getEnumValidValues((Class<? extends Enum<?>>) targetType);
        message = "유효하지 않은 값입니다. 허용 값: " + validValues;
    }
}

private String getEnumValidValues(Class<? extends Enum<?>> enumClass) {
    return Arrays.stream(enumClass.getEnumConstants())
            .map(Enum::name)
            .collect(Collectors.joining(", "));
}
```

---

## 3. 개선 효과

| 측면 | 개선 내용 |
|------|----------|
| **DRY** | 중복 핸들러 6개 → 1개 |
| **OCP** | 새 예외 추가 시 Handler 수정 불필요 |
| **안정성** | 문자열 비교 → 타입 기반으로 리팩토링 내성 강화 |

---

## 4. 예외별 HTTP 상태 코드 매핑

| 예외 클래스 | HTTP 상태 | 설명 |
|------------|----------|------|
| `InvalidAuctionException` | 400 | 경매 검증 실패 |
| `AuctionNotFoundException` | 404 | 경매 없음 |
| `AuctionEndedException` | 400 | 종료된 경매 |
| `BidTooLowException` | 400 | 입찰가 부족 |
| `InvalidBidException` | 400 | 입찰 검증 실패 |
| `SelfBidNotAllowedException` | 403 | 본인 경매 입찰 |
| `WinningNotFoundException` | 404 | 낙찰 정보 없음 |

---

## 5. 확장 예시

새로운 도메인 예외 추가 시:

```java
// 1. DomainException 상속한 예외 클래스만 생성
public class PaymentFailedException extends DomainException {

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.PAYMENT_REQUIRED;  // 402
    }

    public static PaymentFailedException insufficientBalance() {
        return new PaymentFailedException("INSUFFICIENT_BALANCE", "잔액이 부족합니다.");
    }
}

// 2. GlobalExceptionHandler 수정 불필요 - 자동으로 처리됨 ✓
```

---

## 6. 파일 변경 요약

### 신규 파일 (1개)

| 파일 | 역할 |
|------|------|
| `common/exception/DomainException.java` | 도메인 예외 베이스 클래스 |

### 수정 파일 (8개)

| 파일 | 변경 |
|------|------|
| `InvalidAuctionException.java` | DomainException 상속, `getStatus()` 구현 |
| `AuctionNotFoundException.java` | DomainException 상속, `getStatus()` 구현 |
| `AuctionEndedException.java` | DomainException 상속, `getStatus()` 구현 |
| `BidTooLowException.java` | DomainException 상속, `getStatus()` 구현 |
| `InvalidBidException.java` | DomainException 상속, `getStatus()` 구현 |
| `SelfBidNotAllowedException.java` | DomainException 상속, `getStatus()` 구현 |
| `WinningNotFoundException.java` | DomainException 상속, `getStatus()` 구현 |
| `GlobalExceptionHandler.java` | 단일 핸들러 통합, 타입 기반 검증 |
