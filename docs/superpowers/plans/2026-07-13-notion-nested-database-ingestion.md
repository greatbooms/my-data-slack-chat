# Notion 하위 데이터베이스 자동 수집 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Notion 페이지 링크 하나에서 하위 페이지와 데이터베이스 행을 재귀 수집하고, 문서별 짧은 트랜잭션과 실패 상세를 통해 성공·부분 실패·전체 실패를 구분한다.

**Architecture:** Notion HTTP client는 block/data-source 응답을 API 페이지 단위로 전달하고, connector는 하나의 방문 상태로 page와 database를 순회하며 성공·실패 event를 worker에 보낸다. Worker는 JPA entity 대신 불변 data source snapshot을 connector에 넘기고, 문서 upsert와 성공 item 또는 실패 item을 각각 짧은 트랜잭션으로 저장한 뒤 item 집계로 job을 종료한다. 관리자 GraphQL과 React UI는 job 목록에는 집계만 싣고, 선택한 실패 job의 item만 keyset pagination으로 지연 조회한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA, Spring GraphQL, PostgreSQL 16 + pgvector, Liquibase, JUnit 5, Testcontainers, React 19, TypeScript 5.8, TanStack Query 5, GraphQL Code Generator, Vitest, Testing Library, Playwright

## Global Constraints

- 설계 기준은 `docs/superpowers/specs/2026-07-13-notion-nested-database-ingestion-design.md`이다.
- 페이지 모드는 root 아래의 모든 `child_page`와 `child_database`를 재귀적으로 처리하고 page/database ID별 첫 처리 결과만 유지한다.
- `link_to_page`는 따라가지 않고, 페이지 모드에 database 자체 링크를 넣어 root 타입을 자동 전환하지 않는다.
- database는 data source가 정확히 하나일 때만 query하며 Notion view 필터가 아닌 그 data source의 전체 page 결과를 수집한다.
- Notion API version `2026-03-11`, request timeout, token 비노출 오류 형식을 유지한다.
- 외부 API 호출 중 DB 트랜잭션을 열지 않고, 문서·ACL·chunk·embedding·성공 item은 문서 한 건당 하나의 짧은 트랜잭션으로 저장한다.
- 문서 저장 실패는 그 문서 트랜잭션만 rollback한 뒤 별도 트랜잭션으로 실패 item을 기록하고 다음 형제를 계속 처리한다.
- 전체 문서를 메모리에 모으지 않고 현재 문서 본문, API 응답 최대 100개, 방문 ID 집합과 item 개수만 유지한다.
- 성공 0개 이상·실패 0개는 `SUCCEEDED`, 성공 1개 이상·실패 1개 이상은 `PARTIAL_FAILED`, 성공 0개·실패 1개 이상은 `FAILED`다.
- `SUCCEEDED`만 cursor와 `last_synced_at`을 갱신하며 `PARTIAL_FAILED`와 `FAILED`는 기존 값을 유지한다.
- 기존 `ingestion_job_items` 테이블을 그대로 사용하므로 Liquibase changeset을 추가하거나 초기 schema를 수정하지 않는다.
- Notion 자동 발견 문서는 root data source의 ACL principal을 상속하고 principal이 비어 있으면 기존 pipeline 검증이 fail-closed로 실패해야 한다.
- job item reason에는 token, Authorization header, 원본 response body와 stack trace를 저장하지 않는다.
- 자동 테스트는 실제 Notion API나 실제 token을 사용하지 않고 정상·부분 실패·전체 실패를 포함한다.
- GraphQL operation 변경 후 `npm run codegen`으로 `frontend/admin/src/generated`를 생성하며 생성 파일을 직접 편집하지 않는다.
- UI 작업 완료 전 Playwright로 정상 수집과 부분 실패 상세·더 보기를 실제 화면에서 확인한다.
- 사용자-facing UI, 오류와 문서는 한국어로 작성한다.
- 실제 비밀값과 `.env`를 출력하거나 커밋하지 않고 사용자 소유의 미추적 `.playwright-mcp/`를 수정하거나 스테이징하지 않는다.
- 커밋 제목은 Conventional Commits 형식으로 쓰고 본문에 작업 내용, 자동 테스트, Playwright 해당 여부 또는 실제 확인 결과를 기록한다.

## File Map

- `src/main/java/com/mydata/connectors/notion/NotionClient.java`: Notion batch streaming 계약.
- `src/main/java/com/mydata/connectors/notion/NotionApiClient.java`: block/data-source HTTP pagination, block parsing.
- `src/main/java/com/mydata/connectors/notion/NotionApiException.java`: 정제된 status/code 오류 정보.
- `src/main/java/com/mydata/connectors/notion/NotionPageConnector.java`: page/database 통합 재귀 순회와 실패 event 생성.
- `src/main/java/com/mydata/connectors/core/DataSourceSnapshot.java`: connector에 전달할 불변 data source 설정과 cursor.
- `src/main/java/com/mydata/connectors/core/ConnectorItemReference.java`: item 종류·ID·제목·전체 경로와 job item external ID.
- `src/main/java/com/mydata/connectors/core/ConnectorItemType.java`: `PAGE`, `DATABASE`, `DATA_SOURCE`, `BLOCK` 종류.
- `src/main/java/com/mydata/connectors/core/ConnectorFailureStage.java`: `RETRIEVE`, `LIST_BLOCKS`, `QUERY`, `PERSIST` 단계.
- `src/main/java/com/mydata/connectors/core/ConnectorDocumentEvent.java`: 성공 문서와 대상 reference.
- `src/main/java/com/mydata/connectors/core/ConnectorFailureEvent.java`: 실패 대상·단계·사용자용 원인.
- `src/main/java/com/mydata/connectors/core/ConnectorEventSink.java`: 성공·실패 callback 계약.
- `src/main/java/com/mydata/connectors/core/DataSourceConnector.java`: snapshot/event 기반 connector 계약.
- `src/main/java/com/mydata/ingestion/IngestionJobItemEntity.java`: 기존 `ingestion_job_items` JPA 매핑.
- `src/main/java/com/mydata/ingestion/IngestionJobItemStatus.java`: `SUCCEEDED`, `FAILED` item 상태.
- `src/main/java/com/mydata/ingestion/IngestionJobItemCountProjection.java`: 여러 job의 성공·실패 count batch projection.
- `src/main/java/com/mydata/ingestion/IngestionJobItemRepository.java`: item 저장·집계·관리자 pagination.
- `src/main/java/com/mydata/ingestion/IngestionWorker.java`: claim, snapshot load, 건별 저장, 최종 상태 결정.
- `src/main/java/com/mydata/ingestion/IngestionPipelineService.java`: document ID를 반환하는 document upsert 경계.
- `src/main/java/com/mydata/admin/datasources/AdminIngestionJobService.java`: 관리자 job 집계와 item cursor 조회.
- `src/main/resources/graphql/admin.graphqls`: 부분 실패·count·item page GraphQL 계약.
- `frontend/admin/src/routes/IngestionJobHistoryPanel.tsx`: job 표와 선택한 실패 item 상세.
- `frontend/admin/src/routes/DataSourcesPage.tsx`: 선택한 data source를 history panel에 연결.
- `frontend/admin/src/graphql/admin.graphql`: typed job/item operation.
- `frontend/admin/src/api/adminGraphql.ts`: item page API 함수.
- `frontend/admin/src/App.test.tsx`: job 상세 lazy query와 더 보기 UI 테스트.
- `docs/notion-integration-setup.md`, `README.md`: 자동 발견, 공유 범위와 부분 실패 운영 안내.

---

### Task 1: Notion HTTP pagination을 batch streaming 계약으로 전환

**Files:**
- Modify: `src/main/java/com/mydata/connectors/notion/NotionClient.java:1-13`
- Modify: `src/main/java/com/mydata/connectors/notion/NotionApiClient.java:60-141,168-232,284-296,475-541`
- Modify: `src/main/java/com/mydata/connectors/notion/NotionApiException.java:1-13`
- Modify: `src/main/java/com/mydata/connectors/notion/NotionPageConnector.java:61-257`
- Test: `src/test/java/com/mydata/connectors/notion/NotionApiClientTest.java:122-330`
- Test: `src/test/java/com/mydata/connectors/notion/NotionPageConnectorTest.java:23-328`
- Test: `src/test/java/com/mydata/ingestion/NotionIngestionIntegrationTest.java:103-155`

**Interfaces:**
- Consumes: Notion REST `has_more`, `next_cursor`, `results`, `request_status`와 기존 page/database records.
- Produces: `void queryDataSourcePages(String, Consumer<List<NotionPage>>)`와 `void listBlockChildren(String, Consumer<List<NotionBlock>>)`; `NotionBlock.underlyingType()`; `NotionApiException.statusCode()`와 `code()`.

- [ ] **Step 1: API 응답 묶음이 다음 cursor 전에 전달되는 RED 테스트 작성**

기존 pagination 테스트를 callback 순서와 중간 실패를 검증하도록 바꾼다.

```java
@Test
void queryDataSourcePagesStreamsEachResponseBatchBeforeRequestingNextCursor() {
    AtomicBoolean firstBatchDelivered = new AtomicBoolean();
    server.createContext("/v1/data_sources/data-source-1/query", exchange -> {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!body.contains("start_cursor")) {
            respond(exchange, 200, pageQueryResponse(true, "cursor-2", "row-1", "First"));
            return;
        }
        assertThat(firstBatchDelivered).isTrue();
        respond(exchange, 200, pageQueryResponse(false, null, "row-2", "Second"));
    });
    List<List<String>> batches = new ArrayList<>();

    client().queryDataSourcePages("data-source-1", pages -> {
        batches.add(pages.stream().map(NotionApiClient.NotionPage::id).toList());
        firstBatchDelivered.set(true);
    });

    assertThat(batches).containsExactly(List.of("row-1"), List.of("row-2"));
}

@Test
void queryDataSourcePagesKeepsDeliveredBatchWhenLaterCursorFails() {
    AtomicInteger requests = new AtomicInteger();
    server.createContext("/v1/data_sources/data-source-1/query", exchange -> {
        if (requests.getAndIncrement() == 0) {
            respond(exchange, 200, pageQueryResponse(true, "cursor-2", "row-1", "First"));
            return;
        }
        respond(exchange, 404, """
            {"object":"error","code":"object_not_found","message":"hidden"}
            """);
    });
    List<String> delivered = new ArrayList<>();

    assertThatThrownBy(() -> client().queryDataSourcePages(
        "data-source-1",
        pages -> pages.forEach(page -> delivered.add(page.id()))
    ))
        .isInstanceOf(NotionApiException.class)
        .satisfies(error -> {
            NotionApiException notionError = (NotionApiException) error;
            assertThat(notionError.statusCode()).isEqualTo(404);
            assertThat(notionError.code()).isEqualTo("object_not_found");
        });
    assertThat(delivered).containsExactly("row-1");
}

private String pageQueryResponse(
    boolean hasMore,
    String nextCursor,
    String pageId,
    String title
) {
    String cursorJson = nextCursor == null ? "null" : "\"" + nextCursor + "\"";
    return """
        {
          "has_more": %s,
          "next_cursor": %s,
          "request_status": { "type": "complete" },
          "results": [{
            "object": "page",
            "id": "%s",
            "url": "https://notion.so/%s",
            "created_time": "2026-07-13T00:00:00.000Z",
            "last_edited_time": "2026-07-13T00:00:00.000Z",
            "properties": {
              "Name": {
                "type": "title",
                "title": [{ "plain_text": "%s" }]
              }
            }
          }]
        }
        """.formatted(hasMore, cursorJson, pageId, pageId, title);
}
```

