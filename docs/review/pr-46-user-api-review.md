# PR #46 리뷰 분석

> **PR**: feat(user-api): User API (온보딩/프로필/마이페이지) 구현
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/46
> **리뷰어**: Copilot, CodeRabbit
> **분석일**: 2026-01-25

---

## Copilot 리뷰

### 1. 닉네임/전화번호 중복 확인 Race Condition
- **파일**: `user/application/service/UserService.java`
- **내용**: 닉네임/전화번호 중복 확인과 저장 사이에 동시 요청 시 Race Condition 발생 가능. `DataIntegrityViolationException` 핸들링 권장.
- **판정**: ✅ 수용
- **AI 분석**: 2명이 동시에 같은 닉네임으로 온보딩하면 둘 다 중복 확인을 통과하고 저장 시 DB 제약 조건에서 실패함. Unique 제약 조건이 있으므로 `DataIntegrityViolationException`을 catch해서 적절한 예외로 변환해야 함.
- **결정**: 수용
- **의견**: GlobalExceptionHandler에 DataIntegrityViolationException 핸들러 추가

### 2. size/cursor 파라미터 검증 누락
- **파일**: `user/adapter/in/controller/UserController.java`
- **내용**: `size`와 `cursor` 파라미터에 `@Min`/`@Max` 검증이 없어 size=99999999 같은 악의적 요청 가능.
- **판정**: ✅ 수용
- **AI 분석**: size가 무제한이면 메모리 문제 및 DB 부하 발생 가능. `@Min(1) @Max(100)` 정도로 제한해야 함.
- **결정**: 수용
- **의견**: ai 동의

### 3. 탈퇴 후 Access Token 여전히 유효
- **파일**: `user/application/service/UserService.java:deactivateAccount()`
- **내용**: 계정 탈퇴 시 `isActive=false`만 설정하고 Access Token은 여전히 유효함. 탈퇴 직후에도 API 호출 가능.
- **판정**: ⚠️ 선택적
- **AI 분석**: Access Token은 Stateless라 즉시 무효화 불가. 방법은 (1) Refresh Token만 삭제 + Access Token 짧은 만료시간 (2) Token 블랙리스트 (3) 탈퇴 시 Refresh Token으로 재발급 차단. 현재 구조에서 Refresh Token 삭제만으로도 장기적 접근은 차단됨.
- **결정**: 보류
- **의견**: Refresh Token 삭제 로직은 이미 있음. Access Token 만료(30분)까지는 허용하는 것으로 현재 수준 유지. 추후 Token Revocation 구현 시 개선.

### 4. checkNickname 엔드포인트 @NotBlank/@Size 누락
- **파일**: `user/adapter/in/controller/UserController.java:checkNickname()`
- **내용**: nickname 파라미터에 빈 문자열, 공백, 200자 입력 등 검증 없음.
- **판정**: ✅ 수용
- **AI 분석**: 인증 없이 접근 가능한 엔드포인트라 악의적 입력 방지 필요. `@NotBlank @Size(min=2, max=20)` 추가 필요.
- **결정**: 수용
- **의견**: ai 동의

### 5. 게스트 로그인 프로덕션 노출
- **파일**: `auth/adapter/in/controller/AuthController.java`
- **내용**: 게스트 로그인이 프로덕션에서도 노출되어 보안 위험.
- **판정**: ❌ 거부
- **AI 분석**: 이는 의도적인 기능임. FairBid는 "호구 없는 경매" 컨셉으로 게스트도 경매를 볼 수 있어야 함. 게스트는 경매 등록/입찰이 불가하므로 보안 문제 없음.
- **결정**: 거부
- **의견**: 의도된 기능, 게스트는 조회만 가능

---

## CodeRabbit 리뷰

### 6. CookieUtils HttpOnly 설정 누락
- **파일**: `auth/infrastructure/security/CookieUtils.java:21`
- **내용**: HttpOnly가 지정되지 않아 false로 기본 설정됨 → XSS로 쿠키 탈취 가능.
- **판정**: ✅ 수용
- **AI 분석**: Refresh Token 쿠키는 JavaScript에서 접근할 필요 없으므로 `HttpOnly=true` 필수. 현재 Set-Cookie 문자열에 `HttpOnly;` 추가 필요.
- **결정**: 수용
- **의견**: ai 동의

### 7. 닉네임 URL 인코딩 누락
- **파일**: `user/adapter/in/controller/UserController.java:checkNickname()` (백엔드) + `OnboardingPage.jsx:46` (프론트엔드)
- **내용**: 특수문자 포함 닉네임(예: `a&b=c`)이 URL 파라미터로 전달 시 파싱 오류 가능.
- **판정**: ⚠️ 선택적
- **AI 분석**: 프론트엔드에서 `encodeURIComponent()` 사용 중이므로 대부분 케이스 커버됨. 닉네임에 허용할 특수문자 정책을 정하고 서버 측에서도 검증하는 게 확실함.
- **결정**: 보류
- **의견**: 프론트에서 이미 인코딩 중. 닉네임 허용 문자 정책(한글/영문/숫자만 등)을 정하면 해결됨. TODO 기록.

