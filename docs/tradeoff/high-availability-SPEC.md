# 장애 대응 / 고가용성 Specification

> ⚠️ **이 문서는 향후 진행할 테스트의 계획서입니다. 아직 실행 전이며, 실행 후 별도 결과 문서를 작성할 예정입니다.**

> 각 컴포넌트의 SPOF를 인식하고, 장애 시뮬레이션을 통해 고가용성 구성을 검증한다.

---

## 1. Overview

### Problem Statement

현재 구조의 단일 장애점(SPOF):
```
[현재 구조]
- Redis: 단일 인스턴스 → 죽으면 서비스 터짐
- Stream Consumer: 단일 → 죽으면 메시지 쌓임
- RDB: 단일 → 죽으면 영속화 실패
```

### Solution Summary
```
[목표 구조]
- Redis: Sentinel (자동 failover)
- Stream: Consumer Group (분산 처리 + 멱등성)
- RDB: Replication (읽기 분산 + 백업)
```

### Success Criteria

| 기준 | 목표 |
|------|------|
| Redis 장애 | Sentinel이 자동 failover, 서비스 중단 최소화 |
| Consumer 장애 | 다른 Consumer가 처리, 메시지 유실 없음 |
| RDB 장애 | Slave로 읽기 가능, 복구 후 정합성 유지 |

---

## 2. 테스트 범위

### 2.1 깊이 조절

| 영역 | 깊이 | 이유 |
|------|------|------|
| **Redis** | 깊게 (풀코스) | 메인 DB, 핵심 SPOF |
| Stream/Consumer | 가볍게 (Step 3까지) | HA + 멱등성까지 |
| RDB | 가볍게 (Step 2까지) | Replication까지 |

### 2.2 테스트 환경

- **로컬 Docker** (docker-compose)
- 장애 주입: `docker kill`, `docker pause`

---

## 3. Redis 장애 대응 (깊게)

### Step 1: 현재 상태 확인

**목표**: SPOF 인식

**시나리오**:
```bash
# Redis 강제 종료
docker kill redis-master

# 서비스 요청
curl -X POST http://localhost:8080/api/bids
```

**확인 사항**:
- 서비스 터지는지 확인
- 에러 로그 확인

**예상 결과**: 서비스 완전 중단

---

### Step 2: Replication 구성

**목표**: Master-Slave 구성, 수동 failover 체험

**구성**:
```yaml
# docker-compose.yml
services:
  redis-master:
    image: redis:7
    ports:
      - "6379:6379"
  
  redis-slave-1:
    image: redis:7
    ports:
      - "6380:6379"
    command: redis-server --replicaof redis-master 6379

  redis-slave-2:
    image: redis:7
    ports:
      - "6381:6379"
    command: redis-server --replicaof redis-master 6379
```

**시나리오**:
```bash
# 데이터 동기화 확인
redis-cli -p 6379 SET test "hello"
redis-cli -p 6380 GET test

# Master 종료
docker kill redis-master

# Slave 수동 승격
redis-cli -p 6380 REPLICAOF NO ONE
```

**확인 사항**:
- 데이터 동기화 여부
- 수동 승격 과정
- 앱 재연결 필요 여부

**예상 결과**: 수동 failover 가능하지만 번거로움 체감

---

### Step 3: Persistence 설정

**목표**: 재시작 시 데이터 복구 확인

**구성**:
```
# redis.conf
appendonly yes
appendfsync everysec
```

**시나리오**:
```bash
# 데이터 입력
redis-cli SET bid:123 "data"

# Redis 강제 종료 + 재시작
docker kill redis-master
docker start redis-master

# 데이터 확인
redis-cli GET bid:123
```

**확인 사항**:
- 재시작 후 데이터 존재 여부
- 유실 범위 (AOF everysec 기준 최대 1초)

**예상 결과**: 대부분 데이터 복구, 일부 유실 가능

---

### Step 4: Sentinel 구성

**목표**: 자동 failover 구성

**구성**:
```yaml
# docker-compose.yml
services:
  redis-master:
    image: redis:7
    
  redis-slave-1:
    image: redis:7
    command: redis-server --replicaof redis-master 6379
    
  redis-slave-2:
    image: redis:7
    command: redis-server --replicaof redis-master 6379
    
  sentinel-1:
    image: redis:7
    ports:
      - "26379:26379"
    command: redis-sentinel /etc/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/sentinel.conf

  sentinel-2:
    image: redis:7
    ports:
      - "26380:26379"
    command: redis-sentinel /etc/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/sentinel.conf

  sentinel-3:
    image: redis:7
    ports:
      - "26381:26379"
    command: redis-sentinel /etc/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/sentinel.conf
```
```
# sentinel.conf
sentinel monitor mymaster redis-master 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
```

