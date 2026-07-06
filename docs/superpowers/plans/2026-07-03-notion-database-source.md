# Notion Database Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Notion 데이터베이스 링크 또는 database ID를 데이터소스로 등록하고 row page들을 수집한다.

**Architecture:** 기존 `DataSourceType.NOTION`과 `NotionPageConnector`를 확장한다. 관리자 config에는 `notionRootPageId` 또는 `notionDatabaseId` 중 하나를 저장하고, 데이터베이스 모드에서는 Notion database를 data source로 해석한 뒤 query 결과 page를 기존 page 수집 경로로 전달한다.

**Tech Stack:** Java 21 `HttpClient`, Spring Boot GraphQL, React admin UI, Vitest, Gradle/JUnit, Notion REST API `2026-03-11`.

---

### Task 1: Notion API Boundary

**Files:**
- Modify: `src/main/java/com/mydata/connectors/notion/NotionClient.java`
- Modify: `src/main/java/com/mydata/connectors/notion/NotionApiClient.java`
- Test: `src/test/java/com/mydata/connectors/notion/NotionApiClientTest.java`

- [ ] Add failing tests for retrieving a database data source ID and querying paginated data source rows.
- [ ] Implement `retrieveDatabase` and `queryDataSourcePages` with existing auth/version headers.
- [ ] Fail when data source query returns incomplete results.

### Task 2: Notion Connector Database Mode

**Files:**
- Modify: `src/main/java/com/mydata/connectors/notion/NotionPageConnector.java`
- Test: `src/test/java/com/mydata/connectors/notion/NotionPageConnectorTest.java`

- [ ] Add failing tests for `notionDatabaseId` config.
- [ ] Reuse existing page block collection for each queried database row page.
- [ ] Add database/data source metadata without changing page-root behavior.

### Task 3: Admin API Config

**Files:**
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceInputs.java`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourcePayload.java`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java`
- Modify: `src/main/resources/graphql/admin.graphqls`
- Test: `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java`

- [ ] Add `notionDatabaseId` to create/update/payload GraphQL schema.
- [ ] Validate that Notion sources have exactly one of `notionRootPageId` or `notionDatabaseId`.
- [ ] Normalize Notion database links to database IDs before storing config.

### Task 4: Admin UI

**Files:**
- Modify: `frontend/admin/src/graphql/admin.graphql`
- Modify: `frontend/admin/src/routes/DataSourceFormDialog.tsx`
- Modify: `frontend/admin/src/routes/DataSourcesPage.tsx`
- Modify: generated GraphQL files via `npm run codegen`
- Test: `frontend/admin/src/App.test.tsx`

- [ ] Add UI control for Notion root type: page or database.
- [ ] Send only the selected Notion config field.
- [ ] Show database link/ID guidance in the form.

### Task 5: Docs and Verification

**Files:**
- Modify: `docs/notion-integration-setup.md`
- Modify: `README.md`
- Modify: `src/main/java/com/mydata/connectors/notion/AGENTS.md`

- [ ] Document database link/ID setup and data source permission requirements.
- [ ] Run backend tests, frontend tests, codegen, and `git diff --check`.
- [ ] Run Playwright normal/failure screen checks for the admin data source form.
