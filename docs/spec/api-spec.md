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

### Trade (거래)
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /trades/{tradeId} | 거래 상세 조회 |
| GET | /trades/my | 내 거래 목록 조회 |
| POST | /trades/{tradeId}/method | 거래 방식 선택 (둘 다 가능 시) |
| POST | /trades/{tradeId}/direct/propose | 직거래 시간 제안 |
| POST | /trades/{tradeId}/direct/accept | 직거래 제안 수락 |
| POST | /trades/{tradeId}/direct/counter | 직거래 역제안 |
| POST | /trades/{tradeId}/delivery/address | 배송지 입력 |
| POST | /trades/{tradeId}/delivery/payment | 입금 완료 확인 (구매자) |
| POST | /trades/{tradeId}/delivery/payment/verify | 입금 확인 (판매자) |
| POST | /trades/{tradeId}/delivery/payment/reject | 미입금 처리 (판매자) |
| POST | /trades/{tradeId}/delivery/ship | 송장번호 입력 |
| POST | /trades/{tradeId}/delivery/confirm | 수령 확인 |
| POST | /trades/{tradeId}/complete | 거래 완료 확인 |

### User (사용자)
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /users/me | 내 정보 조회 |
| PUT | /users/me | 프로필 수정 (닉네임) |
| POST | /users/me/onboarding | 온보딩 완료 |
| DELETE | /users/me | 회원 탈퇴 |
| GET | /users/me/auctions | 내 판매 경매 목록 |
| GET | /users/me/bids | 내 입찰 경매 목록 |
| PUT | /users/me/bank-account | 판매 계좌 등록/수정 |
| GET | /users/check-nickname | 닉네임 중복 확인 |

### Auth (인증)
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /auth/oauth2/{provider} | OAuth 로그인 시작 |
| GET | /auth/oauth2/callback/{provider} | OAuth 콜백 처리 |
| POST | /auth/refresh | Access Token 재발급 |
| POST | /auth/logout | 로그아웃 |

### Admin (관리자)
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /admin/stats/overview | 통계 개요 |
| GET | /admin/auctions | 경매 관리 목록 |
| GET | /admin/auctions/{id} | 경매 상세 (입찰 이력 포함) |
| POST | /admin/auctions/{id}/force-close | 경매 강제 종료 |
| GET | /admin/users | 유저 관리 목록 |
| GET | /admin/users/{id} | 유저 상세 (활동 이력 포함) |

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
| RESPONSE_DEADLINE_EXPIRED | 400 | 응답 기한 만료 |
| TRADE_NOT_FOUND | 404 | 거래 없음 |
| NOT_TRADE_PARTICIPANT | 403 | 거래 참여자 아님 |
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
| TRADE_CREATED | 거래 생성됨 |
| METHOD_SELECTED | 거래 방식 선택됨 |
| DIRECT_TIME_PROPOSAL | 직거래 시간 제안 |
| DIRECT_ACCEPTED | 직거래 약속 확정 |
| ADDRESS_SUBMITTED | 배송지 입력됨 |
| SHIPPED | 상품 발송됨 |
| TRADE_COMPLETED | 거래 완료 |
| RESPONSE_REMINDER | 응답 기한 임박 |
| SECOND_CHANCE | 2순위 구매 권한 부여 |