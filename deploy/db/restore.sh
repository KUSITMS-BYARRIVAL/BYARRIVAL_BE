#!/usr/bin/env bash
# 백업을 새 DB에 복구한다. 운영 DB는 건드리지 않는다.
# 사용법: ./db/restore.sh <백업 파일> [새 DB 이름]   기본 이름은 byarrival_restore
# 대상 DB가 이미 있으면 멈춘다. 운영 DB로 바꾸는 순서는 README의 "복구"를 따른다.
set -euo pipefail

file="${1:?백업 파일을 인자로 넘겨야 합니다}"
target="${2:-byarrival_restore}"
CONTAINER="${BACKUP_CONTAINER:-byarrival-postgres}"

[[ -f "$file" ]] || { echo "파일이 없습니다: $file" >&2; exit 1; }
[[ "$target" =~ ^[a-z][a-z0-9_]*$ ]] || { echo "DB 이름은 영문 소문자·숫자·_만 씁니다: $target" >&2; exit 1; }

if [[ "$(docker exec "$CONTAINER" sh -c 'printf %s "$POSTGRES_DB"')" == "$target" ]]; then
  echo "운영 DB에는 바로 복구할 수 없습니다: $target" >&2
  exit 1
fi

# 이미 있는 DB면 createdb가 실패해 여기서 멈춘다
docker exec "$CONTAINER" sh -c 'createdb -U "$POSTGRES_USER" "$1"' _ "$target"
docker exec -i "$CONTAINER" sh -c 'pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --exit-on-error' _ "$target" < "$file"

echo "복구 완료: $target"
docker exec "$CONTAINER" sh -c 'psql -U "$POSTGRES_USER" -d "$1" -c "
  SELECT (SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL) AS flyway,
         (SELECT count(*) FROM users) AS users,
         (SELECT count(*) FROM article) AS articles,
         (SELECT count(*) FROM route) AS routes"' _ "$target"
