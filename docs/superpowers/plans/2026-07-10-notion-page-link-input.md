# Notion 페이지 링크 입력 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 데이터소스에서 Notion 페이지도 데이터베이스와 동일하게 전체 URL 또는 ID로 입력하고 표준 UUID로 저장한다.

**Architecture:** 관리자 데이터소스 저장 경계인 `AdminDataSourceService`에서 페이지와 데이터베이스가 하나의 Notion ID 정규화 함수를 공유한다. 프런트엔드는 원문을 GraphQL에 전달하고, 서버의 검증 오류는 데이터소스 폼의 `role="alert"` 영역에 짧은 한국어 메시지로 표시한다. GraphQL schema, DB schema, connector 수집 로직은 변경하지 않는다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring GraphQL, JUnit 5, Testcontainers PostgreSQL, React 19, TypeScript 5.8, TanStack Query, Vitest, Testing Library, Playwright

## Global Constraints

- 설계 기준은 `docs/superpowers/specs/2026-07-10-notion-page-link-input-design.md`이다.
- `notionRootPageId`, `notionDatabaseId` GraphQL 필드명과 `config_json` key를 유지한다.
- 전체 HTTP(S) URL, 하이픈 UUID, 32자리 UUID를 허용하고 소문자 `8-4-4-4-12` UUID로 저장한다.
- URL path의 마지막 Notion ID만 추출하며 query parameter와 fragment에만 있는 ID는 허용하지 않는다.
- 기존 database 링크/ID 입력의 허용 범위와 결과를 바꾸지 않는다.
- 기존 데이터 행을 마이그레이션하지 않고 생성·수정 요청부터 새 규칙을 적용한다.
- 실제 Notion API나 실제 token을 자동 테스트에서 사용하지 않는다.
- 사용자-facing UI, 오류, 문서는 한국어로 작성한다.
- 정상 케이스와 실패 케이스 자동 테스트를 모두 추가한다.
- UI 작업 완료 전 Playwright로 정상·실패 화면을 직접 확인한다.
- 실제 비밀값과 `.env`를 출력하거나 커밋하지 않는다.
- 사용자 소유의 미추적 `.playwright-mcp/`는 스테이징하거나 수정하지 않는다.
- 커밋 제목은 Conventional Commits 형식으로 쓰고, 본문에 작업 내용·자동 테스트·Playwright 결과를 기록한다.

## File Map

- `src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java`: Notion page/database 입력의 공통 정규화 및 config 저장 경계.
- `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java`: 생성·수정·실패·database 회귀를 실제 PostgreSQL과 GraphQL 경계에서 검증.
- `frontend/admin/src/routes/DataSourceFormDialog.tsx`: 페이지 링크/ID 라벨, placeholder, 저장 오류 표시.
- `frontend/admin/src/routes/DataSourcesPage.tsx`: mutation 원문 전달, 저장 오류의 사용자 메시지 변환, 폼 재개방 시 오류 초기화.
- `frontend/admin/src/App.test.tsx`: 페이지 URL payload와 실패 오류 화면 검증.
- `docs/notion-integration-setup.md`: 페이지 링크 복사·붙여넣기 안내와 정규화 결과 설명.

---

### Task 1: 서버에서 Notion 페이지 URL과 ID를 공통 정규화

**Files:**
- Modify: `src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java:195-319`
- Test: `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java:16-290`

**Interfaces:**
- Consumes: GraphQL `CreateDataSourceInput.notionRootPageId`, `UpdateDataSourceInput.notionRootPageId`, 기존 `notionDatabaseId` 값.
- Produces: `private static String normalizeNotionId(String value, String fieldName)`과 표준 UUID config 값.

- [ ] **Step 1: 페이지 URL·ID 생성과 실패를 재현하는 테스트를 먼저 작성**

