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

## 서비스 구조
- 각 서비스는 독립 Gradle 프로젝트 (모노레포 내)
- 현재: `catalog-service`
- 향후: `order-service`, `edge-service`, `config-service` 등
- 공통 구조: `src/`, `build.gradle`, `Dockerfile`, `deploy/` (K8s 매니페스트)

## reference/ 디렉토리 규칙
- 카테고리별 하위 디렉토리로 분류:
  - `spring-mvc/`, `spring-data/`, `spring-cloud/`
  - `resilience/`, `observability/`, `security/`
  - `container/`, `kubernetes/`
- 파일명: PascalCase (예: `DispatcherServlet.md`, `CircuitBreaker.md`)

> 파일 타입별 세부 규칙은 `.claude/rules/`에서 조건부로 로드된다.