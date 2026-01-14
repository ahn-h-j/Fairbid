# PR #21 리뷰 분석

> **PR**: refactor: 클린 아키텍처 원칙에 따른 Service 리팩토링
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/21
> **리뷰어**: CodeRabbit, GitHub Copilot
> **분석일**: 2026-01-14

---

## CodeRabbit 리뷰

### 1. 마크다운 테이블 빈 줄 누락 (MD058)
- **파일**: `.github/ISSUE_TEMPLATE/refactor.md:20-35`
- **내용**: 테이블 주변에 빈 줄이 누락됨. 마크다운 린트 규칙(MD058) 위반
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 마크다운 린터 경고로 기능에 영향 없음. 표준 준수 시 수정 권장
- **결정**: 거부
- **의견**: 문서화에 강하게 표준 준수할 생각 없음

---

### 2. BidIncrementPolicy 상세 주석 추가 필요
- **파일**: `auction/domain/policy/BidIncrementPolicy.java:39-42`
- **내용**: `calculateAdjustedIncrement()` 메서드에 정수 나눗셈 이유, 할증율 공식, Math.round 사용 이유 주석 추가 권장
- **판정**: ✅ 수용
- **AI 분석**: CLAUDE.md에서 "주석은 상세하게 작성해라" 규칙 명시. 복잡한 계산 로직이므로 주석 추가가 적절
- **결정**: 거부
- **의견**: 이미 작성된 주석과 코드로 파악 가능

---

### 3. clean-architecture-refactoring.md 코드 예시 불일치
- **파일**: `docs/refactoring/clean-architecture-refactoring.md:57-68`
- **내용**: 문서의 `Bid.determineBidAmount()` 예시가 if-else 로직을 보여주나, 실제 구현은 전략 패턴(`bidType.calculateAmount()`) 사용
- **판정**: ✅ 수용
- **AI 분석**: 문서와 실제 코드 불일치. 문서가 최종 구현 상태를 반영해야 정확함
- **결정**: 거부
- **의견**: bid 리팩토링 이전에 작업했던 것이기에 맞음

---

### 4. 마크다운 코드 블록 언어 지정 누락 (MD040)
- **파일**: 다수의 문서 파일 (bid-strategy-pattern-refactoring.md, auction-policy-refactoring.md, common-exception-refactoring.md, notification-refactoring.md, refactoring-work-guide.md)
- **내용**: 구조 다이어그램 코드 블록에 언어(text/plaintext) 지정 누락
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 린터 경고. 렌더링에 영향 미미. `text` 또는 `plaintext` 지정으로 해결 가능
- **결정**: 거부
- **의견**: 문서화에 과하게 신경쓸 생각 없음

---

### 5. NotificationType - 미사용 amount 파라미터 주의
- **파일**: `notification/domain/NotificationType.java:28, 42`
- **내용**: TRANSFER, FAILED 타입에서 `amount` 파라미터 미사용. Javadoc에 명시되어 있으나 혼란 가능
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 `FcmPushNotificationAdapter`에서 FAILED는 null 전달로 처리. 인터페이스 일관성 vs 명확성 트레이드오프
- **결정**: 거부
- **의견**: 인테페이스 일관성을 우선

---

### 6. BidUpdateMessage null 체크 권장
- **파일**: `notification/dto/BidUpdateMessage.java:36-47`
- **내용**: `from(BidPlacedEvent event)` 메서드에서 event null 시 NPE 발생. 방어적 null 체크 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 호출부(BidEventListener)에서 null 전달 가능성 낮음. 도메인 내부 호출이므로 과도한 방어 코드 불필요할 수 있음
- **결정**: 수용
- **의견**: null체크와 같은 Runtime 방어는 중요한거 같음

---

### 7. BidEventPublisher 모듈 간 의존성
- **파일**: `bid/application/port/out/BidEventPublisher.java`
- **내용**: bid 모듈이 auction.domain.Auction을 직접 참조. DTO 사용 시 모듈 결합도 감소 가능
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 모놀리식 구조에서는 문제없음. MSA 분리 시점에 리팩토링 고려
- **결정**: 보류
- **의견**: MSA 분리를 고려하게 되면 그때 생각 todo/Todo.md에 작성

