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

---

## 진행 중

<!-- 현재 작업 중인 항목 -->

---

## 완료

<!-- 완료된 항목 -->
