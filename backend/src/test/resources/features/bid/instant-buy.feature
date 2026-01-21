# language: ko
기능: 즉시 구매
  구매자는 즉시구매가가 설정된 경매에서 즉시 구매를 요청할 수 있다.
  즉시 구매 시 1시간 최종 입찰 기회가 제공되며, 현재가가 90% 이상이면 비활성화된다.

  배경:
    조건 서버가 실행중이다

  시나리오: 즉시 구매 성공
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 맥북 프로 16인치              |
      | description     | 상태 매우 좋음                 |
      | category        | ELECTRONICS                |
      | startPrice      | 1000000                    |
      | instantBuyPrice | 2000000                    |
      | duration        | HOURS_24                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    만약 구매자가 즉시 구매를 요청한다
    그러면 응답 상태 코드는 201이다
    그리고 응답 본문의 "bidType" 값은 "INSTANT_BUY"이다
    그리고 응답 본문의 "amount" 값은 2000000이다

  시나리오: 즉시구매가가 없는 경매에서 즉시 구매 시 400 반환
    조건 판매자가 즉시구매가 없이 경매 정보를 입력한다
      | title           | 아이폰 15 프로               |
      | description     | 미개봉 새상품                 |
      | category        | ELECTRONICS                |
      | startPrice      | 500000                     |
      | duration        | HOURS_24                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    만약 구매자가 즉시 구매를 요청한다
    그러면 응답 상태 코드는 400이다
    그리고 응답 본문의 "errorCode" 값은 "INSTANT_BUY_NOT_AVAILABLE"이다

  시나리오: 현재가가 90% 이상일 때 즉시 구매 시 400 반환
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 갤럭시 S24 울트라              |
      | description     | 개봉만 한 새상품                |
      | category        | ELECTRONICS                |
      | startPrice      | 900000                     |
      | instantBuyPrice | 1000000                    |
      | duration        | HOURS_24                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    만약 구매자가 즉시 구매를 요청한다
    그러면 응답 상태 코드는 400이다
    그리고 응답 본문의 "errorCode" 값은 "INSTANT_BUY_DISABLED"이다

  시나리오: 이미 즉시 구매가 활성화된 경매에서 중복 즉시 구매 시 400 반환
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 다이슨 에어랩                  |
      | description     | 미개봉 풀세트                  |
      | category        | FASHION                    |
      | startPrice      | 300000                     |
      | instantBuyPrice | 600000                     |
      | duration        | HOURS_24                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    그리고 구매자가 즉시 구매를 요청한다
    그리고 응답 상태 코드는 201이다
    만약 다른 구매자가 즉시 구매를 요청한다
    그러면 응답 상태 코드는 400이다
    그리고 응답 본문의 "errorCode" 값은 "INSTANT_BUY_ALREADY_ACTIVATED"이다

  시나리오: 즉시 구매 활성화 후 일반 입찰 성공
    조건 판매자가 아래와 같은 경매 정보를 입력한다
      | title           | 닌텐도 스위치 OLED              |
      | description     | 미개봉 새상품                  |
      | category        | ELECTRONICS                |
      | startPrice      | 200000                     |
      | instantBuyPrice | 400000                     |
      | duration        | HOURS_24                   |
    그리고 경매 등록을 요청한다
    그리고 응답 상태 코드는 201이다
    그리고 구매자가 즉시 구매를 요청한다
    그리고 응답 상태 코드는 201이다
    만약 다른 구매자가 원터치 입찰을 요청한다
    그러면 응답 상태 코드는 201이다
    그리고 응답 본문의 "bidType" 값은 "ONE_TOUCH"이다
