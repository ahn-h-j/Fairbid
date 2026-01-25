# User Feature Specification

> User(사용자) 도메인 구현 스펙 정의서

---

## 1. 도메인 모델링

### 설계 결정
- **단일 User 도메인**: Identity Context에 User 도메인 하나만 존재
- **다른 Context 참조 방식**: sellerId, bidderId 등 `Long` 타입으로 참조 (현행 유지)
- **Seller/Buyer 분리 없음**: 같은 사용자가 판매자/구매자 역할을 동시에 수행하며, 별도 역할 객체 불필요
- **닉네임 표시**: 다른 Context에서 닉네임이 필요하면 항상 최신 닉네임을 JOIN으로 조회 (스냅샷 저장 안 함)

### User 도메인 속성
| 속성 | 타입 | 설명 | 제약조건 |
|------|------|------|----------|
| userId | Long | PK | Auto-generated |
| email | String | OAuth Provider 제공 이메일 | UK, 수정 불가 |
| nickname | String | 사용자 닉네임 | UK, 2~20자, 문자 제한 없음, 변경 횟수 제한 없음 |
| phoneNumber | String | 전화번호 | UK, 최초 설정 후 변경 불가 |
| provider | Enum | OAuth Provider (KAKAO, NAVER, GOOGLE) | Not Null |
| providerId | String | Provider 고유 사용자 ID | Not Null |
| warningCount | Integer | 경고 횟수 | 기본값 0 |
| isActive | Boolean | 활성 상태 | 기본값 true |
| createdAt | LocalDateTime | 가입일시 | JPA Auditing |
| updatedAt | LocalDateTime | 수정일시 | JPA Auditing |

### DB 스키마 (기존 schema.md 대체)
- 기존 USER 테이블의 `password`, `name` 컬럼 제거
- `nickname`, `provider`, `providerId` 컬럼 추가
- 테이블 새로 생성 가능 (기존 데이터 없음)

---

## 2. 인증 (Authentication)

### OAuth2 소셜 로그인
- **지원 Provider**: 카카오, 네이버, 구글
- **자체 회원가입 없음**: OAuth 전용
- **로그인 플로우**: 리다이렉트 방식
- **이메일 정책**: 모든 Provider에서 이메일 필수 동의로 설정. 이메일 동의 거부 시 가입 불가
- **Provider 토큰**: 저장하지 않음. 로그인 시 사용자 정보 조회용으로만 사용 후 버림

### 인증 플로우
```
[사용자] → [로그인 버튼 클릭]
    → [프론트: 이전 페이지 URL을 localStorage에 저장]
    → [GET /api/v1/auth/oauth2/{provider} 로 이동]
    → [서버: OAuth Provider 인증 페이지로 리다이렉트 (state 파라미터 포함)]
    → [Provider 인증 완료]
    → [Provider → GET /api/v1/auth/oauth2/callback/{provider} 로 리다이렉트]
    → [서버: state 검증 (CSRF 방지)]
    → [서버: Code → Provider Access Token 교환]
    → [서버: Provider API로 사용자 정보 조회 (email, name, providerId)]
    → [서버: DB에서 provider + providerId로 사용자 조회]
        → 차단된 사용자: 프론트 /login?error=BLOCKED 로 리다이렉트
        → 이메일 미제공: 프론트 /login?error=EMAIL_REQUIRED 로 리다이렉트
        → 기존 사용자 (정상): Refresh Token 생성
        → 신규 사용자: User 레코드 생성 (nickname, phoneNumber = null) → Refresh Token 생성
    → [서버: Refresh Token을 HttpOnly 쿠키에 설정]
    → [서버: 프론트 /auth/callback 으로 302 리다이렉트]
    → [프론트: /auth/callback 페이지 로드]
    → [프론트: POST /api/v1/auth/refresh 호출 (쿠키 자동 전송)]
    → [프론트: Access Token 수신 → 메모리 저장]
    → [프론트: JWT의 onboarded 클레임 확인]
        → onboarded=false: /onboarding 으로 이동
        → onboarded=true: localStorage의 이전 페이지 URL로 복귀
```

