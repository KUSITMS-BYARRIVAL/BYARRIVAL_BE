#!/usr/bin/env bash
# .java 파일을 고친 뒤 컴파일을 확인한다. 실패하면 exit 2로 Claude에게 에러를 돌려준다.

file_path=$(jq -r '.tool_input.file_path // empty')

case "$file_path" in
  *.java) ;;
  *) exit 0 ;;
esac

cd "$CLAUDE_PROJECT_DIR" || exit 0

if [[ "$file_path" == */src/test/* ]]; then
  task="compileTestJava"
else
  task="compileJava"
fi

if ! output=$(./gradlew "$task" -q 2>&1); then
  echo "컴파일 실패 ($task):" >&2
  echo "$output" | grep -E 'error:|\.java:[0-9]+' | head -30 >&2
  exit 2
fi
