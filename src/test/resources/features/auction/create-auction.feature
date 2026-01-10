# language: ko
기능: 경매 등록
  판매자는 새로운 경매 상품을 등록할 수 있다.
  시작가는 즉시구매가보다 낮아야 하며, 입력된 가격에 따라 입찰 단위가 자동 계산된다.

  시나리오: 정상적인 경매 등록
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 맥북 프로 16인치              |
      | description     | 상태 매우 좋음, 박스 포함       |
      | category        | ELECTRONICS                |
      | startPrice      | 1000000                    |
      | instantBuyPrice | 1500000                    |
      | duration        | HOURS_24                   |
    만약 경매 등록을 요청한다
    그러면 응답 상태 코드는 201이다
    그리고 응답 본문의 "title" 값은 "맥북 프로 16인치"이다
    그리고 응답 본문의 "startPrice" 값은 1000000이다
    그리고 응답 본문의 "status" 값은 "BIDDING"이다
    그리고 응답 본문의 "bidIncrement" 값은 30000이다

  시나리오: 즉시구매가가 시작가보다 낮은 경우 실패
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 아이폰 15                    |
      | description     | 미개봉 새상품                 |
      | category        | ELECTRONICS                |
      | startPrice      | 1000000                    |
      | instantBuyPrice | 900000                     |
      | duration        | HOURS_24                   |
    만약 경매 등록을 요청한다
    그러면 응답 상태 코드는 400이다
    그리고 응답 본문의 "errorCode" 값은 "INSTANT_BUY_PRICE_TOO_LOW"이다
