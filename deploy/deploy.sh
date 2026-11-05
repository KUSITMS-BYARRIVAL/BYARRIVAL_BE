#!/usr/bin/env bash
# 블루/그린 무중단 배포
# 사용법: ./deploy.sh <이미지>   예) ./deploy.sh ghcr.io/kusitms-byarrival/byarrival_be:<sha>
#
# 1. 배치 시간대(KST 23:30~00:30)이면 아무것도 바꾸지 않고 종료한다.
# 2. 쉬고 있는 쪽(blue/green)에 새 이미지를 띄우고 헬스 체크를 기다린다(5초 간격 40회).
# 3. 헬스 체크가 통과하면 Nginx를 새 쪽으로 돌리고, 이전 쪽을 내린다.
#    통과하지 못하면 새 쪽만 내리고 기존 쪽은 그대로 둔다.
set -euo pipefail

cd "$(dirname "$0")"

APP_IMAGE="${1:?배포할 이미지를 인자로 넘겨야 합니다}"
export APP_IMAGE

COMPOSE=(docker compose -f docker-compose.prod.yml)
UPSTREAM_FILE="nginx/upstream.inc"
HEALTH_RETRIES=40
HEALTH_INTERVAL=5

log() { echo "[deploy] $(TZ=Asia/Seoul date '+%F %T') $*"; }

# 1. 23:45 일일 배치와 겹치면 배치가 두 번 돌거나 중간에 끊기므로 배포하지 않는다
now=$((10#$(TZ=Asia/Seoul date +%H%M)))
if (( now >= 2330 || now < 30 )); then
  log "KST 23:30~00:30은 일일 배치 시간대라 배포하지 않습니다. 00:30 이후 다시 실행해 주세요."
  exit 1
fi

port_of() { [[ "$1" == "blue" ]] && echo 8081 || echo 8082; }

if [[ -f "$UPSTREAM_FILE" ]] && grep -q "app-blue" "$UPSTREAM_FILE"; then
  active="blue"; target="green"
elif [[ -f "$UPSTREAM_FILE" ]] && grep -q "app-green" "$UPSTREAM_FILE"; then
  active="green"; target="blue"
else
  active=""; target="blue"
fi
target_port="$(port_of "$target")"
log "현재: ${active:-없음(첫 배포)} → 배포 대상: $target ($APP_IMAGE)"

# DB와 Redis가 떠 있는지 확인한다. 이미 떠 있으면 설정이 바뀌었어도 재생성하지 않는다
# (구 앱이 서비스 중이므로 DB·Redis 설정 변경은 README의 별도 절차로 적용한다)
"${COMPOSE[@]}" up -d --wait --no-recreate postgres redis

# 2. 새 쪽 기동
"${COMPOSE[@]}" pull "app-$target"
"${COMPOSE[@]}" up -d --no-deps --force-recreate "app-$target"

log "헬스 체크 대기: http://127.0.0.1:$target_port/actuator/health"
healthy=false
for ((i = 1; i <= HEALTH_RETRIES; i++)); do
  if curl -fsS --connect-timeout 2 --max-time 5 "http://127.0.0.1:$target_port/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    healthy=true
    break
  fi
  sleep "$HEALTH_INTERVAL"
done

if [[ "$healthy" != true ]]; then
  log "헬스 체크 실패. app-$target 을 내리고 기존 상태를 유지합니다."
  "${COMPOSE[@]}" logs --tail 100 "app-$target" || true
  "${COMPOSE[@]}" stop "app-$target"
  exit 1
fi
log "app-$target 정상 기동"

# 3. Nginx 전환. 실패하면 upstream.inc를 이전 내용으로 되돌리고 새 쪽을 내린다
previous_upstream=""
[[ -f "$UPSTREAM_FILE" ]] && previous_upstream="$(cat "$UPSTREAM_FILE")"

cat > "$UPSTREAM_FILE" <<EOF
upstream app {
    server app-$target:8080;
    keepalive 16;
}
EOF

switch_nginx() {
  if [[ -n "$("${COMPOSE[@]}" ps -q --status running nginx)" ]]; then
    "${COMPOSE[@]}" exec -T nginx nginx -t && "${COMPOSE[@]}" exec -T nginx nginx -s reload
  else
    "${COMPOSE[@]}" up -d --no-deps nginx
  fi
}

if ! switch_nginx; then
  log "Nginx 전환 실패. upstream.inc를 되돌리고 app-$target 을 내립니다."
  if [[ -n "$previous_upstream" ]]; then
    printf '%s\n' "$previous_upstream" > "$UPSTREAM_FILE"
  else
    rm -f "$UPSTREAM_FILE"
  fi
  "${COMPOSE[@]}" stop "app-$target"
  exit 1
fi
log "Nginx가 app-$target 을 바라보도록 전환했습니다"

# 이전 쪽 종료 (진행 중인 요청은 Spring graceful shutdown으로 마무리된다)
if [[ -n "$active" ]]; then
  sleep 5
  "${COMPOSE[@]}" stop "app-$active"
  log "app-$active 종료"
fi

docker image prune -f > /dev/null
log "배포 완료"