### OAuth State Parameter
- Spring Security OAuth2 Client가 자동으로 state 생성/검증 처리
- CSRF 공격 방지 목적

### 차단된 사용자 처리
- `warningCount >= 3` 또는 `isActive == false`인 경우 **JWT 발급 거부**
- OAuth 인증 성공해도 서버에서 로그인 차단
- 프론트엔드에 차단 사유 안내 메시지 표시

---

## 3. JWT 토큰 전략

### 토큰 구성
| 토큰 | 만료시간 | 저장 위치 (프론트) | 전달 방식 | 용도 |
|------|----------|-------------------|-----------|------|
| Access Token | 30분 | In-memory (변수) | Authorization: Bearer 헤더 | API 인증 |
| Refresh Token | 2주 | HttpOnly 쿠키 | Cookie (자동 전송) | Access Token 재발급 |

### Access Token Payload
```json
{
  "sub": "userId",
  "nickname": "닉네임",
  "onboarded": true,
  "iat": 1234567890,
  "exp": 1234569690
}
```

### Refresh Token 쿠키 설정
```
Set-Cookie: refresh_token={token}; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=1209600
```
- **HttpOnly**: JavaScript 접근 불가 (XSS 방어)
- **Secure**: HTTPS에서만 전송
- **SameSite=Strict**: 크로스 사이트 요청 시 쿠키 미전송 (CSRF 방어)
- **Path=/api/v1/auth**: 인증 엔드포인트에서만 전송 (불필요한 노출 방지)

### Refresh Token Rotation
- Refresh Token 사용 시 새 Refresh Token도 함께 발급 (기존 토큰 무효화)
- 이미 사용된(무효화된) Refresh Token으로 요청 시 → 해당 사용자의 모든 Refresh Token 무효화 (탈취 감지)
- 단일 세션만 허용: 새 로그인 시 기존 Refresh Token 무효화

### 세션 정책
- **단일 세션만 허용**: 한 사용자는 한 기기에서만 로그인 가능
- 다른 기기에서 로그인 시 기존 Refresh Token 삭제 (기존 세션 만료)
- Redis 저장: `refresh:{userId}` → 단일 키로 관리

### 토큰 갱신 플로우
```
[Access Token 만료 → API 401 응답]
    → [프론트: 갱신 Queue에 등록 (중복 방지)]
    → [프론트: POST /api/v1/auth/refresh (쿠키 자동 전송)]
    → [서버: Redis에서 Refresh Token 유효성 확인]
        → 유효: 새 Access Token + 새 Refresh Token(Rotation) 발급
        → 무효: 401 → 프론트에서 로그인 페이지로 이동
    → [프론트: 대기 중인 요청들 새 Access Token으로 재시도]
```

### 프론트엔드 토큰 갱신 구현
- **401 발생 시 갱신 + Queue 패턴**
- 첫 번째 401에서 갱신 요청 발생
- 갱신 진행 중 다른 API의 401은 Queue에 대기
- 갱신 성공 시 Queue의 모든 요청을 새 토큰으로 재시도
- 갱신 실패 시 Queue의 모든 요청에 에러 전파, 로그인 페이지로 이동

### Redis Refresh Token 저장 구조
```
Key: "refresh:{userId}"
Value: refreshToken (해시 저장)
TTL: 2주
```

---

## 4. 온보딩 (최초 가입 시)

### 수집 정보
| 필드 | 필수 | 검증 규칙 |
|------|------|-----------|
| 닉네임 | 필수 | 2~20자, UK 중복 검사 |
| 전화번호 | 필수 | 010-XXXX-XXXX 형식 검증, UK 중복 검사, SMS 인증 없음 |

### 온보딩 상태 판별
- **JWT의 `onboarded` 클레임으로 판별** (DB 조회 불필요)
- nickname과 phoneNumber가 모두 not null이면 `onboarded = true`
- 온보딩 완료 시 새 JWT 발급 (onboarded=true로 갱신)

### 온보딩 완료 조건
- 닉네임과 전화번호가 모두 설정되어야 온보딩 완료
- 온보딩 미완료 상태에서는 경매 등록/입찰 불가
- 조회는 가능 (비로그인도 조회 가능하므로)

