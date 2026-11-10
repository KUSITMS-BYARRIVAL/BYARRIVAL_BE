# 운영 배포

dev에 머지되면(push) `.github/workflows/deploy.yml`이 이미지를 GHCR에 올리고, 서버에서 `deploy.sh`로 블루/그린 배포를 합니다. Actions의 Deploy 워크플로를 dev 브랜치로 수동 실행해도 같습니다.

출시 전까지 이 서버는 개발 서버 역할을 합니다. 출시 전에 운영 서버 분리나 main 배포 전환 여부를 다시 정합니다.

```
Nginx(:80→:443) ─┬─ app-blue  (127.0.0.1:8081)
                 └─ app-green (127.0.0.1:8082)
PostgreSQL, Redis: 외부 포트 없음 (compose 네트워크 안에서만 접근)
Prometheus: 외부 포트 없음, app-blue·app-green의 /actuator/prometheus를 15초마다 수집
Grafana: 서버 localhost:3000에만 공개 (SSH 터널로 접속)
```

## 배포 흐름 (`deploy.sh`)

1. KST 23:30~00:30이면 아무것도 바꾸지 않고 종료합니다. 23:45 일일 배치가 두 컨테이너에서 두 번 돌거나, 배포 중 이전 컨테이너를 내리면서 끊기는 것을 막습니다. 00:30 이후 Actions에서 다시 실행합니다.
2. 쉬고 있는 쪽에 새 이미지를 띄우고 `/actuator/health`가 `UP`이 될 때까지 기다립니다. 5초 간격으로 40번 확인하고, 한 번 확인에 최대 5초까지 기다립니다(전체 최대 약 7분).
3. 통과하면 `nginx/upstream.inc`를 새 쪽으로 바꾸고 Nginx를 reload한 뒤 이전 쪽을 내립니다.
   - 헬스 체크가 실패하면 새 쪽만 내리고 기존 쪽은 그대로 둡니다.
   - Nginx 검사·reload가 실패하면 `upstream.inc`를 이전 내용으로 되돌리고 새 쪽을 내립니다.

PostgreSQL·Redis는 이미 떠 있으면 배포 때 재생성하지 않습니다(`--no-recreate`). 설정을 바꿨을 때는 아래 "DB·Redis 설정 변경" 절차로 적용합니다.

HTTP(80) 요청은 HTTPS(443)로 리다이렉트합니다. 80에서는 인증서 발급·갱신 확인 경로(`/.well-known/acme-challenge/`)만 직접 응답합니다.

`/actuator`는 Nginx에서 막혀 있어 외부에서 접근할 수 없습니다.

## 모니터링

### 디스코드 알림

`.env`에 웹훅 URL을 넣으면 다음 배포부터 알림이 갑니다. 비어 있으면 그 채널 알림은 보내지 않습니다.

| 채널 | 키 | 보내는 내용 |
|---|---|---|
| 서버 에러 | `DISCORD_ERROR_WEBHOOK_URL` | 500 에러(처리되지 않은 예외, 5xx BusinessException, 필터에서 난 예외). 같은 예외·메서드·URI는 5분에 한 번만 보내고, 그사이 생략한 건수를 다음 알림에 붙입니다. 서로 다른 에러가 1,000가지를 넘으면 새 에러는 알림 하나로 묶습니다. |
| 배치·운영 | `DISCORD_BATCH_WEBHOOK_URL` | 일일 배치 완료 요약(카테고리 이름별 추천 대상 기사 수, 최대 수 미달 카테고리, 소요 시간), 배치 실패와 사유(0건 실패면 빈·미달 카테고리 이름), 배치 비정상 종료, RSS Feed 자동 비활성화 |

예외 메시지에는 사용자 값이나 외부 URL이 섞일 수 있어 알림에는 예외 종류만 넣습니다. 원인은 서버 로그(`docker compose -f docker-compose.prod.yml logs app-blue`)에서 확인합니다.

웹훅은 디스코드 채널 설정 → 연동 → 웹후크에서 만듭니다. 웹훅 URL에는 토큰이 들어 있으므로 `.env`에만 두고 공유하지 않습니다.

### Prometheus·Grafana

배포할 때 `deploy.sh`가 Prometheus와 Grafana를 함께 띄웁니다. 둘 다 메모리 256MB로 제한하고, Prometheus는 15일(최대 1GB)만 보관합니다. 모니터링이 실패해도 앱 배포는 계속합니다. Grafana는 `.env`에 `GRAFANA_ADMIN_PASSWORD`가 있을 때만 띄웁니다. 비밀번호 없이 뜨면 기본 계정(admin/admin)이 생기므로, compose에서도 `monitoring` profile로 분리하고 비밀번호가 비어 있으면 시작하지 않게 막았습니다. 직접 띄울 때는 `docker compose -f docker-compose.prod.yml up -d grafana`처럼 서비스 이름을 지정합니다.

Grafana 접속:

```bash
ssh -L 3000:127.0.0.1:3000 ubuntu@<서버 IP>
```

