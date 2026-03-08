# hooks/ 디렉토리

Claude Code의 도구 사용 전후에 자동 실행되는 스크립트를 관리하는 디렉토리.

## 동작 원리

훅은 `settings.json`에 등록하여 활성화한다. 스크립트 단독으로는 동작하지 않음.

```json
// settings.json 내 hooks 설정 예시
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [{ "type": "command", "command": "bash .claude/hooks/스크립트.sh" }]
      }
    ]
  }
}
```

## 훅 이벤트 종류

| 이벤트 | 시점 | 용도 |
|--------|------|------|
| `PreToolUse` | 도구 실행 전 | 파일 보호, 위험 명령 차단 |
| `PostToolUse` | 도구 실행 후 | 자동 포맷팅, 린트 실행 |
| `Stop` | 응답 완료 시 | 결과 검증, 알림 |
| `SessionStart` | 세션 시작 시 | 환경 초기화 |
| `SessionEnd` | 세션 종료 시 | 정리 작업 |

## 스크립트 작성 규칙

- stdin으로 JSON 입력을 받는다 (`cat`으로 읽기)
- `jq`로 `tool_input.file_path` 등 필요한 값 추출
- 종료 코드:
  - `exit 0` — 허용 (계속 진행)
  - `exit 2` — 차단 (도구 실행 중단)
- stderr로 메시지 출력 시 사용자에게 표시됨
- 반드시 실행 권한 부여: `chmod +x 스크립트.sh`

## 현재 등록된 훅

| 파일 | 이벤트 | matcher | 역할 |
|------|--------|---------|------|
| `protect-files.sh` | PreToolUse | `Edit\|Write` | `.env`, `gradlew`, lock 파일 등 보호 대상 수정 차단 |

## 추가/수정/삭제

- 추가: ① 이 디렉토리에 `.sh` 파일 생성 → ② `chmod +x` → ③ `settings.json`의 `hooks`에 등록
- 수정: 스크립트 내용만 편집 (settings.json 변경 불필요)
- 삭제: ① `settings.json`에서 해당 훅 항목 제거 → ② `.sh` 파일 삭제