### 8. 사용하지 않는 상수 (TOKEN_PREFIX_LENGTH, TOKEN_PREFIX)
- **파일**: `auth/infrastructure/security/JwtAuthenticationFilter.java:24-25`
- **내용**: `TOKEN_PREFIX_LENGTH`와 `TOKEN_PREFIX` 상수가 정의되어 있지만 실제로 사용되지 않음.
- **판정**: ✅ 수용
- **AI 분석**: Dead code 제거 필요. 현재 `resolveToken()` 메서드에서 하드코딩된 7 사용 중.
- **결정**: 수용
- **의견**: 상수를 사용하도록 수정하거나, 불필요하면 삭제

### 9. 마이페이지 조회 쿼리 status null 처리
- **파일**: `auction/adapter/out/persistence/repository/JpaAuctionRepository.java`
- **내용**: `findBySellerIdWithCursor` 메서드에서 status가 null일 때의 동작이 명확하지 않음.
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 JPQL에서 `(:status IS NULL OR a.status = :status)` 패턴 사용 중이므로 null 시 전체 조회됨. 의도한 동작이지만 주석으로 명시하면 좋음.
- **결정**: 보류
- **의견**: 현재 동작이 의도대로임. 주석 추가는 선택적.

### 10. 마이페이지 입찰 목록 N+1 쿼리 가능성
- **파일**: `user/adapter/out/persistence/UserMyPagePersistenceAdapter.java`
- **내용**: 입찰한 경매 목록 조회 시 각 경매별로 추가 쿼리 발생 가능.
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 JPQL로 JOIN + GROUP BY 사용하여 단일 쿼리로 처리 중. DTO Projection이므로 N+1 문제 없음. 성능 모니터링 후 필요 시 개선.
- **결정**: 보류
- **의견**: 현재 구현이 적절함

### 11. OnboardingResult record의 accessToken null 처리
- **파일**: `user/application/port/in/CompleteOnboardingUseCase.java`
- **내용**: `OnboardingResult(accessToken, refreshToken)` record에서 null이 전달될 수 있음.
- **판정**: ⚠️ 선택적
- **AI 분석**: JwtTokenProvider.generateToken()은 항상 non-null을 반환하므로 실제로 null이 올 가능성 없음. 방어적 코딩으로 `Objects.requireNonNull()` 추가 가능하지만 과도함.
- **결정**: 보류
- **의견**: 과도한 방어적 코딩

### 12. SecurityConfig에서 user-info 엔드포인트 permitAll
- **파일**: `auth/infrastructure/security/SecurityConfig.java`
- **내용**: `/api/v1/auth/user-info`가 permitAll인데 인증 정보를 반환하므로 authenticated 필요할 수 있음.
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 user-info는 쿠키의 Access Token을 읽어 사용자 정보 반환. 프론트엔드 초기 로딩 시 사용하며, Token이 없으면 빈 응답 반환하므로 permitAll이 적절함. 민감 정보는 포함되지 않음.
- **결정**: 거부
- **의견**: 현재 설계 의도대로 동작. Token 없으면 null 반환.

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 5개 | Race Condition, size/cursor 검증, checkNickname 검증, HttpOnly 누락, 미사용 상수 |
| ⚠️ 선택적 | 6개 | 탈퇴 후 Token, 닉네임 인코딩, status null 처리, N+1 쿼리, OnboardingResult null, user-info permitAll |
| ❌ 거부 | 2개 | 게스트 로그인 노출, user-info permitAll |

---

## 후속 작업 (TODO)

1. **필수 (수용):**
   - [x] GlobalExceptionHandler에 `DataIntegrityViolationException` 핸들러 추가
   - [x] `UserController`의 size 파라미터에 `@Min(1) @Max(100)` 추가
   - [x] `checkNickname` 엔드포인트에 `@NotBlank @Size(min=2, max=20)` 추가
   - [x] `CookieUtils`에 `HttpOnly;` 플래그 추가 (이미 적용되어 있음)
   - [x] `JwtAuthenticationFilter`의 미사용 상수 정리 (이미 정리되어 있음)

2. **선택적:**
   - [ ] 닉네임 허용 문자 정책 정의 (한글/영문/숫자만 등)
   - [ ] 탈퇴 시 Refresh Token 삭제 여부 확인 (이미 구현되어 있다면 문서화)
