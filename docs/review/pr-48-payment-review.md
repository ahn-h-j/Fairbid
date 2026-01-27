# PR #48 리뷰 분석

> **PR**: feat(trade): Mock 결제 기능 및 노쇼 경고 시스템 구현
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/48
> **리뷰어**: CodeRabbit, Copilot
> **분석일**: 2026-01-27

---

## CodeRabbit 리뷰

### 1. PaymentService 결제 결과 검증 누락
- **파일**: `PaymentService.java:76-81`
- **내용**: `paymentGatewayPort.processPayment()` 반환값(`PaymentResult`)을 검증하지 않음. 실제 PG 연동 시 실패해도 PAID 상태로 변경될 위험
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 Mock 구현은 항상 성공 반환. 실제 PG 연동 시 반드시 수정 필요하나, 현재 스코프에서는 Mock만 사용
- **결정**: 거부
- **의견**: payment는 나중에 수정할 예정

---

### 2. PaymentCompletePage transaction.id → transactionId
- **파일**: `PaymentCompletePage.jsx:82-85`
- **내용**: 백엔드 응답 필드명은 `transactionId`인데 `transaction.id`로 접근
- **판정**: ✅ 수용
- **AI 분석**: 응답 DTO 구조와 불일치. 실제 오류 발생 가능
- **결정**: 수용
- **의견**: ai 동의

---

### 3. Transaction.transferToSecondRank 상태 가드 누락
- **파일**: `Transaction.java:111-122`
- **내용**: Javadoc에는 "취소 상태에서만 승계"라고 되어 있으나 실제로 어떤 상태에서도 승계 가능
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 호출 흐름상 NoShowProcessor에서만 호출되어 문제없으나, 도메인 방어적 프로그래밍 관점에서 유효
- **결정**: 수용
- **의견**: 혹시 모를 오용 대비

---

### 4. TransactionController 권한 체크 누락
- **파일**: `TransactionController.java:56-90`
- **내용**: `getTransaction`, `getTransactionByAuctionId` API에서 buyer/seller 권한 검증 없이 누구나 조회 가능
- **판정**: ⚠️ 선택적
- **AI 분석**: 민감 정보 노출 우려. 다만 현재 API는 인증된 사용자만 접근 가능하고, 실제 데이터 유출 위험은 낮음
- **결정**: 보류
- **의견**: todo/Todo.md에 기록

---

### 5. JacksonConfig LocalDateTime과 setTimeZone
- **파일**: `JacksonConfig.java:31-34`
- **내용**: `LocalDateTime`은 타임존 정보가 없어 `setTimeZone(UTC)` 설정이 무시됨. `Instant` 또는 커스텀 Serializer 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: 프론트엔드에서 `parseServerDate()` + 'Z' 추가로 UTC 해석 중. 현재 동작에 문제없으나 장기적으로 `Instant` 전환 권장
- **결정**: 수용
- **의견**: ai 의견 동의

---

### 6. TestNoShowHelper REQUIRES_NEW propagation
- **파일**: `TestNoShowHelper.java:34-35`
- **내용**: `@Transactional`의 기본 propagation은 REQUIRED. 별도 트랜잭션으로 즉시 커밋하려면 `REQUIRES_NEW` 필요
- **판정**: ✅ 수용
- **AI 분석**: 테스트 헬퍼의 의도대로 동작하려면 필요한 수정
- **결정**: 수용
- **의견**: ai 동의

---

### 7. Transaction.isReminderTarget 마감 이후 조건 누락
- **파일**: `Transaction.java:151-163`
- **내용**: `now > deadline-1h`만 검사하여 마감 이후에도 리마인더 대상으로 판정됨
- **판정**: ✅ 수용
- **AI 분석**: 마감 이후에는 리마인더 발송 불필요. 상한 조건(`now < deadline`) 추가 필요
- **결정**: 수용
- **의견**: ai 동의

---

### 8. docs/payment-spec.md 스펙-구현 불일치
- **파일**: `docs/payment-spec.md:20-23`
- **내용**: "User 경고/차단 시스템"이 범위 외로 명시되어 있으나 실제로 구현됨
- **판정**: ✅ 수용
- **AI 분석**: 문서와 코드 정합성 유지를 위해 범위 내로 이동 필요
- **결정**: 수용
- **의견**: ai 동의

---

### 9. AuctionDetailPage TestNoShowButton 프로덕션 노출
- **파일**: `AuctionDetailPage.jsx:420-487`
- **내용**: 테스트용 노쇼 버튼이 환경 가드 없이 프로덕션에 노출됨
- **판정**: ✅ 수용
- **AI 분석**: 테스트 기능이 실제 사용자에게 노출되면 안 됨. 환경 변수 또는 프로파일 체크 필요
- **결정**: 거부
- **의견**: 나중에 한번에 처리할 예정

---

### 10. TestController @Profile 누락
- **파일**: `TestController.java:28-43`
- **내용**: 테스트 컨트롤러가 프로덕션에서도 항상 등록됨. `@Profile({"local", "test"})` 추가 권장
- **판정**: ✅ 수용
- **AI 분석**: 강제 종료/노쇼 API가 프로덕션에 노출되면 심각한 보안 문제
- **결정**: 거부
- **의견**: 나중에 한번에 처리할 예정

---

### 11. formatters.js 타임존 오프셋 처리
- **파일**: `formatters.js:82-86`
- **내용**: `+09:00` 같은 오프셋이 있는 문자열에 `Z`를 추가하면 `Invalid Date`
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 서버는 타임존 없이 반환하므로 문제없으나, 방어적 코드로 정규식 체크 추가 권장
- **결정**: 수용
- **의견**: ai 동의

---

### 12. PaymentPage 중복 조건 검사
- **파일**: `PaymentPage.jsx:121-124`
- **내용**: `isExpired`가 이미 `NO_SHOW` 포함하는데 다시 검사
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 동작에 문제없으나 불필요한 중복
- **결정**: 거부
- **의견**: ai 동의

---

### 13. docs/payment-spec.md BC 격리 원칙
- **파일**: `docs/payment-spec.md:197-200`
- **내용**: Transaction과 Winning을 "같은 트랜잭션"으로 묶는 것이 BC 격리 위반
- **판정**: ❌ 거부
- **AI 분석**: 현재 MVP 단계에서 단순함 우선. 추후 확장 시 이벤트 기반으로 리팩토링 가능
- **결정**: 거부
- **의견**: 

---

### 14. MockPaymentProcessor Thread.sleep 블로킹
- **파일**: `docs/payment-spec.md:371-388`
- **내용**: `Thread.sleep()`이 요청 스레드 블로킹. 비동기 방식 권장
- **판정**: ❌ 거부
- **AI 분석**: Mock 구현이며 테스트 용도. 실제 PG 연동 시 비동기로 교체될 예정
- **결정**: 거부
- **의견**: 결제 방식 변경 예정

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 6개 | #2, #6, #7, #8, #9, #10 |
| ⚠️ 선택적 | 6개 | #1, #3, #4, #5, #11, #12 |
| ❌ 거부 | 2개 | #13, #14 |
