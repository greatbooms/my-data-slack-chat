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

그 다음 애플리케이션을 실행합니다. Gradle `bootRun`은 루트 `.env` 파일을 자동으로 읽어 Spring Boot 프로세스 환경변수로 넘깁니다.

```bash
./gradlew bootRun
```

같은 키가 터미널 환경변수와 `.env`에 함께 있으면 터미널 환경변수가 우선합니다.
`.env` 파일은 `.gitignore`에 포함되어 있으므로 커밋되지 않습니다.
공유용 예시는 `.env.example`만 관리합니다.

### 환경변수 요약

이미 관리자 계정이 있는 로컬 Docker DB를 그대로 쓰는 경우 최소 설정은 `.env`의 `SPRING_PROFILES_ACTIVE=local`입니다.
새 DB에서 관리자 계정을 자동 생성하려면 `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`도 함께 지정합니다.
전체 환경변수 예시와 필수/선택 표기는 [.env.example](.env.example)을 기준으로 확인합니다.

## 외부 연동 설정 모음

필요한 연동만 선택해서 설정합니다. 환경변수 예시는 [.env.example](.env.example)에 모아두었습니다.

| 연동 | 필요한 상황 | 핵심 설정 | 문서 |
| --- | --- | --- | --- |
| Slack Socket Mode | 로컬에서 Slack 이벤트를 받을 때 | `SLACK_SOCKET_MODE_ENABLED`, `SLACK_APP_TOKEN`, `SLACK_BOT_TOKEN` | [Slack 앱 설정](docs/slack-app-setup.md) |
| Slack HTTP Events API | 공개 HTTPS endpoint로 Slack 이벤트를 받을 때 | `SLACK_HTTP_EVENTS_ENABLED`, `SLACK_SIGNING_SECRET` | [Slack 앱 설정](docs/slack-app-setup.md#http-events-api로-전환할-때) |
| Notion | Notion 페이지를 수집할 때 | `NOTION_API_TOKEN`, `Notion 루트 페이지 ID` | [Notion Integration 키 발급과 페이지 연결](docs/notion-integration-setup.md) |

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

현재 관리자 화면에서 직접 생성 가능한 데이터소스는 테스트/로컬 수집용 `LOCAL_TEXT`와 Notion 페이지 수집용 `NOTION`입니다.
`SLACK`, `GOOGLE_DRIVE` 타입은 스키마와 화면 선택지는 준비되어 있지만 실제 데이터 수집 커넥터는 후속 단계에서 붙입니다.

프론트엔드만 개발 서버로 실행하려면 백엔드를 `50506` 포트로 먼저 띄운 뒤 다음 명령을 사용합니다.

```bash
cd frontend/admin
npm ci
npm run codegen
npm run dev
```

Vite 개발 서버는 기본적으로 `http://localhost:61263/admin-ui`에서 열리고, `/admin/auth/**`와 `/admin/graphql` 요청을 Spring Boot 서버로 프록시합니다.

## 비로컬 실행

`local` 또는 `test` 프로필이 아닌 환경에서는 DB와 보안값을 직접 지정해야 합니다.
삭제되지 않은 관리자 계정이 아직 없다면 초기 관리자 이메일과 비밀번호도 함께 지정합니다.
Slack, Notion 같은 외부 연동 값은 필요한 기능을 켤 때만 추가합니다.

```bash
ADMIN_BOOTSTRAP_EMAIL=admin@example.com \
ADMIN_BOOTSTRAP_PASSWORD=초기_관리자_비밀번호 \
ADMIN_BOOTSTRAP_DISPLAY_NAME=관리자 \
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
- Notion 페이지 수집 커넥터
- 테스트/로컬 개발용 결정적 임베딩 클라이언트
- ACL 필터가 적용된 벡터 검색
- 출처 저장을 포함한 채팅 답변 흐름
- Slack Socket Mode 수신 기반과 선택형 HTTP Events API 엔드포인트
- 세션 기반 관리자 로그인과 GraphQL 관리자 API
- React 관리자 콘솔 정적 서빙
- 관리자 화면의 대시보드, 데이터소스 관리, 수동 수집 요청, 수집 기록 조회
- 관리자 화면의 유저 생성, 수정, 비활성화, 소프트 삭제, 복구, 비밀번호 초기화
