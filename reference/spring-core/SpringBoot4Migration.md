# Spring Boot 4 / Spring Framework 7 핵심 변화

## 개요

Spring Boot 4.0 (2025.11)은 Spring Framework 7.0 위에 구축된 메이저 업그레이드다.
Jakarta EE 11 전환, Jackson 3 기본 채택, autoconfigure 모듈 분리, JSpecify null safety 도입 등
실무 마이그레이션에 직접적인 영향을 주는 변경이 다수 포함되어 있다.

🌱 Spring은 메이저 버전마다 "deprecated → removed" 사이클을 엄격히 지킨다.
Boot 3.x에서 deprecated 경고를 무시했다면, Boot 4.0에서 컴파일 에러로 돌아온다.

## Baseline 변경

```
Spring Boot 3.x                    Spring Boot 4.0
─────────────────────────────────────────────────────
JDK 17+                         →  JDK 17+ (JDK 25 권장)
Spring Framework 6.x            →  Spring Framework 7.0
Jakarta EE 10                   →  Jakarta EE 11
  - Servlet 6.0                 →    Servlet 6.1
  - JPA 3.1                     →    JPA 3.2
  - Bean Validation 3.0         →    Bean Validation 3.1
Jackson 2.x                     →  Jackson 3.0 (기본)
Hibernate ORM 6.x               →  Hibernate ORM 7.1
HikariCP 5.x                    →  HikariCP 7.0
Flyway 10.x                     →  Flyway 11.11
Tomcat 10.1                     →  Tomcat 11.0
Kotlin 1.9+                     →  Kotlin 2.2
Gradle 8.x                      →  Gradle 9 (8.14+)
JUnit 5                         →  JUnit 6
TestContainers 1.x              →  TestContainers 2.0
```

## 핵심 Breaking Changes

### 1. Jackson 3.0 전환

가장 영향 범위가 넓은 변경이다. Jackson 3.0은 패키지가 `com.fasterxml.jackson` → `tools.jackson`으로 변경되었다.

```java
// [실무노트] Jackson 2 → 3 마이그레이션 시 가장 먼저 해야 할 일:
// import 경로 전체 변경. IDE의 "Replace in Files"로 일괄 치환한다.

// Before (Jackson 2)
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;

// After (Jackson 3)
import tools.jackson.databind.ObjectMapper;
import tools.jackson.annotation.JsonProperty;
```

```java
// [실무노트] Jackson2ObjectMapperBuilder는 더 이상 없다.
// JsonMapper.builder()를 직접 사용한다.

// Before
ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
    .featuresToEnable(SerializationFeature.INDENT_OUTPUT)
    .build();

// After
JsonMapper mapper = JsonMapper.builder()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .build();
```

Boot 4.0은 Jackson 2를 deprecated 형태로만 유지한다. 신규 프로젝트는 반드시 Jackson 3을 사용해야 한다.

### 2. Jakarta EE 11 전환

```java
// [실무노트] javax → jakarta 전환은 Boot 3.0에서 이미 완료되었지만,
// Boot 4.0에서는 javax.annotation.Resource, javax.inject.Inject 등
// 잔여 javax 의존성까지 완전히 제거되었다.

// 컴파일 에러가 나는 경우:
import javax.annotation.PostConstruct;    // ❌ 제거됨
import jakarta.annotation.PostConstruct;  // ✅
```

주요 Jakarta EE 11 버전 변경:

| 스펙 | Boot 3.x | Boot 4.0 | 영향 |
|------|----------|----------|------|
| Servlet | 6.0 | 6.1 | Tomcat 11 필수 |
| JPA | 3.1 | 3.2 | Hibernate 7.1 필수 |
| Bean Validation | 3.0 | 3.1 | Hibernate Validator 9.0 |
| WebSocket | 2.1 | 2.2 | 마이너 API 변경 |

### 3. Undertow 지원 제거

