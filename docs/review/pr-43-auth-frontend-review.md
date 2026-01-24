# PR #43 리뷰 분석

> **PR**: feat(identity): 프론트엔드 인증 상태관리 및 유저 화면 구현
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/43
> **리뷰어**: Copilot, CodeRabbit
> **분석일**: 2026-01-24

---

## Copilot + CodeRabbit 리뷰 (중복 통합)

### 1. ProtectedRoute 리다이렉트 시 쿼리 파라미터/해시 손실
- **파일**: `frontend/src/components/ProtectedRoute.jsx:28,33`
- **내용**: `location.pathname`만 저장하므로 `?keyword=...#section` 등이 사라짐
- **판정**: ✅ 수용
- **AI 분석**: 경매 목록 필터 적용 상태에서 로그인 필요 시 필터가 날아가는 실제 버그. `pathname + search + hash` 로 수정 필요
- **결정**: 수용
- **의견**: 필터는 유지되어야 함

### 2. Layout 로그인 버튼 state.from도 동일 문제
- **파일**: `frontend/src/components/Layout.jsx:85`
- **내용**: 로그인 버튼 클릭 시에도 `location.pathname`만 전달
- **판정**: ✅ 수용
- **AI 분석**: #1과 동일한 맥락. Layout에서도 전체 경로를 전달해야 함
- **결정**: 수용
- **의견**: 위와 동일

### 3. AuthCallbackPage 인라인 JWT 디코딩 → decodeJwtPayload 사용
- **파일**: `frontend/src/pages/AuthCallbackPage.jsx:42`
- **내용**: `atob(token.split('.')[1])` 인라인 사용 → base64url 패딩 미처리, 한글 닉네임 깨짐 가능
- **판정**: ✅ 수용
- **AI 분석**: `api/client.js`에 이미 `decodeJwtPayload()`가 존재하며 UTF-8을 올바르게 처리함. 코드 중복 제거 및 안정성 확보
- **결정**: 수용
- **의견**: 한글닉네임이 대부분일거라 깨지면 안됨

### 4. 온보딩 닉네임 중복확인 중 제출 방지
- **파일**: `frontend/src/pages/OnboardingPage.jsx:92`
- **내용**: `nicknameStatus === 'checking'` 상태에서 폼 제출이 가능함
- **판정**: ✅ 수용
- **AI 분석**: validate()에 checking 상태 체크 추가 + 제출 버튼 disabled 조건 추가 필요. 실제 UX 결함
- **결정**: 수용
- **의견**: ai 동의

### 5. 회원 탈퇴 실패 시 에러 미표시
- **파일**: `frontend/src/pages/MyPage.jsx:151`
- **내용**: catch에서 에러 무시하고 모달만 닫힘. 사용자가 실패 사유를 알 수 없음
- **판정**: ✅ 수용
- **AI 분석**: 에러 상태 추가하여 모달 내 에러 메시지 표시, 실패 시 모달 유지
- **결정**: 수용
- **의견**: ai 동의

### 6. decodeJwtPayload base64url 패딩 미처리
- **파일**: `frontend/src/api/client.js:79`
- **내용**: JWT payload가 4의 배수가 아닌 경우 `atob()` 실패 가능
- **판정**: ✅ 수용
- **AI 분석**: JWT 표준에서 base64url은 패딩을 생략함. `=` 패딩 추가 로직 필요
- **결정**: 수용
- **의견**: ai 동의

### 7. 온보딩 완료 후 리다이렉트 경로 부재
- **파일**: `frontend/src/pages/OnboardingPage.jsx:114`
- **내용**: ProtectedRoute가 `/onboarding`으로 보낸 경우 `location.state?.from`을 사용하지 않아 원래 페이지로 복귀 불가
- **판정**: ✅ 수용
- **AI 분석**: `location.state?.from`을 우선 확인하고, 없으면 localStorage fallback 사용하는 로직 필요
- **결정**: 수용
- **의견**: 회원가입, 로그인 후에는 원래 페이지로 돌아와야 함