`AdminDataSourceGraphQlTest`에 parameterized test import를 추가한다.

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
```

기존 `createsNotionDataSourceWithRootPageConfig`를 다음 parameterized test로 교체한다.

```java
@ParameterizedTest
@ValueSource(strings = {
    "https://www.notion.so/greatbooms/Project-Wiki-248104cd477e80fdb757e945d38000bd?pvs=4",
    "248104cd477e80fdb757e945d38000bd",
    "248104cd-477e-80fd-b757-e945d38000bd"
})
void createsNotionDataSourceWithRootPageLinkOrIdConfig(String notionRootPageInput) throws Exception {
    String suffix = UUID.randomUUID().toString();
    UserEntity owner = users.save(UserEntity.create("notion-owner-" + suffix + "@example.com", "Owner"));
    WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
    MockHttpSession adminSession = loginAs("notion-admin-" + suffix + "@example.com");

    MvcResult createResult = graphQl(adminSession, """
        mutation {
          createDataSource(input: {
            workspaceId: "%s",
            ownerUserId: "%s",
            type: NOTION,
            name: "Notion wiki",
            visibility: WORKSPACE,
            syncMode: MANUAL,
            notionRootPageId: "%s"
          }) {
            id
            type
            notionRootPageId
          }
        }
        """.formatted(workspace.getId(), owner.getId(), notionRootPageInput))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.createDataSource.type").value("NOTION"))
        .andExpect(jsonPath("$.data.createDataSource.notionRootPageId")
            .value("248104cd-477e-80fd-b757-e945d38000bd"))
        .andReturn();

    String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");
    assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow()
        .configValue("notionRootPageId"))
        .isEqualTo("248104cd-477e-80fd-b757-e945d38000bd");
    assertPolicy(dataSourceId, PrincipalKeys.workspace(workspace.getId()));
}
```

잘못된 링크가 저장되지 않는 테스트를 같은 클래스에 추가한다.

```java
@Test
void rejectsNotionDataSourceWithInvalidRootPageLink() throws Exception {
    String suffix = UUID.randomUUID().toString();
    UserEntity owner = users.save(UserEntity.create("notion-invalid-owner-" + suffix + "@example.com", "Owner"));
    WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
    MockHttpSession adminSession = loginAs("notion-invalid-admin-" + suffix + "@example.com");
    int initialDataSourceCount = dataSources.findByDeletedAtIsNullOrderByCreatedAtDesc().size();

    graphQl(adminSession, """
        mutation {
          createDataSource(input: {
            workspaceId: "%s",
            ownerUserId: "%s",
            type: NOTION,
            name: "Invalid Notion page",
            visibility: WORKSPACE,
            syncMode: MANUAL,
            notionRootPageId: "https://www.notion.so/greatbooms/not-a-page-id"
          }) {
            id
          }
        }
        """.formatted(workspace.getId(), owner.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors[0].message")
            .value("notionRootPageId 형식이 올바르지 않습니다"));

    assertThat(dataSources.findByDeletedAtIsNullOrderByCreatedAtDesc())
        .hasSize(initialDataSourceCount);
}
```

수정 경로도 URL을 정규화하는 테스트를 추가한다.

```java
@Test
void updatesNotionRootPageConfigFromLink() throws Exception {
    String suffix = UUID.randomUUID().toString();
    UserEntity owner = users.save(UserEntity.create("notion-update-owner-" + suffix + "@example.com", "Owner"));
    WorkspaceEntity workspace = workspaces.save(WorkspaceEntity.create(owner.getId(), "Personal"));
    MockHttpSession adminSession = loginAs("notion-update-admin-" + suffix + "@example.com");

    MvcResult createResult = graphQl(adminSession, """
        mutation {
          createDataSource(input: {
            workspaceId: "%s",
            ownerUserId: "%s",
            type: NOTION,
            name: "Notion wiki",
            visibility: WORKSPACE,
            syncMode: MANUAL,
            notionRootPageId: "248104cd477e80fdb757e945d38000bd"
          }) {
            id
          }
        }
        """.formatted(workspace.getId(), owner.getId()))
        .andExpect(status().isOk())
        .andReturn();
    String dataSourceId = JsonPaths.readString(createResult, "$.data.createDataSource.id");

    graphQl(adminSession, """
        mutation {
          updateDataSource(id: "%s", input: {
            notionRootPageId: "https://www.notion.so/greatbooms/Updated-0123456789abcdef0123456789abcdef?pvs=4"
          }) {
            notionRootPageId
          }
        }
        """.formatted(dataSourceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updateDataSource.notionRootPageId")
            .value("01234567-89ab-cdef-0123-456789abcdef"));

    assertThat(dataSources.findById(UUID.fromString(dataSourceId)).orElseThrow()
        .configValue("notionRootPageId"))
        .isEqualTo("01234567-89ab-cdef-0123-456789abcdef");
}
```

- [ ] **Step 2: 대상 백엔드 테스트를 실행해 RED 확인**

Run:

```bash
./gradlew test --tests 'com.mydata.admin.datasources.AdminDataSourceGraphQlTest'
```

Expected: URL/compact ID가 원문으로 반환되어 정규화 기대값이 실패하고, 잘못된 URL이 오류 없이 저장되어 실패한다. 기존 database 링크 테스트는 계속 통과한다.

- [ ] **Step 3: 페이지와 데이터베이스가 공통 정규화 함수를 사용하도록 최소 구현**

`AdminDataSourceService.applyNotionConfig`의 저장 분기를 다음처럼 바꾼다.

```java
if (hasRootPageId) {
    dataSource.putConfig(
        NOTION_ROOT_PAGE_ID_CONFIG_KEY,
        normalizeNotionId(rootPageId, "notionRootPageId")
    );
    dataSource.putConfig(NOTION_DATABASE_ID_CONFIG_KEY, "");
    return;
}

dataSource.putConfig(NOTION_ROOT_PAGE_ID_CONFIG_KEY, "");
dataSource.putConfig(
    NOTION_DATABASE_ID_CONFIG_KEY,
    normalizeNotionId(databaseId, "notionDatabaseId")
);
```

기존 `normalizeNotionDatabaseId`를 다음 공통 함수로 교체한다.

```java
private static String normalizeNotionId(String value, String fieldName) {
    String trimmed = requireText(value, fieldName);
    String candidateSource = trimmed;
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다", exception);
        }
        candidateSource = uri.getPath() == null ? "" : uri.getPath();
    }

    Matcher matcher = NOTION_ID_PATTERN.matcher(candidateSource);
    String candidate = null;
    while (matcher.find()) {
        candidate = matcher.group(1);
    }
    if (candidate == null) {
        throw new IllegalArgumentException(fieldName + " 형식이 올바르지 않습니다");
    }

    String compact = candidate.replace("-", "").toLowerCase();
    return compact.substring(0, 8)
        + "-" + compact.substring(8, 12)
        + "-" + compact.substring(12, 16)
        + "-" + compact.substring(16, 20)
        + "-" + compact.substring(20);
}
```

- [ ] **Step 4: 대상 백엔드 테스트를 다시 실행해 GREEN 확인**

Run:

```bash
./gradlew test --tests 'com.mydata.admin.datasources.AdminDataSourceGraphQlTest'
```

Expected: 모든 parameterized 입력, invalid URL 실패, 수정 경로, 기존 database 링크 회귀 테스트가 통과한다.

- [ ] **Step 5: 서버를 새 코드로 재시작하고 Playwright 정상·실패 경계 확인**

기존 `bootRun` 세션에 `Ctrl-C`를 보내고 다음 명령을 지속 실행 세션으로 시작한다.

```bash
set -a
source .env
set +a
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Expected startup evidence:

