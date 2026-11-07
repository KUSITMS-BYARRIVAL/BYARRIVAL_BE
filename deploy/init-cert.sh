#!/usr/bin/env bash
# Let's Encrypt IP 주소 인증서 최초 발급 (서버에서 한 번만 실행)
# 사용법: ./init-cert.sh <서버 공인 IP> [--staging]
#
# - 기존 인증서(deploy.sh 가 만든 임시 인증서 등)를 백업해 두고 webroot 방식으로 실제 인증서를 받는다.
#   발급에 실패하면 백업을 되돌린다.
# - IP 주소 인증서는 Let's Encrypt 정책상 6일짜리(shortlived)만 발급된다.
#   만료 3일 전부터 certbot 컨테이너가 자동 갱신하고, Nginx는 6시간마다 reload 한다.
# - --staging 으로 먼저 시험하면 발급 횟수 제한에 걸리지 않는다 (브라우저는 신뢰하지 않음).
set -euo pipefail

cd "$(dirname "$0")"

IP="${1:?서버 공인 IP를 인자로 넘겨야 합니다}"
STAGING=()
[[ "${2:-}" == "--staging" ]] && STAGING=(--staging)

COMPOSE=(docker compose -f docker-compose.prod.yml)
CERT_NAME="byarrival"

log() { echo "[init-cert] $*"; }
certbot_sh() { "${COMPOSE[@]}" run --rm --no-deps --entrypoint sh certbot -c "$1"; }

if [[ -z "$("${COMPOSE[@]}" ps -q --status running nginx)" ]]; then
  log "Nginx가 실행 중이 아닙니다. HTTPS 설정이 들어간 버전을 먼저 배포해 주세요."
  exit 1
fi

# 기존 인증서(임시 또는 이전 발급분)는 지우지 않고 옮겨 두었다가, 발급에 실패하면 되돌린다
LE=/etc/letsencrypt
BACKUP="$LE/backup-$CERT_NAME"
backup_cert() {
  certbot_sh "rm -rf $BACKUP && mkdir -p $BACKUP && \
    for p in live/$CERT_NAME archive/$CERT_NAME renewal/$CERT_NAME.conf; do \
      [ -e $LE/\$p ] && mkdir -p \$(dirname $BACKUP/\$p) && mv $LE/\$p $BACKUP/\$p; \
    done; true"
}
restore_cert() {
  certbot_sh "for p in live/$CERT_NAME archive/$CERT_NAME renewal/$CERT_NAME.conf; do \
      rm -rf $LE/\$p; [ -e $BACKUP/\$p ] && mkdir -p \$(dirname $LE/\$p) && mv $BACKUP/\$p $LE/\$p; \
    done; rm -rf $BACKUP; true"
}

log "기존 인증서를 백업하고 $IP 인증서를 발급합니다 ${STAGING[*]:-}"
backup_cert

if ! "${COMPOSE[@]}" run --rm --no-deps --entrypoint certbot certbot certonly \
  "${STAGING[@]}" \
  --webroot -w /var/www/certbot \
  --preferred-profile shortlived \
  --ip-address "$IP" \
  --cert-name "$CERT_NAME" \
  --non-interactive --agree-tos --register-unsafely-without-email; then
  restore_cert
  log "발급 실패. 기존 인증서를 되돌렸습니다."
  log "80 포트(보안 그룹)와 http://$IP/.well-known/acme-challenge/ 접근을 확인한 뒤 다시 실행해 주세요."
  exit 1
fi
certbot_sh "rm -rf $BACKUP"

# 6일짜리 인증서이므로 기본값(만료 30일 전)이면 12시간마다 재발급된다. 만료 3일 전으로 맞춘다.
# 실패해도 발급된 인증서는 아래에서 적용한다
if ! certbot_sh "f=$LE/renewal/$CERT_NAME.conf && { echo 'renew_before_expiry = 3 days'; grep -v 'renew_before_expiry' \"\$f\"; } > \"\$f.tmp\" && mv \"\$f.tmp\" \"\$f\""; then
  log "경고: 갱신 시점 설정에 실패했습니다. $LE/renewal/$CERT_NAME.conf 맨 위에 'renew_before_expiry = 3 days'를 직접 넣어 주세요."
fi

"${COMPOSE[@]}" exec -T nginx nginx -t
"${COMPOSE[@]}" exec -T nginx nginx -s reload
log "완료. https://$IP 로 접속해 확인해 주세요."
