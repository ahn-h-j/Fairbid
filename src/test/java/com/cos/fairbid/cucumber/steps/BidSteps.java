package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.bid.adapter.in.dto.PlaceBidRequest;
import com.cos.fairbid.bid.domain.BidType;
import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import io.cucumber.java.ko.만약;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 입찰 기능 Step Definitions
 */
public class BidSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;

    public BidSteps(TestAdapter testAdapter, TestContext testContext) {
        this.testAdapter = testAdapter;
        this.testContext = testContext;
    }

    @만약("구매자가 원터치 입찰을 요청한다")
    public void 구매자가_원터치_입찰을_요청한다() {
        // When: 원터치 입찰 요청 (금액은 자동 계산)
        Long auctionId = testContext.getLastCreatedAuctionId();

        PlaceBidRequest request = PlaceBidRequest.builder()
                .bidType(BidType.ONE_TOUCH)
                .build();

        String url = "/api/v1/auctions/" + auctionId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("구매자가 {long}원으로 직접 입찰을 요청한다")
    public void 구매자가_금액으로_직접_입찰을_요청한다(Long amount) {
        // When: 금액 직접 지정 입찰 요청
        Long auctionId = testContext.getLastCreatedAuctionId();

        PlaceBidRequest request = PlaceBidRequest.builder()
                .amount(amount)
                .bidType(BidType.DIRECT)
                .build();

        String url = "/api/v1/auctions/" + auctionId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("존재하지 않는 경매에 입찰을 요청한다")
    public void 존재하지_않는_경매에_입찰을_요청한다() {
        // When: 존재하지 않는 경매 ID로 입찰 요청
        Long nonExistentId = 999999L;

        PlaceBidRequest request = PlaceBidRequest.builder()
                .bidType(BidType.ONE_TOUCH)
                .build();

        String url = "/api/v1/auctions/" + nonExistentId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }
}
