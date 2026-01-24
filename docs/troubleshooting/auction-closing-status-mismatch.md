# Troubleshooting

프로젝트 진행 중 발견된 문제와 해결책을 기록합니다.

---

## 1. 경매 종료 상태 불일치 문제

### 문제 발견

테스팅 툴로 경매를 종료시켰는데 "진행중"으로 표시됨.

- "1분 전으로 설정" 후 1분 대기 → 여전히 "진행중"
- "경매 강제 종료" 버튼 클릭 → 여전히 "진행중"

### 원인 추적

#### 1단계: 테스팅 툴 확인

테스팅 툴이 Redis의 `scheduledEndTime`만 수정하고 RDB는 안 건드림.

```java
// TestController.setEndTime()
redisTemplate.opsForHash().put(key, "scheduledEndTime", newEndTime.toString());
// RDB 업데이트 없음!
```

#### 2단계: 스케줄러 확인

스케줄러가 RDB를 폴링해서 종료 대상을 찾고 있음.

```java
// AuctionClosingService.closeExpiredAuctions()
List<Auction> closingAuctions = auctionRepository.findClosingAuctions();

// JpaAuctionRepository - RDB 조회
@Query("SELECT a FROM AuctionEntity a WHERE a.status IN :statuses AND a.scheduledEndTime <= :now")
```

**결과**: 테스팅 툴은 Redis만 수정 → 스케줄러는 RDB를 봄 → 종료 대상 못 찾음

#### 3단계: 일반 종료는?

테스팅 툴 문제는 알겠는데, 그럼 24시간 후 자연 종료는 제대로 되나?

- RDB의 `scheduledEndTime`이 지나면 스케줄러가 잡음 ✓
- `auctionRepository.save(auction)`으로 RDB 상태 업데이트 ✓

**그런데**: 종료 후에도 경매 상세에서 "진행중"으로 표시됨.

#### 4단계: 조회 경로 확인

```java
// AuctionService.getAuctionDetail() - Redis 먼저 조회
return auctionCachePort.findById(auctionId)
        .orElseGet(() -> {
            // 캐시 미스 시 RDB 조회
        });
```

종료 처리 시 RDB만 업데이트하고 Redis 캐시는 갱신하지 않음.

```java
// AuctionClosingHelper.processAuctionClosing()
auction.close(winnerId);
auctionRepository.save(auction);   // RDB만 저장
// Redis 캐시 갱신 없음!
```

### 정리: 실제 로직의 문제

테스팅 툴 버그를 추적하다 보니 실제 로직에서도 문제 발견:

```
┌─────────────────────────────────────────────────────────────┐
│ 문제 1: 스케줄러가 RDB를 폴링                               │
├─────────────────────────────────────────────────────────────┤
│ - Redis가 메인 DB인 구조에서 스케줄러만 RDB를 봄            │
│ - 입찰로 연장된 시간은 Redis에만 반영됨                     │
│ - 불일치 발생 가능                                          │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ 문제 2: 종료 시 Redis 캐시 미갱신                           │
├─────────────────────────────────────────────────────────────┤
│ - 종료 처리 시 RDB만 업데이트                               │
│ - Redis 캐시는 BIDDING 상태 그대로                          │
│ - 경매 상세 조회 시 "진행중"으로 표시                       │
└─────────────────────────────────────────────────────────────┘
```

### 해결책

#### 1. 종료 대기 큐를 Redis Sorted Set으로 변경

RDB 폴링 대신 Redis에서 종료 대상을 관리.

```
Key: auction:closing
Type: Sorted Set
Score: 종료시간(ms)
Member: 경매ID
```

```java
// 경매 생성 시
ZADD auction:closing {endTimeMs} {auctionId}

// 스케줄러 (매초)
ZRANGEBYSCORE auction:closing 0 {nowMs}

// 연장 시 (score 업데이트)
ZADD auction:closing {newEndTimeMs} {auctionId}

// 종료 처리 후
ZREM auction:closing {auctionId}
```

#### 2. 종료 시 Redis 캐시 동기 업데이트

```java
// AuctionClosingHelper.processAuctionClosing() 수정
auction.close(winnerId);
auctionRepository.save(auction);              // RDB 저장

auctionCachePort.updateStatus(auctionId, AuctionStatus.ENDED);  // Redis 캐시 갱신
auctionCachePort.removeFromClosingQueue(auctionId);             // Sorted Set에서 제거

eventPublisher.publishAuctionClosed(auctionId);
```

#### 결과

```
종료 처리 후:
- Redis auction:{id} → status: ENDED  ✓
- Redis auction:closing → 제거됨      ✓
- RDB → status: ENDED                 ✓

경매 상세 (Redis): "종료" ✓
경매 목록 (RDB): "종료" ✓
```

### 구현 우선순위

1. 종료 시 Redis 캐시 상태 업데이트 추가
2. 종료 대기 큐를 Redis Sorted Set으로 변경
3. 테스팅 툴 수정 (Sorted Set 사용하도록)

---
