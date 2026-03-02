# HATEOAS (Hypermedia As The Engine Of Application State)

## 핵심 개념

REST API의 성숙도 모델(Richardson Maturity Model) 최상위 레벨(Level 3)에 해당하는 개념.
클라이언트가 서버 응답에 포함된 하이퍼미디어 링크를 통해 다음 가능한 액션을 동적으로 발견할 수 있게 한다.

### 왜 필요한가?

| 기존 REST API | HATEOAS 적용 |
|--------------|-------------|
| 클라이언트가 URL 구조를 하드코딩 | 서버가 링크를 제공, 클라이언트는 링크만 따라감 |
| API 변경 시 클라이언트 수정 필요 | API 진화에 유연하게 대응 가능 |
| 문서 의존도 높음 | 자기 서술적(self-descriptive) API |

### 핵심 원칙

1. **자기 서술적 메시지**: 응답 자체에 다음 행동 정보 포함
2. **동적 탐색**: 클라이언트는 진입점(entry point)만 알면 됨
3. **느슨한 결합**: 서버 URL 변경이 클라이언트에 영향 최소화

---

## 응답 예시

### 일반 REST 응답
```json
{
  "isbn": "978-1234567890",
  "title": "클라우드 네이티브 스프링",
  "author": "토마스 비탈레",
  "price": 35000
}
```

### HATEOAS 적용 응답
```json
{
  "isbn": "978-1234567890",
  "title": "클라우드 네이티브 스프링",
  "author": "토마스 비탈레",
  "price": 35000,
  "_links": {
    "self": {
      "href": "http://localhost:8080/books/978-1234567890"
    },
    "update": {
      "href": "http://localhost:8080/books/978-1234567890",
      "method": "PUT"
    },
    "delete": {
      "href": "http://localhost:8080/books/978-1234567890",
      "method": "DELETE"
    },
    "all-books": {
      "href": "http://localhost:8080/books"
    }
  }
}
```

---

## Spring HATEOAS 구현

### 의존성 추가
```groovy
implementation 'org.springframework.boot:spring-boot-starter-hateoas'
```

### 기본 구현 예시

```java
// [책참조] HATEOAS 적용 — 도메인 모델을 RepresentationModel로 래핑

@RestController
@RequestMapping("/books")
public class BookController {

    @GetMapping("/{isbn}")
    public EntityModel<Book> getBook(@PathVariable String isbn) {
        Book book = bookService.findByIsbn(isbn);

        return EntityModel.of(book,
            // self 링크
            linkTo(methodOn(BookController.class).getBook(isbn)).withSelfRel(),
            // 전체 목록 링크
            linkTo(methodOn(BookController.class).getAllBooks()).withRel("all-books"),
            // 삭제 링크
            linkTo(methodOn(BookController.class).deleteBook(isbn)).withRel("delete")
        );
    }

    @GetMapping
    public CollectionModel<EntityModel<Book>> getAllBooks() {
        List<EntityModel<Book>> books = bookService.findAll().stream()
            .map(book -> EntityModel.of(book,
                linkTo(methodOn(BookController.class).getBook(book.isbn())).withSelfRel()))
            .toList();

        return CollectionModel.of(books,
            linkTo(methodOn(BookController.class).getAllBooks()).withSelfRel());
    }
}
```

### 주요 클래스

| 클래스 | 용도 |
|-------|-----|
| `EntityModel<T>` | 단일 리소스 + 링크 래핑 |
| `CollectionModel<T>` | 컬렉션 리소스 + 링크 래핑 |
| `RepresentationModel` | 링크만 필요한 경우 상속용 |
| `WebMvcLinkBuilder` | `linkTo()`, `methodOn()`으로 타입 세이프 링크 생성 |

---

## 실무 고려사항

### 장점
- API 진화에 유연 (버전 관리 부담 감소)
- 클라이언트-서버 결합도 낮춤
- API 탐색 가능 (discoverability)

### 단점 / 주의점
- 응답 크기 증가 (대량 데이터 시 오버헤드)
- 클라이언트 구현 복잡도 증가 (링크 파싱 로직 필요)
- 실제로 Level 3까지 구현하는 경우 드묾

### [실무노트] 현실적인 적용
```
대부분의 프로젝트에서는 완전한 HATEOAS보다는:
1. 페이지네이션 링크 (next, prev, first, last)
2. 관련 리소스 링크 (연관 엔티티 조회)
정도만 선택적으로 적용하는 경우가 많다.

완전한 HATEOAS는 공개 API나 장기 운영 API에서 가치가 높고,
내부 마이크로서비스 간 통신에서는 오버엔지니어링일 수 있다.
```

---

## 참고 자료
- [Spring HATEOAS 공식 문서](https://docs.spring.io/spring-hateoas/docs/current/reference/html/)
- [Richardson Maturity Model](https://martinfowler.com/articles/richardsonMaturityModel.html)
- HAL (Hypertext Application Language) 스펙
