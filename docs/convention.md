# Coding Convention

> 코딩 컨벤션 및 예외 처리 규칙

---

## 1. 명명 규칙

- 클래스: PascalCase
- 메서드/변수: camelCase
- 상수: UPPER_SNAKE_CASE
- 패키지: lowercase

---

## 2. DTO

- Request/Response DTO → `record` 사용
- 내부 전달용 DTO → `record` 또는 `@Data`

---

## 3. Entity ↔ DTO 변환

- Mapper 클래스에서 처리
- Entity ↔ DTO 서로 직접 참조 금지

---

## 4. 의존성 주입

- 생성자 주입 (`@RequiredArgsConstructor`)
- `@Autowired` 필드 주입 금지

---

## 5. 메서드

- 20~30라인 넘어가면 분리 고려

---

## 6. 주석 스타일

- 공개 API → JavaDoc (`/** */`)
- 복잡한 로직 → 인라인 주석

---

## 7. 금지 사항 (Anti-Patterns)

- `@Autowired` 필드 주입 금지 → 생성자 주입 사용
- Entity 직접 Controller에서 반환 금지 → DTO 변환 필수
- `throw new RuntimeException()` 금지 → 커스텀 예외 사용
- N+1 문제 방지 (Fetch Join, Batch Size 고려)
- 시간 비교 시 클라이언트 시간 신뢰 금지 → 서버 시간 기준 통일

---

## 8. Exception Handling

### 예외 계층 구조
- BusinessException (최상위 커스텀 예외)
    - 도메인 규칙 위반 (예: 입찰 금액 부족, 경매 종료됨)
    - 리소스 없음 (예: 경매 없음, 유저 없음)
    - 권한/상태 오류 (예: 본인 경매 입찰, 이미 낙찰됨)

### 예외 네이밍 규칙
- 클래스명: `{도메인}{상황}Exception` (예: AuctionNotFoundException, BidTooLowException)
- 에러 코드: `UPPER_SNAKE_CASE` (예: AUCTION_NOT_FOUND, BID_TOO_LOW)

### HTTP Status Code 매핑
| 상황 | HTTP Status |
|------|-------------|
| 리소스 없음 | 404 |
| 잘못된 요청 (검증 실패, 중복 등) | 400 |
| 권한 없음 | 403 |
| 서버 오류 | 500 |