```
Servlet 6.1을 지원하지 않아 완전히 제거되었다.

사용 가능한 임베디드 서버:
  ✅ Tomcat 11.0
  ✅ Jetty 12.1
  ❌ Undertow (제거)
```

```java
// [실무노트] Undertow를 사용 중이었다면 Tomcat 또는 Jetty로 전환해야 한다.
// Boot 4.0에서 spring-boot-starter-undertow 의존성 자체가 없다.
```

### 4. spring-jcl 모듈 제거

```java
// [실무노트] Spring의 자체 Commons Logging 브릿지(spring-jcl)가 제거되었다.
// Apache Commons Logging 1.3.0이 직접 사용된다.
// 대부분의 프로젝트에서는 SLF4J + Logback 조합이므로 영향이 적지만,
// spring-jcl을 명시적으로 exclude하던 설정이 있다면 제거해야 한다.
```

### 5. 속성명 변경

```yaml
# Before (Boot 3.x)
spring:
  dao:
    exceptiontranslation:
      enabled: true
management:
  tracing:
    enabled: true

# After (Boot 4.0)
spring:
  persistence:
    exceptiontranslation:
      enabled: true
management:
  tracing:
    export:
      enabled: true
```

### 6. HttpHeaders 변경

```java
// [실무노트] HttpHeaders가 더 이상 MultiValueMap<String, String>을 확장하지 않는다.
// MultiValueMap으로 캐스팅하던 코드는 컴파일 에러가 발생한다.

// Before
MultiValueMap<String, String> headers = httpHeaders; // ❌ Boot 4에서 불가

// After
httpHeaders.getFirst("Content-Type");  // ✅ HttpHeaders API 직접 사용
```

### 7. Auto-Configuration 모듈화

🌱 Boot 4.0의 구조적으로 가장 큰 변화다. 기존에는 어떤 starter를 추가하든 거대한 `spring-boot-autoconfigure` JAR 하나가 통째로 딸려왔다. Boot 4.0에서는 이 JAR이 기술별 모듈로 분리되었다.

```
Boot 3.x 구조:
┌─────────────────────────────────────────────────┐
│         spring-boot-autoconfigure (단일 JAR)      │
│                                                   │
│  WebMVC config + JDBC config + JPA config +       │
│  Batch config + Kafka config + Redis config + ... │
│                                                   │
│  → 웹만 쓰는데 Batch, Kafka 설정 클래스까지 포함   │
└─────────────────────────────────────────────────┘

Boot 4.0 구조:
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│ spring-boot-     │ │ spring-boot-     │ │ spring-boot-     │
│ webmvc           │ │ data-jdbc        │ │ kafka            │
│                  │ │                  │ │                  │
│ WebMVC 설정만    │ │ JDBC 설정만      │ │ Kafka 설정만     │
└──────────────────┘ └──────────────────┘ └──────────────────┘
  → 필요한 모듈만 포함, 의존성 트리가 깨끗해짐
```

#### Starter 매핑 변경

| 기술 | Boot 3.x | Boot 4.0 |
|------|----------|----------|
| Spring MVC | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| REST Client | web starter에 포함 | `spring-boot-starter-restclient` |
| WebClient | webflux starter에 포함 | `spring-boot-starter-webclient` |
| H2 Console | `h2` 의존성만 추가 | `spring-boot-starter-h2-console` |
| Flyway | `flyway-core` 의존성만 추가 | `spring-boot-starter-flyway` |
| Liquibase | `liquibase-core` 의존성만 추가 | `spring-boot-starter-liquibase` |
| WebFlux | `spring-boot-starter-webflux` | `spring-boot-starter-webflux` (동일) |

```groovy
// [실무노트] Boot 3.x에서는 H2 드라이버만 클래스패스에 있으면
// auto-configuration이 콘솔을 자동 활성화했다.
// Boot 4.0에서는 해당 auto-configuration 클래스가 별도 모듈로 분리되어
// 명시적으로 starter를 추가해야 한다.

// Before (Boot 3.x) — build.gradle
dependencies {
    runtimeOnly 'com.h2database:h2'  // 이것만으로 H2 콘솔 활성화
}

// After (Boot 4.0) — build.gradle
dependencies {
    runtimeOnly 'com.h2database:h2'
    implementation 'org.springframework.boot:spring-boot-starter-h2-console'  // 명시 필요
}
```

