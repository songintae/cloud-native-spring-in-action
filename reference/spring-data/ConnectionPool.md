# 커넥션 풀 (Connection Pool)

## 개요

커넥션 풀은 데이터베이스 커넥션을 미리 생성해두고 재사용하는 메커니즘이다.
JDBC 커넥션 생성은 TCP 핸드셰이크 + 인증 + SSL 협상을 포함하는 비용이 큰 작업이므로,
매 요청마다 새로 만들면 응답 시간이 수십~수백 ms 증가한다.

🌱 Spring Boot는 클래스패스에 있는 커넥션 풀 라이브러리를 자동 감지하여 `DataSource` 빈을 구성한다.
별도 설정 없이도 `spring-boot-starter-data-jdbc`나 `spring-boot-starter-data-jpa`를 추가하면
HikariCP가 기본 커넥션 풀로 동작한다.

## 핵심 동작 원리

### 커넥션 풀 라이프사이클

```
애플리케이션 기동
  │
  ├─ (1) 풀 초기화
  │       - minimum-idle 수만큼 커넥션을 미리 생성
  │       - 각 커넥션: TCP connect → DB 인증 → SSL 협상
  │
  ├─ (2) 커넥션 대여 (borrow)
  │       ┌─────────────────────────────────────────────┐
  │       │  Thread → pool.getConnection()               │
  │       │    ├─ 유휴 커넥션 있음 → 즉시 반환 (~0.01ms) │
  │       │    ├─ 유휴 없음 + 풀 여유 → 새로 생성 후 반환 │
  │       │    └─ 풀 꽉 참 → connection-timeout까지 대기  │
  │       │         → 타임아웃 초과 시 SQLException       │
  │       └─────────────────────────────────────────────┘
  │
  ├─ (3) 커넥션 사용
  │       - SQL 실행, 트랜잭션 처리
  │
  ├─ (4) 커넥션 반납 (return)
  │       - connection.close() → 실제 종료가 아니라 풀에 반환
  │       - 풀이 커넥션 상태를 검증 (isValid)
  │
  ├─ (5) 유지보수 (백그라운드)
  │       - idle-timeout 초과 유휴 커넥션 제거
  │       - max-lifetime 초과 커넥션 교체 (graceful eviction)
  │       - leak-detection: 대여 후 미반환 커넥션 경고
  │
  └─ (6) 풀 종료 (애플리케이션 셧다운)
          - 모든 커넥션 close
          - 그레이스풀 셧다운 시 진행 중 쿼리 완료 대기
```

### 풀 없이 vs 풀 사용 비교

```
[풀 없이] 요청당 커넥션 생성
  Thread → TCP connect (1~5ms) → Auth (1~3ms) → SQL → close
  Thread → TCP connect (1~5ms) → Auth (1~3ms) → SQL → close
  → 매번 2~8ms 오버헤드

[풀 사용] 미리 생성된 커넥션 재사용
  Thread → pool.getConnection() (~0.01ms) → SQL → return to pool
  Thread → pool.getConnection() (~0.01ms) → SQL → return to pool
  → 오버헤드 거의 없음
```

## 기본 사용법

### Spring Boot에서 사용 가능한 커넥션 풀

🌱 Spring Boot는 아래 순서로 클래스패스를 탐색하여 자동 선택한다 (`DataSourceAutoConfiguration`):

| 우선순위 | 라이브러리 | 특징 |
|---------|-----------|------|
| 1 | **HikariCP** | Spring Boot 기본. 가장 빠르고 가벼움. 바이트코드 최적화 |
| 2 | Tomcat JDBC Pool | Tomcat 내장 풀. 레거시 프로젝트에서 사용 |
| 3 | Apache DBCP2 | Commons DBCP 후속. 기능은 많지만 성능이 Hikari보다 낮음 |
| 4 | Oracle UCP | Oracle DB 전용 최적화 |

