# Spring Cloud 설정 관리 솔루션 비교

## 개요

12-Factor App의 세 번째 원칙 **III. Config**는 설정을 코드에서 분리하여 환경 변수나 외부 저장소에서 관리할 것을 요구한다.
마이크로서비스 환경에서는 수십~수백 개의 서비스가 각각의 설정을 가지므로, 중앙화된 설정 관리가 필수다.

외부 설정 관리의 핵심 가치는 **재배포 없이 런타임에 설정을 변경**할 수 있다는 점이다.
기능 플래그 토글, 로그 레벨 조정, 연결 풀 크기 변경 등을 새 빌드/배포 파이프라인 없이 즉시 반영할 수 있어,
운영 민첩성과 장애 대응 속도가 크게 향상된다.

```
┌─────────────────────────────────────────────────────────┐
│              12-Factor App: III. Config                  │
│                                                         │
│  "설정을 코드에서 엄격히 분리하라"                       │
│  "환경마다 달라지는 값은 환경 변수에 저장하라"           │
│                                                         │
│  ✗ application.yml에 DB URL 하드코딩                    │
│  ✗ 프로파일별 설정 파일 번들링 (jar 내부)               │
│  ✓ 외부 설정 서버에서 런타임에 주입                     │
│  ✓ 환경 변수 또는 외부 저장소 활용                      │
│  ✓ 재배포 없이 런타임에 설정 변경 가능                  │
└─────────────────────────────────────────────────────────┘
```

| 솔루션 | 핵심 목적 | 저장소 | Spring 통합 |
|--------|-----------|--------|-------------|
| Spring Cloud Config Server | 설정 중앙화 | Git, File, JDBC | 네이티브 |
| HashiCorp Consul | 서비스 디스커버리 + KV Store | Raft 기반 내장 | spring-cloud-consul |
| Apache ZooKeeper | 분산 코디네이션 | ZAB 기반 내장 | spring-cloud-zookeeper |
| HashiCorp Vault | 시크릿 관리 | 다양한 Secret Engine | spring-cloud-vault |

## 핵심 개념

### 1. Spring Cloud Config Server

Git 저장소를 백엔드로 사용하여 모든 마이크로서비스의 설정을 중앙에서 관리한다.
Spring 생태계와 가장 자연스럽게 통합되며, 러닝커브가 낮다.

#### 아키텍처

```
┌──────────────┐     ┌─────────────────────┐     ┌──────────────────┐
│  Git Repo    │     │  Config Server      │     │  Microservices   │
│              │     │                     │     │                  │
│ catalog-     │────>│  @EnableConfigServer │────>│  catalog-service │
│  service.yml │     │                     │     │  order-service   │
│ order-       │     │  :8888              │     │  edge-service    │
│  service.yml │     │                     │     │                  │
│ application  │     │  GET /{app}/{profile}│     │  bootstrap.yml   │
│  .yml(공통)  │     │                     │     │  → config server │
└──────────────┘     └─────────────────────┘     └──────────────────┘
```

#### Config Server 설정

```java
// [실무노트] Config Server는 독립 서비스로 배포한다.
// 다른 서비스보다 먼저 기동되어야 하므로 K8s에서는 initContainer 또는
// spring.cloud.config.fail-fast + retry 설정으로 대응한다.
@SpringBootApplication
@EnableConfigServer
public class ConfigServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
```

```yaml
# Config Server의 application.yml
server:
  port: 8888

spring:
  cloud:
    config:
      server:
        git:
          # [실무] 운영 환경에서는 private repo + SSH 키 인증 사용
          uri: https://github.com/my-org/config-repo
          default-label: main
          search-paths: '{application}'  # 서비스별 디렉토리 분리
          clone-on-start: true           # 기동 시 clone (fail-fast)
          timeout: 5                     # Git 연결 타임아웃(초)
```

#### 클라이언트 설정

