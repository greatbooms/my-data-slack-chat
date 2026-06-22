# 관리자 GraphQL 콘솔 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 사용자가 세션 로그인 후 `/admin/graphql`을 통해 사용자와 데이터소스를 관리하고, React 관리자 화면이 GraphQL Code Generator 타입으로 API를 호출하게 만든다.

**Architecture:** Spring Security가 `/admin/graphql`과 `/admin-ui/**`를 세션 기반으로 보호한다. GraphQL resolver는 화면 DTO만 반환하고 실제 권한/업무 규칙은 `admin.users`, `admin.datasources`, `admin.auth` service에 둔다. React 앱은 schema-first GraphQL 계약과 operation 파일에서 타입을 생성하고, 빌드 산출물을 Spring Boot 정적 리소스로 서빙한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Security, Spring GraphQL, JPA, Liquibase, PostgreSQL/pgvector, React, Vite, TypeScript, GraphQL Code Generator, graphql-request, TanStack Query.

---

## File Structure

- Modify: `build.gradle` - Spring Security/GraphQL/test 의존성과 frontend build task를 추가한다.
- Modify: `src/main/resources/db/changelog/db.changelog-master.sql` - 관리자 로그인과 데이터소스 권한 범위 필드를 추가한다.
- Modify: `src/main/resources/application.yml`, `src/main/resources/application-local.yml` - bootstrap admin 환경변수를 설정한다.
- Modify: `.env.example`, `README.md` - 실행 환경변수와 관리자 화면 실행법을 갱신한다.
- Modify: `src/main/java/com/mydata/users/UserEntity.java`, `src/main/java/com/mydata/users/UserRepository.java` - role/status/password/soft-delete 필드를 추가한다.
- Modify: `src/main/java/com/mydata/datasources/DataSourceEntity.java`, `src/main/java/com/mydata/datasources/DataSourceRepository.java` - owner/visibility/soft-delete/last-sync 관리를 추가한다.
- Create: `src/main/java/com/mydata/users/UserRole.java`, `src/main/java/com/mydata/users/UserStatus.java`
- Create: `src/main/java/com/mydata/datasources/DataSourceVisibility.java`
- Create: `src/main/java/com/mydata/admin/auth/*` - 관리자 로그인, 로그아웃, CSRF, bootstrap 생성, Spring Security user details를 담당한다.
- Create: `src/main/java/com/mydata/admin/graphql/*` - GraphQL resolver, DTO, error 처리 adapter를 둔다.
- Create: `src/main/java/com/mydata/admin/users/*` - 관리자 사용자 관리 service와 DTO mapper를 둔다.
- Create: `src/main/java/com/mydata/admin/datasources/*` - 관리자 데이터소스 관리 service와 DTO mapper를 둔다.
- Create: `src/main/resources/graphql/admin.graphqls` - 관리자 GraphQL schema 계약 파일.
- Delete: `src/main/java/com/mydata/admin/AdminDataSourceController.java`, `AdminTokenAuthenticationInterceptor.java`, `AdminWebMvcConfiguration.java` - 기존 token 관리자 API 제거.
- Replace tests: `src/test/java/com/mydata/admin/*` - token API 테스트를 세션/GraphQL 테스트로 대체한다.
- Create: `frontend/admin/*` - React/Vite/TypeScript 관리자 앱과 GraphQL Codegen 설정.
- Create: `src/main/java/com/mydata/admin/ui/*` - `/admin-ui/**` SPA fallback 설정.

---

### Task 1: DB 스키마와 엔티티 확장

**Files:**
- Modify: `src/main/resources/db/changelog/db.changelog-master.sql`
- Modify: `src/main/java/com/mydata/users/UserEntity.java`
- Modify: `src/main/java/com/mydata/users/UserRepository.java`
- Create: `src/main/java/com/mydata/users/UserRole.java`
- Create: `src/main/java/com/mydata/users/UserStatus.java`
- Modify: `src/main/java/com/mydata/datasources/DataSourceEntity.java`
- Modify: `src/main/java/com/mydata/datasources/DataSourceRepository.java`
- Create: `src/main/java/com/mydata/datasources/DataSourceVisibility.java`
- Modify: `src/test/java/com/mydata/database/LiquibaseMigrationTest.java`
- Create: `src/test/java/com/mydata/admin/AdminSchemaMigrationTest.java`

- [ ] **Step 1: Write the failing migration test**

Create `src/test/java/com/mydata/admin/AdminSchemaMigrationTest.java` with assertions for these columns:

```java
package com.mydata.admin;

import com.mydata.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSchemaMigrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void createsAdminUserAndDataSourceColumns() {
        Integer usersColumns = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'users'
              AND column_name IN ('password_hash', 'role', 'status', 'deleted_at', 'updated_at')
            """, Integer.class);
        Integer dataSourceColumns = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'data_sources'
              AND column_name IN ('owner_user_id', 'visibility', 'deleted_at')
            """, Integer.class);

        assertThat(usersColumns).isEqualTo(5);
        assertThat(dataSourceColumns).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./gradlew test --tests com.mydata.admin.AdminSchemaMigrationTest
```

Expected: FAIL because the new columns do not exist.

- [ ] **Step 3: Add Liquibase changeset and entity fields**

Add changeset `eric:003-admin-console-schema` with `ALTER TABLE` statements:

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE data_sources ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES users(id);
ALTER TABLE data_sources ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE data_sources ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
```

Add enums:

```java
public enum UserRole { USER, ADMIN }
public enum UserStatus { ACTIVE, DISABLED }
public enum DataSourceVisibility { PRIVATE, WORKSPACE }
```

Update entities with matching `@Enumerated(EnumType.STRING)` fields, mutator methods for admin flows, and repository methods:

```java
Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);
List<UserEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();
List<DataSourceEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();
```

- [ ] **Step 4: Run migration and existing persistence tests**

Run:

```bash
./gradlew test --tests com.mydata.admin.AdminSchemaMigrationTest --tests com.mydata.database.LiquibaseMigrationTest --tests com.mydata.documents.CorePersistenceTest
```

Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/resources/db/changelog/db.changelog-master.sql src/main/java/com/mydata/users src/main/java/com/mydata/datasources src/test/java/com/mydata/admin/AdminSchemaMigrationTest.java src/test/java/com/mydata/database/LiquibaseMigrationTest.java
git commit -m "feat: add admin console schema fields"
```

### Task 2: Spring Security 관리자 세션 인증

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/com/mydata/admin/auth/AdminSecurityConfiguration.java`
- Create: `src/main/java/com/mydata/admin/auth/AdminUserDetailsService.java`
- Create: `src/main/java/com/mydata/admin/auth/AdminAuthController.java`
- Create: `src/main/java/com/mydata/admin/auth/AdminBootstrapProperties.java`
- Create: `src/main/java/com/mydata/admin/auth/AdminBootstrapInitializer.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Modify: `.env.example`
- Create: `src/test/java/com/mydata/admin/auth/AdminAuthenticationTest.java`
- Create: `src/test/java/com/mydata/admin/auth/AdminBootstrapInitializerTest.java`
- Delete: `src/main/java/com/mydata/admin/AdminTokenAuthenticationInterceptor.java`
- Delete: `src/main/java/com/mydata/admin/AdminWebMvcConfiguration.java`
- Delete: `src/test/java/com/mydata/admin/AdminTokenAuthenticationInterceptorTest.java`

- [ ] **Step 1: Write failing auth tests**

Create tests that verify:

```java
mockMvc.perform(post("/admin/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\":\"admin@example.com\",\"password\":\"secret1234\"}"))
    .andExpect(status().isOk());

mockMvc.perform(post("/admin/graphql")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"query\":\"{ viewer { email } }\"}"))
    .andExpect(status().isUnauthorized());
```

Also verify disabled/deleted/non-admin users cannot log in.

- [ ] **Step 2: Run auth tests and confirm failure**

```bash
./gradlew test --tests com.mydata.admin.auth.AdminAuthenticationTest
```

Expected: FAIL because Security configuration and controller do not exist.

- [ ] **Step 3: Implement minimal session auth**

Add dependencies:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
testImplementation 'org.springframework.security:spring-security-test'
```

Map DB role to authority:

```java
new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
```

Use `@EnableMethodSecurity` and configure:

```java
.requestMatchers("/admin-ui/login", "/admin-ui/assets/**", "/admin/auth/login", "/admin/auth/csrf").permitAll()
.requestMatchers("/admin-ui/**", "/admin/graphql", "/admin/auth/logout").hasRole("ADMIN")
```

Keep `GET /admin/auth/csrf`, `POST /admin/auth/login`, `POST /admin/auth/logout`.

- [ ] **Step 4: Implement bootstrap admin**

Read:

```yaml
my-data:
  admin:
    bootstrap:
      email: ${ADMIN_BOOTSTRAP_EMAIL:}
      password: ${ADMIN_BOOTSTRAP_PASSWORD:}
      display-name: ${ADMIN_BOOTSTRAP_DISPLAY_NAME:관리자}
