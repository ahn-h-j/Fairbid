# PR #37 리뷰 분석

> **PR**: fix(auction): 경매 종료 Redis-RDB 상태 불일치 해결
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/37
> **리뷰어**: CodeRabbit, GitHub Copilot
> **분석일**: 2026-01-24

---

## CodeRabbit 리뷰

### 1. Long.parseLong() 파싱 실패 시 예외 처리
- **파일**: `backend/src/main/java/com/cos/fairbid/auction/adapter/out/cache/RedisAuctionCacheAdapter.java:78-91`
- **내용**: `findAuctionIdsToClose()`에서 Redis Sorted Set 멤버를 `Long.parseLong()`으로 파싱 시, 손상된 데이터가 있으면 `NumberFormatException`으로 스케줄러 전체가 실패할 수 있음
- **판정**: ✅ 수용
- **AI 분석**: Sorted Set에는 우리 코드만 `String.valueOf(auctionId)`로 데이터를 넣지만, 운영 중 수동 개입이나 버그로 예기치 않은 값이 들어갈 가능성이 있음. 스케줄러는 전체 경매 종료를 관장하므로 하나의 잘못된 데이터로 인한 전체 실패는 치명적임. try-catch로 방어하고 로그를 남기는 것이 안전함
- **결정**: 수용
- **의견**: ai의견 동의

### 2. TestController @Profile 어노테이션 누락
- **파일**: `backend/src/main/java/com/cos/fairbid/common/test/TestController.java:25-29`
- **내용**: 클래스 주석에 "프로덕션 환경에서는 비활성화되어야 함"이라 명시했으나, `@Profile` 어노테이션이 없어 모든 환경에서 활성화됨. 경매 강제 종료, 종료 시간 조작 등 민감한 기능이 프로덕션에 노출될 수 있음
- **판정**: ✅ 수용
- **AI 분석**: 보안 관점에서 명확한 취약점. `@Profile({"dev", "test", "local"})`를 추가하면 프로덕션 빌드에서 빈 자체가 생성되지 않아 완전히 차단됨. 비용 대비 효과가 매우 높은 수정임
- **결정**: 거부
- **의견**: 포트폴리오 용이기에 정상기능 동작 테스트를 위해 계속 남겨둘 예정

### 3. 경매 상태 검증 누락 - 중복 종료 처리 가능성
- **파일**: `backend/src/main/java/com/cos/fairbid/winning/application/service/AuctionClosingHelper.java:43-63`
- **내용**: `processAuctionClosing()`에서 경매 상태(BIDDING/INSTANT_BUY_PENDING)를 검증하지 않아, Redis Sorted Set에서 제거 실패 시 다음 스케줄러 실행에서 동일 경매가 다시 처리될 수 있음
- **판정**: ✅ 수용
- **AI 분석**: 현재 도메인 모델의 `close()` 메서드에서 상태 검증을 할 수 있지만, Service 레이어에서도 early return으로 불필요한 DB 조회/트랜잭션을 방지하는 것이 효율적. 특히 `removeFromClosingQueue` 실패 시나리오에서 로그 노이즈도 줄일 수 있음
- **결정**: 수용
- **의견**: ai 의견 동의

### 4. TOCTOU 경쟁 조건: hasKey()와 put() 사이 키 삭제 가능성
- **파일**: `backend/src/main/java/com/cos/fairbid/auction/adapter/out/cache/RedisAuctionCacheAdapter.java:97-106`
- **내용**: `updateStatus()`에서 `hasKey()` 체크 후 `put()` 실행 전에 다른 프로세스가 키를 삭제하면, status 필드만 있는 불완전한 해시가 생성될 수 있음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 이론적 경쟁 조건이지만 실제 발생 확률은 극히 낮음. 경매 종료 시점에 캐시를 삭제하는 로직은 없으며, TTL 만료로 삭제되더라도 이미 종료된 경매의 상태 업데이트이므로 영향이 미미함. Lua 스크립트로 원자적 처리하면 완벽하지만, 현재 복잡도 대비 이득이 적음
- **결정**: 거부
- **의견**: 정상동작하고 있음