터널을 연 채로 브라우저에서 `http://localhost:3000`에 `admin` / `GRAFANA_ADMIN_PASSWORD`로 로그인합니다. Prometheus 데이터 소스는 자동으로 등록됩니다. JVM·HTTP 대시보드는 Dashboards → New → Import에서 ID `4701`(JVM Micrometer)과 `19004`(Spring Boot 3.x Statistics)를 가져와 쓰면 됩니다.

앱이 따로 남기는 메트릭:

| 메트릭 | 내용 |
|---|---|
| `byarrival_batch_duration_seconds{result="success"\|"failure"}` | 일일 배치 실행 시간과 횟수 |
| `byarrival_batch_last_success` | 마지막 배치 성공 시각(epoch seconds). 하루 넘게 그대로면 배치가 돌지 않은 것 |
| `byarrival_batch_category_articles{categoryId}` | 마지막 성공 배치의 카테고리별 저장 기사 수 |
| `byarrival_feed_fetch_failures_total` | RSS Feed 수집 실패 수 |
| `byarrival_feed_deactivated_total` | 연속 실패로 자동 비활성화한 Feed 수 |

blue/green 중 쉬고 있는 쪽은 꺼져 있으므로 Prometheus Targets에서 한쪽이 DOWN으로 보이는 것이 정상입니다.

## 팀원 서버 접속

서버 설정은 이미 끝나 있어서 팀원이 따로 세팅할 것은 없습니다. 접속용 키만 등록하면 됩니다.
서버 공인 IP는 팀 공유 채널에서 확인합니다. 개인 키(pem 포함)는 메신저로 주고받지 않습니다.

1. 내 컴퓨터에서 키를 만듭니다. 맥 터미널과 윈도우 PowerShell 모두 같은 명령입니다.

   ```bash
   ssh-keygen -t ed25519 -C "byarrival-<이름>"
   ```

2. 공개 키(`~/.ssh/id_ed25519.pub`, `ssh-ed25519 AAAA...`로 시작하는 한 줄)를 이미 접속할 수 있는 팀원에게 보냅니다.
3. 그 팀원이 서버에서 `~/.ssh/authorized_keys`에 한 줄 추가합니다.
4. 접속합니다.

   ```bash
   ssh ubuntu@<서버 IP>
   ```

## 서버 구성 (설정 완료)

| 항목 | 내용 |
|---|---|
| 서버 | 가비아 클라우드 Gen2, Ubuntu 24.04 LTS, 4GB 메모리, swap 2GB |
| 타임존 | Asia/Seoul |
| 보안 그룹 인바운드 | 22, 80, 443 (80은 인증서 발급·갱신에 필요, DB·Redis 포트는 열지 않음) |
| HTTPS 인증서 | Let's Encrypt IP 주소 인증서 (`~/byarrival/certbot/conf`, 자동 갱신) |
| 배포 폴더 | `~/byarrival` (compose, 스크립트, Nginx 설정은 배포 때 Actions가 복사) |
| 환경 변수 | `~/byarrival/.env` (서버에만 있음, 권한 600) |
| 배포 키 | GitHub Actions 전용 키가 `authorized_keys`에 등록됨 |
| GitHub Secrets | `SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY` |

GHCR 로그인은 워크플로의 `GITHUB_TOKEN`을 쓰므로 따로 등록하지 않습니다.

### `.env` 키 목록

값을 추가하거나 바꿀 때 참고합니다. 앱이 읽는 값(`DB_*`, `REDIS_*`, `KAKAO_*`, `APPLE_*`, `JWT_SECRET`, `JAVA_OPTS`, `DISCORD_*`, `FCM_*`, `ANTHROPIC_API_KEY`)은 수정 후 다음 배포부터 적용됩니다. `POSTGRES_*`는 아래 "DB·Redis 설정 변경"을 따릅니다.