**시나리오**:
```bash
# Master 강제 종료
docker kill redis-master

# Sentinel 로그 확인
docker logs sentinel-1

# 새 Master 확인
redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
```

**확인 사항**:
- failover 자동 발생 여부
- failover 소요 시간
- Sentinel 로그 (감지 → 투표 → 승격)

**예상 결과**: 5-10초 내 자동 failover

---

### Step 4-1: Split Brain 방지

**목표**: Master 2개 되는 상황 방지

**배경**:
```
네트워크 단절 시 Sentinel이 새 Master 승격
→ 원래 Master도 살아있으면 Master 2개
→ 데이터가 두 군데로 갈라짐 (Split Brain)
```

**구성**:
```
# redis.conf (Master)
min-replicas-to-write 1
min-replicas-max-lag 10
```
→ Slave 1개 이상 연결 안 되면 쓰기 거부

**시나리오**:
```bash
# Master와 Slave 사이 네트워크 끊기
docker network disconnect fairbid_network redis-slave-1
docker network disconnect fairbid_network redis-slave-2

# Master에 쓰기 시도
redis-cli -p 6379 SET test "hello"
```

**확인 사항**:
- Master가 쓰기 거부하는지 (NOREPLICAS 에러)
- 클라이언트가 에러 받는지

**예상 결과**: Master가 고립되면 쓰기 거부 → Split Brain 방지

---

### Step 4-2: 네트워크 파티션 장애

**목표**: 프로세스 죽음이 아닌 네트워크 단절 상황 테스트

**배경**:
```
docker kill = 프로세스 깔끔하게 종료 (비현실적)
network disconnect = 프로세스 살아있는데 통신 불가 (현실적)
```

**시나리오**:
```bash
# Master 네트워크만 끊기 (프로세스는 살아있음)
docker network disconnect fairbid_network redis-master

# Sentinel 로그 확인 (sdown → odown → failover)
docker logs -f sentinel-1

# 새 Master 확인
redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster

# 네트워크 복구
docker network connect fairbid_network redis-master
```

**확인 사항**:
- Sentinel이 감지하는 시간 (down-after-milliseconds)
- failover 완료까지 시간
- docker kill과 비교해서 차이점

**예상 결과**: docker kill보다 감지 시간 더 걸림 (타임아웃 대기)

---

### Step 4-3: 구 Master 복구 시 동작

**목표**: failover 후 원래 Master 재시작 시 정상 동작 확인

**시나리오**:
```bash
# 1. 현재 Master 확인
redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster

# 2. Master 종료 → failover 발생
docker kill redis-master

# 3. 새 Master에 데이터 쓰기
redis-cli -h <새 Master IP> SET after-failover "new-data"

# 4. 구 Master 재시작
docker start redis-master

# 5. 구 Master 역할 확인
redis-cli -p 6379 INFO replication
# role:slave 여야 정상

# 6. 구 Master에서 데이터 확인
redis-cli -p 6379 GET after-failover
```

**확인 사항**:
- 구 Master가 자동으로 Slave가 되는지
- 새 Master의 데이터가 동기화되는지
- 수동 개입 필요 여부

**예상 결과**: Sentinel이 자동으로 구 Master를 Slave로 재구성

---

### Step 5: 클라이언트 튜닝

**목표**: failover 중 앱 동작 확인

**구성** (Spring Boot):
```yaml
spring:
  redis:
    sentinel:
      master: mymaster
      nodes:
        - sentinel-1:26379
        - sentinel-2:26379
        - sentinel-3:26379
    timeout: 3000ms
    lettuce:
      pool:
        max-active: 8
```

**시나리오**:
```bash
# 부하 테스트 중 Master 종료
k6 run scripts/bid-load.js &
docker kill redis-master
```

**확인 사항**:
- failover 중 에러율
- 재연결 자동 여부
- 요청 재시도 동작

**예상 결과**: 일시적 에러 후 자동 복구

---

### Step 6: 모니터링

**목표**: 장애 시 관찰 포인트 정리

**수집 지표**:
```bash
# Redis INFO
redis-cli INFO replication
redis-cli INFO memory
redis-cli INFO stats

# Sentinel 상태
redis-cli -p 26379 SENTINEL master mymaster
```

**확인 사항**:
- Master/Slave 상태
- 메모리 사용량
- 커넥션 수
- replication lag

