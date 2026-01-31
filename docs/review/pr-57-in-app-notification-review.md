# PR #57 리뷰 분석

> **PR**: feat(support): 인앱 알림 시스템 구현 (#52)
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/57
> **리뷰어**: coderabbitai[bot], copilot
> **분석일**: 2026-01-31

---

## coderabbitai 리뷰

### 1. notificationId 입력 값 검증 누락
- **파일**: `NotificationController.java:64-70`
- **내용**: markAsRead 엔드포인트에서 빈 문자열이나 악의적 입력에 대한 검증 없음
- **판정**: ✅ 수용
- **AI 분석**: @NotBlank 어노테이션 추가로 간단히 해결 가능
- **결정**: 수용
- **의견**: ai 동의

---

### 2. save() 예외 발생 시 조용히 유실
- **파일**: `NotificationRedisAdapter.java:37-54`
- **내용**: JsonProcessingException 발생 시 로그만 남기고 반환, 호출자가 실패 인지 불가
- **판정**: ✅ 수용
- **AI 분석**: 알림 저장 실패가 치명적이지 않으나, 예외 전파 또는 메트릭 수집 권장
- **결정**: 수용
- **의견**: 예외 전파하여 호출자가 인지하도록 함

---

### 3. markAsRead 경쟁 조건 존재
- **파일**: `NotificationRedisAdapter.java:76-98`
- **내용**: range()와 set() 사이 다른 요청이 리스트 수정 시 잘못된 인덱스 업데이트 가능
- **판정**: ⚠️ 선택적
- **AI 분석**: Lua 스크립트나 Redis 트랜잭션 사용 권장. 다만 현재 트래픽 수준에서는 발생 확률 낮음
- **결정**: 거부
- **의견**: MVP 단계에서 발생 확률 낮고 영향 미미. 트래픽 증가 시 개선 고려

---

### 4. markAsRead 호출 시 에러 처리 누락
- **파일**: `NotificationDropdown.jsx:30-36`
- **내용**: 네트워크 오류나 서버 에러 발생 시 사용자 피드백 없이 실패
- **판정**: ✅ 수용
- **AI 분석**: try/catch로 간단히 해결 가능. UX 개선
- **결정**: 수용
- **의견**: ai 동의

---

### 5. getRelativeTime 유효하지 않은 날짜 방어 처리 누락
- **파일**: `NotificationDropdown.jsx:98-109`
- **내용**: dateString이 null/undefined/파싱 불가 시 Invalid Date 발생 가능
- **판정**: ✅ 수용
- **AI 분석**: null 체크 및 isNaN 체크 추가 필요
- **결정**: 수용
- **의견**: ai 동의

---

### 6. Controller가 아웃바운드 포트 직접 의존
- **파일**: `NotificationController.java:22-28`
- **내용**: 인바운드 어댑터가 NotificationStoragePort(아웃바운드 포트) 직접 주입 → 헥사고날 아키텍처 위반
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 현재 비즈니스 로직이 단순하여 서비스 레이어 불필요. 향후 확장 시 고려
- **결정**: 수용
- **의견**: 헥사고날 아키텍처 준수 

---

## copilot 리뷰

### 7. getRelativeTime에서 getServerTime() 사용 권장
- **파일**: `NotificationDropdown.jsx:98-104`
- **내용**: 클라이언트 Date() 대신 코드베이스의 getServerTime() 패턴 사용 권장
- **판정**: ✅ 수용
- **AI 분석**: 다른 컴포넌트(AuctionCard)에서 사용 중인 패턴과 일관성 유지
- **결정**: 수용
- **의견**: 프로젝트 전체가 서버 시간 기준 사용, 일관성 유지

---

### 8. createdAt 타입을 LocalDateTime으로 변경
- **파일**: `NotificationController.java:75-95`
- **내용**: 다른 DTO들은 LocalDateTime 타입 그대로 사용, 일관성 필요
- **판정**: ✅ 수용
- **AI 분석**: Jackson 설정에 맡기면 ISO 형식으로 직렬화됨
- **결정**: 수용
- **의견**: ai 동의

---

### 9. 알림 API 통합 테스트 추가
- **파일**: `NotificationController.java:34-70`
- **내용**: Cucumber 기반 통합 테스트 추가 권장
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 테스트 추가는 좋지만 MVP 단계에서는 선택적
- **결정**: 수용
- **의견**: cucumber 테스트로 추가

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 8개 | #1, #2, #4, #5, #6, #7, #8, #9 |
| ❌ 거부 | 1개 | #3 (경쟁조건 - MVP에서 영향 미미) |

---

## 반영 계획

### Backend
1. #1 - NotificationController: @NotBlank 검증 추가
2. #2 - NotificationRedisAdapter: save() 예외 전파
3. #6 - NotificationService UseCase 분리 (아키텍처)
4. #8 - NotificationResponse: createdAt → LocalDateTime
5. #9 - Cucumber 통합 테스트 추가

### Frontend
6. #4 - NotificationDropdown: markAsRead try/catch
7. #5 - getRelativeTime: null/invalid 방어
8. #7 - getRelativeTime: getServerTime() 사용
