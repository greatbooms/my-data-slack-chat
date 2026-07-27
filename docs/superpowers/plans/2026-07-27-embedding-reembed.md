# 임베딩 재생성(재임베딩) 구현 계획

> **For agentic workers:** 이 계획은 task 단위로 구현한다. 이 저장소 규칙(CLAUDE.md)에 따라 실제 구현은 Codex에 위임하고, push 전 Codex 적대적 리뷰를 통과시킨다. 각 task는 독립적으로 검증 가능한 단위다.

**Goal:** 임베딩 provider/모델 교체 후, 저장된 `document_chunks`를 원본 재조회 없이 현재 모델로 재임베딩하고, 관리자 화면에서 데이터소스별로 실행·진행률을 확인한다.

**Architecture:** 신규 `EmbeddingMigrationService`가 데이터소스의 활성 문서 청크를 순회하며 현재 `EmbeddingClient.model()` 임베딩이 없는 청크만 `embed()`+`upsert`한다(멱등 백필). 실행은 `@Async` 백그라운드, 진행률은 DB 커버리지 조회로 계산한다. GraphQL mutation으로 시작하고 `DataSource.embeddingCoverage` 필드를 폴링해 admin UI에 진행률을 표시한다.

**Tech Stack:** Java 21, Spring Boot, Spring GraphQL, JPA/JdbcTemplate, JUnit + Testcontainers(Postgres), React(admin) + graphql-codegen, Playwright.

## Global Constraints

- 사용자-facing 문자열/로그/주석은 한국어로 작성한다.
- Liquibase 스키마 변경 없음(기존 테이블만 사용). 신규 의존성 없음.
- 검색은 `embedding_model`로 필터한다(불변). 다른 모델(예: `deterministic-1536`)의 기존 임베딩은 삭제하지 않고 남긴다.
- 재임베딩은 저장된 `document_chunks`만 사용한다. 커넥터/원본 재조회는 하지 않는다.
- 커버리지 = (현재 모델 임베딩이 있는 청크 수) / (데이터소스의 `deleted_at IS NULL` 문서에 속한 청크 총수).
- 관리자 API는 `@PreAuthorize("hasRole('ADMIN')")` 규칙을 따른다(기존 admin 서비스와 동일).

---

### Task 1: 데이터소스별 청크 커버리지 조회

**Files:**
- Modify: `src/main/java/com/mydata/documents/DocumentChunkRepository.java`
- Create: `src/main/java/com/mydata/embeddings/EmbeddingCoverageProjection.java`
- Test: `src/test/java/com/mydata/embeddings/EmbeddingMigrationServiceIntegrationTest.java` (Task 2와 공유; 여기서는 커버리지 조회만 검증)

**Interfaces:**
- Produces:
  - `interface EmbeddingCoverageProjection { long getTotalChunks(); long getCoveredChunks(); }`
  - `EmbeddingCoverageProjection DocumentChunkRepository.coverageByDataSource(UUID dataSourceId, String model)`
  - `List<DocumentChunkEntity>` 순회용(스트림/배치): 데이터소스 활성 문서 청크 조회. 배치 처리를 위해 `List<UUID> DocumentChunkRepository.findChunkIdsByDataSource(UUID dataSourceId)` + 기존 `findById`/`findByDocumentIdOrderByChunkIndex` 재사용, 또는 청크 엔티티 직접 조회 `List<DocumentChunkEntity> findByDataSource(UUID dataSourceId)`.

- [ ] **Step 1: 커버리지/청크 조회 쿼리와 projection 추가**

`EmbeddingCoverageProjection.java`:
```java
package com.mydata.embeddings;

public interface EmbeddingCoverageProjection {
    long getTotalChunks();
    long getCoveredChunks();
}
```

`DocumentChunkRepository.java`에 추가(기존 native 쿼리 스타일 참고):
```java
@Query(value = """
    SELECT count(*) AS "totalChunks",
           count(*) FILTER (WHERE e.chunk_id IS NOT NULL) AS "coveredChunks"
    FROM document_chunks c
    JOIN external_documents d ON d.id = c.document_id
    LEFT JOIN document_embeddings e
      ON e.chunk_id = c.id AND e.embedding_model = :model
    WHERE d.data_source_id = :dataSourceId
      AND d.deleted_at IS NULL
    """, nativeQuery = true)
EmbeddingCoverageProjection coverageByDataSource(
    @Param("dataSourceId") UUID dataSourceId, @Param("model") String model);

@Query("""
    SELECT chunk FROM DocumentChunkEntity chunk
    WHERE chunk.document.dataSourceId = :dataSourceId
      AND chunk.document.deletedAt IS NULL
    ORDER BY chunk.document.id, chunk.chunkIndex
    """)
List<DocumentChunkEntity> findByDataSource(@Param("dataSourceId") UUID dataSourceId);
```
주의: `DocumentChunkEntity.document`는 `ExternalDocumentEntity` 연관이다. `ExternalDocumentEntity`의 `dataSourceId`/`deletedAt` 필드명이 JPQL 경로와 맞는지 확인하고, 다르면 native SQL로 청크 목록도 조회한다.

