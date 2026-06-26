# Workspace Admin Implementation Plan

## Task 1: Schema And Domain

- [ ] Add `deleted_at`, `updated_at` to `workspaces` via Liquibase.
- [ ] Add fields and domain methods to `WorkspaceEntity`.
- [ ] Add repository queries for active/deleted workspaces.

## Task 2: Backend GraphQL

- [ ] Add workspace inputs, service, payloads.
- [ ] Add GraphQL queries/mutations for list/create/update/delete/restore.
- [ ] Add integration tests for lifecycle.

## Task 3: Default Workspace Guarantee

- [ ] Create default workspace service.
- [ ] Call it after admin/user creation.
- [ ] Add startup backfill for active users without active workspaces.

## Task 4: Admin UI

- [ ] Add workspace operations to GraphQL documents and regenerate types.
- [ ] Add Workspaces page and form dialog.
- [ ] Add sidebar nav item.
- [ ] Update tests.

## Task 5: Verify

- [ ] Run targeted backend tests.
- [ ] Run frontend tests.
- [ ] Run full Gradle tests when feasible.
- [ ] Restart local server.