---

### 8. Auction.isInExtensionPeriod() 테스트 용이성
- **파일**: `auction/domain/Auction.java:207-222`
- **내용**: `LocalDateTime.now()` 직접 호출로 단위 테스트 시간 제어 어려움. 파라미터화 또는 Clock 주입 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 테스트 용이성 개선 제안. extend() 메서드는 이미 now 변수 재사용 패턴 적용됨
- **결정**:
- **의견**:

---

### 9. FcmClient - FCM 토큰 조회 미구현 TODO
- **파일**: `notification/adapter/out/fcm/FcmClient.java:50-55`
- **내용**: `getFcmToken()`이 항상 null 반환. 실제 FCM 전송 불가. 이슈 트래킹 필요
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 기존 TODO 주석으로 인지됨. User 도메인 구현 후 작업 필요. 현재 리팩토링 범위 외
- **결정**: 보류
- **의견**: User가 모킹이기에 todo/Todo.md 에 작성

---

### 10. FcmClient 에러 처리 개선 권장
- **파일**: `notification/adapter/out/fcm/FcmClient.java:57-75`
- **내용**: `Exception` 대신 `FirebaseMessagingException` 구체적 처리 권장. auctionId 로그 포함 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 에러 처리 세분화로 FCM 특정 에러(토큰 만료 등) 대응 가능. 현재 mock 상태이므로 실제 구현 시 적용
- **결정**: 보류
- **의견**: User가 모킹이기에 todo/Todo.md 에 작성

---

### 11. AuctionClosingProcessor 아키텍처 검토
- **파일**: `winning/domain/service/AuctionClosingProcessor.java:1-22`
- **내용**: domain.service 패키지에 위치하면서 application.port (PushNotificationPort)에 의존. 클린 아키텍처 위반 가능성
- **판정**: ⚠️ 선택적
- **AI 분석**: 두 가지 선택지: (1) application.service로 이동 또는 (2) 알림 발송 책임을 Helper로 이동. 현재 구조도 동작에 문제없음
- **결정**: 수용
- **의견**: 아키텍처 위반인듯 서비스로 옮기는 선택지 수행

---

### 12. PriceBracket null 가격 방어 코드
- **파일**: `auction/domain/policy/PriceBracket.java:48-54`
- **내용**: `getIncrementForPrice(Long price)`에서 null/음수 가격 시 NPE 발생. 방어적 검증 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 호출부에서 null 검증 수행한다면 불필요. 도메인 정책 클래스로서 방어적 처리 시 더 안전
- **결정**: 거부
- **의견**: Request단에서 검증을 수행하고 있음

---

### 13. AuctionClosingHelper 유찰 처리 완료 로그
- **파일**: `winning/application/service/AuctionClosingHelper.java:51-56`
- **내용**: 유찰 처리 시 완료 로그 없음. 낙찰 처리에만 완료 로그 존재. 운영 모니터링 일관성 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 로그 일관성 개선 제안. 운영 환경에서 유찰 건 추적에 도움
- **결정**: 수용
- **의견**: 성공여부는 로그로 남기는것이 좋아보임

---

### 14. GlobalExceptionHandler ENUM_DESCRIPTIONS 확장성
- **파일**: `common/exception/GlobalExceptionHandler.java:35-39`
- **내용**: 새 Enum 타입 추가 시 맵 업데이트 누락 가능. 주석이나 문서에 명시 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: getOrDefault로 기본값 제공하여 런타임 오류 방지됨. 문서화로 유지보수성 향상 가능
- **결정**: 수용
- **의견**: 추후 개발할때 일관성 필요

---

### 15. NoShowProcessor TODO 추적
- **파일**: `winning/domain/service/NoShowProcessor.java:41-42`
- **내용**: User 도메인 구현 후 경고 부여 로직 추가 필요. 이슈 생성 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 기존 TODO 주석으로 인지됨. User 모듈 개발 시점에 작업
- **결정**: 보류
- **의견**: User가 모킹이기에 todo/Todo.md 에 작성

