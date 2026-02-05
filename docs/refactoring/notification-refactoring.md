# Notification 모듈 리팩토링

> 📅 작업일: 2026-01-XX
> 🎯 목표: FCM 어댑터 private 메서드 제거, 알림 메시지 생성을 전략 패턴으로 분리

---

## Before / After 요약

| 항목 | Before | After |
|------|--------|-------|
| FcmPushNotificationAdapter | 88줄 (private 35줄) | 36줄 |
| BidEventListener | 60줄 | 39줄 |
| 메시지 생성 | Adapter에서 하드코딩 | `NotificationType` Enum 전략 패턴 |
| FCM 전송 | Adapter에 혼재 | `FcmClient`로 분리 |

---

## 1. 문제점 (Before)

### 1.1 FcmPushNotificationAdapter - 메시지 생성 + 전송 혼재

```java
// 88줄 - 동일 패턴 3번 반복 + private 메서드 35줄
public class FcmPushNotificationAdapter {

    public void sendWinningNotification(Long userId, Long auctionId,
                                        String auctionTitle, Long bidAmount) {
        String title = "축하합니다! 낙찰되었습니다 🎉";  // 하드코딩
        String body = String.format("[%s] %,d원에 낙찰...", auctionTitle, bidAmount);
        sendPushNotification(userId, title, body, "WINNING", auctionId);
    }

    public void sendTransferNotification(...) {
        String title = "낙찰 기회가 생겼습니다!";  // 하드코딩
        String body = String.format("[%s] 2순위로...", auctionTitle);
        sendPushNotification(userId, title, body, "TRANSFER", auctionId);
    }

    // private 메서드 35줄
    private void sendPushNotification(Long userId, String title, String body,
                                      String type, Long auctionId) {
        if (FirebaseApp.getApps().isEmpty()) { ... }
        try {
            String fcmToken = null;
            if (fcmToken == null) { ... }
            Message message = Message.builder()...
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) { ... }
    }
}
```

### 1.2 BidEventListener - 필드 나열 변환

```java
// 30줄 메서드 - 8개 필드 직접 나열
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

## 2. 해결책 (After)

### 2.1 NotificationType Enum - 전략 패턴

각 알림 유형이 자신의 제목과 본문 생성 책임을 가짐:

```java
public enum NotificationType {

    WINNING {
        @Override
        public String getTitle() {
            return "축하합니다! 낙찰되었습니다 🎉";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] %,d원에 낙찰되었습니다.", auctionTitle, amount);
        }
    },

    TRANSFER {
        @Override
        public String getTitle() {
            return "낙찰 기회가 생겼습니다!";
        }

        @Override
        public String formatBody(String auctionTitle, Long amount) {
            return String.format("[%s] 2순위로 낙찰 권한이 승계되었습니다.", auctionTitle);
        }
    },

    FAILED { ... };

    public abstract String getTitle();
    public abstract String formatBody(String auctionTitle, Long amount);
}
```

### 2.2 FcmClient - FCM 전송만 담당

```java
@Component
public class FcmClient {

    public void send(Long userId, String title, String body,
                     NotificationType type, Long auctionId) {
        if (!isFirebaseInitialized()) {
            logMock(userId, type, title, body);
            return;
        }

        try {
            Message message = buildMessage(userId, title, body, type, auctionId);
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            log.error("FCM 전송 실패: userId={}", userId, e);
        }
    }
}
```

### 2.3 FcmPushNotificationAdapter - 조합만 담당

```java
// After - 36줄 (88줄 → 36줄, -59%)
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

    @Override
    public void sendTransferNotification(Long userId, Long auctionId,
                                         String auctionTitle) {
        NotificationType type = NotificationType.TRANSFER;
        fcmClient.send(userId, type.getTitle(),
                type.formatBody(auctionTitle, null), type, auctionId);
    }
    // ...
}
```

### 2.4 BidUpdateMessage.from(Event) - 오버로드 추가

```java
// After - 이벤트 객체 직접 변환
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

### 2.5 BidEventListener - 단순화

```java
// After - 10줄
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleBidPlacedEvent(BidPlacedEvent event) {
    log.debug("BidPlacedEvent 수신: auctionId={}", event.getAuctionId());

    try {
        auctionBroadcastPort.broadcastBidUpdate(BidUpdateMessage.from(event));
    } catch (Exception e) {
        log.error("BidUpdateMessage 브로드캐스트 실패", e);
    }
}
```

---

## 3. 개선 효과

| 측면 | 개선 내용 |
|------|----------|
| **코드량** | FcmAdapter 88줄 → 36줄 (-59%) |
| **SRP** | 메시지 생성(Enum) / 전송(Client) / 조합(Adapter) 분리 |
| **확장성** | 새 알림 유형 추가 시 Enum에 상수만 추가 |

---

## 4. 구조 다이어그램

### Before

```
FcmPushNotificationAdapter
    ├── sendWinningNotification()   → 메시지 생성 + sendPushNotification()
    ├── sendTransferNotification()  → 메시지 생성 + sendPushNotification()
    └── [private] sendPushNotification() (35줄)
```

### After

```
NotificationType (Enum)
    ├── WINNING.getTitle() / formatBody()
    ├── TRANSFER.getTitle() / formatBody()
    └── FAILED.getTitle() / formatBody()

FcmClient
    └── send() - FCM 전송만

FcmPushNotificationAdapter
    └── NotificationType + FcmClient 조합
```

---

## 5. 파일 변경 요약

### 신규 파일 (2개)

| 파일 | 역할 |
|------|------|
| `notification/domain/NotificationType.java` | 알림 유형별 메시지 생성 (전략 패턴) |
| `notification/adapter/out/fcm/FcmClient.java` | FCM 전송 책임 |

### 수정 파일 (3개)

| 파일 | 변경 |
|------|------|
| `FcmPushNotificationAdapter.java` | private 메서드 제거, 위임 구조 |
| `BidUpdateMessage.java` | `from(BidPlacedEvent)` 오버로드 추가 |
| `BidEventListener.java` | 메서드 단순화 |
