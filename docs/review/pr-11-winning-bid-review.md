# PR #11 리뷰 분석

> **PR**: feat(winning): 낙찰, 노쇼 관련 기능 구현
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/11
> **리뷰어**: CodeRabbit, GitHub Copilot
> **분석일**: 2026-01-11

---

## CodeRabbit 리뷰

### 1. Upgrade Firebase Admin SDK to version 9.7.0
- **파일**: `build.gradle:58`
- **내용**: firebase-admin 9.2.0은 구버전이며, 보안 취약점 해결을 위해 최신 9.7.0으로 업그레이드 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 9.2.0 버전도 정상 동작함. 보안 취약점이 확인되면 업그레이드 필요하나, 메이저 기능 변경 없이 안정적으로 운영 중이라면 급하지 않음.
- **결정**: 거부
- **의견**: 추후 변경해도 문제 없음

---

### 2. Add composite index on (status, scheduledEndTime)
- **파일**: `AuctionRepository.java:39-45`
- **내용**: findClosingAuctions() 쿼리 성능 향상을 위해 (status, scheduledEndTime) 복합 인덱스 추가 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: `findClosingAuctions()` 쿼리가 매초 실행되므로 인덱스 추가 시 성능 향상 기대. 다만 현재 데이터량이 적다면 체감 효과 미미. 운영 데이터 증가 시 추가 권장.
- **결정**: 보류
- **의견**: 부하테스트를 통해 데이터 확보후 유의미한 성능 개선이 있을 경우 추가(docs/todo/Todo.md 문서에 작성할 것)

---

### 3. Add Javadoc to findTop2ByAuctionId method
- **파일**: `BidPersistenceAdapter.java:31-37`
- **내용**: findTop2ByAuctionId 메서드에 Javadoc 주석 추가 필요
- **판정**: ❌ 거부
- **AI 분석**: 메서드명 `findTop2ByAuctionId`가 충분히 명확하고, 인터페이스 `BidRepository`에 이미 문서화되어 있음. 구현체에 중복 문서화는 유지보수 부담만 증가.
- **결정**: 거부
- **의견**: 추가 주석을 작성할 필요는 없어보임

---

### 4. Configure ThreadPoolTaskScheduler for scheduling
- **파일**: `FairBidApplication.java`
- **내용**: 단일 스레드 기본값 사용 중, SchedulingConfigurer 구현하여 ThreadPoolTaskScheduler 설정 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 스케줄러가 2개뿐이고 단일 스레드로도 충분. 스케줄러가 늘어나거나 처리 시간이 길어지면 그때 ThreadPoolTaskScheduler 설정 권장.
- **결정**: 보류
- **의견**: 필요성이 생기면 그때 추가(docs/todo/Todo.md 문서에 작성할 것)

---

### 5. bidAmount parameter unused in sendTransferNotification
- **파일**: `FcmPushNotificationAdapter.java:30-36`
- **내용**: sendTransferNotification 메서드에서 bidAmount 매개변수가 메시지 본문에 사용되지 않음
- **판정**: ❌ 거부
- **AI 분석**: `sendTransferNotification`에서 bidAmount를 메시지에 포함하지 않는 것은 의도된 설계. 2순위 승계 알림에서는 금액보다 "권한 승계" 자체가 중요한 정보. 인터페이스 일관성을 위해 파라미터는 유지.
- **결정**: 거부
- **의견**: bidAmount를 전송할 필요가 없음

---

### 6. Close InputStream to prevent resource leak
- **파일**: `FirebaseConfig.java:40-54`
- **내용**: InputStream을 닫지 않아 리소스 누수 발생. try-with-resources 사용 필요
- **판정**: ✅ 수용
- **AI 분석**: `resource.getInputStream()` 호출 후 close하지 않아 리소스 누수 발생. try-with-resources로 감싸야 함.
```java
// Before
InputStream serviceAccount = resource.getInputStream();

// After
try (InputStream serviceAccount = resource.getInputStream()) {
    // ...
}
```
- **결정**: 수용
- **의견**: 리소스 누수는 해결해야함

