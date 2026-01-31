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
| 이메일 | UK | OAuth Provider 제공 |
| 닉네임 | UK | 2~20자 |
| 전화번호 | UK | 최초 설정 후 변경 불가 |
| 역할 | | USER, ADMIN |
| Provider | | KAKAO, NAVER, GOOGLE |
| ProviderId | | Provider 고유 ID |
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
| 직거래가능여부 | | |
| 택배가능여부 | | |
| 직거래희망위치 | | 직거래 가능 시 |
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

### TRADE (거래)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| 거래ID | PK | |
| 경매ID | FK UK | AUCTION.경매ID |
| 판매자ID | FK | USER.사용자ID |
| 구매자ID | FK | USER.사용자ID |
| 최종낙찰가 | | |
| 상태 | | AWAITING_METHOD_SELECTION, AWAITING_ARRANGEMENT, ARRANGED, COMPLETED, CANCELLED |
| 거래방식 | | DIRECT, DELIVERY |
| 응답마감일시 | | |
| 거래생성일시 | | |
| 거래완료일시 | | |

---

### DIRECT_TRADE_INFO (직거래 정보)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| ID | PK | |
| 거래ID | FK | TRADE.거래ID |
| 거래장소 | | |
| 만남날짜 | | |
| 만남시간 | | |
| 상태 | | PROPOSED, COUNTER_PROPOSED, ACCEPTED |
| 제안자ID | FK | USER.사용자ID |

---

### DELIVERY_INFO (택배 정보)

| 컬럼 | 설명 | 비고 |
|------|------|------|
| ID | PK | |
| 거래ID | FK | TRADE.거래ID |
| 수령인명 | | |
| 수령인연락처 | | |
| 우편번호 | | |
| 주소 | | |
| 상세주소 | | |
| 택배사 | | |
| 송장번호 | | |
| 상태 | | AWAITING_ADDRESS, ADDRESS_SUBMITTED, SHIPPED, DELIVERED |

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
AUCTION 1:1 TRADE (낙찰, Optional)
TRADE 1:1 DIRECT_TRADE_INFO (직거래 시, Optional)
TRADE 1:1 DELIVERY_INFO (택배 시, Optional)
```
