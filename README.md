# My Data

Spring Boot 기반 개인 데이터 RAG 챗봇 기반 프로젝트입니다.

## 사전 준비

- Java 21
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

## 로컬 애플리케이션 실행

### 방법 1. 기본값으로 바로 실행

로컬 프로필은 기본 DB 접속값과 로컬용 보안값을 가지고 있어서, 별도 환경변수 없이도 실행할 수 있습니다.

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
| `SLACK_SIGNING_SECRET` | 선택 | 필수 | local: `local-signing-secret` | Slack Events API 요청 서명 검증에 사용하는 시크릿입니다. |
| `EMBEDDING_MODEL` | 선택 | 선택 | `deterministic-1536` | 임베딩 모델 이름입니다. 현재 로컬/테스트용 결정적 임베딩 클라이언트에서 사용합니다. |

`local` 프로필은 개발 편의를 위해 로컬 전용 기본 보안값을 제공합니다.
그래도 실제 Slack 앱을 연결할 때는 `.env`의 `SLACK_SIGNING_SECRET` 값을 Slack 앱 관리 화면의 Signing Secret 값으로 바꾸는 것을 권장합니다.

## 비로컬 실행

`local` 또는 `test` 프로필이 아닌 환경에서는 보안값을 반드시 직접 지정해야 합니다.
Slack 서명 시크릿 값이 없거나 비어 있거나 로컬 기본값이면 애플리케이션이 시작되지 않습니다.
삭제되지 않은 관리자 계정이 아직 없다면 초기 관리자 이메일과 비밀번호도 함께 지정해야 합니다.

```bash
ADMIN_BOOTSTRAP_EMAIL=admin@example.com \
ADMIN_BOOTSTRAP_PASSWORD=초기_관리자_비밀번호 \
ADMIN_BOOTSTRAP_DISPLAY_NAME=관리자 \
SLACK_SIGNING_SECRET=Slack_앱_서명_시크릿 \
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
- Slack 이벤트 엔드포인트 기반
