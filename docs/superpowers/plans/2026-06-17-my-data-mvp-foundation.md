# My Data MVP Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot 기반에서 PostgreSQL/pgvector 스키마, ACL 모델, 수동 수집 job, 권한 필터 retrieval, Slack 질문 처리 뼈대까지 검증 가능한 첫 end-to-end 기반을 만든다.

**Architecture:** 첫 구현은 모듈러 모놀리스다. 실제 Google Drive/Notion/Slack 데이터 수집은 후속 계획으로 분리하고, 이번 계획에서는 동일한 커넥터 계약을 사용하는 `LOCAL_TEXT` 테스트 커넥터로 수집-청킹-임베딩-권한 검색-채팅 응답 경로를 먼저 고정한다. JPA는 도메인 저장에 사용하고, pgvector 검색은 `JdbcTemplate` native SQL repository로 분리한다.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Gradle, PostgreSQL + pgvector, Flyway, Spring Data JPA, Spring Web, Spring Validation, JdbcTemplate, JUnit 5, Testcontainers, MockMvc

---

## Scope Check

설계 스펙은 Google Drive, Notion, Slack 데이터소스까지 포함한다. 이들은 OAuth, provider API, 파일 파싱, provider별 권한 해석이 독립적으로 큰 하위 시스템이므로 별도 구현 계획으로 나눈다.

이번 계획의 완료 기준은 다음이다.

- Spring Boot 프로젝트가 생성된다.
- DB schema와 pgvector extension이 Flyway로 생성된다.
- principal key와 ACL 모델이 테스트된다.
- 수동 수집 job API가 생성된다.
- `LOCAL_TEXT` 커넥터를 통해 문서, ACL, 청크, 임베딩이 저장된다.
- 검색은 질문자 principal key로 필터링된다.
- Slack endpoint는 signature 검증과 URL verification 응답 경로를 갖는다.
- 실제 외부 provider 없이도 테스트로 end-to-end 흐름을 검증한다.

후속 계획:

- Google Drive connector implementation plan
- Notion connector implementation plan
- Slack source ingestion implementation plan
- Real LLM/embedding provider integration plan

## Reference Notes

- Spring Boot Gradle plugin current docs state that the plugin supports Gradle 8.x 8.14+ or 9.x.
- Spring Boot system requirements current docs state that Spring Boot 4.1.0 requires Java 17+.
- Use Java 21 as the project runtime because it is a current LTS and satisfies the Spring Boot requirement.

## File Structure

Create or modify these files.

```text
settings.gradle
build.gradle
docker-compose.yml
.gitignore
src/main/java/com/mydata/MyDataApplication.java
src/main/resources/application.yml
src/main/resources/application-local.yml
src/test/resources/application-test.yml
src/main/resources/db/migration/V1__initial_schema.sql

src/main/java/com/mydata/common/domain/BaseEntity.java
src/main/java/com/mydata/common/json/JsonMaps.java
src/main/java/com/mydata/auth/Permission.java
src/main/java/com/mydata/auth/PrincipalKeys.java

src/main/java/com/mydata/users/UserEntity.java
src/main/java/com/mydata/users/UserRepository.java
src/main/java/com/mydata/workspaces/WorkspaceEntity.java
src/main/java/com/mydata/workspaces/WorkspaceRepository.java
src/main/java/com/mydata/datasources/DataSourceEntity.java
src/main/java/com/mydata/datasources/DataSourceRepository.java
src/main/java/com/mydata/datasources/DataSourceType.java
src/main/java/com/mydata/datasources/DataSourceStatus.java
src/main/java/com/mydata/datasources/SyncMode.java
src/main/java/com/mydata/documents/ExternalDocumentEntity.java
src/main/java/com/mydata/documents/ExternalDocumentRepository.java
src/main/java/com/mydata/documents/DocumentAclEntryEntity.java
src/main/java/com/mydata/documents/DocumentChunkEntity.java
src/main/java/com/mydata/documents/DocumentChunkRepository.java
src/main/java/com/mydata/embeddings/DocumentEmbeddingRepository.java
src/main/java/com/mydata/embeddings/EmbeddingClient.java
src/main/java/com/mydata/embeddings/DeterministicEmbeddingClient.java
src/main/java/com/mydata/ingestion/IngestionJobEntity.java
src/main/java/com/mydata/ingestion/IngestionJobRepository.java
src/main/java/com/mydata/ingestion/IngestionCommandService.java
src/main/java/com/mydata/ingestion/IngestionWorker.java
src/main/java/com/mydata/ingestion/IngestionPipelineService.java
src/main/java/com/mydata/ingestion/Chunker.java
src/main/java/com/mydata/connectors/core/DataSourceConnector.java
src/main/java/com/mydata/connectors/core/DocumentHandler.java
src/main/java/com/mydata/connectors/core/RawAclEntry.java
src/main/java/com/mydata/connectors/core/RawContent.java
src/main/java/com/mydata/connectors/core/RawExternalDocument.java
src/main/java/com/mydata/connectors/core/SyncCursor.java
src/main/java/com/mydata/connectors/local/LocalTextConnector.java
src/main/java/com/mydata/retrieval/RetrievedChunk.java
src/main/java/com/mydata/retrieval/PgVectorSearchRepository.java
src/main/java/com/mydata/retrieval/RetrievalService.java
src/main/java/com/mydata/chat/ChatApplicationService.java
src/main/java/com/mydata/chat/ChatMessageEntity.java
src/main/java/com/mydata/chat/ChatMessageRepository.java
src/main/java/com/mydata/chat/ChatRetrievalCitationEntity.java
src/main/java/com/mydata/chat/ChatSessionEntity.java
src/main/java/com/mydata/chat/ChatSessionRepository.java
src/main/java/com/mydata/chat/LlmClient.java
src/main/java/com/mydata/chat/StubLlmClient.java
src/main/java/com/mydata/slackbot/SlackEventController.java
src/main/java/com/mydata/slackbot/SlackSignatureVerifier.java
src/main/java/com/mydata/admin/AdminDataSourceController.java

src/test/java/com/mydata/support/PostgresIntegrationTest.java
src/test/java/com/mydata/database/FlywayMigrationTest.java
src/test/java/com/mydata/auth/PrincipalKeysTest.java
src/test/java/com/mydata/documents/CorePersistenceTest.java
src/test/java/com/mydata/admin/AdminDataSourceControllerTest.java
src/test/java/com/mydata/ingestion/IngestionPipelineIntegrationTest.java
src/test/java/com/mydata/retrieval/PgVectorSearchRepositoryTest.java
src/test/java/com/mydata/chat/ChatApplicationServiceTest.java
src/test/java/com/mydata/slackbot/SlackSignatureVerifierTest.java
src/test/java/com/mydata/slackbot/SlackEventControllerTest.java
```

