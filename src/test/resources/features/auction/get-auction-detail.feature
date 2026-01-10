# language: ko
기능: 경매 상세 조회
  구매자는 경매의 상세 정보를 조회할 수 있다.
  상세 정보에는 기본 정보와 함께 즉시 구매 가능 여부, 다음 입찰 가능 금액, 수정 가능 여부가 포함된다.

  배경:
    조건 서버가 실행중이다

  시나리오: 정상적인 경매 상세 조회
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 맥북 프로 16인치              |
      | description     | 상태 매우 좋음, 박스 포함       |
      | category        | ELECTRONICS                |
      | startPrice      | 1000000                    |
      | instantBuyPrice | 1500000                    |
      | duration        | HOURS_24                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    만약 등록된 경매의 상세 정보를 조회한다
    그러면 응답 상태 코드는 200이다
    그리고 응답 본문의 "title" 값은 "맥북 프로 16인치"이다
    그리고 응답 본문의 "startPrice" 값은 1000000이다
    그리고 응답 본문의 "currentPrice" 값은 1000000이다
    그리고 응답 본문의 "instantBuyPrice" 값은 1500000이다
    그리고 응답 본문의 "bidIncrement" 값은 30000이다
    그리고 응답 본문의 "instantBuyEnabled" 값은 true이다
    그리고 응답 본문의 "nextMinBidPrice" 값은 1030000이다
    그리고 응답 본문의 "editable" 값은 true이다

  시나리오: 즉시구매가 없는 경매 상세 조회
    조건 판매자가 즉시구매가 없이 경매 정보를 입력한다
      | title           | 아이폰 15                    |
      | description     | 미개봉 새상품                 |
      | category        | ELECTRONICS                |
      | startPrice      | 500000                     |
      | duration        | HOURS_48                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    만약 등록된 경매의 상세 정보를 조회한다
    그러면 응답 상태 코드는 200이다
    그리고 응답 본문의 "instantBuyEnabled" 값은 false이다
    그리고 응답 본문의 "editable" 값은 true이다

  시나리오: 존재하지 않는 경매 조회 시 404 반환
    만약 존재하지 않는 경매 ID로 상세 정보를 조회한다
    그러면 응답 상태 코드는 404이다
    그리고 응답 본문의 "errorCode" 값은 "AUCTION_NOT_FOUND"이다
