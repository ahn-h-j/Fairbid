# PR #19 리뷰 분석

> **PR**: [FEAT] 프론트엔드 메인페이지(경매 목록 + 검색) 구현
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/19
> **리뷰어**: CodeRabbit, GitHub Copilot
> **분석일**: 2026-01-13

---

## CodeRabbit 리뷰

### 1. .container 클래스 오버라이드로 인한 레이아웃 영향
- **파일**: `frontend/css/style.css:673-676`
- **내용**: `.container`의 `max-width`를 1200px로 변경하는 CSS 규칙이 모든 페이지에 적용됨. `detail.html`, `create.html`도 `.container`를 사용하므로 기존 800px 레이아웃이 1200px로 변경됨
- **판정**: ✅ 수용
- **AI 분석**: 목록 페이지는 넓은 레이아웃이 필요하지만, 상세/등록 페이지는 좁은 레이아웃이 가독성에 유리함. `.container-wide` 같은 전용 클래스 도입이 적절함
- **결정**: 수용
- **의견**: 일관된 형식이 UX 측면에서 좋음

### 2. detail.html formatStatus에서 PENDING 상태 제거
- **파일**: `frontend/detail.html:349-360`
- **내용**: 백엔드에서 `AuctionStatus.PENDING`이 제거되었으나 프론트엔드 매핑에 여전히 존재함
- **판정**: ✅ 수용
- **AI 분석**: 백엔드와 프론트엔드 간 일관성 유지를 위해 제거 필요. 현재 PENDING 상태가 반환될 일이 없으므로 실제 동작에는 영향 없음
- **결정**: 수용
- **의견**: 제거가 안된 요소인거 같음

### 3. img alt 속성 XSS 취약점
- **파일**: `frontend/index.html:232-240`
- **내용**: `auction.title`이 `alt` 속성에서 이스케이프 없이 사용됨. 제목에 큰따옴표가 포함되면 속성을 탈출하여 악성 속성 주입 가능
- **판정**: ✅ 수용
- **AI 분석**: `alt="${auction.title}"` → `alt="${escapeHtml(auction.title)}"` 로 변경 필요. 이미 `escapeHtml()` 함수가 존재하므로 간단히 적용 가능
- **결정**: 수용
- **의견**: 적용이 되지 않은 부분인거 같음

### 4. api.js JSDoc에서 PENDING 상태 제거
- **파일**: `frontend/js/api.js:94-103`
- **내용**: JSDoc 주석에 `PENDING` 상태가 여전히 포함됨
- **판정**: ✅ 수용
- **AI 분석**: 문서와 실제 코드 간 일관성 유지 필요. `(PENDING, BIDDING, ENDED, FAILED, CANCELLED)` → `(BIDDING, ENDED, FAILED, CANCELLED)`
- **결정**: 수용
- **의견**: 제거가 안된 요소인거 같음

### 5. create.html 경매 등록 완료 후 링크 오류
- **파일**: `frontend/create.html:156` (outside diff)
- **내용**: 경매 등록 완료 후 `'/?id='`로 이동하는데, 상세 페이지는 `/detail.html?id=`임
- **판정**: ✅ 수용
- **AI 분석**: PR 범위 밖이지만 명확한 버그. 수정 필요
- **결정**: 수용
- **의견**: 경매 보기 버튼은 경매 상세로 가는것이 맞음

### 6. 중복된 상태 배지 스타일
- **파일**: `frontend/css/style.css:594-618`
- **내용**: `.status-bidding`, `.status-ended`가 이미 정의되어 있는데 중복 정의됨
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 코드 정리 차원에서 중복 제거 권장. 동작에는 영향 없음
- **결정**: 수용
- **의견**: 중복 제거

### 7. CDN 스크립트 SRI 해시 추가 권장
- **파일**: `frontend/detail.html:9-10`
- **내용**: 외부 CDN 스크립트(SockJS, STOMP)에 무결성 검사(SRI) 해시 추가 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 보안 강화 차원에서 권장되나, 현재 단계에서 필수는 아님
- **결정**: 보류
- **의견**: 필요성이 생기면 그때 추가(docs/todo/Todo.md 문서에 작성할 것)

---

## GitHub Copilot 리뷰

### 8. page 파라미터 유효성 검증 부재
- **파일**: `frontend/index.html:113`
- **내용**: URL에서 읽은 page 파라미터가 음수나 과대값일 경우 검증 없이 사용됨
- **판정**: ⚠️ 선택적
- **AI 분석**: 백엔드에서 유효하지 않은 page 값 처리 가능. 프론트엔드 검증은 UX 개선 차원
- **결정**: 거부
- **의견**: 백엔드에서 처리 가능

### 9. status/sort 파라미터 유효성 검증 부재
- **파일**: `frontend/index.html:118`
- **내용**: URL에서 읽은 status, sort 파라미터가 유효한 값인지 검증 없이 API로 전송됨
- **판정**: ⚠️ 선택적
- **AI 분석**: 백엔드에서 유효하지 않은 값 처리 가능. 프론트엔드 검증은 UX 개선 차원
- **결정**: 거부
- **의견**: 백엔드에서 처리 가능

### 10. 검색 input aria-label 누락
- **파일**: `frontend/index.html:21`
- **내용**: 검색 입력 필드에 `aria-label` 속성이 없어 스크린 리더 접근성 저하
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 접근성 개선 차원에서 `aria-label="경매 상품 검색"` 추가 권장
- **결정**: 수용
- **의견**: UX 측면에서 필요

### 11. clear 버튼 aria-label 누락
- **파일**: `frontend/index.html:56`
- **내용**: 검색어 지우기 버튼에 `aria-label` 속성이 없음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 접근성 개선 차원에서 `aria-label="검색어 지우기"` 추가 권장
- **결정**: 수용
- **의견**: UX 측면에서 필요

### 12. 페이지네이션 버튼 aria-label 누락
- **파일**: `frontend/index.html:306`
- **내용**: 이전/다음 페이지 버튼에 `aria-label` 속성이 없음
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 접근성 개선 차원에서 `aria-label="이전 페이지"`, `aria-label="다음 페이지"` 추가 권장
- **결정**: 수용
- **의견**: UX 측면에서 필요

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 5개 | #1 .container 오버라이드, #2 detail.html PENDING 제거, #3 XSS 취약점, #4 JSDoc PENDING 제거, #5 create.html 링크 오류 |
| ⚠️ 선택적 | 7개 | #6 중복 CSS, #7 SRI 해시, #8 page 검증, #9 status/sort 검증, #10-12 aria-label |
| ❌ 거부 | 0개 | - |
