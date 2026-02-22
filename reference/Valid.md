# @Valid 애노테이션과 Bean Validation

## 개요

`@Valid`는 JSR-380 (Bean Validation 2.0) 스펙의 일부로, 객체의 필드에 선언된 제약 조건을 검증한다.
Spring MVC에서는 컨트롤러 메서드 파라미터에 `@Valid`를 붙이면 자동으로 유효성 검사가 수행된다.

## 핵심 동작 원리

### 시퀀스 다이어그램

```
┌──────────┐    ┌─────────────────────┐    ┌─────────────────────────────┐    ┌────────────┐    ┌───────────┐
│  Client  │    │  DispatcherServlet  │    │ RequestMappingHandlerAdapter│    │  Validator │    │ Controller│
└────┬─────┘    └──────────┬──────────┘    └──────────────┬──────────────┘    └─────┬──────┘    └─────┬─────┘
     │                     │                              │                         │                 │
     │  POST /api/books    │                              │                         │                 │
     │  {title: "", ...}   │                              │                         │                 │
     │────────────────────>│                              │                         │                 │
     │                     │                              │                         │                 │
     │                     │  handle(request, response)   │                         │                 │
     │                     │─────────────────────────────>│                         │                 │
     │                     │                              │                         │                 │
     │                     │                              │  1. HTTP Body → Object  │                 │
     │                     │                              │     (Jackson 역직렬화)   │                 │
     │                     │                              │                         │                 │
     │                     │                              │  2. @Valid 감지         │                 │
     │                     │                              │─────────────────────────>│                 │
     │                     │                              │     validate(object)    │                 │
     │                     │                              │                         │                 │
     │                     │                              │  3. ConstraintViolation │                 │
     │                     │                              │<─────────────────────────│                 │
     │                     │                              │     (검증 결과 반환)     │                 │
     │                     │                              │                         │                 │
     │                     │                              │──┐                      │                 │
     │                     │                              │  │ 4. 검증 실패 시       │                 │
     │                     │                              │  │ MethodArgumentNot    │                 │
     │                     │                              │  │ ValidException 발생   │                 │
     │                     │                              │<─┘                      │                 │
     │                     │                              │                         │                 │
     │                     │                              │  5. 검증 성공 시         │                 │
     │                     │                              │────────────────────────────────────────────>│
     │                     │                              │     Controller 메서드 호출                  │
     │                     │                              │                         │                 │
└────┴─────────────────────┴──────────────────────────────┴─────────────────────────┴─────────────────┘
```

### 처리 흐름 요약

```
1. 요청 수신 (JSON Body)
   ↓
2. HttpMessageConverter (Jackson)가 JSON → 객체 변환
   ↓
3. @Valid 감지 → Validator.validate() 호출
   ↓
4. 제약 조건 위반 시 → MethodArgumentNotValidException 발생
   ↓
5. 검증 통과 시 → Controller 메서드 실행
```

## 기본 사용법

### 1. 의존성 추가

```groovy
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

### 2. DTO에 제약 조건 선언

```java
public class BookRequest {

    @NotBlank(message = "ISBN은 필수입니다")
    @Pattern(regexp = "^(97[89])-\\d{1,5}-\\d{1,7}-\\d{1,7}-\\d$",
             message = "올바른 ISBN-13 형식이 아닙니다")
    private String isbn;

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    private String title;

    @NotBlank(message = "저자는 필수입니다")
    private String author;

    @NotNull(message = "가격은 필수입니다")
    @Positive(message = "가격은 0보다 커야 합니다")
    private BigDecimal price;

    // getters, setters
}
```

### 3. 컨트롤러에서 @Valid 적용

```java
@RestController
@RequestMapping("/api/books")
public class BookController {