block 경계에는 `listBlockChildrenStreamsEachResponseBatchBeforeRequestingNextCursor`, `listBlockChildrenStopsPaginationWhenConsumerFails`, `listBlockChildrenParsesChildDatabaseAndUnsupportedUnderlyingType`를 추가한다. 마지막 테스트 fixture는 다음 두 결과를 사용한다.

```json
[
  {
    "id": "database-1",
    "type": "child_database",
    "has_children": false,
    "child_database": { "title": "Roadmap" }
  },
  {
    "id": "unsupported-1",
    "type": "unsupported",
    "has_children": false,
    "unsupported": { "block_type": "child_database" }
  }
]
```

- [ ] **Step 2: Notion client 테스트를 실행해 RED 확인**

Run:

```bash
./gradlew test --tests 'com.mydata.connectors.notion.NotionApiClientTest'
```

Expected: `Consumer<List<NotionPage>>`, `Consumer<List<NotionBlock>>` overload와 `underlyingType`, structured exception accessor가 없어 compilation이 실패한다.

- [ ] **Step 3: batch consumer와 structured 오류를 최소 구현**

`NotionClient`를 다음 계약으로 바꾼다.

```java
public interface NotionClient {
    NotionApiClient.NotionPage retrievePage(String pageId);

    NotionApiClient.NotionDatabase retrieveDatabase(String databaseId);

    void queryDataSourcePages(
        String dataSourceId,
        Consumer<List<NotionApiClient.NotionPage>> batchConsumer
    );

    void listBlockChildren(
        String blockId,
        Consumer<List<NotionApiClient.NotionBlock>> batchConsumer
    );
}
```

두 pagination loop는 응답마다 immutable batch를 한 번 전달하고 callback 반환 후 다음 cursor로 이동한다.

```java
public void queryDataSourcePages(String dataSourceId, Consumer<List<NotionPage>> batchConsumer) {
    String nextCursor = null;
    do {
        JsonNode root = postJson(
            "/v1/data_sources/" + pathSegment(dataSourceId) + "/query",
            queryDataSourceRequestBody(nextCursor)
        );
        rejectIncompleteQuery(root);
        List<NotionPage> batch = new ArrayList<>();
        for (JsonNode result : root.path("results")) {
            if ("page".equals(result.path("object").asString())) {
                batch.add(toPage(result));
            }
        }
        batchConsumer.accept(List.copyOf(batch));
        nextCursor = root.path("has_more").asBoolean(false)
            ? blankToNull(root.path("next_cursor").asString(null))
            : null;
    } while (nextCursor != null);
}

public void listBlockChildren(String blockId, Consumer<List<NotionBlock>> batchConsumer) {
    String nextCursor = null;
    do {
        String path = "/v1/blocks/" + pathSegment(blockId) + "/children?page_size=" + PAGE_SIZE;
        if (nextCursor != null) {
            path += "&start_cursor=" + queryParam(nextCursor);
        }
        JsonNode root = getJson(path);
        List<NotionBlock> batch = new ArrayList<>();
        root.path("results").forEach(block -> batch.add(toBlock(block)));
        batchConsumer.accept(List.copyOf(batch));
        nextCursor = root.path("has_more").asBoolean(false)
            ? blankToNull(root.path("next_cursor").asString(null))
            : null;
    } while (nextCursor != null);
}
```

`NotionBlock`과 오류 타입은 다음 형태로 확장한다.

```java
public record NotionBlock(
    String id,
    String type,
    String plainText,
    boolean hasChildren,
    String underlyingType
) {
}
```

```java
public class NotionApiException extends RuntimeException {
    private final Integer statusCode;
    private final String code;

    public NotionApiException(String message) {
        this(message, null, null, null);
    }

    public NotionApiException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public NotionApiException(int statusCode, String code) {
        this(
            "Notion API 오류: status=" + statusCode + ", code=" + (code == null ? "unknown" : code),
            statusCode,
            code,
            null
        );
    }

    private NotionApiException(String message, Integer statusCode, String code, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.code = code;
    }

    public Integer statusCode() { return statusCode; }
    public String code() { return code; }
}
```

`child_page`와 `child_database`는 각 payload의 `title`을 읽고, `unsupported`는 `unsupported.block_type`을 nullable 문자열로 보존한다. HTTP 비정상 응답은 `new NotionApiException(response.statusCode(), code)`로 만든다.

- [ ] **Step 4: 기존 connector와 fake client를 consumer 호출로 이관**

현재 수집 의미를 바꾸지 않고 accumulator에 batch를 처리한다.

```java
private PageContent collectPageContent(String pageId) {
    List<String> lines = new ArrayList<>();
    List<String> childPageIds = new ArrayList<>();
    collectBlockChildren(pageId, lines, childPageIds, new HashSet<>());
    return new PageContent(lines, childPageIds);
}

private void collectBlockChildren(
    String blockId,
    List<String> lines,
    List<String> childPageIds,
    Set<String> visitedBlockIds
) {
    notionClient.listBlockChildren(blockId, blocks ->
        collectBlocks(blocks, lines, childPageIds, visitedBlockIds)
    );
}
```

명시적 database query와 test fake는 다음 callback 형태로 바꾼다.

```java
notionClient.queryDataSourcePages(dataSource.id(), pages -> {
    for (NotionApiClient.NotionPage page : pages) {
        fetchDatabasePage(
            database,
            databaseTitle,
            dataSource,
            page,
            null,
            databaseTitle,
            List.of(databaseTitle),
            1,
            principalKey,
            handler,
            visitedPageIds
        );
    }
});

public void queryDataSourcePages(
    String dataSourceId,
    Consumer<List<NotionApiClient.NotionPage>> batchConsumer
) {
    batchConsumer.accept(dataSourcePages.getOrDefault(dataSourceId, List.of()).stream()
        .map(pages::get)
        .toList());
}
```

- [ ] **Step 5: Notion client·connector 회귀 테스트 실행**

Run:

```bash
./gradlew test --tests 'com.mydata.connectors.notion.*' \
  --tests 'com.mydata.ingestion.NotionIngestionIntegrationTest'
```

Expected: API batch 순서·중간 실패·child database parsing과 기존 page/database 수집 테스트가 모두 PASS한다.

- [ ] **Step 6: Task 1 커밋**

```bash
git add src/main/java/com/mydata/connectors/notion \
  src/test/java/com/mydata/connectors/notion \
  src/test/java/com/mydata/ingestion/NotionIngestionIntegrationTest.java
git commit -m "refactor(notion): stream paginated API batches" \
  -m "Deliver Notion block and data-source responses batch-by-batch, preserve structured sanitized errors, and keep existing page/database ingestion behavior.

자동 테스트: ./gradlew test --tests com.mydata.connectors.notion.* --tests com.mydata.ingestion.NotionIngestionIntegrationTest
Playwright: UI 변경 없음, 해당 없음"
```

---

### Task 2: Connector event와 건별 수집 트랜잭션 구현

**Files:**
- Create: `src/main/java/com/mydata/connectors/core/DataSourceSnapshot.java`
- Create: `src/main/java/com/mydata/connectors/core/ConnectorItemType.java`
- Create: `src/main/java/com/mydata/connectors/core/ConnectorItemReference.java`
- Create: `src/main/java/com/mydata/connectors/core/ConnectorFailureStage.java`
- Create: `src/main/java/com/mydata/connectors/core/ConnectorDocumentEvent.java`
- Create: `src/main/java/com/mydata/connectors/core/ConnectorFailureEvent.java`
- Create: `src/main/java/com/mydata/connectors/core/ConnectorEventSink.java`
- Delete: `src/main/java/com/mydata/connectors/core/DocumentHandler.java`
- Modify: `src/main/java/com/mydata/connectors/core/DataSourceConnector.java:1-10`
- Modify: `src/main/java/com/mydata/datasources/DataSourceEntity.java:117-153`
- Modify: `src/main/java/com/mydata/connectors/local/LocalTextConnector.java:1-68`
- Modify: `src/main/java/com/mydata/connectors/slack/SlackChannelConnector.java:1-121`
- Modify: `src/main/java/com/mydata/connectors/slack/SlackRawDocumentFactory.java:1-121`
- Modify: `src/main/java/com/mydata/connectors/notion/NotionPageConnector.java:1-312`
- Modify: `src/main/java/com/mydata/slackbot/SlackMessageIngestionEventConsumer.java:45-65`
- Create: `src/main/java/com/mydata/ingestion/IngestionJobItemEntity.java`
- Create: `src/main/java/com/mydata/ingestion/IngestionJobItemStatus.java`
- Create: `src/main/java/com/mydata/ingestion/IngestionJobItemCountProjection.java`
- Create: `src/main/java/com/mydata/ingestion/IngestionJobItemRepository.java`
- Modify: `src/main/java/com/mydata/ingestion/IngestionJobEntity.java:55-79`
- Modify: `src/main/java/com/mydata/ingestion/IngestionPipelineService.java:46-81`
- Modify: `src/main/java/com/mydata/ingestion/IngestionWorker.java:1-100`
- Create: `src/test/java/com/mydata/ingestion/IngestionWorkerIntegrationTest.java`
- Modify: `src/test/java/com/mydata/ingestion/IngestionPipelineIntegrationTest.java:1-449`
- Modify: `src/test/java/com/mydata/ingestion/SlackIngestionIntegrationTest.java:1-216`
- Modify: `src/test/java/com/mydata/connectors/slack/SlackChannelConnectorTest.java`
- Modify: `src/test/java/com/mydata/connectors/notion/NotionPageConnectorTest.java`

