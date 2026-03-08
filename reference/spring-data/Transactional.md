# @Transactional

## 개요

`@Transactional`은 메서드 또는 클래스에 선언적 트랜잭션 경계를 지정하는 애노테이션이다.
Spring은 이 애노테이션이 붙은 메서드를 AOP 프록시로 감싸서, 메서드 시작 시 트랜잭션을 열고 정상 완료 시 커밋, 예외 발생 시 롤백하는 흐름을 자동으로 처리한다.

🌱 Spring이 선언적 트랜잭션을 도입한 이유는 명확하다 — JDBC의 `connection.setAutoCommit(false)` / `commit()` / `rollback()`을 비즈니스 로직에서 분리하기 위함이다.

## 핵심 동작 원리

### 프록시 기반 트랜잭션 처리 흐름

```
호출자 → [프록시] → 실제 빈
           │
           ├─ (1) TransactionInterceptor 진입
           │       - PlatformTransactionManager.getTransaction() 호출
           │       - 전파 속성에 따라 기존 트랜잭션 참여 or 새 트랜잭션 시작
           │
           ├─ (2) 실제 메서드 실행
           │       - 이 동안 커넥션을 점유한다
           │
           ├─ (3-A) 정상 완료 → commit()
           │
           └─ (3-B) 예외 발생
                    ├─ RuntimeException / Error → rollback()
                    └─ Checked Exception → commit() (기본 동작!)
```

🌱 핵심 클래스:
- `TransactionInterceptor` — AOP 어드바이스. 트랜잭션 시작/커밋/롤백을 담당
- `PlatformTransactionManager` — 실제 트랜잭션 관리 추상화 (`DataSourceTransactionManager`, `JpaTransactionManager` 등)
- `TransactionSynchronizationManager` — ThreadLocal로 현재 트랜잭션 상태를 관리

## 주요 속성

### propagation (전파 속성)

| 전파 속성 | 동작 | 사용 시점 |
|----------|------|----------|
| `REQUIRED` (기본) | 기존 트랜잭션 있으면 참여, 없으면 새로 시작 | 대부분의 서비스 메서드 |
| `REQUIRES_NEW` | 항상 새 트랜잭션 시작 (기존 트랜잭션 일시 중단) | 감사 로그처럼 메인 트랜잭션과 독립적으로 커밋해야 할 때 |
| `SUPPORTS` | 기존 트랜잭션 있으면 참여, 없으면 트랜잭션 없이 실행 | 읽기 전용 조회에서 트랜잭션이 있으면 활용 |
| `NOT_SUPPORTED` | 트랜잭션 없이 실행 (기존 트랜잭션 일시 중단) | 트랜잭션이 불필요한 외부 API 호출 |
| `MANDATORY` | 기존 트랜잭션 필수, 없으면 예외 | 반드시 트랜잭션 안에서 호출되어야 하는 메서드 |
| `NEVER` | 트랜잭션이 있으면 예외 | 트랜잭션 컨텍스트에서 호출되면 안 되는 메서드 |
| `NESTED` | 기존 트랜잭션 안에서 세이브포인트 생성 | JDBC에서만 지원, JPA에서는 미지원 |

```java
// [실무노트] REQUIRES_NEW는 새 커넥션을 추가로 점유한다.
// 풀 사이즈가 작으면 데드락이 발생할 수 있으므로 주의해야 한다.
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveAuditLog(AuditEvent event) {
    auditRepository.save(event);
}
```

### readOnly

```java
// [실무노트] readOnly=true는 단순 힌트가 아니다.
// 1. Hibernate: 더티 체킹 스냅샷을 생성하지 않아 메모리 절약
// 2. JDBC 드라이버: 읽기 전용 커넥션으로 설정 → DB가 읽기 최적화 가능
// 3. Spring Data JDBC: FlushMode를 MANUAL로 설정
@Transactional(readOnly = true)
public Book getByIsbn(String isbn) {
    return bookRepository.findByIsbn(isbn);
}
```

### rollbackFor / noRollbackFor

```java
// 기본 롤백 정책: RuntimeException, Error → 롤백 / Checked Exception → 커밋
// Checked Exception에서도 롤백하려면 명시적으로 지정해야 한다.
@Transactional(rollbackFor = Exception.class)
public void processOrder(Order order) throws BusinessException {
    // BusinessException(checked)이 발생해도 롤백된다
}
```

### timeout

```java
// 트랜잭션 시작 후 지정 시간 내에 완료되지 않으면 롤백한다.
// 기본값은 -1 (타임아웃 없음)
@Transactional(timeout = 5)  // 5초
public void importBooks(List<Book> books) {
    bookRepository.saveAll(books);
}
```

## 자주 겪는 함정

### 1. 같은 클래스 내부 호출 — 프록시 우회

```java
@Service
public class BookService {

    public void processBooks() {
        saveBook(book);  // ❌ 프록시를 거치지 않아 @Transactional이 무시된다
    }

    @Transactional
    public void saveBook(Book book) {
        bookRepository.save(book);
    }
}
```