#### 테스트 모듈도 분리

테스트용 auto-configuration도 별도 starter로 분리되었다. `@WebMvcTest`, `@DataJdbcTest` 같은 슬라이스 테스트를 사용한다면 대응하는 test starter가 필요하다.

```groovy
// [실무노트] Boot 3.x에서는 spring-boot-starter-test 하나로 모든 슬라이스 테스트가 가능했다.
// Boot 4.0에서는 사용하는 슬라이스에 맞는 test starter를 추가해야 한다.

dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'

    // Boot 4.0에서 추가 필요
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'     // @WebMvcTest
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jdbc-test'  // @DataJdbcTest
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'   // @DataJpaTest
}
```

#### Escape Hatch — 점진적 마이그레이션

한 번에 모든 starter를 바꾸기 어렵다면, `spring-boot-autoconfigure-classic`을 추가하여 Boot 3.x처럼 모든 auto-configuration을 한 번에 가져올 수 있다.

```groovy
// [실무노트] 마이그레이션 과도기에만 사용하고, 안정화 후 제거해야 한다.
// classic 모듈은 향후 버전에서 제거될 수 있다.
dependencies {
    implementation 'org.springframework.boot:spring-boot-autoconfigure-classic'

    // 테스트도 마찬가지
    testImplementation 'org.springframework.boot:spring-boot-starter-test-classic'
}
```

## 주요 Deprecation

| 대상 | 대안 | 제거 예정 |
|------|------|----------|
| `RestTemplate` | `RestClient` | Framework 7.1에서 @Deprecated |
| Jackson 2.x 지원 | Jackson 3.0 | Boot 4.x 내 |
| MVC XML 설정 (`<mvc:*>`) | Java Config | 향후 제거 |
| JUnit 4 지원 | JUnit Jupiter (`SpringExtension`) | 향후 제거 |
| `ListenableFuture` | `CompletableFuture` | 이미 제거 |
| `AntPathMatcher` (HTTP) | `PathPatternParser` | 향후 제거 |
| Spring nullness 어노테이션 | JSpecify 어노테이션 | 향후 제거 |

```java
// [실무노트] RestTemplate은 아직 동작하지만, 신규 코드에서는 RestClient를 사용한다.
// RestClient는 fluent API로 가독성이 좋고, HTTP Interface Client와도 연동된다.

// Before (RestTemplate)
ResponseEntity<Book> response = restTemplate.getForEntity(
    "http://catalog-service/books/{isbn}", Book.class, isbn);

// After (RestClient)
Book book = restClient.get()
    .uri("http://catalog-service/books/{isbn}", isbn)
    .retrieve()
    .body(Book.class);
```

## 주요 신규 기능

### Null Safety — JSpecify 전환