## Task 1: Spring Boot 프로젝트 골격 생성

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `.gitignore`
- Create: `src/main/java/com/mydata/MyDataApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-local.yml`
- Create: `src/test/resources/application-test.yml`

- [ ] **Step 1: Spring Boot Gradle 프로젝트 파일 작성**

Create `settings.gradle`:

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'my-data'
```

Create `build.gradle`:

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
}

group = 'com.mydata'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

dependencies {
    implementation platform('org.springframework.boot:spring-boot-dependencies:4.1.0')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    runtimeOnly 'org.postgresql:postgresql'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

Create `.gitignore`:

```gitignore
.gradle/
build/
out/
*.iml
.idea/
.DS_Store
.env
```

- [ ] **Step 2: 애플리케이션 진입점 작성**

Create `src/main/java/com/mydata/MyDataApplication.java`:

```java
package com.mydata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MyDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyDataApplication.class, args);
    }
}
```

- [ ] **Step 3: 기본 설정 파일 작성**

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: my-data
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/my_data}
    username: ${DATABASE_USERNAME:my_data}
    password: ${DATABASE_PASSWORD:my_data}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true

my-data:
  admin-token: ${ADMIN_API_TOKEN:local-admin-token}
  slack:
    signing-secret: ${SLACK_SIGNING_SECRET:local-signing-secret}
  embedding:
    model: ${EMBEDDING_MODEL:deterministic-1536}
    dimensions: 1536
```

Create `src/main/resources/application-local.yml`:

```yaml
spring:
  config:
    activate:
      on-profile: local
logging:
  level:
    com.mydata: DEBUG
```

Create `src/test/resources/application-test.yml`:

```yaml
spring:
  config:
    activate:
      on-profile: test
  jpa:
    hibernate:
      ddl-auto: validate
my-data:
  admin-token: test-admin-token
  slack:
    signing-secret: test-signing-secret
  embedding:
    model: deterministic-1536
    dimensions: 1536
```

- [ ] **Step 4: Gradle wrapper 생성**

Run:

