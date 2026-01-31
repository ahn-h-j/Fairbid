# PR #56 리뷰 분석

> **PR**: feat(trade): 거래 연결 기능 구현 (Mock 결제 → 직거래/택배 플로우)
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/56
> **리뷰어**: coderabbitai[bot]
> **분석일**: 2026-01-31

---

## coderabbitai 리뷰

### 1. NotificationStoragePort 및 InAppNotification 클래스 누락
- **파일**: `FcmPushNotificationAdapter.java:3-22`
- **내용**: import하는 NotificationStoragePort와 InAppNotification 클래스가 존재하지 않아 컴파일 실패
- **판정**: ❌ 거부
- **AI 분석**: 해당 클래스들은 #52 (In-App 알림 기능) 브랜치에서 구현됨. 현재 PR 범위 외.
- **결정**: 거부
- **의견**: ai 동의

---

### 2. Mock 결제 관련 알림 타입 제거 필요
- **파일**: `NotificationType.java:45-66`, `FcmPushNotificationAdapter.java`
- **내용**: PAYMENT_COMPLETED, PAYMENT_REMINDER 알림 타입과 관련 메서드가 남아있음
- **판정**: ✅ 수용
- **AI 분석**: PR 목표가 Mock 결제 코드 완전 제거이므로, 결제 관련 알림 타입도 제거해야 일관성 유지
- **결정**: 수용
- **의견**: ai 동의

---

### 3. TradeController.getTrade 권한 검사 누락
- **파일**: `TradeController.java:47-66`
- **내용**: userId를 가져오지만 실제 권한 검증 없이 거래 정보 조회 가능
- **판정**: ✅ 수용
- **AI 분석**: 현재 사용자가 거래 당사자(판매자/구매자)인지 검증 필요. 보안 취약점.
- **결정**: 수용
- **의견**: ai 동의

---

### 4. Trade.complete() 상태 검증이 관대함
- **파일**: `Trade.java:135-140`
- **내용**: ARRANGED와 AWAITING_ARRANGEMENT 상태 모두에서 완료 허용 → 조율 단계 건너뛰기 가능
- **판정**: ✅ 수용
- **AI 분석**: TradeCommandService.complete()에서도 상태 체크 없이 바로 trade.complete() 호출. AWAITING_ARRANGEMENT에서 API 호출 시 조율 단계 건너뛰고 COMPLETED 가능. 보안/비즈니스 로직 문제.
- **결정**: 수용
- **의견**: 서비스 레이어 확인 결과 상태 체크 없음. ARRANGED에서만 완료 허용하도록 수정 필요

---

### 5. UserController에서 TradeRepositoryPort 직접 접근
- **파일**: `UserController.java:56`
- **내용**: 컨트롤러가 리포지토리 포트 직접 주입 → 헥사고날 아키텍처 위반
- **판정**: ✅ 수용
- **AI 분석**: UseCase를 통해 접근해야 함. GetTradeStatsUseCase 생성 또는 GetMyProfileUseCase 확장 필요
- **결정**: 수용
- **의견**: ai 동의

---

### 6. Trade.transferToSecondRank 완료/취소 상태 검증 누락
- **파일**: `Trade.java:159-176`
- **내용**: 이미 완료/취소된 거래도 2순위 승계 가능
- **판정**: ✅ 수용
- **AI 분석**: COMPLETED, CANCELLED 상태에서는 승계 불가하도록 가드 추가 필요
- **결정**: 수용
- **의견**: ai 동의

---

### 7. Trade 생성 시 거래방식 둘 다 false인 경우 처리
- **파일**: `Trade.java:63-75`
- **내용**: directTradeAvailable=false, deliveryAvailable=false일 때 DELIVERY로 기본 설정됨
- **판정**: ❌ 거부
- **AI 분석**: #53에서 Auction.create() 시 "최소 1개 거래방식 필수" 검증 추가됨. Trade.create()에 도달하기 전 Auction 단계에서 이미 검증되므로 해당 케이스는 도달 불가능.
- **결정**: 거부
- **의견**: #53에서 Auction 검증 있음. Trade에서 중복 검증 불필요

---

### 8. 다이어그램 Mock 결제 플로우 잔존
- **파일**: `docs/feature/trade/거래.mmd:66-86`
- **내용**: Mock 결제 처리 블록이 다이어그램에 남아있음
- **판정**: ✅ 수용
- **AI 분석**: PR 목표와 불일치. 다이어그램 업데이트 필요
- **결정**: 수용
- **의견**: ai 동의

---