```text
Tomcat started on port 20001
Started MyDataApplication
```

Playwright로 다음을 확인한다. 자격증명은 `.env`에서 읽고 화면이나 로그에 출력하지 않는다.

1. `http://localhost:20001/admin-ui/`에서 로그인한다.
2. 데이터소스 화면에서 `NOTION`/`페이지`를 선택하고 표준 페이지 URL을 붙여 넣어 생성한다.
3. 생성된 행을 수정해 페이지 값이 하이픈 UUID로 표시되는지 확인한다.
4. 잘못된 페이지 URL로 생성 시도 후 dialog가 열린 채 유지되고 새 행이 생기지 않는지 확인한다.
5. 정상 케이스에서 만든 QA 데이터소스는 화면의 삭제 버튼으로 정리한다.

- [ ] **Step 6: 백엔드 변경을 커밋**

```bash
git add src/main/java/com/mydata/admin/datasources/AdminDataSourceService.java \
  src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java
git commit -m "feat(notion): accept page links as root IDs" \
  -m "Normalize Notion page links and IDs at the admin data-source boundary while preserving database-link behavior.

자동 테스트: ./gradlew test --tests com.mydata.admin.datasources.AdminDataSourceGraphQlTest
Playwright: 페이지 URL 생성·표준 UUID 표시 정상, 잘못된 링크 저장 거부 확인"
```

---

### Task 2: 관리자 UI 안내·오류 표시와 사용자 문서 완성

**Files:**
- Modify: `frontend/admin/src/routes/DataSourceFormDialog.tsx:23-264`
- Modify: `frontend/admin/src/routes/DataSourcesPage.tsx:58-284`
- Test: `frontend/admin/src/App.test.tsx:580-760`
- Modify: `docs/notion-integration-setup.md:60-114`

