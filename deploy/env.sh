# .env 값 읽기(backup.sh, watchdog.sh가 불러 쓴다). 실행 권한은 필요 없다.
# .env에는 특수문자가 있을 수 있어 실행(source)하지 않고 값만 읽는다.
# Compose처럼 작은따옴표·큰따옴표로 감싼 값, 따옴표 없는 값과 뒤쪽 주석( #...)을 처리한다. 변수 치환과 이스케이프는 처리하지 않는다.
env_value() {
  local value
  value="$(sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" .env 2>/dev/null | tail -n 1 | tr -d '\r' || true)"
  case "$value" in
    \'*) value="${value#\'}"; value="${value%%\'*}" ;;
    \"*) value="${value#\"}"; value="${value%%\"*}" ;;
    \#*) value="" ;;
    *)
      value="${value%%[[:space:]]#*}"
      value="${value%"${value##*[![:space:]]}"}"
      ;;
  esac
  printf '%s' "$value"
}