---

## 4. Stream/Consumer 장애 대응 (가볍게)

### Step 1: 현재 상태 확인

**목표**: 단일 Consumer 한계 인식

**시나리오**:
```bash
# Consumer 강제 종료
docker kill fairbid-app

# Stream 길이 확인
redis-cli XLEN bid-stream
```

**확인 사항**:
- 메시지 쌓이는지 확인
- 재시작 후 처리 여부

---

### Step 2: Consumer Group 구성

**목표**: 다중 Consumer로 분산 처리

**구성**:
```bash
# Consumer Group 생성
redis-cli XGROUP CREATE bid-stream bid-group $ MKSTREAM
```

**시나리오**:
```bash
# Consumer 2개 실행
java -jar app.jar --consumer.id=1 &
java -jar app.jar --consumer.id=2 &

# 하나 종료
kill -9 <consumer-1-pid>

# 메시지 처리 확인
redis-cli XPENDING bid-stream bid-group
```

**확인 사항**:
- 다른 Consumer가 처리하는지
- Pending 메시지 재처리 여부

---

### Step 2-1: Pending 메시지 재처리 (XCLAIM)

**목표**: 죽은 Consumer의 메시지를 다른 Consumer가 가져가기

**배경**:
```
Consumer1이 메시지 읽음 (XREADGROUP)
→ pending 상태 (아직 XACK 안 함)
→ Consumer1 죽음
→ 메시지는 Consumer1한테 할당된 채로 방치
→ Consumer2는 이 메시지 못 봄
```

**시나리오**:
```bash
# 1. 메시지 발행
redis-cli XADD bid-stream '*' bidId "123" amount "10000"

# 2. Consumer1이 읽기 (XACK 안 함)
redis-cli XREADGROUP GROUP bid-group consumer1 COUNT 1 STREAMS bid-stream >

# 3. Consumer1 죽음 시뮬레이션 (그냥 안 씀)

# 4. Pending 메시지 확인
redis-cli XPENDING bid-stream bid-group

# 5. Consumer2가 오래된 pending 메시지 가져오기
# 60000ms(1분) 이상 pending인 메시지를 consumer2가 가져감
redis-cli XAUTOCLAIM bid-stream bid-group consumer2 60000 0-0

# 6. Consumer2가 처리 후 XACK
redis-cli XACK bid-stream bid-group <message-id>
```

**확인 사항**:
- XAUTOCLAIM으로 메시지 소유권 이전되는지
- 이전 후 Consumer2가 처리 가능한지
- 원래 Consumer1의 pending 목록에서 사라지는지

**예상 결과**: 일정 시간 후 다른 Consumer가 orphan 메시지 처리 가능

---

### Step 2-2: 자동 Claim 로직 구현

**목표**: 애플리케이션에서 주기적으로 orphan 메시지 처리

**구현**:
```java
@Scheduled(fixedDelay = 30000) // 30초마다
public void claimOrphanMessages() {
    // 60초 이상 pending인 메시지 가져오기
    List<MapRecord<String, Object, Object>> claimed =
        redisTemplate.opsForStream().autoClaim(
            "bid-stream",
            "bid-group",
            "consumer-" + instanceId,
            Duration.ofSeconds(60),
            "-"  // 처음부터
        );

    for (MapRecord<String, Object, Object> record : claimed) {
        processMessage(record);  // 처리
        acknowledge(record);      // XACK
    }
}
```

**시나리오**:
```bash
# Consumer1 강제 종료 (XACK 전)
kill -9 <consumer1-pid>

# 30초 대기

# Consumer2 로그 확인 (claim 발생하는지)
```

**확인 사항**:
- 스케줄러가 orphan 메시지 감지하는지
- 중복 처리 방지 (멱등성)와 함께 동작하는지

**예상 결과**: Consumer 장애 시에도 메시지 유실 없음

---

### Step 3: 멱등성 처리

**목표**: 중복 처리 방지

**구현**:
```java
// unique key로 중복 체크
if (bidRepository.existsByBidId(bidId)) {
    return; // 이미 처리됨
}
```

**시나리오**:
```bash
# Consumer 처리 중 강제 종료 (XACK 전)
# 재시작 후 같은 메시지 재처리 시도

# RDB 중복 데이터 확인
SELECT COUNT(*) FROM bid WHERE bid_id = 'xxx';
```

**확인 사항**:
- 중복 저장 방지 여부

---

## 5. RDB 장애 대응 (가볍게)

### Step 1: 현재 상태 확인

**목표**: 단일 DB 한계 인식

