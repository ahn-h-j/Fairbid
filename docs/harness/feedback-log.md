# Harness Feedback Log

> 가드레일 실패가 자동으로 기록되는 파일. `/evolve` 스킬이 이 파일을 분석한다.
> 수동으로 편집하지 마라. hook/pre-commit이 자동으로 append한다.

---

### 2026-04-06 19:30
- **단계**: GC 스캔
- **도구**: /gc
- **위반**: `transition-all` CSS 사용 (31건) — frontend/CLAUDE.md에서 금지하지만 ESLint 규칙 없음
- **파일**: AuctionListPage, AuctionDetailPage, AuctionCreatePage, TradeDetailPage, LandingPage, OnboardingPage, Pagination, ImageUpload, ImageGallery
- **조치**: 코드 수정 별도 진행 예정, ESLint 규칙 추가 필요
- **상태**: resolved — ESLint `no-restricted-syntax` 규칙 추가 (2026-04-06)