**Interfaces:**
- Consumes: Task 1이 반환하는 `notionRootPageId 형식이 올바르지 않습니다` GraphQL error와 `DataSourceFormValues.notionRootPageId` 원문.
- Produces: `DataSourceFormDialog.errorMessage: string | null`, `dataSourceMutationErrorMessage(error: unknown): string | null`, 페이지 링크/ID 라벨과 placeholder.

- [ ] **Step 1: 페이지 링크 payload와 실패 오류 화면 테스트를 먼저 작성**

`App.test.tsx`의 기존 페이지 test 이름을 `Notion 페이지 데이터소스를 만들 때 페이지 링크를 함께 보낸다`로 바꾸고, 다음 상수와 기대값을 사용한다.

```ts
const pageLink = 'https://www.notion.so/greatbooms/Project-Wiki-248104cd477e80fdb757e945d38000bd?pvs=4';
```

기존 test 안의 fixture 응답은 서버 정규화 결과를 사용한다.

```ts
notionRootPageId: '248104cd-477e-80fd-b757-e945d38000bd'
```

입력과 payload 검증을 다음으로 교체한다.

```ts
const pageInput = screen.getByLabelText('Notion 루트 페이지 링크 또는 ID');
expect(pageInput).toHaveAttribute(
  'placeholder',
  'https://www.notion.so/workspace/Project-Wiki-...'
);
fireEvent.change(pageInput, {
  target: { value: pageLink }
});
fireEvent.click(screen.getByRole('button', { name: '저장' }));

await waitFor(() => {
  expect(fetchMock).toHaveBeenCalledTimes(5);
});
const createBody = JSON.parse((fetchMock.mock.calls[3][1] as RequestInit).body as string);
expect(createBody.variables.input).toMatchObject({
  name: 'Notion wiki',
  notionRootPageId: pageLink,
  ownerUserId: 'user-id',
  type: 'NOTION',
  workspaceId: 'workspace-id'
});
expect(createBody.variables.input.notionDatabaseId).toBeUndefined();
```

잘못된 페이지 링크의 서버 오류를 표시하는 test를 추가한다.

```ts
it('잘못된 Notion 페이지 링크 저장 오류를 폼에 표시한다', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(jsonResponse({
      headerName: 'X-CSRF-TOKEN',
      parameterName: '_csrf',
      token: 'csrf-token'
    }))
    .mockResolvedValueOnce(adminDataSourcesResponse([]))
    .mockResolvedValueOnce(adminDataSourceFormOptionsResponse({
      users: [{
        id: 'user-id',
        email: 'owner@example.com',
        displayName: '데이터 오너',
        role: 'USER',
        status: 'ACTIVE',
        deletedAt: null
      }],
      workspaces: [{
        id: 'workspace-id',
        ownerUserId: 'user-id',
        name: 'Personal',
        deletedAt: null
      }]
    }))
    .mockResolvedValueOnce(jsonResponse({
      data: { createDataSource: null },
      errors: [{ message: 'notionRootPageId 형식이 올바르지 않습니다' }]
    }));
  vi.stubGlobal('fetch', fetchMock);

  renderApp('/data-sources');

  expect(await screen.findByText('데이터소스가 없습니다.')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '데이터소스 추가' }));
  expect(await screen.findByRole('option', { name: 'Personal' })).toBeVisible();
  fireEvent.change(screen.getByLabelText('이름'), {
    target: { value: 'Invalid Notion page' }
  });
  fireEvent.change(screen.getByLabelText('워크스페이스'), {
    target: { value: 'workspace-id' }
  });
  fireEvent.change(screen.getByLabelText('소유 유저'), {
    target: { value: 'user-id' }
  });
  fireEvent.change(screen.getByLabelText('종류'), {
    target: { value: 'NOTION' }
  });
  fireEvent.change(screen.getByLabelText('Notion 루트 페이지 링크 또는 ID'), {
    target: { value: 'https://www.notion.so/greatbooms/not-a-page-id' }
  });
  fireEvent.click(screen.getByRole('button', { name: '저장' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'notionRootPageId 형식이 올바르지 않습니다'
  );
  expect(screen.getByRole('dialog', { name: '데이터소스 추가' })).toBeVisible();
  expect(fetchMock).toHaveBeenCalledTimes(4);
});
```

- [ ] **Step 2: 대상 프런트엔드 test를 실행해 RED 확인**

Run:

```bash
cd frontend/admin
npm test -- App.test.tsx
```

Expected: 새 페이지 라벨을 찾지 못하고, mutation 실패 뒤 `role="alert"`가 없어 실패한다.

- [ ] **Step 3: 폼 라벨·오류 표시·오류 초기화를 최소 구현**

`DataSourceFormDialogProps`에 오류 문자열을 추가한다.

```ts
type DataSourceFormDialogProps = {
  dataSource: DataSourceFieldsFragment | null;
  errorMessage: string | null;
  isSubmitting: boolean;
  users: UserFieldsFragment[];
  workspaces: WorkspaceFieldsFragment[];
  onClose: () => void;
  onSubmit: (values: DataSourceFormValues) => void;
};
```

component 인자에서도 `errorMessage`를 받고, footer 직전에 표시한다.

```tsx
{errorMessage ? <p className="form-error" role="alert">{errorMessage}</p> : null}
```

페이지 입력 UI를 다음으로 교체한다.

```tsx
<label>
  Notion 루트 페이지 링크 또는 ID
  <input
    value={values.notionRootPageId}
    disabled={Boolean(dataSource && dataSource.type !== 'NOTION')}
    placeholder="https://www.notion.so/workspace/Project-Wiki-..."
    required
    onChange={(event) => setValues({ ...values, notionRootPageId: event.target.value })}
  />
</label>
```

`DataSourcesPage`의 form open/close 함수에서 이전 mutation 오류를 초기화한다.

```ts
function openCreateForm() {
  saveDataSourceMutation.reset();
  setEditingDataSource(null);
  setIsFormOpen(true);
}

function openEditForm(dataSource: DataSourceFieldsFragment) {
  saveDataSourceMutation.reset();
  setEditingDataSource(dataSource);
  setIsFormOpen(true);
}

function closeForm() {
  saveDataSourceMutation.reset();
  setEditingDataSource(null);
  setIsFormOpen(false);
}
```

dialog 호출에 오류 message를 전달한다.

```tsx
<DataSourceFormDialog
  dataSource={editingDataSource}
  errorMessage={dataSourceMutationErrorMessage(saveDataSourceMutation.error)}
  isSubmitting={saveDataSourceMutation.isPending}
  users={users}
  workspaces={workspaces}
  onClose={closeForm}
  onSubmit={(values) => saveDataSourceMutation.mutate(values)}
/>
```

파일 아래에 사용자-facing 오류 변환 함수를 추가한다.

```ts
function dataSourceMutationErrorMessage(error: unknown): string | null {
  if (error === null || error === undefined) {
    return null;
  }
  if (error instanceof Error
    && error.message.includes('notionRootPageId 형식이 올바르지 않습니다')) {
    return 'notionRootPageId 형식이 올바르지 않습니다';
  }
  return '데이터소스를 저장하지 못했습니다.';
}
```

- [ ] **Step 4: 대상 프런트엔드 test를 다시 실행해 GREEN 확인**

Run:

```bash
cd frontend/admin
npm test -- App.test.tsx
```

Expected: page 링크 payload, database payload 회귀, invalid 링크 alert test가 모두 통과한다.

- [ ] **Step 5: 사용자 안내 문서를 링크 붙여넣기 흐름으로 갱신**

`docs/notion-integration-setup.md`의 페이지 section 제목과 안내를 다음 내용으로 바꾼다.

```markdown
## 4. Notion 페이지 링크 또는 ID 확인하기

관리자 화면에서 `Notion 수집 대상`을 `페이지`로 선택하면 `Notion 루트 페이지 링크 또는 ID`를 입력합니다.

가장 쉬운 방법:

1. Notion에서 수집할 루트 페이지를 엽니다.
2. 우측 상단 `Share`에서 링크를 복사합니다.
3. 복사한 링크 전체를 관리자 화면에 붙여 넣습니다.

서버는 URL path의 마지막 page ID를 추출해 소문자 하이픈 UUID로 저장합니다. 전체 링크 대신 32자리 ID나 하이픈 UUID를 직접 입력해도 됩니다.
```

데이터소스 생성 절차의 페이지 항목을 다음으로 교체한다.

```markdown
5. 페이지를 선택한 경우 `Notion 루트 페이지 링크 또는 ID`에 복사한 페이지 링크나 ID를 입력합니다.
```

- [ ] **Step 6: 전체 자동 검증 실행**

Run:

```bash
./gradlew test
```

