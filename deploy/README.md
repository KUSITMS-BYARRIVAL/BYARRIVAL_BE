# 운영 배포

main에 push되면 `.github/workflows/deploy.yml`이 이미지를 GHCR에 올리고, 서버에서 `deploy.sh`로 블루/그린 배포를 합니다.

```
Nginx(:80) ─┬─ app-blue  (127.0.0.1:8081)
            └─ app-green (127.0.0.1:8082)
PostgreSQL, Redis: 외부 포트 없음 (compose 네트워크 안에서만 접근)
```

## 배포 흐름 (`deploy.sh`)

1. KST 23:30~00:30이면 아무것도 바꾸지 않고 종료합니다. 23:45 일일 배치가 두 컨테이너에서 두 번 돌거나, 배포 중 이전 컨테이너를 내리면서 끊기는 것을 막습니다. 00:30 이후 Actions에서 다시 실행합니다.
2. 쉬고 있는 쪽에 새 이미지를 띄우고 `/actuator/health`가 `UP`이 될 때까지 기다립니다. 5초 간격으로 40번 확인하고, 한 번 확인에 최대 5초까지 기다립니다(전체 최대 약 7분).
3. 통과하면 `nginx/upstream.inc`를 새 쪽으로 바꾸고 Nginx를 reload한 뒤 이전 쪽을 내립니다.
   - 헬스 체크가 실패하면 새 쪽만 내리고 기존 쪽은 그대로 둡니다.
   - Nginx 검사·reload가 실패하면 `upstream.inc`를 이전 내용으로 되돌리고 새 쪽을 내립니다.

PostgreSQL·Redis는 이미 떠 있으면 배포 때 재생성하지 않습니다(`--no-recreate`). 설정을 바꿨을 때는 아래 "DB·Redis 설정 변경" 절차로 적용합니다.

지금은 HTTP(80)로만 서비스합니다. HTTPS(443, 인증서, 80→443 리다이렉트)는 DCK-74에서 추가합니다.

`/actuator`는 Nginx에서 막혀 있어 외부에서 접근할 수 없습니다.

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
| 보안 그룹 인바운드 | 22, 80, 443 (443은 DCK-74 HTTPS용, DB·Redis 포트는 열지 않음) |
| 배포 폴더 | `~/byarrival` (compose, 스크립트, Nginx 설정은 배포 때 Actions가 복사) |
| 환경 변수 | `~/byarrival/.env` (서버에만 있음, 권한 600) |
| 배포 키 | GitHub Actions 전용 키가 `authorized_keys`에 등록됨 |
| GitHub Secrets | `SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY` |

GHCR 로그인은 워크플로의 `GITHUB_TOKEN`을 쓰므로 따로 등록하지 않습니다.

### `.env` 키 목록

값을 추가하거나 바꿀 때 참고합니다. 앱이 읽는 값(`DB_*`, `REDIS_*`, `KAKAO_*`, `APPLE_*`, `JAVA_OPTS`)은 수정 후 다음 배포부터 적용됩니다. `POSTGRES_*`는 아래 "DB·Redis 설정 변경"을 따릅니다.

| 키 | 값 |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER` | `byarrival` |
| `POSTGRES_PASSWORD` | 무작위 비밀번호 (DB를 처음 만들 때만 적용되므로 이후 `.env`만 바꿔서는 변경되지 않음) |
| `DB_URL` | `jdbc:postgresql://postgres:5432/byarrival` |
| `DB_USERNAME`, `DB_PASSWORD` | `POSTGRES_USER`, `POSTGRES_PASSWORD`와 같은 값 |
| `REDIS_HOST`, `REDIS_PORT` | `redis`, `6379` |
| `KAKAO_APP_ID` | 카카오 앱 ID |
| `APPLE_CLIENT_ID` | iOS 앱 Bundle ID |
| `JAVA_OPTS` | `-Xms256m -Xmx768m` (전환 중 앱 두 개가 같이 뜨므로 4GB 서버 기준으로 제한) |

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

### DB·Redis 설정 변경

`docker-compose.prod.yml`의 postgres·redis 설정이나 `.env`의 `POSTGRES_*`를 바꾼 경우에만 필요합니다. 컨테이너를 다시 만드는 동안 DB 요청이 실패하므로 사용자가 적은 시간에 합니다(KST 23:30~00:30 배치 시간대는 피합니다).

```bash
cd ~/byarrival
docker compose -f docker-compose.prod.yml up -d --wait postgres redis
```

`POSTGRES_PASSWORD`는 DB 데이터가 처음 만들어질 때만 적용됩니다. 비밀번호를 바꾸려면 DB 안에서 `ALTER USER`로 바꾼 뒤 `.env`의 `POSTGRES_PASSWORD`와 `DB_PASSWORD`를 같은 값으로 고치고 다시 배포합니다.

## 자주 쓰는 명령 (서버의 `~/byarrival`)

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f --tail 100 app-blue
cat nginx/upstream.inc   # 지금 연결된 쪽
```
