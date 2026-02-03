# PR #61 리뷰 분석

> **PR**: refactor: 낙찰자 결정 기준을 RDB에서 Redis로 변경 (#58)
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/61
> **리뷰어**: Copilot, CodeRabbit
> **분석일**: 2026-02-02

---

## CodeRabbit 리뷰

### 1. multiGet 결과 null/빈 리스트 가드 필요
- **파일**: `RedisAuctionCacheAdapter.java:161`
- **내용**: multiGet은 키가 없으면 빈 리스트, 파이프라인에서는 null 반환. values.get(0)에서 예외 발생 위험
- **판정**: ✅ 수용
- **AI 분석**: Spring Data Redis의 multiGet은 키가 없으면 빈 리스트, 트랜잭션/파이프라인에서는 null을 반환함. 현재 코드는 이에 대한 가드가 없어 IndexOutOfBoundsException 또는 NPE 발생 가능
- **결정**: 수용
- **의견**: ai 동의

### 2. 1순위/2순위 조회 원자성 문제
- **파일**: `AuctionClosingHelper.java:85`
- **내용**: 두 개의 개별 Redis 조회 사이에 새로운 입찰이 들어오면 스냅샷 불일치 발생
- **판정**: ✅ 수용
- **AI 분석**: getTopBidderInfo()와 getSecondBidderInfo()를 별도로 호출하면, 그 사이에 입찰이 발생할 경우 threshold 계산이 잘못될 수 있음. 단, 경매 종료 시점에는 BIDDING 상태가 아니므로 새 입찰이 불가하여 실제 문제 발생 확률은 낮음
- **결정**: 거부
- **의견**: 이미 종료된 시점에서 실행되기에 새로운 입찰이 발생하지 않음

### 3. Lua 스크립트 prevTopBidAmount nil 체크 필요
- **파일**: `bid.lua:111`
- **내용**: prevTopBidAmount가 nil일 때 HSET 호출 시 Redis 오류 발생
- **판정**: ✅ 수용
- **AI 분석**: Redis Lua에서 nil 값을 HSET에 전달하면 "command arguments must be strings or integers" 오류 발생. prevTopBidderId만 체크하고 prevTopBidAmount는 체크하지 않아 데이터 불일치 시 스크립트 실패 가능
- **결정**: 수용
- **의견**: ai 동의

### 4. auctionId 변경 시 상태 리셋 필요
- **파일**: `AuctionDetailPage.jsx:38`
- **내용**: 라우트 변경 시 hasBid/wasFirstRank가 이전 경매 값으로 남을 수 있음
- **판정**: ✅ 수용
- **AI 분석**: React 컴포넌트에서 auctionId가 변경되면 bidStorageKey도 변경되지만, useState 초기값은 첫 렌더링에서만 평가됨. useEffect로 auctionId 변경 시 상태 재동기화 필요
- **결정**: 수용
- **의견**: ai 동의

### 5. userBidRank=2 배너 표시 안 됨
- **파일**: `AuctionDetailPage.jsx:293`
- **내용**: 백엔드가 2순위를 반환해도 배너가 숨겨짐
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 조건이 `auction.userBidRank === 1 || hasBid || wasFirstRank`로 되어 있어 userBidRank=2인 경우 hasBid/wasFirstRank가 false면 배너가 안 보임. 다만 2순위 표시가 요구사항에 포함되어 있는지 확인 필요
- **결정**: 거부
- **의견**: 2순위 표시 기능 불필요. calculateUserBidRank에서 return 2 제거 예정

---

## Copilot 리뷰

### 6. BidAsyncService에서 topBidderId 정확성
- **파일**: `BidAsyncService.java:79`
- **내용**: @Async 메서드 실행 시점에 다른 사용자가 더 높은 입찰을 했을 수 있어 bidderId가 더 이상 topBidderId가 아닐 수 있음
- **판정**: ⚠️ 선택적
- **AI 분석**: Async 메서드는 RDB 동기화용이고, 실시간 WebSocket 메시지는 동기 BidService에서 발행됨. 비동기 이벤트는 이력 저장 용도라면 현재 구현이 맞음. 다만 주석이 오해의 소지가 있음
- **결정**: 거부
- **의견**: ai 동의 하지만 주석을 수정할 필요가 있음

### 7. 2순위 계산이 프론트엔드에서 사용되지 않음
- **파일**: `AuctionController.java:145`
- **내용**: calculateUserBidRank에서 2순위를 계산하지만 프론트엔드는 1순위만 체크함
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 백엔드에서 2순위 계산 로직이 있지만 프론트엔드에서 활용하지 않음. 향후 2순위 표시 기능을 위한 준비로 볼 수 있음. 현재 스코프에서는 불필요한 코드일 수 있음
- **결정**: 수용
- **의견**: 2순위 표시 안 함. calculateUserBidRank에서 return 2 제거

### 8. WebSocket 메시지에 secondBidderId 미포함
- **파일**: `AuctionDetailPage.jsx:79`
- **내용**: BidUpdateMessage에 secondBidderId가 없어 실시간으로 2순위를 알 수 없음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 요구사항이 1순위만 표시하는 것이라면 문제없음. 2순위 실시간 표시가 필요하면 BidUpdateMessage에 secondBidderId 추가 필요
- **결정**: 거부
- **의견**: 2순위 표시 안 함. secondBidderId 추가 불필요

### 9. 낙관적 업데이트 race condition
- **파일**: `AuctionDetailPage.jsx:129`
- **내용**: 입찰 성공 후 optimistic update와 WebSocket 메시지가 충돌할 수 있음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 네트워크 지연으로 인해 자신의 입찰 결과가 WebSocket으로 먼저 도착할 가능성은 낮음. 실제 문제 발생 확률은 미미하나, 엣지 케이스로 고려할 수 있음
- **결정**:
- **의견**:

### 10. 동일 사용자 재입찰 시 2순위 처리
- **파일**: `bid.lua:188`
- **내용**: 같은 사용자가 다시 입찰하면 자신의 이전 입찰이 2순위로 이동하지 않음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 의도된 동작으로 보임. 같은 사용자의 이전 입찰을 2순위로 유지하는 것은 비즈니스적으로 의미가 없음. 다만 주석으로 명시하면 좋음
- **결정**: 거부
- **의견**: 의도된 동작. 같은 사람의 이전 입찰을 2순위로 유지할 필요 없음

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 4개 | #1 multiGet 가드, #2 원자성, #3 Lua nil 체크, #4 상태 리셋 |
| ⚠️ 선택적 | 6개 | #5 2순위 배너, #6 Async topBidderId, #7 2순위 미사용, #8 secondBidderId 미포함, #9 race condition, #10 동일 사용자 재입찰 |
| ❌ 거부 | 0개 | - |

---

## 반영 계획

### Backend
1. #1 - `RedisAuctionCacheAdapter.java`: multiGet 결과 null/size 가드 추가
2. #3 - `bid.lua`: prevTopBidAmount nil 체크 추가

### Frontend
1. #4 - `AuctionDetailPage.jsx`: auctionId 변경 시 useEffect로 상태 리셋

### 보류 (스코프 외)
- #2 원자성: 경매 종료 시점에는 입찰 불가하므로 실제 문제 발생 확률 낮음
- #5~#10: 현재 요구사항 범위 외 또는 Nitpick
