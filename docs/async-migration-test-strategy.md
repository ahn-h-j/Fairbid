# 비동기 RDB 동기화 테스트 전략

> 동기 방식의 **장애 상황 일관성 문제**를 테스트로 증명하고, 비동기 전환 시 @Async vs Redis Stream 중 최적의 솔루션을 선택하기 위한 전략

---

## 1. 현재 아키텍처 분석

### 1.1 현재 입찰 플로우 (동기)

```
┌─────────────────────────────────────────────────────────────────┐
│  BidService.placeBid()  @Transactional                          │
│                                                                 │
│  ├─ [1] Redis Lua 스크립트 실행        ◀── 트랜잭션 밖 (즉시 반영)
│  ├─ [2] Spring Event 발행 (WebSocket)                           │
│  ├─ [3] auctionRepository.update()     ◀── 트랜잭션 안          │
│  └─ [4] bidRepository.save()           ◀── 트랜잭션 안          │
│                                                                 │
│  예외 발생 시: RDB 롤백, 하지만 Redis는 이미 반영됨              │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 현재 성능

- **TPS 1,000 처리 가능**
- **2분에 10만건 처리**
- **성능상 문제 없음**

### 1.3 실제 문제점: 장애 상황 일관성

| 상황 | Redis | RDB | HTTP 응답 | 문제 |
|------|-------|-----|-----------|------|
| 정상 | ✅ 성공 | ✅ 성공 | 200 | 없음 |
| **DB 장애** | ✅ 성공 | ❌ 실패 | **500** | **불일치 + 잘못된 응답** |

```
사용자: "입찰 실패했네" (HTTP 500 받음)
Redis: "입찰 성공했는데?" (이미 반영됨)
→ Redis-RDB 불일치 + 사용자 혼란
```

---

## 2. 비교 대상 정의

### 2.1 비교 아키텍처

| 구분 | 방식 | 특징 |
|------|------|------|
| **Baseline** | 동기 (현재) | RDB write가 요청 스레드에서 실행 |
| **Option A** | @Async + ThreadPool | Spring 비동기, 별도 스레드풀에서 RDB write |
| **Option B** | Redis Stream | 이미 사용 중인 Redis 활용, Consumer Group |
| **Option C** | Apache Kafka | 높은 처리량, 강력한 내구성, 순서 보장 |
| **Option D** | RabbitMQ | 유연한 라우팅, 낮은 지연, 간단한 설정 |

### 2.2 비교 플로우 (비동기 전환 후)

```
┌─────────────────────────────────────────────────────────────────┐
│  BidService.placeBid()                                          │
│  ├─ [1] Redis Lua 스크립트 실행              ~1-2ms             │
│  ├─ [2] 메시지 발행 (Stream/Kafka/RabbitMQ)  ~1-3ms             │
│  └─ [3] HTTP 응답 반환                                          │
└─────────────────────────────────────────────────────────────────┘
                      (총 지연: 2-5ms)  ◀── 응답 시간 대폭 단축

                    ┌──────────────────┐
                    │  Message Broker  │
                    └────────┬─────────┘
                             │ (비동기)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  Consumer (별도 프로세스/스레드)                                 │
│  ├─ 메시지 수신                                                  │
│  ├─ auctionRepository.updateCurrentPrice()                      │
│  ├─ bidRepository.save()                                        │
│  └─ ACK (성공 시) / NACK + 재시도 (실패 시)                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 도메인 기반 요구사항 분석

경매 도메인 특성상 필요한 요구사항을 정의합니다.

### 3.1 순서 보장 (Ordering)

```
시나리오: 경매 A에 User1이 10,000원, User2가 11,000원 순서로 입찰

Redis 기록: User1(10,000) → User2(11,000)  ✓ Lua 스크립트로 원자적 보장
RDB 기록:   User2(11,000) → User1(10,000)  ✗ 비동기 시 순서 역전 가능
```

**요구사항**: 같은 경매에 대한 입찰은 **순서 보장 필요**
- 분쟁 시 입찰 이력이 증거로 사용됨
- 순서가 뒤바뀌면 "내가 먼저 입찰했다" 분쟁 발생 가능

