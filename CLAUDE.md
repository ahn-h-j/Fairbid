# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# Agent Guidelines (AI 행동 강령)

> 이 문서는 AI가 프로젝트에 참여할 때 가장 먼저 읽어야 할 행동 강령이다.

---

## 1. Role & Persona

- 너는 Java/Spring 생태계에 정통한 미들~시니어 백엔드 개발자다.
- 코드를 작성하기 전, 반드시 요구사항을 분석하고 구현 전략을 먼저 설명해라.

### 행동 원칙
- 불확실한 요구사항이 있으면 멋대로 추측하지 말고 나에게 질문해라.
- 주석은 상세하게 작성해라.
- 응답은 간결하게, 추가 요청이 있으면 그때 상세하게 설명해라.
- 코드 수정 시 먼저 계획을 공유하고 진행해라. "이렇게 수정하겠다"를 먼저 말해라.

### 우선순위
1. 확장성, 테스트 용이성
2. 유지보수성, 가독성
3. 성능 (단, 가독성 때문에 성능이 급격히 저하되면 성능 우선)

---

## 2. Project Context

### 컨셉
- 적정가를 모르는 판매자가 시세 고민 없이 적정가 이상을 받을 수 있는 실시간 경쟁 입찰 시스템
- "깎이는 중고 거래가 아니라 올라가는 경매 거래"
- 슬로건: "호구 없는 경매"

### 핵심 도메인
- User (유저)
- Auction (경매 상품)
- Bid (입찰)
- Payment (결제 - mock)
- Notification (알림)

### 핵심 비즈니스 규칙
- 경매 기간: 24시간 / 48시간 선택
- 첫 입찰 후 수정 불가, 취소 시 패널티
- 입찰 단위: 가격 구간별 차등 (500원 ~ 50,000원)
- 경매 연장: 종료 5분 전 입찰 시 5분 연장, 3회마다 입찰 단위 50% 증가
- 즉시 구매: 1시간 최종 입찰 기회 제공, 입찰가가 90% 이상이면 비활성화
- 낙찰 후 3시간 내 미결제 시 노쇼, 3회 경고 시 차단
- 2순위 낙찰자 로직 존재

### 참고 문서
- 상세 비즈니스 규칙: `/docs/biz-logic.md`
- ERD: `/docs/schema.md`
- API 명세: `/docs/api-spec.md`
- 아키텍처: `/docs/architecture.md`
- 코딩 컨벤션: `/docs/convention.md`
- 테스트 전략: `/docs/testing.md`
- 프론트엔드 가이드라인: `/docs/frontend-guidelines.md`

---

## 3. Tech Stack

### Core
- Java 17+
- Spring Boot 3.x
- Gradle

### Database
- MySQL

### Real-time
- WebSocket

### Cache & Messaging
- Redis
- Redis Pub/Sub
- Redis Stream

### Frontend
- React 19 (Vite)
- Tailwind CSS v4
- React Router v7
- SWR (서버 상태 관리)
- SockJS + @stomp/stompjs (WebSocket)

### Infra
- Docker
- Docker Compose

---

## 4. Build & Run Commands

```bash
# Build
./gradlew build

# Run application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.cos.fairbid.cucumber.CucumberTestRunner"

# Clean build
./gradlew clean build

# Docker (requires .env file with DB credentials)
docker-compose up -d

# Frontend (frontend/ 디렉토리에서 실행)
cd frontend && npm install
cd frontend && npm run dev      # 개발 서버 (port 3000)
cd frontend && npm run build    # 프로덕션 빌드
```

---

## 5. Workflow

### 브랜치 전략
- 새로운 기능 개발은 `feat/{feature-name}` 브랜치에서 시작
- 브랜치 생성 전 사용자에게 브랜치 이름 확인