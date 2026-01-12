# language: ko
기능: 입찰
  구매자는 진행 중인 경매에 입찰할 수 있다.
  입찰 방식은 원터치 입찰과 금액 직접 지정 방식이 있다.

  배경:
    조건 서버가 실행중이다

  시나리오: 원터치 입찰 성공
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 맥북 프로 16인치              |
      | description     | 상태 매우 좋음, 박스 포함       |
      | category        | ELECTRONICS                |
      | startPrice      | 1000000                    |
      | instantBuyPrice | 1500000                    |
      | duration        | HOURS_24                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    만약 구매자가 원터치 입찰을 요청한다
    그러면 응답 상태 코드는 201이다
    그리고 응답 본문의 "bidType" 값은 "ONE_TOUCH"이다
    그리고 입찰 금액이 시작가 + 입찰단위와 같다

  시나리오: 금액 직접 지정 입찰 성공
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 아이폰 15 프로               |
      | description     | 미개봉 새상품                 |
      | category        | ELECTRONICS                |
      | startPrice      | 500000                     |
      | instantBuyPrice | 800000                     |
      | duration        | HOURS_48                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    만약 구매자가 550000원으로 직접 입찰을 요청한다
    그러면 응답 상태 코드는 201이다
    그리고 응답 본문의 "bidType" 값은 "DIRECT"이다
    그리고 응답 본문의 "amount" 값은 550000이다

  시나리오: 최소 입찰 금액 미만으로 입찰 시 400 반환
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 에어팟 프로 2               |
      | description     | 개봉 후 미사용               |
      | category        | ELECTRONICS                |
      | startPrice      | 200000                    |
      | instantBuyPrice | 300000                    |
      | duration        | HOURS_24                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    만약 구매자가 200001원으로 직접 입찰을 요청한다
    그러면 응답 상태 코드는 400이다
    그리고 응답 본문의 "errorCode" 값은 "BID_TOO_LOW"이다

  시나리오: 존재하지 않는 경매에 입찰 시 404 반환
    만약 존재하지 않는 경매에 입찰을 요청한다
    그러면 응답 상태 코드는 404이다
    그리고 응답 본문의 "errorCode" 값은 "AUCTION_NOT_FOUND"이다