**해결책**: Partition Key = auctionId (같은 경매는 같은 파티션/큐로)

### 3.2 지연 허용 범위 (Latency Tolerance)

```
┌────────────────────────────────────────────────────────────────┐
│                       경매 타임라인                             │
├────────────────────────────────────────────────────────────────┤
│  입찰 발생 ──────────────────────────────────► 경매 종료        │
│      │                                            │            │
│      │◄─── 지연 허용 구간 ───►│                   │            │
│      │                        │                   │            │
│   Redis                    RDB 반영            낙찰 처리        │
│   기록                     (비동기)           (RDB 기준)        │
└────────────────────────────────────────────────────────────────┘
```

**요구사항**: 경매 종료 전까지 RDB 동기화 완료
- 최소 24시간 경매이므로 **30초 이내** 동기화면 충분
- 단, 즉시구매(1시간 제한)나 연장(5분) 상황에서는 더 빠른 동기화 필요
- **권장: p99 기준 5초 이내**

### 3.3 데이터 손실 허용 범위 (Durability)

**요구사항**: 입찰 데이터 손실 불가
- Redis가 원본이지만, RDB는 영구 저장소
- 메시지 유실 시 Redis-RDB 불일치 발생
- **최소 At-Least-Once 보장 필요** (중복은 허용, 유실은 불가)

### 3.4 요구사항 요약

| 요구사항 | 수준 | 이유 |
|----------|------|------|
| 순서 보장 | **필수** (같은 경매 내) | 입찰 이력 증거 |
| 지연 허용 | **5초 이내** (p99) | 즉시구매/연장 대응 |
| 데이터 손실 | **불가** | 금전 거래 관련 |
| 중복 처리 | **허용** (멱등성 구현) | At-Least-Once 허용 |

---

## 4. 측정 지표 정의

### 4.1 성능 지표

| 지표 | 설명 | 측정 방법 | 의미 |
|------|------|----------|------|
| **TPS** | 초당 처리 입찰 수 | k6 RPS 측정 | 시스템 처리 용량 |
| **p50 Latency** | 중간값 응답 시간 | k6 percentile | 일반적인 사용자 경험 |
| **p95 Latency** | 95% 응답 시간 | k6 percentile | 대부분의 사용자 경험 |
| **p99 Latency** | 99% 응답 시간 | k6 percentile | 최악의 사용자 경험 |
| **Error Rate** | 실패율 | k6 error count | 안정성 |

### 4.2 리소스 지표

| 지표 | 설명 | 측정 방법 | 의미 |
|------|------|----------|------|
| **CPU 사용률** | 애플리케이션/브로커 CPU | docker stats | 리소스 효율성 |
| **Memory 사용량** | Heap + 브로커 메모리 | docker stats | 메모리 요구사항 |
| **DB Connection Pool** | 활성 커넥션 수 | HikariCP 메트릭 | DB 부하 분산 효과 |
| **Message Backlog** | 미처리 메시지 수 | 브로커 메트릭 | Consumer 처리 속도 |

### 4.3 내구성 지표

| 지표 | 설명 | 측정 방법 | 의미 |
|------|------|----------|------|
| **메시지 유실률** | 발행 대비 처리 비율 | 카운터 비교 | 데이터 안정성 |
| **중복 처리율** | 중복 메시지 비율 | 멱등성 키 충돌 | At-Least-Once 비용 |
| **순서 역전율** | 순서가 뒤바뀐 비율 | 시퀀스 검증 | 순서 보장 정확도 |

### 4.4 장애 복구 지표

| 지표 | 설명 | 측정 방법 | 의미 |
|------|------|----------|------|
| **복구 시간** | 장애 후 정상화까지 시간 | 타임스탬프 | 서비스 가용성 |
| **장애 중 유실** | 장애 동안 유실된 메시지 | 카운터 비교 | 장애 내성 |
| **Backlog 소진 시간** | 밀린 메시지 처리 시간 | 브로커 메트릭 | 복구 속도 |