```
// [실무노트] 해결 방법:
// 1. 트랜잭션이 필요한 메서드를 별도 빈으로 분리 (가장 권장)
// 2. self-injection 패턴 사용
// 3. ApplicationContext에서 자기 자신을 조회 (비권장)
```

### 2. Checked Exception 시 의도치 않은 커밋

```java
@Transactional
public void transfer(Account from, Account to, BigDecimal amount)
        throws InsufficientFundsException {  // Checked Exception
    from.debit(amount);
    to.credit(amount);
    // InsufficientFundsException 발생 시 → 커밋됨 (!)
    // rollbackFor = InsufficientFundsException.class 필요
}
```

### 3. 트랜잭션 안에서 외부 호출

```java
// [실무노트] 트랜잭션 안에서 외부 API를 호출하면 커넥션 점유 시간이 길어진다.
// 외부 서비스가 느려지면 커넥션 풀이 고갈되어 전체 서비스가 마비될 수 있다.

// ❌ 안티패턴
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);
    paymentClient.charge(order);       // 외부 API — 커넥션을 물고 대기
    notificationClient.send(order);    // 외부 API — 커넥션을 물고 대기
}

// ✅ 트랜잭션 범위를 최소화
@Transactional
public void saveOrder(Order order) {
    orderRepository.save(order);       // DB 작업만 트랜잭션 안에서
}

public void createOrder(Order order) {
    saveOrder(order);                  // 트랜잭션 종료
    paymentClient.charge(order);       // 트랜잭션 밖에서
    notificationClient.send(order);    // 트랜잭션 밖에서
}
```

### 4. rollback-only 마킹 — try-catch로도 롤백을 막을 수 없다

```java
// [실무노트] 가장 혼란스러운 함정 중 하나다.
// 내부 빈에서 RuntimeException이 발생하면 Spring은 트랜잭션에 "rollback-only" 마크를 찍는다.
// 외부에서 try-catch로 예외를 삼켜도, 커밋 시점에 마크를 확인하고 롤백한다.
// → UnexpectedRollbackException 발생

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);

        try {
            inventoryService.decreaseStock(order.getItemId(), order.getQuantity());
        } catch (RuntimeException e) {
            // ❌ 예외를 잡았지만 소용없다!
            // inventoryService에서 이미 rollback-only가 마킹되었다.
            log.warn("재고 차감 실패, 무시하고 진행", e);
        }

        // 여기까지 정상 도달하지만...
        // 메서드 종료 시 commit() 호출 → rollback-only 감지 → 롤백!
        // → UnexpectedRollbackException 발생
    }
}

@Service
public class InventoryService {

    @Transactional  // 전파 속성 REQUIRED (기본) → 같은 트랜잭션에 참여
    public void decreaseStock(Long itemId, int quantity) {
        // ...
        throw new RuntimeException("재고 부족");
        // → Spring이 현재 트랜잭션에 rollback-only 마킹
    }
}
```

```
동작 흐름:

OrderService.createOrder()  ─── 트랜잭션 시작 ───────────────────────┐
  │                                                                   │
  ├─ orderRepository.save(order)  ✅                                  │
  │                                                                   │
  ├─ inventoryService.decreaseStock()                                 │
  │     └─ RuntimeException 발생                                      │
  │     └─ TransactionInterceptor가 rollback-only 마킹 ⚠️            │
  │                                                                   │
  ├─ catch로 예외 삼킴 (코드는 계속 진행)                              │
  │                                                                   │
  └─ 메서드 종료 → commit() 시도                                      │
        └─ rollback-only 감지 → 실제로는 rollback 수행               │
        └─ UnexpectedRollbackException 던짐                           │
  ────────────────────────────────────────────────────────────────────┘
```

```java
// [실무노트] 해결 방법:

// 방법 1: 내부 서비스에 REQUIRES_NEW를 사용하여 별도 트랜잭션으로 분리
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void decreaseStock(Long itemId, int quantity) {
    // 별도 트랜잭션이므로 여기서 롤백되어도 외부 트랜잭션에 영향 없음
}

// 방법 2: 예외를 던지기 전에 비즈니스 로직으로 판단
@Transactional
public boolean tryDecreaseStock(Long itemId, int quantity) {
    if (stock < quantity) return false;  // 예외 대신 반환값으로 실패 전달
    // ...
    return true;
}
```

### 5. @TransactionalEventListener에서 DML이 동작하지 않는다

```java
// [실무노트] @TransactionalEventListener는 기본 phase가 AFTER_COMMIT이다.
// 트랜잭션이 커밋된 "후"에 실행되지만, 기존 트랜잭션의 커넥션을 그대로 사용한다.
// 이미 커밋이 완료된 커넥션이므로 INSERT/UPDATE/DELETE가 조용히 무시된다.

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCreatedEvent(order.getId()));
        // → 트랜잭션 커밋 후 리스너 실행
    }
}

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationRepository notificationRepository;

    // ❌ AFTER_COMMIT(기본) 시점에서 DML이 동작하지 않는다
    @TransactionalEventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        // 이미 커밋된 커넥션을 재사용하므로 save()가 DB에 반영되지 않는다.
        // 예외도 발생하지 않아 디버깅이 매우 어렵다.
        notificationRepository.save(
            new Notification(event.getOrderId(), "주문 생성됨")
        );  // ❌ DB에 저장되지 않음!
    }
}
```

