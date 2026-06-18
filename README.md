# My Data

Spring Boot 기반 개인 데이터 RAG 챗봇 기반 프로젝트입니다.

## 사전 준비

- Java 21
- Docker Desktop 또는 Docker Engine
- 프로젝트 경로: `/Users/eric/workspace/my-data/.worktrees/mvp-foundation`

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

로컬 프로필로 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기본 웹 포트는 Spring Boot 기본값인 `8080`입니다.
다른 포트로 실행하려면 `SERVER_PORT`를 함께 지정합니다.

```bash
SERVER_PORT=18080 SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

`local` 프로필은 개발 편의를 위해 로컬 전용 기본 보안값을 제공합니다.

- `ADMIN_API_TOKEN`: 로컬 관리자 API 토큰
- `SLACK_SIGNING_SECRET`: 로컬 Slack 서명 검증 시크릿

## 비로컬 실행

`local` 또는 `test` 프로필이 아닌 환경에서는 보안값을 반드시 직접 지정해야 합니다.
값이 없거나 비어 있거나 로컬 기본값이면 애플리케이션이 시작되지 않습니다.

```bash
ADMIN_API_TOKEN=원하는_관리자_토큰 \
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