---

## 5. 테스트 시나리오

### 5.1 Phase 1: 동기 방식 문제점 증명

#### 테스트 1-1: 응답 시간 병목

**목적**: RDB write가 응답 시간에 미치는 영향 측정

```javascript
// k6 스크립트 개요
export const options = {
  scenarios: {
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '30s', target: 200 },
      ],
    },
  },
};
```

**측정 항목**:
- VU 증가에 따른 p95 latency 변화
- DB 커넥션 풀 사용률
- 에러 발생 시점 (커넥션 풀 고갈)

**예상 결과**:
- VU 100 이상에서 p95 latency 급격히 증가
- 커넥션 풀 고갈 시 타임아웃 에러 발생

#### 테스트 1-2: DB 장애 시 영향

**목적**: RDB 장애가 입찰 기능에 미치는 영향 측정

```bash
# 테스트 중 DB 지연 주입
docker exec mysql tc qdisc add dev eth0 root netem delay 500ms

# 또는 DB 컨테이너 일시 중지
docker pause fairbid-mysql
```

**측정 항목**:
- DB 지연 시 응답 시간 변화
- DB 중단 시 에러율
- Redis 성공 but RDB 실패 건수 (데이터 불일치)

**예상 결과**:
- DB 500ms 지연 → 응답 시간 500ms+ 증가
- DB 중단 → 100% 에러 (Redis 성공분도 롤백)

---

### 5.2 Phase 2: @Async 한계 증명

#### 테스트 2-1: 스레드풀 고갈

**목적**: @Async 스레드풀의 한계점 측정

```java
// 테스트용 설정
@Configuration
public class AsyncConfig {
    @Bean
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        return executor;
    }
}
```

**측정 항목**:
- 스레드풀 사용률
- 큐 대기열 증가 추이
- RejectedExecutionException 발생 시점

**예상 결과**:
- 부하 증가 시 큐가 가득 차서 요청 거부
- 스레드풀 크기 증가는 메모리 비용 증가

#### 테스트 2-2: 장애 시 데이터 유실

**목적**: 애플리케이션 재시작 시 @Async 큐 유실 확인

```bash
# 부하 테스트 중 애플리케이션 강제 종료
docker kill fairbid-app

# 재시작 후 Redis-RDB 데이터 비교
```

**측정 항목**:
- 강제 종료 시 미처리 메시지 수
- Redis vs RDB 레코드 수 불일치

**예상 결과**:
- 메모리 큐에 있던 메시지 전부 유실
- Redis-RDB 데이터 불일치 발생

---

### 5.3 Phase 3: 메시징 시스템 성능 비교

#### 테스트 3-1: 최대 처리량 (TPS)

**목적**: 각 시스템의 최대 처리량 측정

```javascript
// k6 constant-rate 테스트
export const options = {
  scenarios: {
    constant_rps: {
      executor: 'constant-arrival-rate',
      rate: 1000,  // 초당 1000 요청
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
};
```

**측정 항목** (각 시스템별):
- 최대 TPS (에러율 1% 미만 유지 기준)
- 해당 TPS에서의 p99 latency
- 리소스 사용량

#### 테스트 3-2: 지연 시간 분포

**목적**: 각 시스템의 end-to-end 지연 측정

```
측정 구간:
┌─────────┬───────────────┬─────────────┬──────────────┐
│ 입찰 요청 │ 메시지 발행    │ Consumer 수신 │ RDB 저장 완료 │
└────┬────┴───────┬───────┴──────┬──────┴───────┬──────┘
     │            │              │              │
     t0           t1             t2             t3

- 응답 지연: t1 - t0 (사용자 체감)
- 전파 지연: t2 - t1 (브로커 내부)
- 처리 지연: t3 - t2 (Consumer)
- E2E 지연: t3 - t0 (전체)
```

**측정 항목**:
- 응답 지연 (p50, p95, p99)
- E2E 지연 (p50, p95, p99)
- 지연 시간 분포 히스토그램

#### 테스트 3-3: 백프레셔 상황

