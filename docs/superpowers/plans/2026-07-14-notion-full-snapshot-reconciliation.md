# Notion Full Snapshot Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 완전히 성공한 Notion 재수집에서 현재 범위에 없는 문서를 소프트 삭제하고, 같은 ID가 다시 나타나면 복구하며, 실패한 snapshot과 증분 커넥터의 기존 문서는 보존한다.

**Architecture:** 성공 `ingestion_job_items.document_id`를 DB에 저장된 snapshot 확인 목록으로 재사용하고, 마지막 짧은 트랜잭션에서 PostgreSQL anti-join UPDATE를 실행한다. 커넥터가 정리 모드를 명시하며 Notion만 `FULL_SNAPSHOT`을 사용한다. 같은 data source의 job은 claim 시 source row lock과 partial unique index로 직렬화한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, PostgreSQL + pgvector, Liquibase formatted SQL, JUnit 5, AssertJ, Gradle

## Global Constraints

- 누락 문서 정리는 현재 Notion 데이터소스에만 적용한다.
- `PARTIAL_FAILED`, `FAILED` 또는 connector 예외 종료에서는 누락 문서를 전혀 삭제하지 않는다.
- 같은 `(data_source_id, external_id)` 문서는 기존 UUID를 유지하고 재등장 시 `deleted_at`을 해제한다.
- 원격 API 호출 동안 DB 트랜잭션과 connection을 점유하지 않는다.
- reconciliation용 전체 문서 ID를 JVM 컬렉션이나 SQL `IN` 파라미터로 만들지 않는다.
- 같은 data source에 `RUNNING` job은 최대 하나만 존재하고, 대기 job은 `PENDING`으로 남긴다.
- ACL 검색은 principal이 없거나 비어 있으면 기존처럼 fail-closed로 동작한다.
- DB 변경은 `db.changelog-master.json`을 include 전용으로 유지하고 `changes/007-...sql`에 추가한다.
- UI는 변경하지 않으며 Playwright 화면 검증 대상이 아니다.
- 실제 비밀값과 `.env`는 수정하거나 커밋하지 않는다.

---

## File Structure

### Create

- `src/main/java/com/mydata/connectors/core/ConnectorReconciliationMode.java`: connector별 누락 문서 정리 가능 범위
- `src/main/resources/db/changelog/changes/007-full-snapshot-reconciliation-indexes.sql`: seen anti-join 및 단일 RUNNING job 인덱스
- `src/test/java/com/mydata/connectors/local/LocalTextConnectorTest.java`: 기본 정리 모드 회귀

### Modify

- `src/main/java/com/mydata/connectors/core/DataSourceConnector.java`: 기본 `NONE` 정리 모드 제공
- `src/main/java/com/mydata/connectors/notion/NotionPageConnector.java`: `FULL_SNAPSHOT` 선언
- `src/main/java/com/mydata/documents/ExternalDocumentEntity.java`: `deleted_at` 매핑과 재수집 복구
- `src/main/java/com/mydata/documents/ExternalDocumentRepository.java`: 성공 snapshot anti-join soft-delete
- `src/main/java/com/mydata/datasources/DataSourceRepository.java`: claim용 pessimistic row lock
- `src/main/java/com/mydata/ingestion/IngestionJobRepository.java`: 같은 source의 기존 RUNNING job을 제외한 conditional claim
- `src/main/java/com/mydata/ingestion/IngestionWorker.java`: 정리 모드 전달, 성공 정리, source별 claim 직렬화
- `src/main/resources/db/changelog/db.changelog-master.json`: 007 include
- `src/test/java/com/mydata/database/LiquibaseMigrationTest.java`: changeset 수 7 검증
- `src/test/java/com/mydata/database/LiquibaseChangelogStructureTest.java`: 006·007 include와 007 precondition 검증
- `src/test/java/com/mydata/admin/AdminSchemaMigrationTest.java`: 두 partial index 계약 검증
- `src/test/java/com/mydata/connectors/notion/NotionPageConnectorTest.java`: Notion 정리 모드 검증
- `src/test/java/com/mydata/connectors/slack/SlackChannelConnectorTest.java`: Slack 기본 모드 검증
- `src/test/java/com/mydata/ingestion/IngestionPipelineIntegrationTest.java`: 동일/변경 콘텐츠 tombstone 복구
- `src/test/java/com/mydata/ingestion/IngestionWorkerIntegrationTest.java`: 정리 gate와 동시 claim 검증
- `src/test/java/com/mydata/ingestion/NotionIngestionIntegrationTest.java`: 실제 Notion 제거·복구·부분 실패 경계
- `src/test/java/com/mydata/ingestion/SlackIngestionIntegrationTest.java`: 증분 수집의 이전 문서 보존
- `src/test/java/com/mydata/retrieval/PgVectorSearchRepositoryTest.java`: tombstone 검색 제외와 복구
- `docs/notion-integration-setup.md`: 완전 성공 재수집의 삭제·복구 의미
- `README.md`: Notion 재수집 동작 요약

---

### Task 1: Liquibase Reconciliation Invariants

**Files:**
- Create: `src/main/resources/db/changelog/changes/007-full-snapshot-reconciliation-indexes.sql`
- Modify: `src/main/resources/db/changelog/db.changelog-master.json`
- Modify: `src/test/java/com/mydata/database/LiquibaseMigrationTest.java`
- Modify: `src/test/java/com/mydata/database/LiquibaseChangelogStructureTest.java`
- Modify: `src/test/java/com/mydata/admin/AdminSchemaMigrationTest.java`

**Interfaces:**
- Produces: partial index `idx_ingestion_job_items_job_succeeded_document`
- Produces: partial unique index `uq_ingestion_jobs_running_data_source`
- Produces: DB invariant that one data source has at most one `RUNNING` job

- [ ] **Step 1: Write failing migration contract tests**

