# PR #30 리뷰 분석

> **PR**: Redis Main DB + Lua Script 기반 입찰 처리 구현
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/30
> **리뷰어**: GitHub Copilot, CodeRabbit
> **분석일**: 2026-01-20

---

## Copilot 리뷰

### 1. Redis 설정 클래스 추가 권장
- **파일**: `backend/build.gradle:39`
- **내용**: Spring Boot 자동 설정에만 의존하지 말고 RedisConfig 클래스를 만들어 직렬화 전략, 커넥션 팩토리, 에러 핸들링을 명시적으로 설정하라는 제안
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 StringRedisTemplate 기본 설정으로 동작하고 있으며, Hash 구조에 String 직렬화를 사용하므로 즉각적인 문제는 없음. 다만 향후 복잡한 객체 저장이나 커넥션 풀 세부 튜닝이 필요할 때 RedisConfig가 유용함
- **결정**: 보류
- **의견**: 아직은 필요 없어 보임 Todo/todo.md에 기록

---

### 2. 캐시 미스 시 Race Condition
- **파일**: `backend/src/main/java/com/cos/fairbid/bid/application/service/BidService.java:42`
- **내용**: 동시에 여러 입찰 요청이 캐시 미스를 감지하면 모두 DB에서 로드하여 중복 쿼리 발생. 분산 락 또는 SETNX 사용 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: 캐시 미스는 경매 시작 직후 첫 입찰 시점에만 발생. 이후에는 Redis에 데이터가 존재하므로 빈번하지 않음. 단, 트래픽이 급증하는 경매 시작 시점에 동시 요청이 몰리면 DB 부하 발생 가능. SETNX 기반 로딩 락이나 캐시 워밍 전략으로 해결 가능
- **결정**: 수용
- **의견**: 경매 생성 시 AfterCommit으로 캐시 워밍, 실패 시 기존 cache-aside가 fallback으로 동작

---

### 3. Redis 비밀번호 설정 누락
- **파일**: `backend/src/main/resources/application.yml:7`
- **내용**: Redis 비밀번호 인증 설정이 없음. 프로덕션 환경에서는 필수
- **판정**: ✅ 수용
- **AI 분석**: 현재 개발/테스트 환경에서는 문제없지만, 프로덕션 배포 시 Redis 인증 없이 노출되면 보안 위협. `SPRING_DATA_REDIS_PASSWORD` 환경변수로 설정 추가 필요
- **결정**: 수용
- **의견**: ai 의견 동의

---

### 4. 캐시 로딩 동기 처리로 인한 성능 저하
- **파일**: `backend/src/main/java/com/cos/fairbid/bid/application/service/BidService.java:83`
- **내용**: 캐시 미스 시 DB 조회가 동기적으로 수행되어 입찰 요청을 블로킹함. 경매가 BIDDING 상태로 전환될 때 미리 Redis에 로드하는 캐시 워밍 전략 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: 캐시 미스는 경매당 최초 1회만 발생. DB 조회 시간이 수십 ms 수준이면 크게 문제되지 않음. 다만 캐시 워밍으로 첫 입찰 응답 시간을 개선할 수 있음
- **결정**: 수용
- **의견**: #2와 동일. 경매 생성 시 AfterCommit으로 캐시 워밍하여 해결

---

### 5. BidAsyncService 중복 메서드 및 미사용
- **파일**: `backend/src/main/java/com/cos/fairbid/bid/application/service/BidAsyncService.java:105-106`
- **내용**: syncToDatabase와 saveBidAndPublishEvent 메서드가 기능 중복. 또한 BidService에서 실제로 사용하지 않아 dead code
- **판정**: ✅ 수용
- **AI 분석**: BidAsyncService는 향후 MQ 기반 비동기 처리를 위해 준비된 코드로 보이나, 현재 BidService에서 동기적으로 RDB 저장을 수행하므로 사용되지 않음. 정리하거나 명확한 용도 문서화 필요
- **결정**: 거부
- **의견**: 향후 기능을 위한 것

---

### 6. AsyncConfig Rejection Policy 부재
- **파일**: `backend/src/main/java/com/cos/fairbid/common/config/AsyncConfig.java:30`
- **내용**: 큐가 가득 차고 스레드가 모두 사용 중일 때 기본 AbortPolicy로 인해 작업이 거부됨. CallerRunsPolicy 설정 및 종료 시 작업 완료 대기 설정 권장
- **판정**: ✅ 수용
- **AI 분석**: 현재 BidAsyncService가 사용되지 않아 당장 문제는 없으나, 향후 비동기 처리 활성화 시 필수. CallerRunsPolicy는 큐 포화 시 호출 스레드가 직접 실행하여 작업 유실 방지
- **결정**: 수용
- **의견**: 영속화를 위한 RDB이지만 확실히 데이터를 기록해야 함

