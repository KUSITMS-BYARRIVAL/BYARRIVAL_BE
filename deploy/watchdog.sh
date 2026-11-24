#!/usr/bin/env bash
# 감시 프로그램 자체를 감시한다. deploy.sh가 등록한 cron이 5분마다 실행한다.
# Prometheus나 Alertmanager가 죽으면 다른 알림이 모두 조용해지므로, 이 스크립트가 디스코드로 직접 알린다.
# 배포 중 재시작을 장애로 보지 않도록 두 번 이어서 실패해야 알리고, 복구되면 복구를 알린다.
set -uo pipefail
umask 077

cd "$(dirname "$0")" || exit 1

STATE_DIR="backups/.state"
STATE_FILE="$STATE_DIR/watchdog"
mkdir -p "$STATE_DIR"

. ./env.sh

webhook="$(env_value DISCORD_BATCH_WEBHOOK_URL)"
[[ -n "$webhook" ]] || exit 0

running() { [[ "$(docker inspect -f '{{.State.Running}}' "byarrival-$1" 2>/dev/null)" == true ]]; }
ready() { docker exec "byarrival-$1" wget -q -T 5 -O /dev/null "http://localhost:$2/-/ready" 2>/dev/null; }

down=()
for target in prometheus:9090 alertmanager:9093; do
  name="${target%%:*}"
  { running "$name" && ready "$name" "${target##*:}"; } || down+=("$name")
done
if [[ -n "$(env_value GRAFANA_ADMIN_PASSWORD)" ]]; then
  running grafana || down+=(grafana)
fi

send() {
  curl -fsS --max-time 10 -H 'Content-Type: application/json' \
    -d "{\"content\": \"$1\", \"allowed_mentions\": {\"parse\": []}}" "$webhook" > /dev/null
}

# 상태 파일: 이어서 실패한 횟수와 마지막으로 알린 대상
count=0; notified=""
[[ -f "$STATE_FILE" ]] && IFS='|' read -r count notified < "$STATE_FILE"
[[ "$count" =~ ^[0-9]+$ ]] || count=0

current="${down[*]:-}"
if [[ -n "$current" ]]; then
  count=$((count + 1))
  # 전송에 실패하면 notified를 바꾸지 않아 다음 실행에서 다시 보낸다
  if (( count >= 2 )) && [[ "$current" != "$notified" ]] \
    && send "🚨 **감시 프로그램 다운** ($current) 다른 알림이 오지 않을 수 있습니다. 서버에서 확인하세요."; then
    notified="$current"
  fi
else
  count=0
  if [[ -n "$notified" ]] && send "✅ **감시 프로그램 복구** ($notified)"; then
    notified=""
  fi
fi
printf '%s|%s\n' "$count" "$notified" > "$STATE_FILE"