---

### 7. Add distributed lock for PaymentTimeoutScheduler
- **파일**: `PaymentTimeoutScheduler.java`
- **내용**: 다중 인스턴스 환경에서 중복 처리 방지를 위해 분산 락 또는 비관적 락 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 단일 인스턴스 운영 시 불필요. 다중 인스턴스 배포 시 Redis 분산 락 또는 ShedLock 도입 필요. fixedDelay 변경이 우선.
- **결정**: 거부
- **의견**: 아직은 단일 인스턴스

---

### 8. Move broadcast outside transaction in AuctionClosingHelper
- **파일**: `AuctionClosingHelper.java:68-71`
- **내용**: 트랜잭션 내부에서 브로드캐스트 호출 시 롤백되어도 이벤트가 발송될 수 있음
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 `broadcastAuctionClosed()`가 try-catch로 감싸져 있어 예외 발생 시에도 트랜잭션에 영향 없음. 완벽하게 하려면 `@TransactionalEventListener` 사용하여 커밋 후 브로드캐스트 권장.
- **결정**: 수용
- **의견**: 경매 종료는 중요한 로직이기에 최대한 안전하게 하는 것이 중요

---

### 9. Use REQUIRES_NEW for individual winning processing
- **파일**: `NoShowProcessingService.java:42-62`
- **내용**: 단일 트랜잭션에서 여러 레코드 처리 시 하나 실패하면 전체 롤백 위험. REQUIRES_NEW로 분리 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 try-catch로 개별 오류 처리 중이나, 예외 발생 시 해당 winning의 상태 변경이 롤백되지 않음. `AuctionClosingHelper`처럼 별도 Helper 클래스에서 REQUIRES_NEW로 분리하면 더 안전.
- **결정**: 수용
- **의견**: 트랜잭션이 깨져서 전부 롤백되는 문제는 잡고싶음

---

### 10. Add null validation in Winning.createFirstRank()
- **파일**: `Winning.java:42-53`
- **내용**: auctionId, bidderId, bidAmount에 대한 null 매개변수 체크 추가 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: 방어적 프로그래밍 관점에서 null 체크 추가는 좋으나, 서비스 레이어(`AuctionClosingHelper`)에서 이미 유효한 Bid 객체로부터 값을 추출하므로 실제로 null이 들어올 가능성 낮음. 도메인 보호 차원에서 추가해도 무방.
- **결정**: 수용
- **의견**: 중복일 수 있지만 방어적으로 진행해도 괜찮을거 같음

---

### 11. Add null validation in Winning.createSecondRank()
- **파일**: `Winning.java:64-75`
- **내용**: createFirstRank와 동일하게 null 체크 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: 위와 동일.
- **결정**: 생략
- **의견**: 위에서 답변

---

### 12. Use Math.ceil() in isEligibleForAutoTransfer calculation
- **파일**: `Winning.java:95-101`
- **내용**: double을 long으로 캐스팅 시 절단 오류 발생 가능. Math.ceil() 사용하여 정확한 경계값 계산 필요
- **판정**: ❌ 거부
- **AI 분석**: 현재 `(long)` 캐스팅으로 truncate(버림)하는 것은 의도된 동작. 예: 1순위 100,000원일 때 threshold = 90,000원. 2순위 89,999원이면 탈락, 90,000원이면 승계. Math.ceil() 사용 시 threshold = 90,001원이 되어 2순위에게 불리해짐.
- **결정**: 거부
- **의견**: 현재 컨셉은 판매자 보호 판매자 입장에서는 소수점 단위의 미세한 수치에 의해 재경매하는 것 보다는 판매하는게 좋음

---