| 키 | 값 |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER` | `byarrival` |
| `POSTGRES_PASSWORD` | 무작위 비밀번호 (DB를 처음 만들 때만 적용되므로 이후 `.env`만 바꿔서는 변경되지 않음) |
| `DB_URL` | `jdbc:postgresql://postgres:5432/byarrival` |
| `DB_USERNAME`, `DB_PASSWORD` | `POSTGRES_USER`, `POSTGRES_PASSWORD`와 같은 값 |
| `REDIS_HOST`, `REDIS_PORT` | `redis`, `6379` |
| `KAKAO_APP_ID` | 카카오 앱 ID |
| `APPLE_CLIENT_ID` | iOS 앱 Bundle ID |
| `FCM_ENABLED` | `true`면 FCM으로 푸시를 보냅니다. 없거나 `false`면 로그만 남깁니다 |
| `FCM_CREDENTIALS_BASE64` | Firebase 서비스 계정 키 JSON 파일을 base64로 인코딩한 한 줄 (`base64 -w0 키파일.json`) |
| `JWT_SECRET` | JWT 서명 키. 32바이트 이상 무작위 문자열(예: `openssl rand -base64 48`). 없으면 앱이 시작되지 않고, 바꾸면 발급된 액세스 토큰이 모두 무효가 됨 |
| `JAVA_OPTS` | `-Xms256m -Xmx768m` (전환 중 앱 두 개가 같이 뜨므로 4GB 서버 기준으로 제한) |
| `DISCORD_ERROR_WEBHOOK_URL` | 서버 에러 채널 디스코드 웹훅 URL. 비우면 알림 끔 |
| `DISCORD_BATCH_WEBHOOK_URL` | 배치·운영 채널 디스코드 웹훅 URL. 비우면 알림 끔 |
| `ANTHROPIC_API_KEY` | AI 요약에 쓰는 Claude API 키(운영 전용 키, `sk-ant-`로 시작). 없으면 앱은 뜨지만 요약이 모두 실패함. 키의 만료일을 팀 캘린더에 적어 둠 |
| `GRAFANA_ADMIN_PASSWORD` | Grafana `admin` 비밀번호. 없으면 Grafana를 띄우지 않음. Grafana를 처음 띄울 때만 적용되므로 이후 변경은 Grafana 화면에서 함 |

## 서버를 새로 만들 때만

서버를 옮기거나 다시 만들 때만 필요한 순서입니다.

1. Ubuntu 서버 생성, 공인 IP 할당, 보안 그룹 인바운드 22·80·443
2. `sudo timedatectl set-timezone Asia/Seoul`, swap 2GB, Docker와 Compose 플러그인 설치, `ubuntu` 계정을 `docker` 그룹에 추가
3. `~/byarrival/.env`를 위 키 목록대로 만들고 `chmod 600`
4. 배포 전용 키를 만들어 공개 키를 서버 `authorized_keys`에 추가

   ```bash
   ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/byarrival-deploy -N ""
   ```

5. GitHub Secrets의 `SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY`(배포 전용 개인 키 전체)를 새 서버 기준으로 갱신
6. 첫 배포가 끝나면 아래 "HTTPS 인증서"의 최초 발급을 실행

### DB·Redis 설정 변경

`docker-compose.prod.yml`의 postgres·redis 설정이나 `.env`의 `POSTGRES_*`를 바꾼 경우에만 필요합니다. 컨테이너를 다시 만드는 동안 DB 요청이 실패하므로 사용자가 적은 시간에 합니다(KST 23:30~00:30 배치 시간대는 피합니다).

```bash
cd ~/byarrival
docker compose -f docker-compose.prod.yml up -d --wait postgres redis
```

`POSTGRES_PASSWORD`는 DB 데이터가 처음 만들어질 때만 적용됩니다. 비밀번호를 바꾸려면 DB 안에서 `ALTER USER`로 바꾼 뒤 `.env`의 `POSTGRES_PASSWORD`와 `DB_PASSWORD`를 같은 값으로 고치고 다시 배포합니다.

## HTTPS 인증서

서버 공인 IP로 Let's Encrypt **IP 주소 인증서**를 받습니다. Let's Encrypt 정책상 IP 주소 인증서는 **6일짜리**(shortlived)만 발급됩니다.

- `certbot` 컨테이너가 12시간마다 확인해 만료 3일 전에 갱신합니다.
- `nginx` 컨테이너는 6시간마다 reload해서 갱신된 인증서를 읽습니다.
- 갱신이 3일 넘게 실패하면 인증서가 만료되므로 가끔 아래 "상태 확인"을 봅니다.

### 최초 발급 (서버에서 한 번)

인증서가 없으면 `deploy.sh`가 임시 자체 서명 인증서를 만들어 Nginx를 띄웁니다. 이 상태에서는 브라우저가 경고를 띄우므로, HTTPS 설정이 처음 배포된 직후 실제 인증서를 발급합니다.

```bash
cd ~/byarrival
./init-cert.sh <서버 공인 IP> --staging   # 시험 발급 (신뢰되지 않는 인증서, 발급 횟수 제한 없음)
./init-cert.sh <서버 공인 IP>             # 실제 발급
```

시험 발급이 성공해야 실제 발급을 합니다. 실패하면 보안 그룹의 80 포트와 `http://<서버 IP>/.well-known/acme-challenge/` 접근을 확인합니다.

### 상태 확인

```bash
docker compose -f docker-compose.prod.yml logs --tail 50 certbot
echo | openssl s_client -connect localhost:443 2>/dev/null | openssl x509 -noout -dates   # notAfter가 만료 시각
```

### 도메인으로 바꿀 때

도메인을 서버 IP에 연결한 뒤 `init-cert.sh`의 `--ip-address "$IP"`를 `-d <도메인>`으로 바꿔 발급합니다. 도메인 인증서는 90일짜리라 `--preferred-profile shortlived`와 `renew_before_expiry` 조정은 필요 없습니다.

## 자주 쓰는 명령 (서버의 `~/byarrival`)

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f --tail 100 app-blue
cat nginx/upstream.inc   # 지금 연결된 쪽
```
