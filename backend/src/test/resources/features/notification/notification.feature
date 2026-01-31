Feature: 알림 API
  사용자 알림 조회 및 읽음 처리 API

  Scenario: 알림 목록이 비어있을 때 빈 목록을 반환한다
    Given 서버가 실행중이다
    And 인증된 사용자이다
    When 알림 목록을 조회한다
    Then 응답 상태 코드는 200이다
    And 응답 본문의 목록 크기는 0이다

  Scenario: 읽지 않은 알림이 없으면 카운트는 0이다
    Given 서버가 실행중이다
    And 인증된 사용자이다
    When 읽지 않은 알림 개수를 조회한다
    Then 응답 상태 코드는 200이다
    And 응답 본문의 "unreadCount" 값은 0이다

  Scenario: 존재하지 않는 알림 읽음 처리 시 200을 반환한다
    Given 서버가 실행중이다
    And 인증된 사용자이다
    When "non-existent-id" 알림을 읽음 처리한다
    Then 응답 상태 코드는 200이다