### 중복 방지 정책
- **전화번호 UK**: Provider 무관하게 동일 전화번호로 중복 가입 불가
- **닉네임 UK**: 동일 닉네임 사용 불가
- 목적: 노쇼 패널티 회피를 위한 멀티 계정 방지

---

## 5. 프로필 관리

### 수정 가능 항목
| 항목 | 수정 가능 | 제한 | 비고 |
|------|-----------|------|------|
| 닉네임 | O | 제한 없음 | UK 중복 검사 필요. 변경 시 JWT 재발급 |
| 전화번호 | X | - | 최초 설정 후 변경 불가 |
| 이메일 | X | - | OAuth Provider에서 제공, 변경 불가 |

### 회원 탈퇴
- **Soft Delete 방식**: `isActive = false` 처리
- 진행 중 경매/미결제 건 검증 없이 비활성화만 처리 (1차 범위)
- 탈퇴된 계정으로 재로그인 시 차단 (isActive 체크)
- 탈퇴 시 Redis의 Refresh Token도 삭제

---

## 6. 마이페이지

### 구조
```
마이페이지
├── 프로필 섹션 (닉네임, 이메일, 전화번호, 경고 n/3)
├── 판매 탭
│   ├── [전체] [진행중] [결제대기] [거래완료] [유찰] 필터
│   └── 경매 카드 리스트 (제목, 현재가, 상태 배지, 등록일) - 무한스크롤
└── 구매 탭
    └── 입찰한 경매 리스트 (제목, 내 입찰가, 현재가, 상태) - 무한스크롤
```

### 판매 탭 상태 필터
| 필터 | 조건 |
|------|------|
| 전체 | 내가 등록한 모든 경매 |
| 진행중 | status = ACTIVE |
| 결제대기 | status = ENDED, 낙찰자 결제 대기 중 |
| 거래완료 | 결제 완료된 경매 |
| 유찰 | 입찰 없이 종료된 경매 |

### 구매 탭 표시 정보
| 항목 | 설명 |
|------|------|
| 내 입찰가 | 해당 경매에서 내 최고 입찰 금액 |
| 현재가 | 경매의 현재 최고 입찰가 |
| 상태 | 진행중 / 낙찰 / 패찰 / 결제대기 등 |

### 페이지네이션
- **Cursor 기반 무한스크롤** 적용
- 페이지 사이즈: 20건
- 정렬: 최신순 (생성일시 DESC)
- API 파라미터: `?cursor={lastId}&size=20&status={filter}`

### 경고 횟수 노출
- 마이페이지 프로필 섹션에 `경고 n/3` 형태로 표시
- 사용자가 현재 경고 상태를 인지할 수 있도록 투명하게 운영

---

## 7. 접근 제어 (Authorization)

### 페이지별 접근 권한
| 페이지 | 비로그인 | 로그인 | 온보딩 완료 |
|--------|----------|--------|-------------|
| 경매 목록 | O | O | O |
| 경매 상세 | O | O | O |
| 경매 등록 | X | X | O |
| 입찰 | X | X | O |
| 마이페이지 | X | O | O |
| 온보딩 | X | O | - |

### WebSocket 접근
- **비로그인도 실시간 경매 구독 허용** (경매 상세 조회와 동일 정책)
- 입찰 액션만 인증 필요 (기존 REST API에서 처리)

### 프론트엔드 접근 제어
- 비로그인 상태에서 로그인 필수 액션 시도 시 → 로그인 페이지로 이동
- 로그인 전 위치 저장 → 로그인 성공 후 해당 페이지로 복귀
- 온보딩 미완료 상태에서 입찰/등록 시도 시 → 온보딩 페이지로 이동

---

## 8. API 엔드포인트

### Auth (인증)
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | /api/v1/auth/oauth2/{provider} | OAuth 로그인 시작 (리다이렉트) | X |
| GET | /api/v1/auth/oauth2/callback/{provider} | OAuth 콜백 처리 | X |
| POST | /api/v1/auth/refresh | Access Token 재발급 (Refresh Token Rotation) | Refresh Token (쿠키) |
| POST | /api/v1/auth/logout | 로그아웃 (Refresh Token 삭제 + 쿠키 제거) | O |

