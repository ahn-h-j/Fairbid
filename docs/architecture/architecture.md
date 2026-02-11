# Architecture (아키텍처)

> 경매 프로젝트 헥사고날 아키텍처 설계

---

## 1. 아키텍처 개요

### 스타일
- **헥사고날 아키텍처 (Ports & Adapters)**
- 모놀리식 → 추후 WebSocket(Live Bidding) 분리 예정

### 핵심 원칙
- Domain은 외부 기술(JPA, HTTP, Redis 등)에 의존하지 않는다
- 외부와의 통신은 Port(인터페이스)를 통해서만 한다
- Adapter가 Port를 구현하여 실제 기술과 연결한다

---

## 2. Bounded Context

```
┌─────────────────────────────────────────────────────────────┐
│                      Identity Context                        │
│  - User (계정/인증)                                          │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│               Auction Management Context                     │
│  - Seller (판매자 역할)                                      │
│  - AuctionItem (물품 정보)                                   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                  Live Bidding Context                        │
│  - Buyer (구매자 역할)                                       │
│  - AuctionSession (진행 정보 - 실시간)                       │
│  - Bid (입찰)                                                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   Winning Context                            │
│  - Winning (낙찰)                                            │
│  - 경매 종료 처리, 노쇼 처리, 2순위 승계                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     Trade Context                            │
│  - Transaction (거래)                                        │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    Support Context                           │
│  - Notification (알림, WebSocket, Push)                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 패키지 구조

```
src/main/java/com/cos/fairbid/
├── application/           # Application Layer
│   ├── port/
│   │   ├── in/            # Inbound Port (UseCase 인터페이스)
│   │   └── out/           # Outbound Port (Repository 인터페이스)
│   └── service/           # UseCase 구현체
│
├── domain/                # Domain Layer
│   ├── model/             # Domain Model (순수 비즈니스 로직, POJO)
│   └── exception/         # 도메인 예외
│
└── adapter/               # Adapter Layer
    ├── in/                # Inbound Adapter
    │   ├── controller/    # REST Controller
    │   ├── dto/           # Request/Response DTO
    │   └── monitoring/    # 모니터링 스케줄러 (Micrometer Gauge 등)
    │
    └── out/               # Outbound Adapter
        └── persistence/   # JPA Repository 구현
            ├── entity/    # JPA Entity
            ├── repository/# Spring Data JPA Repository
            └── mapper/    # Entity ↔ Domain 변환
```

---

## 4. 계층별 역할

### Controller (Inbound Adapter)
- HTTP 요청 수신
- Request DTO → UseCase 호출
- Response DTO 반환
- **비즈니스 로직 금지**

---

### UseCase (Port In)
- 애플리케이션 유스케이스 인터페이스 정의
- 하나의 유스케이스 = 하나의 비즈니스 행위

---

### Service (UseCase 구현)
- UseCase 인터페이스 구현
- Domain 객체 조합하여 비즈니스 로직 수행
- Port Out을 통해 외부 리소스 접근

---

### Port Out (Outbound Port)
- 외부 리소스 접근을 위한 인터페이스
- Domain 계층에서 정의

---

### Adapter Out (Outbound Adapter)
- Port Out 구현체
- 실제 기술과 연결 (JPA, Redis, 외부 API 등)

---

### Domain
- 순수 비즈니스 로직
- 외부 기술 의존 없음 (POJO)
- 불변성, 유효성 검증 포함

---

### Entity
- JPA Entity
- DB 테이블 매핑 전용
- 비즈니스 로직 금지

---

### Mapper
- Entity ↔ Domain 변환
- 양방향 변환 메서드 제공

---

### Orchestrator
- 여러 UseCase 조합이 필요한 복잡한 흐름 처리
- 트랜잭션 경계 관리

---

## 5. 계층 간 호출 흐름

```
[HTTP Request]
      │
      ▼
┌─────────────┐
│ Controller  │  ← Inbound Adapter
└─────────────┘
      │ Request DTO → Command
      ▼
