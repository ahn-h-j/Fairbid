# Frontend Guidelines

> 출처: vercel-labs/agent-skills (react-best-practices, web-interface-guidelines)
> Next.js 전용 규칙 제외, React + Vite 환경에 맞게 필터링됨.

---

## 1. React Performance (Critical)

### Waterfall 제거
- `await`를 실제 사용 시점까지 지연하라. 순차 await 금지.
- 독립적인 데이터 요청은 `Promise.all()`로 병렬 처리하라.
- 부분 의존 관계가 있으면 의존성 기반 병렬화를 사용하라.

### Bundle Size 최적화
- 배럴 파일(index.js re-export) 사용을 피하라. 직접 import 경로를 사용하라.
- 무거운 컴포넌트는 `React.lazy()` + `Suspense`로 동적 로딩하라.
- 조건부로만 필요한 모듈은 동적 `import()`를 사용하라.
- 서드파티 스크립트는 초기 로딩에서 제외하라.

---

## 2. Re-render 최적화 (Medium)

- 상태 구독을 파생된 boolean 값으로 좁혀라 (전체 객체 구독 금지).
- `useMemo`로 단순 원시값을 감싸지 마라.
- 비원시값 기본값은 메모이즈된 컴포넌트 밖으로 추출하라.
- 비용이 큰 연산은 별도 메모이즈드 컴포넌트로 분리하라.
- `useEffect` 의존성은 구체적 값으로 좁혀라.
- 함수형 `setState`를 사용하여 stale closure를 방지하라.
- 비용이 큰 초기값은 lazy initialization (`useState(() => compute())`) 사용.
- 긴급하지 않은 업데이트는 `useTransition`을 사용하라.

---

## 3. Rendering Performance (Medium)

- SVG 애니메이션은 `<g>` wrapper에 적용하라 (`transform-box: fill-box`).
- 긴 리스트는 `content-visibility: auto` 적용.
- 정적 JSX는 렌더 함수 밖으로 추출하라.
- 조건부 렌더링에 `&&` 대신 삼항 연산자 사용 (falsy 값 렌더 방지).
- 로딩 상태에 `useTransition` 활용.

---

## 4. JavaScript Performance (Low-Medium)

- DOM 읽기/쓰기를 분리하라 (layout thrashing 방지).
- 반복 조회가 필요한 데이터는 Map/Set으로 인덱스를 구성하라.
- 루프 내에서 프로퍼티 접근, 함수 호출을 캐싱하라.
- 배열 순회를 합쳐라 (여러 번 순회 금지).
- `sort()` 대신 `toSorted()` 사용 (불변성).
- 조기 반환(early return)을 적극 활용하라.

---

## 5. Accessibility

- 아이콘 버튼은 반드시 `aria-label` 부여.
- 폼 컨트롤은 `<label>` 또는 `aria-label` 필수.
- 인터랙티브 요소는 키보드 핸들러(`onKeyDown`/`onKeyUp`) 필수.
- 클릭 동작에 `<div onClick>` 금지 → `<button>` 사용.
- 네비게이션은 `<a>` / `<Link>` 사용.
- 이미지는 `alt` 필수 (장식용은 `alt=""`).
- 장식 아이콘은 `aria-hidden="true"`.
- 비동기 업데이트(토스트, 유효성검사)는 `aria-live="polite"`.

---

## 6. Forms

- `autocomplete`와 의미있는 `name` 속성 부여.
- 올바른 `type` 사용 (`email`, `tel`, `url`, `number`) + `inputMode`.
- `onPaste` + `preventDefault`로 붙여넣기 차단 금지.
- 레이블은 클릭 가능하게 (`htmlFor` 또는 컨트롤 감싸기).
- 이메일/코드/사용자명은 `spellCheck={false}`.
- 제출 버튼은 요청 시작 전까지 활성 유지, 요청 중 스피너 표시.
- 에러는 필드 옆에 인라인 표시, 제출 시 첫 에러 필드에 포커스.
- placeholder는 `…`으로 끝내고 예시 패턴 표시.
- 미저장 변경사항 있을 때 페이지 이탈 경고.

---

## 7. Animation

- `prefers-reduced-motion` 존중 (감소 변형 제공 또는 비활성화).
- `transform`/`opacity`만 애니메이션 (compositor-friendly).
- `transition: all` 절대 금지 → 속성을 명시적으로 나열.
- 애니메이션은 사용자 입력에 의해 중단 가능해야 함.

---

## 8. Typography & Content

- `...` 대신 `…` (ellipsis 문자) 사용.
- 긴 텍스트는 `truncate`, `line-clamp-*`, 또는 `break-words` 처리.
- Flex 자식에 `min-w-0` 부여 (텍스트 truncation 허용).
- 빈 상태(empty state) 처리 → 빈 배열/문자열에 깨진 UI 금지.
- 숫자 열에 `font-variant-numeric: tabular-nums` 사용.

---

## 9. Images & Performance

- `<img>`에 `width`/`height` 명시 (CLS 방지).
- 뷰포트 하단 이미지는 `loading="lazy"`.
- 50개 이상 리스트는 가상화 적용.
- DOM 읽기를 렌더 함수 내에서 하지 마라 (`getBoundingClientRect` 등).
- CDN 도메인에 `<link rel="preconnect">` 추가.

---

## 10. Navigation & State

- URL이 상태를 반영하라 (필터, 탭, 페이지네이션 → 쿼리 파라미터).
- 파괴적 동작은 확인 모달 또는 undo 제공 → 즉시 실행 금지.
- `useState`를 쓰는 곳이면 URL 동기화를 고려하라.

---

## 11. Touch & Interaction

- `touch-action: manipulation` (더블탭 줌 지연 방지).
- 모달/드로어에 `overscroll-behavior: contain`.
- 버튼/링크에 `hover:` 상태 필수 (시각 피드백).

---

## 12. Dark Mode & Theming

- 다크 테마 시 `color-scheme: dark` 설정.
- `<meta name="theme-color">`를 페이지 배경과 일치.

---

## 13. Locale

- 날짜/시간: `Intl.DateTimeFormat` 사용 (하드코딩 금지).
- 숫자/통화: `Intl.NumberFormat` 사용.

---

## 14. Anti-patterns (금지 목록)

- `user-scalable=no` 또는 `maximum-scale=1` → 줌 비활성화 금지.
- `transition: all` 금지.
- `outline-none` without `focus-visible` 대체 금지.
- `<div>` / `<span>` + click handler → `<button>` 사용.
- 크기 없는 이미지 금지.
- 라벨 없는 폼 인풋 금지.
- `aria-label` 없는 아이콘 버튼 금지.
- 하드코딩된 날짜/숫자 포맷 금지 → `Intl.*` 사용.