Change the expected Liquibase row count to 7 in `LiquibaseMigrationTest`:

```java
assertThat(liquibaseChanges).isEqualTo(7);
```

Extend `LiquibaseChangelogStructureTest` so the include list contains 006 and 007, the include count is exact, and the new SQL declares a HALT precondition:

```java
assertThat(masterContent).contains(
    "\"file\": \"db/changelog/changes/006-ingestion-job-item-indexes.sql\"",
    "\"file\": \"db/changelog/changes/007-full-snapshot-reconciliation-indexes.sql\""
);
assertThat(includeCount).isEqualTo(7);

String reconciliationMigration = Files.readString(
    changelogDirectory.resolve("changes/007-full-snapshot-reconciliation-indexes.sql")
);
assertThat(reconciliationMigration)
    .contains("--preconditions onFail:HALT onError:HALT")
    .contains("HAVING count(*) > 1")
    .contains("idx_ingestion_job_items_job_succeeded_document")
    .contains("uq_ingestion_jobs_running_data_source");
```

Add this test to `AdminSchemaMigrationTest`:

```java
@Test
void createsFullSnapshotReconciliationIndexes() {
    Map<String, String> itemIndexes = indexes("ingestion_job_items");
    Map<String, String> jobIndexes = indexes("ingestion_jobs");

    assertThat(itemIndexes.get("idx_ingestion_job_items_job_succeeded_document"))
        .contains("(job_id, document_id)")
        .contains("status = 'SUCCEEDED'::text")
        .contains("document_id IS NOT NULL");
    assertThat(jobIndexes.get("uq_ingestion_jobs_running_data_source"))
        .contains("CREATE UNIQUE INDEX")
        .contains("(data_source_id)")
        .contains("status = 'RUNNING'::text");
}

private Map<String, String> indexes(String tableName) {
    return jdbcTemplate.query("""
        SELECT indexname, indexdef
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND tablename = ?
        """, resultSet -> {
        Map<String, String> definitions = new java.util.LinkedHashMap<>();
        while (resultSet.next()) {
            definitions.put(resultSet.getString("indexname"), resultSet.getString("indexdef"));
        }
        return definitions;
    }, tableName);
}
```

Refactor the existing keyset index test to call `indexes("ingestion_job_items")` instead of duplicating the query.

- [ ] **Step 2: Run the migration tests and verify RED**

Run:

```bash
./gradlew test --tests com.mydata.database.LiquibaseChangelogStructureTest --tests com.mydata.database.LiquibaseMigrationTest --tests com.mydata.admin.AdminSchemaMigrationTest
```

Expected: FAIL because changeset 007 and both new indexes do not exist and the applied changeset count is still 6.

- [ ] **Step 3: Add changeset 007 and master include**

Create `007-full-snapshot-reconciliation-indexes.sql` with exactly:

```sql
--liquibase formatted sql

--changeset eric:007-full-snapshot-reconciliation-indexes
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM (SELECT data_source_id FROM ingestion_jobs WHERE status = 'RUNNING' GROUP BY data_source_id HAVING count(*) > 1) duplicate_running_jobs
CREATE INDEX IF NOT EXISTS idx_ingestion_job_items_job_succeeded_document
    ON ingestion_job_items(job_id, document_id)
    WHERE status = 'SUCCEEDED' AND document_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ingestion_jobs_running_data_source
    ON ingestion_jobs(data_source_id)
    WHERE status = 'RUNNING';
```

Append this include after 006 in `db.changelog-master.json`:

```json
{
  "include": {
    "file": "db/changelog/changes/007-full-snapshot-reconciliation-indexes.sql"
  }
}
```

- [ ] **Step 4: Run the migration tests and verify GREEN**

Run the same focused Gradle command. Expected: all three test classes PASS with Liquibase count 7 and both new index definitions present.

- [ ] **Step 5: Commit the migration invariant**

```bash
git add src/main/resources/db/changelog src/test/java/com/mydata/database src/test/java/com/mydata/admin/AdminSchemaMigrationTest.java
git commit -m "feat(ingestion): add reconciliation database invariants" -m "Notion snapshot 정리용 seen index와 data source별 단일 RUNNING job 제약을 추가했습니다. 중복 RUNNING 상태에서는 migration이 중단됩니다.

검증: migration 관련 Gradle 테스트 통과. Playwright: UI 변경 없음."
```

---

### Task 2: Connector Mode and Document Restoration

**Files:**
- Create: `src/main/java/com/mydata/connectors/core/ConnectorReconciliationMode.java`
- Create: `src/test/java/com/mydata/connectors/local/LocalTextConnectorTest.java`
- Modify: `src/main/java/com/mydata/connectors/core/DataSourceConnector.java`
- Modify: `src/main/java/com/mydata/connectors/notion/NotionPageConnector.java`
- Modify: `src/main/java/com/mydata/documents/ExternalDocumentEntity.java`
- Modify: `src/test/java/com/mydata/connectors/notion/NotionPageConnectorTest.java`
- Modify: `src/test/java/com/mydata/connectors/slack/SlackChannelConnectorTest.java`
- Modify: `src/test/java/com/mydata/ingestion/IngestionPipelineIntegrationTest.java`

**Interfaces:**
- Produces: `ConnectorReconciliationMode { NONE, FULL_SNAPSHOT }`
- Produces: `DataSourceConnector.reconciliationMode()` with default `NONE`
- Produces: `ExternalDocumentEntity.getDeletedAt()` and automatic restore in `updateFromIngestion(...)`
- Consumes: existing `(data_source_id, external_id)` document identity

- [ ] **Step 1: Write failing connector mode tests**

Add to `NotionPageConnectorTest`:

```java
@Test
void declaresFullSnapshotReconciliation() {
    NotionPageConnector connector = new NotionPageConnector(new FakeNotionClient());

    assertThat(connector.reconciliationMode())
        .isEqualTo(ConnectorReconciliationMode.FULL_SNAPSHOT);
}
```

Add to `SlackChannelConnectorTest` using its existing fake client:

```java
@Test
void keepsReconciliationDisabledForIncrementalSync() {
    SlackChannelConnector connector = new SlackChannelConnector(new FakeSlackClient());

    assertThat(connector.reconciliationMode())
        .isEqualTo(ConnectorReconciliationMode.NONE);
}
```

Create `LocalTextConnectorTest`:

```java
package com.mydata.connectors.local;

import com.mydata.connectors.core.ConnectorReconciliationMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTextConnectorTest {
    @Test
    void keepsReconciliationDisabledOutsideApprovedNotionScope() {
        assertThat(new LocalTextConnector().reconciliationMode())
            .isEqualTo(ConnectorReconciliationMode.NONE);
    }
}
```

- [ ] **Step 2: Write failing unchanged and changed restoration tests**

Add this fixture helper and raw document helper to `IngestionPipelineIntegrationTest`:

```java
private record RestoreFixture(UserEntity owner, DataSourceEntity dataSource) {
}

private RestoreFixture restoreFixture(String name) {
    String suffix = UUID.randomUUID().toString();
    UserEntity owner = users.save(UserEntity.create(
        "restore-" + suffix + "@example.com", "Restore Owner"
    ));
    WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), name));
    DataSourceEntity dataSource = dataSources.saveAndFlush(DataSourceEntity.create(
        workspace.getId(), DataSourceType.NOTION, name, DataSourceStatus.ACTIVE, SyncMode.MANUAL
    ));
    return new RestoreFixture(owner, dataSource);
}

private RawExternalDocument restoreDocument(
    RestoreFixture fixture,
    String externalId,
    String content,
    String contentHash
) {
    return new RawExternalDocument(
        externalId,
        DataSourceType.NOTION,
        "Restore page",
        "https://notion.so/" + externalId,
        "text/plain",
        null,
        null,
        contentHash,
        Map.of(),
        new RawContent(content, "text/plain"),
        List.of(new RawAclEntry(
            PrincipalKeys.user(fixture.owner().getId()), "READ", false, "NOTION"
        ))
    );
}
```

Add both tests:

```java
@Test
void unchangedReingestionRestoresSoftDeletedDocumentWithSameId() {
    RestoreFixture fixture = restoreFixture("Unchanged restore");
    RawExternalDocument rawDocument = restoreDocument(
        fixture, "unchanged-restore", "unchanged restored content", "restore-hash-1"
    );
    UUID originalDocumentId = pipeline.ingest(
        fixture.dataSource().getWorkspaceId(), fixture.dataSource().getId(), rawDocument
    );
    UUID originalChunkId = chunks.findByDocumentIdOrderByChunkIndex(originalDocumentId)
        .getFirst()
        .getId();
    jdbcTemplate.update(
        "UPDATE external_documents SET deleted_at = now() WHERE id = ?",
        originalDocumentId
    );

    UUID restoredDocumentId = pipeline.ingest(
        fixture.dataSource().getWorkspaceId(), fixture.dataSource().getId(), rawDocument
    );

    ExternalDocumentEntity restored = documents.findById(restoredDocumentId).orElseThrow();
    assertThat(restored.getId()).isEqualTo(originalDocumentId);
    assertThat(restored.getDeletedAt()).isNull();
    assertThat(chunks.findByDocumentIdOrderByChunkIndex(restored.getId()))
        .singleElement()
        .satisfies(chunk -> assertThat(chunk.getId()).isEqualTo(originalChunkId));
}

@Test
void changedReingestionRestoresSoftDeletedDocumentWithSameId() {
    RestoreFixture fixture = restoreFixture("Changed restore");
    UUID originalDocumentId = pipeline.ingest(
        fixture.dataSource().getWorkspaceId(),
        fixture.dataSource().getId(),
        restoreDocument(fixture, "changed-restore", "original content", "restore-hash-1")
    );
    UUID originalChunkId = chunks.findByDocumentIdOrderByChunkIndex(originalDocumentId)
        .getFirst()
        .getId();
    jdbcTemplate.update(
        "UPDATE external_documents SET deleted_at = now() WHERE id = ?",
        originalDocumentId
    );

    UUID restoredDocumentId = pipeline.ingest(
        fixture.dataSource().getWorkspaceId(),
        fixture.dataSource().getId(),
        restoreDocument(fixture, "changed-restore", "changed restored content", "restore-hash-2")
    );

    ExternalDocumentEntity restored = documents.findById(restoredDocumentId).orElseThrow();
    assertThat(restored.getId()).isEqualTo(originalDocumentId);
    assertThat(restored.getDeletedAt()).isNull();
    assertThat(chunks.findByDocumentIdOrderByChunkIndex(restored.getId()))
        .singleElement()
        .satisfies(chunk -> {
            assertThat(chunk.getId()).isNotEqualTo(originalChunkId);
            assertThat(chunk.getContent()).isEqualTo("changed restored content");
        });
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```bash
./gradlew test --tests com.mydata.connectors.notion.NotionPageConnectorTest --tests com.mydata.connectors.slack.SlackChannelConnectorTest --tests com.mydata.connectors.local.LocalTextConnectorTest --tests com.mydata.ingestion.IngestionPipelineIntegrationTest
```

Expected: test compilation fails because `ConnectorReconciliationMode`, `reconciliationMode()` and `ExternalDocumentEntity.getDeletedAt()` do not exist.

- [ ] **Step 4: Implement the connector contract**

Create:

```java
package com.mydata.connectors.core;

