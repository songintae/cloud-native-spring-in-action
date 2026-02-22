# Spring DispatcherServlet

## 개요

DispatcherServlet은 Spring MVC의 핵심 컴포넌트로, **Front Controller 패턴**을 구현한다.
모든 HTTP 요청을 단일 진입점에서 받아 적절한 핸들러에게 위임하는 역할을 수행한다.

```
[Client] → [DispatcherServlet] → [Handler] → [View]
```

## 핵심 개념

### 1. Front Controller 패턴

- 모든 요청이 하나의 컨트롤러(DispatcherServlet)를 통해 진입
- 공통 관심사(인증, 로깅, 예외 처리 등)를 중앙에서 처리
- 요청별 처리 로직은 개별 핸들러에 위임

```java
// [실무노트] web.xml 없이 Spring Boot가 자동으로 DispatcherServlet을 등록
// ServletRegistrationBean을 통해 커스터마이징 가능
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 2. 요청 처리 흐름

```
1. 요청 수신
   ↓
2. HandlerMapping → 적절한 Handler(Controller) 찾기
   ↓
3. HandlerAdapter → Handler 실행
   ↓
4. Handler 실행 → ModelAndView 반환
   ↓
5. ViewResolver → View 이름으로 실제 View 찾기
   ↓
6. View 렌더링 → 응답 반환
```

## 주요 컴포넌트

### HandlerMapping

요청 URL을 처리할 핸들러(컨트롤러)를 찾는다.

| 구현체 | 설명 |
|--------|------|
| `RequestMappingHandlerMapping` | `@RequestMapping` 기반 매핑 (가장 많이 사용) |
| `BeanNameUrlHandlerMapping` | Bean 이름을 URL로 매핑 |
| `SimpleUrlHandlerMapping` | URL 패턴을 직접 매핑 |

```java
// @RequestMapping 기반 매핑 예시
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping("/{isbn}")  // GET /api/books/{isbn}
    public Book getBook(@PathVariable String isbn) {
        return bookService.findByIsbn(isbn);
    }
}
```

### HandlerAdapter

찾은 핸들러를 실제로 실행하는 어댑터.

```java
// [실무노트] 다양한 형태의 핸들러를 통일된 방식으로 실행
// - @Controller 메서드
// - HttpRequestHandler
// - Controller 인터페이스 구현체
```

| 구현체 | 설명 |
|--------|------|
| `RequestMappingHandlerAdapter` | `@RequestMapping` 메서드 실행 |
| `HttpRequestHandlerAdapter` | `HttpRequestHandler` 실행 |
| `SimpleControllerHandlerAdapter` | `Controller` 인터페이스 구현체 실행 |

### ViewResolver

논리적 뷰 이름을 실제 View 객체로 변환한다.

```java
// application.yml 설정 예시
spring:
  mvc:
    view:
      prefix: /WEB-INF/views/
      suffix: .jsp

// 컨트롤러에서 "home" 반환 시 → /WEB-INF/views/home.jsp 로 해석
```

```java
// [실무노트] REST API에서는 ViewResolver 대신 HttpMessageConverter 사용
@RestController  // @ResponseBody가 포함되어 있음
public class ApiController {

    @GetMapping("/api/data")
    public ResponseEntity<Data> getData() {
        // JSON/XML로 직접 직렬화 (ViewResolver 거치지 않음)
        return ResponseEntity.ok(data);
    }
}
```

## 예외 처리

### HandlerExceptionResolver

컨트롤러에서 발생한 예외를 처리한다.

```java
// 전역 예외 처리 (권장)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(BookNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(ex.getMessage()));
    }

    // [실무노트] 예외 타입별로 세분화하여 클라이언트에게 명확한 에러 응답 제공
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(message));
    }
}
```

## 인터셉터 (HandlerInterceptor)

요청 처리 전후에 공통 로직을 삽입한다.

```java
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) {
        log.info("Request: {} {}", request.getMethod(), request.getRequestURI());
        request.setAttribute("startTime", System.currentTimeMillis());
        return true;  // false 반환 시 요청 처리 중단
    }

    @Override
    public void postHandle(HttpServletRequest request,
                          HttpServletResponse response,
                          Object handler,
                          ModelAndView modelAndView) {
        // 컨트롤러 실행 후, 뷰 렌더링 전
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception ex) {
        long startTime = (Long) request.getAttribute("startTime");
        log.info("Response: {} ({}ms)", response.getStatus(),
                 System.currentTimeMillis() - startTime);
    }
}

