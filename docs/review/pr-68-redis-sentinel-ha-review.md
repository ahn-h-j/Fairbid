# PR #68 리뷰 분석

> **PR**: Redis Sentinel 기반 고가용성(HA) 인프라 구성 (#67)
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/68
> **리뷰어**: CodeRabbit, Copilot
> **분석일**: 2026-02-20

---

## Major

### 1. min-replicas-to-write 1이 기본 적용되어 Slave 없이 기동 시 쓰기 실패
- **파일**: `docker-compose.yml:67`
- **내용**: Slave 없이 `docker compose up redis backend mysql`만 실행하면 NOREPLICAS로 모든 쓰기 실패
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 .env에 sentinel 프로필이 항상 포함되어 있고, docker compose up 시 slave/sentinel도 함께 기동됨. 개발자가 부분 기동하는 경우에만 문제 발생. HA override 파일 분리 또는 healthcheck 후 CONFIG SET 방식으로 해결 가능.
- **결정**: 거부
- **비고**:

### 2. Redis 컨테이너 자동 감지가 slave/sentinel을 잘못 선택할 수 있음
- **파일**: `k6/scripts/run-ha-step1-spof.sh:30`
- **내용**: `grep -i redis | head -1`이 redis-slave-1, sentinel 등을 먼저 매칭할 위험
- **판정**: ⚠️ 선택적
- **AI 분석**: step1 스크립트에서만 사용. compose 서비스명 기반 필터링(`$DC ps -q redis`)으로 변경하면 안전. 다른 step2/3 스크립트는 이미 `$DC ps -q redis` 사용 중.
- **결정**: 거부
- **비고**:

### 3. 스크립트 중단 시 docker-compose.yml/`.env` 복원 안됨 (trap 핸들러 미적용)
- **파일**: 거의 모든 스크립트 (`step2-speed`, `step2-replication`, `step3-sentinel`, `step3-speed`, `split-brain`, `split-brain-before`)
- **내용**: Ctrl+C 등으로 스크립트 중단 시 .env 수정 상태 잔존, 네트워크 분리 미복구
- **판정**: ⚠️ 선택적
- **AI 분석**: 테스트 스크립트이므로 프로덕션 영향 없음. 다만 수동 복구가 필요해져서 불편. `trap cleanup EXIT` 패턴을 모든 스크립트에 추가하면 안전성 향상.
- **결정**: 거부
- **비고**:

### 4. NEW_MASTER_SVC 미초기화 시 docker exec 실패
- **파일**: `k6/scripts/run-ha-step3-sentinel.sh:188,210`, `split-brain-before.sh:88,113`
- **내용**: failover 미발생 또는 예상 외 IP인 경우 빈 변수로 명령 실행
- **판정**: ⚠️ 선택적
- **AI 분석**: Sentinel failover가 실패하면 테스트 자체가 무의미한 상태. 가드 조건 추가하면 에러 메시지가 명확해짐.
- **결정**: 거부
- **비고**:

### 5. min-replicas-to-write 복원이 구 Master(현재 Slave)에 적용됨
- **파일**: `k6/scripts/run-ha-step3-split-brain-before.sh:152`
- **내용**: Sentinel failover 후 구 Master는 Slave로 전환. 새 Master에 CONFIG SET 해야 함
- **판정**: ✅ 수용
- **AI 분석**: split-brain-before 스크립트에서 `$DC exec -T redis redis-cli CONFIG SET min-replicas-to-write 1` 실행 시, failover 이후 `redis` 컨테이너는 Slave가 됨. 새 Master 서비스명으로 변경 필요.
- **결정**: 수용
- **비고**:

### 6. Sentinel에 announce-ip 미설정
- **파일**: `redis/sentinel.conf:20`
- **내용**: Docker 환경에서 Sentinel 간 재연결 시 안정적인 IP 광고 필요
- **판정**: ⚠️ 선택적
- **AI 분석**: 고정 IP 네트워크(172.22.0.0/24) 사용 중이라 현재는 문제 없음. 다만 Sentinel이 내부 IP 대신 컨테이너 hostname을 광고할 수 있어, 명시적 announce-ip 설정이 안전.
- **결정**: 거부
- **비고**:

### 7. AUCTION_KEY 빈 값 처리 누락
- **파일**: `k6/scripts/run-ha-step3-split-brain.sh:54`
- **내용**: 경매가 없는 상태에서 실행 시 빈 키로 Redis 명령 실행
- **판정**: ⚠️ 선택적
- **AI 분석**: k6 부하 40초 후 조회하므로 정상 상황에서는 항상 키가 존재. 방어 코드 추가하면 에러 메시지 명확해짐.
- **결정**: 수용
- **비고**:

---

## Minor

### 8. SPEC 문서와 실제 구현 불일치
- **파일**: `docs/tradeoff/high-availability-SPEC.md:365,387`
- **내용**: SPEC은 호스트명 사용, 실제 구현은 고정 IP. SPEC 코드 예시에 @Profile("sentinel"), ReadFrom.MASTER 누락
- **판정**: ✅ 수용
- **AI 분석**: 문서와 구현 동기화 필요. 구현이 완료된 상태이므로 문서를 실제 코드에 맞게 업데이트해야 함.
- **결정**: 수용
- **비고**:

### 9. 복구 폴링 타임아웃 불일치
- **파일**: `k6/scripts/run-ha-step2-replication.sh:116`
- **내용**: step2는 30초, 다른 스크립트는 90초 사용
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: step2는 수동 failover라 복구가 빠름. 다만 통일하면 유지보수 용이.
- **결정**: 거부
- **비고**:

### 10. 경매 키 출력에 "auction:" 접두사 중복
- **파일**: `k6/scripts/run-ha-step2-replication.sh:138`
- **내용**: `AUCTION_KEY`가 이미 `auction:...` 형태인데 `auction:${AUCTION_KEY}` 출력
- **판정**: ✅ 수용
- **AI 분석**: 출력 오류. 수정 간단.
- **결정**: 수용
- **비고**:

### 11. "Step 4(Sentinel)" 오타
- **파일**: `k6/scripts/run-ha-step2-replication.sh:157`
- **내용**: "Step 3(Sentinel)"이 맞음
- **판정**: ✅ 수용
- **AI 분석**: 오타 수정.
- **결정**: 수용
- **비고**:

### 12. k6 시나리오 시간(120초)과 테스트 타임라인 불일치
- **파일**: `k6/scripts/run-ha-step2-speed.sh:33`
- **내용**: Baseline 40초 + 복구 폴링 90초 = 130초인데 k6는 120초
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: k6가 먼저 끝나도 테스트 결과에는 영향 없음. k6 메트릭이 복구 구간 전체를 포함하지 못할 뿐.
- **결정**: 거부
- **비고**:

### 13. sed -i macOS/Linux 호환성
- **파일**: 모든 스크립트의 `sed -i` 사용처
- **내용**: macOS BSD sed는 `-i` 뒤에 백업 확장자 필수
- **판정**: ❌ 거부
- **AI 분석**: 실행 환경이 Ubuntu(WSL2/원격 서버)로 고정. macOS 지원 불필요.
- **결정**: 거부
- **비고**:

### 14. Markdown 코드 블록 언어 지정자 누락
- **파일**: `docs/tradeoff/high-availability-SPEC.md:15,25,460,467,517`
- **내용**: markdownlint 경고
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 가독성 개선. 필수는 아님.
- **결정**: 거부
- **비고**:

### 15. Redis/Sentinel 비밀번호 미설정
- **파일**: `backend/src/main/resources/application-sentinel.yml:19`
- **내용**: 비밀번호 미사용. 향후를 위해 환경변수 기반 설정 추가 권장
- **판정**: ❌ 거부
- **AI 분석**: Docker 내부 네트워크에서만 접근 가능. 현재 스코프에서 불필요.
- **결정**: 거부
- **비고**:

---

## Nitpick

### 16. Trend import 미사용
- **파일**: `k6/scenarios/ha-redis-spof.js:19`
- **내용**: `Trend`가 import되었지만 사용되지 않음
- **판정**: ✅ 수용 (Nitpick)
- **AI 분석**: 불필요한 import. 삭제 한 줄이면 끝.
- **결정**: 수용
- **비고**:

### 17. set -e 일관성
- **파일**: `k6/scripts/run-ha-step1-spof.sh:1-107`
- **내용**: step1만 set -e 사용, 다른 스크립트에는 없음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: `set -e`는 명령 실패 시 즉시 종료. 테스트 스크립트에서는 중간 실패(Redis 연결 타임아웃 등)가 예상되므로 오히려 없는 게 나을 수 있음. step1에서도 제거하거나, 전체 통일하거나 둘 중 하나.
- **결정**: 거부
- **비고**: 현재 테스트에 문제 없기에 그냥 놔둠

### 18. IP-컨테이너 하드코딩 매핑 반복
- **파일**: 여러 스크립트
- **내용**: 공용 헬퍼 함수로 추출 권장
- **판정**: ❌ 거부
- **AI 분석**: 각 스크립트가 독립 실행 가능해야 하며, 공유 함수 의존성 추가는 오히려 복잡도 증가.
- **결정**: 거부
- **비고**:

### 19. NEW_MASTER_SVC 미사용 변수 (split-brain.sh)
- **파일**: `k6/scripts/run-ha-step3-split-brain.sh:75-79`
- **내용**: 변수 설정만 하고 사용 안 함
- **판정**: ✅ 수용 (Nitpick)
- **AI 분석**: 이전 리팩토링에서 사용처가 제거됨. 변수 할당도 제거하면 깔끔.
- **결정**: 수용
- **비고**:

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 5개 | #5 min-replicas 복원 대상, #8 SPEC 문서 동기화, #10 키 접두사 중복, #11 오타, #16 미사용 import |
| ⚠️ 선택적 | 10개 | #1 min-replicas 기본값, #2 컨테이너 감지, #3 trap 핸들러, #4 변수 가드, #6 announce-ip, #7 빈 키 처리, #9 타임아웃, #12 시간 불일치, #14 마크다운, #17 set -e |
| ❌ 거부 | 3개 | #13 macOS 호환, #15 비밀번호, #18 공용 헬퍼 |
| Nitpick | 1개 | #19 미사용 변수 |

