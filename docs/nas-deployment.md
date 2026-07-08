# Synology NAS 배포

이 문서는 `kis-trader-back` 프로젝트와 같은 배포 흐름을 기준으로 합니다.

흐름:

1. `main` 브랜치에 push
2. GitHub Actions가 Docker 이미지를 GHCR에 빌드/푸시
3. GitHub Actions가 Tailscale로 NAS에 연결
4. SSH로 NAS 배포 경로에 `compose.yml`, `deploy-synology.sh` 업로드
5. NAS에서 `docker compose pull`, `docker compose up -d`
6. `/actuator/health`가 healthy가 될 때까지 대기

관련 파일:

- 워크플로우: `.github/workflows/deploy.yml`
- NAS Compose: `deploy/compose.yml`
- NAS 배포 스크립트: `scripts/deploy-synology.sh`
- 운영 환경변수 예시: `.env.prod.example`

## 운영 구조

NAS 배포 Compose는 앱 컨테이너만 실행합니다. 개발용 `docker-compose.yml`은 로컬 PostgreSQL까지 포함하므로 NAS 배포에는 사용하지 않습니다.

`deploy/compose.yml`은 KIS 프로젝트처럼 `network_mode: host`를 사용합니다. 따라서 앱 컨테이너에서 `127.0.0.1:<port>`는 NAS 호스트의 포트를 바라봅니다. NAS의 DB 컨테이너가 호스트 포트 `15432`로 열려 있다면 다음처럼 연결합니다.

```env
DATABASE_URL=jdbc:postgresql://127.0.0.1:15432/my_data?currentSchema=public
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<password>
```

주의할 점:

- KIS 프로젝트의 `DATABASE_URL=postgresql://postgres:<password>@127.0.0.1:15432/kis_trader_back?schema=public` 형식은 Prisma/Node용입니다.
- 이 프로젝트는 Spring Boot JDBC를 사용하므로 `jdbc:postgresql://...` 형식이어야 합니다.
- PostgreSQL schema 파라미터도 `schema=public`이 아니라 `currentSchema=public`을 사용합니다.
- 비밀번호는 URL에 넣지 말고 `DATABASE_PASSWORD`에 분리해서 넣는 방식을 권장합니다.

## NAS 사전 준비

NAS에 필요한 것:

- Docker 또는 Synology Container Manager
- 기존 PostgreSQL/pgvector 컨테이너
- GitHub Actions에서 접속 가능한 Tailscale
- SSH 접속 가능한 NAS 사용자
- NAS 사용자가 Docker 명령을 실행할 수 있는 권한

Docker CLI 경로는 기본값으로 `/usr/local/bin/docker`를 사용합니다. NAS에서 위치가 다르면 배포 실행 시 `DOCKER_BIN`을 지정합니다.

```sh
DOCKER_BIN=/usr/bin/docker
```

NAS 사용자가 `sudo -n docker ...`를 실행할 수 없고 Docker 그룹 권한으로 직접 실행한다면 다음 값을 사용합니다.

```sh
USE_SUDO_DOCKER=false
```

## NAS 배포 경로 준비

예시 배포 경로:

```text
/volume1/docker/my-data-slack-chat
```

NAS에서 배포 경로를 만들고 운영 환경파일을 작성합니다.

```sh
mkdir -p /volume1/docker/my-data-slack-chat
cd /volume1/docker/my-data-slack-chat
vi .env.prod
```

`.env.prod`는 `.env.prod.example`을 기준으로 작성합니다.

최소 예시:

```env
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=50506

DATABASE_URL=jdbc:postgresql://127.0.0.1:15432/my_data?currentSchema=public
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=<password>

ADMIN_BOOTSTRAP_EMAIL=admin@example.com
ADMIN_BOOTSTRAP_PASSWORD=<initial-admin-password>
ADMIN_BOOTSTRAP_DISPLAY_NAME=관리자

SLACK_SOCKET_MODE_ENABLED=false
SLACK_HTTP_EVENTS_ENABLED=false
MY_DATA_LLM_PROVIDER=stub
```

실제 Slack, Notion, OpenAI, Claude 비밀값은 필요한 기능을 켤 때만 추가합니다.

## GitHub Secrets

Repository Settings의 `Secrets and variables` → `Actions`에 다음 값을 등록합니다.

필수:

- `TS_OAUTH_CLIENT_ID`
- `TS_OAUTH_SECRET`
- `SYNOLOGY_HOST`
- `SYNOLOGY_USER`
- `SYNOLOGY_SSH_KEY`
- `SYNOLOGY_DEPLOY_PATH`

선택:

- `SYNOLOGY_PORT`

설명:

- `TS_OAUTH_CLIENT_ID`, `TS_OAUTH_SECRET`: Tailscale OAuth Client 값입니다. `auth_keys` writable scope가 필요합니다.
- `SYNOLOGY_HOST`: NAS의 Tailscale hostname 또는 100.x IP입니다.
- `SYNOLOGY_PORT`: SSH 포트입니다. 생략하면 `2008`을 사용합니다.
- `SYNOLOGY_USER`: SSH 로그인 사용자입니다.
- `SYNOLOGY_SSH_KEY`: GitHub Actions가 사용할 SSH private key입니다.
- `SYNOLOGY_DEPLOY_PATH`: NAS 배포 경로입니다. 예: `/volume1/docker/my-data-slack-chat`

## 수동 배포 확인

GitHub Actions 없이 NAS에서 수동으로 확인하려면 배포 경로에 `compose.yml`, `deploy-synology.sh`, `.env.prod`가 있어야 합니다.

```sh
cd /volume1/docker/my-data-slack-chat
IMAGE=ghcr.io/greatbooms/my-data-slack-chat:latest \
APP_NAME=my-data-slack-chat \
DEPLOY_PATH=/volume1/docker/my-data-slack-chat \
./deploy-synology.sh
```

상태 확인:

```sh
docker compose -f compose.yml ps
docker logs --tail 120 my-data-slack-chat
curl http://127.0.0.1:50506/actuator/health
```

관리자 화면:

```text
http://<NAS_HOST>:50506/admin-ui
```

## DB 연결 문제 확인

NAS에서 DB 포트가 열려 있는지 먼저 확인합니다.

```sh
nc -zv 127.0.0.1 15432
```

DB 컨테이너가 호스트 포트를 열지 않았다면, 현재 Compose의 `network_mode: host` 방식으로는 `127.0.0.1:15432`에 붙을 수 없습니다. 그 경우에는 DB 컨테이너 포트를 NAS 호스트로 publish하거나, 배포 Compose를 DB 컨테이너와 같은 Docker network에 붙이고 `jdbc:postgresql://<db-container-name>:5432/my_data` 형식으로 바꿔야 합니다.

## 트러블슈팅

### 배포 스크립트가 `.env.prod`가 없다고 실패할 때

NAS 배포 경로에 운영 환경파일을 직접 만들어야 합니다.

```sh
ls -la /volume1/docker/my-data-slack-chat/.env.prod
```

### 컨테이너가 unhealthy일 때

```sh
docker inspect my-data-slack-chat --format '{{json .State.Health}}'
docker logs --tail 120 my-data-slack-chat
curl -v http://127.0.0.1:50506/actuator/health
```

### GitHub Actions가 NAS에 접속하지 못할 때

확인할 값:

- `SYNOLOGY_HOST`
- `SYNOLOGY_PORT`
- `SYNOLOGY_USER`
- `SYNOLOGY_SSH_KEY`
- Tailscale OAuth Client 권한
- NAS가 tailnet에서 online 상태인지 여부
