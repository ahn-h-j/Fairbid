# k6 부하테스트

FairBid 프로젝트의 성능 측정을 위한 k6 부하테스트 스크립트.

## 설치

### k6 설치

**Windows (Chocolatey)**:
```bash
choco install k6
```

**Windows (winget)**:
```bash
winget install k6 --source winget
```

**macOS**:
```bash
brew install k6
```

**Docker**:
```bash
docker pull grafana/k6
```

## 사전 준비

1. 애플리케이션 실행
```bash
docker-compose up -d
```

2. 테스트용 데이터 준비
   - 테스트용 경매 생성 필요
   - 경매 ID를 환경 변수로 전달

## 테스트 실행

### 1. 동시 입찰 경합 테스트
```bash
# 기본 실행
k6 run k6/scenarios/bid-stress.js

# 특정 경매 ID로 실행
k6 run --env AUCTION_ID=1 k6/scenarios/bid-stress.js

# 다른 서버로 실행
k6 run --env BASE_URL=http://your-server:8080 --env AUCTION_ID=1 k6/scenarios/bid-stress.js
```

### 2. WebSocket 동시 연결 테스트
```bash
k6 run --env AUCTION_ID=1 k6/scenarios/websocket-load.js
```

### 3. 복합 부하 테스트
```bash
k6 run k6/scenarios/mixed-load.js
```

## 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `BASE_URL` | API 서버 주소 | `http://localhost:8080` |
| `WS_URL` | WebSocket 서버 주소 | `ws://localhost:8080/ws` |
| `AUCTION_ID` | 테스트 대상 경매 ID | `1` |

## 결과 확인

테스트 결과는 `k6/results/` 디렉터리에 JSON 형식으로 저장됩니다.

```
k6/results/
├── bid-stress-result.json
├── websocket-load-result.json
└── mixed-load-result.json
```

## Grafana 연동

k6는 Prometheus 형식으로 메트릭을 내보낼 수 있습니다.

```bash
# Prometheus Remote Write로 메트릭 전송
k6 run --out experimental-prometheus-rw k6/scenarios/mixed-load.js
```

## 시나리오 설명

### bid-stress.js (동시 입찰 경합)
- **목적**: DB 락 경합 상황에서의 성능 측정
- **패턴**: 점진적 부하 증가 (10 → 50 → 100 → 200 VUs)
- **측정 항목**: 입찰 성공률, 응답 시간, 에러율

### websocket-load.js (WebSocket 동시 연결)
- **목적**: WebSocket 서버 수용량 측정
- **패턴**: 점진적 연결 증가 (50 → 100 → 200 connections)
- **측정 항목**: 연결 성공률, 연결 시간, 메시지 수신 수

### mixed-load.js (복합 시나리오)
- **목적**: 실제 사용 패턴 시뮬레이션
- **구성**:
  - 경매 목록 조회: 100 VUs (읽기 부하)
  - 입찰: 20 VUs (쓰기 부하)
  - WebSocket 구독: 50 connections
- **측정 항목**: 전체 시스템 처리량, 시나리오별 응답 시간