```bash
gradle wrapper --gradle-version 9.0.0
```

Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/*` are created.

- [ ] **Step 5: 빈 테스트 실행**

Run:

```bash
./gradlew test
```

Expected: build succeeds with no tests or generated context test passing.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle build.gradle .gitignore gradlew gradlew.bat gradle src/main src/test
git commit -m "chore: scaffold Spring Boot application"
```

## Task 2: 로컬 PostgreSQL/pgvector 실행 환경 추가

**Files:**
- Create: `docker-compose.yml`
- Modify: `src/main/resources/application-local.yml`

- [ ] **Step 1: docker-compose 작성**

Create `docker-compose.yml`:

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    container_name: my-data-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: my_data
      POSTGRES_USER: my_data
      POSTGRES_PASSWORD: my_data
    volumes:
      - my_data_postgres:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U my_data -d my_data"]
      interval: 5s
      timeout: 3s
      retries: 20

volumes:
  my_data_postgres:
```

- [ ] **Step 2: local profile DB 설정 명시**

Ensure `src/main/resources/application-local.yml` contains:

```yaml
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:postgresql://localhost:5432/my_data
    username: my_data
    password: my_data
logging:
  level:
    com.mydata: DEBUG
```

- [ ] **Step 3: DB 컨테이너 실행 검증**

Run:

```bash
docker compose up -d postgres
docker compose ps
```

Expected: `my-data-postgres` is `healthy` or `running`.

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml src/main/resources/application-local.yml
git commit -m "chore: add local pgvector database"
```

## Task 3: Flyway 초기 스키마와 migration 테스트

**Files:**
- Create: `src/main/resources/db/migration/V1__initial_schema.sql`
- Create: `src/test/java/com/mydata/support/PostgresIntegrationTest.java`
- Create: `src/test/java/com/mydata/database/FlywayMigrationTest.java`

- [ ] **Step 1: 실패하는 migration 테스트 작성**

Create `src/test/java/com/mydata/support/PostgresIntegrationTest.java`:

```java
package com.mydata.support;

import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
        .withDatabaseName("my_data_test")
        .withUsername("my_data")
        .withPassword("my_data");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

Create `src/test/java/com/mydata/database/FlywayMigrationTest.java`:

```java
package com.mydata.database;

import com.mydata.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends PostgresIntegrationTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void createsRequiredTablesAndVectorExtension() {
        Integer tableCount = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name IN (
                'users',
                'workspaces',
                'data_sources',
                'external_documents',
                'document_acl_entries',
                'document_chunks',
                'document_embeddings',
                'ingestion_jobs',
                'chat_sessions',
                'chat_messages'
              )
            """, Integer.class);

        String vectorVersion = jdbcTemplate.queryForObject(
            "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
            String.class
        );

        assertThat(tableCount).isEqualTo(10);
        assertThat(vectorVersion).isNotBlank();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests com.mydata.database.FlywayMigrationTest
```

Expected: FAIL because the migration file does not exist or required tables are missing.

- [ ] **Step 3: 초기 schema migration 작성**

Create `src/main/resources/db/migration/V1__initial_schema.sql` with the schema from the approved design:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES users(id),
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspace_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, user_id)
);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    provider TEXT NOT NULL,
    external_workspace_id TEXT NOT NULL,
    external_user_id TEXT NOT NULL,
    email TEXT,
    display_name TEXT,
    principal_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, external_workspace_id, external_user_id)
);

CREATE TABLE data_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    type TEXT NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL,
    sync_mode TEXT NOT NULL,
    sync_cron TEXT,
    sync_cursor_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    credentials_ref TEXT,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE data_source_access_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source_id UUID NOT NULL REFERENCES data_sources(id) ON DELETE CASCADE,
    principal_key TEXT NOT NULL,
    permission TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (data_source_id, principal_key, permission)
);

CREATE TABLE external_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    data_source_id UUID NOT NULL REFERENCES data_sources(id),
    external_id TEXT NOT NULL,
    source_type TEXT NOT NULL,
    title TEXT NOT NULL,
    uri TEXT,
    mime_type TEXT,
    author TEXT,
    external_created_at TIMESTAMPTZ,
    external_updated_at TIMESTAMPTZ,
    content_hash TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (data_source_id, external_id)
);

CREATE TABLE document_acl_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES external_documents(id) ON DELETE CASCADE,
    principal_key TEXT NOT NULL,
    permission TEXT NOT NULL,
    source TEXT NOT NULL,
    inherited BOOLEAN NOT NULL DEFAULT false,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, principal_key, permission)
);

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES external_documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

CREATE TABLE document_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id UUID NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    embedding_model TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (chunk_id, embedding_model)
);

CREATE TABLE ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    data_source_id UUID NOT NULL REFERENCES data_sources(id),
    trigger_type TEXT NOT NULL,
    status TEXT NOT NULL,
    requested_by_user_id UUID REFERENCES users(id),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ingestion_job_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES ingestion_jobs(id) ON DELETE CASCADE,
    external_id TEXT,
    document_id UUID REFERENCES external_documents(id),
    status TEXT NOT NULL,
    reason TEXT,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    channel_type TEXT NOT NULL,
    external_channel_id TEXT,
    external_thread_id TEXT,
    created_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_retrieval_citations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    chunk_id UUID NOT NULL REFERENCES document_chunks(id),
    rank INTEGER NOT NULL,
    score DOUBLE PRECISION,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_data_sources_workspace ON data_sources(workspace_id);
CREATE INDEX idx_documents_workspace ON external_documents(workspace_id);
CREATE INDEX idx_documents_source_external ON external_documents(data_source_id, external_id);
CREATE INDEX idx_document_acl_principal ON document_acl_entries(principal_key);
CREATE INDEX idx_document_acl_document ON document_acl_entries(document_id);
CREATE INDEX idx_chunks_document ON document_chunks(document_id);
```

Note: IVFFlat 같은 pgvector approximate index는 대표성 있는 embedding 데이터가 쌓인 뒤 row count 기준으로 `lists`를 선택할 수 있을 때 후속 migration에서 추가한다.

- [ ] **Step 4: migration 테스트 통과 확인**

Run:

```bash
./gradlew test --tests com.mydata.database.FlywayMigrationTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration src/test/java/com/mydata/support src/test/java/com/mydata/database
git commit -m "feat: add initial database schema"
```

## Task 4: Principal key와 권한 값 객체 구현

**Files:**
- Create: `src/main/java/com/mydata/auth/Permission.java`
- Create: `src/main/java/com/mydata/auth/PrincipalKeys.java`
- Create: `src/test/java/com/mydata/auth/PrincipalKeysTest.java`

- [ ] **Step 1: 실패하는 principal key 테스트 작성**

Create `src/test/java/com/mydata/auth/PrincipalKeysTest.java`:

```java
package com.mydata.auth;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrincipalKeysTest {
    @Test
    void createsInternalUserPrincipal() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(PrincipalKeys.user(userId))
            .isEqualTo("USER:11111111-1111-1111-1111-111111111111");
    }

    @Test
    void createsWorkspacePrincipal() {
        UUID workspaceId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThat(PrincipalKeys.workspace(workspaceId))
            .isEqualTo("WORKSPACE:22222222-2222-2222-2222-222222222222");
    }

    @Test
    void createsSlackUserPrincipal() {
        assertThat(PrincipalKeys.slackUser("T123", "U456"))
            .isEqualTo("SLACK_USER:T123:U456");
    }

    @Test
    void createsSlackWorkspacePrincipal() {
        assertThat(PrincipalKeys.slackWorkspace("T123"))
            .isEqualTo("SLACK_WORKSPACE:T123");
    }

    @Test
    void createsSlackChannelPrincipal() {
        assertThat(PrincipalKeys.slackChannel("T123", "C789"))
            .isEqualTo("SLACK_CHANNEL:T123:C789");
    }

    @Test
    void createsGoogleUserPrincipal() {
        assertThat(PrincipalKeys.googleUser("Owner@Example.com"))
            .isEqualTo("GOOGLE_USER:owner@example.com");
    }

    @Test
    void createsGoogleUserPrincipalWithRootLocale() {
        Locale originalLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));

            assertThat(PrincipalKeys.googleUser("INFO@Example.com"))
                .isEqualTo("GOOGLE_USER:info@example.com");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> PrincipalKeys.user(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrincipalKeys.slackUser(null, "U456"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrincipalKeys.slackUser("T123", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrincipalKeys.slackChannel("T123", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PrincipalKeys.googleUser(null))
            .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests com.mydata.auth.PrincipalKeysTest
```

Expected: FAIL because `PrincipalKeys` does not exist.

- [ ] **Step 3: 최소 구현 작성**

Create `src/main/java/com/mydata/auth/Permission.java`:

```java
package com.mydata.auth;

public enum Permission {
    READ
}
```

Create `src/main/java/com/mydata/auth/PrincipalKeys.java`:

```java
package com.mydata.auth;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class PrincipalKeys {
    private PrincipalKeys() {
    }

    public static String user(UUID userId) {
        Objects.requireNonNull(userId, "userId");

        return "USER:" + userId;
    }

    public static String workspace(UUID workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");

        return "WORKSPACE:" + workspaceId;
    }

    public static String slackUser(String slackWorkspaceId, String slackUserId) {
        Objects.requireNonNull(slackWorkspaceId, "slackWorkspaceId");
        Objects.requireNonNull(slackUserId, "slackUserId");

        return "SLACK_USER:" + slackWorkspaceId + ":" + slackUserId;
    }

    public static String slackWorkspace(String slackWorkspaceId) {
        Objects.requireNonNull(slackWorkspaceId, "slackWorkspaceId");

        return "SLACK_WORKSPACE:" + slackWorkspaceId;
    }

    public static String slackChannel(String slackWorkspaceId, String channelId) {
        Objects.requireNonNull(slackWorkspaceId, "slackWorkspaceId");
        Objects.requireNonNull(channelId, "channelId");

        return "SLACK_CHANNEL:" + slackWorkspaceId + ":" + channelId;
    }

    public static String googleUser(String email) {
        Objects.requireNonNull(email, "email");

        return "GOOGLE_USER:" + email.toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run:

```bash
./gradlew test --tests com.mydata.auth.PrincipalKeysTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/mydata/auth src/test/java/com/mydata/auth
git commit -m "feat: add principal key helpers"
```

## Task 5: 핵심 JPA 모델과 저장 테스트

**Files:**
- Create: `src/main/java/com/mydata/common/domain/BaseEntity.java`
- Create: `src/main/java/com/mydata/common/json/JsonMaps.java`
- Create: `src/main/java/com/mydata/users/UserEntity.java`
- Create: `src/main/java/com/mydata/users/UserRepository.java`
- Create: `src/main/java/com/mydata/workspaces/WorkspaceEntity.java`
- Create: `src/main/java/com/mydata/workspaces/WorkspaceRepository.java`
- Create: `src/main/java/com/mydata/datasources/DataSourceType.java`
- Create: `src/main/java/com/mydata/datasources/DataSourceStatus.java`
- Create: `src/main/java/com/mydata/datasources/SyncMode.java`
- Create: `src/main/java/com/mydata/datasources/DataSourceEntity.java`
- Create: `src/main/java/com/mydata/datasources/DataSourceRepository.java`
- Create: `src/main/java/com/mydata/documents/ExternalDocumentEntity.java`
- Create: `src/main/java/com/mydata/documents/ExternalDocumentRepository.java`
- Create: `src/main/java/com/mydata/documents/DocumentAclEntryEntity.java`
- Create: `src/main/java/com/mydata/documents/DocumentChunkEntity.java`
- Create: `src/main/java/com/mydata/documents/DocumentChunkRepository.java`
- Create: `src/test/java/com/mydata/documents/CorePersistenceTest.java`

- [ ] **Step 1: 실패하는 저장 테스트 작성**

Create `src/test/java/com/mydata/documents/CorePersistenceTest.java`:

```java
package com.mydata.documents;

import com.mydata.auth.Permission;
import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CorePersistenceTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired ExternalDocumentRepository documents;
    @Autowired DocumentChunkRepository chunks;

    @Test
    void persistsDocumentWithAclAndChunks() {
        UserEntity user = users.save(UserEntity.create("owner@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Personal"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));

        ExternalDocumentEntity document = documents.save(ExternalDocumentEntity.create(
            workspace.getId(),
            source.getId(),
            "note-1",
            "LOCAL_TEXT",
            "First note",
            "hash-1"
        ));
        document.addAcl(DocumentAclEntryEntity.read(document, PrincipalKeys.user(user.getId()), "MANUAL", false));
        document.addChunk(DocumentChunkEntity.create(document, 0, "hello private note", 3));
        documents.saveAndFlush(document);

        ExternalDocumentEntity reloaded = documents.findById(document.getId()).orElseThrow();

        assertThat(reloaded.getAclEntries()).hasSize(1);
        assertThat(reloaded.getChunks()).hasSize(1);
        assertThat(chunks.findByDocumentIdOrderByChunkIndex(document.getId()))
            .extracting(DocumentChunkEntity::getContent)
            .containsExactly("hello private note");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests com.mydata.documents.CorePersistenceTest
```

Expected: FAIL because entity and repository classes do not exist.

- [ ] **Step 3: 공통 base entity와 JSON helper 작성**

Create `src/main/java/com/mydata/common/domain/BaseEntity.java`:

```java
package com.mydata.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
```

Create `src/main/java/com/mydata/common/json/JsonMaps.java`:

```java
package com.mydata.common.json;

public final class JsonMaps {
    public static final String EMPTY_OBJECT = "{}";

    private JsonMaps() {
    }
}
```

- [ ] **Step 4: 핵심 entity와 repository 작성**

Create the entities and repositories with these exact rules:

- Every entity extends `BaseEntity`.
- Every enum is persisted with `@Enumerated(EnumType.STRING)`.
- JSONB fields are mapped as `String` with `columnDefinition = "jsonb"`.
- `ExternalDocumentEntity` owns `aclEntries` and `chunks` through `@OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)`.
- `DocumentAclEntryEntity.read(...)` stores permission as `Permission.READ`.
- `DocumentChunkRepository` exposes `List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(UUID documentId)`.

Required enum contents:

```java
package com.mydata.datasources;

public enum DataSourceType {
    LOCAL_TEXT,
    GOOGLE_DRIVE,
    NOTION,
    SLACK
}
```

```java
package com.mydata.datasources;

public enum DataSourceStatus {
    ACTIVE,
    PAUSED,
    ERROR
}
```

```java
package com.mydata.datasources;

public enum SyncMode {
    MANUAL,
    SCHEDULED,
    MANUAL_AND_SCHEDULED
}
```

Create `src/main/java/com/mydata/users/UserRepository.java`:

```java
package com.mydata.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);
}
```

Create `src/main/java/com/mydata/workspaces/WorkspaceRepository.java`:

```java
package com.mydata.workspaces;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {
}
```

Create `src/main/java/com/mydata/datasources/DataSourceRepository.java`:

```java
package com.mydata.datasources;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataSourceRepository extends JpaRepository<DataSourceEntity, UUID> {
    List<DataSourceEntity> findByWorkspaceId(UUID workspaceId);
}
```

Create `src/main/java/com/mydata/documents/ExternalDocumentRepository.java`:

```java
package com.mydata.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalDocumentRepository extends JpaRepository<ExternalDocumentEntity, UUID> {
    Optional<ExternalDocumentEntity> findByDataSourceIdAndExternalId(UUID dataSourceId, String externalId);
}
```

Create `src/main/java/com/mydata/documents/DocumentChunkRepository.java`:

```java
package com.mydata.documents;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {
    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndex(UUID documentId);
}
```

- [ ] **Step 5: 저장 테스트 통과 확인**

Run:

```bash
./gradlew test --tests com.mydata.documents.CorePersistenceTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/mydata/common src/main/java/com/mydata/users src/main/java/com/mydata/workspaces src/main/java/com/mydata/datasources src/main/java/com/mydata/documents src/test/java/com/mydata/documents
git commit -m "feat: add core persistence model"
```

## Task 6: 수동 수집 job API 추가

**Files:**
- Create: `src/main/java/com/mydata/ingestion/IngestionJobEntity.java`
- Create: `src/main/java/com/mydata/ingestion/IngestionJobRepository.java`
- Create: `src/main/java/com/mydata/ingestion/IngestionCommandService.java`
- Create: `src/main/java/com/mydata/admin/AdminDataSourceController.java`
- Create: `src/test/java/com/mydata/admin/AdminDataSourceControllerTest.java`

- [ ] **Step 1: 실패하는 admin API 테스트 작성**

Create `src/test/java/com/mydata/admin/AdminDataSourceControllerTest.java`:

```java
package com.mydata.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydata.datasources.DataSourceEntity;
import com.mydata.datasources.DataSourceRepository;
import com.mydata.datasources.DataSourceStatus;
import com.mydata.datasources.DataSourceType;
import com.mydata.datasources.SyncMode;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminDataSourceControllerTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;

    @Test
    void createsManualSyncJobForDataSource() throws Exception {
        UserEntity user = users.save(UserEntity.create("owner@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Personal"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));

        mvc.perform(post("/admin/data-sources/{id}/sync", source.getId())
                .header("X-Admin-Token", "test-admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("requestedByUserId", user.getId().toString()))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.dataSourceId").value(source.getId().toString()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.triggerType").value("MANUAL"));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests com.mydata.admin.AdminDataSourceControllerTest
```

Expected: FAIL because controller and ingestion job classes do not exist.

- [ ] **Step 3: ingestion job 모델과 command service 작성**

Create `IngestionJobEntity` with fields matching `ingestion_jobs`: `workspaceId`, `dataSourceId`, `triggerType`, `status`, `requestedByUserId`, `startedAt`, `finishedAt`, `errorMessage`.

Use enums:

```java
package com.mydata.ingestion;

public enum IngestionTriggerType {
    MANUAL,
    SCHEDULED
}
```

```java
package com.mydata.ingestion;

public enum IngestionJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED
}
```

Create `IngestionCommandService` with:

```java
@Transactional
public IngestionJobEntity requestManualSync(UUID dataSourceId, UUID requestedByUserId) {
    DataSourceEntity source = dataSourceRepository.findById(dataSourceId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "data source not found"));
    IngestionJobEntity job = IngestionJobEntity.pending(
        source.getWorkspaceId(),
        source.getId(),
        IngestionTriggerType.MANUAL,
        requestedByUserId
    );
    return ingestionJobRepository.save(job);
}
```

- [ ] **Step 4: admin controller 작성**

Create `AdminDataSourceController`:

```java
package com.mydata.admin;

import com.mydata.ingestion.IngestionCommandService;
import com.mydata.ingestion.IngestionJobEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/admin/data-sources")
public class AdminDataSourceController {
    private final IngestionCommandService ingestionCommandService;
    private final String adminToken;

    public AdminDataSourceController(
        IngestionCommandService ingestionCommandService,
        @Value("${my-data.admin-token}") String adminToken
    ) {
        this.ingestionCommandService = ingestionCommandService;
        this.adminToken = adminToken;
    }

    @PostMapping("/{id}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncResponse sync(
        @RequestHeader("X-Admin-Token") String token,
        @PathVariable UUID id,
        @RequestBody SyncRequest request
    ) {
        if (!adminToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid admin token");
        }
        IngestionJobEntity job = ingestionCommandService.requestManualSync(id, request.requestedByUserId());
        return new SyncResponse(job.getId(), job.getDataSourceId(), job.getStatus().name(), job.getTriggerType().name());
    }

    public record SyncRequest(UUID requestedByUserId) {
    }

    public record SyncResponse(UUID jobId, UUID dataSourceId, String status, String triggerType) {
    }
}
```

- [ ] **Step 5: admin API 테스트 통과 확인**

Run:

```bash
./gradlew test --tests com.mydata.admin.AdminDataSourceControllerTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/mydata/ingestion src/main/java/com/mydata/admin src/test/java/com/mydata/admin
git commit -m "feat: add manual ingestion job API"
```

## Task 7: LOCAL_TEXT 커넥터와 수집 파이프라인

**Files:**
- Create: `src/main/java/com/mydata/connectors/core/DataSourceConnector.java`
- Create: `src/main/java/com/mydata/connectors/core/DocumentHandler.java`
- Create: `src/main/java/com/mydata/connectors/core/RawAclEntry.java`
- Create: `src/main/java/com/mydata/connectors/core/RawContent.java`
- Create: `src/main/java/com/mydata/connectors/core/RawExternalDocument.java`
- Create: `src/main/java/com/mydata/connectors/core/SyncCursor.java`
- Create: `src/main/java/com/mydata/connectors/local/LocalTextConnector.java`
- Create: `src/main/java/com/mydata/ingestion/Chunker.java`
- Create: `src/main/java/com/mydata/ingestion/IngestionPipelineService.java`
- Create: `src/main/java/com/mydata/ingestion/IngestionWorker.java`
- Create: `src/test/java/com/mydata/ingestion/IngestionPipelineIntegrationTest.java`

- [ ] **Step 1: 실패하는 수집 파이프라인 테스트 작성**

Create `src/test/java/com/mydata/ingestion/IngestionPipelineIntegrationTest.java`:

```java
package com.mydata.ingestion;

import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.*;
import com.mydata.documents.ExternalDocumentRepository;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionPipelineIntegrationTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository jobs;
    @Autowired IngestionWorker worker;
    @Autowired ExternalDocumentRepository documents;

    @Test
    void ingestsLocalTextDocumentWithOwnerAclAndChunks() {
        UserEntity user = users.save(UserEntity.create("owner@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(user.getId(), "Personal"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        source.putConfig("externalId", "note-1");
        source.putConfig("title", "Local note");
        source.putConfig("content", "alpha beta gamma delta epsilon zeta eta theta");
        source.putConfig("principalKey", PrincipalKeys.user(user.getId()));
        dataSources.save(source);

        IngestionJobEntity job = jobs.save(IngestionJobEntity.pending(
            workspace.getId(),
            source.getId(),
            IngestionTriggerType.MANUAL,
            user.getId()
        ));

        worker.run(job.getId());

        assertThat(documents.findByDataSourceIdAndExternalId(source.getId(), "note-1"))
            .isPresent()
            .get()
            .satisfies(document -> {
                assertThat(document.getAclEntries()).hasSize(1);
                assertThat(document.getChunks()).isNotEmpty();
            });
    }
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests com.mydata.ingestion.IngestionPipelineIntegrationTest
```

Expected: FAIL because connector and worker classes do not exist.

- [ ] **Step 3: connector core records 작성**

Create records with these exact signatures:

```java
public record RawContent(String text, String mimeType) {}
public record RawAclEntry(String principalKey, String permission, boolean inherited, String source) {}
public record SyncCursor(Map<String, Object> value) {}
```

Create `RawExternalDocument`:

```java
public record RawExternalDocument(
    String externalId,
    DataSourceType sourceType,
    String title,
    String uri,
    String mimeType,
    Instant externalCreatedAt,
    Instant externalUpdatedAt,
    String contentHash,
    Map<String, Object> metadata,
    RawContent content,
    List<RawAclEntry> aclEntries
) {}
```

Create `DataSourceConnector`:

```java
public interface DataSourceConnector {
    DataSourceType supports();
    SyncCursor fetchChanges(DataSourceEntity dataSource, SyncCursor cursor, DocumentHandler handler);
}
```

Create `DocumentHandler`:

```java
@FunctionalInterface
public interface DocumentHandler {
    void handle(RawExternalDocument document);
}
```

- [ ] **Step 4: LOCAL_TEXT connector 작성**

`LocalTextConnector` reads `externalId`, `title`, `content`, and `principalKey` from `DataSourceEntity.configJson`. It emits one `RawExternalDocument` with a SHA-256 content hash and one READ ACL entry.

- [ ] **Step 5: chunker와 pipeline 작성**

`Chunker` behavior:

- Split text by whitespace.
- Chunk size: 120 words.
- Overlap: 20 words.
- For text shorter than 120 words, return one chunk.

`IngestionPipelineService` behavior:

- Upsert `external_documents` by `data_source_id + external_id`.
- Replace ACL entries and chunks for changed documents.
- Skip unchanged documents when `content_hash` matches.
- Store `source = 'MANUAL'` for local ACL entries.

`IngestionWorker` behavior:

- Load `IngestionJobEntity`.
- Mark job `RUNNING`.
- Resolve connector by `DataSourceType`.
- Run connector and pipeline.
- Mark job `SUCCEEDED` when all documents succeed.
- Mark job `FAILED` if connector resolution or pipeline setup fails.

- [ ] **Step 6: 수집 파이프라인 테스트 통과 확인**

Run:

```bash
./gradlew test --tests com.mydata.ingestion.IngestionPipelineIntegrationTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mydata/connectors src/main/java/com/mydata/ingestion src/test/java/com/mydata/ingestion
git commit -m "feat: add local ingestion pipeline"
```

## Task 8: 임베딩 저장과 ACL 필터 포함 vector search

**Files:**
- Create: `src/main/java/com/mydata/embeddings/EmbeddingClient.java`
- Create: `src/main/java/com/mydata/embeddings/DeterministicEmbeddingClient.java`
- Create: `src/main/java/com/mydata/embeddings/DocumentEmbeddingRepository.java`
- Create: `src/main/java/com/mydata/retrieval/RetrievedChunk.java`
- Create: `src/main/java/com/mydata/retrieval/PgVectorSearchRepository.java`
- Create: `src/main/java/com/mydata/retrieval/RetrievalService.java`
- Create: `src/test/java/com/mydata/retrieval/PgVectorSearchRepositoryTest.java`
- Modify: `src/main/java/com/mydata/ingestion/IngestionPipelineService.java`

- [ ] **Step 1: 실패하는 ACL retrieval 테스트 작성**

Create `src/test/java/com/mydata/retrieval/PgVectorSearchRepositoryTest.java`:

```java
package com.mydata.retrieval;

import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.*;
import com.mydata.ingestion.IngestionJobEntity;
import com.mydata.ingestion.IngestionJobRepository;
import com.mydata.ingestion.IngestionTriggerType;
import com.mydata.ingestion.IngestionWorker;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PgVectorSearchRepositoryTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository jobs;
    @Autowired IngestionWorker worker;
    @Autowired RetrievalService retrievalService;

    @Test
    void returnsOnlyChunksVisibleToRequesterPrincipal() {
        UserEntity owner = users.save(UserEntity.create("owner@example.com", "Owner"));
        UserEntity stranger = users.save(UserEntity.create("stranger@example.com", "Stranger"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        source.putConfig("externalId", "note-1");
        source.putConfig("title", "Searchable note");
        source.putConfig("content", "project alpha budget planning");
        source.putConfig("principalKey", PrincipalKeys.user(owner.getId()));
        dataSources.save(source);

        IngestionJobEntity job = jobs.save(IngestionJobEntity.pending(
            workspace.getId(),
            source.getId(),
            IngestionTriggerType.MANUAL,
            owner.getId()
        ));
        worker.run(job.getId());

        List<RetrievedChunk> visible = retrievalService.retrieve(
            workspace.getId(),
            List.of(PrincipalKeys.user(owner.getId())),
            "alpha budget",
            5
        );
        List<RetrievedChunk> hidden = retrievalService.retrieve(
            workspace.getId(),
            List.of(PrincipalKeys.user(stranger.getId())),
            "alpha budget",
            5
        );

        assertThat(visible).hasSize(1);
        assertThat(visible.getFirst().title()).isEqualTo("Searchable note");
        assertThat(hidden).isEmpty();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests com.mydata.retrieval.PgVectorSearchRepositoryTest
```

Expected: FAIL because embedding repository and retrieval service do not exist.

- [ ] **Step 3: deterministic embedding client 작성**

Create `EmbeddingClient`:

```java
package com.mydata.embeddings;

public interface EmbeddingClient {
    String model();
    int dimensions();
    float[] embed(String text);
}
```

Create `DeterministicEmbeddingClient`:

```java
package com.mydata.embeddings;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class DeterministicEmbeddingClient implements EmbeddingClient {
    private static final int DIMENSIONS = 1536;

    @Override
    public String model() {
        return "deterministic-1536";
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embed(String text) {
        byte[] digest = sha256(text);
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            int value = digest[i % digest.length] & 0xff;
            vector[i] = value / 255.0f;
        }
        return vector;
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 4: vector literal helper와 repository 작성**

`DocumentEmbeddingRepository` uses `JdbcTemplate` and stores vectors with:

```java
String vectorLiteral(float[] vector) {
    StringBuilder builder = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
        if (i > 0) {
            builder.append(',');
        }
        builder.append(vector[i]);
    }
    return builder.append(']').toString();
}
```

Insert SQL:

```sql
INSERT INTO document_embeddings (chunk_id, embedding_model, embedding)
VALUES (?, ?, CAST(? AS vector))
ON CONFLICT (chunk_id, embedding_model)
DO UPDATE SET embedding = EXCLUDED.embedding
```

`PgVectorSearchRepository` SQL:

```sql
SELECT
  c.id AS chunk_id,
  c.content,
  d.title,
  d.uri,
  d.source_type,
  e.embedding <=> CAST(? AS vector) AS distance
FROM document_embeddings e
JOIN document_chunks c ON c.id = e.chunk_id
JOIN external_documents d ON d.id = c.document_id
WHERE d.workspace_id = ?
  AND d.deleted_at IS NULL
  AND EXISTS (
    SELECT 1
    FROM document_acl_entries acl
    WHERE acl.document_id = d.id
      AND acl.permission = 'READ'
      AND acl.principal_key = ANY(?)
  )
ORDER BY e.embedding <=> CAST(? AS vector)
LIMIT ?
```

Use a JDBC `Array` for `principalKeys`.

- [ ] **Step 5: pipeline에 embedding 저장 연결**

After saving chunks in `IngestionPipelineService`, call `EmbeddingClient.embed(chunk.getContent())` and upsert each chunk embedding through `DocumentEmbeddingRepository`.

- [ ] **Step 6: retrieval 테스트 통과 확인**

Run:

```bash
./gradlew test --tests com.mydata.retrieval.PgVectorSearchRepositoryTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mydata/embeddings src/main/java/com/mydata/retrieval src/main/java/com/mydata/ingestion src/test/java/com/mydata/retrieval
git commit -m "feat: add ACL-filtered vector retrieval"
```

## Task 9: Chat application service와 citation 저장

**Files:**
- Create: `src/main/java/com/mydata/chat/LlmClient.java`
- Create: `src/main/java/com/mydata/chat/StubLlmClient.java`
- Create: `src/main/java/com/mydata/chat/ChatApplicationService.java`
- Create: `src/main/java/com/mydata/chat/ChatSessionEntity.java`
- Create: `src/main/java/com/mydata/chat/ChatSessionRepository.java`
- Create: `src/main/java/com/mydata/chat/ChatMessageEntity.java`
- Create: `src/main/java/com/mydata/chat/ChatMessageRepository.java`
- Create: `src/main/java/com/mydata/chat/ChatRetrievalCitationEntity.java`
- Create: `src/test/java/com/mydata/chat/ChatApplicationServiceTest.java`

- [ ] **Step 1: 실패하는 chat service 테스트 작성**

Create `src/test/java/com/mydata/chat/ChatApplicationServiceTest.java`:

```java
package com.mydata.chat;

import com.mydata.auth.PrincipalKeys;
import com.mydata.datasources.*;
import com.mydata.ingestion.*;
import com.mydata.support.PostgresIntegrationTest;
import com.mydata.users.UserEntity;
import com.mydata.users.UserRepository;
import com.mydata.workspaces.WorkspaceEntity;
import com.mydata.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatApplicationServiceTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired WorkspaceRepository workspaces;
    @Autowired DataSourceRepository dataSources;
    @Autowired IngestionJobRepository jobs;
    @Autowired IngestionWorker worker;
    @Autowired ChatApplicationService chat;
    @Autowired ChatMessageRepository messages;

    @Test
    void answersUsingOnlyAuthorizedRetrievedContext() {
        UserEntity owner = users.save(UserEntity.create("owner@example.com", "Owner"));
        WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
        DataSourceEntity source = dataSources.save(DataSourceEntity.create(
            workspace.getId(),
            DataSourceType.LOCAL_TEXT,
            "Local notes",
            DataSourceStatus.ACTIVE,
            SyncMode.MANUAL
        ));
        source.putConfig("externalId", "note-1");
        source.putConfig("title", "Budget note");
        source.putConfig("content", "alpha project budget is 1000");
        source.putConfig("principalKey", PrincipalKeys.user(owner.getId()));
        dataSources.save(source);
        worker.run(jobs.save(IngestionJobEntity.pending(
            workspace.getId(),
            source.getId(),
            IngestionTriggerType.MANUAL,
            owner.getId()
        )).getId());

        ChatApplicationService.Answer answer = chat.answer(
            workspace.getId(),
            owner.getId(),
            "SLACK",
            "C123",
            "1710000000.000000",
            List.of(PrincipalKeys.user(owner.getId())),
            "alpha budget?"
        );

        assertThat(answer.content()).contains("Budget note");
        assertThat(answer.citations()).hasSize(1);
        assertThat(messages.findBySessionIdOrderByCreatedAt(answer.sessionId())).hasSize(2);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests com.mydata.chat.ChatApplicationServiceTest
```

Expected: FAIL because chat service classes do not exist.

- [ ] **Step 3: LLM stub 작성**

Create `LlmClient`:

```java
package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;

import java.util.List;

public interface LlmClient {
    String generate(String question, List<RetrievedChunk> chunks);
}
```

Create `StubLlmClient`:

```java
package com.mydata.chat;

import com.mydata.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class StubLlmClient implements LlmClient {
    @Override
    public String generate(String question, List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "색인된 데이터에서 답을 찾지 못했습니다.";
        }
        String sources = chunks.stream()
            .map(RetrievedChunk::title)
            .distinct()
            .collect(Collectors.joining(", "));
        return "검색된 근거를 찾았습니다. 출처: " + sources;
    }
}
```

- [ ] **Step 4: chat service 작성**

`ChatApplicationService.answer(...)` behavior:

- Find or create `ChatSessionEntity` by workspace, channel type, external channel id, external thread id.
- Save user message.
- Call `RetrievalService.retrieve(...)`.
- Call `LlmClient.generate(...)`.
- Save assistant message.
- Save `ChatRetrievalCitationEntity` for each returned chunk with rank and distance score.
- Return `Answer(UUID sessionId, String content, List<Citation> citations)`.

- [ ] **Step 5: chat service 테스트 통과 확인**

Run:

```bash
./gradlew test --tests com.mydata.chat.ChatApplicationServiceTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/mydata/chat src/test/java/com/mydata/chat
git commit -m "feat: add chat answer service"
```

## Task 10: Slack signature 검증과 event endpoint

**Files:**
- Create: `src/main/java/com/mydata/slackbot/SlackSignatureVerifier.java`
- Create: `src/main/java/com/mydata/slackbot/SlackEventController.java`
- Create: `src/test/java/com/mydata/slackbot/SlackSignatureVerifierTest.java`
- Create: `src/test/java/com/mydata/slackbot/SlackEventControllerTest.java`

- [ ] **Step 1: 실패하는 Slack signature 테스트 작성**

Create `src/test/java/com/mydata/slackbot/SlackSignatureVerifierTest.java`:

```java
package com.mydata.slackbot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackSignatureVerifierTest {
    @Test
    void verifiesExpectedHmacSignature() {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier("secret");
        String timestamp = "1531420618";
        String body = "token=xyzz&team_id=T1&api_app_id=A1";
        String signature = "v0=085dc07b2c42b48f4d9996b12898475010ff39e6307033272a9b3e064e48e4d7";

        assertThat(verifier.isValid(timestamp, body, signature)).isTrue();
    }

    @Test
    void rejectsInvalidSignature() {
        SlackSignatureVerifier verifier = new SlackSignatureVerifier("secret");

        assertThat(verifier.isValid("1531420618", "body", "v0=bad")).isFalse();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run:

```bash
./gradlew test --tests com.mydata.slackbot.SlackSignatureVerifierTest
```

Expected: FAIL because `SlackSignatureVerifier` does not exist.

- [ ] **Step 3: Slack signature verifier 구현**

Create `src/main/java/com/mydata/slackbot/SlackSignatureVerifier.java`:

```java
package com.mydata.slackbot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class SlackSignatureVerifier {
    private final String signingSecret;

    public SlackSignatureVerifier(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public boolean isValid(String timestamp, String body, String signature) {
        try {
            String base = "v0:" + timestamp + ":" + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "v0=" + HexFormat.of().formatHex(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Slack event controller 테스트 작성**

Create `src/test/java/com/mydata/slackbot/SlackEventControllerTest.java`:

```java
package com.mydata.slackbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydata.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SlackEventControllerTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void respondsToUrlVerificationChallenge() throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(Map.of(
            "type", "url_verification",
            "challenge", "challenge-token"
        ));

        mvc.perform(post("/slack/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Slack-Request-Timestamp", "1531420618")
                .header("X-Slack-Signature", "test-bypass"))
            .andExpect(status().isOk())
            .andExpect(content().string("challenge-token"));
    }
}
```

- [ ] **Step 5: Slack event controller 구현**

`SlackEventController` behavior:

- Accept raw request body as `String`.
- In profile `test`, allow signature `test-bypass`.
- In non-test profiles, verify `X-Slack-Request-Timestamp`, body, and `X-Slack-Signature`.
- For `type=url_verification`, return the `challenge` plain text.
- For unsupported events in this foundation slice, return `200 OK` with body `ok`.
- Slack user identity mapping is implemented in the real Slack answer integration plan, after this endpoint can safely receive and verify events.

- [ ] **Step 6: Slack 테스트 통과 확인**

Run:

```bash
./gradlew test --tests com.mydata.slackbot.SlackSignatureVerifierTest --tests com.mydata.slackbot.SlackEventControllerTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/mydata/slackbot src/test/java/com/mydata/slackbot
git commit -m "feat: add Slack event foundation"
```

## Task 11: 전체 검증과 README 실행 메모

**Files:**
- Create: `README.md`
- Modify: existing files only if tests reveal a mismatch

- [ ] **Step 1: README 작성**

Create `README.md`:

````markdown
# My Data

Spring Boot 기반 개인 데이터 RAG 챗봇입니다.

## Local DB

```bash
docker compose up -d postgres
```

## Test

```bash
./gradlew test
```

## Local Run

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## Current Scope

- PostgreSQL + pgvector schema
- ACL principal model
- Manual ingestion job API
- LOCAL_TEXT connector for foundation tests
- Deterministic embedding client for tests/local development
- ACL-filtered vector retrieval
- Slack event endpoint foundation
````

- [ ] **Step 2: 전체 테스트 실행**

Run:

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 3: 로컬 앱 부팅 확인**

Run:

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Expected: application starts and Flyway validates migrations. Stop with `Ctrl-C`.

- [ ] **Step 4: 작업 트리 확인**

Run:

```bash
git status --short
```

Expected: only intentional source and README changes are shown.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: add local development guide"
```

## Self-Review Checklist

- Spec coverage:
  - Spring Boot modular monolith: Task 1.
  - PostgreSQL + pgvector + Flyway: Task 2 and Task 3.
  - ACL principal model: Task 4.
  - JPA metadata persistence: Task 5.
  - Manual ingestion job: Task 6.
  - Shared connector contract: Task 7.
  - Chunking and embedding storage: Task 7 and Task 8.
  - ACL-filtered vector search: Task 8.
  - Chat answer orchestration: Task 9.
  - Slack event foundation: Task 10.
  - Verification and local run notes: Task 11.
- Deferred by explicit split:
  - Real Google Drive connector.
  - Real Notion connector.
  - Slack data source ingestion.
  - Real LLM/embedding provider.
  - Production secret backend.
- Placeholder scan:
  - No prohibited placeholder tokens or vague error-handling instructions remain.
  - Every task has files, commands, expected results, and a commit.
- Type consistency:
  - `DataSourceType.LOCAL_TEXT` is introduced before the local connector task.
  - `PrincipalKeys.user(...)` is introduced before ACL persistence and retrieval tests.
  - `RetrievedChunk` is introduced before `LlmClient` and chat service tests.
