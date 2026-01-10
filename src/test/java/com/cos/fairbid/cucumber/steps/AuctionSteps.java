package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.auction.adapter.in.dto.CreateAuctionRequest;
import com.cos.fairbid.auction.domain.AuctionDuration;
import com.cos.fairbid.auction.domain.Category;
import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import io.cucumber.java.ko.만약;
import io.cucumber.java.ko.조건;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public class AuctionSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;

    public AuctionSteps(TestAdapter testAdapter, TestContext testContext) {
        this.testAdapter = testAdapter;
        this.testContext = testContext;
    }

    @조건("판매자가 아래와 같은 경매 정보를 입력한다")
    public void 판매자가_아래와_같은_경매_정보를_입력한다(Map<String, String> auctionInfo) {
        CreateAuctionRequest request = CreateAuctionRequest.builder()
                .title(auctionInfo.get("title"))
                .description(auctionInfo.get("description"))
                .category(Category.valueOf(auctionInfo.get("category")))
                .startPrice(Long.parseLong(auctionInfo.get("startPrice")))
                .instantBuyPrice(Long.parseLong(auctionInfo.get("instantBuyPrice")))
                .duration(AuctionDuration.valueOf(auctionInfo.get("duration")))
                .build();
        
        testContext.setLastRequestBody(request);
    }

    @만약("경매 등록을 요청한다")
    public void 경매_등록을_요청한다() {
        CreateAuctionRequest request = testContext.getLastRequestBody();
        ResponseEntity<Map> response = testAdapter.post("/api/v1/auctions", request, Map.class);
        testContext.setLastResponse(response);
    }
}
