# PR #38 리뷰 분석

> **PR**: feat(frontend): HTML/CSS 프론트엔드를 React + Vite SPA로 마이그레이션
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/38
> **리뷰어**: Copilot, CodeRabbit
> **분석일**: 2026-01-24

---

## CodeRabbit 리뷰

### 1. CLAUDE.md 프론트엔드 버전 불일치
- **파일**: `CLAUDE.md:88`
- **내용**: 문서에 React 18+, React Router v6로 명시되어 있지만 실제 package.json에는 React 19.2.3, react-router-dom 7.12.0이 설치됨
- **판정**: ✅ 수용
- **AI 분석**: 문서와 실제 의존성 버전이 다르면 혼란을 줄 수 있음. 단순 문서 정합화 작업
- **결정**: 수용
- **의견**: 버젼은 정확히 기입되어야 함

### 2. 접근성: viewport 줌 비활성화 제거
- **파일**: `frontend/index.html:5`
- **내용**: `maximum-scale=1.0, user-scalable=no`가 저시력 사용자 핀치줌을 차단함. WCAG 위반
- **판정**: ✅ 수용
- **AI 분석**: PWA 앱 느낌을 위해 추가했으나, 접근성 표준(WCAG 2.1 SC 1.4.4)에서 사용자 줌 제한을 금지함. `viewport-fit=cover`만 유지하면 앱 느낌은 그대로 유지 가능
- **결정**: 수용
- **의견**: 지침 위반이면 수정 필수

### 3. 네트워크 오류 처리 누락
- **파일**: `frontend/src/api/client.js:69-90`
- **내용**: `fetch()`가 네트워크 장애 시 throw하는 에러를 `ApiError`로 변환하지 않음. 호출자가 일관된 에러 타입을 기대할 수 있음
- **판정**: ✅ 수용
- **AI 분석**: DNS 실패, 연결 거부 등에서 raw TypeError가 전파되면 상위 catch 핸들러에서 `error.code`, `error.message` 접근 시 의도치 않은 동작 가능. try/catch로 감싸서 `ApiError('NETWORK_ERROR', ...)`로 통일하면 에러 처리가 일관됨
- **결정**:  수용
- **의견**: ai 동의

### 4. auctionId 0 falsy 체크 문제
- **파일**: `frontend/src/api/useAuction.js:13`
- **내용**: `auctionId ? ... : null` 형태의 truthy 체크는 ID가 0인 경우 요청을 스킵함
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 백엔드에서 auction ID는 1부터 시작하는 auto-increment이므로 실제로 0이 나올 가능성은 없음. 방어적 코딩 관점에서는 맞지만 현재 시스템에서는 영향 없음
- **결정**: 거부
- **의견**: id 0은 나올일이 없음

### 5. AuctionCard에서 INSTANT_BUY_PENDING 타이머 미표시
- **파일**: `frontend/src/components/AuctionCard.jsx:24`
- **내용**: 목록 카드에서 `isBidding = status === 'BIDDING'`만 체크하여 INSTANT_BUY_PENDING 상태에서 타이머가 숨겨짐
- **판정**: ⚠️ 선택적
- **AI 분석**: 상세 페이지에서는 이미 수정했으나 카드 컴포넌트에는 미반영됨. 즉시구매 대기 중에도 추가 입찰이 가능하므로 목록에서도 카운트다운을 보여주는 것이 일관성 있음
- **결정**: 부분 수용 - 타이머 제거 후 "마감 임박" 정적 뱃지로 대체
- **의견**: 목록은 WebSocket 미사용(30초 API 폴링)이므로 실시간 카운트다운은 부정확할 수 있음(종료 5분 전 입찰 시 연장되나 카드는 모름). 타이머 대신 scheduledEndTime 기준 10분 이내일 때 "마감 임박" 정적 뱃지만 표시하는 방식으로 변경. 30초 폴링으로 갱신되므로 뱃지 정확도는 충분함

### 6. Spinner size 폴백 처리
- **파일**: `frontend/src/components/Spinner.jsx:10`
- **내용**: 유효하지 않은 size prop 시 sizeClasses[size]가 undefined 반환
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: TypeScript를 사용하지 않는 환경에서 방어적 코딩. 현재 호출처에서 항상 유효한 값을 전달하므로 즉각적 문제는 아님
- **결정**: 거부
- **의견**: ai 동의

### 7. WebSocket auctionId 0 연결 차단
- **파일**: `frontend/src/hooks/useWebSocket.js:25-27`
- **내용**: `if (!auctionId) return;` 가드가 ID 0을 차단함
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: #4와 동일 이유. 현재 시스템에서 auction ID 0은 존재하지 않음
- **결정**: 패스
- **의견**: 위에서 답변

### 8. currentPrice || startPrice → ?? 연산자로 변경
- **파일**: `frontend/src/pages/AuctionDetailPage.jsx:200`, `frontend/src/components/AuctionCard.jsx:77`
- **내용**: `||` 연산자가 currentPrice 0을 falsy로 처리하여 startPrice로 폴백됨. `??`로 변경 필요
- **판정**: ✅ 수용
- **AI 분석**: 시작가가 0원인 경매는 없겠지만, currentPrice가 아직 설정되지 않은 상태에서 null vs 0 구분이 필요할 수 있음. `??`가 의미적으로 더 정확한 연산자
- **결정**: 수용
- **의견**: 명확한 구분이 필요

