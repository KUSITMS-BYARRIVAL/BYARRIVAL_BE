#!/usr/bin/env bash
# 로컬 부하 테스트. 사용법: ./perf/run.sh [smoke|load|stress]   (기본 load)
# 전용 컨테이너를 새로 띄워 가상 데이터를 넣고 k6를 돌린 뒤 모두 지운다. 결과는 perf/results/<이름>.md
# 환경 변수: USERS(회원 수, 기본 5000), RATE(load의 초당 조회 반복 수), P95_MS(기준, 기본 500), KEEP=1(끝나고 지우지 않음)
set -euo pipefail
cd "$(dirname "$0")"

export PROFILE="${1:-load}" USERS="${USERS:-5000}" RATE="${RATE:-}" P95_MS="${P95_MS:-500}"
[[ "$PROFILE" =~ ^(smoke|load|stress)$ ]] || { echo "smoke, load, stress 중 하나를 넘겨 주세요" >&2; exit 1; }
PERF_JWT_SECRET="$(openssl rand -hex 32)"
export PERF_JWT_SECRET
COMPOSE=(docker compose -f docker-compose.perf.yml)

cleanup() { [[ "${KEEP:-}" == 1 ]] || "${COMPOSE[@]}" --profile k6 down -v --remove-orphans > /dev/null 2>&1 || true; }
trap cleanup EXIT

mkdir -p results
chmod 777 results   # k6 컨테이너가 다른 사용자로 결과를 쓴다
"${COMPOSE[@]}" down -v --remove-orphans > /dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build postgres redis app

echo "앱 기동 대기"
for ((i = 1; i <= 90; i++)); do
  if "${COMPOSE[@]}" run --rm --no-deps -T --entrypoint wget k6 -qO- http://app:8080/actuator/health 2> /dev/null | grep -q '"status":"UP"'; then
    ready=1; break
  fi
  sleep 2
done
[[ "${ready:-}" == 1 ]] || { "${COMPOSE[@]}" logs --tail 50 app; exit 1; }

echo "가상 데이터 입력 (회원 $USERS명)"
"${COMPOSE[@]}" exec -T postgres psql -X -q -U byarrival -d byarrival -v ON_ERROR_STOP=1 \
  -c 'CREATE EXTENSION IF NOT EXISTS pg_stat_statements'
seed_output="$("${COMPOSE[@]}" exec -T postgres psql -X -q -U byarrival -d byarrival -v ON_ERROR_STOP=1 -v users="$USERS" < seed.sql)"
FIRST_USER_ID="$(sed -n 's/^FIRST_USER_ID=//p' <<< "$seed_output")"
[[ -n "$FIRST_USER_ID" ]] || { echo "가상 데이터를 넣지 못했습니다: $seed_output" >&2; exit 1; }

# JIT·커넥션 풀이 준비되지 않은 첫 요청이 결과에 섞이지 않게 예열한다
if [[ "$PROFILE" != smoke ]]; then
  echo "예열"
  "${COMPOSE[@]}" run --rm --no-deps -T -e FIRST_USER_ID="$FIRST_USER_ID" -e PROFILE=warmup -e RESULT=.warmup \
    k6 run --quiet --no-usage-report /scripts/api.js > /dev/null 2>&1 || true
fi
"${COMPOSE[@]}" exec -T postgres psql -X -q -U byarrival -d byarrival -c 'SELECT pg_stat_statements_reset()' > /dev/null

status=0
"${COMPOSE[@]}" run --rm --no-deps -T -e FIRST_USER_ID="$FIRST_USER_ID" -e COMMUTE_OFFSET="$([[ "$PROFILE" == smoke ]] && echo 0 || echo 300)" k6 run --quiet --no-usage-report /scripts/api.js || status=$?

echo "== 가장 오래 걸린 쿼리"
"${COMPOSE[@]}" exec -T postgres psql -X -U byarrival -d byarrival -c "
  SELECT calls, round(mean_exec_time::numeric, 2) AS mean_ms, round(total_exec_time::numeric) AS total_ms,
         left(regexp_replace(query, '\s+', ' ', 'g'), 110) AS query
  FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 8" || true
exit "$status"