**목적**: Consumer가 느릴 때 각 시스템의 동작 비교

```java
// Consumer에 인위적 지연 추가
@KafkaListener(...)
public void consume(BidMessage msg) {
    Thread.sleep(100);  // 100ms 처리 지연
    // ...
}
```

**측정 항목**:
- 메시지 백로그 증가 속도
- Producer 영향 (블로킹 여부)
- 메모리 사용량 증가

---

### 5.4 Phase 4: 장애 시나리오 테스트

#### 테스트 4-1: 브로커 장애

**목적**: 메시지 브로커 다운 시 동작 확인

```bash
# 각 브로커 컨테이너 중지
docker stop fairbid-redis     # Redis Stream
docker stop fairbid-kafka     # Kafka
docker stop fairbid-rabbitmq  # RabbitMQ
```

**측정 항목**:
- 브로커 다운 감지 시간
- 다운 중 Producer 동작 (에러? 버퍼링?)
- 복구 후 밀린 메시지 처리

#### 테스트 4-2: Consumer 장애

**목적**: Consumer 크래시 후 재시작 시 동작 확인

```bash
# Consumer 프로세스 강제 종료 후 재시작
docker kill fairbid-consumer
sleep 10
docker start fairbid-consumer
```

**측정 항목**:
- 처리 중이던 메시지 재처리 여부
- 중복 처리 발생 여부
- Consumer Group 리밸런싱 시간 (Kafka)

#### 테스트 4-3: 네트워크 파티션

**목적**: 네트워크 단절 시 데이터 일관성 확인

```bash
# Producer ↔ Broker 네트워크 단절
docker network disconnect fairbid-network fairbid-app
sleep 30
docker network connect fairbid-network fairbid-app
```

**측정 항목**:
- 단절 중 발생한 메시지 유실
- 재연결 후 복구 동작
- 순서 보장 유지 여부

---

## 6. 트레이드오프 비교 프레임워크

### 6.1 정량적 비교표

테스트 결과를 아래 표에 기록합니다.

| 지표 | 동기 (Baseline) | @Async | Redis Stream | Kafka | RabbitMQ |
|------|----------------|--------|--------------|-------|----------|
| **최대 TPS** | - | - | - | - | - |
| **p50 응답 지연** | - | - | - | - | - |
| **p95 응답 지연** | - | - | - | - | - |
| **p99 응답 지연** | - | - | - | - | - |
| **E2E p99 지연** | N/A | - | - | - | - |
| **메시지 유실률** | N/A | - | - | - | - |
| **순서 역전율** | N/A | - | - | - | - |
| **브로커 장애 복구 시간** | N/A | N/A | - | - | - |
| **CPU 사용률 (앱)** | - | - | - | - | - |
| **CPU 사용률 (브로커)** | N/A | N/A | - | - | - |
| **메모리 사용량** | - | - | - | - | - |

### 6.2 정성적 비교표

| 항목 | @Async | Redis Stream | Kafka | RabbitMQ |
|------|--------|--------------|-------|----------|
| **운영 복잡도** | 낮음 (추가 인프라 없음) | 낮음 (기존 Redis 사용) | 높음 (ZK/KRaft, 브로커) | 중간 |
| **학습 곡선** | 낮음 | 중간 | 높음 | 중간 |
| **순서 보장** | 보장 안됨 | 단일 스트림 내 보장 | 파티션 내 보장 | 단일 큐 내 보장 |
| **내구성** | 없음 (메모리) | AOF/RDB 의존 | 강력함 (복제) | 설정에 따라 |
| **수평 확장** | 불가 | 제한적 | 우수 | 우수 |
| **모니터링** | Spring Actuator | Redis CLI | Kafka UI, 풍부 | RabbitMQ UI |
| **에코시스템** | Spring 내장 | 제한적 | 매우 풍부 | 풍부 |
| **포트폴리오 가치** | 낮음 | 중간 | 높음 | 중간 |

### 6.3 의사결정 매트릭스

가중치를 부여한 점수 비교 (1-5점)

