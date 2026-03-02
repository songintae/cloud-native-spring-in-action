# @ConfigurationProperties 바인딩 방식: record vs class

## 개요

`@ConfigurationProperties`는 `application.yml`의 프로퍼티 그룹을 타입 안전한 자바 객체에 바인딩하는 메커니즘이다.
Spring Boot는 두 가지 바인딩 방식을 지원한다:

1. 생성자 바인딩 (Constructor Binding) — record 또는 `@ConstructorBinding` 클래스
2. 자바빈 바인딩 (JavaBean Binding) — setter 기반 mutable 클래스

두 방식은 바인딩 시점의 동작, 불변성, Config Refresh 시 갱신 방식이 다르다.

## 핵심 동작 원리

### 바인딩 흐름

```
SpringApplication.run()
  ↓
ConfigurationPropertiesBindingPostProcessor
  ↓
Binder.bind()
  ↓
┌─────────────────────────────────────┬─────────────────────────────────────┐
│  생성자 바인딩 (record)              │  자바빈 바인딩 (class + setter)      │
│                                     │                                     │
│  1. Environment에서 프로퍼티 조회     │  1. 기본 생성자로 빈 인스턴스 생성    │
│  2. 생성자 파라미터에 값 주입         │  2. Environment에서 프로퍼티 조회     │
│  3. 새 인스턴스 생성 (불변)           │  3. setter로 값 주입 (가변)          │
│                                     │                                     │
│  → 인스턴스 생성 후 변경 불가         │  → 인스턴스 생성 후에도 setter 호출   │
│  → refresh 시 새 인스턴스로 교체      │  → refresh 시 기존 인스턴스에 rebind │
└─────────────────────────────────────┴─────────────────────────────────────┘
```

Spring Boot는 바인딩 방식을 자동으로 결정한다:
- record 타입이거나 `@ConstructorBinding`이 있으면 → 생성자 바인딩
- 기본 생성자 + setter가 있으면 → 자바빈 바인딩

## 기본 사용법

### record 기반 (생성자 바인딩) — 권장

```java
// [실무노트] record는 자동으로 생성자 바인딩이 적용된다.
// 필드가 final이므로 바인딩 후 값이 변경될 수 없어 스레드 안전성이 보장된다.
// @ConstructorBinding 애노테이션은 생성자가 하나뿐이면 생략 가능하다.
@ConfigurationProperties(prefix = "polar")
public record PolarProperties(String greeting) {}
```

### class 기반 (자바빈 바인딩) — 현재 프로젝트 코드

```java
// [실무노트] setter 기반이므로 런타임에 값이 변경될 수 있다.
// 멀티스레드 환경에서 setter 호출 시점에 따라 일관성 없는 값을 읽을 가능성이 있다.
@Setter
@Getter
@ConfigurationProperties(prefix = "polar")
public class PolarProperties {
    private String greeting;
}
```

### application.yml

```yaml
polar:
  greeting: Welcome to the local book catalog!
```

두 방식 모두 `@ConfigurationPropertiesScan`이 있으면 자동으로 빈으로 등록된다.
프로젝트의 `CatalogServiceApplication`에 이미 선언되어 있으므로 별도 설정이 필요 없다.

## 심화 내용

### 바인딩 방식별 내부 동작 차이

#### 생성자 바인딩 (record)

```
Binder.bind("polar", Bindable.of(PolarProperties.class))
  ↓
ConstructorBound 감지 → ValueObjectBinder 사용
  ↓
DefaultBindConstructorProvider가 생성자 파라미터 목록 추출
  ↓
각 파라미터에 대해 Environment에서 값 조회
  ↓
new PolarProperties("Welcome to the local book catalog!")
  ↓
불변 인스턴스 반환
```

- 생성자 파라미터에 값이 없으면 `null` 또는 기본값이 들어간다
- `@DefaultValue` 애노테이션으로 기본값을 지정할 수 있다:

```java
// [실무노트] @DefaultValue로 yml에 키가 없을 때의 폴백 값을 지정한다.
// record의 경우 생성자 파라미터에 직접 붙인다.
@ConfigurationProperties(prefix = "polar")
public record PolarProperties(
    @DefaultValue("Hello!") String greeting
) {}
```

#### 자바빈 바인딩 (class + setter)

```
Binder.bind("polar", Bindable.of(PolarProperties.class))
  ↓
setter 존재 → JavaBeanBinder 사용
  ↓
기본 생성자로 인스턴스 생성
  ↓
각 프로퍼티에 대해 setter 호출
  ↓
setGreeting("Welcome to the local book catalog!")
  ↓
가변 인스턴스 반환
```

### Config Refresh 시 동작 차이