---

## Copilot 리뷰

### 16. NotificationType TRANSFER - 미사용 amount 파라미터
- **파일**: `notification/domain/NotificationType.java:28`
- **내용**: TRANSFER의 `formatBody`에서 amount 미사용. amount 포함하거나 nullable 허용 구조 제안
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: CodeRabbit #5와 동일 이슈. 인터페이스 일관성 유지 vs 타입별 파라미터 분리 트레이드오프
- **결정**: 패스
- **의견**: 위에서 답변

---

### 17. NotificationType FAILED - 미사용 amount 파라미터
- **파일**: `notification/domain/NotificationType.java:42`
- **내용**: FAILED에서 amount 미사용. 문서화 또는 구조 개선 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: CodeRabbit #5와 동일 이슈
- **결정**: 패스
- **의견**: 위에서 답변

---

### 18. BidIncrementPolicy - final 클래스 권장
- **파일**: `auction/domain/policy/BidIncrementPolicy.java:21`
- **내용**: private 생성자만 있어도 final 추가하면 상속 방지 의도가 더 명확함
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 유틸리티 클래스 best practice. Lombok @UtilityClass 사용 시 자동 적용됨. 현재도 동작 문제없음
- **결정**: 수용
- **의견**: 상속 방지 의도를 더 드러내는 것이 좋아보임

---

### 19. AuctionExtensionPolicy - final 클래스 권장
- **파일**: `auction/domain/policy/AuctionExtensionPolicy.java:23`
- **내용**: BidIncrementPolicy와 동일. final 추가 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: Copilot #18과 동일 이유
- **결정**: 수용
- **의견**: 위와 동일

---

### 20. GlobalExceptionHandler ENUM_DESCRIPTIONS 문서화
- **파일**: `common/exception/GlobalExceptionHandler.java:39`
- **내용**: 어떤 Enum 타입이 지원되는지 명확하지 않음. 주석 추가 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: CodeRabbit #14와 동일 이슈
- **결정**: 패스
- **의견**: 위에서 답변

---

### 21. PriceBracket 네이밍 명확화
- **파일**: `auction/domain/policy/PriceBracket.java:19`
- **내용**: UNDER_10K가 "10,000 미만"을 의미하는지 명확하지 않음. BELOW_10K 또는 문서화 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 로직(price < upperBound)이 정확히 "미만"을 처리함. 네이밍 개선은 가독성 향상에 기여
- **결정**: 수용
- **의견**: ai 동의 

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 2개 | #2 BidIncrementPolicy 주석, #3 문서 코드 예시 수정 |
| ⚠️ 선택적 | 19개 | #1,4-21 (대부분 Nitpick) |
| ❌ 거부 | 0개 | - |

---

## 수용 예정 항목 상세

### #2 BidIncrementPolicy 주석 추가
```java
/**
 * 연장 횟수에 따른 할증된 입찰 단위 계산
 *
 * @param baseIncrement 기본 입찰 단위 (PriceBracket에서 계산된 값)
 * @param extensionCount 연장 횟수
 * @return 할증 적용된 입찰 단위
 *
 * 계산 로직:
 * 1. 정수 나눗셈으로 할증 단계 산출 (3회마다 1단계)
 *    - surchargeMultiplier = extensionCount / EXTENSION_SURCHARGE_INTERVAL
 * 2. 할증율 계산: 1.0 + (할증 단계 × 50%)
 *    - 0단계: 100%, 1단계: 150%, 2단계: 200%
 * 3. Math.round로 반올림하여 Long 반환 (truncation 방지)
 */
```

### #3 clean-architecture-refactoring.md 수정
변경 전:
```java
public static Long determineBidAmount(BidType bidType, Long requestedAmount, Auction auction) {
    if (bidType == BidType.ONE_TOUCH) {
        return auction.getMinBidAmount();
    }
    // ...
}
```

변경 후:
```java
public static Long determineBidAmount(BidType bidType, Long requestedAmount, Auction auction) {
    return bidType.calculateAmount(requestedAmount, auction);
}
```
