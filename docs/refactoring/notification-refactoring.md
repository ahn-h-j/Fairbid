# Notification 모듈 리팩토링

## 1. 개요

Notification 모듈의 private 메서드를 제거하고, 전략 패턴을 적용하여 책임을 분리함.

---

## 2. 리팩토링 전 문제점

### 2.1 FcmPushNotificationAdapter (88줄)

**문제점**:
- private 메서드 `sendPushNotification()` 35줄
- 3개 public 메서드가 동일한 패턴 반복
- 메시지 생성과 FCM 전송이 한 클래스에 혼재

```java
// Before - 동일 패턴 3번 반복
public void sendWinningNotification(...) {
    String title = "축하합니다! 낙찰되었습니다 🎉";
    String body = String.format("[%s] %,d원에 낙찰...", auctionTitle, bidAmount);
    sendPushNotification(userId, title, body, "WINNING", auctionId);
}

public void sendTransferNotification(...) {
    String title = "낙찰 기회가 생겼습니다!";
    String body = String.format("[%s] 2순위로...", auctionTitle);
    sendPushNotification(userId, title, body, "TRANSFER", auctionId);
}

// private 메서드 35줄
private void sendPushNotification(...) {
    if (FirebaseApp.getApps().isEmpty()) { ... }
    try {
        String fcmToken = null;
        if (fcmToken == null) { ... }
        Message message = Message.builder()...
        FirebaseMessaging.getInstance().send(message);
    } catch (Exception e) { ... }
}
```

### 2.2 BidEventListener (30줄 메서드)

**문제점**:
- `handleBidPlacedEvent()` 메서드가 30줄
- 이벤트 → 메시지 변환 시 8개 필드를 직접 나열

```java
// Before
BidUpdateMessage message = BidUpdateMessage.from(
    event.getAuctionId(),
    event.getCurrentPrice(),
    event.getScheduledEndTime(),
    event.isExtended(),
    event.getNextMinBidPrice(),
    event.getBidIncrement(),
    event.getTotalBidCount(),
    event.getOccurredAt()
);
```

---

## 3. 리팩토링 내용

### 3.1 NotificationType enum (전략 패턴)

각 알림 유형이 자신의 제목과 본문 생성 책임을 가짐.

```java
public enum NotificationType {

    WINNING {
        @Override
        public String getTitle() {
            return "축하합니다! 낙찰되었습니다 🎉";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] %,d원에 낙찰되었습니다. 3시간 내에 결제해주세요.",
                    auctionTitle, amount);
        }
    },

    TRANSFER { ... },
    FAILED { ... };

    public abstract String getTitle();
    public abstract String formatBody(String auctionTitle, Long amount);
}
```

### 3.2 FcmClient (FCM 전송 책임 분리)

FCM 전송 로직만 담당하는 클래스 생성.

```java
@Component
public class FcmClient {

    public void send(Long userId, String title, String body,
                     NotificationType type, Long auctionId) {
        if (!isFirebaseInitialized()) {
            logMock(userId, type, title, body);
            return;
        }
        // FCM 전송 로직
    }
}
```

### 3.3 FcmPushNotificationAdapter 단순화

메시지 생성은 NotificationType에, 전송은 FcmClient에 위임.

```java
// After (36줄)
@Component
@RequiredArgsConstructor
public class FcmPushNotificationAdapter implements PushNotificationPort {

    private final FcmClient fcmClient;

    @Override
    public void sendWinningNotification(Long userId, Long auctionId,
                                        String auctionTitle, Long bidAmount) {
        NotificationType type = NotificationType.WINNING;
        fcmClient.send(userId, type.getTitle(),
                type.formatBody(auctionTitle, bidAmount), type, auctionId);
    }
    // ... 나머지 메서드도 동일 패턴
}
```

### 3.4 BidUpdateMessage.from(BidPlacedEvent) 추가

이벤트 객체를 직접 받아 변환하는 오버로드 추가.

```java
// After
public static BidUpdateMessage from(BidPlacedEvent event) {
    return new BidUpdateMessage(
            event.getAuctionId(),
            event.getCurrentPrice(),
            event.getScheduledEndTime(),
            event.isExtended(),
            event.getNextMinBidPrice(),
            event.getBidIncrement(),
            event.getTotalBidCount(),
            event.getOccurredAt()
    );
}
```

### 3.5 BidEventListener 단순화

```java
// After (10줄)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleBidPlacedEvent(BidPlacedEvent event) {
    log.debug("BidPlacedEvent 수신: auctionId={}, currentPrice={}",
            event.getAuctionId(), event.getCurrentPrice());

    try {
        auctionBroadcastPort.broadcastBidUpdate(BidUpdateMessage.from(event));
    } catch (Exception e) {
        log.error("BidUpdateMessage 브로드캐스트 실패: auctionId={}",
                event.getAuctionId(), e);
    }
}
```

---

## 4. 파일 변경 요약

### 4.1 신규 파일 (2개)

| 파일 경로 | 역할 |
|----------|------|
| `notification/domain/NotificationType.java` | 알림 유형별 메시지 생성 (전략 패턴) |
| `notification/adapter/out/fcm/FcmClient.java` | FCM 전송 책임 |

### 4.2 수정 파일 (3개)

| 파일 경로 | 변경 내용 |
|----------|----------|
| `notification/adapter/out/fcm/FcmPushNotificationAdapter.java` | private 메서드 제거, 위임 구조로 변경 |
| `notification/dto/BidUpdateMessage.java` | `from(BidPlacedEvent)` 오버로드 추가 |
| `notification/adapter/in/event/BidEventListener.java` | 메서드 단순화, 로그 레벨 조정 |

---

## 5. 구조 다이어그램

### 리팩토링 전

```
FcmPushNotificationAdapter
    ├── sendWinningNotification()   → title, body 생성 + sendPushNotification()
    ├── sendTransferNotification()  → title, body 생성 + sendPushNotification()
    ├── sendFailedAuctionNotification() → title, body 생성 + sendPushNotification()
    └── [private] sendPushNotification() (35줄)
```

### 리팩토링 후

```
NotificationType (enum, 전략 패턴)
    ├── WINNING.getTitle() / formatBody()
    ├── TRANSFER.getTitle() / formatBody()
    └── FAILED.getTitle() / formatBody()

FcmClient
    └── send() - FCM 전송만 담당

FcmPushNotificationAdapter
    ├── sendWinningNotification()   → NotificationType + FcmClient 조합
    ├── sendTransferNotification()  → NotificationType + FcmClient 조합
    └── sendFailedAuctionNotification() → NotificationType + FcmClient 조합
```

---

## 6. 코드 라인 수 비교

| 파일 | Before | After | 감소 |
|-----|--------|-------|-----|
| FcmPushNotificationAdapter | 88줄 | 36줄 | -52줄 |
| BidEventListener | 60줄 | 39줄 | -21줄 |

---

## 7. 검토 후 적용하지 않은 항목

| 항목 | 사유 |
|-----|------|
| FCM 토큰 조회 구현 | User 도메인 모킹 상태. 별도 작업 필요 |
| WebSocket CORS 설정 변경 | 인프라 설정으로 현재 리팩토링 범위 외 |

---

## 8. 검증

```bash
# Cucumber 테스트 실행
./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"

# 전체 빌드
./gradlew build
```

모든 테스트 통과 확인 완료.