### 13. Avoid full entity reconstruction in WinningSteps
- **파일**: `WinningSteps.java:78-102`
- **내용**: 모든 필드를 일일이 복사하는 방식은 필드 추가 시 테스트가 깨짐. 특정 필드만 변경하는 방식 권장
- **판정**: ✅ 수용
- **AI 분석**: 모든 필드를 일일이 복사하는 방식은 필드 추가 시 테스트가 깨짐. 개선 방안:
  - `AuctionEntity`에 `updateScheduledEndTime(LocalDateTime)` 메서드 추가
  - 또는 테스트용 `@Modifying` 쿼리 사용
- **결정**: 수용
- **의견**: 테스트를 위해 AuctionEntity를 수정하는건 

---

### 14. Add serialVersionUID to WinningNotFoundException
- **파일**: `WinningNotFoundException.java`
- **내용**: 직렬화 호환성을 위해 serialVersionUID 추가 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: Exception 직렬화를 사용하지 않는다면 불필요. 분산 환경에서 예외를 직렬화하여 전송하는 경우에만 필요.
- **결정**: 거부
- **의견**: 필요성을 못느낌

---

## Copilot 리뷰

### 1. Use AuctionNotFoundException instead of RuntimeException
- **파일**: `AuctionClosingHelper.java:46-47`
- **내용**: 일반 RuntimeException 대신 AuctionNotFoundException 같은 도메인 예외 사용 필요
- **판정**: ✅ 수용
- **AI 분석**: `AuctionNotFoundException`이 이미 존재함. 일반 RuntimeException 대신 도메인 예외를 사용해야 예외 처리가 명확해짐.
```java
// Before
.orElseThrow(() -> new RuntimeException("경매를 찾을 수 없습니다: " + auctionId));

// After
.orElseThrow(() -> AuctionNotFoundException.withId(auctionId));
```
- **결정**: 수용
- **의견**: ai 동의

---

### 2. Use fixedDelay instead of fixedRate in AuctionClosingScheduler
- **파일**: `AuctionClosingScheduler.java:22-24`
- **내용**: fixedRate는 이전 작업 완료 여부와 관계없이 실행되어 동시 실행 가능성 있음. fixedDelay 사용 또는 분산 락 필요
- **판정**: ✅ 수용
- **AI 분석**: `fixedRate`는 이전 작업 완료 여부와 관계없이 일정 간격으로 실행. 작업이 1초 이상 걸리면 중복 실행됨. `fixedDelay`는 이전 작업 완료 후 대기하므로 안전.
```java
// Before
@Scheduled(fixedRate = 1000)

// After
@Scheduled(fixedDelay = 1000)
```
- **결정**: 수용
- **의견**: ai 동의

---

### 3. Add input validation in Winning.createSecondRank()
- **파일**: `Winning.java:64-75`
- **내용**: 입력 매개변수 null 체크 및 유효성 검증 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: CodeRabbit 리뷰와 동일. 서비스에서 검증된 값이 전달되므로 필수는 아님.
- **결정**: 생략
- **의견**: 위에서 답변

---

### 4. Consider immutability in Winning domain
- **파일**: `Winning.java`
- **내용**: 도메인 객체의 불변성 원칙 준수. 상태 변경 시 새 인스턴스 반환 고려
- **판정**: ❌ 거부
- **AI 분석**: 도메인 객체의 상태 변경(`markAsNoShow()`, `markAsPaid()` 등)은 비즈니스 로직의 일부. 매번 새 인스턴스를 반환하는 방식은 JPA 영속성 컨텍스트와 맞지 않고 코드 복잡도만 증가.
- **결정**: 거부
- **의견**: ai 동의 

---

### 5. Use fixedDelay instead of fixedRate in PaymentTimeoutScheduler
- **파일**: `PaymentTimeoutScheduler.java:22-24`
- **내용**: fixedRate 사용 시 중복 처리 가능성. fixedDelay로 변경 필요
- **판정**: ✅ 수용
- **AI 분석**: AuctionClosingScheduler와 동일한 이유.
- **결정**: 수용
- **의견**: ai 동의