### 9. 검색 입력값 URL 동기화
- **파일**: `frontend/src/pages/AuctionListPage.jsx:14-16`
- **내용**: `searchInput`이 초기 렌더링에서만 URL keyword를 반영하고, 브라우저 뒤로가기/앞으로가기 시 입력창이 갱신되지 않음
- **판정**: ✅ 수용
- **AI 분석**: React Router의 `useSearchParams`는 URL 변경 시 re-render를 트리거하지만, `useState` 초기값은 최초 1회만 적용됨. `useEffect`로 keyword 변경을 감지하여 입력창을 동기화해야 함
- **결정**: 수용
- **의견**: ai 동의

---

## Copilot 리뷰

### 10. nginx.conf WebSocket 프록시 누락
- **파일**: `frontend/nginx.conf`
- **내용**: 프로덕션 nginx 설정에 `/ws` location 블록이 없어 WebSocket 연결 불가
- **판정**: ✅ 수용
- **AI 분석**: Vite dev proxy에서는 ws:true로 처리되지만, 프로덕션 nginx에서는 별도 location 블록과 Upgrade/Connection 헤더 설정이 필수. 이 없이는 Docker 배포 시 실시간 기능이 동작하지 않음
- **결정**: 수용
- **의견**: 반드시 필요

### 11. React.StrictMode 누락
- **파일**: `frontend/src/main.jsx:5`
- **내용**: StrictMode 래퍼가 없어 개발 모드에서 잠재적 문제(side effect, deprecated API 사용 등) 감지 불가
- **판정**: ✅ 수용
- **AI 분석**: StrictMode는 프로덕션 빌드에 영향 없고, 개발 시 useEffect 이중 호출 등으로 버그를 조기 발견할 수 있음. React 권장 패턴
- **결정**: 수용
- **의견**: 권장 패턴 중시

### 12. Error Boundary 부재
- **파일**: `frontend/src/App.jsx:23`
- **내용**: 렌더링 에러 발생 시 전체 앱이 크래시되고 흰 화면이 표시됨
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 테스트/개발 단계에서는 에러를 직접 확인하는 것이 디버깅에 유리할 수 있음. 프로덕션 배포 전에는 추가 필요
- **결정**: 보류 
- **의견**: docs/todo/Todo.md에 기록

### 13. PWA 아이콘 파일 미포함
- **파일**: `frontend/public/manifest.json:12,18`
- **내용**: manifest에서 참조하는 `/icons/icon-192.png`, `/icons/icon-512.png` 파일이 PR에 포함되지 않음
- **판정**: ✅ 수용
- **AI 분석**: 아이콘 파일 없이는 PWA 설치 시 깨진 아이콘 표시. 실제 아이콘 에셋 생성 필요
- **결정**: 보류
- **의견**: docs/todo/Todo.md에 기록

### 14. X-User-Id 프로덕션 보안
- **파일**: `frontend/src/api/client.js:64-67`
- **내용**: 테스트용 X-User-Id 헤더가 프로덕션에서도 동작 가능. 환경 분기 없음
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 프로젝트가 개발/테스트 단계이므로 당장 문제는 아님. 프로덕션 배포 시에는 JWT 등 적절한 인증으로 교체 필요. 현 단계에서는 의도된 동작
- **결정**: 거부
- **의견**: 현 단계에서는 테스트용

### 15. SWR 30초 자동 갱신 UX 이슈
- **파일**: `frontend/src/api/useAuctions.js:30`
- **내용**: 30초 `refreshInterval`이 사용자가 목록 탐색 중 콘텐츠를 갑자기 업데이트할 수 있음
- **판정**: ❌ 거부
- **AI 분석**: 경매 시스템 특성상 실시간 가격/상태 변동이 핵심이므로 주기적 갱신은 필요함. 30초는 과도하지 않은 간격이며, SWR의 `isValidating` 상태로 로딩 인디케이터를 보여줄 수 있음. 목록 레이아웃이 바뀌지 않고 카드 내 가격/상태만 업데이트되므로 jarring하지 않음
- **결정**: 거부
- **의견**: 주기적 갱신은 의도한 작업

### 16. useTimer endTimeStr 유효성 검증
- **파일**: `frontend/src/hooks/useTimer.js:42`
- **내용**: 유효하지 않은 날짜 문자열 시 NaN 전파
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 서버 API에서 항상 유효한 ISO 날짜를 반환하므로 실제 발생 가능성 낮음. 방어적 코딩 관점
- **결정**: 수용
- **의견**: NaN전파는 방지

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 9개 | #1 버전정합, #2 접근성, #3 네트워크에러, #5 카드타이머, #8 ??연산자, #9 URL동기화, #10 nginx WS, #11 StrictMode, #13 PWA아이콘 |
| ⚠️ 선택적 | 5개 | #4 ID 0체크, #6 Spinner폴백, #7 WS ID체크, #12 ErrorBoundary, #14 X-User-Id, #16 타이머검증 |
| ❌ 거부 | 1개 | #15 SWR 자동갱신 |
