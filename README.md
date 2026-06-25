# My Data

Spring Boot 기반 개인 데이터 RAG 챗봇 기반 프로젝트입니다.

## 사전 준비

- Java 21
- Node.js 20 또는 22 이상
- npm
- Docker Desktop 또는 Docker Engine

저장소를 원하는 위치에 clone한 뒤 프로젝트 루트로 이동합니다.

```bash
git clone https://github.com/greatbooms/my-data-slack-chat.git
cd my-data-slack-chat
```

## 로컬 DB 실행

PostgreSQL과 pgvector는 Docker Compose로 실행합니다.

```bash
docker compose up -d postgres
```

DB 상태를 확인합니다.

```bash
docker compose ps postgres
```

로컬 DB는 다른 PostgreSQL 서비스와 충돌하지 않도록 `localhost:5433`으로 열립니다.
컨테이너 내부 PostgreSQL 포트는 `5432`입니다.

## 테스트 실행

전체 테스트는 다음 명령으로 실행합니다.

```bash
./gradlew test
```

테스트에서는 Testcontainers가 별도 PostgreSQL/pgvector 컨테이너를 띄웁니다.
Gradle `processResources` 단계에서 관리자 React 앱도 함께 빌드하므로, 로컬에 `node`와 `npm`이 있어야 합니다.

## 로컬 애플리케이션 실행

### 방법 1. 기본값으로 바로 실행

로컬 프로필은 기본 DB 접속값과 로컬용 보안값을 가지고 있어서, 별도 환경변수 없이도 실행할 수 있습니다.
`bootRun`은 실행 전에 관리자 React 앱을 빌드해 Spring Boot 정적 리소스로 복사합니다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기본 웹 포트는 `50506`입니다.
다른 포트로 실행하려면 `SERVER_PORT`를 함께 지정합니다.

```bash
SERVER_PORT=18080 SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

### 방법 2. `.env` 파일로 실행

환경변수를 매번 입력하지 않으려면 예시 파일을 복사해 `.env`를 만듭니다.

```bash
cp .env.example .env
```

필요하면 `.env` 값을 수정합니다.

```bash
vi .env
```

현재 터미널에 `.env` 값을 로드합니다.

```bash
set -a
source .env
set +a
```

그 다음 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

`.env` 파일은 `.gitignore`에 포함되어 있으므로 커밋되지 않습니다.
공유용 예시는 `.env.example`만 관리합니다.

### 구동에 필요한 환경변수

이미 관리자 계정이 있는 로컬 Docker DB를 그대로 쓰는 경우 최소 환경변수는 `SPRING_PROFILES_ACTIVE=local`입니다.
새 DB에서는 `.env.example`처럼 초기 관리자 계정 환경변수도 함께 지정합니다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

`.env.example`처럼 `.env`를 만들어 실행할 때 사용하는 전체 환경변수는 다음과 같습니다.

| 환경변수 | 로컬 필수 여부 | 비로컬 필수 여부 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 필수 | 선택 | 없음 | 로컬 실행 시 `local`로 설정합니다. |
| `SERVER_PORT` | 선택 | 선택 | `50506` | 애플리케이션 HTTP 포트입니다. |
| `DATABASE_URL` | 선택 | 권장 | local: `jdbc:postgresql://localhost:5433/my_data`, 그 외: `jdbc:postgresql://localhost:5432/my_data` | PostgreSQL 접속 URL입니다. |
| `DATABASE_USERNAME` | 선택 | 권장 | `my_data` | PostgreSQL 사용자명입니다. |
| `DATABASE_PASSWORD` | 선택 | 권장 | `my_data` | PostgreSQL 비밀번호입니다. |
| `ADMIN_BOOTSTRAP_EMAIL` | 권장 | 조건부 필수 | 없음 | 삭제되지 않은 관리자 계정이 없을 때 생성할 초기 관리자 이메일입니다. |
| `ADMIN_BOOTSTRAP_PASSWORD` | 권장 | 조건부 필수 | 없음 | 삭제되지 않은 관리자 계정이 없을 때 생성할 초기 관리자 비밀번호입니다. |
| `ADMIN_BOOTSTRAP_DISPLAY_NAME` | 선택 | 선택 | `관리자` | 초기 관리자 표시 이름입니다. |
| `SLACK_SOCKET_MODE_ENABLED` | 선택 | 선택 | `false` | Slack Socket Mode 수신부를 켤지 결정합니다. Slack을 붙여 로컬 개발할 때 `true`로 설정합니다. |
| `SLACK_HTTP_EVENTS_ENABLED` | 선택 | 선택 | `false` | `/slack/events` HTTP Events API 엔드포인트를 켤지 결정합니다. |
| `SLACK_SIGNING_SECRET` | 조건부 필수 | 조건부 필수 | local: `local-signing-secret`, 그 외: 없음 | `SLACK_HTTP_EVENTS_ENABLED=true`일 때 Slack HTTP Events API 요청 서명 검증에 사용합니다. |
| `SLACK_APP_TOKEN` | 조건부 필수 | 조건부 필수 | 없음 | `SLACK_SOCKET_MODE_ENABLED=true`일 때 Socket Mode WebSocket 연결에 사용할 App-Level Token입니다. 보통 `xapp-`로 시작합니다. |
| `SLACK_BOT_TOKEN` | 조건부 필수 | 조건부 필수 | 없음 | `SLACK_SOCKET_MODE_ENABLED=true`일 때 이벤트 처리와 이후 답변 전송에 사용할 Bot User OAuth Token입니다. 보통 `xoxb-`로 시작합니다. |
| `EMBEDDING_MODEL` | 선택 | 선택 | `deterministic-1536` | 임베딩 모델 이름입니다. 현재 로컬/테스트용 결정적 임베딩 클라이언트에서 사용합니다. |