**Interfaces:**
- Consumes: Task 1의 batch `NotionClient`, 기존 `ingestion_job_items` table, `TransactionTemplate`, pipeline ACL 검증.
- Produces: `SyncCursor DataSourceConnector.fetchChanges(DataSourceSnapshot, ConnectorEventSink)`; 성공/실패 event; `UUID IngestionPipelineService.ingest(UUID workspaceId, UUID dataSourceId, RawExternalDocument)`; item repository와 최종 job 상태 계산.

- [ ] **Step 1: 트랜잭션·부분 실패·cursor 규칙 RED 통합 테스트 작성**

`IngestionWorkerIntegrationTest`에 test connector를 `GOOGLE_DRIVE` 타입으로 등록하고 다음 케이스를 작성한다.

```java
@Test
void connectorRunsOutsideDatabaseTransaction() {
    Fixture fixture = fixture("outside-tx", Map.of("cursor", "before"));
    connector.events(documentEvent("first", readableDocument("first")));

    worker.run(fixture.job().getId());

    assertThat(connector.transactionActiveDuringFetch()).isFalse();
    assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.SUCCEEDED);
}

@Test
void secondDocumentPersistenceFailureRollsBackOnlyThatDocumentAndContinuesWithThird() {
    Fixture fixture = fixture("partial", Map.of("cursor", "before"));
    connector.events(
        documentEvent("first", readableDocument("first")),
        documentEvent("second", documentWithUnsupportedAcl("second")),
        documentEvent("third", readableDocument("third"))
    );

    worker.run(fixture.job().getId());

    assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "first")).isPresent();
    assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "second")).isEmpty();
    assertThat(documents.findByDataSourceIdAndExternalId(fixture.dataSource().getId(), "third")).isPresent();
    assertThat(jobItems.countByJobIdAndStatus(fixture.job().getId(), IngestionJobItemStatus.SUCCEEDED))
        .isEqualTo(2);
    assertThat(jobItems.countByJobIdAndStatus(fixture.job().getId(), IngestionJobItemStatus.FAILED))
        .isEqualTo(1);
    assertThat(ingestionJobs.findById(fixture.job().getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.PARTIAL_FAILED);
    assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow().syncCursorValue())
        .containsEntry("cursor", "before");
    assertThat(dataSources.findById(fixture.dataSource().getId()).orElseThrow().getLastSyncedAt())
        .isNull();
}

private record Fixture(DataSourceEntity dataSource, IngestionJobEntity job) { }

private Fixture fixture(String name, Map<String, Object> cursor) {
    String suffix = UUID.randomUUID().toString();
    UserEntity owner = users.save(UserEntity.create(
        "worker-" + suffix + "@example.com", "Worker Owner"
    ));
    WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), name));
    DataSourceEntity source = DataSourceEntity.create(
        workspace.getId(), DataSourceType.GOOGLE_DRIVE, name, DataSourceStatus.ACTIVE, SyncMode.MANUAL
    );
    source.assignOwner(owner.getId());
    source.replaceSyncCursor(cursor);
    source = dataSources.saveAndFlush(source);
    IngestionJobEntity job = ingestionJobs.saveAndFlush(IngestionJobEntity.pending(
        workspace.getId(), source.getId(), IngestionTriggerType.MANUAL, owner.getId()
    ));
    return new Fixture(source, job);
}

private ConnectorDocumentEvent documentEvent(String id, RawExternalDocument document) {
    return new ConnectorDocumentEvent(
        document,
        new ConnectorItemReference(ConnectorItemType.DATA_SOURCE, id, id, List.of(id))
    );
}

private RawExternalDocument readableDocument(String id) {
    return rawDocument(id, "READ");
}

private RawExternalDocument documentWithUnsupportedAcl(String id) {
    return rawDocument(id, "WRITE");
}

private RawExternalDocument rawDocument(String id, String permission) {
    return new RawExternalDocument(
        id, DataSourceType.GOOGLE_DRIVE, id, null, "text/plain", null, null,
        id + "-hash", Map.of(), new RawContent(id + " content", "text/plain"),
        List.of(new RawAclEntry("WORKSPACE:qa", permission, false, "TEST"))
    );
}

static final class TestConnector implements DataSourceConnector {
    private List<ConnectorDocumentEvent> events = List.of();
    private boolean transactionActiveDuringFetch;

    void events(ConnectorDocumentEvent... values) { events = List.of(values); }
    boolean transactionActiveDuringFetch() { return transactionActiveDuringFetch; }

    @Override
    public DataSourceType supports() { return DataSourceType.GOOGLE_DRIVE; }

    @Override
    public SyncCursor fetchChanges(DataSourceSnapshot source, ConnectorEventSink sink) {
        transactionActiveDuringFetch = TransactionSynchronizationManager.isActualTransactionActive();
        events.forEach(sink::onDocument);
        return new SyncCursor(Map.of("cursor", "after"));
    }
}
```

같은 fixture로 `allSuccessUpdatesCursorAndLastSyncedAt`, `allFailuresMarkFailedAndKeepCursorAndLastSyncedAt`, `emptyRunSucceeds`, `alreadyClaimedJobIsNotRunAgain`, `topLevelConnectorFailureMarksJobFailed`를 작성한다. 성공 item은 `documentId`가 있고 reason이 null이며 실패 item은 `documentId`가 null이고 `[DATA_SOURCE] second (PERSIST): 문서 저장에 실패했습니다`만 포함하는지 검증한다.

- [ ] **Step 2: worker 통합 테스트를 실행해 RED 확인**

Run:

```bash
./gradlew test --tests 'com.mydata.ingestion.IngestionWorkerIntegrationTest'
```

Expected: snapshot/event/item 타입과 `markPartialFailed`가 없어 compilation이 실패한다.

- [ ] **Step 3: 불변 snapshot과 event 계약 구현**

핵심 타입을 다음 시그니처로 고정한다.

```java
public record DataSourceSnapshot(
    UUID id,
    UUID workspaceId,
    UUID ownerUserId,
    DataSourceType type,
    DataSourceVisibility visibility,
    Map<String, Object> config,
    SyncCursor cursor
) {
    public DataSourceSnapshot {
        config = Map.copyOf(config);
        cursor = cursor == null ? new SyncCursor(Map.of()) : cursor;
    }

    public static DataSourceSnapshot from(DataSourceEntity source) {
        return new DataSourceSnapshot(
            source.getId(),
            source.getWorkspaceId(),
            source.getOwnerUserId(),
            source.getType(),
            source.getVisibility(),
            source.configValues(),
            new SyncCursor(source.syncCursorValue())
        );
    }

    public String configValue(String key) {
        Object value = config.get(key);
        return value instanceof String stringValue ? stringValue : null;
    }

    public String visibilityPrincipalKey() {
        return switch (visibility) {
            case PRIVATE -> PrincipalKeys.user(Objects.requireNonNull(ownerUserId));
            case WORKSPACE -> PrincipalKeys.workspace(workspaceId);
        };
    }
}
```

```java
public enum ConnectorItemType { PAGE, DATABASE, DATA_SOURCE, BLOCK }
public enum ConnectorFailureStage { RETRIEVE, LIST_BLOCKS, QUERY, PERSIST }

public record ConnectorItemReference(
    ConnectorItemType type,
    String externalId,
    String title,
    List<String> path
) {
    public ConnectorItemReference {
        path = List.copyOf(path);
    }

    public String qualifiedExternalId() {
        return type.name().toLowerCase(Locale.ROOT) + ":" + externalId;
    }

    public String displayPath() {
        return path.isEmpty() ? title : String.join(" / ", path);
    }
}

public record ConnectorDocumentEvent(
    RawExternalDocument document,
    ConnectorItemReference reference
) {
}

public record ConnectorFailureEvent(
    ConnectorItemReference reference,
    ConnectorFailureStage stage,
    String userSafeReason
) {
    public String formattedReason() {
        return "[%s] %s (%s): %s".formatted(
            reference.type(), reference.displayPath(), stage, userSafeReason
        );
    }
}

public interface ConnectorEventSink {
    void onDocument(ConnectorDocumentEvent event);
    void onFailure(ConnectorFailureEvent event);
}
```

`DataSourceEntity.configValues()`는 `Map.copyOf(readConfig())`를 반환한다. `DataSourceConnector`는 다음 하나의 실행 메서드만 가진다.

```java
public interface DataSourceConnector {
    DataSourceType supports();
    SyncCursor fetchChanges(DataSourceSnapshot dataSource, ConnectorEventSink sink);
}
```

LOCAL_TEXT와 Slack은 성공 문서를 `DATA_SOURCE` reference로 감싸고, Notion의 기존 page와 database row는 `PAGE` reference로 감싼다. 이에 따라 job item external ID는 설계의 `data-source:`, `page:`, `database:`, `block:` prefix 안에서만 생성된다. `SlackRawDocumentFactory`는 `DataSourceSnapshot`을 받는다.

- [ ] **Step 4: 기존 table에 맞는 item entity와 pipeline 반환값 구현**

`BaseEntity`는 `created_at`을 요구하므로 item entity는 직접 ID와 `processed_at`을 매핑한다.

```java
@Getter
@Entity
@Table(name = "ingestion_job_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngestionJobItemEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "external_id", columnDefinition = "text")
    private String externalId;

    @Column(name = "document_id")
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private IngestionJobItemStatus status;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt = OffsetDateTime.now();

    public static IngestionJobItemEntity succeeded(UUID jobId, String externalId, UUID documentId) {
        IngestionJobItemEntity item = new IngestionJobItemEntity();
        item.jobId = jobId;
        item.externalId = externalId;
        item.documentId = Objects.requireNonNull(documentId);
        item.status = IngestionJobItemStatus.SUCCEEDED;
        return item;
    }

    public static IngestionJobItemEntity failed(UUID jobId, String externalId, String reason) {
        IngestionJobItemEntity item = new IngestionJobItemEntity();
        item.jobId = jobId;
        item.externalId = externalId;
        item.status = IngestionJobItemStatus.FAILED;
        item.reason = reason;
        return item;
    }
}
```

```java
public enum IngestionJobItemStatus { SUCCEEDED, FAILED }

public interface IngestionJobItemRepository extends JpaRepository<IngestionJobItemEntity, UUID> {
    long countByJobIdAndStatus(UUID jobId, IngestionJobItemStatus status);
    List<IngestionJobItemEntity> findByJobIdOrderByProcessedAtAscIdAsc(UUID jobId);
}
```

