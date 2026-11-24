#!/usr/bin/env bash
# DB 백업. deploy.sh가 등록한 cron이 매일 03:00(KST)에 실행한다. 직접 실행해도 된다: ./db/backup.sh
#
# 1. pg_dump(-Fc)로 backups/에 저장하고, 읽을 수 있는 파일인지 확인한다.
# 2. .env에 BACKUP_S3_* 값이 있으면 S3에도 올린다.
# 3. 성공하면 7일 지난 서버 백업을 지우고 성공 시각을 메트릭으로 남긴다. 실패하면 디스코드로 알린다.
# 백업에는 개인정보가 있으므로 소유자만 읽게 하고 짧게 보관한다. S3의 7일 삭제는 버킷 수명 주기 규칙이 한다.
set -euo pipefail
umask 077

cd "$(dirname "$0")/.."

RETENTION_DAYS=7
CONTAINER="${BACKUP_CONTAINER:-byarrival-postgres}"
AWS_CLI_IMAGE="amazon/aws-cli:2.31.0"
METRIC_FILE="metrics/backup.prom"
STATE_DIR="backups/.state"

mkdir -p backups metrics "$STATE_DIR"

log() { echo "[backup] $(TZ=Asia/Seoul date '+%F %T') $*"; }

# .env에는 특수문자가 있을 수 있어 실행(source)하지 않고 값만 읽는다
env_value() {
  local value
  value="$(sed -n "s/^$1=//p" .env 2>/dev/null | tail -n 1 | tr -d '\r')"
  value="${value%\"}"; value="${value#\"}"
  printf '%s' "$value"
}

write_metrics() {
  local tmp="$METRIC_FILE.tmp" name
  {
    for name in byarrival_backup_last_success_timestamp_seconds byarrival_backup_size_bytes \
      byarrival_backup_s3_enabled byarrival_backup_s3_last_success_timestamp_seconds; do
      [[ -s "$STATE_DIR/$name" ]] || continue
      printf '# TYPE %s gauge\n%s %s\n' "$name" "$name" "$(cat "$STATE_DIR/$name")"
    done
  } > "$tmp"
  # node-exporter는 다른 사용자로 돌기 때문에 읽을 수 있어야 한다
  chmod 644 "$tmp"
  mv "$tmp" "$METRIC_FILE"
}
set_state() { printf '%s' "$2" > "$STATE_DIR/$1"; }

# 알림이 실패해도 백업 결과는 바뀌지 않는다
notify() {
  local webhook
  webhook="$(env_value DISCORD_BATCH_WEBHOOK_URL)"
  [[ -n "$webhook" ]] || return 0
  curl -fsS --max-time 10 -H 'Content-Type: application/json' \
    -d "{\"content\": \"$1\", \"allowed_mentions\": {\"parse\": []}}" "$webhook" > /dev/null || log "디스코드 알림 실패"
}

part=""
step="준비"
on_exit() {
  local status=$?
  [[ -z "$part" ]] || rm -f "$part"
  if (( status != 0 )); then
    log "실패: $step"
    notify "🚨 **DB 백업 실패** ($step) 서버의 backups/backup.log를 확인하세요."
  fi
}
trap on_exit EXIT

# 두 번 겹쳐 돌지 않게 한다
if command -v flock > /dev/null; then
  exec 9> "$STATE_DIR/lock"
  flock -n 9 || { log "이미 백업 중입니다."; exit 0; }
fi

file="backups/byarrival-$(TZ=Asia/Seoul date '+%Y%m%d-%H%M%S').dump"
part="$file.part"

step="pg_dump"
docker exec "$CONTAINER" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$part"

step="백업 파일 확인"
docker exec -i "$CONTAINER" pg_restore --list < "$part" > /dev/null
mv "$part" "$file"
part=""
size="$(wc -c < "$file" | tr -d ' ')"
log "저장: $file ($size bytes)"

set_state byarrival_backup_last_success_timestamp_seconds "$(date +%s)"
set_state byarrival_backup_size_bytes "$size"
write_metrics

# 새 백업이 성공한 뒤에만 지운다. 방금 만든 것은 항상 남는다
find backups -maxdepth 1 -name 'byarrival-*.dump' -mmin "+$((RETENTION_DAYS * 24 * 60))" -delete
find backups -maxdepth 1 -name 'byarrival-*.part' -mmin +1440 -delete

bucket="$(env_value BACKUP_S3_BUCKET)"
access_key="$(env_value BACKUP_S3_ACCESS_KEY)"
secret_key="$(env_value BACKUP_S3_SECRET_KEY)"
region="$(env_value BACKUP_S3_REGION)"
if [[ -n "$bucket" && -n "$access_key" && -n "$secret_key" ]]; then
  set_state byarrival_backup_s3_enabled 1
  write_metrics
  step="S3 업로드"
  # 키가 프로세스 목록에 보이지 않도록 값이 아니라 환경 변수 이름만 넘긴다
  AWS_ACCESS_KEY_ID="$access_key" AWS_SECRET_ACCESS_KEY="$secret_key" AWS_DEFAULT_REGION="${region:-ap-northeast-2}" \
    docker run --rm -i -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION \
    "$AWS_CLI_IMAGE" s3 cp - "s3://$bucket/$(basename "$file")" --sse AES256 --only-show-errors < "$file"
  set_state byarrival_backup_s3_last_success_timestamp_seconds "$(date +%s)"
  stored="서버 + S3"
  log "S3 업로드 완료"
else
  set_state byarrival_backup_s3_enabled 0
  stored="서버만"
  log "BACKUP_S3_* 값이 없어 서버에만 보관합니다."
fi
write_metrics
notify "✅ **DB 백업 완료** $(( (size + 1023) / 1024 ))KB · $stored"
