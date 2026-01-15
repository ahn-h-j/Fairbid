# PR #27 리뷰 분석

> **PR**: [CHORE] 모니터링 및 k6 부하테스트 환경 구축
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/27
> **리뷰어**: CodeRabbit, GitHub Copilot
> **분석일**: 2026-01-15

---

## Copilot 리뷰

### 1. bid-stress.js 조건문 연산자 우선순위 문제
- **파일**: `k6/scenarios/bid-stress.js:140-153`
- **내용**: `success && res.status === 200 || res.status === 201` 조건문이 연산자 우선순위 오류로 인해 success가 false여도 201일 때 body 파싱이 실행됨
- **판정**: ✅ 수용
- **AI 분석**: JavaScript 연산자 우선순위로 인한 버그. `success && (res.status === 200 || res.status === 201)`로 수정 필요
- **결정**: 수용
- **의견**: ai 동의

### 2. WebSocket threshold 메트릭 이름 불일치
- **파일**: `k6/scenarios/websocket-load.js:42-45`
- **내용**: thresholds에서 `ws_connecting`을 사용하지만 실제 커스텀 메트릭은 `ws_connect_time`으로 정의됨
- **판정**: ✅ 수용
- **AI 분석**: threshold가 존재하지 않는 메트릭을 참조하여 의도한 검증이 동작하지 않음
- **결정**: 수용
- **의견**: ai 동의

### 3. Grafana histogram_quantile 쿼리 집계 누락
- **파일**: `monitoring/grafana/provisioning/dashboards/fairbid-dashboard.json:103-121`
- **내용**: histogram_quantile 쿼리에 `sum by (le)` 집계가 누락되어 여러 인스턴스/레이블이 있을 경우 잘못된 백분위수 계산
- **판정**: ✅ 수용
- **AI 분석**: 현재 단일 인스턴스에서는 문제없으나, 수평 확장 시 잘못된 메트릭 표시됨. `histogram_quantile(0.95, sum by (le) (rate(...)))`로 수정 필요
- **결정**: 수용
- **의견**: 수평 확장을 고려한 모니터링이 필요

### 4. README 에러율 임계값 불일치
- **파일**: `k6/README.md:112-120`
- **내용**: README에는 "HTTP 에러율 < 5%"로 문서화되어 있으나 config.js의 thresholds는 `rate<0.01`(1%)로 설정됨
- **판정**: ⚠️ 선택적
- **AI 분석**: 문서와 코드 불일치. 실제 시나리오별 threshold가 5%이므로 config.js의 공통 threshold가 사용되지 않아 큰 문제 아님
- **결정**: 수용
- **의견**: ReadME에서 성능 기준을 삭제

---

## CodeRabbit 리뷰

### Actionable Comments

### 5. config.js generateBidAmount 함수 미사용
- **파일**: `k6/scenarios/config.js:27-32`
- **내용**: generateBidAmount 함수가 정의되어 있으나 어떤 시나리오에서도 사용되지 않음
- **판정**: ⚠️ 선택적
- **AI 분석**: 각 시나리오에서 인라인으로 입찰 금액을 계산 중. 공통 함수로 통일하거나 삭제 권장
- **결정**: 수용
- **의견**: 테스트 케이스가 늘어날 것을 대비하여 공통함수로 통일

### Nitpick Comments

### 6. bid-constant.js JSON.parse 에러 처리 추가
- **파일**: `k6/scenarios/bid-constant.js:59-65`
- **내용**: 서버 응답 파싱 시 try-catch 없어 예외 발생 가능
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 부하 테스트 중 서버 불안정 시 발생 가능. 다만 setup 실패 시 테스트 자체가 중단되어 명확한 피드백 제공
- **결정**: 거부
- **의견**: 크래시 나서 끝나기 때문에 테스트를 올바르게 수행할 수 있었음

### 7. mixed-load.js 미사용 전역 변수
- **파일**: `k6/scenarios/mixed-load.js:22-24`
- **내용**: `testAuctionId` 전역 변수가 선언되었으나 사용되지 않음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: setup()이 data 객체로 auctionId를 전달하므로 불필요한 코드
- **결정**: 수용
- **의견**: 불필요함

### 8. websocket-load.js STOMP 헤더 파싱 개선
- **파일**: `k6/scenarios/websocket-load.js:110-115`
- **내용**: `split(':')` 사용 시 값에 콜론이 포함된 헤더가 잘림
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 STOMP 메시지에서는 문제없으나 확장성을 위해 `indexOf(':')`로 첫 번째 콜론만 분리 권장
- **결정**: 수용
- **의견**: 확장성을 고려

---

## 생략된 항목

pr-review-guide.md 생략 기준에 따라 다음 항목은 문서화하지 않음:
- 마크다운 코드 블록 언어 지정 (docs/refactoring-roadmap.md, k6/README.md)
- 마크다운 테이블 주변 빈 줄 추가 (docs/refactoring-roadmap.md)
- 마크다운 emphasis를 heading으로 변경 (docs/refactoring-roadmap.md)

---

## 요약

| 결정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 7개 | 조건문 버그, threshold 메트릭, histogram_quantile, README 삭제, generateBidAmount 통일, 미사용 변수, STOMP 파싱 |
| ❌ 거부 | 1개 | JSON.parse 에러 처리 (현재 충분) |