`IngestionPipelineService.ingest`는 `workspaceId`, `dataSourceId`를 사용하고 unchanged/new 양쪽에서 document ID를 반환한다.

```java
@Transactional
public UUID ingest(UUID workspaceId, UUID dataSourceId, RawExternalDocument rawDocument) {
    validateAclEntries(rawDocument.aclEntries());
    var existingDocument = documents.findByDataSourceIdAndExternalId(dataSourceId, rawDocument.externalId());
    if (existingDocument.isPresent() && isUnchanged(existingDocument.get(), rawDocument)) {
        ExternalDocumentEntity document = existingDocument.get();
        updateDocumentFromIngestion(document, rawDocument);
        documents.saveAndFlush(document);
        backfillMissingEmbeddings(document);
        return document.getId();
    }
    ExternalDocumentEntity document = existingDocument.orElseGet(() -> ExternalDocumentEntity.create(
        workspaceId,
        dataSourceId,
        rawDocument.externalId(),
        rawDocument.sourceType().name(),
        rawDocument.title(),
        rawDocument.uri(),
        rawDocument.contentHash()
    ));
    updateDocumentFromIngestion(document, rawDocument);
    document = documents.saveAndFlush(document);
    replaceAclEntries(document, rawDocument.aclEntries());
    writeEmbeddings(replaceChunks(document, rawDocument.content().text()));
    return document.getId();
}
```

- [ ] **Step 5: worker에서 외부 호출과 건별 트랜잭션 분리**

claim 뒤 snapshot을 짧은 읽기 트랜잭션으로 만들고 connector는 그 transaction 반환 후 호출한다. Worker sink는 다음 규칙을 사용한다.

```java
private void ingestAndFinalize(UUID jobId) {
    DataSourceSnapshot source = transactions.execute(status -> loadSnapshot(jobId));
    DataSourceConnector connector = requireConnector(source.type());
    SyncCursor nextCursor = connector.fetchChanges(source, new ConnectorEventSink() {
        @Override
        public void onDocument(ConnectorDocumentEvent event) {
            try {
                transactions.executeWithoutResult(status -> {
                    UUID documentId = pipeline.ingest(
                        source.workspaceId(), source.id(), event.document()
                    );
                    jobItems.save(IngestionJobItemEntity.succeeded(
                        jobId, event.reference().qualifiedExternalId(), documentId
                    ));
                });
            } catch (RuntimeException persistenceFailure) {
                log.warn("수집 문서 저장 실패: job={}, externalId={}",
                    jobId, event.reference().qualifiedExternalId(), persistenceFailure);
                persistFailure(jobId, new ConnectorFailureEvent(
                    event.reference(),
                    ConnectorFailureStage.PERSIST,
                    "문서 저장에 실패했습니다"
                ));
            }
        }

        @Override
        public void onFailure(ConnectorFailureEvent event) {
            persistFailure(jobId, event);
        }
    });
    finalizeJob(jobId, source.id(), nextCursor);
}
```

`persistFailure`는 새 transaction에서 FAILED item 하나만 저장한다. item 저장 실패는 감싸거나 삼키지 않아 worker 최상위 `markFailed`로 전파한다. 최종 transaction은 repository count로 상태를 계산한다.

```java
private void finalizeJob(UUID jobId, UUID dataSourceId, SyncCursor nextCursor) {
    transactions.executeWithoutResult(status -> {
        long succeeded = jobItems.countByJobIdAndStatus(jobId, IngestionJobItemStatus.SUCCEEDED);
        long failed = jobItems.countByJobIdAndStatus(jobId, IngestionJobItemStatus.FAILED);
        IngestionJobEntity job = loadJob(jobId);
        if (failed == 0) {
            DataSourceEntity dataSource = dataSources.findActiveById(dataSourceId).orElseThrow();
            if (nextCursor != null) {
                dataSource.replaceSyncCursor(nextCursor.value());
            }
            dataSource.markSynced();
            job.markSucceeded();
        } else if (succeeded > 0) {
            job.markPartialFailed(succeeded + failed, failed);
        } else {
            job.markFailed("전체 " + failed + "개 항목 수집 실패");
        }
    });
}
```

`markPartialFailed`는 status, finishedAt, `전체 N개 중 M개 실패`만 갱신한다. direct Slack event consumer는 `pipeline.ingest(dataSource.getWorkspaceId(), dataSource.getId(), rawDocument)`로 바꾼다.

- [ ] **Step 6: worker·pipeline·connector 전체 회귀 테스트 실행**

Run:

```bash
./gradlew test --tests 'com.mydata.ingestion.*' \
  --tests 'com.mydata.connectors.local.*' \
  --tests 'com.mydata.connectors.slack.*' \
  --tests 'com.mydata.connectors.notion.*' \
  --tests 'com.mydata.slackbot.*'
```

Expected: connector 실행 시 transaction 비활성, 문서별 rollback, item 연결, 세 최종 상태, cursor 규칙과 기존 Slack/Notion/LOCAL_TEXT 동작이 PASS한다.

- [ ] **Step 7: Task 2 커밋**

```bash
git add src/main/java/com/mydata/connectors/core \
  src/main/java/com/mydata/connectors/local \
  src/main/java/com/mydata/connectors/slack \
  src/main/java/com/mydata/connectors/notion/NotionPageConnector.java \
  src/main/java/com/mydata/datasources/DataSourceEntity.java \
  src/main/java/com/mydata/ingestion \
  src/main/java/com/mydata/slackbot/SlackMessageIngestionEventConsumer.java \
  src/test/java/com/mydata/connectors \
  src/test/java/com/mydata/ingestion \
  src/test/java/com/mydata/slackbot
git commit -m "feat(ingestion): persist per-item outcomes" \
  -m "Run connectors outside database transactions, persist each document and job item atomically, and derive succeeded, partial-failed, and failed job outcomes without advancing failed cursors.

자동 테스트: ./gradlew test --tests com.mydata.ingestion.* --tests com.mydata.connectors.local.* --tests com.mydata.connectors.slack.* --tests com.mydata.connectors.notion.* --tests com.mydata.slackbot.*
Playwright: UI 변경 없음, 해당 없음"
```

---

### Task 3: Notion page/database 통합 재귀 순회와 부분 실패 구현

**Files:**
- Modify: `src/main/java/com/mydata/connectors/notion/NotionPageConnector.java:26-312`
- Test: `src/test/java/com/mydata/connectors/notion/NotionPageConnectorTest.java:23-328`
- Test: `src/test/java/com/mydata/ingestion/NotionIngestionIntegrationTest.java:36-155`

**Interfaces:**
- Consumes: Task 1의 batch Notion API와 structured `NotionApiException`, Task 2의 snapshot·event sink·item transaction.
- Produces: root/child/row가 공유하는 `visitedPageIds`, `visitedDatabaseIds`; child database 자동 발견; 대상별 `PAGE`, `DATABASE`, `BLOCK` failure event.

- [ ] **Step 1: 재귀 발견·중복 제거·계속 처리 RED 테스트 작성**

`NotionPageConnectorTest`에 recording sink를 추가한다.

```java
private static final class RecordingSink implements ConnectorEventSink {
    private final List<ConnectorDocumentEvent> documents = new ArrayList<>();
    private final List<ConnectorFailureEvent> failures = new ArrayList<>();

    @Override
    public void onDocument(ConnectorDocumentEvent event) { documents.add(event); }

    @Override
    public void onFailure(ConnectorFailureEvent event) { failures.add(event); }
}
```

정상 재귀 케이스는 `root -> child page -> database-1 -> row-1 -> database-2 -> row-2` fixture를 만들고 다음을 검증한다.

```java
@Test
void fetchChangesDiscoversDatabasesUnderRootChildAndRowPages() {
    FakeNotionClient notion = new FakeNotionClient();
    notion.page("root", "Root", "https://notion.so/root");
    notion.page("child", "Child", "https://notion.so/child");
    notion.page("row-1", "First row", "https://notion.so/row-1");
    notion.page("row-2", "Second row", "https://notion.so/row-2");
    notion.blocks("root", new NotionApiClient.NotionBlock(
        "child", "child_page", "Child", false, null
    ));
    notion.blocks("child", new NotionApiClient.NotionBlock(
        "database-1", "child_database", "Roadmap", false, null
    ));
    notion.blocks("row-1", new NotionApiClient.NotionBlock(
        "database-2", "child_database", "Subtasks", false, null
    ));
    notion.blocks("row-2");
    notion.database(
        "database-1", "Roadmap", "https://notion.so/database-1",
        new NotionApiClient.NotionDataSource("data-source-1", "Roadmap")
    );
    notion.database(
        "database-2", "Subtasks", "https://notion.so/database-2",
        new NotionApiClient.NotionDataSource("data-source-2", "Subtasks")
    );
    notion.queryPages("data-source-1", "row-1");
    notion.queryPages("data-source-2", "row-2");
    UUID ownerId = UUID.randomUUID();
    DataSourceSnapshot source = new DataSourceSnapshot(
        UUID.randomUUID(),
        UUID.randomUUID(),
        ownerId,
        DataSourceType.NOTION,
        DataSourceVisibility.PRIVATE,
        Map.of(NotionPageConnector.ROOT_PAGE_ID_CONFIG_KEY, "root"),
        new SyncCursor(Map.of())
    );
    RecordingSink sink = new RecordingSink();

    new NotionPageConnector(notion).fetchChanges(source, sink);

    assertThat(sink.documents)
        .extracting(event -> event.document().externalId())
        .containsExactly("root", "child", "row-1", "row-2");
    assertThat(sink.documents.get(2).reference().path())
        .containsExactly("Root", "Child", "Roadmap", "First row");
    assertThat(sink.failures).isEmpty();
}
```

실패·중복 케이스는 다음 메서드로 분리하고 각 fixture와 assertion을 고정한다.

