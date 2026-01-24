# PR #44 리뷰 분석

> **PR**: feat(identity): User 도메인 + Spring Security + OAuth2 인증 구현
> **URL**: https://github.com/ahn-h-j/Fairbid/pull/44
> **리뷰어**: coderabbitai
> **분석일**: 2026-01-24

---

## CodeRabbit 리뷰

### 1. OAuth state 파라미터 누락 (CSRF 방지)
- **파일**: `auth/adapter/in/controller/AuthController.java:71`
- **내용**: OAuth 2.0 스펙(RFC 6749)에서 권장하는 `state` 파라미터가 없어 Login CSRF 공격에 취약함. 공격자가 피해자를 자신의 계정으로 로그인시킬 수 있음.
- **판정**: ✅ 수용
- **AI 분석**: state 파라미터는 OAuth2 보안의 핵심 요소임. SameSite=Strict 쿠키로 일부 방어되지만, state 파라미터가 표준 방어선임. 세션이 없으므로 state를 HttpOnly 쿠키에 저장하고 콜백에서 검증하는 방식이 적절함.
- **결정**: 수용
- **의견**: ai 동의

### 2. onboarded 값 하드코딩
- **파일**: `auth/adapter/in/controller/AuthController.java:124`
- **내용**: `TokenResponse`의 onboarded 값이 항상 `true`로 고정되어 실제 온보딩 상태를 반영하지 않음.
- **판정**: ✅ 수용
- **AI 분석**: Access Token 디코딩으로 판단 가능하다고 주석했지만, 응답 필드가 잘못된 값을 제공하면 혼란을 줌. Access Token 클레임에서 onboarded를 추출하거나, 필드를 제거하는 것이 맞음.
- **결정**: 수용
- **의견**: ai 동의

### 3. Cookie 객체 미사용 (Dead Code)
- **파일**: `auth/adapter/in/controller/AuthController.java:159`
- **내용**: Cookie 객체를 생성하고 속성을 설정하지만 `response.addCookie()`를 호출하지 않아 사용되지 않음. Set-Cookie 헤더를 직접 설정하고 있으므로 Cookie 객체 코드는 Dead Code임.
- **판정**: ✅ 수용
- **AI 분석**: SameSite 속성은 Cookie API로 설정 불가하여 헤더를 직접 사용한 것은 맞지만, 불필요한 Cookie 객체 생성 코드를 제거해야 함.
- **결정**: 수용
- **의견**: ai 동의

### 4. RestClient 타임아웃 미설정
- **파일**: `auth/adapter/out/oauth/GoogleOAuthClient.java:41` (Kakao, Naver 동일)
- **내용**: `RestClient.create()`는 기본 타임아웃이 없어 외부 OAuth 서버 무응답 시 스레드가 무기한 대기할 수 있음. 스레드 풀 고갈 위험.
- **판정**: ✅ 수용
- **AI 분석**: 외부 서비스 호출에는 반드시 타임아웃이 필요함. connect timeout 5초, read timeout 10초 정도가 적절. 3개 OAuth 클라이언트 모두 동일하게 적용 필요.
- **결정**: 수용
- **의견**: ai 동의

### 5. OAuth 응답 필드 null 검증 누락 (NPE 위험)
- **파일**: `auth/adapter/out/oauth/GoogleOAuthClient.java:70`, `KakaoOAuthClient.java:70`, `NaverOAuthClient.java:65`
- **내용**: OAuth Provider 응답에서 providerId, 중첩 객체(kakao_account, response) 등이 null일 수 있으며 검증 없이 사용하면 NPE 발생.
- **판정**: ✅ 수용
- **AI 분석**: 외부 API 응답은 신뢰할 수 없으므로 방어적 검증이 필수임. Provider 장애나 예상치 못한 응답 형식에 대비해야 함.
- **결정**: 수용
- **의견**: ai 동의

