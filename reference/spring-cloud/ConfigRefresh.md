# Spring Cloud Config Refresh 동작 방식

## 개요

`POST /actuator/refresh` 호출 시 Spring Cloud는 `RefreshEvent`를 발행하고, Environment를 다시 로드한다.
이때 설정값이 실제로 갱신되는 방식은 빈의 선언 방식에 따라 크게 두 가지로 나뉜다.

```
POST /actuator/refresh
  ↓
RefreshEvent 발행
  ↓
Environment 재로드 (Config Server, Git 등에서 최신 값 fetch)
  ↓
┌─────────────────────────────────┬──────────────────────────────────┐
│  @ConfigurationProperties       │  @RefreshScope                   │
│  → 프로퍼티 값만 rebind         │  → 빈 캐시 폐기 (destroy)       │
│  → 빈 인스턴스 유지             │  → 다음 접근 시 빈 재생성       │
│  → @PostConstruct 재실행 안 됨  │  → @PostConstruct 재실행됨      │
└─────────────────────────────────┴──────────────────────────────────┘
```

## 1. @ConfigurationProperties — Rebind 방식

refresh 이벤트 발생 시 기존 빈을 파괴하지 않고, Environment를 다시 읽어 프로퍼티 값만 **rebind**(재바인딩)한다.

```java
// [실무노트] @ConfigurationProperties 빈은 refresh 시 rebind만 수행된다.
// 빈 인스턴스 자체는 그대로 유지되므로 @PostConstruct가 다시 실행되지 않는다.
@ConfigurationProperties(prefix = "polar")
public record PolarProperties(
    String greeting,
    String feature
) {}
```

### 동작 흐름

```
1. RefreshEvent 수신
   ↓
2. ConfigurationPropertiesRebinder.rebind() 호출
   ↓
3. Environment에서 최신 프로퍼티 값 조회
   ↓
4. 기존 빈의 필드에 새 값을 덮어씀 (setter 또는 바인딩)
   ↓
5. 빈 인스턴스는 동일 → @PostConstruct 실행되지 않음
```

### 장점

- 가볍고 빠름 — 빈 재생성 없이 값만 교체
- 별도 애노테이션 추가 없이 자동 갱신 대상

### 주의점

```
⚠️ yml에서 key를 삭제해도 이전 값이 그대로 남아있음

  [변경 전] polar.greeting=Hello, polar.feature=dark-mode
  [yml에서 polar.feature 키 삭제 후 refresh]
  [변경 후] polar.greeting=Hello, polar.feature=dark-mode  ← 여전히 남아있음!

  원인: rebind는 새 값을 덮어쓸 뿐, 기존 필드를 null로 초기화하지 않는다.
        Environment에 해당 키가 없으면 바인딩 자체가 스킵된다.
```

```java
// [실무노트] @PostConstruct에서 파생 값을 계산하는 경우,
// refresh 후에도 이전 계산 결과가 유지되는 함정이 있다.
@Component
@ConfigurationProperties(prefix = "polar")
@Setter
public class PolarConfig {
    private String greeting;
    private String uppercaseGreeting; // 파생 값

    @PostConstruct
    void init() {
        // refresh 시 다시 실행되지 않음!
        this.uppercaseGreeting = greeting.toUpperCase();
    }
}
// greeting이 "Hello" → "Hola"로 변경되어도
// uppercaseGreeting은 "HELLO"로 남아있음
```

## 2. @RefreshScope — 빈 재생성 방식

refresh 이벤트 발생 시 해당 스코프의 빈 캐시를 **폐기(destroy)**한다.
다음 접근 시 빈을 처음부터 새로 생성하므로 `@PostConstruct` 포함 모든 초기화 로직이 다시 실행된다.

```java
// [실무노트] @RefreshScope 빈은 CGLIB 프록시로 감싸진다.
// refresh 시 내부 타겟 빈이 폐기되고, 다음 호출 시 새로 생성된다.
@RestController
@RefreshScope
public class MessageController {

    @Value("${polar.greeting:Hello}")
    private String greeting;

    @PostConstruct
    void init() {
        // refresh 후 다음 접근 시 다시 실행됨
        log.info("MessageController 초기화: greeting={}", greeting);
    }

    @GetMapping("/message")
    public String getMessage() {
        return greeting;
    }
}
```

### 동작 흐름

```
1. RefreshEvent 수신
   ↓
2. RefreshScope.refreshAll() 호출
   ↓
3. 스코프 내 모든 빈의 캐시 폐기 + @PreDestroy 실행
   ↓
4. (이 시점에서 빈은 존재하지 않음)
   ↓
5. 다음 요청이 해당 빈에 접근할 때 새로 생성
   ↓
6. @PostConstruct 실행, 최신 Environment에서 @Value 주입
```

### 장점