`POST /actuator/refresh` 호출 시 `ConfigurationPropertiesRebinder`가 동작한다.
이때 record와 class의 갱신 방식이 다르다:

```
POST /actuator/refresh
  ↓
ConfigurationPropertiesRebinder.rebind()
  ↓
┌─────────────────────────────────────┬─────────────────────────────────────┐
│  record (생성자 바인딩)              │  class (자바빈 바인딩)               │
│                                     │                                     │
│  기존 인스턴스 폐기                  │  기존 인스턴스 유지                   │
│  → 새 인스턴스 생성 (새 값으로)       │  → setter로 새 값 덮어쓰기           │
│                                     │                                     │
│  ✅ yml key 삭제 시 null/기본값      │  ❌ yml key 삭제 시 이전 값 잔존      │
│  ✅ 항상 일관된 상태                  │  ⚠️ rebind 중 불일치 가능            │
└─────────────────────────────────────┴─────────────────────────────────────┘
```

이 차이는 `ConfigRefresh.md`에서 다룬 "yml key 삭제 미감지 문제"와 직접 연결된다.
record 방식은 매번 새 인스턴스를 생성하므로 이 문제가 발생하지 않는다.

### 검증 (Validation)

두 방식 모두 `@Validated`를 클래스에 붙이면 바인딩 시점에 검증이 실행된다:

```java
// [실무노트] @Validated를 붙이면 애플리케이션 기동 시점에 프로퍼티 검증이 실행된다.
// 잘못된 설정값이 있으면 기동 자체가 실패하므로, 런타임 오류를 사전에 방지할 수 있다.
@Validated
@ConfigurationProperties(prefix = "polar")
public record PolarProperties(
    @NotBlank String greeting
) {}
```

기동 시 `polar.greeting`이 비어있으면:
```
***************************
APPLICATION FAILED TO START
***************************
Binding to target [Bindable@... type = com.polarbookshop.catalogservice.config.PolarProperties] failed:

    Property: polar.greeting
    Value:
    Reason: must not be blank
```

## 실무 고려사항

### 🌱 Spring 관점

```
의사결정 흐름:

Q1. 프로퍼티 객체가 바인딩 후 변경될 필요가 있는가?
  ├─ YES → class + setter (드문 경우)
  └─ NO (대부분)
      ↓
Q2. Java 17+ 사용 가능한가?
  ├─ YES → record 사용 (권장)
  └─ NO  → class + @ConstructorBinding
```

- record는 `equals()`, `hashCode()`, `toString()`이 자동 생성되어 디버깅과 테스트가 편하다
- class 기반에서 `@PostConstruct`로 파생 값을 계산하면 refresh 시 재실행되지 않는 함정이 있다 (`ConfigRefresh.md` 참고)
- 중첩 프로퍼티도 record로 표현 가능하다:

```java
// [실무노트] 중첩 프로퍼티는 내부 record로 표현한다.
// polar.server.host, polar.server.port 형태로 바인딩된다.
@ConfigurationProperties(prefix = "polar")
public record PolarProperties(
    String greeting,
    Server server
) {
    public record Server(String host, int port) {}
}
```

### ☁️ Cloud Native 관점

- 12-Factor App의 III. Config 원칙 — 설정을 코드와 분리하여 환경변수나 외부 설정 서버에서 주입한다. `@ConfigurationProperties`는 이 원칙의 Spring 구현체다
- Config Server + `@ConfigurationProperties` 조합에서 record를 사용하면, refresh 시 항상 깨끗한 새 인스턴스가 생성되어 설정 불일치 위험이 줄어든다

## 정리

| 항목 | record (생성자 바인딩) | class (자바빈 바인딩) |
|------|:---------------------:|:--------------------:|
| 불변성 | ✅ final 필드 | ❌ setter로 변경 가능 |
| 스레드 안전성 | ✅ 보장 | ⚠️ setter 호출 시점 주의 |
| refresh 방식 | 새 인스턴스 생성 | 기존 인스턴스에 rebind |
| yml key 삭제 반영 | ✅ null/기본값 | ❌ 이전 값 잔존 |
| @PostConstruct | 지원 안 됨 (불필요) | 지원됨 (refresh 시 미실행 주의) |
| 기본값 지정 | `@DefaultValue` | 필드 초기화 |
| 검증 | `@Validated` + 생성자 파라미터 | `@Validated` + 필드 |
| 보일러플레이트 | 없음 | Lombok 또는 수동 getter/setter |

> `@ConfigurationProperties`는 record로 선언하는 것이 가장 간결하고 안전하다.
> 불변성이 보장되고, Config Refresh 시 새 인스턴스로 교체되어 설정 불일치 문제가 없으며,
> `@Validated`와 조합하면 잘못된 설정을 기동 시점에 차단할 수 있다.