### 8. useInfiniteScroll params 변경 시 페이지네이션 미리셋
- **파일**: `frontend/src/hooks/useInfiniteScroll.js:48`
- **내용**: 판매 탭 필터 변경 시 `size`가 유지되어 혼합 결과 표시 가능
- **판정**: ⚠️ 선택적
- **AI 분석**: SWRInfinite는 key가 변경되면 자동으로 캐시가 무효화되지만, size가 유지되면 불필요한 다중 페이지 요청 발생 가능. params 변경 감지 시 `setSize(1)` 호출 고려
- **결정**: 수용
- **의견**: 문제 사전 방지

### 9. 닉네임 중복확인 race condition (AbortController)
- **파일**: `frontend/src/pages/OnboardingPage.jsx:37-50`
- **내용**: 느린 네트워크에서 이전 응답이 나중 응답을 덮어쓸 수 있음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 500ms debounce로 대부분 방지되지만, 극단적 네트워크 지연 시 발생 가능. AbortController 적용이 이상적이나 현실적 영향도 낮음
- **결정**: 거부
- **의견**: 닉네임 중복 확인은 크게 문제되는 상황은 아니라고 생각됨 

### 10. MyPage loadMore 메모이제이션 부재로 Observer 재생성
- **파일**: `frontend/src/pages/MyPage.jsx:96`
- **내용**: `loadMore`가 매 렌더마다 새로 생성되어 IntersectionObserver가 빈번히 재연결됨
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: useInfiniteScroll 내 loadMore를 useCallback으로 감싸면 해결. 현재 동작에 큰 문제는 없으나 불필요한 리렌더 발생
- **결정**: 수용
- **의견**: 불필요한 리렌더 방지

### 11. useInfiniteScroll getCursor 주입 가능하게 변경
- **파일**: `frontend/src/hooks/useInfiniteScroll.js:13-36`
- **내용**: `id || auctionId` 하드코딩으로 다른 엔티티에서 재사용 어려움
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 2곳(auctions, bids)에서만 사용하며 둘 다 id/auctionId로 충분. 확장 필요 시 적용
- **결정**: 보류
- **의견**: docs/todo/Todo.md에 작성

### 12. useInfiniteScroll isValidating 미사용 변수
- **파일**: `frontend/src/hooks/useInfiniteScroll.js:41`
- **내용**: 구조 분해에서 `isValidating`을 꺼내지만 사용하지 않음
- **판정**: ✅ 수용 (Nitpick)
- **AI 분석**: 불필요한 변수 제거. 린트 경고 대상
- **결정**: 수용
- **의견**: ai 동의

### 13. LoginPage ONBOARDING_REQUIRED 리다이렉트 일관성
- **파일**: `frontend/src/pages/LoginPage.jsx:104-108`
- **내용**: ONBOARDING_REQUIRED 사용자가 `/`로 이동 후 다시 `/onboarding`으로 리다이렉트됨. 직접 보내는 것이 효율적
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재도 동작하지만 불필요한 리다이렉트 1회 발생. 게스트 로그인에서는 이미 직접 처리 중
- **결정**: 수용
- **의견**: 불필요한 리다이렉트로 지연 발생 가능

### 14. MyPage 탭 ARIA 패턴 미완성
- **파일**: `frontend/src/pages/MyPage.jsx:258-287`
- **내용**: `role="tab"`에 `id`, `aria-controls` 누락. `role="tabpanel"`에 `aria-labelledby` 누락
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: WAI-ARIA 탭 패턴 완전 준수를 위해 필요. 접근성 개선이나 현재 동작에 영향 없음
- **결정**: 수용
- **의견**: 완전 준수해도 됨

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 8개 | #1 쿼리파라미터 보존, #2 Layout 동일, #3 decodeJwtPayload 재사용, #4 checking 중 제출방지, #5 탈퇴에러표시, #6 base64 패딩, #7 온보딩 리다이렉트, #12 미사용변수 |
| ⚠️ 선택적 | 6개 | #8 페이지리셋, #9 AbortController, #10 loadMore 메모이제이션, #11 getCursor 주입, #13 리다이렉트 일관성, #14 ARIA 탭 |
| ❌ 거부 | 0개 | - |
