# 구현 진행 상황 분석 문서

> 작성일: 2026-01-11 (최종 수정: 2026-01-30)
> 분석 대상: docs/feature/*.mmd 시퀀스 다이어그램 기반 구현 현황

---

## 1. 개요

본 문서는 FairBid 프로젝트의 기능 명세(mmd 파일)와 실제 구현 코드를 비교 분석하여 현재 진행 상황을 정리한 것이다.

### 분석 대상 기능 명세
| 파일 | 기능 | 위치 |
|------|------|------|
| 경매등록.mmd | 경매 상품 등록 | docs/feature/auction/ |
| 경매조회.mmd | 경매 상세 조회 | docs/feature/auction/ |
| 경매목록조회.mmd | 경매 목록 조회 | docs/feature/auction/ |
| 입찰.mmd | 입찰 처리 | docs/feature/bid/ |
| 낙찰.mmd | 경매 종료 및 낙찰 처리 | docs/feature/bid/ |

---

## 2. 기능별 구현 현황

### 2.1 경매 등록 (경매등록.mmd)

#### 명세 요약
```
판매자 → AuctionController → AuctionService → AuctionRepository → RDB
```

#### 명세 상세 로직
1. Auction 상태 설정: `status = 'BIDDING'`
2. 즉시구입가 검증: `startPrice < instantBuyPrice`
3. 입찰 단위(bidIncrement) 계산 (가격 구간별)
4. 종료 예정 시간(endTime) 계산 (24h/48h)

#### 구현 상태: ✅ 완료

| 항목 | 구현 여부 | 구현 위치 |
|------|----------|----------|
| AuctionController | ✅ | `auction/adapter/in/controller/AuctionController.java` |
| CreateAuctionUseCase | ✅ | `auction/application/port/in/CreateAuctionUseCase.java` |
| AuctionService | ✅ | `auction/application/service/AuctionService.java` |
| AuctionRepository | ✅ | `auction/application/port/out/AuctionRepository.java` |
| 상태 설정 (BIDDING) | ✅ | `Auction.create()` - 라인 83 |
| 즉시구매가 검증 | ✅ | `Auction.create()` - 라인 64-66 |
| 입찰 단위 계산 | ✅ | `Auction.calculateBidIncrement()` - 라인 106-120 |
| 종료 시간 계산 | ✅ | `Auction.create()` - 라인 79 |

#### BDD 테스트: ✅ 존재
- `features/auction/create-auction.feature`
- 정상 등록 시나리오
- 즉시구매가 검증 실패 시나리오

---

### 2.2 경매 조회 (경매조회.mmd)

#### 명세 요약
```
구매자 → AuctionController → AuctionService → AuctionRepository → RDB
```

#### 명세 상세 로직
1. 즉시 구매 버튼 활성화 여부 계산 (입찰가 <= 현재가 * 90%)
2. 다음 입찰 가능 최소 금액 계산 (현재 최고가 + 입찰 단위)
3. 수정 가능 여부 플래그 계산 (총 입찰 횟수 == 0)

#### 구현 상태: ✅ 완료

| 항목 | 구현 여부 | 구현 위치 |
|------|----------|----------|
| GetAuctionDetailUseCase | ✅ | `auction/application/port/in/GetAuctionDetailUseCase.java` |
| AuctionService.getAuctionDetail() | ✅ | `AuctionService.java` - 라인 57-61 |
| 즉시 구매 활성화 여부 | ✅ | `Auction.isInstantBuyEnabled()` - 라인 143-152 |
| 다음 입찰 최소 금액 | ✅ | `Auction.getNextMinBidPrice()` - 라인 160-162 |
| 수정 가능 여부 | ✅ | `Auction.isEditable()` - 라인 170-172 |

#### BDD 테스트: ✅ 존재
- `features/auction/get-auction-detail.feature`

---

### 2.3 경매 목록 조회 (경매목록조회.mmd)

#### 명세 요약
```
구매자 → AuctionController → AuctionService → AuctionRepository → RDB
```

#### 명세 상세 로직
1. 상태(status) 필터링 (선택)
2. 키워드(keyword) 검색 - 상품명 LIKE 검색 (선택)
3. 페이지네이션 (기본값: page=0, size=20)
4. 정렬 (createdAt DESC)

#### 구현 상태: ✅ 완료

| 항목 | 구현 여부 | 구현 위치 |
|------|----------|----------|
| GetAuctionListUseCase | ✅ | `auction/application/port/in/GetAuctionListUseCase.java` |
| AuctionService.getAuctionList() | ✅ | `AuctionService.java` |
| AuctionRepository.findAll() | ✅ | `AuctionRepository.java` |
| JPA Specification 동적 쿼리 | ✅ | `AuctionSpecification.java` |
| 페이지네이션 | ✅ | Spring Data Pageable 사용 |
| 잘못된 enum 파라미터 예외 처리 | ✅ | `GlobalExceptionHandler` |

#### BDD 테스트: ✅ 존재
- `features/auction/get-auction-list.feature`
- 전체 목록 조회
- 상태 필터링 조회
- 키워드 검색 조회
- 빈 결과 조회
- 잘못된 상태값 400 에러

---

### 2.4 입찰 (입찰.mmd)

#### 명세 요약
```
구매자 → BidController → BidService → AuctionRepository (Lock) → Auction (Domain) → BidRepository → RDB → EventPublisher
```

#### 명세 상세 로직
1. **동시성 제어**: 비관적 락으로 경매 조회 (`SELECT ... FOR UPDATE`)
2. **입찰 검증**: 경매 종료 여부, 본인 경매 입찰 불가
3. **연장 처리**: 종료 5분 전 입찰 시 5분 연장, 연장 3회마다 입찰 단위 50% 증가
4. **입찰 처리**: 현재가 갱신, 총 입찰수 증가, 입찰단위 재계산
5. **이벤트 발행**: BidPlacedEvent (실시간 UI 업데이트)

#### 구현 상태: ✅ 완료

| 항목 | 구현 여부 | 구현 위치 |
|------|----------|----------|
| BidController | ✅ | `bid/adapter/in/controller/BidController.java` |
| PlaceBidUseCase | ✅ | `bid/application/port/in/PlaceBidUseCase.java` |
| BidService | ✅ | `bid/application/service/BidService.java` |
| 비관적 락 조회 | ✅ | `BidService.placeBid()` - 라인 49 |
| 입찰 자격 검증 | ✅ | `Auction.validateBidEligibility()` - 라인 198-208 |
| 연장 구간 확인 | ✅ | `Auction.isInExtensionPeriod()` - 라인 215-219 |
| 연장 처리 | ✅ | `Auction.extend()` - 라인 225-229 |
| 할증 입찰단위 계산 | ✅ | `Auction.getAdjustedBidIncrement()` - 라인 237-242 |
| 입찰 처리 | ✅ | `Auction.placeBid()` - 라인 264-282 |
| 이벤트 발행 | ✅ | `BidService.publishBidPlacedEvent()` - 라인 115-123 |
| BidPlacedEvent | ✅ | `bid/domain/event/BidPlacedEvent.java` |
| BidEventListener | ✅ | `notification/adapter/in/event/BidEventListener.java` |

#### BDD 테스트: ✅ 존재
- `features/bid/place-bid.feature`
- 원터치 입찰 성공
- 금액 직접 지정 입찰 성공
- 최소 금액 미만 입찰 실패
- 존재하지 않는 경매 입찰 실패

---

### 2.5 낙찰 (낙찰.mmd)

#### 명세 요약
```
Scheduler → RDB → NotificationService (WebSocket) → 구매자
```

#### 명세 상세 로직

**1단계: 즉시 차단**
- 종료 대상 경매 조회 (status='ACTIVE')
- 웹소켓으로 종료 신호 전달 (UI 즉시 차단)

**2단계: 1, 2순위 추출**
- 최고가/차순위 입찰자 조회 (ORDER BY DESC LIMIT 2)

**3단계: Outbox 패턴 기반 트랜잭션**
- Auction 상태 변경 (CLOSED)
- Winning 테이블에 1, 2순위 후보 정보 저장
- Outbox 테이블에 알림 및 결제 감시 이벤트 저장

**4단계: 노쇼 처리 및 승계**
- 1순위 미결제 시 패널티 부여
- 2순위로 낙찰 권한 승계
- 경매 최종 낙찰자 변경

#### 구현 상태: ⚠️ 부분 완료

| 항목 | 구현 여부 | 구현 위치 | 비고 |
|------|----------|----------|------|
| AuctionClosingScheduler | ✅ | `winning/adapter/in/scheduler/AuctionClosingScheduler.java` | |
| CloseAuctionUseCase | ✅ | `winning/application/port/in/CloseAuctionUseCase.java` | |
| AuctionClosingService | ✅ | `winning/application/service/AuctionClosingService.java` | |
| AuctionClosingHelper | ✅ | `winning/application/service/AuctionClosingHelper.java` | REQUIRES_NEW 트랜잭션 |
| Winning 도메인 | ✅ | `winning/domain/Winning.java` | |
| WinningRepository | ✅ | `winning/application/port/out/WinningRepository.java` | |
| 1, 2순위 추출 | ✅ | `AuctionClosingHelper` 내부 | |
| 노쇼 처리 서비스 | ✅ | `NoShowProcessingService.java` | |
| 2순위 승계 로직 | ✅ | `NoShowProcessingService.transferToSecondRank()` | |
| PaymentTimeoutScheduler | ✅ | `winning/adapter/in/scheduler/PaymentTimeoutScheduler.java` | |
| WebSocket 종료 알림 | ✅ | `AuctionBroadcastPort` + `WebSocketBroadcastAdapter` | |
| FCM 푸시 알림 | ✅ | `FcmPushNotificationAdapter.java` | |
| Outbox 패턴 | ❌ | 미구현 | 현재 직접 이벤트 발행 방식 |
| User 경고 부여 | ❌ | 미구현 | TODO 주석으로 표시됨 |

#### BDD 테스트: ✅ 존재
- `features/winning/auction-closing.feature`
- 입찰 있는 경매 종료 → 최고가 입찰자 낙찰
- 입찰 없는 경매 종료 → 유찰 처리
- 2명 입찰 → 1, 2순위 후보 생성

---

## 3. 아키텍처 준수 현황

### 3.1 헥사고날 아키텍처

| 계층 | 준수 여부 | 비고 |
|------|----------|------|
| Domain (순수 POJO) | ✅ | JPA 의존성 없음 |
| Port In (UseCase) | ✅ | 인터페이스 정의 |
| Port Out (Repository) | ✅ | 인터페이스 정의 |
| Adapter In (Controller) | ✅ | REST API |
| Adapter Out (Persistence) | ✅ | JPA 구현체 |
| Entity ↔ Domain 분리 | ✅ | Mapper 사용 |

### 3.2 Bounded Context 구현

| Context | 구현 패키지 | 상태 |
|---------|------------|------|
| Auction Management | `auction/` | ✅ 완료 |
| Live Bidding | `bid/` | ✅ 완료 |
| Winning | `winning/` | ✅ 완료 |
| Notification | `notification/` | ✅ 완료 |
| Identity (User/Auth) | `user/`, `auth/` | ✅ 완료 |
| Trade | `trade/` | 🔄 진행중 |

---

## 4. 미구현/진행중 항목 요약

### 4.1 핵심 비즈니스 로직

| 항목 | 상태 | 관련 기능 |
|------|------|----------|
| User 도메인 (경고/차단) | ✅ 완료 | 노쇼 처리 |
| Trade 도메인 (거래 연결) | 🔄 진행중 | 직거래/택배 플로우 |
| Admin 페이지 | ✅ 완료 | 관리자 기능 |
| 즉시 구매 로직 | ❌ 미구현 | 입찰 |
| Outbox 패턴 | ❌ 미구현 | 이벤트 신뢰성 |

### 4.2 명세서 대비 누락 로직

#### 입찰.mmd
- 즉시 구매 입찰 처리 (1시간 최종 입찰 기회 제공)

#### 낙찰.mmd
- Outbox 테이블 기반 이벤트 저장
- 2순위 90% 미만 시 판매자 판매 여부 질문

### 4.3 인프라

| 항목 | 구현 여부 |
|------|----------|
| WebSocket 설정 | ✅ |
| FCM 푸시 알림 | ✅ |
| Redis Pub/Sub | ❌ |
| Redis Stream | ❌ |

---

## 5. 테스트 현황

### 5.1 BDD 테스트 (Cucumber)

| Feature | 시나리오 수 | 상태 |
|---------|------------|------|
| health-check.feature | 1 | ✅ |
| create-auction.feature | 2 | ✅ |
| get-auction-detail.feature | 2 | ✅ |
| get-auction-list.feature | 5 | ✅ |
| place-bid.feature | 4 | ✅ |
| auction-closing.feature | 3 | ✅ |

### 5.2 테스트 인프라

- CucumberTestRunner 설정 완료
- RestTestAdapter (REST API 테스트) 구현
- TestContext (테스트 상태 공유) 구현

---

## 6. 결론 및 권장 사항

### 6.1 현재 완성도

| 영역 | 완성도 |
|------|--------|
| 경매 등록/조회 | 100% |
| 입찰 | 90% (즉시 구매 제외) |
| 낙찰 | 90% (Outbox 제외) |
| User/Auth | 100% |
| Admin | 100% |
| Trade | 70% (진행중) |
| **전체** | **~90%** |

### 6.2 다음 단계 권장 작업

1. **Trade 도메인 완성**
   - 직거래 플로우 (시간 제안/수락/역제안)
   - 택배 플로우 (배송지/송장/수령확인)
   - 노쇼 로직 변경 (응답 기한 기반)

2. **즉시 구매 기능 구현**
   - 즉시 구매 입찰 타입 추가
   - 1시간 최종 입찰 기회 로직

3. **Outbox 패턴 도입** (선택)
   - 이벤트 신뢰성 향상

4. **Redis 통합** (선택)
   - 실시간 입찰 캐싱
   - 분산 락 적용 검토

---

## 7. 파일 매핑 참조

```
docs/feature/auction/경매등록.mmd
  └─ src/main/java/com/cos/fairbid/auction/
      ├─ adapter/in/controller/AuctionController.java
      ├─ application/service/AuctionService.java
      └─ domain/Auction.java

docs/feature/auction/경매조회.mmd
  └─ src/main/java/com/cos/fairbid/auction/
      ├─ adapter/in/controller/AuctionController.java
      └─ application/service/AuctionService.java

docs/feature/auction/경매목록조회.mmd
  └─ src/main/java/com/cos/fairbid/auction/
      ├─ adapter/in/controller/AuctionController.java
      ├─ adapter/in/dto/AuctionListResponse.java
      ├─ adapter/out/persistence/repository/AuctionSpecification.java
      ├─ application/port/in/GetAuctionListUseCase.java
      └─ application/service/AuctionService.java

docs/feature/bid/입찰.mmd
  └─ src/main/java/com/cos/fairbid/bid/
      ├─ adapter/in/controller/BidController.java
      ├─ application/service/BidService.java
      └─ domain/Bid.java

docs/feature/bid/낙찰.mmd
  └─ src/main/java/com/cos/fairbid/winning/
      ├─ adapter/in/scheduler/AuctionClosingScheduler.java
      ├─ adapter/in/scheduler/PaymentTimeoutScheduler.java
      ├─ application/service/AuctionClosingService.java
      ├─ application/service/NoShowProcessingService.java
      └─ domain/Winning.java
```