🌱 Spring Framework 7.0은 자체 `@Nullable`/`@NonNull` 대신 [JSpecify](https://jspecify.dev/) 표준 어노테이션을 채택했다.

```java
// [실무노트] JSpecify는 제네릭 타입 인자, 배열 요소, varargs의
// nullness까지 표현할 수 있어 기존 Spring 어노테이션보다 표현력이 높다.
// Kotlin과의 상호운용성도 개선된다.

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

// 제네릭 타입 인자의 nullness 표현 가능
List<@Nullable String> names;  // 리스트 요소가 null일 수 있음
```

### 내장 Resilience — @Retryable, @ConcurrencyLimit

🌱 Spring Framework 7.0은 재시도와 동시성 제한을 spring-core에 내장했다.
기존에는 Spring Retry 별도 라이브러리가 필요했다.

```java
// [실무노트] spring-retry 의존성 없이 재시도를 사용할 수 있다.
// @EnableResilientMethods로 활성화한다.

@Configuration
@EnableResilientMethods
public class ResilienceConfig {}

@Service
public class CatalogService {

    @Retryable(maxAttempts = 3)
    public Book getBook(String isbn) {
        return restClient.get()
            .uri("/books/{isbn}", isbn)
            .retrieve()
            .body(Book.class);
    }

    // [실무노트] @ConcurrencyLimit으로 동시 실행 수를 제한할 수 있다.
    // 외부 API 호출 시 상대 서비스를 보호하는 용도로 유용하다.
    @ConcurrencyLimit(permits = 10)
    public void syncInventory() {
        // ...
    }
}
```

### API Versioning

🌱 Spring MVC/WebFlux에서 REST API 버전 관리를 프레임워크 수준에서 지원한다.

```yaml
# [실무] Boot 4.0에서 API 버전 관리 활성화
spring:
  mvc:
    apiversion:
      enabled: true
      media-type:
        parameter-name: version
```

```java
// [실무노트] @ApiVersion으로 컨트롤러 메서드에 버전을 지정한다.
// 미디어 타입 기반, 헤더 기반, URL 기반 버전 해석을 지원한다.
@RestController
@RequestMapping("/books")
public class BookController {

    @GetMapping("/{isbn}")
    @ApiVersion("1")
    public BookV1 getBookV1(@PathVariable String isbn) { ... }

    @GetMapping("/{isbn}")
    @ApiVersion("2")
    public BookV2 getBookV2(@PathVariable String isbn) { ... }
}
```

### BeanRegistrar — 프로그래밍 방식 빈 등록

```java
// [실무노트] @Configuration 없이 프로그래밍 방식으로 빈을 등록할 수 있다.
// 조건부 빈 등록이나 동적 빈 생성에 유용하다.
public class CatalogBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry) {
        registry.registerBean("bookValidator", BookValidator.class);
    }
}
```

### HTTP Interface Client 자동 구성

```java
// [실무노트] @HttpExchange 인터페이스를 선언하면
// Boot 4.0이 자동으로 프록시 빈을 생성한다.
// 별도 @Bean 설정 없이 @ImportHttpServices로 활성화한다.

@HttpExchange("/books")
public interface BookClient {

    @GetExchange("/{isbn}")
    Book getByIsbn(@PathVariable String isbn);

    @PostExchange
    Book create(@RequestBody Book book);
}
```

### OpenTelemetry Starter

```yaml
# [실무] spring-boot-starter-opentelemetry 추가로
# OTLP 기반 metrics/traces 내보내기를 한 번에 설정할 수 있다.
# 기존에는 micrometer-tracing + opentelemetry-exporter를 개별 설정해야 했다.
```

### RestTestClient

```java
// [실무노트] WebTestClient의 비반응형(non-reactive) 버전이다.
// MVC 기반 서비스를 테스트할 때 WebFlux 의존성 없이 사용할 수 있다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookControllerTests {

    @Autowired
    RestTestClient restTestClient;

    @Test
    void shouldReturnBook() {
        restTestClient.get().uri("/books/1234567890")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Book.class)
            .value(book -> assertThat(book.isbn()).isEqualTo("1234567890"));
    }
}
```

## 실무 고려사항

### 🌱 Spring 관점

- **마이그레이션 순서** — ① Jackson 3 전환 (영향 범위 최대) → ② 속성명 변경 → ③ 제거된 API 대체 → ④ 신규 기능 도입 순서가 안전하다
- **RestTemplate → RestClient** — 당장 제거되지는 않지만, 7.1에서 @Deprecated 예정이므로 신규 코드는 RestClient로 작성한다
- **Auto-configuration 모듈 분리** — `spring-boot-autoconfigure` JAR이 기능별로 분리되었다. 커스텀 auto-configuration을 작성했다면 import 경로를 확인해야 한다
- **Hibernate 7.1** — JPA 3.2 기반으로 `EntityManagerFactory` 생성 방식이 변경되었다. `hibernate.hbm2ddl.auto` 동작에 미세한 차이가 있을 수 있다

### ☁️ Cloud Native 관점

- **OpenTelemetry 일급 지원** — `spring-boot-starter-opentelemetry`로 관측성 설정이 크게 간소화된다. 기존 Micrometer + 개별 exporter 조합보다 설정이 단순하고, OTLP 표준을 따르므로 벤더 종속성이 줄어든다
- **내장 Resilience** — `@Retryable`, `@ConcurrencyLimit`가 spring-core에 포함되어 별도 라이브러리 없이 복원력 패턴을 적용할 수 있다. 다만 서킷 브레이커는 여전히 Resilience4j가 필요하다
- **HikariCP 7.0** — 메이저 버전 업그레이드이므로 커넥션 풀 설정의 속성명이나 기본값 변경 여부를 확인해야 한다
- **TestContainers 2.0** — 통합 테스트에서 컨테이너 기반 테스트를 사용 중이라면 API 변경사항을 확인해야 한다
- **GraalVM 25** — Native Image 빌드 시 새로운 "exact reachability metadata" 형식을 사용한다. 기존 reflect-config.json 등의 형식이 변경될 수 있다

## 마이그레이션 체크리스트

```
□ Jackson 2 → 3 import 경로 변경 (com.fasterxml.jackson → tools.jackson)
□ Jackson2ObjectMapperBuilder → JsonMapper.builder() 전환
□ 잔여 javax.* import를 jakarta.*로 변경
□ Undertow 사용 시 Tomcat/Jetty로 전환
□ 변경된 속성명 업데이트 (spring.dao → spring.persistence 등)
□ HttpHeaders를 MultiValueMap으로 캐스팅하는 코드 수정
□ RestTemplate → RestClient 전환 (권장)
□ ListenableFuture → CompletableFuture 전환
□ spring-jcl exclude 설정 제거
□ Auto-configuration 커스텀 클래스의 import 경로 확인
□ spring-boot-starter-web → spring-boot-starter-webmvc 전환
□ 슬라이스 테스트용 test starter 추가 (webmvc-test, data-jdbc-test 등)
□ raw 의존성(h2, flyway-core 등)을 전용 starter로 교체
□ Gradle 8.14+ 또는 9로 업그레이드
□ 테스트: JUnit 6 / TestContainers 2.0 호환성 확인
□ Native Image: GraalVM 25 메타데이터 형식 확인
```

## 정리

| 변경 영역 | 핵심 내용 | 영향도 |
|----------|----------|--------|
| Jackson 3.0 | 패키지 경로 변경, Builder API 변경 | 🔴 높음 |
| Jakarta EE 11 | Servlet 6.1, JPA 3.2, Validation 3.1 | 🟡 중간 |
| Auto-Config 모듈화 | autoconfigure JAR 분리, starter 매핑 변경, test starter 추가 필요 | 🔴 높음 |
| Undertow 제거 | Tomcat/Jetty만 지원 | 🟡 해당 시 높음 |
| Null Safety | JSpecify 표준 어노테이션 전환 | 🟢 점진적 |
| RestTemplate | Deprecated 예정, RestClient 권장 | 🟡 점진적 |
| 내장 Resilience | @Retryable, @ConcurrencyLimit 내장 | 🟢 신규 |
| API Versioning | 프레임워크 수준 버전 관리 | 🟢 신규 |
| OpenTelemetry | 공식 Starter 추가 | 🟢 신규 |

> Spring Boot 4.0 마이그레이션의 핵심은 Jackson 3 전환이다.
> 패키지 경로가 완전히 바뀌므로 영향 범위가 가장 넓고, 커스텀 Serializer/Deserializer가 있다면 모두 수정해야 한다.
> 나머지 변경은 대부분 점진적으로 대응 가능하며, 신규 기능(Resilience, API Versioning, OpenTelemetry)은
> Cloud Native 애플리케이션의 운영 품질을 프레임워크 수준에서 끌어올린다.