- `fetchChangesDeduplicatesPagesAndDatabasesAcrossAllDiscoveryPaths`: root와 child에 같은 `database-1` block을 두고, `row-1`을 child page와 database query 양쪽에 둔다. document external ID는 `root`, `child`, `row-1`만 나오고 `retrieveDatabaseCalls("database-1")`, `queryCalls("data-source-1")`가 각각 1인지 검증한다.
- `childDatabaseFailureEmitsFailureAndContinuesWithSiblingDatabase`: `database-bad` retrieve는 `NotionApiException(404, "object_not_found")`, 다음 `database-good`은 `good-row`를 반환하게 한다. document에 root와 good-row가 있고 failure가 `DATABASE`, `RETRIEVE`, `Root / Hidden DB`, 공유 안내를 가지는지 검증한다.
- `queryFailureAfterFirstBatchKeepsRowsAndContinuesWithSiblingDatabase`: database-1 query 첫 batch에 row-1을 전달하고 다음 cursor에서 404를 던진 뒤 database-2 row-2를 전달한다. row-1과 row-2가 모두 있고 `database:database-1`의 `QUERY` failure가 하나인지 검증한다.
- `rowBlockFailureEmitsPageFailureAndContinuesWithNextRow`: row-1 block 조회만 실패시키고 row-2를 정상 처리한다. row-1 document는 없고 row-2 document와 `page:row-1`, `LIST_BLOCKS` failure가 있는지 검증한다.
- `childPageFailureEmitsFailureAndContinuesWithSiblingPage`: child-a retrieve만 실패시키고 child-b를 정상 처리한다. root와 child-b document, `page:child-a`, `RETRIEVE` failure를 검증한다.
- `unsupportedDatabaseBlockEmitsBlockFailureWithoutFollowingLinkToPage`: `underlyingType=child_database` unsupported와 `link_to_page` block을 함께 반환한다. `block:unsupported-1` failure 하나만 있고 두 block ID로 page/database retrieve를 호출하지 않았는지 검증한다.

404 database reason은 `원본 database를 integration에 공유하세요`를 포함하고 token·response body를 포함하지 않아야 한다.

- [ ] **Step 2: Notion connector 테스트를 실행해 RED 확인**

Run:

```bash
./gradlew test --tests 'com.mydata.connectors.notion.NotionPageConnectorTest'
```

Expected: `child_database`가 database query 경로로 이동하지 않아 nested row 기대값과 failure event 기대값이 실패한다.

- [ ] **Step 3: page와 database가 공유하는 방문 상태·위치 타입 구현**

`fetchDatabasePage`를 제거하고 query row도 `fetchPage`로 보낸다.

```java
private static final class TraversalState {
    private final String principalKey;
    private final ConnectorEventSink sink;
    private final Set<String> visitedPageIds = new HashSet<>();
    private final Set<String> visitedDatabaseIds = new HashSet<>();

    private TraversalState(String principalKey, ConnectorEventSink sink) {
        this.principalKey = principalKey;
        this.sink = sink;
    }
}

private record ResourceReference(String id, String title) { }
private record DatabaseContext(
    String id,
    String title,
    NotionApiClient.NotionDataSource dataSource
) { }
private record PageLocation(
    String rootPageId,
    String parentPageId,
    String parentTitle,
    List<String> parentPath,
    DatabaseContext database
) { }
private record PageContent(
    List<String> lines,
    List<ResourceReference> childPages,
    List<ResourceReference> childDatabases,
    List<NotionApiClient.NotionBlock> unsupportedDatabaseBlocks
) { }
```

방문 ID는 원격 호출 전에 추가한다. page metadata 또는 block 조회가 실패하면 불완전 문서를 내보내지 않고 각각 `RETRIEVE`, `LIST_BLOCKS` failure를 보낸다. 성공 문서를 먼저 내보낸 뒤 child page, child database 순서로 재귀한다.

- [ ] **Step 4: child database query와 오류 분류 구현**

database 함수는 다음 경계로 구성한다.

```java
private void fetchDatabase(
    ResourceReference reference,
    List<String> parentPath,
    PageLocation parent,
    TraversalState state
) {
    if (!state.visitedDatabaseIds.add(reference.id())) {
        return;
    }
    List<String> databasePath = append(parentPath, titleOrFallback(reference.title(), reference.id()));
    NotionApiClient.NotionDatabase database;
    try {
        database = notionClient.retrieveDatabase(reference.id());
    } catch (NotionApiException retrieveFailure) {
        state.sink.onFailure(failure(
            ConnectorItemType.DATABASE,
            reference.id(),
            databasePath,
            ConnectorFailureStage.RETRIEVE,
            databaseReason(retrieveFailure)
        ));
        return;
    }
    NotionApiClient.NotionDataSource dataSource;
    try {
        dataSource = singleDataSource(database);
    } catch (IllegalArgumentException invalidDatabase) {
        state.sink.onFailure(failure(
            ConnectorItemType.DATABASE,
            reference.id(),
            databasePath,
            ConnectorFailureStage.RETRIEVE,
            invalidDatabase.getMessage()
        ));
        return;
    }
    DatabaseContext context = new DatabaseContext(
        database.id(), titleOrFallback(database.title(), database.id()), dataSource
    );
    try {
        notionClient.queryDataSourcePages(dataSource.id(), pages -> pages.forEach(page ->
            fetchPage(page.id(), page, new PageLocation(
                parent.rootPageId(),
                parent.parentPageId(),
                parent.parentTitle(),
                databasePath,
                context
            ), state)
        ));
    } catch (NotionApiException queryFailure) {
        state.sink.onFailure(failure(
            ConnectorItemType.DATABASE,
            reference.id(),
            databasePath,
            ConnectorFailureStage.QUERY,
            databaseReason(queryFailure)
        ));
    }
}
```

`retrieveDatabase` try와 `queryDataSourcePages` try를 분리했으므로 retrieve 404는 `RETRIEVE`, cursor 실패는 `QUERY`로 기록된다. `sink.onDocument`와 `sink.onFailure`에서 발생한 DB/item infrastructure 예외는 `NotionApiException`이 아니므로 이 catch에 잡히지 않고 worker 최상위로 전파된다.

`object_not_found` database 오류만 다음 정제 문구를 사용한다.

```java
private String databaseReason(NotionApiException error) {
    if (Integer.valueOf(404).equals(error.statusCode())
        && "object_not_found".equals(error.code())) {
        return error.getMessage() + "; 원본 database를 integration에 공유하세요";
    }
    return error.getMessage();
}
```

`child_database`는 block children 재귀 대상에 넣지 않고 `childDatabases`에 추가한다. `link_to_page`는 무시하며 `unsupported.underlyingType == child_database`일 때만 `BLOCK/RETRIEVE` 실패를 보낸다.

- [ ] **Step 5: 통합 테스트에서 부분 성공 문서·item·ACL 확인**

`NotionIngestionIntegrationTest` fake에 database/data-source batch와 실패 설정을 추가하고, root page와 good/bad child database를 한 job으로 실행한다. 기존 test setup 방식으로 `job`, `dataSource`, `principalKey` local 변수를 만든 뒤 다음 결과를 검증한다.

```java
@Test
void workerKeepsNestedDatabaseRowsWhenSiblingDatabaseFails() {
    worker.run(job.getId());

    assertThat(documents.findByDataSourceIdAndExternalId(dataSource.getId(), "root")).isPresent();
    assertThat(documents.findByDataSourceIdAndExternalId(dataSource.getId(), "good-row")).isPresent();
    assertThat(ingestionJobs.findById(job.getId()).orElseThrow().getStatus())
        .isEqualTo(IngestionJobStatus.PARTIAL_FAILED);
    assertThat(jobItems.findByJobIdOrderByProcessedAtAscIdAsc(job.getId()))
        .extracting(IngestionJobItemEntity::getStatus)
        .contains(IngestionJobItemStatus.SUCCEEDED, IngestionJobItemStatus.FAILED);
    assertThat(aclEntries.findByDocumentId(
        documents.findByDataSourceIdAndExternalId(dataSource.getId(), "good-row").orElseThrow().getId()
    )).allSatisfy(acl -> assertThat(acl.getPrincipalKey()).isEqualTo(principalKey));
}
```

- [ ] **Step 6: Notion 단위·통합 회귀 테스트 실행**

Run:

```bash
./gradlew test --tests 'com.mydata.connectors.notion.*' \
  --tests 'com.mydata.ingestion.NotionIngestionIntegrationTest' \
  --tests 'com.mydata.ingestion.IngestionWorkerIntegrationTest'
```

Expected: root/child/row database 재귀, 중복 제거, 중간 query 실패 유지, sibling 계속 처리, ACL 상속과 기존 명시적 database mode가 모두 PASS한다.

- [ ] **Step 7: Task 3 커밋**

```bash
git add src/main/java/com/mydata/connectors/notion/NotionPageConnector.java \
  src/test/java/com/mydata/connectors/notion/NotionPageConnectorTest.java \
  src/test/java/com/mydata/ingestion/NotionIngestionIntegrationTest.java
git commit -m "feat(notion): ingest nested databases from pages" \
  -m "Traverse child pages and databases with shared visited state, retain successful rows across target failures, and emit sanitized path-aware failure events.

자동 테스트: ./gradlew test --tests com.mydata.connectors.notion.* --tests com.mydata.ingestion.NotionIngestionIntegrationTest --tests com.mydata.ingestion.IngestionWorkerIntegrationTest
Playwright: UI 변경 없음, 해당 없음"
```

---

### Task 4: 관리자 GraphQL에 job 집계와 실패 item pagination 제공

**Files:**
- Create: `src/main/java/com/mydata/admin/datasources/AdminIngestionJobService.java`
- Create: `src/main/java/com/mydata/admin/datasources/AdminIngestionJobItemPayload.java`
- Create: `src/main/java/com/mydata/admin/datasources/AdminIngestionJobItemPagePayload.java`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminIngestionJobPayload.java:1-35`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java:39-86`
- Modify: `src/main/java/com/mydata/admin/graphql/AdminGraphQlController.java:38-64,195-203`
- Modify: `src/main/java/com/mydata/ingestion/IngestionJobRepository.java:1-27`
- Modify: `src/main/java/com/mydata/ingestion/IngestionJobItemRepository.java`
- Modify: `src/main/resources/graphql/admin.graphqls:1-11,170-217`
- Modify: `src/main/java/com/mydata/admin/datasources/AGENTS.md`
- Create: `src/test/java/com/mydata/admin/datasources/AdminIngestionJobGraphQlTest.java`
- Modify: `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java:120-150`
- Modify: `src/test/java/com/mydata/admin/graphql/AdminGraphQlSecurityTest.java`

**Interfaces:**
- Consumes: Task 2 item entity/repository와 job 상태, `(processedAt, id)` 정렬.
- Produces: `AdminIngestionJobService.listJobs`, `listItems`; job count 필드; `ingestionJobItems` query와 opaque base64url cursor.

- [ ] **Step 1: job count·filter·cursor·보안 RED 테스트 작성**

새 integration test에 다음 메서드와 fixture 결과를 작성한다.

