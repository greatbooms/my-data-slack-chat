# My Data

Spring Boot based personal data RAG chatbot foundation.

## Local DB

```bash
docker compose up -d postgres
```

The local PostgreSQL/pgvector container is exposed on `localhost:5433` to avoid
colliding with other local PostgreSQL services.

## Test

```bash
./gradlew test
```

## Local Run

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Local schema changes are managed by Liquibase.

## Current Scope

- PostgreSQL + pgvector schema
- Liquibase database migrations
- ACL principal model
- Manual ingestion job API
- LOCAL_TEXT connector for foundation tests
- Deterministic embedding client for tests/local development
- ACL-filtered vector retrieval
- Chat answer orchestration with citation persistence
- Slack event endpoint foundation