- 깨끗한 상태 보장 — yml에서 key를 삭제하면 해당 필드는 기본값(null 또는 default)으로 초기화됨
- `@PostConstruct` 파생 값 계산도 최신 값 기준으로 재실행됨

### 주의점

```
⚠️ 빈 재생성 비용

  - DB 커넥션, 외부 API 클라이언트 등 무거운 초기화 로직이 있으면 부담
  - 재생성 시점에 해당 빈을 사용하는 요청이 있으면 일시적 지연 발생 가능
  - CGLIB 프록시 오버헤드 (미세하지만 존재)
```

## 3. 공통 주의점

### @Value만 사용한 일반 빈은 refresh 대상이 아님

```java
// ❌ refresh 이벤트가 발생해도 greeting 값이 갱신되지 않음
@RestController
public class StaticController {
    @Value("${polar.greeting:Hello}")
    private String greeting;
}

// ✅ 방법 1: @RefreshScope 추가
@RestController
@RefreshScope
public class RefreshableController {
    @Value("${polar.greeting:Hello}")
    private String greeting;
}

// ✅ 방법 2: @ConfigurationProperties 사용 (권장)
@ConfigurationProperties(prefix = "polar")
public record PolarProperties(String greeting) {}
```

### yml key 삭제 미감지 문제

```
@ConfigurationProperties의 고질적 한계:
  - rebind는 "새 값 덮어쓰기"만 수행
  - Environment에 키가 없으면 바인딩 자체를 스킵
  - 결과적으로 삭제된 키의 이전 값이 잔존

해결 방법:
  1. key 삭제가 필요한 시나리오라면 @RefreshScope 사용
  2. key를 삭제하는 대신 빈 문자열("")이나 명시적 기본값으로 설정
  3. record 기반 @ConfigurationProperties는 불변이므로 rebind 시
     새 인스턴스가 생성됨 → key 삭제 문제가 완화될 수 있음
```

### 다중 인스턴스 환경에서의 전파

```
refresh는 호출된 인스턴스에만 적용된다.

  POST /actuator/refresh → instance-1 ✅ 갱신됨
                           instance-2 ❌ 이전 값 유지
                           instance-3 ❌ 이전 값 유지

다중 인스턴스 환경에서는 Spring Cloud Bus로 전파해야 한다:

  POST /actuator/busrefresh → Bus(RabbitMQ/Kafka) → 전체 인스턴스 갱신

  ┌──────────┐    ┌───────────────┐    ┌──────────────┐
  │ Webhook  │───>│ instance-1    │    │ instance-2   │
  │ or 수동  │    │ /busrefresh   │    │              │
  └──────────┘    └──────┬────────┘    └──────▲───────┘
                         │ RefreshRemoteEvent  │
                    ┌────▼─────────────────────┤
                    │   Message Broker          │
                    │   (RabbitMQ / Kafka)      │
                    └──────────────────────────┬┘
                                               │
                                        ┌──────▼───────┐
                                        │ instance-3   │
                                        └──────────────┘
```

## 비교 요약

| 항목 | @ConfigurationProperties | @RefreshScope |
|------|:------------------------:|:-------------:|
| 갱신 방식 | 프로퍼티 rebind | 빈 캐시 폐기 + 재생성 |
| 빈 인스턴스 | 유지 | 폐기 후 새로 생성 |
| @PostConstruct | 재실행 안 됨 | 재실행됨 |
| yml key 삭제 반영 | ❌ (이전 값 잔존) | ✅ (기본값으로 초기화) |
| 성능 비용 | 낮음 | 빈 재생성 비용 |
| 프록시 | 없음 | CGLIB 프록시 |
| 추가 애노테이션 | 불필요 (자동 대상) | 명시 필요 |
| 적합한 경우 | 단순 프로퍼티 값 변경 | 파생 값 계산, key 삭제 필요 시 |

## 실무 가이드

```
의사결정 흐름:

Q1. 단순 프로퍼티 값 변경만 필요한가?
  ├─ YES → @ConfigurationProperties (record 권장)
  └─ NO
      ↓
Q2. @PostConstruct에서 파생 값을 계산하는가?
  ├─ YES → @RefreshScope 사용
  └─ NO
      ↓
Q3. yml key 삭제를 런타임에 반영해야 하는가?
  ├─ YES → @RefreshScope 사용
  └─ NO  → @ConfigurationProperties로 충분
```

> refresh의 핵심은 "어떤 빈이 어떤 방식으로 갱신되는지"를 정확히 이해하는 것이다.
> 대부분의 경우 `@ConfigurationProperties`의 rebind로 충분하지만,
> 파생 값 계산이나 key 삭제 반영이 필요하다면 `@RefreshScope`를 선택해야 한다.
> 다중 인스턴스 환경에서는 반드시 Spring Cloud Bus를 통한 전파를 고려하라.
