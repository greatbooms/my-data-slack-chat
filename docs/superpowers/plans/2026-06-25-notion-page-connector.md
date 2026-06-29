# Notion Page Connector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first Notion page connector that loads shared Notion page content into the existing ingestion, embedding, retrieval, and chat path.

**Architecture:** Keep Notion-specific HTTP and JSON parsing behind a small `connectors.notion` package. Admin GraphQL/UI will accept only the minimal `notionRootPageId` setting so a manual sync can create `RawExternalDocument` records with owner/workspace ACLs.

**Tech Stack:** Java 21 `HttpClient`, Spring Boot configuration properties, existing `DataSourceConnector`, existing GraphQL admin API, React admin UI, Notion REST API `2026-03-11`.

---

### Task 1: Branch And Guardrails

**Files:**
- Create: `docs/superpowers/plans/2026-06-25-notion-page-connector.md`

- [x] **Step 1: Create branch**

Run: `git checkout -b codex/notion-page-connector`

Expected: new branch from latest `main`.

- [x] **Step 2: Keep secrets local**

Do not commit `.env`. Use `NOTION_API_TOKEN` only through environment-backed config.

### Task 2: Notion HTTP Boundary

**Files:**
- Create: `src/main/java/com/mydata/connectors/notion/NotionProperties.java`
- Create: `src/main/java/com/mydata/connectors/notion/NotionApiClient.java`
- Test: `src/test/java/com/mydata/connectors/notion/NotionApiClientTest.java`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`

- [x] **Step 1: Write failing HTTP client tests**

Test cases:
- `retrievePage` sends `Authorization`, `Notion-Version`, and parses page metadata.
- `listBlockChildren` follows paginated `next_cursor`.
- non-2xx responses throw an exception without logging token values.

- [x] **Step 2: Implement minimal client**

Use Java `HttpClient` and `tools.jackson.databind.ObjectMapper`. Keep Notion DTOs small.

### Task 3: Notion Page Connector

**Files:**
- Create: `src/main/java/com/mydata/connectors/notion/NotionPageConnector.java`
- Test: `src/test/java/com/mydata/connectors/notion/NotionPageConnectorTest.java`

- [x] **Step 1: Write failing connector tests**

Test cases:
- Root page config is required: `notionRootPageId`.
- Page title, URL, last edited time, and block text become a `RawExternalDocument`.
- ACL is `USER:{ownerUserId}` for `PRIVATE` and `WORKSPACE:{workspaceId}` for `WORKSPACE`.
- Child pages are recursively loaded as separate raw documents.

- [x] **Step 2: Implement connector**

Return `DataSourceType.NOTION`. For MVP, read the configured root page and nested child pages. Convert supported text blocks only; ignore unsupported media blocks.

### Task 4: Admin Data Source Settings

**Files:**
- Modify: `src/main/resources/graphql/admin.graphqls`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceInputs.java`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourcePayload.java`
- Test: `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java`
- Modify: `frontend/admin/src/graphql/admin.graphql`
- Modify: `frontend/admin/src/routes/DataSourceFormDialog.tsx`
- Modify: `frontend/admin/src/routes/DataSourcesPage.tsx`
- Regenerate: `frontend/admin/src/generated/*`

- [x] **Step 1: Write failing backend GraphQL test**

Creating `type: NOTION` with `notionRootPageId` stores config and rebuilds READ policy.

- [x] **Step 2: Implement backend support**

Allow `LOCAL_TEXT` and `NOTION`. Add `notionRootPageId` to create/update inputs and expose it in payload.

- [x] **Step 3: Update admin UI**

When type is `NOTION`, show a Notion root page ID field. Include it in create/update mutations.

### Task 5: Integration And Documentation

**Files:**
- Test: `src/test/java/com/mydata/ingestion/NotionIngestionIntegrationTest.java`
- Modify: `README.md`

- [x] **Step 1: Add integration test with fake Notion connector/client**

Manual sync for a Notion data source should persist document, ACL, chunks, and embeddings.

- [x] **Step 2: Document local Notion setup**

Add `NOTION_API_TOKEN`, integration capabilities, page sharing, and root page ID notes.

- [x] **Step 3: Verify**

Run:

```bash
./gradlew --console=plain cleanTest test
```

Expected: all tests pass.
