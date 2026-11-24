#!/usr/bin/env bash
# DB 상태 점검. 읽기만 한다. 사용법: ./db/report.sh
set -euo pipefail
cd "$(dirname "$0")"
CONTAINER="${BACKUP_CONTAINER:-byarrival-postgres}"
docker exec -i "$CONTAINER" sh -c 'psql -X -q -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < report.sql