```yaml
# catalog-service의 application.yml
spring:
  application:
    name: catalog-service
  config:
    # [실무] Spring Boot 4.x에서는 spring.config.import 방식 사용
    import: "configserver:http://config-service:8888"
  cloud:
    config:
      fail-fast: true    # Config Server 연결 실패 시 즉시 종료
      retry:
        max-attempts: 6
        initial-interval: 1000
        multiplier: 1.5
```

#### @RefreshScope로 런타임 설정 갱신

```java
// [실무노트] @RefreshScope 빈은 /actuator/refresh 호출 시 재생성된다.
// 단, 대규모 서비스에서는 Spring Cloud Bus(RabbitMQ/Kafka)로
// 모든 인스턴스에 일괄 갱신 이벤트를 전파하는 것이 실용적이다.
@RestController
@RefreshScope
public class MessageController {

    @Value("${greeting.message:Hello}")
    private String message;

    @GetMapping("/message")
    public String getMessage() {
        return message;
    }
}
```

```
갱신 흐름:

1. Git Repo에서 설정 변경 + push
   ↓
2-A. 수동: POST /actuator/refresh (단일 인스턴스)
2-B. 자동: Git Webhook → Config Server → Spring Cloud Bus → 전체 인스턴스
   ↓
3. @RefreshScope 빈 재생성
   ↓
4. 새 설정값 반영 (재시작 불필요)
```

#### 장단점

| 장점 | 단점 |
|------|------|
| Spring 생태계 네이티브 통합 | Config Server 자체가 SPOF 가능 |
| Git 이력으로 설정 변경 추적/롤백 | 실시간 반영 아님 (refresh 필요) |
| 낮은 러닝커브, 빠른 도입 | 대규모 환경에서 Bus 추가 필요 |
| 프로파일/라벨 기반 환경 분리 | Git 저장소 가용성에 의존 |
| 설정 암호화 지원 (symmetric/asymmetric) | 시크릿 관리에는 Vault가 더 적합 |

### 2. HashiCorp Consul

서비스 디스커버리와 KV(Key-Value) Store를 하나의 플랫폼에서 제공한다.
설정 변경을 실시간으로 Watch할 수 있어, Config Server보다 빠른 반영이 가능하다.

#### 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    Consul Cluster (Raft)                     │
│                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │  Server   │◄──►│  Server   │◄──►│  Server   │  (3 or 5)  │
│  │  (Leader) │    │ (Follower)│    │ (Follower)│             │
│  └─────┬────┘    └──────────┘    └──────────┘              │
│        │                                                    │
│  ┌─────▼──────────────────────────────────┐                │
│  │           KV Store                      │                │
│  │  config/catalog-service/data  → YAML   │                │
│  │  config/order-service/data    → YAML   │                │
│  │  config/application/data      → 공통   │                │
│  └────────────────────────────────────────┘                │
└──────────────────────┬──────────────────────────────────────┘
                       │ Watch (Long Polling)
          ┌────────────┼────────────┐
          ▼            ▼            ▼
   ┌────────────┐ ┌────────────┐ ┌────────────┐
   │ catalog-   │ │ order-     │ │ edge-      │
   │ service    │ │ service    │ │ service    │
   └────────────┘ └────────────┘ └────────────┘
```

#### Spring Cloud Consul Config 설정

```yaml
# application.yml
spring:
  application:
    name: catalog-service
  config:
    import: "consul:localhost:8500"
  cloud:
    consul:
      host: localhost
      port: 8500
      config:
        # [실무] format을 YAML로 설정하면 Consul KV에 YAML 블록 저장 가능
        format: YAML
        prefix: config           # KV 경로 prefix
        default-context: application  # 공통 설정 경로
        watch:
          enabled: true          # 실시간 변경 감지
          delay: 1000            # 폴링 간격(ms)
      discovery:
        enabled: true            # 서비스 디스커버리도 함께 사용
        health-check-interval: 10s