`local` 프로필은 개발 편의를 위해 HTTP Events API용 로컬 기본 signing secret을 제공합니다.
기본값에서는 Slack 수신 방식이 모두 꺼져 있습니다.
Slack을 로컬에서 붙여 테스트할 때는 Socket Mode를 켜고 `SLACK_APP_TOKEN`, `SLACK_BOT_TOKEN`을 실제 값으로 설정합니다.

## Slack 앱 설정

초기 개발은 Socket Mode로 진행합니다.
Socket Mode는 애플리케이션이 Slack으로 WebSocket 연결을 열어 이벤트를 받는 방식이라, 로컬 개발 중에 ngrok 같은 공개 HTTPS 터널을 매번 열지 않아도 됩니다.
추후 배포 규모가 커지거나 공개 HTTPS 엔드포인트 운영이 자연스러워지면 HTTP Events API 방식으로 전환할 수 있습니다.

현재 코드는 Socket Mode 수신부를 포함하고 있습니다.
`SLACK_SOCKET_MODE_ENABLED=true`로 실행하면 앱이 Slack으로 WebSocket 연결을 열고 `app_mention`, `message.im` 이벤트를 내부 질문 이벤트로 변환합니다.
`/slack/events` HTTP endpoint는 추후 전환을 위한 선택 기능으로 남겨두었고, `SLACK_HTTP_EVENTS_ENABLED=true`일 때만 활성화됩니다.

### 1. Slack 앱 생성