### 6. access_token 교환 응답 null 검증 누락
- **파일**: `auth/adapter/out/oauth/GoogleOAuthClient.java:94`, `NaverOAuthClient.java:96` (Kakao 동일)
- **내용**: 토큰 교환 응답에서 `access_token`이 없을 경우 null 반환되어 후속 API 호출에서 NPE 발생. OAuth 에러 응답(invalid_grant 등)도 미처리.
- **판정**: ✅ 수용
- **AI 분석**: Authorization Code 만료, 중복 사용 등으로 토큰 교환 실패 가능. access_token null 시 명확한 예외를 던져야 함.
- **결정**: 수용
- **의견**: ai 동의

### 7. PII(이메일) INFO 레벨 로깅
- **파일**: `auth/application/service/AuthService.java:60`
- **내용**: INFO 레벨에서 이메일 주소를 그대로 로깅하면 프로덕션 로그에 개인정보가 남음. GDPR/개인정보보호법 위반 가능성.
- **판정**: ✅ 수용
- **AI 분석**: 프로덕션 로그에 PII가 남으면 안 됨. DEBUG로 변경하거나 이메일을 마스킹(u***@example.com) 처리해야 함.
- **결정**: 수용
- **의견**: ai 동의

### 8. Refresh Token 만료 시 잘못된 예외 타입
- **파일**: `auth/application/service/AuthService.java:111`
- **내용**: `JwtTokenProvider.validateToken()`이 모든 만료 토큰에 대해 `TokenExpiredException.accessToken()`을 던짐. Refresh Token 만료 시에는 `refreshToken()` 팩토리를 사용해야 함.
- **판정**: ✅ 수용
- **AI 분석**: 사용자에게 "다시 로그인해주세요" 메시지를 정확히 전달하려면 토큰 종류를 구분해야 함. `getUserIdFromToken` 대신 별도 메서드 or 파라미터화 필요.
- **결정**: 수용
- **의견**: ai 동의

### 9. Auth 예외가 DomainException을 상속하지 않음
- **파일**: `auth/domain/exception/TokenExpiredException.java:25`, `TokenInvalidException.java:17`, `OAuthEmailRequiredException.java:21`, `RefreshTokenReusedException.java:29`
- **내용**: 기존 프로젝트의 GlobalExceptionHandler가 DomainException 기반으로 동작하는데, auth 예외들이 RuntimeException을 직접 상속하여 핸들링되지 않고 500으로 응답됨.
- **판정**: ✅ 수용
- **AI 분석**: 프로젝트 패턴에 맞게 DomainException을 상속하거나, GlobalExceptionHandler에 별도 핸들러를 추가해야 함. 현재 상태로는 401/400 대신 500이 반환됨.
- **결정**: 수용
- **의견**: ai 동의

### 10. JwtProperties 설정값 검증 누락
- **파일**: `auth/infrastructure/jwt/JwtProperties.java:38`
- **내용**: secretKey가 비어있거나, expiration이 0/음수일 경우 런타임에 문제 발생. @Validated + @NotBlank + @Positive로 fail-fast 검증 권장.
- **판정**: ✅ 수용
- **AI 분석**: spring-boot-starter-validation 의존성이 이미 있으므로 @Validated 추가만으로 적용 가능. 잘못된 설정으로 인한 런타임 오류를 사전 방지함.
- **결정**: 수용
- **의견**: ai 동의

### 11. SecurityConfig에서 actuator/test 엔드포인트 과도 허용
- **파일**: `auth/infrastructure/security/SecurityConfig.java:76`
- **내용**: `/actuator/**` 전체와 `/api/v1/test/**`가 permitAll로 설정됨. actuator는 민감 정보 노출 가능, test 엔드포인트는 프로덕션에서 비활성화 필요.
- **판정**: ⚠️ 선택적
- **AI 분석**: 현재 actuator는 health/info/prometheus/metrics만 노출 중이고, test 컨트롤러는 개발용임. 프로덕션 배포 전 Profile 분리나 엔드포인트 제한이 필요하지만, 현재 개발 단계에서는 급하지 않음.
- **결정**: 보류
- **의견**: docs/todo/Todo.md에 기록