### 9. 노쇼처리 다이어그램 시간 불일치
- **파일**: `docs/feature/winning/노쇼처리.mmd:3-23`
- **내용**: 3시간 기준이 남아있음 (실제는 24h/72h)
- **판정**: ✅ 수용
- **AI 분석**: 문서와 구현 일치 필요
- **결정**: 수용
- **의견**: ai 동의, 실제 구현도 반여되어 있는지 체크하고 알림 TTL이 24h로 되어 있을거임 

---

### 10. TradeDetailPage 역제안 시 prompt 사용
- **파일**: `TradeDetailPage.jsx:303-310`
- **내용**: 역제안 시 prompt() 사용 → UX 저하
- **판정**: ✅ 수용
- **AI 분석**: 모달/폼 방식이 더 나은 UX지만, MVP 단계에서는 수용 가능
- **결정**: 수용
- **의견**: 모달/폼 방식으로 변경

---

### 11. ArrangedUI 수령확인 버튼 권한
- **파일**: `TradeDetailPage.jsx:557-597`
- **내용**: 판매자/구매자 모두에게 수령확인 버튼 노출
- **판정**: ✅ 수용
- **AI 분석**: 구매자만 수령확인 가능해야 함. isSeller 체크 필요
- **결정**: 수용
- **의견**: ai 동의

---

### 12. 문서 불일치 - User 경고 부여
- **파일**: `docs/implementation-analysis.md:201-203`
- **내용**: "User 경고 부여 ❌ 미구현"으로 표기되어 있으나 실제 구현됨
- **판정**: ✅ 수용 (Nitpick)
- **AI 분석**: 문서 정합성 필요
- **결정**: 수용
- **의견**: ai 동의

---

### 13. 직거래 제안 과거 시간 검증
- **파일**: `DirectTradeProposalRequest.java:16-21`, `DirectTradeInfo.java:105-114`
- **내용**: 오늘 날짜 + 과거 시간 제안이 검증 통과
- **판정**: ✅ 수용
- **AI 분석**: 당일 제안을 허용한다면 시간 검증 추가 필요
- **결정**: 수용
- **의견**: 당일 약속 허용. 과거 시간 검증 추가 필요

---

### 14. MyPage 취소 시 신규 등록 케이스 미처리
- **파일**: `MyPage.jsx:326-331`
- **내용**: 배송지 없는 상태에서 신규 등록 취소 시 폼 미초기화
- **판정**: ✅ 수용
- **AI 분석**: 엣지 케이스. 큰 이슈 아님
- **결정**: 수용
- **의견**: 취소 시 폼 초기화 추가

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 12개 | #2, #3, #4, #5, #6, #8, #9, #10, #11, #12, #13, #14 |
| ❌ 거부 | 2개 | #1 (다른 PR 범위), #7 (#53에서 검증) |

---

## 우선순위 정리

### 즉시 반영 필요 (보안/핵심 로직)
1. #3 - TradeController 권한 검사 ✅ 완료
2. #4 - Trade.complete() 상태 검증 강화 ✅ 완료
3. #5 - UserController 아키텍처 위반 ✅ 완료
4. #6 - Trade.transferToSecondRank 상태 검증 ✅ 완료
5. #13 - 직거래 과거 시간 검증 ✅ 완료

### 반영 권장 (일관성/UX)
6. #2 - Mock 결제 알림 타입 제거 ✅ 완료
7. #8, #9 - 다이어그램 업데이트 ⏳ 보류 (문서 작업)
8. #10 - 역제안 모달/폼 방식 변경 ✅ 완료
9. #11 - 수령확인 버튼 권한 ✅ 완료
10. #12 - 문서 정합성 ⏳ 보류 (문서 작업)
11. #14 - MyPage 취소 시 폼 초기화 ✅ 완료

---

## 수정 완료 현황

| 항목 | 상태 | 수정 내용 |
|------|------|----------|
| #3 | ✅ | TradeController.getTrade()에 거래 참여자 권한 검사 추가 |
| #4 | ✅ | Trade.complete()를 ARRANGED 상태에서만 허용하도록 제한 |
| #5 | ✅ | GetTradeStatsUseCase 생성, UserService 구현, UserController 수정 |
| #6 | ✅ | Trade.transferToSecondRank()에 COMPLETED/CANCELLED 가드 추가 |
| #13 | ✅ | DirectTradeInfo.validateProposal()에 당일 과거 시간 검증 추가 |
| #2 | ✅ | NotificationType, PushNotificationPort, FcmPushNotificationAdapter에서 PAYMENT_COMPLETED/PAYMENT_REMINDER 제거 |
| #10 | ✅ | TradeDetailPage 역제안 prompt → 모달 UI로 변경 |
| #11 | ✅ | ArrangedUI 수령확인 버튼을 구매자만 표시하도록 수정 |
| #14 | ✅ | MyPage 배송지 취소 시 폼 초기화 로직 추가 |