[Slack 앱 관리 화면](https://api.slack.com/apps)에서 새 앱을 만들거나 기존 앱을 엽니다.
처음 만드는 경우 `Create New App`에서 `From an app manifest`를 선택하면 설정을 빠르게 맞출 수 있습니다.

초기 개발용 manifest 예시는 다음과 같습니다.

```json
{
  "display_information": {
    "name": "My Data",
    "description": "개인 데이터 RAG 챗봇",
    "background_color": "#2563eb"
  },
  "features": {
    "bot_user": {
      "display_name": "My Data",
      "always_online": false
    }
  },
  "oauth_config": {
    "scopes": {
      "bot": [
        "app_mentions:read",
        "chat:write",
        "im:history"
      ]
    }
  },
  "settings": {
    "event_subscriptions": {
      "bot_events": [
        "app_mention",
        "message.im"
      ]
    },
    "org_deploy_enabled": false,
    "socket_mode_enabled": true,
    "token_rotation_enabled": false
  }
}
```

manifest를 사용하지 않고 화면에서 직접 설정해도 됩니다.
직접 설정하는 경우 아래 단계를 그대로 따라갑니다.

### 2. Socket Mode App Token 발급

Slack 앱 설정에서 `Basic Information`으로 이동합니다.
`App-Level Tokens` 영역에서 `Generate Token and Scopes`를 누릅니다.

토큰 이름은 예를 들어 `socket-mode`로 입력하고, scope는 다음을 추가합니다.

- `connections:write`: Socket Mode WebSocket 연결을 열 때 필요합니다.

생성된 App-Level Token을 복사합니다.
실제 값은 보통 `xapp-`로 시작합니다.

`.env`에는 다음처럼 넣습니다.

```bash
SLACK_SOCKET_MODE_ENABLED=true
SLACK_APP_TOKEN=복사한_App_Level_Token
```

### 3. Bot User OAuth Token 준비

Slack 앱 설정에서 `OAuth & Permissions`로 이동합니다.
`Bot Token Scopes`에 다음 scope를 추가합니다.

- `app_mentions:read`: 채널에서 앱을 멘션한 질문 이벤트를 받습니다.
- `chat:write`: Slack 채널이나 스레드에 답변 메시지를 보냅니다.
- `im:history`: DM 질문 이벤트까지 받을 때 추가합니다.

scope를 바꾼 뒤 `Install to Workspace` 또는 `Reinstall to Workspace`를 실행합니다.
설치가 끝나면 같은 화면의 `Bot User OAuth Token` 값을 복사합니다.
실제 값은 보통 `xoxb-`로 시작합니다.

`.env`에는 다음처럼 넣습니다.

```bash
SLACK_BOT_TOKEN=복사한_Bot_User_OAuth_Token
```

Socket Mode로 Slack 이벤트를 받으려면 `.env`에 최소 다음 값이 있어야 합니다.

```bash
SLACK_SOCKET_MODE_ENABLED=true
SLACK_APP_TOKEN=xapp-...
SLACK_BOT_TOKEN=xoxb-...
```

### 4. Socket Mode와 이벤트 구독 확인

Slack 앱 설정에서 `Socket Mode`가 켜져 있는지 확인합니다.
manifest를 사용했다면 이미 켜져 있습니다.
직접 설정한다면 `Socket Mode` 메뉴에서 `Enable Socket Mode`를 켭니다.

Slack 앱 설정에서 `Event Subscriptions`를 켜고, `Subscribe to bot events`에 다음 이벤트를 추가합니다.

- `app_mention`: 채널에서 앱을 멘션한 질문을 받을 때 사용합니다.
- `message.im`: 앱 DM으로 질문을 받을 때 사용합니다.

Socket Mode에서는 `Request URL`을 입력하지 않습니다.
앱이 Slack으로 연결을 열기 때문에 Slack이 내 로컬 서버로 직접 HTTP 요청을 보낼 필요가 없습니다.

### 5. 채널 초대

채널에서 앱을 멘션하려면 앱이 해당 채널에 들어와 있어야 합니다.
Slack 채널에서 다음처럼 초대합니다.

```text
/invite @My Data
```

DM으로 질문하려면 Slack 앱의 `App Home` 또는 DM 화면에서 앱에게 메시지를 보냅니다.

### 6. 현재 코드와 후속 전환 메모

현재 Socket Mode 이벤트 수신부는 Slack 질문을 내부 질문 이벤트로 변환하고 로그로 남기는 기반 단계입니다.
Slack으로 실제 RAG 답변을 보내는 흐름은 Slack 유저와 내부 유저/권한 매핑을 정한 뒤 연결합니다.

추후 운영 규모가 커지고 공개 HTTPS endpoint를 안정적으로 운영하게 되면 HTTP Events API로 전환할 수 있습니다.
그때는 `Socket Mode`를 끄고 `Event Subscriptions`의 `Request URL`을 다음 형식으로 설정합니다.

```text
https://내_도메인/slack/events
```

HTTP Events API 방식에서는 `SLACK_SIGNING_SECRET`으로 Slack 요청 서명을 검증합니다.
이 방식을 켤 때는 다음 값을 설정합니다.

```bash
SLACK_SOCKET_MODE_ENABLED=false
SLACK_HTTP_EVENTS_ENABLED=true
SLACK_SIGNING_SECRET=Slack_앱_Signing_Secret
```

## 관리자 콘솔

관리자 화면은 React로 만들고, 빌드 결과물을 Spring Boot가 같은 서버에서 정적 리소스로 서빙합니다.
로컬 애플리케이션을 실행한 뒤 다음 주소로 접속합니다.

```text
http://localhost:50506/admin-ui
```

관리자 로그인 화면은 다음 주소에서도 직접 열 수 있습니다.

```text
http://localhost:50506/admin-ui/login
```

관리자 화면은 세션 기반 로그인을 사용합니다. 삭제되지 않은 관리자 계정이 없는 새 DB에서는 다음 환경변수로 초기 관리자 계정을 생성합니다.

```bash
ADMIN_BOOTSTRAP_EMAIL=admin@example.com
ADMIN_BOOTSTRAP_PASSWORD=change-me
ADMIN_BOOTSTRAP_DISPLAY_NAME=관리자
```

관리자 API는 GraphQL로 통신하며 엔드포인트는 다음과 같습니다.

```text
/admin/graphql
```

관리자 GraphQL mutation은 관리자 세션과 CSRF 토큰이 필요합니다. 이전의 `ADMIN_API_TOKEN` 기반 관리자 API 방식은 제거되었습니다.

### 관리자 화면 기능

현재 관리자 화면은 다음 기능을 제공합니다.

- 대시보드: 연결된 데이터소스 수, 진행 중 수집 job 수, 관리 대상 유저 수를 확인하고 새로고침할 수 있습니다.
- 데이터소스 관리: 데이터소스를 추가, 수정, 소프트 삭제하고 수동 수집을 요청할 수 있습니다.
- 데이터소스 수집 상태: 데이터소스 목록에서 마지막 수집 시간을 확인하고, 선택한 데이터소스의 수집 기록을 볼 수 있습니다.
- 유저 관리: 유저를 추가, 수정, 비활성화, 소프트 삭제, 복구하고 임시 비밀번호를 재설정할 수 있습니다.
- 권한 관리 기반 정보: 데이터소스에는 소유 유저, 가시성, 수집 방식이 저장되며 이후 ACL 기반 검색/답변 범위 제어에 사용됩니다.

현재 관리자 화면에서 직접 생성 가능한 데이터소스는 테스트/로컬 수집용 `LOCAL_TEXT`입니다.
`SLACK`, `NOTION`, `GOOGLE_DRIVE` 타입은 스키마와 화면 선택지는 준비되어 있지만 실제 외부 API 연동 커넥터는 후속 단계에서 붙입니다.

프론트엔드만 개발 서버로 실행하려면 백엔드를 `50506` 포트로 먼저 띄운 뒤 다음 명령을 사용합니다.

```bash
cd frontend/admin
npm ci
npm run codegen
npm run dev
```

Vite 개발 서버는 기본적으로 `http://localhost:61263/admin-ui`에서 열리고, `/admin/auth/**`와 `/admin/graphql` 요청을 Spring Boot 서버로 프록시합니다.

## 비로컬 실행

`local` 또는 `test` 프로필이 아닌 환경에서는 보안값을 반드시 직접 지정해야 합니다.
Slack Socket Mode를 켜면 App-Level Token과 Bot User OAuth Token이 필요합니다.
Slack HTTP Events API를 켜면 signing secret이 필요하고, 로컬 기본 signing secret은 사용할 수 없습니다.
삭제되지 않은 관리자 계정이 아직 없다면 초기 관리자 이메일과 비밀번호도 함께 지정해야 합니다.

```bash
ADMIN_BOOTSTRAP_EMAIL=admin@example.com \
ADMIN_BOOTSTRAP_PASSWORD=초기_관리자_비밀번호 \
ADMIN_BOOTSTRAP_DISPLAY_NAME=관리자 \
SLACK_SOCKET_MODE_ENABLED=true \
SLACK_APP_TOKEN=xapp-... \
SLACK_BOT_TOKEN=xoxb-... \
DATABASE_URL=jdbc:postgresql://localhost:5433/my_data \
DATABASE_USERNAME=my_data \
DATABASE_PASSWORD=my_data \
./gradlew bootRun
```

## DB 형상관리

DB 스키마는 Liquibase로 관리합니다.
변경 이력 파일은 다음 위치에 있습니다.

```text
src/main/resources/db/changelog/db.changelog-master.sql
```

현재 로컬 DB의 Liquibase 적용 상태는 다음 명령으로 확인할 수 있습니다.

```bash
docker exec my-data-postgres psql -U my_data -d my_data \
  -c "SELECT id, exectype FROM databasechangelog ORDER BY orderexecuted;"
```

## 현재 구현 범위

- PostgreSQL + pgvector 스키마
- Liquibase DB 마이그레이션
- ACL 권한 주체 모델
- 수동 수집 job API
- 기반 테스트용 `LOCAL_TEXT` 커넥터
- 테스트/로컬 개발용 결정적 임베딩 클라이언트
- ACL 필터가 적용된 벡터 검색
- 출처 저장을 포함한 채팅 답변 흐름
- Slack Socket Mode 수신 기반과 선택형 HTTP Events API 엔드포인트
- 세션 기반 관리자 로그인과 GraphQL 관리자 API
- React 관리자 콘솔 정적 서빙
- 관리자 화면의 대시보드, 데이터소스 관리, 수동 수집 요청, 수집 기록 조회
- 관리자 화면의 유저 생성, 수정, 비활성화, 소프트 삭제, 복구, 비밀번호 초기화