┌─────────────┐
│   UseCase   │  ← Port In (Interface)
└─────────────┘
      │
      ▼
┌─────────────┐
│   Service   │  ← UseCase 구현
└─────────────┘
      │ Domain 객체 사용
      ▼
┌─────────────┐
│   Domain    │  ← 비즈니스 로직
└─────────────┘
      │
      ▼
┌─────────────┐
│  Port Out   │  ← Outbound Port (Interface)
└─────────────┘
      │
      ▼
┌─────────────┐
│  Adapter    │  ← Outbound Adapter (JPA, Redis 등)
└─────────────┘
      │
      ▼
┌─────────────┐
│   Entity    │  ← DB 매핑
└─────────────┘
      │
      ▼
[Database / Redis / External API]
```

---

## 6. 의존성 규칙

```
Controller  ──────▶  UseCase (Interface)
                          │
                          ▼
                      Service
                          │
              ┌───────────┼───────────┐
              ▼           ▼           ▼
          Domain    Port Out    Orchestrator
              │      (Interface)      │
              │           │           │
              │           ▼           │
              │       Adapter         │
              │           │           │
              │           ▼           │
              │       Entity          │
              │           │           │
              └───────────┴───────────┘
```

### 규칙
1. **Domain**은 아무것도 의존하지 않는다
2. **Port Out**은 Domain에서 정의하고, Adapter가 구현한다
3. **Service**는 Domain과 Port Out 인터페이스만 의존한다 (구현체 모름)
4. **Controller**는 UseCase 인터페이스만 의존한다
5. **Entity**는 Adapter에서만 사용한다 (Domain으로 올라오지 않음)
6. **Adapter**는 Port를 구현한다. 
7. **Controller**는 UseCase를 호출한다.

### 금지사항 (Forbidden Patterns)
- Controller에서 직접 Repository 호출 금지
- Domain에서 JPA Annotation 사용 금지
- Service에서 Entity 직접 반환 금지
- Mapper 없이 Entity ↔ Domain 직접 변환 금지

---

## 7. 입찰-경매 가격 동기화 전략

> 입찰 성능 최적화를 위해 일반 입찰 시 auction 테이블 UPDATE를 제거하고,
> 경매 목록 조회 시 Redis에서 실시간 가격을 오버레이하는 방식으로 전환.

### 데이터 흐름

```
[입찰 요청]
    │
    ▼
┌─────────────┐
│  BidService  │  Redis Lua 스크립트로 입찰 처리 (currentPrice, totalBidCount 갱신)
└─────────────┘
    │
    ├── Redis (auction:{id} Hash) ← 실시간 가격 반영 (Source of Truth)
    ├── RDB (bid 테이블) ← 입찰 이력 저장
    └── RDB (auction 테이블) ← 즉시 구매 활성화 시에만 UPDATE
                                (일반 입찰 시 UPDATE 하지 않음)

[경매 목록 조회]
    │
    ▼
┌────────────────┐
│ AuctionService │  1. RDB에서 페이지네이션/필터링 조회
└────────────────┘  2. Redis에서 최신 currentPrice 배치 조회 (getCurrentPrices)
    │               3. Redis 가격으로 RDB 결과 덮어쓰기 (오버레이)
    ▼
[클라이언트에 실시간 가격 반영된 목록 응답]
```

### 모니터링

`BidConsistencyChecker` (bid/adapter/in/monitoring/)가 5초마다 Redis vs RDB 입찰 건수를 비교하여
Prometheus Gauge로 노출한다.

| 메트릭 | 타입 | 설명 |
|--------|------|------|
| `fairbid_bid_total` | Counter (tag: result) | 입찰 성공/실패 건수 |
| `fairbid_bid_rdb_sync_seconds` | Timer | RDB 입찰 이력 저장 소요 시간 |
| `fairbid_bid_redis_count` | Gauge | Redis 기준 총 입찰 수 |
| `fairbid_bid_rdb_count` | Gauge | RDB 기준 총 입찰 수 |
| `fairbid_bid_inconsistency_count` | Gauge | Redis-RDB 입찰 건수 차이 |