### 12. User.isBlocked() NPE 위험
- **파일**: `user/domain/User.java:72`
- **내용**: `warningCount`와 `isActive`가 wrapper 타입(Integer, Boolean)이라 reconstitute()로 복원 시 null이면 NPE 발생.
- **판정**: ⚠️ 선택적
- **AI 분석**: reconstitute()는 DB에서 복원할 때만 사용되며, DB에는 NOT NULL 제약이 있어 실제로 null이 될 가능성은 낮음. 하지만 방어적으로 primitive 타입(int, boolean) 사용이 더 안전함.
- **결정**: 수용
- **의견**: 방어적으로

### 13. Refresh Token 평문 저장 → 해시 저장 권장 (Nitpick)
- **파일**: `auth/adapter/out/redis/RefreshTokenRedisAdapter.java:32-57`
- **내용**: Redis 유출 시 토큰 재사용 위험을 줄이기 위해 SHA-256 해시 저장/비교 권장.
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 보안 강화 관점에서 유효한 제안임. Redis 접근이 탈취되면 Refresh Token을 그대로 사용할 수 있음. 다만 Token Rotation이 이미 적용되어 있어 리스크가 어느 정도 완화됨.
- **결정**: 수용
- **의견**: Refresh Token이 탈취되면 치명적 보안 강화 필요

### 14. JwtTokenProvider Secret Key 초기화 예외 처리 (Nitpick)
- **파일**: `auth/infrastructure/jwt/JwtTokenProvider.java:35-39`
- **내용**: 잘못된 Base64 인코딩이나 키 길이 부족 시 명확하지 않은 예외 발생. 설정 검증과 에러 메시지 추가 권장.
- **판정**: ✅ 수용 (Nitpick)
- **AI 분석**: JwtProperties에 @Validated를 추가하면 secretKey 빈 값은 방지되지만, Base64 디코딩 실패나 키 길이 부족은 별도 검증 필요.
- **결정**: 수용
- **의견**: ai 동의

### 15. JwtAuthenticationFilter 예외 범위 과도 (Nitpick)
- **파일**: `auth/infrastructure/security/JwtAuthenticationFilter.java:60-63`
- **내용**: `Exception` 전체를 catch하여 NPE 등 예상치 못한 예외도 무시됨. JWT 관련 예외만 catch하고 나머지는 별도 로깅 권장.
- **판정**: ⚠️ 선택적 (Nitpick)
- **AI 분석**: 필터에서 예외가 전파되면 요청 자체가 실패하므로 catch-all이 안전한 선택이기도 함. 다만 warn 레벨 로깅 추가는 디버깅에 유리함.
- **결정**: 수용
- **의견**: ai 동의

### 16. AuthController provider 입력 검증 (Nitpick)
- **파일**: `auth/adapter/in/controller/AuthController.java:66-71`
- **내용**: `OAuthProvider.valueOf()` 실패 시 IllegalArgumentException → 500 응답. 400 Bad Request로 처리가 적절.
- **판정**: ✅ 수용 (Nitpick)
- **AI 분석**: GlobalExceptionHandler에서 IllegalArgumentException을 처리하지 않으면 500으로 응답됨. try-catch로 400 반환 또는 핸들러 추가 필요.
- **결정**: 수용
- **의견**: ai 동의

---

## 요약

| 판정 | 개수 | 항목 |
|------|------|------|
| ✅ 수용 | 12개 | state 파라미터, onboarded 하드코딩, Dead Code, 타임아웃, null 검증(2), PII 로깅, 토큰 예외 구분, DomainException 상속, JwtProperties 검증, Secret Key 초기화, provider 입력 검증 |
| ⚠️ 선택적 | 4개 | actuator/test 허용, isBlocked NPE, 해시 저장, 필터 예외 범위 |
| ❌ 거부 | 0개 | - |
