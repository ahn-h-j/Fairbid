Feature: Health Check
  서버의 상태를 확인하는 API

  Scenario: 서버가 정상적으로 동작하는지 확인한다
    Given 서버가 실행중이다
    When "/health" 엔드포인트를 호출한다
    Then 응답 상태 코드는 200이다
    And 응답 본문의 "status" 값은 "UP"이다
