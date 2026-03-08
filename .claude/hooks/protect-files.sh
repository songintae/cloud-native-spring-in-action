#!/bin/bash
# 보호 파일 수정 차단 훅
# .env, lock 파일, gradlew 등 실수로 수정하면 안 되는 파일을 보호한다

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

PROTECTED_PATTERNS=(
  ".env"
  "gradlew"
  "gradlew.bat"
  "gradle/wrapper"
  ".gitignore"
  "gradle.lockfile"
  ".git/"
)

for pattern in "${PROTECTED_PATTERNS[@]}"; do
  if [[ "$FILE_PATH" == *"$pattern"* ]]; then
    echo "BLOCKED: $FILE_PATH 는 보호 대상 파일입니다." >&2
    exit 2
  fi
done

exit 0
