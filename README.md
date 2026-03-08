# Cloud Native Spring in Action 학습 프로젝트

「Cloud Native Spring in Action」 책을 기반으로 실습하면서, 실무에서 바로 참고할 수 있도록 확장한 학습용 레포지토리.

책의 예제를 그대로 따라가는 것이 아니라, Spring Boot 4.0 / Spring Cloud 2025.x 최신 스택으로 마이그레이션하고, Kubernetes 배포·Claude Code 활용 등 실무적 요소를 더해 하나의 레퍼런스 프로젝트로 구성했다.

## 방향성

- 클라우드 네이티브 아키텍처의 핵심 패턴을 Spring 생태계로 직접 구현하고 검증한다
- 단순 실습을 넘어, 실제 실무에서 참고할 수 있는 수준의 코드와 문서를 축적한다
- 학습 과정에서 얻은 인사이트를 `reference/` 문서로 정리하여 지식 베이스를 구축한다
- 책의 진행에 따라 서비스와 인프라 구성이 지속적으로 확장될 예정이다

## 기술 스택

| 영역 | 기술 |
|------|------|
| Framework | Spring Boot 4.0, Spring Cloud 2025.x |
| Data | Spring Data JDBC, PostgreSQL, Flyway |
| Config | Spring Cloud Config (Git-backed) |
| Container | Docker, Docker Compose, Kubernetes |
| Test | JUnit 5, Testcontainers |
| AI Tooling | Claude Code (커스텀 스킬·훅·규칙) |

## 사전 요구사항

- Java 17+
- Docker / Docker Compose