# API Specification (API 명세)

> AI 참고용 API 구조 요약

---

## 공통 사항

### Base URL
```
/api/v1
```

### 인증
- 인증 방식: JWT
- Header: `Authorization: Bearer {token}`

### 공통 Response 형식
```json
{
  "success": true,
  "data": { ... },
  "serverTime": "2026-01-06T12:00:00",
  "error": null
}
```

### 공통 Error Response 형식
```json
{
  "success": false,
  "data": null,
  "serverTime": "2026-01-06T12:00:00",
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지",
    "timestamp": "2026-01-06T12:00:00"
  }
}
```

---

## API 엔드포인트

### User (사용자)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /users/signup | 회원가입 |
| POST | /users/login | 로그인 |
| GET | /users/me | 내 정보 조회 |
| GET | /users/me/bids | 내 입찰 내역 조회 |

### Auction (경매)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /auctions | 경매 등록 |
| GET | /auctions | 경매 목록 조회 |
| GET | /auctions/{auctionId} | 경매 상세 조회 |
| PUT | /auctions/{auctionId} | 경매 수정 (첫 입찰 전만 가능) |
| DELETE | /auctions/{auctionId} | 경매 취소 (첫 입찰 후 취소 시 패널티) |

### Bid (입찰)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /auctions/{auctionId}/bids | 입찰하기 |
| POST | /auctions/{auctionId}/instant-buy | 즉시 구매 |
| GET | /auctions/{auctionId}/bids | 입찰 내역 조회 |

### Transaction (거래)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /transactions/{transactionId}/payment | 낙찰 결제 |
| GET | /transactions/{transactionId} | 거래 상세 조회 |
| POST | /transactions/{transactionId}/second-chance | 2순위 구매 권한 수락/거절 |

### Notification (알림)
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /notifications | 알림 목록 조회 |

### WebSocket (실시간)
| Type | Endpoint | 설명 |
|------|----------|------|
| SUBSCRIBE | /topic/auction/{auctionId} | 실시간 현재가 구독 |

---

## 주요 Error Code

| Code | HTTP Status | 설명 |
|------|-------------|------|
| AUCTION_NOT_FOUND | 404 | 경매 없음 |
| BID_TOO_LOW | 400 | 최소 입찰 단위 미만 |
| AUCTION_ENDED | 400 | 이미 종료된 경매 |
| SELF_BID_NOT_ALLOWED | 400 | 본인 경매 입찰 불가 |
| INSTANT_BUY_DISABLED | 400 | 즉시 구매 비활성화 (90% 이상) |
| PAYMENT_EXPIRED | 400 | 결제 기한 만료 |
| NOT_AUCTION_OWNER | 403 | 본인 경매 아님 |
| AUCTION_ALREADY_HAS_BID | 400 | 입찰 존재 시 수정/취소 불가 |

---

## WebSocket 메시지 유형

| Type | 설명 |
|------|------|
| PRICE_UPDATE | 현재가 변경 |
| AUCTION_EXTENDED | 경매 연장 |
| AUCTION_ENDED | 경매 종료 |

---

## 알림 유형

| Type | 설명 |
|------|------|
| OUTBID | 더 높은 입찰 발생 |
| AUCTION_WON | 낙찰 |
| AUCTION_ENDING | 경매 종료 임박 |
| PAYMENT_REMINDER | 결제 기한 알림 |
| SECOND_CHANCE | 2순위 구매 권한 부여 |