### User (사용자)
| Method | Endpoint | 설명 | 인증 |
|--------|----------|------|------|
| GET | /api/v1/users/me | 내 정보 조회 | O |
| PUT | /api/v1/users/me | 프로필 수정 (닉네임) → 새 JWT 발급 | O |
| POST | /api/v1/users/me/onboarding | 온보딩 완료 (닉네임 + 전화번호) → 새 JWT 발급 | O |
| DELETE | /api/v1/users/me | 회원 탈퇴 (Soft Delete) | O |
| GET | /api/v1/users/me/auctions | 내 판매 경매 목록 (?cursor&size&status) | O |
| GET | /api/v1/users/me/bids | 내 입찰 경매 목록 (?cursor&size) | O |
| GET | /api/v1/users/check-nickname | 닉네임 중복 확인 (?nickname=xxx) | X |

---

## 9. 에러 코드

### 인증 관련
| Code | HTTP Status | 설명 |
|------|-------------|------|
| USER_BLOCKED | 403 | 차단된 사용자 (warningCount >= 3) |
| USER_DEACTIVATED | 403 | 탈퇴한 사용자 (isActive = false) |
| TOKEN_EXPIRED | 401 | Access Token 만료 |
| TOKEN_INVALID | 401 | 유효하지 않은 토큰 |
| REFRESH_TOKEN_EXPIRED | 401 | Refresh Token 만료/무효 |
| REFRESH_TOKEN_REUSE | 401 | 이미 사용된 Refresh Token 재사용 (탈취 의심) |
| OAUTH_EMAIL_REQUIRED | 400 | OAuth 이메일 동의 거부 |
| ONBOARDING_REQUIRED | 403 | 온보딩 미완료 상태에서 인증 필요 액션 시도 |

### 사용자 관련
| Code | HTTP Status | 설명 |
|------|-------------|------|
| USER_NOT_FOUND | 404 | 사용자 없음 |
| NICKNAME_DUPLICATE | 400 | 닉네임 중복 |
| PHONE_NUMBER_DUPLICATE | 400 | 전화번호 중복 |
| PHONE_NUMBER_INVALID | 400 | 전화번호 형식 오류 |
| NICKNAME_INVALID | 400 | 닉네임 길이/형식 오류 |

---

## 10. 프론트엔드 화면 구성

### 새로 추가되는 페이지
| 페이지 | 경로 | 설명 |
|--------|------|------|
| 로그인 | /login | OAuth Provider 선택 화면 |
| 온보딩 | /onboarding | 닉네임 + 전화번호 입력 |
| 마이페이지 | /mypage | 프로필 + 판매/구매 탭 |
| OAuth 콜백 | /auth/callback | OAuth 콜백 처리 (토큰 수신 후 리다이렉트) |

### 헤더 변경
```
[비로그인 상태]
┌────────────────────────────────────────────┐
│ FairBid    경매 목록  경매 등록     로그인    │
└────────────────────────────────────────────┘

[로그인 상태]
┌────────────────────────────────────────────┐
│ FairBid    경매 목록  경매 등록   닉네임 ▼   │
│                                  ├ 마이페이지│
│                                  └ 로그아웃  │
└────────────────────────────────────────────┘
```

### 로그인 페이지 구성
```
┌─────────────────────────┐
│      FairBid 로고        │
│   호구 없는 경매          │
│                          │
│  [카카오로 로그인]         │
│  [네이버로 로그인]         │
│  [구글로 로그인]           │
└─────────────────────────┘
```

### 프론트엔드 인증 상태 관리
- Access Token: 모듈 스코프 변수에 저장 (React Context로 상태 공유)
- 앱 초기화 시(새로고침): `/api/v1/auth/refresh` 호출하여 Access Token 재발급
- 인증 상태: `LOADING | UNAUTHENTICATED | ONBOARDING_REQUIRED | AUTHENTICATED`

---

## 11. 백엔드 패키지 구조