```java
// [실무노트] Spring Boot 2.0부터 HikariCP가 기본 커넥션 풀이다.
// spring-boot-starter-data-jdbc 또는 spring-boot-starter-data-jpa에 포함되어 있어
// 별도 의존성 추가 없이 바로 사용 가능하다.
// 다른 풀을 쓰고 싶으면 spring.datasource.type으로 명시적 지정이 필요하다.
```

### Polar Bookshop catalog-service 설정

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/polardb_catalog
    username: user
    password: password
    hikari:
      # [실무] 풀 사이즈 공식: (core_count * 2) + effective_spindle_count
      # 컨테이너 CPU 1~2코어 기준 5가 적정
      maximum-pool-size: 5

      # [실무] maximum-pool-size와 동일하게 설정하여 고정 풀로 운영 (HikariCP 공식 권장)
      # 커넥션 생성/제거 오버헤드를 제거한다
      minimum-idle: 5

      # [실무] 풀에서 커넥션을 못 얻으면 이 시간 후 SQLException 발생
      # 너무 길면 스레드가 블로킹, 너무 짧으면 순간 부하에 취약
      connection-timeout: 2000

      # [실무] DB/LB가 idle 커넥션을 끊기 전에 풀이 먼저 갱신
      # PostgreSQL의 idle_in_transaction_session_timeout보다 짧게 설정
      max-lifetime: 1800000  # 30분

      # [실무] 커넥션 미반환 시 경고 로그로 누수 조기 발견
      leak-detection-threshold: 60000  # 1분
```

## 심화 내용

### HikariCP가 빠른 이유

🌱 HikariCP는 다른 풀 대비 압도적인 성능을 보이는데, 핵심 최적화 기법은 다음과 같다:

1. **ConcurrentBag** — `java.util.concurrent`의 `CopyOnWriteArrayList` 대신 자체 구현한 lock-free 자료구조로 커넥션을 관리한다. Thread-local 캐싱으로 같은 스레드가 이전에 사용한 커넥션을 우선 반환한다.

2. **바이트코드 최적화** — `Connection`, `Statement`, `ResultSet` 프록시를 Javassist로 생성하여 JIT 컴파일러가 인라이닝하기 쉬운 구조를 만든다.

3. **최소한의 코드** — 전체 코드가 ~130KB. 코드가 적으면 CPU 캐시 적중률이 높아진다.

### 풀 사이즈 산정 공식

```
최적 커넥션 수 = (CPU 코어 수 × 2) + effective_spindle_count

예시:
  - 컨테이너 CPU limit 2코어, SSD(spindle 0) → (2 × 2) + 0 = 4
  - 컨테이너 CPU limit 1코어, SSD → (1 × 2) + 0 = 2~3
  - 여유를 두고 3~5 정도가 컨테이너 환경의 일반적 권장값