```

```groovy
// build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-consul-config'
    implementation 'org.springframework.cloud:spring-cloud-starter-consul-discovery'
}
```

#### 장단점

| 장점 | 단점 |
|------|------|
| 서비스 디스커버리 + 설정 통합 플랫폼 | 운영 복잡도 (클러스터 관리) |
| HA 내장 (Raft 합의 알고리즘) | BSL 1.1 라이선스 (v1.16+) |
| 실시간 Watch로 즉각 반영 | Spring 통합이 Config Server보다 덜 매끄러움 |
| 멀티 데이터센터 지원 | KV Store가 전문 설정 관리 도구는 아님 |
| UI 대시보드 내장 | Git 기반 이력 관리 불가 |

### 3. Apache ZooKeeper

Apache 재단의 분산 코디네이션 서비스로, Hadoop, Kafka 등에서 오랫동안 사용되어 검증된 안정성을 가진다.
ZNode 트리 구조에 설정 데이터를 저장하고, Watcher로 변경을 감지한다.

#### 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│              ZooKeeper Ensemble (ZAB Protocol)           │
│                                                         │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐          │
│  │  Server   │◄──►│  Server   │◄──►│  Server   │ (홀수)  │
│  │  (Leader) │    │ (Follower)│    │ (Follower)│         │
│  └─────┬────┘    └──────────┘    └──────────┘          │
│        │                                                │
│  ┌─────▼──────────────────────────────────┐            │
│  │         ZNode Tree                      │            │
│  │  /config                                │            │
│  │    ├── /catalog-service                 │            │
│  │    │     ├── server.port = "9001"       │            │
│  │    │     └── polar.greeting = "Hello"   │            │
│  │    ├── /order-service                   │            │
│  │    │     └── ...                        │            │
│  │    └── /application (공통)              │            │
│  └────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────┘
```

#### Spring Cloud ZooKeeper Config 설정

```yaml
# application.yml
spring:
  application:
    name: catalog-service
  config:
    import: "zookeeper:localhost:2181"
  cloud:
    zookeeper:
      connect-string: localhost:2181
      config:
        enabled: true
        root: config              # ZNode 루트 경로
        default-context: application
```

```groovy
// build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-zookeeper-config'
}
```

#### 장단점

| 장점 | 단점 |
|------|------|
| 오랜 기간 검증된 안정성 | JVM 기반, 높은 리소스 소비 |
| 강한 일관성 (CP 시스템) | 운영 복잡도 (앙상블 관리) |
| Watcher로 실시간 변경 감지 | 생태계 퇴장 추세 (Kafka KRaft 등) |
| Apache 2.0 라이선스 | 설정 관리 전용 도구가 아님 |
| 대규모 분산 시스템에서 검증됨 | UI 없음, CLI 기반 관리 |

### 4. HashiCorp Vault

시크릿(비밀번호, API 키, 인증서 등) 관리에 특화된 도구다.
Dynamic Secrets(동적 자격증명)을 통해 DB 비밀번호 등을 자동 생성/폐기할 수 있어,
보안 수준을 근본적으로 높인다.

#### 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        HashiCorp Vault                          │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐ │
│  │  Auth Methods    │  │  Secret Engines  │  │  Audit Devices │ │
│  │                  │  │                  │  │                │ │
│  │  - Token         │  │  - KV v2         │  │  - File        │ │
│  │  - Kubernetes    │  │  - Database      │  │  - Syslog      │ │
│  │  - AppRole       │  │  - PKI           │  │  - Socket      │ │
│  │  - LDAP/OIDC     │  │  - Transit       │  │                │ │
│  └────────┬────────┘  └────────┬────────┘  └────────────────┘ │
│           │                    │                                │
│  ┌────────▼────────────────────▼──────────────────────┐       │
│  │              Barrier (AES-256 암호화)               │       │
│  │              Storage Backend (Raft/Consul)          │       │
│  └────────────────────────────────────────────────────┘       │
└───────────────────────────┬─────────────────────────────────────┘
                            │
           ┌────────────────┼────────────────┐
           ▼                ▼                ▼
    ┌────────────┐   ┌────────────┐   ┌────────────┐
    │ catalog-   │   │ order-     │   │ edge-      │
    │ service    │   │ service    │   │ service    │
    │            │   │            │   │            │
    │ K8s SA     │   │ AppRole    │   │ Token      │
    │ 인증       │   │ 인증       │   │ 인증       │
    └────────────┘   └────────────┘   └────────────┘
