# Database Schema (ERD)

> 경매 프로젝트 데이터베이스 스키마 정의

---
## 공통 사항

### 시간 관리
- 모든 테이블의 생성일시/수정일시는 JPA Auditing으로 자동 관리
- `@EntityListeners(AuditingEntityListener.class)`
- `@CreatedDate`, `@LastModifiedDate`

### 기본값
- USER.경고횟수 → 0
- USER.활성상태 → true
- AUCTION.연장횟수 → 0
- AUCTION.총입찰수 → 0
- AUCTION.상태 → PENDING
- AUCTION_IMAGE.노출순서 → 1

## 테이블 구조

### USER (사용자)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| 사용자ID | PK | |
| 이메일 | UK | |
| 비밀번호 | | |
| 이름 | | |
| 전화번호 | UK | |
| 가입일시 | | |
| 수정일시 | | |
| 활성상태 | | |
| 경고횟수 | | |

---

### AUCTION (경매)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| 경매ID | PK | |
| 판매자ID | FK | USER.사용자ID |
| 제목 | | |
| 설명 | | |
| 시작가 | | |
| 현재가 | | |
| 즉시구매가 | | |
| 경매시작시간 | | |
| 예정종료시간 | | |
| 실제종료시간 | | |
| 연장횟수 | | |
| 현재입찰단위 | | |
| 상태 | | |
| 카테고리 | | |
| 총입찰수 | | |
| 낙찰자ID | FK | USER.사용자ID |
| 등록일시 | | |
| 수정일시 | | |

---

### BID (입찰)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| 입찰ID | PK | |
| 경매ID | FK | AUCTION.경매ID |
| 입찰자ID | FK | USER.사용자ID |
| 입찰금액 | | |
| 입찰시간 | | |
| 입찰유형 | | |

---

### NOTIFICATION (알림)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| 알림ID | PK | |
| 사용자ID | FK | USER.사용자ID |
| 알림유형 | | |
| 생성일시 | | |
| 메타데이터 | | |

---

### AUCTION_IMAGE (경매이미지)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| 이미지ID | PK | |
| 경매ID | FK | AUCTION.경매ID |
| 이미지URL | | |
| 노출순서 | | |

---

### WINNING (낙찰)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| 낙찰ID | PK | |
| 경매ID | FK | AUCTION.경매ID |
| 순위 | | 1 or 2 |
| 입찰자ID | FK | USER.사용자ID |
| 입찰금액 | | |
| 상태 | | PENDING_PAYMENT, PAID, NO_SHOW, FAILED |
| 결제마감일시 | | |
| 생성일시 | | |

---

### TRANSACTION (거래)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| 거래ID | PK | |
| 경매ID | FK UK | AUCTION.경매ID |
| 판매자ID | FK | USER.사용자ID |
| 구매자ID | FK | USER.사용자ID |
| 최종낙찰가 | | |
| 상태 | | |
| 결제마감일시 | | |
| 거래생성일시 | | |
| 결제완료일시 | | |

---

## 관계

```
USER 1:N AUCTION (생성)
USER 1:N BID (입찰)
USER 1:N WINNING (낙찰)
USER 1:N NOTIFICATION (수신)
AUCTION 1:N BID (입찰받음)
AUCTION 1:N WINNING (낙찰후보, 최대 2개)
AUCTION 1:N AUCTION_IMAGE (보유)
AUCTION 1:1 TRANSACTION (낙찰, Optional)
```