| 기준 | 가중치 | @Async | Redis Stream | Kafka | RabbitMQ |
|------|--------|--------|--------------|-------|----------|
| 성능 (TPS) | 25% | - | - | - | - |
| 응답 지연 | 20% | - | - | - | - |
| 데이터 안정성 | 20% | - | - | - | - |
| 운영 복잡도 | 15% | - | - | - | - |
| 장애 복구 | 10% | - | - | - | - |
| 학습/포폴 가치 | 10% | - | - | - | - |
| **총점** | 100% | - | - | - | - |

---

## 7. 테스트 환경 구성

### 7.1 Docker Compose 구성

```yaml
# docker-compose.test.yml
version: '3.8'

services:
  # 기존 서비스
  app:
    build: ./backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=test
    depends_on:
      - mysql
      - redis

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: fairbid
      MYSQL_ROOT_PASSWORD: test
    ports:
      - "3306:3306"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  # 메시징 시스템 (필요에 따라 활성화)
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      CLUSTER_ID: 'fairbid-test-cluster'
    ports:
      - "9092:9092"

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "5672:5672"
      - "15672:15672"

  # 모니터링
  prometheus:
    image: prom/prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana
    ports:
      - "3001:3000"
```

### 7.2 k6 실행 환경

```bash
# k6 설치 (Windows)
choco install k6

# 또는 Docker로 실행
docker run --rm -i grafana/k6 run - < scripts/load-test.js
```

---

## 8. 실행 계획

### Phase 1: 동기 방식 문제점 증명 (1-2일)
- [ ] 테스트 1-1: 응답 시간 병목
- [ ] 테스트 1-2: DB 장애 시 영향
- [ ] 결과 정리 및 문제점 문서화

### Phase 2: @Async 한계 증명 (1일)
- [ ] @Async 적용 브랜치 생성
- [ ] 테스트 2-1: 스레드풀 고갈
- [ ] 테스트 2-2: 장애 시 데이터 유실
- [ ] 결과 정리

### Phase 3: 메시징 시스템 구현 및 테스트 (3-5일)
- [ ] Redis Stream 구현
- [ ] Kafka 구현
- [ ] RabbitMQ 구현
- [ ] 테스트 3-1 ~ 3-3 각 시스템별 실행
- [ ] 결과 비교표 작성

### Phase 4: 장애 시나리오 테스트 (2일)
- [ ] 테스트 4-1 ~ 4-3 각 시스템별 실행
- [ ] 장애 복구 결과 비교

### Phase 5: 최종 분석 및 문서화 (1일)
- [ ] 트레이드오프 분석 완료
- [ ] 최종 선택 및 근거 문서화
- [ ] 포트폴리오용 정리

---

## 9. 예상 결론 (가설)

테스트 전 예상되는 결과입니다. 실제 테스트로 검증합니다.

### 9.1 동기 → 비동기 전환 필요성

| 시나리오 | 동기 | 비동기 |
|----------|------|--------|
| 고부하 (100+ TPS) | DB 커넥션 풀 고갈 | 안정적 처리 |
| DB 지연 | 응답 지연 전파 | 응답 영향 없음 |
| DB 장애 | 서비스 전체 영향 | 입찰은 계속 가능 |

### 9.2 메시징 시스템 예상 특성

| 시스템 | 장점 | 단점 |
|--------|------|------|
| **Redis Stream** | 추가 인프라 불필요, 낮은 지연 | 내구성 제한, 기능 제한 |
| **Kafka** | 높은 처리량, 강력한 내구성 | 운영 복잡, 리소스 많이 사용 |
| **RabbitMQ** | 균형 잡힌 성능, 유연성 | Kafka보다 처리량 낮음 |

### 9.3 최종 선택 예상

**포트폴리오 목적**을 고려할 때:
- **Kafka**: 학습 가치 높음, 대기업에서 많이 사용
- **Redis Stream**: 실용적 선택, 기존 인프라 활용
- **RabbitMQ**: 균형 잡힌 선택

실제 테스트 데이터로 최종 결정합니다.

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-01-28 | 0.1 | 초안 작성 |