    @PostMapping
    public ResponseEntity<Book> createBook(@Valid @RequestBody BookRequest request) {
        // @Valid가 있으면 BookRequest의 제약 조건 검증
        // 검증 실패 시 이 메서드는 실행되지 않음
        Book book = bookService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(book);
    }
}
```

## 주요 제약 조건 애노테이션

| 애노테이션 | 설명 | 예시 |
|-----------|------|------|
| `@NotNull` | null 불가 | `@NotNull Integer count` |
| `@NotEmpty` | null, 빈 문자열/컬렉션 불가 | `@NotEmpty List<String> items` |
| `@NotBlank` | null, 빈 문자열, 공백만 있는 문자열 불가 | `@NotBlank String name` |
| `@Size` | 크기 제한 | `@Size(min=2, max=100)` |
| `@Min` / `@Max` | 최소/최대값 | `@Min(0) @Max(100)` |
| `@Positive` / `@Negative` | 양수/음수 | `@Positive BigDecimal price` |
| `@Email` | 이메일 형식 | `@Email String email` |
| `@Pattern` | 정규식 매칭 | `@Pattern(regexp="^[A-Z]+$")` |
| `@Past` / `@Future` | 과거/미래 날짜 | `@Past LocalDate birthDate` |

## @Valid vs @Validated

| 구분 | @Valid | @Validated |
|------|--------|------------|
| 스펙 | JSR-380 (표준) | Spring 전용 |
| 그룹 검증 | ❌ 지원 안함 | ✅ 지원 |
| 적용 위치 | 메서드 파라미터, 필드 | 클래스, 메서드 파라미터 |

### 그룹 검증 예시

```java
// 검증 그룹 정의
public interface OnCreate {}
public interface OnUpdate {}

public class BookRequest {

    @Null(groups = OnCreate.class, message = "생성 시 ID는 null이어야 합니다")
    @NotNull(groups = OnUpdate.class, message = "수정 시 ID는 필수입니다")
    private Long id;

    @NotBlank(groups = {OnCreate.class, OnUpdate.class})
    private String title;
}

@RestController
@Validated  // 클래스 레벨에 필요
public class BookController {

    @PostMapping
    public Book create(@Validated(OnCreate.class) @RequestBody BookRequest request) {
        // OnCreate 그룹만 검증
    }

    @PutMapping("/{id}")
    public Book update(@Validated(OnUpdate.class) @RequestBody BookRequest request) {
        // OnUpdate 그룹만 검증
    }
}
```

## 중첩 객체 검증

```java
public class OrderRequest {

    @NotBlank
    private String orderId;

    @Valid  // 중첩 객체도 검증하려면 @Valid 필수
    @NotNull
    private CustomerInfo customer;

    @Valid
    @NotEmpty
    private List<OrderItem> items;  // 컬렉션 내 각 요소도 검증
}

public class CustomerInfo {
    @NotBlank
    private String name;

    @Email
    private String email;
}

public class OrderItem {
    @NotBlank
    private String productId;

    @Positive
    private int quantity;
}
```

## 예외 처리

### MethodArgumentNotValidException 처리

```java
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();

        List<ValidationErrorResponse.Error> errors = fieldErrors.stream()
            .map(error -> new ValidationErrorResponse.Error(
                error.getField(),
                error.getRejectedValue(),
                error.getDefaultMessage()
            ))
            .toList();

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ValidationErrorResponse("Validation failed", errors));
    }
}

// 응답 DTO
public record ValidationErrorResponse(
    String message,
    List<Error> errors
) {
    public record Error(
        String field,
        Object rejectedValue,
        String message
    ) {}
}
```

### 응답 예시

```json
{
  "message": "Validation failed",
  "errors": [
    {
      "field": "title",
      "rejectedValue": "",
      "message": "제목은 필수입니다"
    },
    {
      "field": "price",
      "rejectedValue": -100,
      "message": "가격은 0보다 커야 합니다"
    }
  ]
}
```

## 커스텀 Validator 작성

### 1. 커스텀 애노테이션 정의

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsbnValidator.class)
@Documented
public @interface ValidIsbn {
    String message() default "올바른 ISBN 형식이 아닙니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

### 2. Validator 구현

```java
public class IsbnValidator implements ConstraintValidator<ValidIsbn, String> {

    private static final Pattern ISBN_PATTERN =
        Pattern.compile("^(97[89])-\\d{1,5}-\\d{1,7}-\\d{1,7}-\\d$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;  // null 체크는 @NotNull에 위임
        }
        return ISBN_PATTERN.matcher(value).matches() && isValidChecksum(value);
    }

    private boolean isValidChecksum(String isbn) {
        // ISBN-13 체크섬 검증 로직
        String digits = isbn.replace("-", "");
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(digits.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int checkDigit = (10 - (sum % 10)) % 10;
        return checkDigit == Character.getNumericValue(digits.charAt(12));
    }
}
```

### 3. 사용

```java
public class BookRequest {