```

#### Spring Cloud Vault 설정

```yaml
# application.yml
spring:
  application:
    name: catalog-service
  config:
    import: "vault://secret/catalog-service"
  cloud:
    vault:
      uri: https://vault.example.com:8200
      # [실무] Kubernetes 환경에서는 ServiceAccount 토큰 인증이 가장 안전
      authentication: KUBERNETES
      kubernetes:
        role: catalog-service
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
      kv:
        enabled: true
        backend: secret
        default-context: catalog-service
        application-name: catalog-service
```

```groovy
// build.gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-vault-config'
}
```

#### Dynamic Secrets 예시 (Database)

```yaml
# [실무] DB 비밀번호를 Vault가 자동 생성/폐기 → 유출 위험 최소화
spring:
  cloud:
    vault:
      database:
        enabled: true
        role: catalog-service-db    # Vault DB role
        backend: database
        # Vault가 생성한 임시 자격증명이 자동으로
        # spring.datasource.username / password에 주입됨
```

```
Dynamic Secrets 흐름:

1. 서비스 기동 → Vault 인증 (K8s ServiceAccount)
   ↓
2. Vault에 DB 자격증명 요청
   ↓
3. Vault가 DB에 임시 계정 생성 (TTL: 1h)
   ↓
4. 서비스에 username/password 주입
   ↓
5. TTL 만료 전 자동 갱신 또는 만료 시 폐기
   ↓
6. 서비스 종료 시 Vault가 DB 계정 삭제
```

#### 장단점

| 장점 | 단점 |
|------|------|
| 시크릿 관리의 사실상 표준 | 일반 설정 관리용으로는 과도 |
| Dynamic Secrets (동적 자격증명) | 높은 러닝커브, 복잡한 초기 설정 |
| 상세한 감사 로그 (Audit Log) | BSL 1.1 라이선스 (v1.14+) |
| 다양한 인증 방식 (K8s, OIDC 등) | 운영 복잡도 (Unseal, HA 구성) |
| Encryption as a Service (Transit) | 별도 인프라 운영 필요 |

## 비교

### 종합 비교 테이블

| 항목 | Config Server | Consul | ZooKeeper | Vault |
|------|:------------:|:------:|:---------:|:-----:|
| 핵심 목적 | 설정 중앙화 | 디스커버리+설정 | 분산 코디네이션 | 시크릿 관리 |
| 저장소 | Git, JDBC, File | Raft 내장 KV | ZAB 내장 ZNode | 다양한 Backend |
| 실시간 갱신 | △ (Bus 필요) | ✅ (Watch) | ✅ (Watcher) | △ (Lease 갱신) |
| 무중단 설정 변경 | ✅ @RefreshScope + Bus | ✅ Watch 자동 반영 | ✅ Watcher 자동 반영 | ✅ Lease 갱신 시 반영 |
| 재배포 필요 여부 | 불필요 (refresh) | 불필요 (자동) | 불필요 (자동) | 불필요 (lease 갱신) |
| HA 구성 | 별도 구성 필요 | 내장 (Raft) | 내장 (ZAB) | 별도 구성 필요 |
| Spring 통합도 | ⭐⭐⭐ 네이티브 | ⭐⭐ 양호 | ⭐⭐ 양호 | ⭐⭐ 양호 |
| 운영 복잡도 | 낮음 | 중간 | 높음 | 높음 |
| 러닝커브 | 낮음 | 중간 | 중간 | 높음 |
| 라이선스 | Apache 2.0 | BSL 1.1 | Apache 2.0 | BSL 1.1 |
| 설정 이력 관리 | ✅ (Git) | ❌ | ❌ | ✅ (Audit Log) |
| 시크릿 관리 | △ (암호화) | ❌ | ❌ | ✅ 전문 |
| UI | ❌ | ✅ 내장 | ❌ | ✅ 내장 |

### 시장 동향 분석

```
인기도/채택률 (Spring 마이크로서비스 환경 기준):

