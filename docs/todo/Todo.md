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