public enum ConnectorReconciliationMode {
    NONE,
    FULL_SNAPSHOT
}
```

Add the default to `DataSourceConnector`:

```java
default ConnectorReconciliationMode reconciliationMode() {
    return ConnectorReconciliationMode.NONE;
}
```

Override it in `NotionPageConnector`:

```java
@Override
public ConnectorReconciliationMode reconciliationMode() {
    return ConnectorReconciliationMode.FULL_SNAPSHOT;
}
```

Do not override it in Slack or Local Text.

- [ ] **Step 5: Map and restore the tombstone**

Add to `ExternalDocumentEntity`:

```java
@Column(name = "deleted_at")
private OffsetDateTime deletedAt;
```

At the end of the most complete `updateFromIngestion(...)` overload add:

```java
this.deletedAt = null;
```

Import `java.time.OffsetDateTime`. Both unchanged and changed pipeline paths already call this common method, so no pipeline branch is added.

- [ ] **Step 6: Run focused tests and verify GREEN**

Run the same focused Gradle command. Expected: connector mode and both restoration paths PASS.

- [ ] **Step 7: Commit connector and restoration behavior**

```bash
git add src/main/java/com/mydata/connectors src/main/java/com/mydata/documents/ExternalDocumentEntity.java src/test/java/com/mydata/connectors src/test/java/com/mydata/ingestion/IngestionPipelineIntegrationTest.java
git commit -m "feat(notion): declare snapshot mode and restore documents" -m "Notion만 FULL_SNAPSHOT 정리 모드를 선언하고 재등장한 동일 external ID의 tombstone을 unchanged/changed 경로 모두 해제합니다.

검증: connector 및 ingestion pipeline Gradle 테스트 통과. Playwright: UI 변경 없음."
```

---

### Task 3: Successful Full Snapshot Reconciliation

**Files:**
- Modify: `src/main/java/com/mydata/documents/ExternalDocumentRepository.java`
- Modify: `src/main/java/com/mydata/ingestion/IngestionWorker.java`
- Modify: `src/test/java/com/mydata/ingestion/IngestionWorkerIntegrationTest.java`

**Interfaces:**
- Consumes: `DataSourceConnector.reconciliationMode()` from Task 2
- Consumes: successful `ingestion_job_items(job_id, document_id)` rows
- Produces: `ExternalDocumentRepository.softDeleteUnseenForSucceededFullSnapshot(UUID jobId): int`
- Produces: atomic `SUCCEEDED + optional sweep + cursor/lastSynced` finalization

- [ ] **Step 1: Extend the test connector for explicit full snapshots and post-document failure**

In `IngestionWorkerIntegrationTest.TestConnector`, add state and reset it:

```java
private ConnectorReconciliationMode reconciliationMode = ConnectorReconciliationMode.NONE;
private RuntimeException failureAfterDocuments;

void fullSnapshot() {
    reconciliationMode = ConnectorReconciliationMode.FULL_SNAPSHOT;
}

void throwAfterDocuments(RuntimeException failure) {
    failureAfterDocuments = failure;
}