**시나리오**:
```bash
# DB 정지
docker pause fairbid-mysql

# Consumer 로그 확인 (저장 실패)
```

**확인 사항**:
- 영속화 실패 로그
- Stream에 메시지 남아있는지

---

### Step 2: Replication 구성

**목표**: Master-Slave 구성

**구성**:
```yaml
# docker-compose.yml
services:
  mysql-master:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: root
    command: --server-id=1 --log-bin=mysql-bin
    
  mysql-slave:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: root
    command: --server-id=2 --relay-log=relay-bin
```

**시나리오**:
```bash
# 데이터 동기화 확인
mysql -h master -e "INSERT INTO bid VALUES (...)"
mysql -h slave -e "SELECT * FROM bid"

# Master 정지
docker pause mysql-master

# Slave 데이터 확인
mysql -h slave -e "SELECT * FROM bid"
```

**확인 사항**:
- 데이터 동기화 여부
- Master 장애 시 Slave 데이터 보존

---

## 6. 구현 계획

### 6.1 브랜치 전략
```
main
├── feat/ha-redis-replication    # Redis Replication
├── feat/ha-redis-sentinel       # Redis Sentinel
├── feat/ha-consumer-group       # Consumer Group
└── feat/ha-rdb-replication      # RDB Replication
```

### 6.2 구현 순서
```
[Phase 1] Redis HA (5일)
├── Step 1: 현재 상태 확인 (0.5일)
├── Step 2: Replication 구성 (1일)
├── Step 3: Persistence 설정 (0.5일)
├── Step 4: Sentinel 구성 (1.5일)
├── Step 5: 클라이언트 튜닝 (1일)
└── Step 6: 모니터링 (0.5일)

[Phase 2] Stream/Consumer (2일)
├── Step 1: 현재 상태 확인 (0.5일)
├── Step 2: Consumer Group (1일)
└── Step 3: 멱등성 처리 (0.5일)

[Phase 3] RDB (1일)
├── Step 1: 현재 상태 확인 (0.5일)
└── Step 2: Replication 구성 (0.5일)
```

---

## 7. 결과 기록 형식

### 7.1 단계별 기록
```markdown
# Step N: {단계명}

## 구성
- docker-compose / config 파일

## 장애 시뮬레이션
- 주입 방법
- 명령어

## 결과
| 지표 | 값 |
|------|---|
| failover 시간 | Nms |
| 에러율 | N% |
| ... | ... |

## 로그 / 스크린샷

## 분석
- 뭘 했고
- 뭐가 일어났고
- 왜 그런지
```

---

## 8. 포트폴리오 스토리라인

### 8.1 전체 흐름
```
[SPOF 인식]
Redis가 메인 DB인데 단일 인스턴스 → 죽으면 서비스 터짐
→ 장애 시뮬레이션으로 증명

[단계별 해결]
Replication → "수동 failover 불편하네"
Sentinel → "자동 failover 편하네"
→ 각 단계에서 불편함 체감 후 다음 단계 필요성 이해

[확장]
Redis 경험을 바탕으로 Consumer, RDB까지 장애 대응 설계
→ 전체 아키텍처에 고가용성 적용
```

### 8.2 면접 예상 질문

| 질문 | 답변 포인트 |
|------|------------|
| Redis Sentinel이 뭐예요? | 자동 failover, 구성 방법, failover 시간 |
| failover 중 요청은 어떻게 돼요? | 일시적 에러, 클라이언트 재연결, 재시도 로직 |
| 왜 Cluster 안 썼어요? | 데이터 규모상 Sentinel로 충분, 오버엔지니어링 방지 |
| 멱등성 어떻게 처리했어요? | unique key 체크, 중복 저장 방지 |
| Split Brain 어떻게 방지해요? | min-replicas-to-write 설정, 고립된 Master 쓰기 거부 |
| Consumer 죽으면 그 메시지는요? | XAUTOCLAIM으로 다른 Consumer가 가져감, 멱등성으로 중복 방지 |
| failover 후 구 Master 켜면요? | Sentinel이 자동으로 Slave로 재구성, 데이터 동기화 |

---

## 예상 vs 현실 (테스트 후 작성 예정)

> 이 섹션은 테스트 실행 후 "계획과 달랐던 점"을 기록할 공간입니다.

| 항목 | 예상 | 실제 | 배운 점 |
|------|------|------|---------|
| TBD | - | - | - |

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2025-02-02 | 1.0 | 초안 작성 |
| 2026-02-05 | 1.1 | 계획 문서임을 명시, 예상vs현실 섹션 추가 |