```
동작 흐름:

createOrder()  ─── 트랜잭션 시작 ──────────────────────────────┐
  │                                                             │
  ├─ orderRepository.save(order)  ✅                            │
  ├─ eventPublisher.publishEvent(...)                           │
  │                                                             │
  └─ 메서드 종료 → commit() 완료 ✅                             │
  ──────────────────────────────────────────────────────────────┘
       │
       ▼  (커밋 완료 후, 같은 커넥션에서 리스너 실행)
  onOrderCreated()
    └─ notificationRepository.save(...)
       └─ 이미 커밋된 커넥션 → DML 무시됨 ❌
```

```java
// [실무노트] 해결 방법:

// 방법 1: REQUIRES_NEW로 새 트랜잭션을 열어서 DML 실행
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        notificationService.saveNotification(event.getOrderId());
    }
}

@Service
public class NotificationService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveNotification(Long orderId) {
        notificationRepository.save(
            new Notification(orderId, "주문 생성됨")
        );  // ✅ 새 트랜잭션에서 정상 저장
    }
}

// 방법 2: @Async와 조합하여 별도 스레드에서 새 트랜잭션 실행
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderCreated(OrderCreatedEvent event) {
    // 별도 스레드 → 새 커넥션 → 새 트랜잭션
    notificationRepository.save(
        new Notification(event.getOrderId(), "주문 생성됨")
    );  // ✅ 정상 저장
}
```

### 6. private 메서드에 @Transactional

```java
// [실무노트] Spring AOP는 프록시 기반이므로 private 메서드에는 적용되지 않는다.
// 컴파일 에러도 나지 않고 조용히 무시되므로 발견이 어렵다.

@Transactional  // ❌ 무시됨
private void internalSave(Book book) {
    bookRepository.save(book);
}
```

## 실무 고려사항

### 🌱 Spring 관점

- **클래스 vs 메서드 레벨** — 클래스에 `@Transactional(readOnly = true)`를 걸고, 쓰기 메서드에만 `@Transactional`을 오버라이드하는 패턴이 일반적이다
- **Spring Data의 기본 트랜잭션** — `SimpleJpaRepository`와 `SimpleJdbcRepository`는 이미 `@Transactional`이 적용되어 있다. Repository 메서드를 직접 호출하면 별도 선언 없이도 트랜잭션이 동작한다
- **테스트에서의 @Transactional** — `@SpringBootTest`에 `@Transactional`을 붙이면 테스트 후 자동 롤백된다. 편리하지만, 실제 커밋 시 발생하는 제약조건 위반을 놓칠 수 있다

### ☁️ Cloud Native 관점

- **커넥션 점유 시간 = 확장성의 적** — 마이크로서비스에서 커넥션 풀은 제한적이다. 트랜잭션 범위를 최소화하여 커넥션 점유 시간을 줄이는 것이 수평 확장의 핵심이다
- **분산 트랜잭션 회피** — 마이크로서비스 간 트랜잭션은 `@Transactional`로 해결할 수 없다. Saga 패턴이나 이벤트 기반 최종 일관성(eventual consistency)을 사용한다
- **트랜잭션 타임아웃 설정** — 컨테이너 환경에서는 네트워크 지연이 발생할 수 있으므로, 무한 대기를 방지하기 위해 트랜잭션 타임아웃을 반드시 설정한다

## 정리

| 항목 | 핵심 |
|------|------|
| 기본 동작 | 프록시 기반 AOP, RuntimeException 시 롤백 |
| 전파 속성 | `REQUIRED`가 기본, `REQUIRES_NEW`는 새 커넥션 점유 주의 |
| readOnly | 더티 체킹 생략 + DB 읽기 최적화 힌트 |
| 롤백 정책 | Checked Exception은 기본적으로 커밋됨 — `rollbackFor` 필요 |
| rollback-only | 내부 빈에서 RuntimeException 발생 시 try-catch로도 롤백을 막을 수 없다 |
| 내부 호출 | 같은 클래스 내 호출은 프록시를 우회하여 트랜잭션 미적용 |
| @TransactionalEventListener | AFTER_COMMIT은 커밋된 커넥션을 재사용하므로 DML 무시됨 — REQUIRES_NEW 필요 |
| 트랜잭션 범위 | 외부 호출은 트랜잭션 밖으로 분리, 커넥션 점유 최소화 |

> `@Transactional`의 핵심은 "어디서 열고 어디서 닫는가"다.
> 트랜잭션 범위를 최소화하고, 프록시 동작 방식을 이해하면 대부분의 함정을 피할 수 있다.
