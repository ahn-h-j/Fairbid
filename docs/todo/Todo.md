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

---

## 진행 중

<!-- 현재 작업 중인 항목 -->

---

## 완료

<!-- 완료된 항목 -->