- [ ] **Step 2: 커버리지 조회 테스트(실패 확인 → 통과)**

`EmbeddingMigrationServiceIntegrationTest`(PostgresIntegrationTest 확장)에서 fixture로 데이터소스 1개 + 문서 2개 + 청크 N개를 만들고, 일부 청크에만 현재 모델(`deterministic-1536`) 임베딩을 `DocumentEmbeddingRepository.upsert`로 넣은 뒤:
```java
EmbeddingCoverageProjection cov = chunks.coverageByDataSource(dataSourceId, "deterministic-1536");
assertThat(cov.getTotalChunks()).isEqualTo(전체청크수);
assertThat(cov.getCoveredChunks()).isEqualTo(임베딩넣은청크수);
```
삭제된 문서(`deleted_at` 세팅)의 청크는 total에서 제외됨을 함께 검증.

- [ ] **Step 3: 실행/검증/커밋**

Run: `./gradlew test --tests '*EmbeddingMigrationServiceIntegrationTest*'` → 통과.
Commit: `feat(embeddings): add per-data-source chunk coverage query`

---

### Task 2: 재임베딩 서비스 (@Async, 멱등, 실패 스킵)

**Files:**
- Modify: `src/main/java/com/mydata/MyDataApplication.java` (`@EnableAsync` 추가)
- Create: `src/main/java/com/mydata/embeddings/EmbeddingMigrationService.java`
- Test: `src/test/java/com/mydata/embeddings/EmbeddingMigrationServiceIntegrationTest.java` (확장)

**Interfaces:**
- Consumes: `DocumentChunkRepository.findByDataSource`, `coverageByDataSource`(Task 1); `DocumentEmbeddingRepository.existsByChunkIdAndModel`/`upsert`; `EmbeddingClient.model()`/`embed()`.
- Produces:
  - `record EmbeddingCoverage(String model, long totalChunks, long coveredChunks)`
  - `EmbeddingCoverage EmbeddingMigrationService.coverage(UUID dataSourceId)`
  - `boolean EmbeddingMigrationService.isRunning(UUID dataSourceId)`
  - `void EmbeddingMigrationService.startReembed(UUID dataSourceId)` — 동기 진입점이 `@Async` 내부 메서드를 호출(같은 빈 self-invocation 회피: 내부 `@Async reembedAsync`를 별도 접근으로 호출하거나 `running` 등록만 동기로 하고 처리를 async 메서드에 위임). 구현 시 self-invocation 프록시 이슈 주의.

- [ ] **Step 1: `@EnableAsync` 추가**

`MyDataApplication.java`의 `@EnableScheduling` 옆에 `@EnableAsync` 추가(import `org.springframework.scheduling.annotation.EnableAsync`).

- [ ] **Step 2: 서비스 작성**

```java
@Service
public class EmbeddingMigrationService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingMigrationService.class);
    private final DocumentChunkRepository chunks;
    private final DocumentEmbeddingRepository embeddings;
    private final EmbeddingClient embeddingClient;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    // 생성자 주입

    public EmbeddingCoverage coverage(UUID dataSourceId) {
        String model = embeddingClient.model();
        EmbeddingCoverageProjection p = chunks.coverageByDataSource(dataSourceId, model);
        long total = p == null ? 0 : p.getTotalChunks();
        long covered = p == null ? 0 : p.getCoveredChunks();
        return new EmbeddingCoverage(model, total, covered);
    }

    public boolean isRunning(UUID dataSourceId) { return running.contains(dataSourceId); }

    public boolean startReembed(UUID dataSourceId) {
        if (!running.add(dataSourceId)) return false; // 이미 진행 중
        reembedAsync(dataSourceId);
        return true;
    }

    @Async
    public void reembedAsync(UUID dataSourceId) {
        String model = embeddingClient.model();
        try {
            for (DocumentChunkEntity chunk : chunks.findByDataSource(dataSourceId)) {
                try {
                    if (!embeddings.existsByChunkIdAndModel(chunk.getId(), model)) {
                        embeddings.upsert(chunk.getId(), model, embeddingClient.embed(chunk.getContent()));
                    }
                } catch (RuntimeException e) {
                    log.warn("청크 재임베딩 실패, 건너뜀: chunk={}", chunk.getId(), e);
                }
            }
        } finally {
            running.remove(dataSourceId);
        }
    }
    public record EmbeddingCoverage(String model, long totalChunks, long coveredChunks) {}
}
```
주의: `startReembed`→`reembedAsync`는 같은 빈 self-invocation이라 `@Async` 프록시가 적용 안 될 수 있다. 안전하게 하려면 `reembedAsync`를 별도 빈으로 분리하거나 `ApplicationContext`를 통해 프록시 인스턴스를 호출한다. 테스트에서는 async 여부와 무관하게 `reembedAsync`를 직접 호출해 로직을 검증한다.

