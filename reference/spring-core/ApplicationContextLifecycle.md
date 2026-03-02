# Spring ApplicationContext 라이프사이클 및 이벤트

## 개요

`SpringApplication.run()`을 호출하면 Spring Boot는 이벤트 기반 라이프사이클을 통해 애플리케이션을 부트스트랩한다.
각 단계마다 `ApplicationEvent`가 발행되며, 개발자는 이 이벤트를 구독하여 초기화 로직을 적절한 시점에 실행할 수 있다.

이 문서는 `BookDataLoader`에서 사용하는 `@EventListener(ApplicationReadyEvent.class)`를 출발점으로,
전체 라이프사이클 흐름과 각 이벤트의 의미를 정리한다.

## 전체 라이프사이클 흐름도

```
SpringApplication.run(App.class, args)
  │
  ├─ (1) SpringApplication 인스턴스 생성
  │       - WebApplicationType 추론 (SERVLET / REACTIVE / NONE)
  │       - ApplicationContextInitializer 로드
  │       - ApplicationListener 로드 (spring.factories / META-INF)
  │
  ├─ (2) ★ ApplicationStartingEvent
  │       - 로깅 시스템 초기화 전, 가장 이른 시점
  │
  ├─ (3) Environment 준비
  │       ★ ApplicationEnvironmentPreparedEvent
  │       - application.yml, 환경변수, CLI 인자 바인딩 완료
  │       - 아직 ApplicationContext 생성 전
  │
  ├─ (4) ApplicationContext 생성
  │       ★ ApplicationContextInitializedEvent
  │       - Context 객체 생성 + Initializer 실행 완료
  │       - BeanDefinition 로드 전
  │
  ├─ (5) BeanDefinition 로드
  │       ★ ApplicationPreparedEvent
  │       - @Configuration 클래스 스캔, BeanDefinition 등록 완료
  │       - 아직 빈 인스턴스화 전
  │
  ├─ (6) Context Refresh (핵심 단계)
  │       - BeanDefinition → 빈 인스턴스화 + 의존성 주입
  │       - @PostConstruct 실행
  │       - 내장 서버(Tomcat/Netty) 시작
  │       ★ ContextRefreshedEvent
  │
  ├─ (7) ★ ApplicationStartedEvent
  │       - Context Refresh 완료, 애플리케이션 "살아있음"
  │       - 아직 Runner 실행 전
  │
  ├─ (8) Runner 실행 (CommandLineRunner, ApplicationRunner)
  │       ★ ApplicationReadyEvent          ← BookDataLoader가 구독하는 이벤트
  │       - 모든 초기화 완료, 트래픽 수신 준비 완료
  │
  └─ (9) 종료 / 실패
          - 정상 종료: ★ ContextClosedEvent
          - 기동 실패: ★ ApplicationFailedEvent
```

## 이벤트 상세

### 시간순 이벤트 테이블

| 순서 | 이벤트 | 발생 시점 | Context 상태 | 대표 활용 |
|:----:|--------|----------|:------------:|----------|
| 1 | `ApplicationStartingEvent` | run() 직후, 리스너 등록 후 | 미생성 | 로깅 시스템 커스터마이징 |
| 2 | `ApplicationEnvironmentPreparedEvent` | Environment 바인딩 완료 | 미생성 | 프로파일 동적 추가, 프로퍼티 소스 조작 |
| 3 | `ApplicationContextInitializedEvent` | Context 생성 + Initializer 실행 | 생성됨, 빈 미등록 | Context 커스터마이징 |
| 4 | `ApplicationPreparedEvent` | BeanDefinition 등록 완료 | 빈 정의 등록됨, 미인스턴스화 | BeanDefinition 조작 |
| 5 | `ContextRefreshedEvent` | refresh() 완료 | 빈 인스턴스화 + DI 완료 | 캐시 워밍업 (주의 필요) |
| 6 | `ApplicationStartedEvent` | Context Refresh 직후 | 완전 활성 | 기동 완료 확인 로직 |
| 7 | `ApplicationReadyEvent` | Runner 실행 완료 후 | 완전 활성 + Runner 완료 | 테스트 데이터 로드, 외부 시스템 연동 |
| 8 | `ContextClosedEvent` | 정상 종료 시 | 종료 중 | 리소스 정리, Graceful Shutdown |
| - | `ApplicationFailedEvent` | 기동 실패 시 | 실패 | 알림 발송, 로그 기록 |

### 이벤트별 코드 예시

```java
// [실무노트] ApplicationStartingEvent — Context 생성 전이므로
// @Component로 등록할 수 없다. spring.factories 또는 SpringApplication.addListeners()로 등록해야 한다.
public class StartingListener implements ApplicationListener<ApplicationStartingEvent> {
    @Override
    public void onApplicationEvent(ApplicationStartingEvent event) {
        System.out.println("애플리케이션 시작 중...");
    }
}
```

```java
// [실무노트] ApplicationEnvironmentPreparedEvent — 프로파일을 동적으로 추가하거나
// 외부 프로퍼티 소스를 주입할 때 사용한다. Context 생성 전이므로 역시 spring.factories로 등록.
public class EnvPreparedListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        // 예: 특정 조건에 따라 프로파일 추가
        if (env.getProperty("KUBERNETES_SERVICE_HOST") != null) {
            env.addActiveProfile("kubernetes");
        }
    }
}
```

```java
// [실무노트] ContextRefreshedEvent — 빈 인스턴스화와 DI가 완료된 시점이지만,
// Runner 실행 전이므로 데이터 로드 등 무거운 작업은 ApplicationReadyEvent에서 하는 것이 안전하다.
@Component
public class CacheWarmer {
    @EventListener(ContextRefreshedEvent.class)
    public void warmCache() {
        // 캐시 워밍업 — 단, 이 시점에서 DB 연결이 불안정할 수 있음에 주의
    }
}
```

