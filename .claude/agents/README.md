# agents/ 디렉토리

서브에이전트를 정의하는 디렉토리. 각 에이전트는 `.md` 파일 하나로 구성된다.

## 파일 구조

```yaml
---
name: 에이전트 이름
description: 언제 이 에이전트가 트리거되는지 설명 (구체적일수록 자동 호출 정확도 향상)
allowed-tools: Read, Grep, Glob  # 사용 가능한 도구 제한
model: sonnet  # 선택사항. 비용 절감 시 sonnet, 복잡한 작업은 생략(기본값 사용)
---

시스템 프롬프트를 여기에 작성한다.
에이전트의 역할, 관점, 출력 형식 등을 명시.
```

## 작성 가이드

- `description`이 트리거 조건이다. "코드 리뷰" 같은 키워드를 포함하면 관련 요청 시 자동 호출됨
- `allowed-tools`로 행동 범위를 제한하면 안전하다
  - 읽기 전용: `Read, Grep, Glob`
  - 코드 수정 가능: `Read, Grep, Glob, Edit, Write`
  - Git 연동: `Bash(git diff *), Bash(git log *)`
- 시스템 프롬프트에 이 프로젝트의 듀얼 관점(Spring + Cloud Native)을 명시하면 일관성 유지

## 이 프로젝트에서 고려할 만한 에이전트

| 파일명 | 역할 |
|--------|------|
| `code-reviewer.md` | Spring + Cloud Native 관점 PR 리뷰 |
| `security-checker.md` | 시크릿 하드코딩, 의존성 취약점 탐지 |
| `test-writer.md` | 변경 코드에 대한 테스트 자동 생성 |
| `doc-updater.md` | reference/ 문서 최신 상태 확인 및 업데이트 |

## 추가/수정/삭제

- 추가: 이 디렉토리에 `에이전트명.md` 파일 생성
- 수정: 해당 `.md` 파일의 frontmatter 또는 시스템 프롬프트 편집
- 삭제: 해당 `.md` 파일 제거 (다른 설정 변경 불필요)