- [ ] **Step 3: 멱등/실패-스킵/커버리지 테스트**

TestConfiguration으로 특정 청크에서 예외를 던지는 `EmbeddingClient` 스텁을 주입(또는 spy). 검증:
- 현재 모델 임베딩이 없는 청크만 `upsert`되고, 이미 있는 청크는 재임베딩되지 않는다(호출 카운트 또는 커버리지 before/after).
- 한 청크가 `embed()`에서 예외를 던져도 나머지 청크는 채워지고, 다시 실행하면 실패했던 청크가 채워진다.
- `reembedAsync` 후 `coverage(dataSourceId).coveredChunks() == totalChunks()`.

- [ ] **Step 4: 실행/검증/커밋**

Run: `./gradlew test --tests '*EmbeddingMigrationServiceIntegrationTest*'` → 통과.
Commit: `feat(embeddings): add EmbeddingMigrationService for chunk re-embedding`

---

### Task 3: GraphQL 노출 (mutation + 커버리지 필드)

**Files:**
- Modify: `src/main/resources/graphql/admin.graphqls`
- Create: `src/main/java/com/mydata/admin/datasources/EmbeddingCoveragePayload.java`, `ReembedStatusPayload.java`
- Modify: `src/main/java/com/mydata/admin/graphql/AdminGraphQlController.java`
- Test: `src/test/java/com/mydata/admin/datasources/AdminDataSourceGraphQlTest.java` (확장)

**Interfaces:**
- Consumes: `EmbeddingMigrationService.startReembed`/`isRunning`/`coverage`(Task 2).
- Produces:
  - GraphQL: `type EmbeddingCoverage { model: String!, totalChunks: Int!, coveredChunks: Int! }`, `type ReembedStatus { running: Boolean!, coverage: EmbeddingCoverage! }`, `extend type DataSource { embeddingCoverage: EmbeddingCoverage! }`(또는 `DataSource`에 필드 직접 추가), `mutation reembedDataSource(id: ID!): ReembedStatus!`.

- [ ] **Step 1: 스키마에 타입/필드/mutation 추가**

`admin.graphqls`:
```graphql
type EmbeddingCoverage { model: String!, totalChunks: Int!, coveredChunks: Int! }
type ReembedStatus { running: Boolean!, coverage: EmbeddingCoverage! }
```
`type DataSource { ... }`에 `embeddingCoverage: EmbeddingCoverage!` 추가. `type Mutation`에 `reembedDataSource(id: ID!): ReembedStatus!` 추가.

- [ ] **Step 2: payload 레코드**

```java
public record EmbeddingCoveragePayload(String model, int totalChunks, int coveredChunks) {
    static EmbeddingCoveragePayload from(EmbeddingMigrationService.EmbeddingCoverage c) {
        return new EmbeddingCoveragePayload(c.model(),
            Math.toIntExact(c.totalChunks()), Math.toIntExact(c.coveredChunks()));
    }
}
public record ReembedStatusPayload(boolean running, EmbeddingCoveragePayload coverage) {}
```

- [ ] **Step 3: 컨트롤러 매핑**

`AdminGraphQlController`에 `EmbeddingMigrationService` 주입 후:
```java
@MutationMapping
@PreAuthorize("hasRole('ADMIN')")
public ReembedStatusPayload reembedDataSource(@Argument String id) {
    UUID dsId = UUID.fromString(id);
    embeddingMigration.startReembed(dsId); // 이미 진행 중이면 false 반환, 무시
    return new ReembedStatusPayload(embeddingMigration.isRunning(dsId),
        EmbeddingCoveragePayload.from(embeddingMigration.coverage(dsId)));
}

@SchemaMapping(typeName = "DataSource", field = "embeddingCoverage")
public EmbeddingCoveragePayload embeddingCoverage(AdminDataSourcePayload dataSource) {
    return EmbeddingCoveragePayload.from(embeddingMigration.coverage(dataSource.id()));
}
```
주의: `@SchemaMapping`의 소스 타입은 `DataSource`를 매핑하는 payload(`AdminDataSourcePayload`)와 일치해야 한다. 실제 `dataSources`/`dataSource` 쿼리가 반환하는 타입 기준으로 소스 파라미터 타입을 맞춘다.