```java
// [실무노트] ApplicationReadyEvent — 모든 초기화가 완료된 후 실행된다.
// Runner까지 완료된 시점이므로 데이터 로드, 외부 시스템 연동에 가장 안전한 이벤트.
// 프로젝트 내 BookDataLoader가 이 패턴을 사용한다.
@Component
@Profile("testdata")
@RequiredArgsConstructor
public class BookDataLoader {

    private final BookRepository bookRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void loadBookTestData() {
        Book book1 = new Book("1234567891", "Northern Lights", "Lyra Silverstar", 9.90);
        Book book2 = new Book("1234567892", "Polar Journey", "lorek Polarson", 12.90);
        bookRepository.save(book1);
        bookRepository.save(book2);
    }
}
```

```java
// [실무노트] ApplicationFailedEvent — 기동 실패 시 알림을 보내거나 진단 로그를 남길 때 사용.
// Context가 불완전한 상태일 수 있으므로 빈 의존성 없이 동작하도록 구현해야 한다.
public class FailureListener implements ApplicationListener<ApplicationFailedEvent> {
    @Override
    public void onApplicationEvent(ApplicationFailedEvent event) {
        Throwable exception = event.getException();
        // 슬랙 웹훅, 이메일 등으로 알림 발송
        System.err.println("기동 실패: " + exception.getMessage());
    }
}
```

## 실무 가이드: 이벤트별 적합한 작업

### 매핑 테이블

| 하고 싶은 작업 | 권장 이벤트 | 이유 |
|---------------|:-----------:|------|
| 로깅 시스템 커스터마이징 | `ApplicationStartingEvent` | 로깅 초기화 전 유일한 개입 시점 |
| 프로파일 동적 추가 | `ApplicationEnvironmentPreparedEvent` | Environment 확정 전 마지막 기회 |
| BeanDefinition 동적 조작 | `ApplicationPreparedEvent` | 빈 인스턴스화 전 정의 수정 가능 |
| 캐시 워밍업 | `ApplicationReadyEvent` | 모든 빈과 인프라가 준비된 상태 |
| 테스트 데이터 로드 | `ApplicationReadyEvent` | DB 연결 포함 모든 초기화 완료 |
| 외부 시스템 연동 확인 | `ApplicationReadyEvent` | 네트워크 I/O가 안전한 시점 |
| Graceful Shutdown 정리 | `ContextClosedEvent` | 종료 직전 리소스 해제 |
| 기동 실패 알림 | `ApplicationFailedEvent` | 실패 원인 접근 가능 |

### 흔한 실수

```
❌ ContextRefreshedEvent에서 데이터 로드

  @EventListener(ContextRefreshedEvent.class)
  public void loadData() {
      bookRepository.save(...);  // 위험!
  }

  문제점:
  1. Runner 실행 전이므로 CommandLineRunner/ApplicationRunner의 초기화에 의존하는
     로직이 아직 완료되지 않았을 수 있다.
  2. ContextRefreshedEvent는 Context가 refresh될 때마다 발생한다.
     @RefreshScope 빈이 갱신되면 다시 발행될 수 있어 중복 실행 위험이 있다.
  3. Spring Cloud 환경에서 부모/자식 Context가 있으면 여러 번 발생할 수 있다.

✅ ApplicationReadyEvent에서 데이터 로드 (권장)

  @EventListener(ApplicationReadyEvent.class)
  public void loadData() {
      bookRepository.save(...);  // 안전
  }

  장점:
  1. 모든 Runner 실행 완료 후 딱 한 번 발생한다.
  2. 트래픽 수신 전에 데이터 준비가 보장된다.
  3. 의미적으로 "애플리케이션이 준비되었다"는 시점과 정확히 일치한다.
```

```
❌ ApplicationStartedEvent에서 외부 API 호출

  @EventListener(ApplicationStartedEvent.class)
  public void callExternalApi() {
      restClient.get(...);  // Runner 실행 전이라 불완전할 수 있음
  }

  문제점:
  - Runner에서 RestClient 설정을 추가로 구성하는 경우,
    StartedEvent 시점에는 아직 적용되지 않았을 수 있다.

✅ ApplicationReadyEvent 사용

  @EventListener(ApplicationReadyEvent.class)
  public void callExternalApi() {
      restClient.get(...);  // 모든 초기화 완료 후 실행
  }
```

## 정리

| 단계 | 이벤트 | 핵심 키워드 |
|:----:|--------|:-----------:|
| 기동 시작 | `ApplicationStartingEvent` | 가장 이른 시점 |
| 환경 준비 | `ApplicationEnvironmentPreparedEvent` | 프로퍼티 확정 |
| Context 초기화 | `ApplicationContextInitializedEvent` | Context 생성 |
| 빈 정의 등록 | `ApplicationPreparedEvent` | BeanDefinition |
| Context Refresh | `ContextRefreshedEvent` | 빈 인스턴스화 + DI |
| 기동 완료 | `ApplicationStartedEvent` | Context 활성 |
| 준비 완료 | `ApplicationReadyEvent` | 트래픽 수신 가능 |
| 종료 | `ContextClosedEvent` | Graceful Shutdown |
| 실패 | `ApplicationFailedEvent` | 기동 실패 진단 |

> 대부분의 초기화 로직은 `ApplicationReadyEvent`에서 실행하는 것이 가장 안전하다.
> 이 시점은 모든 빈, Runner, 내장 서버가 준비된 후이며,
> 트래픽 수신이 시작되기 직전이다.
