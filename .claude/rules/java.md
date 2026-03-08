---
paths:
  - "**/*.java"
---

# Java 코드 규칙

## 코드 컨벤션
- Java 17, Spring Boot 4.0.2, Gradle
- record 기반 도메인 모델 (불변 객체)
- Lombok 사용 (빌더, 로깅 등 보일러플레이트 제거)
- 레이어 구조: `web` → `domain` → `persistence`
- 패키지 구조: `com.polarbookshop.{서비스명}.{레이어}`

## 주석 패턴
- `[실무노트]`: 실무 관점의 인사이트를 주석으로 남긴다

## 테스트 컨벤션
- `@WebMvcTest`: 웹 레이어 슬라이스 테스트
- `@SpringBootTest`: 통합 테스트
- `@MockitoBean`: 의존성 모킹
- 테스트 메서드명: `한글_설명_스타일()` 또는 `shouldDoSomething_whenCondition()`