---

### 7. Redis Connection Pool 설정 누락
- **파일**: `backend/src/main/resources/application.yml:7`
- **내용**: Lettuce 커넥션 풀 설정이 없음. 고동시성 입찰 시나리오에 맞게 max-active, max-idle, min-idle 설정 권장
- **판정**: ⚠️ 선택적
- **AI 분석**: Spring Boot 기본값으로도 동작하지만, 부하 테스트 결과에 따라 튜닝이 필요할 수 있음. HikariCP처럼 명시적 설정이 운영 안정성에 도움
- **결정**: 보류
- **의견**: 추후 필요할 때 작업 Todo/todo.md에 작성

---

### 8. 경매 무한 연장 가능성
- **파일**: `backend/src/main/resources/scripts/bid.lua:87`
- **내용**: 연장 윈도우 내 입찰 시 고정 5분 연장. 연속 입찰로 경매가 무한히 연장될 수 있음. 최대 연장 횟수나 총 연장 시간 제한 권장
- **판정**: ❌ 거부
- **AI 분석**: 현재 비즈니스 규칙에 따르면 3회마다 입찰 단위 50% 증가로 연장 남용을 방지. 또한 연장 횟수(extensionCount)를 추적하고 있어 필요시 제한 로직 추가 가능. 현재 요구사항에 최대 연장 제한은 없음
- **결정**: 거부
- **의견**: ai 의견 동의 비즈니스 규칙과 맞지 않음

---

### 9. 경매 종료 시간 검증 누락
- **파일**: `backend/src/main/resources/scripts/bid.lua:38`
- **내용**: status가 BIDDING인지만 확인하고, currentTimeMs가 scheduledEndTimeMs를 초과했는지 검증하지 않음. 상태 업데이트 전 입찰 가능 우려
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 Lua 스크립트에서 연장 윈도우(종료 5분 전) 내 입찰 시 연장 처리를 하고 있어 종료 시간 이후 입찰이 들어와도 연장됨. 단, 스케줄러가 경매를 ENDED로 변경하기 전 짧은 윈도우에서 입찰이 가능할 수 있음. 엄격한 검증이 필요하면 추가 가능
- **결정**: 수용
- **의견**: 종료는 엄격하게 관리되어야 함

---

### 10. Lua 스크립트 pcall 미사용
- **파일**: `backend/src/main/resources/scripts/bid.lua:112`
- **내용**: redis.call 실패 시 스크립트가 중단되어 데이터 불일치 가능. pcall로 에러 핸들링 권장
- **판정**: ❌ 거부
- **AI 분석**: Redis Lua 스크립트는 원자적으로 실행되며, 중간에 실패하면 전체 롤백됨. pcall을 사용하면 오히려 부분 실패를 허용하게 되어 데이터 불일치 위험 증가. 현재 방식이 적절함
- **결정**: 거부
- **의견**: ai 의견 동의

---

## CodeRabbit 리뷰

### 11. 이벤트 발행과 RDB 저장 순서 문제
- **파일**: `backend/src/main/java/com/cos/fairbid/bid/application/service/BidService.java:68`
- **내용**: 현재 웹소켓 이벤트 발행(4단계) 후 RDB 저장(5단계) 순서. RDB 저장 실패 시 클라이언트는 이미 성공 이벤트를 받은 상태. 순서 변경 권장
- **판정**: ✅ 수용
- **AI 분석**: RDB 저장이 실패하면 입찰이 낙찰 후보에서 누락될 수 있음. 영속화 성공 후 이벤트 발행이 더 안전한 순서. 단, 이벤트 발행 실패는 RDB 롤백을 유발하지 않도록 분리 필요
- **결정**: 거부
- **의견**: 메인 DB는 Redis임 RDB는 영속화를 위한것일뿐 Redis에 업데이트 된 후에 웹소켓 이벤트 발행이 맞음

---

## 요약

| 결정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 5개 | #2 캐시 워밍, #3 Redis 비밀번호, #4 캐시 워밍, #6 AsyncConfig 정책, #9 종료시간 검증 |
| ⏸️ 보류 | 2개 | #1 RedisConfig, #7 Connection Pool |
| ❌ 거부 | 4개 | #5 BidAsyncService(향후 기능), #8 무한연장(비즈니스 규칙), #10 pcall(원자성), #11 저장순서(Redis가 메인DB) |