- `listsPartialFailedJobWithSucceededAndFailedItemCounts`: RUNNING job에 success item 2개와 failure item 1개를 저장하고 `markPartialFailed(3, 1)` 후 job query가 `PARTIAL_FAILED`, 2, 1을 반환하는지 검증한다.
- `paginatesFailedItemsForSelectedIngestionJob`: 서로 다른 processedAt/ID 순서의 failure 2개를 저장하고 `first: 1` 결과 cursor를 두 번째 요청의 `after`에 넣어 external ID가 겹치지 않고 두 번째 page의 `hasNextPage=false`인지 검증한다.
- `filtersItemsByStatusAndDoesNotLeakAnotherJobsItems`: 대상 job에는 success/failure를 각각 하나, 다른 job에는 failure 하나를 저장하고 `status: FAILED` 응답이 대상 job failure 하나만 포함하는지 검증한다.
- `rejectsInvalidIngestionJobItemCursorAndPageSize`: `after: "not-base64"`와 `first: 101`을 각각 요청해 정해진 한국어 validation 오류를 검증한다.
- `rejectsItemsForJobOutsideActiveDataSourceWorkspace`: job의 data source를 soft delete한 뒤 item query가 `수집 job을 찾을 수 없습니다`로 fail-closed인지 검증한다.

pagination query와 핵심 기대값은 다음 형태다.

```java
graphQl(adminSession, """
    query {
      ingestionJobItems(jobId: "%s", status: FAILED, first: 1) {
        items { externalId documentId status reason processedAt }
        hasNextPage
        endCursor
      }
    }
    """.formatted(job.getId()))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.ingestionJobItems.items.length()").value(1))
    .andExpect(jsonPath("$.data.ingestionJobItems.items[0].status").value("FAILED"))
    .andExpect(jsonPath("$.data.ingestionJobItems.hasNextPage").value(true))
    .andExpect(jsonPath("$.data.ingestionJobItems.endCursor").isNotEmpty());
```

`AdminGraphQlSecurityTest`에는 session 없는 `ingestionJobItems` 요청이 401/403 경계로 거부되는 `rejectsIngestionJobItemsWithoutAdminSession`을 추가한다.

- [ ] **Step 2: 관리자 GraphQL 테스트를 실행해 RED 확인**

Run:

```bash
./gradlew test --tests 'com.mydata.admin.datasources.AdminIngestionJobGraphQlTest' \
  --tests 'com.mydata.admin.graphql.AdminGraphQlSecurityTest'
```

Expected: schema에 item query/count/status가 없어 GraphQL validation 또는 compilation이 실패한다.

- [ ] **Step 3: batch count와 keyset repository query 구현**

job 목록은 한 번의 집계 query로 count를 가져온다.

```java
public interface IngestionJobItemCountProjection {
    UUID getJobId();
    long getSucceededItemCount();
    long getFailedItemCount();
}

@Query(value = """
    SELECT job_id AS "jobId",
           count(*) FILTER (WHERE status = 'SUCCEEDED') AS "succeededItemCount",
           count(*) FILTER (WHERE status = 'FAILED') AS "failedItemCount"
    FROM ingestion_job_items
    WHERE job_id IN (:jobIds)
    GROUP BY job_id
    """, nativeQuery = true)
List<IngestionJobItemCountProjection> summarizeByJobIdIn(@Param("jobIds") Collection<UUID> jobIds);
```

item page는 JPQL에서 nullable status/cursor를 처리하고 `PageRequest.of(0, limit + 1)`을 받는다.

```java
@Query("""
    SELECT item FROM IngestionJobItemEntity item
    WHERE item.jobId = :jobId
      AND (:status IS NULL OR item.status = :status)
      AND (
        :afterProcessedAt IS NULL
        OR item.processedAt > :afterProcessedAt
        OR (item.processedAt = :afterProcessedAt AND item.id > :afterId)
      )
    ORDER BY item.processedAt ASC, item.id ASC
    """)
List<IngestionJobItemEntity> findAdminPage(
    UUID jobId,
    IngestionJobItemStatus status,
    OffsetDateTime afterProcessedAt,
    UUID afterId,
    Pageable pageable
);
```

- [ ] **Step 4: 전용 admin service, payload와 cursor 구현**

`AdminDataSourceService`에서 기존 job 목록 메서드와 repository 주입을 제거하고 `AdminIngestionJobService`로 옮긴다.

```java
@Service
public class AdminIngestionJobService {
    private static final int DEFAULT_ITEM_LIMIT = 50;
    private static final int MAX_ITEM_LIMIT = 100;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminIngestionJobPayload> listJobs(String dataSourceId, Integer first) {
        UUID sourceId = parseId(dataSourceId, "dataSourceId");
        dataSources.findActiveById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("데이터소스를 찾을 수 없습니다"));
        int limit = first == null || first < 1 ? 20 : Math.min(first, 100);
        List<IngestionJobEntity> jobs = ingestionJobs
            .findByDataSourceIdOrderByCreatedAtDesc(sourceId)
            .stream()
            .limit(limit)
            .toList();
        if (jobs.isEmpty()) {
            return List.of();
        }
        Map<UUID, IngestionJobItemCountProjection> counts = jobItems
            .summarizeByJobIdIn(jobs.stream().map(IngestionJobEntity::getId).toList())
            .stream()
            .collect(Collectors.toMap(
                IngestionJobItemCountProjection::getJobId,
                Function.identity()
            ));
        return jobs.stream().map(job -> {
            IngestionJobItemCountProjection count = counts.get(job.getId());
            return AdminIngestionJobPayload.from(
                job,
                count == null ? 0 : count.getSucceededItemCount(),
                count == null ? 0 : count.getFailedItemCount()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AdminIngestionJobItemPagePayload listItems(
        String jobId,
        IngestionJobItemStatus status,
        Integer first,
        String after
    ) {
        IngestionJobEntity job = ingestionJobs.findById(parseId(jobId, "jobId"))
            .orElseThrow(() -> new IllegalArgumentException("수집 job을 찾을 수 없습니다"));
        dataSources.findActiveById(job.getDataSourceId())
            .filter(dataSource -> dataSource.getWorkspaceId().equals(job.getWorkspaceId()))
            .orElseThrow(() -> new IllegalArgumentException("수집 job을 찾을 수 없습니다"));
        int limit = normalizeItemLimit(first);
        ItemCursor cursor = decodeCursor(after);
        List<IngestionJobItemEntity> fetched = jobItems.findAdminPage(
            job.getId(),
            status,
            cursor == null ? null : cursor.processedAt(),
            cursor == null ? null : cursor.id(),
            PageRequest.of(0, limit + 1)
        );
        boolean hasNextPage = fetched.size() > limit;
        List<IngestionJobItemEntity> page = fetched.stream().limit(limit).toList();
        String endCursor = hasNextPage && !page.isEmpty()
            ? encodeCursor(page.getLast())
            : null;
        return new AdminIngestionJobItemPagePayload(
            page.stream().map(AdminIngestionJobItemPayload::from).toList(),
            hasNextPage,
            endCursor
        );
    }
}
```

`IngestionJobItemCountProjection`은 위 세 accessor만 가진 별도 top-level interface 파일로 둔다.

`listJobs`는 active data source를 확인하고 최대 `first`개의 job ID로 `summarizeByJobIdIn`을 한 번 호출한다. count 없는 job은 0/0으로 만든다. `listItems`는 job과 active data source를 읽고 `job.workspaceId == dataSource.workspaceId`를 검증한 뒤 cursor를 decode한다. limit은 null이면 50, 1 미만 또는 100 초과면 `first는 1 이상 100 이하여야 합니다`를 던진다. `limit + 1`개 중 앞 limit만 payload로 만들고 마지막 반환 item의 `processedAt|id`를 URL-safe Base64 without padding으로 encode한다. malformed cursor는 `after cursor 형식이 올바르지 않습니다`로 통일한다.

cursor helper는 같은 service의 private record/method로 둔다.

```java
private record ItemCursor(OffsetDateTime processedAt, UUID id) { }

private int normalizeItemLimit(Integer first) {
    int limit = first == null ? DEFAULT_ITEM_LIMIT : first;
    if (limit < 1 || limit > MAX_ITEM_LIMIT) {
        throw new IllegalArgumentException("first는 1 이상 100 이하여야 합니다");
    }
    return limit;
}

private ItemCursor decodeCursor(String after) {
    if (after == null || after.isBlank()) {
        return null;
    }
    try {
        String decoded = new String(
            Base64.getUrlDecoder().decode(after),
            StandardCharsets.UTF_8
        );
        String[] parts = decoded.split("\\|", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException();
        }
        return new ItemCursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (RuntimeException invalidCursor) {
        throw new IllegalArgumentException("after cursor 형식이 올바르지 않습니다");
    }
}

private String encodeCursor(IngestionJobItemEntity item) {
    String value = item.getProcessedAt() + "|" + item.getId();
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
}

private UUID parseId(String value, String fieldName) {
    try {
        return UUID.fromString(value);
    } catch (RuntimeException invalidId) {
        throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다");
    }
}
```

payload는 다음 계약을 사용한다.

```java
public record AdminIngestionJobPayload(
    UUID id,
    UUID workspaceId,
    UUID dataSourceId,
    IngestionTriggerType triggerType,
    IngestionJobStatus status,
    String errorMessage,
    int succeededItemCount,
    int failedItemCount,
    String startedAt,
    String finishedAt,
    String createdAt
) {
    public static AdminIngestionJobPayload from(
        IngestionJobEntity job,
        long succeededItemCount,
        long failedItemCount
    ) {
        return new AdminIngestionJobPayload(
            job.getId(),
            job.getWorkspaceId(),
            job.getDataSourceId(),
            job.getTriggerType(),
            job.getStatus(),
            job.getErrorMessage(),
            Math.toIntExact(succeededItemCount),
            Math.toIntExact(failedItemCount),
            job.getStartedAt() == null ? null : job.getStartedAt().toString(),
            job.getFinishedAt() == null ? null : job.getFinishedAt().toString(),
            job.getCreatedAt().toString()
        );
    }

    public static AdminIngestionJobPayload from(IngestionJobEntity job) {
        return from(job, 0, 0);
    }
}

public record AdminIngestionJobItemPayload(
    String externalId,
    UUID documentId,
    IngestionJobItemStatus status,
    String reason,
    String processedAt
) {
    public static AdminIngestionJobItemPayload from(IngestionJobItemEntity item) {
        return new AdminIngestionJobItemPayload(
            item.getExternalId(),
            item.getDocumentId(),
            item.getStatus(),
            item.getReason(),
            item.getProcessedAt().toString()
        );
    }
}

public record AdminIngestionJobItemPagePayload(
    List<AdminIngestionJobItemPayload> items,
    boolean hasNextPage,
    String endCursor
) { }
```

