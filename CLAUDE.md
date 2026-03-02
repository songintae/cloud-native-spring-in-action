# 프로젝트 규칙

## 언어 규칙
- 문서, 주석, 커밋 메시지: 한국어
- 코드(클래스명, 변수명, 메서드명): 영어

## 전문가 관점
모든 코드 작성과 리뷰에 두 가지 전문가 관점을 동시에 적용한다:

### Spring 전문가
- 프레임워크 내부 동작 원리와 설계 의도 설명
- 실무에서 자주 쓰이는 패턴과 안티패턴 식별
- 스테레오타입 애노테이션, DI, AOP, 예외 처리 전략

### Cloud Native 전문가
- 12-Factor App 원칙 준수
- 컨테이너화, 오케스트레이션(Kubernetes) 최적화
- 관측성(Observability): 로깅, 메트릭, 트레이싱
- 복원력(Resilience): 서킷 브레이커, 재시도, 타임아웃

## 코드 컨벤션
- Java 17, Spring Boot 4.0.2, Gradle
- record 기반 도메인 모델 (불변 객체)
- Lombok 사용 (빌더, 로깅 등 보일러플레이트 제거)
- 레이어 구조: `web` → `domain` → `persistence`
- 패키지 구조: `com.polarbookshop.{서비스명}.{레이어}`

## 주석 패턴
- `[실무노트]`: Java/Kotlin 코드 내 실무 관점 주석
- `[실무]`: application.yml 등 설정 파일 내 주석

## 테스트 컨벤션
- `@WebMvcTest`: 웹 레이어 슬라이스 테스트
- `@SpringBootTest`: 통합 테스트
- `@MockitoBean`: 의존성 모킹
- 테스트 메서드명: `한글_설명_스타일()` 또는 `shouldDoSomething_whenCondition()`

## reference/ 디렉토리 규칙
- 카테고리별 하위 디렉토리로 분류:
  - `spring-mvc/`, `spring-data/`, `spring-cloud/`
  - `resilience/`, `observability/`, `security/`
  - `container/`, `kubernetes/`
- 파일명: PascalCase (예: `DispatcherServlet.md`, `CircuitBreaker.md`)

## 서비스 구조
- 각 서비스는 독립 Gradle 프로젝트 (모노레포 내)
- 현재: `catalog-service`
- 향후: `order-service`, `edge-service`, `config-service` 등
- 공통 구조: `src/`, `build.gradle`, `Dockerfile`, `deploy/` (K8s 매니페스트)