    @ValidIsbn
    @NotBlank
    private String isbn;
}
```

## 서비스 레이어 검증

```java
// [실무노트] 컨트롤러뿐 아니라 서비스 레이어에서도 검증 가능
@Service
@Validated  // 클래스 레벨에 필수
public class BookService {

    public Book create(@Valid BookRequest request) {
        // 메서드 호출 시 검증 수행
        // 위반 시 ConstraintViolationException 발생
    }

    public Book findByIsbn(@NotBlank String isbn) {
        // 단일 파라미터도 검증 가능
    }
}
```

```java
// ConstraintViolationException 처리
@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<ErrorResponse> handleConstraintViolation(
        ConstraintViolationException ex) {

    String message = ex.getConstraintViolations().stream()
        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
        .collect(Collectors.joining(", "));

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(message));
}
```

## 내부 동작 상세

### ArgumentResolver에서의 검증

```java
// Spring 내부: RequestResponseBodyMethodProcessor
public class RequestResponseBodyMethodProcessor {

    @Override
    public Object resolveArgument(...) {
        // 1. MessageConverter로 역직렬화
        Object arg = readWithMessageConverters(webRequest, parameter, paramType);

        // 2. @Valid 또는 @Validated 확인
        if (parameter.hasParameterAnnotation(Valid.class) ||
            parameter.hasParameterAnnotation(Validated.class)) {

            // 3. 검증 수행
            WebDataBinder binder = binderFactory.createBinder(webRequest, arg, name);
            binder.validate();  // Validator.validate() 호출

            // 4. 에러 확인
            BindingResult bindingResult = binder.getBindingResult();
            if (bindingResult.hasErrors()) {
                throw new MethodArgumentNotValidException(parameter, bindingResult);
            }
        }

        return arg;
    }
}
```

## 실무 팁

### 1. Fail-Fast vs 전체 검증

```java
// 기본: 모든 필드 검증 후 에러 모아서 반환 (권장)

// Fail-Fast 설정 (첫 에러에서 중단)
@Configuration
public class ValidationConfig {

    @Bean
    public Validator validator() {
        return Validation.byProvider(HibernateValidator.class)
            .configure()
            .failFast(true)
            .buildValidatorFactory()
            .getValidator();
    }
}
```

### 2. 메시지 국제화

```properties
# messages.properties
NotBlank.bookRequest.title=제목을 입력해주세요
Size.bookRequest.title=제목은 {min}자 이상 {max}자 이하여야 합니다
```

```java
public class BookRequest {
    @NotBlank  // 메시지 키 자동 매핑: NotBlank.bookRequest.title
    @Size(min = 1, max = 200)
    private String title;
}
```

### 3. 프로그래밍 방식 검증

```java
@Service
public class BookService {

    private final Validator validator;

    public BookService(Validator validator) {
        this.validator = validator;
    }

    public void processBook(BookRequest request) {
        Set<ConstraintViolation<BookRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()) {
            // 커스텀 처리
            violations.forEach(v ->
                log.warn("Validation error: {} - {}", v.getPropertyPath(), v.getMessage())
            );
            throw new ValidationException("Invalid book request");
        }
    }
}
```

## 정리

| 단계 | 컴포넌트 | 역할 |
|------|----------|------|
| 1 | HttpMessageConverter | JSON → 객체 변환 |
| 2 | RequestResponseBodyMethodProcessor | @Valid 감지 |
| 3 | Validator (Hibernate Validator) | 제약 조건 검증 |
| 4 | BindingResult | 검증 결과 저장 |
| 5 | ExceptionHandler | 예외 → 응답 변환 |

> @Valid는 선언적으로 입력값 검증을 처리하여 컨트롤러 코드를 깔끔하게 유지한다.
> 커스텀 Validator를 활용하면 복잡한 비즈니스 규칙도 재사용 가능한 형태로 구현할 수 있다.