- [ ] **Step 5: GraphQL schema/controller 연결**

schema에 다음 query와 타입을 추가하고 job enum에 `PARTIAL_FAILED`를 추가한다.

```graphql
type Query {
  ingestionJobs(dataSourceId: ID!, first: Int = 20): [IngestionJob!]!
  ingestionJobItems(
    jobId: ID!
    status: IngestionJobItemStatus
    first: Int = 50
    after: String
  ): IngestionJobItemPage!
}

type IngestionJob {
  id: ID!
  workspaceId: ID!
  dataSourceId: ID!
  triggerType: IngestionTriggerType!
  status: IngestionJobStatus!
  errorMessage: String
  succeededItemCount: Int!
  failedItemCount: Int!
  startedAt: String
  finishedAt: String
  createdAt: String!
}

type IngestionJobItemPage {
  items: [IngestionJobItem!]!
  hasNextPage: Boolean!
  endCursor: String
}

type IngestionJobItem {
  externalId: String
  documentId: ID
  status: IngestionJobItemStatus!
  reason: String
  processedAt: String!
}

enum IngestionJobItemStatus { SUCCEEDED FAILED }
enum IngestionJobStatus { PENDING RUNNING SUCCEEDED PARTIAL_FAILED FAILED }
```

controller는 새 service에 `ingestionJobs`와 다음 query를 위임한다.

```java
@QueryMapping
@PreAuthorize("hasRole('ADMIN')")
public AdminIngestionJobItemPagePayload ingestionJobItems(
    @Argument String jobId,
    @Argument IngestionJobItemStatus status,
    @Argument Integer first,
    @Argument String after
) {
    return adminIngestionJobs.listItems(jobId, status, first, after);
}
```

- [ ] **Step 6: 관리자 GraphQL 전체 테스트 실행**

Run:

```bash
./gradlew test --tests 'com.mydata.admin.datasources.*' \
  --tests 'com.mydata.admin.graphql.*'
```

Expected: `PARTIAL_FAILED`, batch counts, status filter, cursor page, 다른 job item 비노출, invalid 입력과 비관리자 거부가 PASS한다.

- [ ] **Step 7: Task 4 커밋**

```bash
git add src/main/java/com/mydata/admin/datasources \
  src/main/java/com/mydata/admin/graphql/AdminGraphQlController.java \
  src/main/java/com/mydata/ingestion/IngestionJobRepository.java \
  src/main/java/com/mydata/ingestion/IngestionJobItemRepository.java \
  src/main/resources/graphql/admin.graphqls \
  src/test/java/com/mydata/admin
git commit -m "feat(admin): expose ingestion failure details" \
  -m "Expose partial-failed jobs, batched success/failure counts, and selected-job item pages through an admin-only keyset-paginated GraphQL boundary.

자동 테스트: ./gradlew test --tests com.mydata.admin.datasources.* --tests com.mydata.admin.graphql.*
Playwright: UI 변경 없음, 해당 없음"
```

---

### Task 5: 관리자 UI에서 부분 실패와 실패 상세 표시

**Files:**
- Modify: `frontend/admin/src/graphql/admin.graphql:90-98,242-254`
- Modify: `frontend/admin/src/api/adminGraphql.ts:1-40,266-274`
- Create: `frontend/admin/src/routes/IngestionJobHistoryPanel.tsx`
- Modify: `frontend/admin/src/routes/DataSourcesPage.tsx:1-304`
- Modify: `frontend/admin/src/App.css:295-304,388-445`
- Modify: `frontend/admin/src/App.test.tsx:539-591`
- Generate: `frontend/admin/src/generated/graphql.ts`
- Generate: `frontend/admin/src/generated/gql.ts`

**Interfaces:**
- Consumes: Task 4의 job count와 `ingestionJobItems` page.
- Produces: 선택 전 query 없음, 실패가 있는 job만 상세 선택, `useInfiniteQuery` 더 보기, loading/error/empty 상태.

- [ ] **Step 1: lazy 상세·더 보기·성공 job 무조회 RED UI 테스트 작성**

`App.test.tsx`의 기존 수집 기록 fixture에 `succeededItemCount`, `failedItemCount`를 추가하고 다음 테스트를 작성한다.

```tsx
it('PARTIAL_FAILED 수집 job의 성공·실패 건수와 선택한 실패 상세를 표시한다', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(jsonResponse({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-token'
    }))
    .mockResolvedValueOnce(adminDataSourcesResponse([
      dataSourceFixture({ id: 'source-id', name: 'Notion 문서함' })
    ]))
    .mockResolvedValueOnce(graphqlResponse('ingestionJobs', [{
      id: 'partial-job',
      workspaceId: 'workspace-id',
      dataSourceId: 'source-id',
      triggerType: 'MANUAL',
      status: 'PARTIAL_FAILED',
      errorMessage: '전체 3개 중 1개 실패',
      succeededItemCount: 2,
      failedItemCount: 1,
      startedAt: '2026-07-13T01:00:00Z',
      finishedAt: '2026-07-13T01:01:00Z',
      createdAt: '2026-07-13T01:00:00Z'
    }]))
    .mockResolvedValueOnce(graphqlResponse('ingestionJobItems', {
      items: [{
        externalId: 'database:bad-database',
        documentId: null,
        status: 'FAILED',
        reason: '[DATABASE] Root / Hidden DB (RETRIEVE): 원본 database를 integration에 공유하세요',
        processedAt: '2026-07-13T01:00:30Z'
      }],
      hasNextPage: false,
      endCursor: null
    }));
  vi.stubGlobal('fetch', fetchMock);

  renderApp('/data-sources');
  fireEvent.click(await screen.findByRole('button', { name: 'Notion 문서함 수집 기록' }));

  expect(await screen.findByText('PARTIAL_FAILED')).toBeVisible();
  expect(screen.getByText('2')).toBeVisible();
  expect(screen.getByText('1')).toBeVisible();
  expect(fetchMock.mock.calls.some((call) => {
    const body = JSON.parse((call[1] as RequestInit | undefined)?.body as string ?? '{}');
    return body.query?.includes('AdminIngestionJobItems');
  })).toBe(false);

  fireEvent.click(screen.getByRole('button', { name: 'partial-job 실패 상세 보기' }));

  expect(await screen.findByText('database:bad-database')).toBeVisible();
  expect(screen.getByText(/원본 database를 integration에 공유하세요/)).toBeVisible();
  expect(JSON.parse((fetchMock.mock.calls[3][1] as RequestInit).body as string).query)
    .toContain('AdminIngestionJobItems');
});
```

추가 테스트는 `job 선택 전과 SUCCEEDED job에는 실패 item query를 보내지 않는다`, `실패 항목 더 보기로 다음 cursor 페이지를 이어 붙인다`, `실패 상세 조회 실패 상태를 표시하고 job 목록을 유지한다`로 분리한다. 더 보기 테스트는 첫 page의 `endCursor: cursor-1`, 두 번째 page의 `endCursor: null`을 사용하고 두 external ID가 중복 없이 남는지 검증한다.

- [ ] **Step 2: frontend test를 실행해 RED 확인**

Run:

```bash
cd frontend/admin
npm test
```

Expected: count 열, 상세 버튼과 `AdminIngestionJobItems` operation이 없어 assertion이 실패한다.

- [ ] **Step 3: typed operation과 API 함수 추가 후 codegen**

`admin.graphql`에 count와 item query를 추가한다.

```graphql
query AdminIngestionJobItems(
  $jobId: ID!
  $status: IngestionJobItemStatus = FAILED
  $first: Int = 50
  $after: String
) {
  ingestionJobItems(jobId: $jobId, status: $status, first: $first, after: $after) {
    items { ...IngestionJobItemFields }
    hasNextPage
    endCursor
  }
}

fragment IngestionJobFields on IngestionJob {
  id
  workspaceId
  dataSourceId
  triggerType
  status
  errorMessage
  succeededItemCount
  failedItemCount
  startedAt
  finishedAt
  createdAt
}

fragment IngestionJobItemFields on IngestionJobItem {
  externalId
  documentId
  status
  reason
  processedAt
}
```

API 함수는 nullable after를 GraphQL variables에 전달한다.

```ts
export async function fetchAdminIngestionJobItems(
  jobId: string,
  status: IngestionJobItemStatus = 'FAILED',
  first = 50,
  after: string | null = null
): Promise<AdminIngestionJobItemsQuery> {
  return await requestAdminGraphql<AdminIngestionJobItemsQuery, AdminIngestionJobItemsQueryVariables>(
    AdminIngestionJobItemsDocument,
    { jobId, status, first, after }
  );
}
```

Run:

```bash
cd frontend/admin
npm run codegen
```

Expected: generated types와 documents가 schema/operation에서 재생성되고 TypeScript error 없이 종료한다.

- [ ] **Step 4: history panel과 infinite query 상태 구현**

`IngestionJobHistoryPanel`은 다음 props와 query 경계를 사용한다.

```tsx
type IngestionJobHistoryPanelProps = {
  dataSourceId: string;
  dataSourceName: string;
};

const itemsQuery = useInfiniteQuery({
  enabled: Boolean(selectedJob && selectedJob.failedItemCount > 0),
  queryKey: ['admin-ingestion-job-items', selectedJob?.id, 'FAILED'],
  initialPageParam: null as string | null,
  queryFn: ({ pageParam }) => fetchAdminIngestionJobItems(
    selectedJob?.id as string,
    'FAILED',
    50,
    pageParam
  ),
  getNextPageParam: (page) => page.ingestionJobItems.hasNextPage
    ? page.ingestionJobItems.endCursor ?? undefined
    : undefined
});

const itemReferences = itemsQuery.data?.pages.flatMap(
  (page) => page.ingestionJobItems.items
) ?? [];
const items = useFragment(IngestionJobItemFieldsFragmentDoc, itemReferences);
```

job 표 열은 `상태`, `성공`, `실패`, `트리거`, `생성`, `시작`, `종료`, `상세` 순서다. `failedItemCount > 0`인 행만 `${job.id} 실패 상세 보기` 버튼을 렌더하고 클릭하면 선택한다. 상세는 job error summary, external ID, reason, 처리 시각을 보여 주며 `hasNextPage`일 때만 `실패 항목 더 보기` 버튼을 렌더한다. 첫 조회와 다음 page 조회의 오류 문구는 각각 `실패 상세를 불러오지 못했습니다.`, `다음 실패 항목을 불러오지 못했습니다.`를 사용한다.