```

If no non-deleted admin exists, create one from env values. If email/password are missing, fail startup outside `test` profile.

- [ ] **Step 5: Run focused and full backend tests**

```bash
./gradlew test --tests com.mydata.admin.auth.AdminAuthenticationTest --tests com.mydata.admin.auth.AdminBootstrapInitializerTest
./gradlew test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/main/resources/application*.yml .env.example src/main/java/com/mydata/admin src/test/java/com/mydata/admin
git commit -m "feat: add admin session security"
```

### Task 3: GraphQL schema와 viewer/dashboard 세로 조각

**Files:**
- Modify: `build.gradle`
- Create: `src/main/resources/graphql/admin.graphqls`
- Create: `src/main/java/com/mydata/admin/graphql/AdminGraphQlController.java`
- Create: `src/main/java/com/mydata/admin/graphql/AdminViewerPayload.java`
- Create: `src/main/java/com/mydata/admin/graphql/AdminDashboardSummaryPayload.java`
- Create: `src/test/java/com/mydata/admin/graphql/AdminGraphQlSecurityTest.java`

- [ ] **Step 1: Write failing GraphQL test**

Use authenticated MockMvc session or `@WithMockUser(roles = "ADMIN")` to verify:

```graphql
query {
  viewer {
    email
    displayName
    role
  }
  dashboardSummary {
    userCount
    dataSourceCount
    runningJobCount
  }
}
```

Expected response contains the logged-in admin email and numeric summary fields.

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests com.mydata.admin.graphql.AdminGraphQlSecurityTest
```

Expected: FAIL because Spring GraphQL and schema are not configured.

- [ ] **Step 3: Implement schema and resolver**

Add dependency:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-graphql'
testImplementation 'org.springframework.graphql:spring-graphql-test'
```

Create `admin.graphqls` with `Query.viewer` and `Query.dashboardSummary` first, then expand in later tasks.

- [ ] **Step 4: Run focused tests**

```bash
./gradlew test --tests com.mydata.admin.graphql.AdminGraphQlSecurityTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add build.gradle src/main/resources/graphql src/main/java/com/mydata/admin/graphql src/test/java/com/mydata/admin/graphql
git commit -m "feat: add admin graphql foundation"
```

### Task 4: 관리자 사용자 GraphQL query/mutation

**Files:**
- Modify: `src/main/resources/graphql/admin.graphqls`
- Create: `src/main/java/com/mydata/admin/users/AdminUserService.java`
- Create: `src/main/java/com/mydata/admin/users/AdminUserPayload.java`
- Create: `src/main/java/com/mydata/admin/users/AdminUserInputs.java`
- Modify: `src/main/java/com/mydata/admin/graphql/AdminGraphQlController.java`
- Create: `src/test/java/com/mydata/admin/users/AdminUserGraphQlTest.java`

- [ ] **Step 1: Write failing user GraphQL tests**

Cover:

```graphql
mutation { createUser(input: { email: "user@example.com", displayName: "User", role: USER, password: "secret1234" }) { email role status } }
query { users { items { email deletedAt } totalCount } }
mutation { disableUser(id: "...") { status } }
mutation { softDeleteUser(id: "...") { deletedAt } }
mutation { restoreUser(id: "...") { status deletedAt } }
mutation { resetUserPassword(id: "...", input: { password: "changed1234" }) { id } }
```

Verify soft-deleted users disappear from the default `users` query.

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests com.mydata.admin.users.AdminUserGraphQlTest
```

Expected: FAIL because schema/service methods are missing.

- [ ] **Step 3: Implement service and resolver**

Rules:

- Email must be unique among all users.
- Admin-created login users must have BCrypt password hashes.
- `softDeleteUser` sets `deletedAt`.
- `restoreUser` clears `deletedAt` and sets `status = ACTIVE`.
- `disableUser` sets `status = DISABLED`.
- Service methods use `@PreAuthorize("hasRole('ADMIN')")`.

- [ ] **Step 4: Run focused tests**

```bash
./gradlew test --tests com.mydata.admin.users.AdminUserGraphQlTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/graphql/admin.graphqls src/main/java/com/mydata/admin src/test/java/com/mydata/admin/users
git commit -m "feat: add admin user graphql operations"
```

### Task 5: 관리자 데이터소스 GraphQL query/mutation

**Files:**
- Modify: `src/main/resources/graphql/admin.graphqls`
- Create: `src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java`
- Create: `src/main/java/com/mydata/admin/datasources/AdminDataSourcePayload.java`
- Create: `src/main/java/com/mydata/admin/datasources/AdminDataSourceInputs.java`
- Modify: `src/main/java/com/mydata/admin/graphql/AdminGraphQlController.java`
- Delete: `src/main/java/com/mydata/admin/AdminDataSourceController.java`
- Delete: `src/test/java/com/mydata/admin/AdminDataSourceControllerTest.java`
- Create: `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java`

