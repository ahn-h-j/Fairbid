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

### 2026-04-06 22:51
- **단계**: pre-commit
- **도구**: Checkstyle
- **위반**: Java 컨벤션 위반
- **파일**: backend/src/test/java/com/cos/fairbid/architecture/HexagonalArchitectureTest.java
- **상태**: open

### 2026-04-06 22:54
- **단계**: pre-commit
- **도구**: Checkstyle
- **위반**: Java 컨벤션 위반
- **파일**: backend/src/test/java/com/cos/fairbid/architecture/HexagonalArchitectureTest.java
- **상태**: open

### 2026-04-06 22:55
- **단계**: pre-commit
- **도구**: Checkstyle
- **위반**: Java 컨벤션 위반
- **파일**: backend/src/main/java/com/cos/fairbid/bid/adapter/out/cache/RedisBidCacheAdapter.java
backend/src/main/java/com/cos/fairbid/user/adapter/in/controller/UserController.java
backend/src/main/java/com/cos/fairbid/user/application/service/UserService.java
backend/src/test/java/com/cos/fairbid/architecture/HexagonalArchitectureTest.java
- **상태**: open

### 2026-04-06 23:40
- **단계**: pre-commit
- **도구**: Checkstyle
- **위반**: Java 컨벤션 위반
- **파일**: backend/src/main/java/com/cos/fairbid/admin/adapter/in/controller/AdminController.java
backend/src/main/java/com/cos/fairbid/admin/adapter/out/persistence/StatsPersistenceAdapter.java
backend/src/main/java/com/cos/fairbid/admin/application/service/StatsService.java
backend/src/main/java/com/cos/fairbid/auction/adapter/in/controller/AuctionController.java
backend/src/main/java/com/cos/fairbid/auction/adapter/out/cache/RedisAuctionCacheAdapter.java
backend/src/main/java/com/cos/fairbid/auction/adapter/out/persistence/entity/AuctionEntity.java
backend/src/main/java/com/cos/fairbid/auth/adapter/in/controller/AuthController.java
backend/src/main/java/com/cos/fairbid/auth/infrastructure/jwt/JwtTokenProvider.java
backend/src/main/java/com/cos/fairbid/bid/adapter/in/controller/BidController.java
backend/src/main/java/com/cos/fairbid/bid/adapter/in/stream/BidStreamConsumer.java
backend/src/main/java/com/cos/fairbid/bid/adapter/out/cache/RedisBidCacheAdapter.java
backend/src/main/java/com/cos/fairbid/bid/adapter/out/persistence/entity/BidEntity.java
backend/src/main/java/com/cos/fairbid/common/annotation/RequireOnboarding.java
backend/src/main/java/com/cos/fairbid/common/test/TestController.java
backend/src/main/java/com/cos/fairbid/notification/adapter/in/controller/NotificationController.java
backend/src/main/java/com/cos/fairbid/notification/adapter/out/redis/NotificationRedisAdapter.java
backend/src/main/java/com/cos/fairbid/notification/adapter/out/websocket/WebSocketBroadcastAdapter.java
backend/src/main/java/com/cos/fairbid/trade/adapter/in/controller/DeliveryController.java
backend/src/main/java/com/cos/fairbid/trade/adapter/in/controller/DirectTradeController.java
backend/src/main/java/com/cos/fairbid/trade/adapter/in/controller/TradeController.java
backend/src/main/java/com/cos/fairbid/trade/adapter/out/persistence/entity/DeliveryInfoEntity.java
backend/src/main/java/com/cos/fairbid/trade/adapter/out/persistence/entity/DirectTradeInfoEntity.java
backend/src/main/java/com/cos/fairbid/trade/adapter/out/persistence/entity/TradeEntity.java
backend/src/main/java/com/cos/fairbid/trade/application/service/TradeCommandService.java
backend/src/main/java/com/cos/fairbid/user/adapter/in/controller/UserController.java
backend/src/main/java/com/cos/fairbid/user/adapter/out/persistence/UserMyPagePersistenceAdapter.java
backend/src/main/java/com/cos/fairbid/user/adapter/out/persistence/entity/UserEntity.java
backend/src/main/java/com/cos/fairbid/user/application/service/UserService.java
backend/src/main/java/com/cos/fairbid/winning/adapter/out/persistence/entity/WinningEntity.java
backend/src/test/java/com/cos/fairbid/architecture/HexagonalArchitectureTest.java
- **상태**: open

### 2026-04-06 23:42
- **단계**: pre-commit
- **도구**: ESLint
- **위반**: 프론트엔드 금지 패턴 위반
- **파일**: frontend/src/api/mutations.js
frontend/src/components/ImageGallery.jsx
frontend/src/components/ImageUpload.jsx
frontend/src/components/Pagination.jsx
frontend/src/pages/AuctionCreatePage.jsx
frontend/src/pages/AuctionDetailPage.jsx
frontend/src/pages/AuctionListPage.jsx
frontend/src/pages/LandingPage.jsx
frontend/src/pages/TradeDetailPage.jsx
frontend/src/utils/constants.js
frontend/src/utils/formatters.js
- **상태**: open

### 2026-04-06 23:45
- **단계**: pre-commit
- **도구**: ESLint
- **위반**: 프론트엔드 금지 패턴 위반
- **파일**: frontend/src/api/mutations.js
frontend/src/components/ImageGallery.jsx
frontend/src/components/ImageUpload.jsx
frontend/src/components/Pagination.jsx
frontend/src/pages/AuctionCreatePage.jsx
frontend/src/pages/AuctionDetailPage.jsx
frontend/src/pages/AuctionListPage.jsx
frontend/src/pages/LandingPage.jsx
frontend/src/pages/TradeDetailPage.jsx
frontend/src/utils/constants.js
frontend/src/utils/formatters.js
- **상태**: open

### 2026-04-06 23:47
- **단계**: pre-commit
- **도구**: ESLint
- **위반**: 프론트엔드 금지 패턴 위반
- **파일**: frontend/src/api/mutations.js
frontend/src/components/ImageGallery.jsx
frontend/src/components/ImageUpload.jsx
frontend/src/components/Pagination.jsx
frontend/src/pages/AuctionCreatePage.jsx
frontend/src/pages/AuctionDetailPage.jsx
frontend/src/pages/AuctionListPage.jsx
frontend/src/pages/LandingPage.jsx
frontend/src/pages/TradeDetailPage.jsx
frontend/src/utils/constants.js
frontend/src/utils/formatters.js
- **상태**: open

### 2026-04-06 23:48
- **단계**: pre-commit
- **도구**: ESLint
- **위반**: 프론트엔드 금지 패턴 위반
- **파일**: frontend/src/api/mutations.js
frontend/src/components/ImageGallery.jsx
frontend/src/components/ImageUpload.jsx
frontend/src/components/Pagination.jsx
frontend/src/pages/AuctionCreatePage.jsx
frontend/src/pages/AuctionDetailPage.jsx
frontend/src/pages/AuctionListPage.jsx
frontend/src/pages/LandingPage.jsx
frontend/src/pages/TradeDetailPage.jsx
frontend/src/utils/constants.js
frontend/src/utils/formatters.js
- **상태**: open