Expected: Gradle `BUILD SUCCESSFUL`, Java test 실패 0건.

Run:

```bash
cd frontend/admin
npm test
npm run build
```

Expected: Vitest 실패 0건, TypeScript 검사와 Vite production build 성공.

Run:

```bash
git diff --check
```

Expected: 출력 없음, exit code 0.

- [ ] **Step 7: 서버를 최종 코드로 재시작하고 Playwright 정상 케이스 확인**

기존 `bootRun` 세션에 `Ctrl-C`를 보내고 Task 1과 동일한 local profile 명령으로 재시작한다. 로그에서 `Tomcat started on port 20001`과 `Started MyDataApplication`을 확인한다.

Playwright 정상 시나리오:

1. `http://localhost:20001/admin-ui/`에 접속하고 `.env` 관리자 계정으로 로그인한다.
2. 데이터소스 화면에서 `데이터소스 추가`를 누른다.
3. 기존 workspace와 owner를 선택하고 종류 `NOTION`, 수집 대상 `페이지`를 선택한다.
4. `Notion 루트 페이지 링크 또는 ID` 필드와 page URL placeholder가 보이는지 확인한다.
5. 이름 `Notion 페이지 링크 QA`와 `https://www.notion.so/greatbooms/Project-Wiki-248104cd477e80fdb757e945d38000bd?pvs=4`를 입력해 저장한다.
6. 목록에 새 행이 생기는지 확인하고 수정 dialog에서 `248104cd-477e-80fd-b757-e945d38000bd`가 표시되는지 확인한다.
7. dialog를 닫고 QA 데이터소스를 삭제해 정리한다.

- [ ] **Step 8: Playwright 실패 케이스 확인**

1. 새 데이터소스 dialog를 다시 열고 `NOTION`/`페이지`를 선택한다.
2. 이름 `잘못된 Notion 페이지 QA`와 `https://www.notion.so/greatbooms/not-a-page-id`를 입력한다.
3. 저장 후 dialog가 닫히지 않고 `notionRootPageId 형식이 올바르지 않습니다` alert가 보이는지 확인한다.
4. 목록에 `잘못된 Notion 페이지 QA` 행이 생기지 않았는지 확인한다.
5. dialog를 닫고 최종 화면에 민감정보가 노출되지 않았는지 확인한다.

- [ ] **Step 9: UI·문서 변경을 커밋**

```bash
git add frontend/admin/src/routes/DataSourceFormDialog.tsx \
  frontend/admin/src/routes/DataSourcesPage.tsx \
  frontend/admin/src/App.test.tsx \
  docs/notion-integration-setup.md
git commit -m "feat(admin): support Notion page link input" \
  -m "Update the admin form and guide for Notion page links, and surface server validation errors without changing GraphQL fields.

자동 테스트: ./gradlew test; cd frontend/admin && npm test && npm run build; git diff --check
Playwright: 페이지 URL 생성·정규화 표시 정상, invalid 링크 alert·미저장 확인"
```

---

### Task 3: 최종 범위와 작업 트리 검증

**Files:**
- Verify only: all files changed by Tasks 1-2

**Interfaces:**
- Consumes: Task 1의 표준 UUID 저장과 Task 2의 링크 UI·오류 alert.
- Produces: 커밋 범위, 자동 테스트, 실행 서버, 브라우저 결과가 일치한다는 최종 증거.

- [ ] **Step 1: 커밋과 변경 범위 확인**

Run:

```bash
git log --oneline --decorate -5
git status --short --branch
git diff main...HEAD --stat
```

Expected:

- 브랜치는 `feat/notion-page-link-input`이다.
- 설계, backend 구현, UI·문서 커밋이 보인다.
- 사용자 소유 `.playwright-mcp/` 외에 의도하지 않은 미추적·수정 파일이 없다.
- DB changelog, GraphQL schema, generated GraphQL 파일은 변경 목록에 없다.

- [ ] **Step 2: 완료 보고용 증거 정리**

다음을 사용자에게 한국어로 보고한다.

- 페이지 URL/ID와 database URL/ID가 동일한 정규화 경계를 사용한다.
- 저장 형식은 소문자 하이픈 UUID이고 기존 데이터 migration은 없다.
- 자동 테스트별 명령과 통과 결과.
- Playwright 정상·실패 시나리오와 실제 확인 결과.
- 서버 접속 URL `http://localhost:20001/admin-ui/`과 실행 상태.
- 커밋 hash와 변경 파일 범위.
