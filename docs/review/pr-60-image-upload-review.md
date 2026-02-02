# PR #60 리뷰 분석

> **PR**: [FEAT] 경매 상품 이미지 업로드 기능 (Cloudinary)
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/60
> **리뷰어**: CodeRabbit, GitHub Copilot
> **분석일**: 2026-02-02

---

## CodeRabbit 리뷰

### 1. ImageGallery activeIndex 범위 이탈 버그
- **파일**: `frontend/src/components/ImageGallery.jsx:11-45`
- **내용**: `images` prop이 변경될 때 `activeIndex`가 동기화되지 않아 `images[activeIndex]`가 `undefined`가 될 수 있음
- **판정**: ✅ 수용
- **AI 분석**: 다른 경매로 이동하거나 이미지 목록이 줄어들 때 실제로 발생 가능한 버그. React의 상태 관리 원칙상 prop 변경 시 관련 state를 동기화해야 함. `useEffect`로 `images` 변경 시 `activeIndex` clamp 처리 필요
- **결정**: 수용
- **의견**: ai 동의

### 2. ImageUpload 클로저 캡처로 인한 이미지 복구 버그
- **파일**: `frontend/src/components/ImageUpload.jsx:66-104`
- **내용**: 업로드 진행 중 삭제 시 클로저에 캡처된 이전 `images` 값이 사용되어 삭제한 이미지가 복구됨
- **판정**: ✅ 수용
- **AI 분석**: JavaScript 클로저의 고전적인 stale closure 문제. 비동기 작업 중 state 참조 시 흔히 발생. `useRef`로 최신 값 동기화가 표준 해결책. `imagesRef`를 추가하여 최신 이미지 배열 동기화 필요
- **결정**: 수용
- **의견**: ai 동의

### 3. 업로드 중 폼 제출 가능 문제
- **파일**: `frontend/src/pages/AuctionCreatePage.jsx:156-164`
- **내용**: 이미지 업로드 진행 중에도 제출 버튼이 활성화되어 이미지가 누락될 수 있음
- **판정**: ✅ 수용
- **AI 분석**: 비동기 업로드와 폼 제출의 동기화 문제. UX 관점에서도 업로드 중 제출 차단이 필요. `onUploadingChange` 콜백 prop 추가하여 부모 컴포넌트에서 제출 버튼 비활성화 필요
- **결정**: 수용
- **의견**: ai 동의

### 4. image_url 컬럼 길이 제한
- **파일**: `backend/.../AuctionEntity.java:93-98`
- **내용**: 기본 String 길이(255)로는 Cloudinary URL이 잘릴 수 있음
- **판정**: ✅ 수용 (Nitpick)
- **AI 분석**: Cloudinary URL은 일반적으로 100~150자 정도이나, 변환 옵션이 추가되면 길어질 수 있음. 안전 마진 확보 위해 `@Column(length = 2048)` 추가 권장
- **결정**: 수용
- **의견**: ai 동의

---

## GitHub Copilot 리뷰

### 1. ImageGallery activeIndex 범위 이탈 (중복)
- **파일**: `frontend/src/components/ImageGallery.jsx:11-55`
- **내용**: CodeRabbit과 동일한 지적
- **판정**: ✅ 수용
- **AI 분석**: 두 리뷰어가 동일하게 지적한 사항으로 우선순위 높음. CodeRabbit #1과 통합하여 처리
- **결정**: 수용
- **의견**: ai 동의

### 2. ImageUpload 파일 필터링 순서 문제
- **파일**: `frontend/src/components/ImageUpload.jsx:33-47`
- **내용**: `slice(0, remainingSlots)` 후 이미지 타입 필터링 시, 비-이미지 파일이 앞에 있으면 실제 업로드 개수가 줄어듦
- **판정**: ✅ 수용
- **AI 분석**: 사용자가 이미지 5장 + PDF 1장을 선택했을 때, PDF가 앞에 있으면 이미지 4장만 업로드되는 문제. 논리적으로 타입 필터링이 먼저여야 함. 필터링 순서를 이미지 타입 필터 → slice로 변경 필요
- **결정**: 수용
- **의견**: ai 동의

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 5개 | activeIndex 동기화, stale closure 수정, 업로드 중 제출 차단, 컬럼 길이 확장, 필터링 순서 수정 |
| ❌ 거부 | 0개 | - |

---

## 반영 계획

### Backend
1. #4 - `AuctionEntity.java`: `@Column(length = 2048)` 추가

### Frontend
1. #1 - `ImageGallery.jsx`: `useEffect`로 `images` 변경 시 `activeIndex` clamp 처리
2. #2 - `ImageUpload.jsx`: `imagesRef` 추가하여 stale closure 문제 해결
3. #6 - `ImageUpload.jsx`: 파일 필터링 순서 변경 (이미지 타입 필터 → slice)
4. #3 - `ImageUpload.jsx`: `onUploadingChange` 콜백 prop 추가
5. #3 - `AuctionCreatePage.jsx`: `isUploading` state 추가 및 제출 버튼 비활성화 처리
