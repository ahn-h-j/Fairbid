# TODO

> 프로젝트 개선 사항 및 향후 작업 목록

---

## 보류

### 복합 인덱스 추가
- **파일**: `AuctionEntity`
- **내용**: (status, scheduledEndTime) 복합 인덱스 추가
- **조건**: 부하테스트를 통해 데이터 확보 후 유의미한 성능 개선이 있을 경우

### ThreadPoolTaskScheduler 설정
- **파일**: `FairBidApplication.java`
- **내용**: SchedulingConfigurer 구현하여 ThreadPoolTaskScheduler 설정
- **조건**: 스케줄러가 늘어나거나 처리 시간이 길어지면

### 페이지 사이즈 상한 설정
- **파일**: `application.yml`
- **내용**: `spring.data.web.pageable.max-page-size` 설정 추가
- **조건**: 필요성이 생기면 그때 추가 (PR #17 리뷰 보류 항목)

### 외부 CDN 스크립트 SRI 해시 추가
- **파일**: `detail.html`
- **내용**: SockJS, STOMP 라이브러리 script 태그에 integrity 속성 추가
- **조건**: 필요성이 생기면 그때 추가 (PR #19 리뷰 보류 항목)

### 검색 쿼리 LOWER() 함수 인덱스 미활용
- **파일**: `AuctionSpecification.java`
- **내용**: `LOWER()` 함수 사용으로 title 컬럼 인덱스 미활용
- **해결**: DB 콜레이션을 `utf8mb4_unicode_ci`로 설정 후 LOWER() 제거
- **조건**: 대량 데이터에서 성능 이슈 발생 시

### 경매 목록 조회 시 불필요한 컬럼 조회
- **파일**: `AuctionPersistenceAdapter.java`
- **내용**: 목록 조회 시 모든 컬럼 SELECT (실제 응답에는 6개 필드만 사용)
- **해결**: JPA DTO Projection 사용
- **조건**: 성능 이슈 발생 시

### BidEventPublisher 모듈 간 의존성
- **파일**: `bid/application/port/out/BidEventPublisher.java`
- **내용**: bid 모듈이 auction.domain.Auction을 직접 참조하여 결합도 높음
- **해결**: 이벤트 발행에 필요한 데이터만 포함하는 DTO 사용
- **조건**: MSA 분리 고려 시 (PR #21 리뷰 보류 항목)

### FCM 토큰 조회 구현
- **파일**: `notification/adapter/out/fcm/FcmClient.java`
- **내용**: `getFcmToken()`이 항상 null 반환, 실제 FCM 전송 불가
- **해결**: User 도메인에서 FCM 토큰 관리 구현
- **조건**: User 도메인 모킹 해제 후 (PR #21 리뷰 보류 항목)

### FcmClient 에러 처리 개선
- **파일**: `notification/adapter/out/fcm/FcmClient.java`
- **내용**: `Exception` 대신 `FirebaseMessagingException` 구체적 처리 필요
- **해결**: FCM 특정 에러(토큰 만료 등) 세분화 처리, auctionId 로그 포함
- **조건**: User 도메인 모킹 해제 후 (PR #21 리뷰 보류 항목)

### NoShowProcessor 경고 부여 로직
- **파일**: `winning/application/service/NoShowProcessor.java`
- **내용**: 1순위 노쇼 시 경고 부여 로직 미구현 (TODO 주석)
- **해결**: User 도메인에서 경고 시스템 구현
- **조건**: User 도메인 모킹 해제 후 (PR #21 리뷰 보류 항목)

### useInfiniteScroll getCursor 주입 가능하게 변경
- **파일**: `frontend/src/hooks/useInfiniteScroll.js`
- **내용**: `id || auctionId` 하드코딩으로 다른 엔티티에서 재사용 어려움. `getCursor` 함수를 인자로 주입받도록 변경
- **조건**: 현재 2곳(auctions, bids)에서만 사용하며 둘 다 id/auctionId로 충분. 확장 필요 시 적용 (PR #43 리뷰 보류 항목)

### Error Boundary 추가
- **파일**: `frontend/src/App.jsx`
- **내용**: 렌더링 에러 발생 시 전체 앱 크래시 방지를 위한 ErrorBoundary 컴포넌트 추가
- **조건**: 프로덕션 배포 전 (PR #38 리뷰 보류 항목)

### PWA 아이콘 파일 생성
- **파일**: `frontend/public/icons/icon-192.png`, `frontend/public/icons/icon-512.png`
- **내용**: manifest.json에서 참조하는 PWA 아이콘 에셋 생성 (192x192, 512x512)
- **조건**: 디자인 확정 후, favicon.svg 기반 PNG 변환 필요 (수동 작업) (PR #38 리뷰 보류 항목)

### RedisConfig 설정 클래스 추가
- **파일**: `common/config/RedisConfig.java`
- **내용**: Spring Boot 자동 설정 대신 명시적 Redis 설정 (직렬화, 커넥션 팩토리 등)
- **조건**: 복잡한 객체 저장 또는 커넥션 풀 세부 튜닝 필요 시 (PR #30 리뷰 보류 항목)

### Redis Connection Pool 설정
- **파일**: `application.yml`
- **내용**: Lettuce 커넥션 풀 설정 (max-active, max-idle, min-idle)
- **조건**: 부하테스트 결과에 따라 튜닝 필요 시 (PR #30 리뷰 보류 항목)

### 경매 종료 큐 Fallback 메커니즘
- **파일**: `AuctionEventListener.java`, 신규 배치 클래스
- **내용**: `addToClosingQueue()` 실패 시 스케줄러가 해당 경매를 종료하지 못하는 문제
- **해결**: 주기적 보정 배치 (예: 10분마다 RDB의 BIDDING 상태 경매와 Sorted Set 비교, 누락 시 재등록)
- **조건**: Redis 부분 장애 시나리오 대응 필요 시 (PR #37 리뷰 보류 항목)

### Actuator/Test 엔드포인트 접근 제한
- **파일**: `SecurityConfig.java`
- **내용**: `/actuator/**`, `/h2-console/**` 등 운영 엔드포인트에 대한 접근 제한 설정
- **해결**: 프로파일별 분리 또는 IP 제한 설정
- **조건**: 프로덕션 배포 시 (PR #44 리뷰 보류 항목)

### HTTPS 설정 (SSL 인증서)
- **파일**: `nginx.conf`, `docker-compose.yml`
- **내용**: 현재 HTTP만 지원 (포트 80). HTTPS 미설정 상태
- **해결**:
  1. EC2에서 certbot으로 Let's Encrypt SSL 인증서 발급
  2. nginx 443 포트 + SSL 설정 추가
  3. HTTP→HTTPS 리다이렉트 설정
  4. docker-compose에서 443 포트 노출
  5. `COOKIE_SECURE: "true"` 설정 (운영)
- **조건**: 프로덕션 보안 강화 시

### Terraform EIP를 aws_eip_association으로 분리
- **파일**: `infra/main.tf:106-116`
- **내용**: `aws_eip`에 `instance`를 직접 연결하면 인스턴스 교체 시 ForceNew로 재생성됨
- **해결**: `aws_eip_association`으로 EIP 할당과 연결을 분리
- **조건**: 인프라 확장 또는 인스턴스 교체 계획 시 (PR #47 리뷰 보류 항목)

---

## 진행 중

<!-- 현재 작업 중인 항목 -->

---

## 완료

<!-- 완료된 항목 -->

<!-- 프론트 html -> react or next.js -->
<!-- Todo 스킬 추출 해볼것 -->
<!-- e2e cucumber 테스트 시도 -->
<!-- playwrite -->
    //Todo ResponseEntity, ApiResponse 통합고려
//Todo 네이밍 변경 : port