---

### 6. Add input validation in Winning.createFirstRank()
- **파일**: `Winning.java:42-53`
- **내용**: auctionId, bidderId, bidAmount 유효성 검증 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: CodeRabbit 리뷰와 동일.
- **결정**: 생략
- **의견**: 위에서 답변

---

### 7. Validate current status before Auction.close()
- **파일**: `Auction.java:293-298`
- **내용**: 현재 상태가 BIDDING인지 확인 후 ENDED로 전환해야 함
- **판정**: ✅ 수용
- **AI 분석**: 상태 머신 관점에서 BIDDING 상태에서만 ENDED로 전환 가능해야 함. 이미 ENDED/FAILED/CANCELLED인 경매에 close() 호출 시 예외 발생해야 함.
```java
public void close(Long winnerId) {
    if (this.status != AuctionStatus.BIDDING) {
        throw new IllegalStateException("BIDDING 상태에서만 종료 가능합니다.");
    }
    // ...
}
```
- **결정**: 수용
- **의견**: ai 동의

---

### 8. Add null check for winnerId in Auction.close()
- **파일**: `Auction.java:293-298`
- **내용**: winnerId null 체크 필요. null이면 fail() 호출해야 함
- **판정**: ✅ 수용
- **AI 분석**: winnerId가 null이면 낙찰자 없는 종료인데, 이 경우 `fail()`을 호출해야 함. close()에 null이 전달되면 예외 발생해야 함.
- **결정**: 수용
- **의견**: ai 동의

---

### 9. Add null check in Auction.transferWinner()
- **파일**: `Auction.java:315-318`
- **내용**: newWinnerId null 체크 필수. 2순위 승계 시 반드시 유효한 ID 필요
- **판정**: ✅ 수용
- **AI 분석**: newWinnerId가 null이면 논리적 오류. 2순위 승계 시 반드시 유효한 bidderId가 있어야 함.
```java
public void transferWinner(Long newWinnerId) {
    if (newWinnerId == null) {
        throw new IllegalArgumentException("새로운 낙찰자 ID는 null일 수 없습니다.");
    }
    // ...
}
```
- **결정**: 수용
- **의견**: ai 동의

---

### 10. Validate current status before Auction.fail()
- **파일**: `Auction.java:304-308`
- **내용**: 현재 상태 유효성 검증 후 FAILED로 전환해야 함
- **판정**: ✅ 수용
- **AI 분석**: close()와 동일. BIDDING 또는 ENDED 상태에서만 FAILED로 전환 가능해야 함.
- **결정**: 수용
- **의견**: ENDED 상태에서만 FAILED로 전환 가능

---

### 11. Validate PENDING_PAYMENT status in transferToSecondRank()
- **파일**: `Winning.java:129-134`
- **내용**: rank == 2 검증 외에 PENDING_PAYMENT 상태 확인 추가 필요
- **판정**: ✅ 수용
- **AI 분석**: 현재 rank == 2 검증만 있음. 상태가 PENDING_PAYMENT인지도 확인해야 함. 이미 NO_SHOW나 PAID 상태인 Winning에 승계 처리하면 안 됨.
```java
public void transferToSecondRank() {
    if (this.rank != 2) {
        throw new IllegalStateException("2순위만 승계 가능합니다.");
    }
    if (this.status != WinningStatus.PENDING_PAYMENT) {
        throw new IllegalStateException("PENDING_PAYMENT 상태에서만 승계 가능합니다.");
    }
    // ...
}
```
- **결정**: 수용
- **의견**: ai 동의

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 11개 | 리소스 누수, 스케줄러 설정, 예외 처리, 상태 검증 등 |
| ⚠️ 선택적 | 10개 | Firebase 버전, DB 인덱스, null 검증, 트랜잭션 분리 등 |
| ❌ 거부 | 4개 | Javadoc 중복, bidAmount 미사용, 경계값 계산, 불변성 |