@Override
public ConnectorReconciliationMode reconciliationMode() {
    return reconciliationMode;
}
```

After `documentEvents.forEach(sink::onDocument)` in `fetchChanges`, throw `failureAfterDocuments` when non-null, before failure events and cursor return. Reset both fields in `reset()`.

- [ ] **Step 2: Write failing successful and empty full snapshot tests**

Add a helper for a later job on the same source:

```java
private IngestionJobEntity pendingJob(Fixture fixture) {
    return ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
        fixture.dataSource().getWorkspaceId(),
        fixture.dataSource().getId(),
        IngestionTriggerType.MANUAL,
        fixture.job().getRequestedByUserId()
    ));
}
```

Add these tests:

```java
@Test
void successfulFullSnapshotSoftDeletesOnlyUnseenDocuments() {
    Fixture fixture = fixture("full-snapshot", Map.of("cursor", "before"));
    connector.fullSnapshot();
    connector.events(
        documentEvent("seen", readableDocument("seen")),
        documentEvent("missing", readableDocument("missing"))
    );
    worker.run(fixture.job().getId());
    UUID seenId = documents.findByDataSourceIdAndExternalId(
        fixture.dataSource().getId(), "seen"
    ).orElseThrow().getId();
    UUID missingId = documents.findByDataSourceIdAndExternalId(
        fixture.dataSource().getId(), "missing"
    ).orElseThrow().getId();

    Fixture otherFixture = fixture("other-source", Map.of("cursor", "before"));
    connector.events(documentEvent("other", readableDocument("other")));
    worker.run(otherFixture.job().getId());
    UUID otherId = documents.findByDataSourceIdAndExternalId(
        otherFixture.dataSource().getId(), "other"
    ).orElseThrow().getId();

    connector.events(documentEvent("seen", readableDocument("seen")));
    IngestionJobEntity nextJob = pendingJob(fixture);
    worker.run(nextJob.getId());

    assertThat(documents.findById(seenId).orElseThrow().getDeletedAt()).isNull();
    assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNotNull();
    assertThat(documents.findById(otherId).orElseThrow().getDeletedAt()).isNull();
    assertThat(ingestionJobs.findById(nextJob.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.SUCCEEDED);
}

@Test
void emptySuccessfulFullSnapshotSoftDeletesAllSourceDocuments() {
    Fixture fixture = fixture("empty-full-snapshot", Map.of("cursor", "before"));
    connector.fullSnapshot();
    connector.events(documentEvent("missing", readableDocument("missing")));
    worker.run(fixture.job().getId());
    UUID missingId = documents.findByDataSourceIdAndExternalId(
        fixture.dataSource().getId(), "missing"
    ).orElseThrow().getId();

    connector.events();
    IngestionJobEntity emptyJob = pendingJob(fixture);
    worker.run(emptyJob.getId());

    assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNotNull();
    assertThat(ingestionJobs.findById(emptyJob.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.SUCCEEDED);
}
```

- [ ] **Step 3: Write failing safety-gate tests**

Add these tests:

```java
@Test
void partialFailedFullSnapshotDoesNotSoftDeleteUnseenDocuments() {
    Fixture fixture = fixture("partial-full-snapshot", Map.of("cursor", "before"));
    connector.fullSnapshot();
    connector.events(
        documentEvent("seen", readableDocument("seen")),
        documentEvent("missing", readableDocument("missing"))
    );
    worker.run(fixture.job().getId());
    UUID missingId = documents.findByDataSourceIdAndExternalId(
        fixture.dataSource().getId(), "missing"
    ).orElseThrow().getId();

    connector.events(documentEvent("seen", readableDocument("seen")));
    connector.failures(failureEvent("missing", "일시적으로 접근할 수 없습니다"));
    IngestionJobEntity partialJob = pendingJob(fixture);
    worker.run(partialJob.getId());

    assertThat(ingestionJobs.findById(partialJob.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.PARTIAL_FAILED);
    assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNull();
    assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow())
        .satisfies(source -> assertThat(source.syncCursorValue()).containsEntry("cursor", "after"));
}

@Test
void allFailedFullSnapshotDoesNotSoftDeleteUnseenDocuments() {
    Fixture fixture = fixture("failed-full-snapshot", Map.of("cursor", "before"));
    connector.fullSnapshot();
    connector.events(documentEvent("missing", readableDocument("missing")));
    worker.run(fixture.job().getId());
    UUID missingId = documents.findByDataSourceIdAndExternalId(
        fixture.dataSource().getId(), "missing"
    ).orElseThrow().getId();

    connector.events();
    connector.failures(failureEvent("root", "루트를 읽지 못했습니다"));
    IngestionJobEntity failedJob = pendingJob(fixture);
    worker.run(failedJob.getId());

    assertThat(ingestionJobs.findById(failedJob.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.FAILED);
    assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNull();
}

@Test
void connectorFailureAfterDocumentDoesNotSoftDeleteUnseenDocuments() {
    Fixture fixture = fixture("interrupted-full-snapshot", Map.of("cursor", "before"));
    connector.fullSnapshot();
    connector.events(
        documentEvent("seen", readableDocument("seen")),
        documentEvent("missing", readableDocument("missing"))
    );
    worker.run(fixture.job().getId());
    UUID missingId = documents.findByDataSourceIdAndExternalId(
        fixture.dataSource().getId(), "missing"
    ).orElseThrow().getId();

    connector.events(documentEvent("seen", readableDocument("seen")));
    connector.throwAfterDocuments(new IllegalStateException("source interrupted"));
    IngestionJobEntity interruptedJob = pendingJob(fixture);
    worker.run(interruptedJob.getId());

    assertThat(ingestionJobs.findById(interruptedJob.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.FAILED);
    assertThat(documents.findById(missingId).orElseThrow().getDeletedAt()).isNull();
}
```

- [ ] **Step 4: Run the worker tests and verify RED**

Run:

```bash
./gradlew test --tests com.mydata.ingestion.IngestionWorkerIntegrationTest
```

Expected: full snapshot success leaves missing documents active because reconciliation is not implemented.

- [ ] **Step 5: Add the fail-closed native repository update**

Add to `ExternalDocumentRepository`:

```java
@Modifying(flushAutomatically = true)
@Query(value = """
    UPDATE external_documents document
    SET deleted_at = now(),
        updated_at = now()
    FROM ingestion_jobs job
    WHERE job.id = :jobId
      AND job.status = 'SUCCEEDED'
      AND document.workspace_id = job.workspace_id
      AND document.data_source_id = job.data_source_id
      AND document.deleted_at IS NULL
      AND NOT EXISTS (
          SELECT 1
          FROM ingestion_job_items failed
          WHERE failed.job_id = job.id
            AND failed.status = 'FAILED'
      )
      AND NOT EXISTS (
          SELECT 1
          FROM ingestion_job_items seen
          WHERE seen.job_id = job.id
            AND seen.status = 'SUCCEEDED'
            AND seen.document_id = document.id
      )
    """, nativeQuery = true)
int softDeleteUnseenForSucceededFullSnapshot(@Param("jobId") UUID jobId);
```

Add `Modifying`, `Query` and `Param` imports. Do not use `clearAutomatically`, because finalization still manages job and source entities.

- [ ] **Step 6: Gate reconciliation in worker finalization**

Inject `ExternalDocumentRepository` into `IngestionWorker`. Pass `connector.reconciliationMode()` to `finalizeJob` only after `fetchChanges` returns normally.

Change the success branch to this order inside the existing finalization transaction:

```java
job.markSucceeded();
ingestionJobs.flush();
int softDeletedDocumentCount = 0;
if (reconciliationMode == ConnectorReconciliationMode.FULL_SNAPSHOT) {
    softDeletedDocumentCount = documents.softDeleteUnseenForSucceededFullSnapshot(jobId);
    log.info(
        "전체 snapshot 문서 정리 완료: job={}, dataSource={}, softDeleted={}",
        jobId,
        dataSourceId,
        softDeletedDocumentCount
    );
}
if (nextCursor != null) {
    dataSource.replaceSyncCursor(nextCursor.value());
}
dataSource.markSynced();
```

The repository SQL checks `SUCCEEDED` and absence of failed items again. Any exception rolls the whole finalization transaction back and the existing outer handler marks the job `FAILED` in a separate transaction.

- [ ] **Step 7: Run worker tests and verify GREEN**

Run the same worker integration test command. Expected: success sweeps only unseen documents; partial, total, and exception failures preserve prior documents.

- [ ] **Step 8: Commit reconciliation behavior**

```bash
git add src/main/java/com/mydata/documents/ExternalDocumentRepository.java src/main/java/com/mydata/ingestion/IngestionWorker.java src/test/java/com/mydata/ingestion/IngestionWorkerIntegrationTest.java
git commit -m "feat(ingestion): reconcile successful full snapshots" -m "성공 job item을 DB seen set으로 사용해 완전히 성공한 full snapshot에서만 누락 문서를 소프트 삭제합니다. 부분·전체·인프라 실패에는 sweep을 실행하지 않습니다.

검증: IngestionWorkerIntegrationTest 통과. Playwright: UI 변경 없음."
```

---

### Task 4: Serialize Jobs Per Data Source

**Files:**
- Modify: `src/main/java/com/mydata/datasources/DataSourceRepository.java`
- Modify: `src/main/java/com/mydata/ingestion/IngestionJobRepository.java`
- Modify: `src/main/java/com/mydata/ingestion/IngestionWorker.java`
- Modify: `src/test/java/com/mydata/ingestion/IngestionWorkerIntegrationTest.java`

**Interfaces:**
- Produces: `DataSourceRepository.findByIdForUpdate(UUID id)` using `PESSIMISTIC_WRITE`
- Produces: `IngestionJobRepository.markPendingJobRunningIfDataSourceIdle(UUID id)`
- Produces: claim that returns false and leaves a queued job `PENDING` while another job for the source is `RUNNING`
- Consumes: `uq_ingestion_jobs_running_data_source` from Task 1

- [ ] **Step 1: Add a blocking test-connector seam**

Change `fetchCount` to `AtomicInteger` and add this one-shot blocker to `TestConnector`:

```java
private final AtomicInteger fetchCount = new AtomicInteger();
private final AtomicBoolean blockNextFetch = new AtomicBoolean();
private volatile CountDownLatch blockedFetchStarted = new CountDownLatch(0);
private volatile CountDownLatch blockedFetchRelease = new CountDownLatch(0);

void blockNextFetch() {
    blockedFetchStarted = new CountDownLatch(1);
    blockedFetchRelease = new CountDownLatch(1);
    blockNextFetch.set(true);
}

boolean awaitBlockedFetch() throws InterruptedException {
    return blockedFetchStarted.await(5, TimeUnit.SECONDS);
}

void releaseBlockedFetch() {
    blockedFetchRelease.countDown();
}

int fetchCount() {
    return fetchCount.get();
}
```

At the beginning of `fetchChanges`, use:

```java
fetchCount.incrementAndGet();
transactionActiveDuringFetch = TransactionSynchronizationManager.isActualTransactionActive();
if (blockNextFetch.compareAndSet(true, false)) {
    blockedFetchStarted.countDown();
    try {
        if (!blockedFetchRelease.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("blocked fetch release timeout");
        }
    } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("blocked fetch interrupted", exception);
    }
}
```

Reset the atomic count and blocker state and replace both latches with count-zero instances in `reset()`.

- [ ] **Step 2: Write the failing concurrent job test**

Add this test and Java concurrency imports:

```java
@Test
void sameDataSourceJobsRunSerially() throws Exception {
    Fixture fixture = fixture("serialized", Map.of("cursor", "before"));
    IngestionJobEntity secondJob = pendingJob(fixture);
    connector.events(documentEvent("first", readableDocument("first")));
    connector.blockNextFetch();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        Future<?> firstRun = executor.submit(() -> worker.run(fixture.job().getId()));
        assertThat(connector.awaitBlockedFetch()).isTrue();
        try {
            Future<?> competingRun = executor.submit(() -> worker.run(secondJob.getId()));
            competingRun.get(5, TimeUnit.SECONDS);

            assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow().getStatus())
                .isEqualTo(IngestionJobStatus.RUNNING);
            assertThat(ingestionJobs.findById(secondJob.getId()).orElseThrow().getStatus())
                .isEqualTo(IngestionJobStatus.PENDING);
            assertThat(connector.fetchCount()).isEqualTo(1);
        } finally {
            connector.releaseBlockedFetch();
        }
        firstRun.get(5, TimeUnit.SECONDS);
    }

    assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.SUCCEEDED);
    worker.run(secondJob.getId());
    assertThat(ingestionJobs.findById(secondJob.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.SUCCEEDED);
    assertThat(connector.fetchCount()).isEqualTo(2);
}
```

- [ ] **Step 3: Run the concurrent test and verify RED**

Run:

```bash
./gradlew test --tests com.mydata.ingestion.IngestionWorkerIntegrationTest.sameDataSourceJobsRunSerially
```

Expected: without source-level claim protection, the second claim attempts to become `RUNNING`; the new unique index either rejects it or the connector is entered twice instead of leaving the job safely `PENDING`.

- [ ] **Step 4: Add a short pessimistic source lock**

Add to `DataSourceRepository`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT dataSource FROM DataSourceEntity dataSource WHERE dataSource.id = :id")
Optional<DataSourceEntity> findByIdForUpdate(@Param("id") UUID id);
```

Import `jakarta.persistence.LockModeType` and `org.springframework.data.jpa.repository.Lock`.

- [ ] **Step 5: Make the native claim conditional on source state**

Rename the repository method to `markPendingJobRunningIfDataSourceIdle`, alias the target table, and use this full native update:

```sql
UPDATE ingestion_jobs target
SET status = 'RUNNING',
    started_at = now(),
    finished_at = NULL,
    error_message = NULL
WHERE target.id = :id
  AND target.status = 'PENDING'
  AND NOT EXISTS (
      SELECT 1
      FROM ingestion_jobs running
      WHERE running.data_source_id = target.data_source_id
        AND running.status = 'RUNNING'
  )
```

The full update remains an atomic `PENDING -> RUNNING` transition.

- [ ] **Step 6: Serialize the claim transaction in the worker**

Replace the claim callback with:

```java
return Boolean.TRUE.equals(transactions.execute(status -> {
    IngestionJobEntity job = loadJob(jobId);
    if (job.getStatus() != IngestionJobStatus.PENDING) {
        return false;
    }
    dataSources.findByIdForUpdate(job.getDataSourceId())
        .orElseThrow(() -> new IllegalStateException(
            "데이터소스를 찾을 수 없습니다: " + job.getDataSourceId()
        ));
    return ingestionJobs.markPendingJobRunningIfDataSourceIdle(jobId) == 1;
}));
```

The row lock is released when this short claim transaction commits. The persisted `RUNNING` status then blocks later claims; no lock is held during connector calls.

- [ ] **Step 7: Run worker and migration tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.mydata.ingestion.IngestionWorkerIntegrationTest --tests com.mydata.admin.AdminSchemaMigrationTest
```

Expected: same-source jobs serialize, different existing claim tests still pass, and the DB unique invariant remains present.

- [ ] **Step 8: Commit claim serialization**

```bash
git add src/main/java/com/mydata/datasources/DataSourceRepository.java src/main/java/com/mydata/ingestion/IngestionJobRepository.java src/main/java/com/mydata/ingestion/IngestionWorker.java src/test/java/com/mydata/ingestion/IngestionWorkerIntegrationTest.java
git commit -m "fix(ingestion): serialize jobs per data source" -m "claim 시 data source row를 짧게 잠그고 기존 RUNNING job을 확인해 동일 source의 snapshot이 겹치지 않게 했습니다. 대기 job은 PENDING으로 유지됩니다.

검증: worker 및 migration 관련 Gradle 테스트 통과. Playwright: UI 변경 없음."
```

---

### Task 5: Connector and Search Regression Coverage

**Files:**
- Modify: `src/test/java/com/mydata/ingestion/NotionIngestionIntegrationTest.java`
- Modify: `src/test/java/com/mydata/ingestion/SlackIngestionIntegrationTest.java`
- Modify: `src/test/java/com/mydata/retrieval/PgVectorSearchRepositoryTest.java`
- Modify: `docs/notion-integration-setup.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: Notion `FULL_SNAPSHOT`, Slack `NONE`, mapped `deletedAt`, and worker reconciliation
- Produces: end-to-end evidence for removal, same-ID restoration, partial failure preservation, and search visibility

- [ ] **Step 1: Write the Notion removal and restoration integration test**

Add these helpers to `NotionIngestionIntegrationTest`:

```java
private IngestionJobEntity pendingJob(
    WorkspaceEntity workspace,
    DataSourceEntity dataSource,
    UserEntity owner
) {
    return ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
        workspace.getId(), dataSource.getId(), IngestionTriggerType.MANUAL, owner.getId()
    ));
}

private NotionApiClient.NotionBlock childPage(String id, String title) {
    return new NotionApiClient.NotionBlock(id, "child_page", title, false, null);
}
```

Add the full fake-client integration test:

```java
@Test
void workerSoftDeletesAndRestoresNotionPageAcrossSuccessfulSnapshots() {
    String suffix = java.util.UUID.randomUUID().toString();
    UserEntity owner = users.save(UserEntity.create(
        "notion-reconcile-" + suffix + "@example.com", "Notion Reconcile Owner"
    ));
    WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(
        owner.getId(), "Notion reconcile workspace"
    ));
    DataSourceEntity dataSource = DataSourceEntity.create(
        workspace.getId(), DataSourceType.NOTION, "Notion reconcile",
        DataSourceStatus.ACTIVE, SyncMode.MANUAL
    );
    dataSource.assignOwner(owner.getId());
    dataSource.putConfig(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root");
    dataSource = dataSources.saveAndFlush(dataSource);
    notion.page("root", "Root", "https://notion.so/root");
    notion.page("child", "Child", "https://notion.so/child");
    notion.blocks("root", childPage("child", "Child"));
    notion.blocks("child");

    worker.run(pendingJob(workspace, dataSource, owner).getId());
    UUID childDocumentId = documents.findByDataSourceIdAndExternalId(
        dataSource.getId(), "child"
    ).orElseThrow().getId();

    notion.blocks("root");
    IngestionJobEntity removalJob = pendingJob(workspace, dataSource, owner);
    worker.run(removalJob.getId());
    assertThat(documents.findById(childDocumentId).orElseThrow().getDeletedAt()).isNotNull();

    notion.blocks("root", childPage("child", "Child"));
    IngestionJobEntity restorationJob = pendingJob(workspace, dataSource, owner);
    worker.run(restorationJob.getId());

    ExternalDocumentEntity restored = documents
        .findByDataSourceIdAndExternalId(dataSource.getId(), "child")
        .orElseThrow();
    assertThat(restored.getId()).isEqualTo(childDocumentId);
    assertThat(restored.getDeletedAt()).isNull();
    assertThat(ingestionJobs.findById(restorationJob.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.SUCCEEDED);
}
```

- [ ] **Step 2: Strengthen the Notion partial failure test**

Before running the existing sibling-database partial failure job, add:

```java
ExternalDocumentEntity previouslySeen = documents.saveAndFlush(ExternalDocumentEntity.create(
    workspace.getId(),
    dataSource.getId(),
    "previously-seen",
    DataSourceType.NOTION.name(),
    "Previously seen",
    "previously-seen-hash"
));
```

After the job, add:

```java
assertThat(documents.findById(previouslySeen.getId()).orElseThrow().getDeletedAt()).isNull();
```

- [ ] **Step 3: Strengthen the Slack incremental regression**

In `workerPersistsAndReusesSlackSyncCursor`, capture the first message document ID after the first job. After the second job assert:

```java
ExternalDocumentEntity firstMessage = documents.findById(firstMessageId).orElseThrow();
assertThat(firstMessage.getDeletedAt()).isNull();
assertThat(documents.findByDataSourceIdAndExternalId(
    dataSource.getId(), "C123:1710000005.000100"
)).isPresent();
```

- [ ] **Step 4: Add search exclusion and restoration coverage**

Add this test to `PgVectorSearchRepositoryTest`:

```java
@Test
void excludesSoftDeletedDocumentAndReturnsItAfterRestoration() {
    UserEntity owner = users.save(UserEntity.create(
        "tombstone-retrieval-owner@example.com", "Tombstone Owner"
    ));
    WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(
        owner.getId(), "Tombstone retrieval workspace"
    ));
    DataSourceEntity source = dataSources.save(DataSourceEntity.create(
        workspace.getId(), DataSourceType.LOCAL_TEXT, "Tombstone note",
        DataSourceStatus.ACTIVE, SyncMode.MANUAL
    ));
    source.putConfig("externalId", "tombstone-note");
    source.putConfig("title", "Tombstone note");
    source.putConfig("content", "tombstone searchable content");
    source.putConfig("principalKey", PrincipalKeys.user(owner.getId()));
    source = dataSources.saveAndFlush(source);
    worker.run(jobs.saveAndFlush(IngestionJobEntity.pending(
        workspace.getId(), source.getId(), IngestionTriggerType.MANUAL, owner.getId()
    )).getId());
    UUID documentId = jdbcTemplate.queryForObject(
        "SELECT id FROM external_documents WHERE data_source_id = ? AND external_id = ?",
        UUID.class,
        source.getId(),
        "tombstone-note"
    );
    String principal = PrincipalKeys.user(owner.getId());

    assertThat(retrievalService.retrieve(
        workspace.getId(), List.of(principal), "tombstone searchable", 5
    )).hasSize(1);
    jdbcTemplate.update("UPDATE external_documents SET deleted_at = now() WHERE id = ?", documentId);
    assertThat(retrievalService.retrieve(
        workspace.getId(), List.of(principal), "tombstone searchable", 5
    )).isEmpty();
    jdbcTemplate.update("UPDATE external_documents SET deleted_at = NULL WHERE id = ?", documentId);
    assertThat(retrievalService.retrieve(
        workspace.getId(), List.of(principal), "tombstone searchable", 5
    )).hasSize(1);
}
```

- [ ] **Step 5: Run connector and search tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.mydata.ingestion.NotionIngestionIntegrationTest --tests com.mydata.ingestion.SlackIngestionIntegrationTest --tests com.mydata.retrieval.PgVectorSearchRepositoryTest
```

Expected: Notion removal and restoration, partial-failure preservation, Slack history preservation, and tombstone search visibility all PASS.

- [ ] **Step 6: Document the resynchronization semantics**

Add a `재수집과 삭제 반영` section after automatic database collection in `docs/notion-integration-setup.md` with this content:

```markdown
## 7. 재수집과 삭제 반영

Notion 수집이 완전히 성공하면 이번 수집 범위에서 발견되지 않은 기존 페이지와 database row는 소프트 삭제되어 새 검색 결과에서 제외됩니다. 같은 Notion ID가 다시 수집 범위에 나타나면 기존 문서 ID를 유지한 채 복구됩니다.

일부 페이지나 database를 읽지 못해 `PARTIAL_FAILED` 또는 `FAILED`가 되면 누락 문서 정리를 실행하지 않습니다. 권한이나 일시적인 API 오류 때문에 기존 문서가 잘못 삭제되지 않도록, 다음 완전 성공 수집에서만 현재 구조를 확정합니다.
```

Renumber following headings. Extend the README Notion capability bullet to mention successful resync soft deletion and same-ID restoration.

- [ ] **Step 7: Commit end-to-end coverage and docs**

```bash
git add src/test/java/com/mydata/ingestion/NotionIngestionIntegrationTest.java src/test/java/com/mydata/ingestion/SlackIngestionIntegrationTest.java src/test/java/com/mydata/retrieval/PgVectorSearchRepositoryTest.java docs/notion-integration-setup.md README.md
git commit -m "test(notion): cover snapshot removal and restoration" -m "Notion 삭제·복구, 부분 실패 보존, Slack 증분 이력 보존과 검색 tombstone 경계를 통합 테스트로 검증하고 운영 문서를 갱신했습니다.

검증: Notion, Slack, pgvector 관련 Gradle 테스트 통과. Playwright: UI 변경 없음."
```

---

### Task 6: Full Verification and Review

**Files:**
- Review: all files changed since `b3087a0`
- Preserve: `.playwright-mcp/` untracked user directory

**Interfaces:**
- Consumes: all previous task commits
- Produces: fresh full-suite, build, migration, and independent review evidence

- [ ] **Step 1: Run formatting and diff checks**

```bash
git diff --check b3087a0..HEAD
git status --short
```

Expected: no whitespace errors; only intentional tracked changes and the pre-existing untracked `.playwright-mcp/` directory.

- [ ] **Step 2: Run the full automated test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, zero failed tests.

- [ ] **Step 3: Run a fresh full build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`, zero compilation, test, or packaging failures.

- [ ] **Step 4: Inspect migration and database state**

Confirm the test evidence reports 7 Liquibase changesets and both reconciliation indexes. If the local development DB is running, query `pg_indexes` and verify no duplicate `RUNNING` jobs exist before restarting the application with migration 007.

- [ ] **Step 5: Request independent whole-branch code review**

Give the reviewer the design spec, this plan, commit range `b3087a0..HEAD`, and full diff. Require explicit findings for correctness, transaction boundaries, concurrency, fail-closed behavior, migration safety, and test gaps. Fix every Critical or Important finding with a focused regression test, then rerun the affected tests.

- [ ] **Step 6: Rerun verification after review fixes**

```bash
./gradlew test
./gradlew build
git diff --check b3087a0..HEAD
```

Expected: both Gradle commands succeed and the diff check is clean. No Playwright run is required because no frontend or GraphQL UI contract changes.