// 인터셉터 등록
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health");
    }
}
```

## Filter vs Interceptor

| 구분 | Filter | Interceptor |
|------|--------|-------------|
| 스펙 | Servlet 스펙 | Spring MVC 스펙 |
| 실행 시점 | DispatcherServlet 전/후 | Handler 실행 전/후 |
| Spring Bean | 접근 어려움 (DelegatingFilterProxy 필요) | 자연스럽게 접근 |
| 용도 | 인코딩, 보안, 로깅 | 인증/인가, 로깅, 공통 처리 |

```
[Filter] → [DispatcherServlet] → [Interceptor] → [Controller]
```

```java
// [실무노트]
// - 보안(Spring Security)은 Filter 레벨에서 처리
// - 비즈니스 로직 관련 공통 처리는 Interceptor 사용
// - AOP는 서비스 레이어에서 사용
```

## Spring Boot에서의 자동 설정

Spring Boot는 `DispatcherServletAutoConfiguration`을 통해 자동 설정한다.

```java
// 주요 자동 설정 내용
// 1. DispatcherServlet 빈 등록
// 2. "/" 경로에 매핑
// 3. multipart 설정
// 4. 기본 ViewResolver 등록
```

```yaml
# application.yml에서 커스터마이징
spring:
  mvc:
    servlet:
      path: /api  # DispatcherServlet 경로 변경
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false  # 정적 리소스 매핑 비활성화
```

## 실무 팁

### 1. 다중 DispatcherServlet

```java
// [실무노트] 관리자/사용자 API 분리 시 유용
@Configuration
public class MultiDispatcherConfig {

    @Bean
    public ServletRegistrationBean<DispatcherServlet> adminServlet(
            ApplicationContext context) {
        DispatcherServlet servlet = new DispatcherServlet();
        // 별도의 WebApplicationContext 설정

        ServletRegistrationBean<DispatcherServlet> registration =
            new ServletRegistrationBean<>(servlet, "/admin/*");
        registration.setName("adminDispatcher");
        registration.setLoadOnStartup(2);
        return registration;
    }
}
```

### 2. 비동기 요청 처리

```java
@RestController
public class AsyncController {

    // [실무노트] 긴 작업은 비동기로 처리하여 서블릿 스레드 반환
    @GetMapping("/async")
    public Callable<String> asyncProcess() {
        return () -> {
            Thread.sleep(3000);  // 긴 작업
            return "completed";
        };
    }

    @GetMapping("/deferred")
    public DeferredResult<String> deferredProcess() {
        DeferredResult<String> result = new DeferredResult<>(5000L);

        // 다른 스레드에서 결과 설정
        asyncService.process(result);

        return result;
    }
}
```

## 정리

| 컴포넌트 | 역할 |
|----------|------|
| DispatcherServlet | Front Controller, 요청 분배 |
| HandlerMapping | URL → Handler 매핑 |
| HandlerAdapter | Handler 실행 |
| ViewResolver | 뷰 이름 → View 객체 변환 |
| HandlerExceptionResolver | 예외 처리 |
| HandlerInterceptor | 요청 전후 공통 처리 |

> DispatcherServlet을 이해하면 Spring MVC의 전체 흐름을 파악할 수 있고,
> 문제 발생 시 어느 단계에서 이슈가 있는지 빠르게 진단할 수 있다.