```
src/main/java/com/cos/fairbid/
├── user/
│   ├── domain/
│   │   ├── User.java                    # User 도메인 모델 (POJO)
│   │   ├── OAuthProvider.java           # Enum: KAKAO, NAVER, GOOGLE
│   │   └── exception/
│   │       ├── UserNotFoundException.java
│   │       ├── UserBlockedException.java
│   │       ├── NicknameDuplicateException.java
│   │       └── PhoneNumberDuplicateException.java
│   │
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── GetUserUseCase.java
│   │   │   │   ├── UpdateUserUseCase.java
│   │   │   │   ├── OnboardingUseCase.java
│   │   │   │   ├── DeactivateUserUseCase.java
│   │   │   │   └── GetUserAuctionsUseCase.java
│   │   │   └── out/
│   │   │       ├── LoadUserPort.java
│   │   │       ├── SaveUserPort.java
│   │   │       └── LoadUserAuctionsPort.java
│   │   └── service/
│   │       ├── UserService.java
│   │       └── UserAuctionService.java
│   │
│   └── adapter/
│       ├── in/
│       │   ├── controller/
│       │   │   └── UserController.java
│       │   └── dto/
│       │       ├── UserResponse.java
│       │       ├── UpdateNicknameRequest.java
│       │       ├── OnboardingRequest.java
│       │       └── UserAuctionResponse.java
│       │
│       └── out/
│           └── persistence/
│               ├── entity/
│               │   └── UserEntity.java
│               ├── repository/
│               │   └── UserJpaRepository.java
│               └── mapper/
│                   └── UserMapper.java
│
└── auth/
    ├── domain/
    │   └── RefreshToken.java
    │
    ├── application/
    │   ├── port/
    │   │   ├── in/
    │   │   │   ├── OAuthLoginUseCase.java
    │   │   │   ├── RefreshTokenUseCase.java
    │   │   │   └── LogoutUseCase.java
    │   │   └── out/
    │   │       ├── OAuthClientPort.java
    │   │       ├── SaveRefreshTokenPort.java
    │   │       └── LoadRefreshTokenPort.java
    │   └── service/
    │       └── AuthService.java
    │
    └── adapter/
        ├── in/
        │   ├── controller/
        │   │   └── AuthController.java
        │   └── dto/
        │       ├── OAuthLoginResponse.java
        │       └── TokenResponse.java
        │
        └── out/
            ├── oauth/
            │   ├── KakaoOAuthClient.java
            │   ├── NaverOAuthClient.java
            │   └── GoogleOAuthClient.java
            └── redis/
                └── RefreshTokenRedisAdapter.java
```

---

## 12. 기술 구현 상세

### Spring Security 설정
- `spring-boot-starter-oauth2-client` 의존성 추가
- `spring-boot-starter-security` 의존성 추가
- SecurityFilterChain 설정: 인증 불필요 경로와 인증 필수 경로 분리
- JWT 인증 필터 추가 (OncePerRequestFilter)
- CORS 설정: 프론트엔드 도메인 허용
- CSRF: SameSite=Strict 쿠키로 방어하므로 CSRF 토큰 비활성화

### JWT 라이브러리
- `io.jsonwebtoken:jjwt` (JJWT) 사용

### 기존 코드 변경 사항
- `AuctionController`: `sellerId = 1L` → SecurityContext에서 userId 추출
- `BidController`: `X-User-Id` 헤더 → SecurityContext에서 userId 추출
- `frontend/src/api/client.js`: X-User-Id 헤더 → Authorization Bearer 헤더 + 401 갱신 로직
- `frontend/src/components/UserSelector.jsx`: 제거 (테스트용)
- `frontend/src/components/Layout.jsx`: 헤더에 로그인/닉네임 표시

---

## 13. 향후 확장 고려사항 (현재 범위 밖)

- SMS 인증 (전화번호 실소유 검증)
- 소셜 계정 연동 (하나의 계정에 여러 Provider 연결)
- 프로필 사진 업로드
- 회원 탈퇴 시 진행 중 경매 처리 로직
- 닉네임 변경 쿨다운/이력 관리
- 관리자 페이지 (사용자 관리, 차단 해제)
- 멀티 세션 지원 (여러 기기 동시 로그인)
- OAuth Provider 토큰 저장 (프로필 사진 동기화 등)
