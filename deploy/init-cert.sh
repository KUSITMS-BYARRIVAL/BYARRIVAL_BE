#!/usr/bin/env bash
# Let's Encrypt IP 주소 인증서 최초 발급 (서버에서 한 번만 실행)
# 사용법: ./init-cert.sh <서버 공인 IP> [--staging]
#
# - deploy.sh 가 만든 임시 자체 서명 인증서를 지우고, webroot 방식으로 실제 인증서를 받는다.
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

log "임시 인증서를 지우고 $IP 인증서를 발급합니다 ${STAGING[*]:-}"
certbot_sh "rm -rf /etc/letsencrypt/live/$CERT_NAME /etc/letsencrypt/archive/$CERT_NAME /etc/letsencrypt/renewal/$CERT_NAME.conf"

if ! "${COMPOSE[@]}" run --rm --no-deps --entrypoint certbot certbot certonly \
  "${STAGING[@]}" \
  --webroot -w /var/www/certbot \
  --preferred-profile shortlived \
  --ip-address "$IP" \
  --cert-name "$CERT_NAME" \
  --non-interactive --agree-tos --register-unsafely-without-email; then
  log "발급 실패. Nginx가 재시작돼도 뜰 수 있도록 다음 배포 때 임시 인증서가 다시 만들어집니다."
  log "80 포트(보안 그룹)와 http://$IP/.well-known/acme-challenge/ 접근을 확인한 뒤 다시 실행해 주세요."
  exit 1
fi

# 6일짜리 인증서이므로 기본값(만료 30일 전)이면 12시간마다 재발급된다. 만료 3일 전으로 맞춘다
certbot_sh "f=/etc/letsencrypt/renewal/$CERT_NAME.conf && { echo 'renew_before_expiry = 3 days'; grep -v 'renew_before_expiry' \"\$f\"; } > \"\$f.tmp\" && mv \"\$f.tmp\" \"\$f\""

"${COMPOSE[@]}" exec -T nginx nginx -t
"${COMPOSE[@]}" exec -T nginx nginx -s reload
log "완료. https://$IP 로 접속해 확인해 주세요."