Config Server  ████████████████████████  높음 - Spring 생태계 기본 선택
Consul         ████████████████         중상 - 디스커버리 통합 시 선호
Vault          ████████████████         중상 - 시크릿 관리 사실상 표준
ZooKeeper      ████████                 하락 - 레거시 시스템 위주
K8s Native     ████████████████████     상승 - ConfigMap/Secret 직접 활용
```

- Config Server는 Spring 팀이 직접 관리하므로 Spring Boot 버전 호환성이 가장 안정적
- Consul/Vault는 BSL 라이선스 전환 이후 OpenTofu, OpenBao 등 포크 프로젝트 등장
- Kubernetes 네이티브 설정 관리(ConfigMap + External Secrets Operator)가 빠르게 성장 중

## 실무 팁

### 런타임 설정 변경 메커니즘 비교

재배포 없이 설정을 변경하는 것은 외부 설정 관리의 핵심 가치다.
각 솔루션이 런타임 갱신을 어떻게 지원하는지 이해해야 적절한 선택이 가능하다.

| 솔루션 | 갱신 트리거 | 반영 속도 | 추가 구성 | 재배포 필요 |
|--------|------------|-----------|-----------|:-----------:|
| Config Server | POST /actuator/refresh | 수동 호출 즉시 | @RefreshScope 필수 | ❌ |
| Config Server + Bus | Git Webhook → Bus 이벤트 | 수 초 (자동) | RabbitMQ/Kafka 필요 | ❌ |
| Consul | KV 변경 → Watch 감지 | 1~2초 (자동) | watch.delay 설정 | ❌ |
| ZooKeeper | ZNode 변경 → Watcher 감지 | 즉시 (자동) | 기본 내장 | ❌ |
| Vault | Lease 만료/갱신 시 | TTL 주기 | Lease 설정 | ❌ |
| K8s ConfigMap | Pod 재시작 또는 Volume 갱신 | 수십 초~수 분 | 별도 Reloader 필요 | △ (롤링 재시작) |

```
런타임 설정 변경 시나리오 예시:

[장애 상황] 외부 API 응답 지연 → 타임아웃 값 긴급 조정

  기존 방식 (재배포):
  코드 수정 → 빌드 → 테스트 → 배포 → 반영  (30분~1시간)

  Config Server + Bus:
  Git push → Webhook → Bus → 전체 인스턴스 반영  (수 초)

  Consul:
  KV Store 값 변경 → Watch → 자동 반영  (1~2초)
```

```java
// [실무노트] 런타임 설정 변경의 핵심: @RefreshScope
// @RefreshScope가 없는 빈은 refresh 이벤트가 발생해도 설정이 갱신되지 않는다.
// @ConfigurationProperties 빈은 @RefreshScope 없이도 자동 갱신된다.

// 패턴 1: @RefreshScope + @Value (단순 값 주입)
@RestController
@RefreshScope
public class GreetingController {
    @Value("${app.greeting:Hello}")
    private String greeting;
}

// 패턴 2: @ConfigurationProperties (권장 - @RefreshScope 불필요)
@ConfigurationProperties(prefix = "app.resilience")
public record ResilienceProperties(
    int timeout,        // 런타임에 변경 가능
    int retryAttempts,  // 런타임에 변경 가능
    double backoffMultiplier
) {}
```

```
주의사항:

- DataSource, ConnectionPool 등 인프라 빈은 @RefreshScope로 갱신 시
  기존 커넥션이 끊길 수 있다 → 커넥션 풀 설정은 신중하게 판단
- @RefreshScope 빈은 프록시로 감싸지므로 미세한 성능 오버헤드 존재
- 설정 변경 후 검증 없이 전체 반영하면 장애 전파 위험
  → Canary 방식으로 일부 인스턴스 먼저 반영 후 확인 권장
```

### 권장 조합: Config Server + Vault

```
┌─────────────────────────────────────────────────────────────┐
│                    권장 아키텍처                              │
│                                                             │
│  ┌──────────┐     ┌─────────────────┐                      │
│  │ Git Repo │────>│  Config Server   │──┐                  │
│  │ (일반설정)│     │  (port, url 등) │  │                   │
│  └──────────┘     └─────────────────┘  │  ┌─────────────┐ │
│                                         ├─>│ Microservice│ │
│  ┌──────────┐     ┌─────────────────┐  │  └─────────────┘ │
│  │  Vault   │────>│  Spring Cloud   │──┘                   │
│  │ (시크릿) │     │  Vault Client   │                      │
│  └──────────┘     └─────────────────┘                      │
│                                                             │
│  일반 설정 → Config Server (Git 이력, 쉬운 관리)           │
│  시크릿    → Vault (동적 자격증명, 감사 로그)              │
└─────────────────────────────────────────────────────────────┘
```

```yaml
# [실무] Config Server + Vault 동시 사용 설정
spring:
  application:
    name: catalog-service
  config:
    import:
      - "configserver:http://config-service:8888"
      - "vault://secret/catalog-service"
  cloud:
    config:
      fail-fast: true
    vault:
      uri: https://vault:8200
      authentication: KUBERNETES
      kubernetes:
        role: catalog-service
```

### Kubernetes 환경: ConfigMap/Secret + External Secrets Operator

```
┌─────────────────────────────────────────────────────────────┐
│              Kubernetes Native 설정 관리                     │
│                                                             │
│  ┌──────────────┐     ┌──────────────────────┐             │
│  │ ConfigMap    │────>│  Pod                  │             │
│  │ (일반 설정)  │     │  ┌──────────────────┐│             │
│  └──────────────┘     │  │ env / volume     ││             │
│                       │  │ mount로 주입     ││             │
│  ┌──────────────┐     │  └──────────────────┘│             │
│  │ Secret       │────>│                      │             │
│  │ (시크릿)     │     └──────────────────────┘             │
│  └──────┬───────┘                                          │
│         │ 동기화                                            │
│  ┌──────▼───────────────────────────────┐                  │
│  │  External Secrets Operator           │                  │
│  │  (Vault, AWS SM, GCP SM → K8s Secret)│                  │
│  └──────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

```yaml
# [실무] ExternalSecret 리소스 예시
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: catalog-service-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: catalog-service-secrets    # 생성될 K8s Secret 이름
  data:
    - secretKey: spring.datasource.password
      remoteRef:
        key: secret/data/catalog-service
        property: db-password
```

### 의사결정 플로우차트

```
시작: 설정 관리 솔루션 선택
│
├─ Q1. Kubernetes 환경인가?
│   │
│   ├─ YES ─── Q2. 외부 시크릿 저장소가 필요한가?
│   │           │
│   │           ├─ YES ─── External Secrets Operator + Vault
│   │           │
│   │           └─ NO ──── ConfigMap/Secret으로 충분
│   │                      (소규모, 시크릿 적음)
│   │
│   └─ NO ──── Q3. Spring 생태계 중심인가?
│               │
│               ├─ YES ─── Config Server + Vault (시크릿)
│               │
│               └─ NO ──── Q4. 서비스 디스커버리도 필요한가?
│                           │
│                           ├─ YES ─── Consul
│                           │
│                           └─ NO ──── Config Server
```

