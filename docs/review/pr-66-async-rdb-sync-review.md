# PR #66 리뷰 분석

> **PR**: feat(bidding): 입찰 RDB 동기화 3단계 진화 (동기 → @Async → Redis Stream)
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/66
> **리뷰어**: CodeRabbit, Copilot
> **분석일**: 2026-02-11

---

## Critical/Major 항목

### 1. BidStreamConsumer @Transactional 미적용 (Spring 프록시 우회)
- **파일**: `BidStreamConsumer.java:194`
- **내용**: `this::onMessage`는 프록시를 우회하여 원본 객체를 참조하므로 `@Transactional`이 실제 적용되지 않음
- **판정**: ✅ 수용
- **AI 분석**: 핵심 버그. @Transactional이 무시되는 상태에서 DB 작업 수행 중. 별도 @Service 빈으로 추출하거나, @Transactional 제거하고 리포지토리 레이어에 위임 필요
- **결정**:
- **비고**:

### 2. ACK 타이밍 문제 (커밋 전 ACK)
- **파일**: `BidStreamConsumer.java:186`
- **내용**: DB save() 후 ACK를 동일 트랜잭션 안에서 수행하면 커밋 시점 예외로 ACK가 먼저 나가 메시지 유실 가능
- **판정**: ✅ 수용
- **AI 분석**: 1번과 연관. 현재 @Transactional 미적용이라 auto-commit 상태이지만, 구조적으로 ACK는 커밋 확인 후 수행해야 함
- **결정**:
- **비고**:

### 3. Stream 발행 실패 무시
- **파일**: `RedisBidStreamAdapter.java:103`, `BidService.java:107`
- **내용**: XADD 실패 시 null 반환이 BidService에서 무시됨. Stream 발행 실패가 RDB 동기화 유실로 이어짐
- **판정**: ✅ 수용
- **AI 분석**: 핵심 데이터 정합성 문제. publishBidSave 반환값 검증 및 실패 시 로그/메트릭/예외 처리 필수
- **결정**:
- **비고**:

### 4. ThreadPoolTaskExecutor 리소스 누수
- **파일**: `BidStreamConsumer.java:162`
- **내용**: executor가 로컬 변수로만 존재하여 destroy()에서 종료되지 않음
- **판정**: ✅ 수용
- **AI 분석**: executor를 필드로 승격시키고 destroy()에서 명시적 종료. 간단한 수정
- **결정**:
- **비고**:

### 5. MKSTREAM 옵션 미사용
- **파일**: `BidStreamConsumer.java:109`
- **내용**: 주석에는 MKSTREAM 자동 생성이라 했으나 실제 미사용. 스트림 키 부재 시 그룹 생성 실패 가능
- **판정**: ✅ 수용
- **AI 분석**: Redis 버전/설정에 따라 스트림 없이 그룹 생성이 실패할 수 있음
- **결정**:
- **비고**:

### 6. bidFailCounter 미사용
- **파일**: `BidService.java:75`
- **내용**: bidFailCounter가 선언/등록만 되고 실제 increment() 호출 없음. Grafana에서 항상 0
- **판정**: ✅ 수용
- **AI 분석**: 명확한 누락. 예외 경로에 bidFailCounter.increment() 추가 필요
- **결정**:
- **비고**:

### 7. DataIntegrityViolationException 과잉 catch
- **파일**: `BidPersistenceAdapter.java:55`
- **내용**: DataIntegrityViolationException을 모두 중복으로 간주. NOT NULL 등 다른 제약 위반도 무시됨
- **판정**: ✅ 수용
- **AI 분석**: 실제 데이터 정합성 버그를 감출 수 있음. unique 위반만 선별 처리 필요
- **결정**:
- **비고**:

### 8. OSIV 설정/주석 불일치
- **파일**: `application.yml:32`
- **내용**: PR 설명에는 OSIV 비활성화가 포함되어 있으나 실제로는 open-in-view: true. 주석도 부정확
- **판정**: ✅ 수용
- **AI 분석**: PR 설명과 실제 코드가 모순. HikariCP 고갈 위험
- **결정**:
- **비고**:

### 9. LoadTestSecurityConfig ADMIN 규칙 누락
- **파일**: `LoadTestSecurityConfig.java:53`
- **내용**: 프로덕션 SecurityConfig 대비 ADMIN 접근 제어 규칙 누락
- **판정**: ✅ 수용
- **AI 분석**: load-test 프로필이 실수로 프로덕션에 활성화될 경우 ADMIN 엔드포인트가 무방비
- **결정**:
- **비고**:

### 10. switch문 NPE
- **파일**: `BidStreamConsumer.java:170-193`
- **내용**: type이 null일 경우 switch문에서 NPE 발생
- **판정**: ✅ 수용
- **AI 분석**: 간단한 null 가드 추가로 방어 가능. PENDING 재처리 시 손상 메시지 대응
- **결정**:
- **비고**:

### 11. run-phase3-fault.sh 컨테이너 이름 하드코딩 버그
- **파일**: `k6/scripts/run-phase3-fault.sh:132`
- **내용**: 수렴 폴링에서 MySQL 컨테이너 이름이 하드코딩. 자동 감지 변수와 불일치
- **판정**: ✅ 수용
- **AI 분석**: 명백한 버그. ${MYSQL_CONTAINER} 변수를 사용하도록 수정 필요
- **결정**:
- **비고**:

### 12. run-phase1-test.sh set -e 문제
- **파일**: `k6/scripts/run-phase1-test.sh:15`
- **내용**: set -e로 인해 k6 실패 시 정합성 검증이 스킵됨
- **판정**: ✅ 수용
- **AI 분석**: 장애 주입 테스트에서 k6 실패는 예상 시나리오. wait에 || true 추가 필수
- **결정**:
- **비고**:

---

## 선택적 항목

### 13. @EnableMethodSecurity 누락
- **파일**: `LoadTestSecurityConfig.java:32`
- **내용**: load-test 프로필에서 @PreAuthorize가 무시됨
- **판정**: ⚠️ 선택적
- **AI 분석**: load-test 프로필은 k6 전용이므로 메서드 보안이 불필요할 수 있음
- **결정**:
- **비고**:

### 14. 로그 인젝션 가능성
- **파일**: `LoadTestUserIdFilter.java:50-52`
- **내용**: 외부 HTTP 헤더 값을 로그에 직접 출력 시 CRLF 로그 인젝션 가능성
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: load-test 프로필 전용이라 실제 위험은 매우 낮음
- **결정**:
- **비고**:

### 15. Redis Stream 자동화 테스트 부재
- **파일**: `BidStreamConsumer.java:100`
- **내용**: 멱등 저장, ACK 보류, XCLAIM 재처리, 중복 방지 등 자동화 테스트 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: 후속 이슈로 등록 권장. 현 PR 범위에서 Testcontainers 기반 테스트까지는 부담
- **결정**:
- **비고**:

### 16. 메시지 필드 누락 시 NPE
- **파일**: `BidStreamConsumer.java:200-209`
- **내용**: Long.parseLong(null)에서 NPE 발생 가능
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 프로듀서가 형식 제어하므로 정상 상황에서는 문제 없으나 방어 로직 추가하면 좋음
- **결정**:
- **비고**:

### 17. saveIdempotent() 매핑 경로 불일치
- **파일**: `BidPersistenceAdapter.java:41-47`
- **내용**: BidMapper 대신 직접 BidEntity.builder() 사용. save()와 매핑 경로 불일치
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: streamRecordId는 Stream 컨슈머에서만 사용하므로 당장은 문제 없음
- **결정**:
- **비고**:

### 18. publishInstantBuyUpdate 파라미터 7개 과다
- **파일**: `RedisBidStreamAdapter.java:62-78`, `BidStreamPort.java:39-42`
- **내용**: DTO/record 클래스로 묶기 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 당장 기능에 영향 없음. 리팩터링 시 정리
- **결정**:
- **비고**:

### 19. Lua/XADD 간극에서 Stream 누락 가능성
- **파일**: `BidService.java:105-106`
- **내용**: Redis 캐시와 XADD 사이에서 Redis 장애 시 입찰이 캐시에만 존재
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: BidConsistencyChecker가 감지 가능. 운영 알림 임계값 설정으로 대응
- **결정**:
- **비고**:

### 20. BidConsistencyChecker 5초 COUNT(*) 부하
- **파일**: `BidConsistencyChecker.java:75-96`
- **내용**: 5초마다 SELECT COUNT(*) FROM bid는 풀 테이블 스캔
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 프로파일 기반 활성화 또는 주기 외부화로 개선 가능
- **결정**:
- **비고**:

### 21. 불일치 WARN 로그 노이즈
- **파일**: `BidConsistencyChecker.java:86-88`
- **내용**: 수렴 과정 중 매 5초마다 WARN 반복 출력
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 임계치 기반 로깅으로 개선 가능
- **결정**:
- **비고**:

### 22. readOnly 트랜잭션 내 managed 엔티티 변경
- **파일**: `AuctionService.java:126-131`
- **내용**: readOnly = true 트랜잭션 내에서 currentPrice 변경. OSIV/flush mode 변경 시 예기치 않은 UPDATE 위험
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재는 readOnly라 DB 반영 안 되지만 의도가 모호. DTO projection 시점에서 오버레이가 더 안전
- **결정**:
- **비고**:

### 23. Redis Pipeline 미사용
- **파일**: `RedisAuctionCacheAdapter.java:63-82`
- **내용**: 개별 Redis 호출 대신 Pipeline 사용 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 페이지 크기 10~20이면 영향 미미. 후속 최적화로 적용 가능
- **결정**:
- **비고**:

### 24. workplan.md 상태 미갱신
- **파일**: `docs/tradeoff/issue-62-workplan.md:42`
- **내용**: Step 6 "현재 진행 중", Step 7~9 미완료이나 실제 Phase 3까지 구현 완료
- **판정**: ⚠️ 선택적
- **AI 분석**: 문서를 유지할 경우 업데이트 필요하나 파일 자체를 제외할 수도 있음
- **결정**:
- **비고**:

### 25. docker exec \r 문제
- **파일**: `k6/scripts/check-consistency.sh:37`
- **내용**: redis-cli 출력에 \r 포함 시 산술 연산 실패
- **판정**: ✅ 수용
- **AI 분석**: Windows/Docker 환경에서 흔한 문제. `tr -d '\r'` 추가로 해결
- **결정**:
- **비고**:

### 26. DIFF 음수 메시지 부정확
- **파일**: `k6/scripts/check-consistency.sh:62`
- **내용**: RDB가 더 많은 경우 "Redis가 -N건 더 많음" 출력
- **판정**: ✅ 수용
- **AI 분석**: 간단한 분기 처리로 정확한 메시지 출력 가능
- **결정**:
- **비고**:

### 27. phase1-fault-inject.sh docker pause 실패 무음
- **파일**: `k6/scripts/phase1-fault-inject.sh:16`
- **내용**: docker pause 실패 시 무음 처리. 장애 주입 미적용 인지 불가
- **판정**: ✅ 수용
- **AI 분석**: 장애 주입 없이 테스트 진행되면 결과가 무의미
- **결정**:
- **비고**:

---

## 거부 항목

### 28. ddl-auto: update 위험
- **파일**: `application.yml:28-29`
- **내용**: 프로덕션에서 의도치 않은 스키마 변경 위험. validate + 마이그레이션 도구 권장
- **판정**: ❌ 거부
- **AI 분석**: 프로젝트가 ddl-auto: create-drop 기반. 프로덕션 배포 전 단계이므로 오버엔지니어링
- **결정**:
- **비고**:

### 29. KEYS 명령어 O(N)
- **파일**: `k6/scripts/check-consistency.sh:22-24`
- **내용**: KEYS "auction:*"는 O(N) 블로킹. SCAN 기반 전환 권장
- **판정**: ❌ 거부
- **AI 분석**: 테스트 스크립트 전용. 키 수가 적고 프로덕션 Redis 아님
- **결정**:
- **비고**:

---

## Nitpick (문서화 불필요 수준)

- FQN 대신 import 사용 (`AuctionService.java:119`, `AuctionCachePort.java:38`)
- k6 주석/설정 불일치 (`bid-constant.js:28`, `bid-sync-test.js:12,206`)
- 미사용 import/변수 (`bid-sync-test.js:17,25`, `run-phase3-baseline.sh:29`)
- workplan.md "커밋 대상 아님" 문구 (`issue-62-workplan.md:3`)
- BID 테이블 streamRecordId 컬럼 schema.md 미반영 (`schema.md:72-82`)
- Grafana 인증 하드코딩 (`run-phase1-test.sh:19-20`)
- 쉘 스크립트 중복 함수 (`run-phase3-fault.sh:37-46`, `run-phase3-baseline.sh:42-80`)
- 컨테이너 이름 하드코딩 (`run-phase3-kill.sh:126-138`)
- jq 미사용 (`grafana-annotation.sh:16-21`)

---

## 요약

| 판정 | 개수 | 주요 항목 |
|------|------|----------|
| ✅ 수용 | 17개 | @Transactional 미적용, Stream 발행 실패 무시, OSIV 불일치, bidFailCounter 누락 |
| ⚠️ 선택적 | 12개 | 파라미터 과다, Pipeline, readOnly 엔티티 변경, 테스트 부재 |
| ❌ 거부 | 2개 | ddl-auto, KEYS 명령어 |

---

## 반영 계획

### Backend
1. #1,#2 - `BidStreamConsumer`: @Transactional 적용을 위해 메시지 처리를 별도 빈으로 추출, ACK 타이밍 수정
2. #3 - `RedisBidStreamAdapter` + `BidService`: Stream 발행 실패 시 처리 (로그/메트릭/fallback)
3. #4 - `BidStreamConsumer`: executor 필드 승격 + destroy() 종료
4. #5 - `BidStreamConsumer`: MKSTREAM 옵션 명시적 사용
5. #6 - `BidService`: bidFailCounter.increment() 추가
6. #7 - `BidPersistenceAdapter`: unique 위반만 선별 catch
7. #8 - `application.yml`: OSIV false로 변경 + 주석 수정
8. #9 - `LoadTestSecurityConfig`: ADMIN 접근 제어 규칙 추가
9. #10 - `BidStreamConsumer`: type null 가드 추가

### k6 Scripts
1. #11 - `run-phase3-fault.sh`: ${MYSQL_CONTAINER} 변수 사용
2. #12 - `run-phase1-test.sh`: k6 wait에 || true 추가

### Docs
1. Nitpick - `schema.md`: streamRecordId 컬럼 추가
2. Nitpick - k6 주석/설정 값 일치시키기
3. Nitpick - FQN → import 정리