### 5. Lua 스크립트 auctionId 추출 로직 키 포맷 결합
- **파일**: `backend/src/main/resources/scripts/bid.lua:17-20`
- **내용**: `string.sub(auctionKey, 9)`는 "auction:" prefix가 정확히 8글자임을 가정. 키 포맷 변경 시 잘못된 ID 추출됨
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 키 포맷은 `auction:{id}`로 고정되어 있고, 변경 계획이 없음. `string.match(auctionKey, ":([^:]+)$")`로 변경하면 더 유연하지만, Lua 스크립트에서 정규식은 성능 오버헤드가 있음. `string.find`로 콜론 위치를 찾는 방식이 절충안이 될 수 있음
- **결정**: 수용
- **의견**: 키 변경 예정은 없지만 만약 변경되었을 경우 문제가 발생할 수 있음 string.find로 처리하는게 좋아보임

---

## GitHub Copilot 리뷰

### 6. Lua 스크립트 auctionId 추출 - 콜론 기반 추출 제안
- **파일**: `backend/src/main/resources/scripts/bid.lua:19-20`
- **내용**: `string.sub(auctionKey, 9)` 대신 콜론 위치를 기준으로 추출하도록 제안. prefix 변경 시 안전성 확보
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: CodeRabbit #5와 동일한 지적. Copilot은 `string.find`로 콜론 위치를 찾고 유효성 검증까지 추가하는 방식을 제안함. 다만 제안된 코드에서 콜론이 없거나 마지막 문자인 경우 `{0, "NOT_FOUND"}`를 반환하는데, 이는 입찰 로직의 에러 코드 형식과 맞지 않을 수 있음
- **결정**: 패스
- **의견**: 위에서 답변

### 7. Redis 동기화 실패 시 Sorted Set 잔존 - 중복 처리 노이즈
- **파일**: `backend/src/main/java/com/cos/fairbid/winning/application/service/AuctionClosingHelper.java:94-102`
- **내용**: `syncRedisAfterClosing()` 실패 시 경매가 Sorted Set에 남아 다음 스케줄러 실행에서 재처리됨. 도메인 상태 검증이 `IllegalStateException`을 발생시켜 로그 노이즈 생성
- **판정**: ✅ 수용
- **AI 분석**: CodeRabbit #3과 연관됨. `processAuctionClosing()` 진입 시 상태 검증을 추가하면, 이미 종료된 경매에 대한 재처리 시도를 early return으로 방지할 수 있어 이 문제도 자연스럽게 해결됨
- **결정**: 패스
- **의견**: 위에서 답변

### 8. AuctionEventListener 종료 큐 등록 실패 시 경매 미종료 위험
- **파일**: `backend/src/main/java/com/cos/fairbid/auction/adapter/in/event/AuctionEventListener.java:31-51`
- **내용**: 캐시 워밍 + 종료 큐 등록이 동일 try-catch에 묶여 있어, `addToClosingQueue()` 실패 시 스케줄러가 해당 경매를 종료하지 못함. fallback 메커니즘(RDB 주기적 체크) 제안
- **판정**: ⚠️ 선택적
- **AI 분석**: 유효한 지적이나, 현재 아키텍처에서 Redis 장애는 전체 시스템 장애와 동일 (입찰 자체가 Redis 기반). Redis가 정상인 상태에서 `ZADD`만 실패하는 시나리오는 극히 드묾. 완전한 해결책은 주기적 보정 배치(예: 10분마다 RDB와 Sorted Set 비교)이지만, 현재 MVP 단계에서는 과도한 설계임
- **결정**: 보류
- **의견**: Todo/todo.md에 작성

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 4개 | #1 방어적 파싱, #2 @Profile 추가, #3 상태 검증, #7 중복 처리 방지 |
| ⚠️ 선택적 | 4개 | #4 TOCTOU, #5 Lua ID 추출, #6 Lua ID 추출(Copilot), #8 fallback 메커니즘 |
| ❌ 거부 | 0개 | - |
