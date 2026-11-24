#!/usr/bin/env bash
# DB 백업. deploy.sh가 등록한 cron이 매일 03:00(KST)에 실행한다. 직접 실행해도 된다: ./db/backup.sh
#
# 1. pg_dump(-Fc)로 backups/에 저장하고, 읽을 수 있는 파일인지 확인한다.
# 2. .env에 BACKUP_S3_* 값이 있으면 S3에도 올린다.
# 3. 성공 시각을 메트릭으로 남기고 결과를 디스코드로 알린다.
# 4. 성공·실패와 상관없이 보관 기간(7일)이 끝나는 서버 백업을 지운다.
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
# node-exporter는 다른 사용자로 돌기 때문에 metrics 안을 볼 수 있어야 한다. backups는 소유자만 본다
chmod 755 metrics
chmod 700 backups

log() { echo "[backup] $(TZ=Asia/Seoul date '+%F %T') $*"; }

. ./env.sh

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

# 보관 상한(탈퇴 회원 파기, MY-10)을 지키려고 백업이 실패해도 지운다. 실패가 7일 이어지면 서버 백업이 모두 사라지므로
# 실패 알림과 BackupStale(26시간) 알림을 받으면 바로 고친다.
# 다음 정리(03:00)까지 두면 7일이 넘는 파일을 지운다. 직접 실행한 백업도 만든 시각과 상관없이 7일을 넘기지 않는다
delete_expired() {
  local cutoff failed=0
  # 다음 03:00에서 7일을 뺀 시각
  if cutoff="$(TZ=Asia/Seoul date -d "-$((RETENTION_DAYS - 1)) days -3 hours" '+%F 03:00:00')"; then
    TZ=Asia/Seoul find backups -maxdepth 1 -name 'byarrival-*.dump' ! -newermt "$cutoff" -delete || failed=1
  else
    failed=1
  fi
  find backups -maxdepth 1 -name 'byarrival-*.part' -mmin +1440 -delete || failed=1
  return "$failed"
}

part=""
step="준비"
on_exit() {
  local status=$?
  [[ -z "$part" ]] || rm -f "$part"
  delete_expired || { log "만료 백업 삭제 실패"; notify "🚨 **만료된 DB 백업 삭제 실패** 서버의 backups를 확인하세요."; }
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