- [ ] **Step 4: GraphQL 통합 테스트**

`AdminDataSourceGraphQlTest`에 추가:
- 정상: 데이터소스에 청크+일부 임베딩을 만든 뒤 `reembedDataSource(id)` mutation → `running`/`coverage` 필드가 오고, 잠시 후(또는 서비스 직접 호출로) `dataSources { embeddingCoverage { coveredChunks totalChunks } }`가 채워짐을 확인.
- 실패: 잘못된 id(UUID 아님) → GraphQL 오류.

- [ ] **Step 5: 실행/검증/커밋**

Run: `./gradlew test --tests '*AdminDataSourceGraphQlTest*'` → 통과. 그리고 전체 `./gradlew test`.
Commit: `feat(embeddings): expose re-embedding via admin GraphQL`

---

### Task 4: 관리자 UI (버튼 + 진행률 폴링)

**Files:**
- Modify: `frontend/admin/src/graphql/admin.graphql`
- Modify: `frontend/admin/src/routes/DataSourcesPage.tsx`
- Modify: `frontend/admin/src/api/adminGraphql.ts` (mutation 호출 추가)
- Regenerate: `frontend/admin/src/generated/*`
- Verify: Playwright

**Interfaces:**
- Consumes: `reembedDataSource` mutation, `DataSource.embeddingCoverage`(Task 3).

- [ ] **Step 1: fragment/mutation 추가 + codegen**

`admin.graphql`의 `fragment DataSourceFields`에 추가:
```graphql
embeddingCoverage { model totalChunks coveredChunks }
```
그리고 mutation:
```graphql
mutation ReembedDataSource($id: ID!) {
  reembedDataSource(id: $id) { running coverage { totalChunks coveredChunks } }
}
```
`adminGraphql.ts`에 `reembedDataSource(id)` 호출 함수 추가(기존 mutation 헬퍼 패턴 따라). 그다음 `cd frontend/admin && npm run codegen`.

- [ ] **Step 2: 데이터소스 행에 커버리지 + 버튼**

`DataSourcesPage.tsx`의 데이터소스 행(작업 셀 근처)에:
- 커버리지 표시: `{c.coveredChunks}/{c.totalChunks}` (0/0이면 "-"). 백분율 병기 가능.
- **"임베딩 재생성"** 버튼: 클릭 시 `ReembedDataSource` mutation 실행 → 성공 후 `admin-data-sources` 쿼리를 일정 간격으로 refetch(`react-query`의 `refetchInterval` 또는 버튼 클릭 후 폴링)해 `coveredChunks` 증가를 표시. `coveredChunks === totalChunks`면 폴링 중단.

- [ ] **Step 3: 프론트 검증**

Run: `cd frontend/admin && npm run codegen && npx tsc --noEmit && npm run test` → 통과.

- [ ] **Step 4: Playwright 실제 화면 확인**

DB throwaway로 앱 기동(기존 검증 방식 재사용) → admin 로그인 → 데이터소스에서 "임베딩 재생성" 클릭 → 커버리지 표시/갱신 확인. 스크린샷 저장(scratchpad).

- [ ] **Step 5: 커밋**

Commit: `feat(admin-ui): add re-embedding button with coverage progress`

---

## 검증(전체)

- `./gradlew test` 전체 통과.
- `frontend/admin`: `npm run codegen` / `tsc --noEmit` / `vitest` 통과.
- Playwright: "임베딩 재생성" 버튼 클릭 → 진행률(커버리지) 표시·갱신 실제 화면 확인.
- push 전 Codex 적대적 리뷰(멱등성, self-invocation `@Async` 적용 여부, 커버리지 쿼리 정확성, 동시 실행/중복 방지, 권한) 통과.

## Self-Review 메모(계획 대비 스펙 커버리지)

- 스펙 "저장된 청크만 재임베딩/멱등" → Task 2.
- 스펙 "데이터소스별/백그라운드/중복 방지" → Task 2(`@Async`, `running`).
- 스펙 "옛 임베딩 유지" → 어떤 task도 삭제하지 않음(Global Constraints).
- 스펙 "커버리지로 진행률" → Task 1(쿼리) + Task 3(노출) + Task 4(표시).
- 스펙 "admin 버튼 + 진행률" → Task 4.
- 스펙 "청크 실패 건너뛰고 계속" → Task 2 Step 2/3.
- 알려진 위험: `@Async` self-invocation 프록시 미적용 가능성 → Task 2 Interfaces/Step 2에 명시(분리 또는 프록시 호출로 해결, 테스트는 로직 직접 검증).
