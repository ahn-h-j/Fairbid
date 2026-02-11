# PR #65 리뷰 분석

> **PR**: 판매자 계좌 등록 및 입금 확인/거절 기능 구현
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/65
> **리뷰어**: CodeRabbit
> **분석일**: 2026-02-05

---

## CodeRabbit 리뷰

### 1. IllegalStateException 메시지 문자열 의존
- **파일**: `common/exception/GlobalExceptionHandler.java:248`
- **내용**: `"인증된 사용자"` substring 매칭으로 401 판별하는 것은 메시지 변경 시 깨질 수 있음. 전용 예외 타입으로 분리 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: 기술적으로 맞는 지적. 다만 이번 PR 스코프가 아닌 기존 코드이며, 현재 SecurityUtils에서 던지는 메시지가 고정되어 있어 당장 문제는 없음. 향후 리팩토링 시 UnauthorizedException 분리가 바람직함
- **결정**: 수용
- **비고**: 하드코딩 개선

### 2. 결제 단계에서 DELIVERY 방식 가드 누락
- **파일**: `trade/application/service/DeliveryService.java:217`
- **내용**: confirmPayment/verifyPayment/rejectPayment에서 TradeMethod.DELIVERY 검증이 없어 직거래 tradeId로 호출 시 500 에러 발생 가능
- **판정**: ✅ 수용
- **AI 분석**: submitAddress()에는 DELIVERY 검증이 있지만 나머지 결제 메서드에는 없음. 직거래 거래에서 호출 시 deliveryInfo가 없어 IllegalStateException(500)이 발생하므로, InvalidTradeStatusException(400)으로 처리하는 것이 올바름
- **결정**: 수용
- **비고**: ai 동의

### 3. 경매목록조회 다이어그램 status=ACTIVE 불일치
- **파일**: `docs/feature/auction/경매목록조회.mmd:12`
- **내용**: 예시 쿼리의 status=ACTIVE가 실제 허용 값과 불일치
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 이번 PR 스코프 외 기존 문서. 별도 chore로 처리 가능
- **결정**: 수용
- **비고**: 

### 4. 인앱알림 다이어그램에 PAYMENT_* 타입 누락
- **파일**: `docs/feature/notification/인앱알림.mmd:12`
- **내용**: PAYMENT_CONFIRMED, PAYMENT_VERIFIED, PAYMENT_REJECTED가 알림 유형 목록에 없음
- **판정**: ⚠️ 선택적
- **AI 분석**: 문서-구현 불일치. 다이어그램 업데이트가 바람직하나, 이 mmd 파일은 설계 문서이지 자동 생성 문서가 아니므로 우선순위 낮음
- **결정**: 수용 
- **비고**:

### 5. 배송 다이어그램에 결제 확인/거절 단계 누락
- **파일**: `docs/feature/trade/배송.mmd:90`
- **내용**: 입금 완료 → 판매자 확인/거절 → 확인 완료 단계가 다이어그램에 미반영
- **판정**: ⚠️ 선택적
- **AI 분석**: 4번과 동일하게 설계 다이어그램 업데이트 건. 비즈로직 문서(biz-logic.md)는 이미 업데이트됨
- **결정**: 수용
- **비고**:

### 6. 노쇼처리 다이어그램 90% 조건 미명시
- **파일**: `docs/feature/winning/노쇼처리.mmd:16`
- **내용**: 2순위 승계 시 90% 조건이 상단 규칙 노트에 빠져 있음
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 이번 PR 스코프 외. 기존 문서 개선 건
- **결정**: 수용
- **비고**:

### 7. 리팩토링 문서 코드블록 언어 지정자 누락
- **파일**: `docs/refactoring/clean-architecture-refactoring.md:25`
- **내용**: Markdown 코드블록에 언어 지정자(text) 추가 필요
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: markdownlint 스타일 이슈. 이번 PR 스코프 외
- **결정**: 수용
- **비고**:

### 8. 리팩토링 문서 아키텍처 다이어그램 언어 지정자 누락
- **파일**: `docs/refactoring/clean-architecture-refactoring.md:204`
- **내용**: ASCII 다이어그램 코드블록에 언어 지정자 추가 필요
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 7번과 동일. 스코프 외
- **결정**: 수용
- **비고**:

### 9. 리팩토링 문서 날짜 미확정
- **파일**: `docs/refactoring/common-exception-refactoring.md:4`
- **내용**: `2026-01-XX` → 실제 날짜로 교체 필요
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 스코프 외 기존 문서
- **결정**: 수용
- **비고**:

### 10. 리팩토링 가이드 코드블록 언어 지정자 누락
- **파일**: `docs/refactoring/refactoring-work-guide.md:15`
- **내용**: 코드블록 언어 지정자 추가 필요
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 7, 8번과 동일. 스코프 외
- **결정**: 수용
- **비고**:

### 11. async-rdb-sync 문서 블록인용 빈 줄
- **파일**: `docs/tradeoff/async-rdb-sync-SPEC.md:4`
- **내용**: markdownlint MD028 경고. 인용문 내부 빈 줄 제거 필요
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 스코프 외 기존 문서
- **결정**: 수용
- **비고**:

### 12. HA 문서 서비스 이름 불일치
- **파일**: `docs/tradeoff/high-availability-SPEC.md:69`
- **내용**: `fairbid-redis` vs `redis-master` 혼용
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 스코프 외 기존 문서
- **결정**: 수용
- **비고**: redis-master로 수정

### 13. HA 문서 Slave 포트 매핑 누락
- **파일**: `docs/tradeoff/high-availability-SPEC.md:112`
- **내용**: compose에 slave 포트 매핑이 없어 시나리오 실행 불가
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 스코프 외 기존 문서
- **결정**: 수용
- **비고**:

### 14. HA 문서 Sentinel 포트/설정 불완전
- **파일**: `docs/tradeoff/high-availability-SPEC.md:205`
- **내용**: Sentinel compose에 포트 매핑 및 설정 마운트 누락
- **판정**: ❌ 거부 (Nitpick)
- **AI 분석**: 스코프 외 기존 문서
- **결정**: 수용
- **비고**:

### 15. handleSubmitBankAccount API 실패 시 로컬 상태 오염
- **파일**: `frontend/src/pages/TradeDetailPage.jsx:446`
- **내용**: apiRequest 실패 시에도 setSavedBankAccount가 실행될 수 있음
- **판정**: ✅ 수용
- **AI 분석**: onAction 래퍼가 try-catch로 감싸지만, handleSubmitBankAccount 내부에서 apiRequest 후 바로 setSavedBankAccount를 호출함. apiRequest가 실패하면 throw되므로 실제로는 setSavedBankAccount에 도달하지 않음. 하지만 순서를 명확히 하기 위해 await 후 상태 업데이트를 보장하는 것이 방어적으로 안전함
- **결정**: 수용
- **비고**:

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 2개 | #2 DELIVERY 가드, #15 계좌 등록 상태 오염 |
| ⚠️ 선택적 | 3개 | #1 예외 타입 분리, #4 알림 다이어그램, #5 배송 다이어그램 |
| ❌ 거부 | 10개 | #3,6~14 스코프 외 문서 Nitpick |

---

## 반영 계획

### Backend
1. #2 - `DeliveryService.java`: confirmPayment, verifyPayment, rejectPayment에 TradeMethod.DELIVERY 가드 추가

### Frontend
1. #15 - `TradeDetailPage.jsx`: handleSubmitBankAccount에서 setSavedBankAccount 호출 순서 보장
