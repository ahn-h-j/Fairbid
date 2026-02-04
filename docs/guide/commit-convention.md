# Git Commit Convention

## Instructions

커밋 시 반드시 아래 컨벤션을 따를 것.

- 변경 사항에 맞는 type 선택
- 변경된 계층에 맞는 scope 선택
- 여러 계층 변경 시 가장 핵심 변경 기준으로 scope 선택
- 커밋은 하나의 논리적 단위로 분리
- 기능 구현 시 기술 계층(domain → infra → api 등) 단위로 커밋 분리

## Format

```
<type>(<scope>): <subject>
```

## Type

- `feat`: 새로운 기능
- `fix`: 버그 수정
- `refactor`: 리팩토링 (기능 변경 없음)
- `docs`: 문서 수정
- `test`: 테스트 추가/수정
- `chore`: 빌드, 설정 파일 수정
- `style`: 코드 포맷팅 (동작 변경 없음)
- `perf`: 성능 개선

## Scope

### 비즈니스 도메인 (Bounded Context)

- `identity`: User, 인증/계정
- `auction`: Seller, AuctionItem (경매 물품 관리)
- `bidding`: Buyer, AuctionSession, Bid (실시간 입찰)
- `trade`: Transaction (거래)
- `support`: Notification (알림)

### 기술 계층

- `api`: Controller, REST API
- `domain`: Domain 모델, 비즈니스 로직
- `infra`: Adapter (persistence, redis, messaging)
- `config`: 설정 파일
- `test`: 테스트 코드
- `docs`: 문서
- `deps`: 의존성 (build.gradle)

## Rules

- subject는 한글로 작성
- 50자 이내
- 마침표 없음
- 명령문으로 작성

## Subject 작성법

- "무엇을" + "어떻게 했는지" 포함
- 변경 대상(기능, 로직, 화면 등)을 명시
- "수정", "추가", "변경" 단독 사용 금지
- 동작의 결과나 목적이 드러나게 작성
- 한글로 작성
- 50자 이내
- 마침표 없음
- 명령문으로 작성

## Body 작성법 (필수)

- subject와 body 사이에 빈 줄 필수
- "왜" 변경했는지 배경/목적 설명
- "무엇을" 변경했는지 주요 변경 내용 나열
- 72자 이내로 줄바꿈
- 불릿(-) 사용하여 항목별 정리

---

## 금지 사항

- `Co-Authored-By` 태그 사용 금지