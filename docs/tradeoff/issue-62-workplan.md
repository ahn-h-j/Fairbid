# Issue #62 작업 계획 (Work Plan)

> 컨텍스트 복구용 문서. 커밋 대상 아님.

---

## 핵심 프레이밍 (2026-02-07 업데이트)

```
[기존 - 약함]
"동기 방식에서 DB 장애 시 20건 유실"
→ "0.018%밖에 안 되는데 MQ까지?"

[변경 - 강함]
"동기 방식에서 DB 장애 시 전체 API 마비 (장애 전파)"
→ @Transactional이 메서드 진입 시 DB 커넥션 획득
→ DB 장애 시 Redis 작업조차 블로킹됨
→ 핵심 수치: p95 급증 (50ms → 3000ms), TPS 급락
```

**스토리라인**:
| Phase | 문제점 | 핵심 수치 |
|-------|--------|----------|
| 1. 동기 | DB 장애 → 전체 API 마비 | p95 급증, TPS 급락 |
| 2. @Async | 장애 격리 O, 앱 종료 시 유실 | N건 영구 유실 |
| 3. MQ | 장애 격리 + 복구 가능 | 유실 0, 자동 복구 |

---

## 현재 진행 상태

- [x] Step 1: 브랜치 전환 + 관측 코드 구현
- [x] Step 2: k6 스크립트 작성
- [x] Step 3: 빌드 + 실행 확인
- [x] Step 4: Phase 1 Baseline 측정 (173,514건, 0 불일치)
- [x] Step 5: Phase 1 장애 주입 테스트 (112,368건, 20건 불일치)
- [ ] **Step 6: Phase 1 결과 문서 작성 ← 현재 진행 중**
  - Grafana에서 p95, TPS 수치 확인 필요
  - 장애 전파 관점으로 분석 문서 작성
- [ ] Step 7: @Async 전환 구현
- [ ] Step 8: Phase 2 Baseline + 테스트 2 실행
- [ ] Step 9: Phase 2 결과 문서 작성

---

## Step 1: 브랜치 전환 + 관측 코드 구현 ✅

- `chore/62-async-sync-test` 체크아웃
- BidService에 Micrometer 커스텀 메트릭 추가
  - `fairbid_bid_total` (Counter, tag: result=success/fail)
  - `fairbid_bid_rdb_sync_seconds` (Timer)
- 정합성 체크 스케줄러 추가 (BidConsistencyChecker)
  - `fairbid_bid_redis_count` (Gauge)
  - `fairbid_bid_rdb_count` (Gauge)
  - `fairbid_bid_inconsistency_count` (Gauge)
- Grafana 대시보드 JSON에 패널 추가
- 커밋

## Step 2: k6 스크립트 작성 ✅

- `k6/scenarios/bid-sync-test.js` 작성
  - setup()에서 테스트 경매 생성
  - 1000 VUs, 120초 부하
  - handleSummary()에서 Redis-RDB 정합성 비교
- `k6/scripts/run-phase1-test.sh` 작성
  - Baseline 60초 → 장애 20초 → 복구 40초
  - docker stop/start로 장애 주입
  - Grafana Annotation 자동화
- 커밋

## Step 3: 빌드 + 실행 확인 ✅

- `docker-compose up -d`
- Grafana (localhost:3001) 접속 → 패널 확인
- load-test 프로파일로 인증 우회 설정

## Step 4: Phase 1 Baseline 측정 ✅

결과:
- 총 입찰: 173,514건
- Redis-RDB 불일치: 0건
- TPS: ~992 req/s
- p95: 확인 필요 (Grafana)

## Step 5: Phase 1 장애 주입 테스트 ✅

실행: `bash k6/scripts/run-phase1-test.sh`

결과:
- 총 입찰: 112,368건 (Redis) / 112,348건 (RDB)
- 불일치: 20건 (0.018%) ← **이건 부차적**
- HTTP 5xx: 800건
- **핵심**: p95, TPS 변화 확인 필요 (Grafana)

변경사항:
- docker pause → docker stop (커넥션 완전 끊기)
- socketTimeout: 3000ms 설정

## Step 6: Phase 1 결과 문서 작성 ← 현재

**할 일**:
1. Grafana에서 장애 구간(60~80초) 수치 확인
   - Baseline p95 vs 장애 중 p95
   - Baseline TPS vs 장애 중 TPS
2. `docs/tradeoff/phase1-sync-result.md` 작성
   - 핵심: "20건 유실"이 아닌 "장애 전파"
   - 수치: p95 Nms → Nms (N배 증가)
   - 수치: TPS N → N 급락
3. Grafana 스크린샷 첨부

**분석 포인트**:
```
[장애 전파 증명]
1. @Transactional → 메서드 진입 시 DB 커넥션 획득
2. DB 장애 → connection-timeout 3초 블로킹
3. Redis 작업조차 시작 못함
4. 결과: p95 급증, TPS 급락, 전체 서비스 품질 저하
```

## Step 7: @Async 전환 구현

**할 일**:
1. BidAsyncService 생성 (RDB 저장 전용)
2. BidService에서 @Transactional 제거
3. RDB 저장을 @Async로 분리

**예상 코드**:
```java
@Service
public class BidService implements PlaceBidUseCase {
    // @Transactional 제거!

    public Bid placeBid(PlaceBidCommand command) {
        // Redis Lua 실행 (블로킹 없음)
        BidResult result = bidCachePort.placeBidAtomic(...);

        // 비동기로 RDB 저장
        bidAsyncService.saveToRdbAsync(bid);

        return bid;  // 즉시 응답
    }
}

@Service
public class BidAsyncService {
    @Async
    @Transactional
    public void saveToRdbAsync(Bid bid) {
        bidRepository.save(bid);
    }
}
```

## Step 8: Phase 2 Baseline + 테스트 2 실행

**Baseline**:
- @Async 전환 후 정상 상태 부하
- 동기 대비 응답시간 개선 확인
- p95가 DB 지연과 무관해짐

**테스트 2: 앱 강제 종료**:
```bash
# 부하 중 앱 강제 종료
docker kill fairbid-app

# 재시작
docker start fairbid-app

# Redis vs RDB 비교 → 영구 불일치 확인
```

**예상 결과**:
- 장애 격리: ✅ (DB 장애 시에도 p95 유지)
- 메모리 유실: ❌ (앱 종료 시 큐에 있던 N건 영구 손실)

## Step 9: Phase 2 결과 문서 작성

- `docs/tradeoff/phase2-async-result.md` 작성
- 핵심: "장애 격리 성공, 하지만 메모리 유실"
- 이게 @Async → MQ 전환 근거

---

## 참고 파일

- 스펙 문서: `docs/async-rdb-sync-SPEC.md`
- BidService: `backend/src/main/java/com/cos/fairbid/bid/application/service/BidService.java`
- Grafana 대시보드: `monitoring/grafana/provisioning/dashboards/fairbid-dashboard.json`
- k6 스크립트: `k6/scenarios/bid-sync-test.js`
- 장애 주입 스크립트: `k6/scripts/run-phase1-test.sh`
- docker-compose: `docker-compose.yml`
