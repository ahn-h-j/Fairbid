# Spring 트랜잭션 - 개별 처리 분리 (REQUIRES_NEW + 클래스 분리)

> **작성일**: 2026-01-11
> **관련 파일**: `AuctionClosingService.java`, `AuctionClosingHelper.java`

## 문제 상황

여러 건의 경매를 처리할 때, 한 건 실패 시 전체 롤백되는 문제

## 해결 방법

두 가지가 **모두** 필요함:

1. **REQUIRES_NEW**: 각 처리를 새로운 트랜잭션으로 분리
2. **별도 클래스 분리**: Spring AOP 프록시가 동작하려면 필수

```java
@Service
public class AuctionClosingService {

    private final AuctionClosingHelper helper;  // 별도 클래스 주입

    @Transactional
    public void closeExpiredAuctions() {
        List<Auction> closingAuctions = auctionRepository.findClosingAuctions();

        for (Auction auction : closingAuctions) {
            try {
                helper.processAuctionClosing(auction.getId());  // 별도 트랜잭션
            } catch (Exception e) {
                // 이 경매만 실패, 다음 경매 계속 진행
            }
        }
    }
}

@Component
public class AuctionClosingHelper {

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // 새 트랜잭션
    public void processAuctionClosing(Long auctionId) {
        // 처리 로직
    }
}
```

## 왜 별도 클래스가 필요한가?

Spring AOP는 **같은 클래스 내 메서드 호출에는 적용되지 않음**

REQUIRES_NEW만 붙여도 같은 클래스 내 호출이면 프록시를 안 거쳐서 **@Transactional이 무시됨**

```java
// ❌ 안됨 - 같은 클래스 내 호출은 @Transactional 무시됨
public void methodA() {
    this.methodB();  // AOP 프록시 안 거침
}

@Transactional(propagation = REQUIRES_NEW)
public void methodB() { }
```

```java
// ✅ 됨 - 다른 빈 호출은 프록시 거침
public void methodA() {
    otherBean.methodB();  // AOP 프록시 거침
}
```

## 요약

| 조건 | 필요 여부 | 역할 |
|------|----------|------|
| REQUIRES_NEW | ✅ 필수 | 새 트랜잭션 시작, 실패해도 다른 건에 영향 없음 |
| 별도 클래스 분리 | ✅ 필수 | Spring AOP 프록시가 동작하도록 함 |
