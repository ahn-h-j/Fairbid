package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.auction.adapter.in.dto.CreateAuctionRequest;
import com.cos.fairbid.auction.domain.AuctionDuration;
import com.cos.fairbid.auction.domain.Category;
import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import io.cucumber.java.ko.그리고;
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
        // Given: 즉시구매가가 포함된 경매 정보 설정
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

    @조건("판매자가 즉시구매가 없이 경매 정보를 입력한다")
    public void 판매자가_즉시구매가_없이_경매_정보를_입력한다(Map<String, String> auctionInfo) {
        // Given: 즉시구매가가 없는 경매 정보 설정
        CreateAuctionRequest request = CreateAuctionRequest.builder()
                .title(auctionInfo.get("title"))
                .description(auctionInfo.get("description"))
                .category(Category.valueOf(auctionInfo.get("category")))
                .startPrice(Long.parseLong(auctionInfo.get("startPrice")))
                .instantBuyPrice(null)
                .duration(AuctionDuration.valueOf(auctionInfo.get("duration")))
                .build();

        testContext.setLastRequestBody(request);
    }

    @만약("경매 등록을 요청한다")
    public void 경매_등록을_요청한다() {
        // When: 경매 등록 API 호출
        CreateAuctionRequest request = testContext.getLastRequestBody();
        ResponseEntity<Map> response = testAdapter.post("/api/v1/auctions", request, Map.class);
        testContext.setLastResponse(response);

        // 생성된 경매 ID 저장 (성공 시)
        extractAndSaveAuctionId(response);
    }

    @만약("등록된 경매의 상세 정보를 조회한다")
    public void 등록된_경매의_상세_정보를_조회한다() {
        // When: 방금 생성한 경매의 상세 정보 조회
        Long auctionId = testContext.getLastCreatedAuctionId();
        ResponseEntity<Map> response = testAdapter.get("/api/v1/auctions/" + auctionId, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("존재하지 않는 경매 ID로 상세 정보를 조회한다")
    public void 존재하지_않는_경매_ID로_상세_정보를_조회한다() {
        // When: 존재하지 않는 경매 ID로 조회 (999999)
        Long nonExistentId = 999999L;
        ResponseEntity<Map> response = testAdapter.get("/api/v1/auctions/" + nonExistentId, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("경매 목록을 조회한다")
    public void 경매_목록을_조회한다() {
        // When: 전체 경매 목록 조회
        ResponseEntity<Map> response = testAdapter.get("/api/v1/auctions", Map.class);
        testContext.setLastResponse(response);
    }

    @만약("상태가 {string}인 경매 목록을 조회한다")
    public void 상태가_인_경매_목록을_조회한다(String status) {
        // When: 상태 필터링으로 경매 목록 조회
        ResponseEntity<Map> response = testAdapter.get("/api/v1/auctions?status=" + status, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("키워드로 경매 목록을 검색한다: {string}")
    public void 키워드로_경매_목록을_검색한다(String keyword) {
        // When: 키워드 검색으로 경매 목록 조회
        ResponseEntity<Map> response = testAdapter.get("/api/v1/auctions?keyword=" + keyword, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("잘못된 상태값으로 경매 목록을 조회한다: {string}")
    public void 잘못된_상태값으로_경매_목록을_조회한다(String invalidStatus) {
        // When: 잘못된 enum 값으로 경매 목록 조회 (400 에러 예상)
        ResponseEntity<Map> response = testAdapter.get("/api/v1/auctions?status=" + invalidStatus, Map.class);
        testContext.setLastResponse(response);
    }

    /**
     * 응답에서 경매 ID를 추출하여 TestContext에 저장한다.
     */
    @SuppressWarnings("unchecked")
    private void extractAndSaveAuctionId(ResponseEntity<Map> response) {
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Map<String, Object> body = response.getBody();
            if (body.containsKey("data") && body.get("data") instanceof Map) {
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data.containsKey("id")) {
                    Object idValue = data.get("id");
                    if (idValue instanceof Number) {
                        testContext.setLastCreatedAuctionId(((Number) idValue).longValue());
                    }
                }
            }
        }
    }
}