```

```java
// [실무노트] 풀 사이즈를 크게 잡는다고 성능이 좋아지지 않는다.
// 커넥션이 많아지면 DB 서버의 컨텍스트 스위칭 비용이 증가하고,
// 오히려 전체 처리량(throughput)이 떨어진다.
// PostgreSQL 공식 문서도 "fewer connections, better performance"를 강조한다.
```

### 주요 설정 항목 상세

| 설정 | 기본값 | 설명 |
|------|--------|------|
| `maximum-pool-size` | 10 | 풀이 유지하는 최대 커넥션 수 (활성 + 유휴) |
| `minimum-idle` | = maximum-pool-size | 유휴 커넥션 최소 유지 수. 기본값이 max와 같아 고정 풀 |
| `connection-timeout` | 30000 (30초) | 풀에서 커넥션 획득 대기 시간. 초과 시 `SQLException` |
| `idle-timeout` | 600000 (10분) | 유휴 커넥션 제거 시간. `minimum-idle < maximum-pool-size`일 때만 동작 |
| `max-lifetime` | 1800000 (30분) | 커넥션 최대 수명. DB의 `wait_timeout`보다 짧게 설정 |
| `leak-detection-threshold` | 0 (비활성) | 커넥션 대여 후 미반환 감지 시간. 0이면 비활성 |
| `validation-timeout` | 5000 (5초) | 커넥션 유효성 검증 타임아웃 |
| `connection-test-query` | 없음 | JDBC4 미지원 드라이버용. PostgreSQL은 설정하지 않는 것이 권장 |

### max-lifetime 동작 방식

`max-lifetime`은 커넥션이 풀에서 살아있을 수 있는 최대 시간이다.
이 값은 반드시 DB의 `wait_timeout`보다 짧아야 한다. HikariCP 공식 권장은 DB의 `wait_timeout`보다 **최소 5초 짧게** 설정하는 것이다. 네트워크 지연이나 GC pause로 인해 정확히 같은 시점에 만료되면 race condition이 발생할 수 있기 때문이다.

```
커넥션 A 생성 시각: 10:00:00
max-lifetime: 30분

  10:00:00  커넥션 A 생성
  10:25:00  커넥션 A 사용 중 (쿼리 실행)
  10:30:00  max-lifetime 도달
     │
     ├─ 커넥션이 유휴 상태 → 즉시 폐기, 새 커넥션 생성
     └─ 커넥션이 사용 중 → 반납 시점에 폐기, 새 커넥션 생성
                          (진행 중 쿼리를 강제 종료하지 않음)
```

```java
// [실무노트] HikariCP는 max-lifetime에 ±2.5%의 랜덤 지터를 추가한다.
// 풀의 모든 커넥션이 동시에 만료되어 한꺼번에 재생성되는 "thundering herd"를 방지하기 위함이다.
// 예: max-lifetime=30분이면 실제 만료는 29분 15초 ~ 30분 45초 사이에 분산된다.
```

### max-lifetime 환경별 권장값

| 환경 | max-lifetime | 이유 |
|------|-------------|------|
| 로컬/학습 | 1800000 (30분) | 기본값으로 충분. 커넥션 재생성 빈도 최소화 |
| AWS Aurora 프로덕션 | 600000~900000 (10~15분) | failover 시 stale 커넥션이 빠르게 교체됨 |
| failover 민감한 서비스 | 120000~300000 (2~5분) | 빠른 복구 우선. 재연결 비용 감수 |

```
[Aurora Failover 시나리오]

max-lifetime: 1800000 (30분)
  → failover 발생 후 최대 30분간 stale 커넥션이 남아있을 수 있음
  → 해당 커넥션으로 쿼리 시 PSQLException 발생

max-lifetime: 600000 (10분)
  → failover 후 최대 10분이면 모든 커넥션이 새 primary로 교체
  → 재연결 비용과 복구 속도의 균형점

max-lifetime: 120000 (2분)
  → 거의 즉시 복구되지만, 시간당 커넥션 재생성 횟수가 크게 증가
```

```java
// [실무노트] AWS Aurora를 사용한다면 max-lifetime을 짧게 가져가는 것이 유리하다.
// Aurora failover 시 기존 커넥션이 모두 무효화되는데,
// max-lifetime이 짧을수록 풀이 커넥션을 자주 교체하므로
// failover 후 새 primary로 연결되는 커넥션이 빨리 확보된다.
// RDS wait_timeout(기본 28800초=8시간)보다 5초 이상 짧게 설정하되,
// failover 복구 속도를 고려하여 10~15분을 권장한다.
```

## 실무 고려사항

### 🌱 Spring 관점

- **connection-test-query를 설정하지 마라** — PostgreSQL, MySQL 8+, H2 등 JDBC4 지원 드라이버는 `Connection.isValid()`를 사용한다. `SELECT 1` 같은 테스트 쿼리는 불필요한 네트워크 왕복을 추가할 뿐이다.

- **@Transactional과 커넥션 점유 시간** — `@Transactional` 메서드가 실행되는 동안 커넥션을 점유한다. 트랜잭션 안에서 외부 API 호출이나 파일 I/O를 하면 커넥션 점유 시간이 길어져 풀이 고갈될 수 있다.

```java
// [실무노트] 트랜잭션 안에서 외부 API를 호출하면 안 된다.
// 외부 서비스가 느려지면 커넥션을 물고 있는 시간이 길어져
// 다른 요청이 커넥션을 못 얻고 connection-timeout으로 실패한다.
@Transactional
public void processOrder(Order order) {
    orderRepository.save(order);           // ← DB 작업만 트랜잭션 안에서
}