### Config Server 고가용성 구성

```java
// [실무노트] Config Server HA 구성 방법

// 방법 1: 다중 인스턴스 + 로드밸런서
// - Config Server를 2~3개 인스턴스로 배포
// - L4 로드밸런서 또는 K8s Service로 분산
// - Git clone 캐시로 Git 장애 시에도 일시적 서비스 가능

// 방법 2: Config Server + JDBC Backend
// - Git 대신 DB를 백엔드로 사용
// - DB HA(RDS Multi-AZ 등)에 의존
// - Git 이력 관리 장점을 잃음

// 방법 3: Config Client의 로컬 캐시 활용
// - spring.cloud.config.allow-override=true
// - 로컬 application.yml을 fallback으로 유지
```

```yaml
# [실무] Config Server HA - K8s Deployment 예시
apiVersion: apps/v1
kind: Deployment
metadata:
  name: config-service
spec:
  replicas: 2    # 최소 2개 인스턴스
  selector:
    matchLabels:
      app: config-service
  template:
    spec:
      containers:
        - name: config-service
          image: config-service:latest
          ports:
            - containerPort: 8888
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8888
            initialDelaySeconds: 5
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8888
            initialDelaySeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: config-service
spec:
  selector:
    app: config-service
  ports:
    - port: 8888
```

### 설정 암호화 전략 비교

| 전략 | 방식 | 장점 | 단점 | 적합한 경우 |
|------|------|------|------|-------------|
| Config Server 대칭키 | `{cipher}` prefix + AES | 간단한 설정 | 키 관리 부담 | 소규모, 빠른 도입 |
| Config Server 비대칭키 | RSA 키페어 | 공개키로 암호화 가능 | 키 로테이션 수동 | 중규모, 팀 분리 |
| Vault Transit Engine | Vault API로 암/복호화 | 키 자동 로테이션 | Vault 의존성 | 대규모, 보안 중시 |
| K8s Sealed Secrets | 클러스터 키로 암호화 | GitOps 친화적 | K8s 전용 | K8s + GitOps |
| SOPS + Age/KMS | 파일 단위 암호화 | 클라우드 KMS 통합 | 도구 추가 필요 | 멀티 클라우드 |

```yaml
# Config Server 대칭키 암호화 예시
# Config Server application.yml
encrypt:
  key: ${ENCRYPT_KEY}  # [실무] 환경 변수로 주입, 절대 하드코딩 금지

# Git Repo의 catalog-service.yml
spring:
  datasource:
    password: '{cipher}AQBHnN3aOHqS...'  # 암호화된 값
    # Config Server가 클라이언트에 전달 시 자동 복호화
```

## 정리

| 시나리오 | 권장 솔루션 |
|----------|-------------|
| Spring Boot 마이크로서비스, 빠른 도입 | Config Server |
| 시크릿 관리가 핵심 요구사항 | Vault (+ Config Server) |
| 서비스 디스커버리 + 설정 통합 | Consul |
| Kubernetes 네이티브 환경 | ConfigMap + External Secrets Operator |
| 레거시 Hadoop/Kafka 생태계 | ZooKeeper (기존 인프라 활용) |
| 대규모 + 높은 보안 요구 | Config Server + Vault + K8s ESO |

> 외부 설정 관리의 본질적 가치는 재배포 없이 런타임에 설정을 변경할 수 있다는 점이다.
> 일반 설정에는 Config Server의 Git 기반 이력 관리 + @RefreshScope가, 시크릿에는 Vault의 동적 자격증명이 각각 최적이다.
> 솔루션 선택 시 "얼마나 빠르게, 얼마나 안전하게 런타임 설정 변경을 반영할 수 있는가"를 핵심 기준으로 삼아야 한다.
