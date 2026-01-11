# PR 작성 지침 (AI Reference Only)

본 문서는 AI가 Pull Request 본문을 생성할 때 참조해야 하는 표준 구조 및 작성 규칙입니다.

## 1. 출력 구조 (Output Structure)

### 개요 (Overview)
- 해당 PR이 해결하려는 핵심 목적을 1~2문장 내외로 기술한다.

### 변경 사항 (Technical Changes)
- 코드의 변경점 중 기술적으로 유의미한 내용을 상세히 기술한다.
- 수정된 함수, 클래스, 로직의 변화를 포함한다.
- 단순히 "파일 수정"이 아닌 "어떤 로직이 어떻게 변경되었는지" 기술한다.

### 영향 범위 (Impact)
- 해당 변경으로 인해 영향을 받는 모듈, API, 또는 UI 요소를 나열한다.
- 잠재적 부작용(Side Effects)이 예상되는 경우 이를 명시한다.

### 테스트 관점 (Testing Points)
- 코드 리뷰어가 중점적으로 검증해야 하는 로직을 제안한다.
- 테스트 시나리오나 예상되는 결과값을 기술한다.

---

## 2. 작성 원칙 (Rules)

1. **언어 및 톤앤매너**:
    - 전문적인 기술 용어를 사용하며, 정중하고 간결한 문체를 유지한다.
    - 모든 문장은 개조식(~함, ~임, ~함)으로 작성한다.
    - 이모지는 절대 사용하지 않는다.

2. **내용의 구체성**:
    - 코드의 `diff`를 분석하여 단순 반복 작업이 아닌 비즈니스 로직의 변화를 포착해 반영한다.
    - 관련된 이슈 번호가 제공된 경우 이를 연동한다.

3. **가독성**:
    - 정보의 위계에 따라 Markdown 헤더(##, ###)를 적절히 활용한다.
    - 복잡한 변경 사항은 불렛 포인트(*)를 활용하여 구조화한다.


name: Java CI with Testcontainers (W2 Task 5)

on:
push:
branches: [ "main", "master" ]
pull_request:
branches: [ "main", "master" ]

permissions:
contents: read
checks: write # 리포트를 쓰기 위해 필요한 권한

jobs:
build-and-test:
runs-on: ubuntu-latest

    steps:
      # 1. 소스 코드 체크아웃
      - name: Checkout repository
        uses: actions/checkout@v4

      # 2. Java 17 설치 (사용 중인 버전에 맞춰 수정 가능)
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      # 3. Gradle 래퍼 실행 권한 부여
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      # 4. 테스트 실행 (Cucumber + Testcontainers)
      # Testcontainers는 Docker가 필요하며, GitHub Runner에는 기본 설치되어 있습니다.
      - name: Run Tests with Gradle
        run: ./gradlew test

      # 5. 테스트 리포트 게시 (성공/실패 상관없이 실행)
      - name: Publish Test Report
        uses: EnricoMi/publish-unit-test-result-action@v2
        if: always() # 테스트가 실패해도 리포트는 생성되어야 하므로 반드시 추가
        with:
          files: "**/build/test-results/test/TEST-*.xml"

---

## 3. AI 협업 섹션 (AI Collaboration)

AI와 페어 프로그래밍으로 구현한 경우 아래 섹션을 추가한다.

### 협업 방식 (Collaboration Method)
- AI와 어떻게 협업했는지 작업 방식을 기술한다.
- 참고한 문서, 컨벤션 분석 여부, 커밋 전략 등을 포함한다.

### 주요 대화 (Key Conversations)
- AI에게 요청한 주요 질문이나 검토 사항을 인용 형식으로 기술한다.
- 카테고리(구현 계획, 확장성 검토, 테스트 방법 등)별로 구분하여 작성한다.

### AI 기여 사항 (AI Contributions)
- AI가 실제로 기여한 기술적 사항을 나열한다.