public void handleOrder(Order order) {
    processOrder(order);                   // ← 트랜잭션 종료 후
    notificationService.sendEmail(order);  // ← 외부 호출은 트랜잭션 밖에서
}
```

- **Spring Boot Actuator로 풀 모니터링** — `hikaricp.connections.active`, `hikaricp.connections.idle`, `hikaricp.connections.pending` 메트릭을 Prometheus/Grafana로 수집하면 풀 상태를 실시간으로 파악할 수 있다.

### ☁️ Cloud Native 관점

- **총 커넥션 수 계산** — `pool_size × replica_count`가 DB의 `max_connections`를 초과하면 안 된다. PostgreSQL 기본 `max_connections=100`이고, 서비스가 여러 개면 각 서비스의 풀 사이즈 합계를 관리해야 한다.

```
catalog-service:  pool=5, replicas=3 → 15 커넥션
order-service:    pool=5, replicas=3 → 15 커넥션
edge-service:     pool=3, replicas=2 →  6 커넥션
                                     ─────────
                              합계:    36 / 100 (여유 있음)
```

- **K8s 환경에서 max-lifetime이 중요한 이유** — 클라우드 로드밸런서(AWS NLB, GCP ILB)나 PgBouncer 같은 커넥션 프록시가 idle 커넥션을 먼저 끊을 수 있다. 풀이 이미 끊어진 커넥션을 사용하면 `PSQLException: This connection has been closed`가 발생한다. `max-lifetime`을 프록시의 idle timeout보다 짧게 설정하면 이 문제를 예방한다.

- **그레이스풀 셧다운과 커넥션 풀** — K8s가 Pod에 SIGTERM을 보내면 Spring Boot는 그레이스풀 셧다운을 시작한다. 이때 HikariCP는 진행 중인 쿼리가 완료될 때까지 기다린 뒤 커넥션을 정리한다. `spring.lifecycle.timeout-per-shutdown-phase`를 설정하여 최대 대기 시간을 제한할 수 있다.

- **설정 외부화** — 풀 사이즈, DB URL, 크레덴셜은 환경마다 다르므로 `application.yml`에 기본값을 두고, K8s에서는 ConfigMap/Secret + 환경변수로 오버라이드하는 것이 12-Factor Config 원칙에 부합한다.

```yaml
# [실무] K8s 환경에서 환경변수로 오버라이드하는 패턴
# SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=10 으로 주입 가능
# Relaxed Binding 덕분에 kebab-case ↔ UPPER_UNDERSCORE 자동 매핑
```

## 정리

| 설정 | 권장값 (컨테이너) | 핵심 이유 |
|------|-----------------|----------|
| `maximum-pool-size` | 3~5 | CPU 코어 기반 산정. 크게 잡으면 DB 부하 증가 |
| `minimum-idle` | = maximum-pool-size | 고정 풀로 생성/제거 오버헤드 제거 |
| `connection-timeout` | 2000ms | 빠른 실패로 스레드 블로킹 방지 |
| `max-lifetime` | 1800000ms (30분) | DB/프록시 idle timeout보다 짧게 |
| `leak-detection-threshold` | 60000ms (1분) | 커넥션 누수 조기 발견 |
| `connection-test-query` | 설정하지 않음 | JDBC4 isValid()가 더 효율적 |

커넥션 풀은 "적게, 고정으로, 빨리 반환"이 핵심이다. 풀 사이즈를 키우는 것보다 커넥션 점유 시간을 줄이는 것이 성능에 훨씬 효과적이다.