`DataSourcesPage`는 기존 job query/state/render를 제거하고 다음처럼 panel을 연결한다.

```tsx
{selectedDataSource ? (
  <IngestionJobHistoryPanel
    key={selectedDataSource.id}
    dataSourceId={selectedDataSource.id}
    dataSourceName={selectedDataSource.name}
  />
) : null}
```

CSS는 `PARTIAL_FAILED` badge 경고색, 선택 행, 상세 reason wrapping을 추가하고 실패 상세 cell에서 `white-space: normal; overflow-wrap: anywhere`를 사용한다.

- [ ] **Step 5: frontend 자동 테스트와 build 실행**

Run:

```bash
cd frontend/admin
npm test
npm run build
```

Expected: 기존 포함 모든 Vitest가 PASS하고 codegen, `tsc --noEmit`, Vite build가 성공한다.

- [ ] **Step 6: 최신 UI로 로컬 서버 재시작**

기존 PID를 확인해 정상 종료한 뒤 DB를 유지하고 현재 `.env`의 port/profile로 실행한다. 비밀값은 출력하지 않는다.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Expected: `.env`의 `SERVER_PORT=20001`이 적용된 환경에서는 `Tomcat started on port 20001`, `Started MyDataApplication` 로그가 보이고 `http://localhost:20001/admin-ui/`가 200을 반환한다.

- [ ] **Step 7: Playwright 정상 수집 확인**

1. 브라우저 skill로 `http://localhost:20001/admin-ui/`를 열고 `.env` 관리자 계정으로 로그인한다. 자격증명을 응답이나 로그에 출력하지 않는다.
2. child database가 포함된 page-root Notion data source의 `수동 수집`을 누른다.
3. `수집 기록`에서 완료 job이 `SUCCEEDED`, 성공 수 1 이상, 실패 수 0인지 확인한다.
4. 성공 job에는 `실패 상세 보기` 버튼이 없고 `AdminIngestionJobItems` 요청이 발생하지 않는지 확인한다.
5. PostgreSQL에서 해당 data source에 root page와 database row external document가 함께 저장되고 row ACL principal이 data source policy principal과 일치하는지 read-only query로 확인한다.

- [ ] **Step 8: Playwright 부분 실패·더 보기 확인**

브라우저 UI는 실제 connector 부분 실패를 integration test가 검증한 동일한 shape의 격리 QA job으로 검증한다. 정상 수집을 끝낸 최신 활성 page-root data source와 그 기존 document 하나를 참조해 `PARTIAL_FAILED` job, 성공 item 1개와 실패 item 51개를 다음 SQL transaction으로 추가한다. 기존 document/data source 값은 수정하지 않는다.

```bash
docker compose exec -T postgres sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
DO $$
DECLARE
  source_row record;
  qa_document_id uuid;
  qa_job_id uuid := gen_random_uuid();
BEGIN
  DELETE FROM ingestion_jobs
  WHERE error_message = '[QA] 전체 52개 중 51개 실패';

  SELECT id, workspace_id
  INTO source_row
  FROM data_sources
  WHERE deleted_at IS NULL
    AND type = 'NOTION'
    AND coalesce(config_json->>'notionRootPageId', '') <> ''
  ORDER BY created_at DESC
  LIMIT 1;

  SELECT id
  INTO qa_document_id
  FROM external_documents
  WHERE data_source_id = source_row.id
  ORDER BY created_at ASC
  LIMIT 1;

  IF source_row.id IS NULL OR qa_document_id IS NULL THEN
    RAISE EXCEPTION '정상 수집된 page-root Notion data source가 필요합니다';
  END IF;

  INSERT INTO ingestion_jobs (
    id, workspace_id, data_source_id, trigger_type, status,
    started_at, finished_at, error_message
  ) VALUES (
    qa_job_id, source_row.workspace_id, source_row.id, 'MANUAL', 'PARTIAL_FAILED',
    now(), now(), '[QA] 전체 52개 중 51개 실패'
  );

  INSERT INTO ingestion_job_items (job_id, external_id, document_id, status, processed_at)
  VALUES (qa_job_id, 'page:qa-success', qa_document_id, 'SUCCEEDED', now());

  INSERT INTO ingestion_job_items (job_id, external_id, status, reason, processed_at)
  SELECT qa_job_id,
         'database:qa-hidden-' || value,
         'FAILED',
         '[DATABASE] QA Root / Hidden DB ' || value
           || ' (RETRIEVE): Notion API 오류: status=404, code=object_not_found; 원본 database를 integration에 공유하세요',
         now() + value * interval '1 millisecond'
  FROM generate_series(1, 51) AS value;
END $$;
SQL
```

1. 수집 기록을 다시 열어 `PARTIAL_FAILED`, 성공 1, 실패 51을 확인한다.
2. 상세 선택 전 item query가 없고 선택 뒤 `status: FAILED`, `first: 50` query가 발생하는지 확인한다.
3. external ID, 전체 경로, `RETRIEVE`, 공유 안내, 처리 시각이 표시되고 token, Authorization, raw response, stack trace가 없는지 확인한다.
4. `실패 항목 더 보기`를 눌러 51번째 item이 추가되고 앞 50개가 유지되며 버튼이 사라지는지 확인한다.
5. 다음 cleanup으로 QA job을 삭제해 cascade로 QA items를 정리하고 실제 data source와 성공 문서는 유지한다.

```bash
docker compose exec -T postgres sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
DELETE FROM ingestion_jobs
WHERE error_message = '[QA] 전체 52개 중 51개 실패';
SQL
```

- [ ] **Step 9: Task 5 커밋**

```bash
git add frontend/admin/src/graphql/admin.graphql \
  frontend/admin/src/api/adminGraphql.ts \
  frontend/admin/src/routes/IngestionJobHistoryPanel.tsx \
  frontend/admin/src/routes/DataSourcesPage.tsx \
  frontend/admin/src/App.css \
  frontend/admin/src/App.test.tsx \
  frontend/admin/src/generated/graphql.ts \
  frontend/admin/src/generated/gql.ts
git commit -m "feat(admin): show ingestion item failures" \
  -m "Show job outcome counts and lazily paginated failure details while keeping successful jobs free of unnecessary item queries.

자동 테스트: cd frontend/admin && npm test && npm run build
Playwright: nested database 정상 수집·성공 count·상세 미조회, PARTIAL_FAILED 51개 상세·더 보기·민감정보 비노출 확인"
```

---

### Task 6: 운영 문서와 전체 회귀 검증 완성

**Files:**
- Modify: `docs/notion-integration-setup.md:1-113`
- Modify: `README.md:104-161,250-260`
- Verify only: all files changed by Tasks 1-5

**Interfaces:**
- Consumes: Tasks 1-5의 자동 발견, 부분 실패, GraphQL과 UI 동작.
- Produces: 실제 운영자가 page/database 선택, integration 공유, 실패 확인과 재시도를 이해할 수 있는 문서와 최종 검증 증거.

- [ ] **Step 1: Notion 운영 안내 갱신**

`docs/notion-integration-setup.md`에 다음 확정 내용을 한국어로 기록한다.

```markdown
## 페이지 아래 데이터베이스 자동 수집

수집 대상을 `페이지`로 등록하면 루트 페이지와 `child_page` 하위 페이지를 재귀적으로 수집하고, 그 범위에서 발견한 `child_database`의 행도 함께 수집합니다. 화면에 보이는 database view의 필터·정렬은 적용하지 않으며, database가 가리키는 단일 data source의 전체 행을 읽습니다.

원본 database를 Notion integration에 직접 공유해야 합니다. linked database에서 원본 ID를 API가 제공하지 않거나 원본이 공유되지 않은 경우 자동 해석하지 않으므로, 원본 database 링크를 `데이터베이스` 대상으로 별도 등록하세요.

## 부분 실패 확인

일부 문서는 저장되고 일부 페이지나 database만 실패하면 job 상태가 `PARTIAL_FAILED`가 됩니다. 관리자 화면의 `수집 기록`에서 성공·실패 개수를 확인하고 `실패 상세 보기`에서 external ID, 경로, 실패 단계와 원인을 확인할 수 있습니다. `더 보기`는 실패 항목을 50개씩 추가로 불러옵니다. 부분 실패와 전체 실패에서는 cursor와 마지막 수집 시각을 갱신하지 않으므로 공유 권한이나 원인을 수정한 뒤 수동 수집을 다시 실행하세요.
```

README 관리자 기능 목록과 Notion 설명에는 하위 database 자동 수집, `PARTIAL_FAILED`, 실패 상세 링크를 짧게 추가한다.

- [ ] **Step 2: 전체 자동 검증 실행**

Run:

```bash
./gradlew test
cd frontend/admin
npm test
npm run build
```

Expected: 전체 Gradle test, 전체 Vitest, GraphQL codegen, TypeScript 검사와 Vite build가 모두 성공한다.

- [ ] **Step 3: schema·작업 트리·민감정보 최종 검사**

Run:

```bash
git diff --check
git status --short
rg -n "T[O]DO|T[B]D|Bearer |Authorization:|secret_|notion-token" \
  src/main/java/com/mydata/connectors \
  src/main/java/com/mydata/ingestion \
  src/main/java/com/mydata/admin \
  frontend/admin/src \
  docs/notion-integration-setup.md
```

Expected: `git diff --check` 출력 없음, status에는 의도한 파일과 사용자 소유 `.playwright-mcp/`만 보이며, 검색 결과에는 test의 의도적 sanitized token 검증 외 실제 credential이 없다. `src/main/resources/db/changelog` 변경은 없어야 한다.

- [ ] **Step 4: 문서 커밋**

```bash
git add docs/notion-integration-setup.md README.md
git commit -m "docs(notion): explain nested database ingestion" \
  -m "Document recursive page/database scope, source sharing requirements, full data-source row collection, and partial-failure recovery through the admin console.

자동 테스트: ./gradlew test; cd frontend/admin && npm test && npm run build; git diff --check
Playwright: nested database SUCCEEDED 화면과 PARTIAL_FAILED 실패 상세·더 보기·민감정보 비노출 확인"
```

- [ ] **Step 5: 최종 커밋 범위 확인**

Run:

```bash
git log --oneline --decorate -8
git status --short --branch
git diff main...HEAD --stat
```

Expected: Task 1-6의 conventional commit과 기존 설계/계획 commit만 현재 feature branch에 있고, tracked 작업 트리는 clean이며 `.playwright-mcp/`는 untracked 상태 그대로다.
