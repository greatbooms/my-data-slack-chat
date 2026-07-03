# Slack Channel Data Connector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Slack 채널 ID 기반 데이터소스를 만들고 수동 수집으로 채널 메시지와 스레드 답글을 RAG 문서로 적재한다.

**Architecture:** 기존 `DataSourceConnector` 계약을 유지하고 `connectors/slack` 패키지에 Slack API wrapper와 connector를 추가한다. 관리자 GraphQL/UI는 Notion 설정 필드와 같은 방식으로 `slackChannelId`를 저장/표시한다. MVP ACL은 데이터소스 visibility를 따른다.

**Tech Stack:** Java 21, Spring Boot 4.1, Slack Java SDK 1.49.0, GraphQL, React/TypeScript, Gradle, JUnit.

---

### Task 1: 관리자 데이터소스 설정 확장

**Files:**
- Modify: `src/main/resources/graphql/admin.graphqls`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceInputs.java`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourcePayload.java`
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java`
- Test: `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java`

- [ ] Add `slackChannelId` to create/update inputs and `DataSource`.
- [ ] Allow `DataSourceType.SLACK` in admin creation.
- [ ] Require `slackChannelId` when creating or updating Slack data sources.
- [ ] Reject `slackChannelId` for non-Slack data sources.
- [ ] Add GraphQL success/failure tests.

### Task 2: Slack API wrapper와 connector 추가

**Files:**
- Create: `src/main/java/com/mydata/connectors/slack/SlackApiException.java`
- Create: `src/main/java/com/mydata/connectors/slack/SlackClient.java`
- Create: `src/main/java/com/mydata/connectors/slack/SlackWebApiClient.java`
- Create: `src/main/java/com/mydata/connectors/slack/SlackChannelConnector.java`
- Create: `src/main/java/com/mydata/connectors/slack/AGENTS.md`
- Test: `src/test/java/com/mydata/connectors/slack/SlackChannelConnectorTest.java`

- [ ] Define a small `SlackClient` interface returning normalized message records.
- [ ] Implement Slack SDK-backed `SlackWebApiClient` using the existing `SlackBotProperties.botToken`.
- [ ] Implement `SlackChannelConnector.supports() == SLACK`.
- [ ] Emit root messages and thread replies as `RawExternalDocument`.
- [ ] Use owner/workspace principal based on data source visibility.
- [ ] Add connector tests for normal messages, replies, missing channel ID, and API errors.

### Task 3: 수동 수집 통합 테스트

**Files:**
- Create: `src/test/java/com/mydata/ingestion/SlackIngestionIntegrationTest.java`

- [ ] Provide a primary fake `SlackClient`.
- [ ] Create a Slack data source with `slackChannelId`.
- [ ] Run an ingestion job through `IngestionWorker`.
- [ ] Assert document, chunk, ACL, metadata, job success.

### Task 4: 관리자 UI 확장

**Files:**
- Modify: `frontend/admin/src/graphql/admin.graphql`
- Modify: `frontend/admin/src/routes/DataSourceFormDialog.tsx`
- Modify: `frontend/admin/src/routes/DataSourcesPage.tsx`
- Test: `frontend/admin/src/App.test.tsx` or focused route test if present.

- [ ] Add `slackChannelId` to GraphQL fragments/mutations.
- [ ] Add `SLACK` to the data source type select.
- [ ] Show a required Slack channel ID field when type is `SLACK`.
- [ ] Submit `slackChannelId` on create/update only for Slack data sources.
- [ ] Regenerate GraphQL TypeScript types with `npm run codegen`.

### Task 5: 문서와 검증

**Files:**
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `docs/slack-app-setup.md`

- [ ] Document Slack collection scopes: `channels:history`, optionally `groups:history`.
- [ ] Document where to enter the Slack channel ID.
- [ ] Run `./gradlew --console=plain test --rerun-tasks`.
- [ ] Run frontend build through Gradle.
- [ ] Run Playwright normal/failure UI checks before finalizing.