- [ ] **Step 1: Write failing data source GraphQL tests**

Cover:

```graphql
mutation { createDataSource(input: { workspaceId: "...", ownerUserId: "...", type: LOCAL_TEXT, name: "Local", visibility: PRIVATE, syncMode: MANUAL }) { name visibility ownerUserId } }
mutation { updateDataSource(id: "...", input: { name: "Updated", visibility: WORKSPACE }) { name visibility } }
mutation { requestDataSourceSync(id: "...") { status triggerType } }
query { dataSources { items { name lastSyncedAt deletedAt } totalCount } }
query { ingestionJobs(dataSourceId: "...") { status errorMessage } }
mutation { softDeleteDataSource(id: "...") { deletedAt } }
```

Verify `PRIVATE` creates `USER:{ownerUserId}` policy and `WORKSPACE` creates `WORKSPACE:{workspaceId}` policy.

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests com.mydata.admin.datasources.AdminDataSourceGraphQlTest
```

Expected: FAIL because data source operations are missing.

- [ ] **Step 3: Implement service and resolver**

Rules:

- Only `LOCAL_TEXT` needs to be creatable in the first UI.
- Deleted data sources are hidden from default list.
- `requestDataSourceSync` calls existing `IngestionCommandService.requestManualSync`.
- Existing token-based `/admin/data-sources/{id}/sync` controller and tests are removed.
- Service methods use `@PreAuthorize("hasRole('ADMIN')")`.

- [ ] **Step 4: Run focused tests**

```bash
./gradlew test --tests com.mydata.admin.datasources.AdminDataSourceGraphQlTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/graphql/admin.graphqls src/main/java/com/mydata/admin src/test/java/com/mydata/admin
git rm src/main/java/com/mydata/admin/AdminDataSourceController.java src/test/java/com/mydata/admin/AdminDataSourceControllerTest.java
git commit -m "feat: add admin data source graphql operations"
```

### Task 6: React/Vite scaffold, Codegen, Gradle 정적 빌드 연동

**Files:**
- Create: `frontend/admin/package.json`
- Create: `frontend/admin/package-lock.json`
- Create: `frontend/admin/index.html`
- Create: `frontend/admin/tsconfig.json`
- Create: `frontend/admin/vite.config.ts`
- Create: `frontend/admin/codegen.ts`
- Create: `frontend/admin/src/generated/.gitkeep`
- Create: `frontend/admin/src/graphql/admin.graphql`
- Create: `src/test/java/com/mydata/admin/ui/AdminUiWebConfigurationTest.java`
- Create: `src/test/resources/static/admin-ui/*`
- Modify: `build.gradle`
- Create: `src/main/java/com/mydata/admin/ui/AdminUiWebConfiguration.java`

- [x] **Step 1: Create failing frontend build expectation**

Add Gradle tasks `adminUiNpmInstall`, `adminUiBuild`, `processResources.dependsOn(adminUiBuild)` and run:

```bash
./gradlew adminUiBuild
```

Expected: FAIL before the frontend files exist.

- [x] **Step 2: Scaffold Vite app and Codegen**

Use dependencies:

```json
"dependencies": {
  "@tanstack/react-query": "^5.0.0",
  "graphql": "^16.0.0",
  "graphql-request": "^7.0.0",
  "lucide-react": "^0.468.0",
  "react": "^19.0.0",
  "react-dom": "^19.0.0",
  "react-router-dom": "^7.0.0"
}
```

Use dev dependencies for TypeScript, Vite, React plugin, GraphQL Code Generator client preset, and generated typed document nodes.

Codegen source:

```ts
schema: '../../src/main/resources/graphql/admin.graphqls',
documents: 'src/graphql/**/*.graphql',
generates: {
  './src/generated/': {
    preset: 'client'
  }
}
```

- [x] **Step 3: Add SPA fallback**

Map `/admin-ui`, `/admin-ui/`, and `/admin-ui/**` to static `admin-ui/index.html` while leaving `/admin/graphql` and `/admin/auth/**` untouched.

- [x] **Step 4: Run frontend and backend build checks**

```bash
cd frontend/admin
npm run codegen
npm run build
cd ../..
./gradlew adminUiBuild processResources
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add build.gradle frontend/admin src/main/java/com/mydata/admin/ui
git commit -m "feat: add admin ui build pipeline"
```

### Task 7: React 관리자 기본 화면과 인증 흐름

**Files:**
- Create: `frontend/admin/src/main.tsx`
- Create: `frontend/admin/src/App.tsx`
- Create: `frontend/admin/src/api/adminGraphql.ts`
- Create: `frontend/admin/src/api/csrf.ts`
- Create: `frontend/admin/src/api/auth.ts`
- Create: `frontend/admin/src/routes/LoginPage.tsx`
- Create: `frontend/admin/src/routes/AdminLayout.tsx`
- Create: `frontend/admin/src/routes/DashboardPage.tsx`
- Create: `frontend/admin/src/App.css`
- Modify: `frontend/admin/src/graphql/admin.graphql`

- [x] **Step 1: Write typed operations**

Add operations:

```graphql
query ViewerAndDashboard {
  viewer { id email displayName role }
  dashboardSummary { userCount dataSourceCount runningJobCount }
}
```

- [x] **Step 2: Run codegen**

```bash
cd frontend/admin
npm run codegen
```

Expected: PASS and generated types include `ViewerAndDashboardQuery`.

- [x] **Step 3: Implement login, layout, dashboard**

Login posts to `/admin/auth/login`, layout renders sidebar routes, dashboard uses generated GraphQL operation through `graphql-request` and TanStack Query.

- [x] **Step 4: Run frontend build**

```bash
cd frontend/admin
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/admin
git commit -m "feat: add admin ui shell"
```

### Task 8: React 사용자/데이터소스 관리 화면

**Files:**
- Modify: `frontend/admin/src/graphql/admin.graphql`
- Create: `frontend/admin/src/routes/UsersPage.tsx`
- Create: `frontend/admin/src/routes/UserFormDialog.tsx`
- Create: `frontend/admin/src/routes/DataSourcesPage.tsx`
- Create: `frontend/admin/src/routes/DataSourceFormDialog.tsx`
- Modify: `frontend/admin/src/App.tsx`
- Modify: `frontend/admin/src/App.css`

- [x] **Step 1: Add operations and generate types**

Operations must cover users list/create/update/disable/delete/restore/password reset and data source list/create/update/delete/sync/job history.

```bash
cd frontend/admin
npm run codegen
```

Expected: PASS.

- [x] **Step 2: Implement pages**

Use compact tables, status badges, icon buttons with labels/tooltips, confirmation dialogs for destructive actions, and disabled loading states for mutation buttons.

- [x] **Step 3: Run frontend build**

```bash
cd frontend/admin
npm run build
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/admin
git commit -m "feat: add admin management screens"
```

### Task 9: 문서, 최종 통합 검증, 정리

**Files:**
- Modify: `README.md`
- Modify: `.env.example`
- Modify: `src/main/java/com/mydata/admin/AGENTS.md`
- Modify: `src/test/java/com/mydata/admin/AGENTS.md`

- [ ] **Step 1: Update docs**

README must include:

```bash
ADMIN_BOOTSTRAP_EMAIL=admin@example.com
ADMIN_BOOTSTRAP_PASSWORD=change-me
ADMIN_BOOTSTRAP_DISPLAY_NAME=관리자
```

Also document:

- local app URL: `http://localhost:50506/admin-ui`
- frontend dev: `cd frontend/admin && npm install && npm run codegen && npm run dev`
- GraphQL endpoint: `/admin/graphql`
- old `ADMIN_API_TOKEN` is removed.

- [ ] **Step 2: Run full verification**

```bash
./gradlew test
cd frontend/admin
npm run codegen
npm run build
cd ../..
./gradlew processResources
```

Expected: PASS.

- [ ] **Step 3: Optional browser verification**

Run:

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=local ADMIN_BOOTSTRAP_EMAIL=admin@example.com ADMIN_BOOTSTRAP_PASSWORD=change-me ADMIN_BOOTSTRAP_DISPLAY_NAME=관리자 ./gradlew bootRun
```

Open `http://localhost:50506/admin-ui` and verify login, dashboard, users, data sources pages render.

- [ ] **Step 4: Final commit**

```bash
git add README.md .env.example src/main/java/com/mydata/admin/AGENTS.md src/test/java/com/mydata/admin/AGENTS.md
git commit -m "docs: update admin console run guide"
```

---

## Self-Review

- Spec coverage: DB schema, Spring Security session auth, GraphQL endpoint, user management, data source management, Codegen, React UI, static serving, docs, and tests are covered by Tasks 1-9.
- Placeholder scan: No placeholder markers or unnamed future work remains in this plan.
- Type consistency: `UserRole`, `UserStatus`, `DataSourceVisibility`, `DataSourceType`, `DataSourceStatus`, and `IngestionJobStatus` match the design spec enum naming. GraphQL uses `requestDataSourceSync`, service delegates to existing `requestManualSync`.
