# 임베딩 재생성(재임베딩) 설계

## 목표

임베딩 provider나 모델을 바꾼 뒤(예: `deterministic-1536` → `text-embedding-3-small`), 이미 수집해 저장한 문서를 **원본 재조회 없이** 현재 임베딩 모델로 다시 임베딩한다. 관리자가 데이터소스별로 실행하고 진행률을 눈으로 확인할 수 있게 한다.

원본(Notion/Slack 등)을 다시 수집하지 않는다. 저장된 `document_chunks` 텍스트를 그대로 사용해 현재 모델 임베딩만 채운다. 그래서 Slack 커서 증분의 영향을 받지 않고 오래된 문서까지 100% 커버한다.

## 배경과 현재 동작

- 검색은 질의 임베딩과 저장 임베딩을 **같은 `embedding_model` 문자열**로만 매칭한다(`PgVectorSearchRepository`의 `WHERE e.embedding_model = ?`). 그래서 모델을 바꾸면 옛 모델로 저장된 문서는 검색에서 빠진다.
- 수집 파이프라인에는 이미 부분 백필 로직이 있다: `IngestionPipelineService.backfillMissingEmbeddings`가 문서의 청크 중 `DocumentEmbeddingRepository.existsByChunkIdAndModel(chunkId, embeddingClient.model())`이 없는 것만 `embed()` 후 `upsert`한다. 단, 이 백필은 "문서가 다시 방출될 때"(재수집 시)에만 문서 단위로 실행된다.
- 그 결과 모델 교체 후 재수집으로 백필하려면 커넥터별 한계에 걸린다. Notion은 `FULL_SNAPSHOT`이라 전체가 다시 방출되어 전량 백필되지만, Slack은 커서(`latestMessageTs`) 이후만 방출하므로 오래된 메시지의 청크는 백필되지 않는다.
- 즉 모델 교체는 드문 이벤트인데, 이를 "전체 재수집"으로 처리하면 외부 API 재조회·rate limit·Slack 커서 리셋 같은 부작용이 따른다. 문서 내용은 그대로이므로 원본 재조회가 필요 없다.

이 문서는 저장된 청크만 대상으로 현재 모델 임베딩을 채우는 **재임베딩 경로**를 추가한다. 기존 `backfillMissingEmbeddings`의 멱등 백필 원칙을 데이터소스 전체 청크로 확장하는 것이다.

## 확정된 결정

1. 재임베딩은 **저장된 `document_chunks`만** 사용한다. 커넥터/원본 재조회는 하지 않는다.
2. 현재 `embeddingClient.model()` 임베딩이 **없는 청크만** `embed()` 후 `upsert`한다. 이미 있으면 건너뛴다 → **멱등**(중복 실행·서버 재시작·부분 실패 후 재실행 모두 안전).
3. 실행 단위는 **데이터소스별**이다.
4. 오래 걸리는 작업이므로 요청은 즉시 반환하고 실제 처리는 **백그라운드(`@Async`)** 로 돈다.
5. 같은 데이터소스에 대한 재임베딩이 이미 진행 중이면 **중복 실행을 막는다**(인메모리 실행 플래그).
6. 다른 모델(예: `deterministic-1536`)의 기존 임베딩은 **삭제하지 않고 남긴다**. 검색은 현재 모델만 필터하므로 무해하고, 롤백 여지도 남는다. 잔여 임베딩 일괄 정리는 이 범위 밖이다.
7. 진행률은 별도 상태 테이블 없이 **DB로 계산**한다: `coverage = (현재 모델 임베딩이 있는 청크 수) / (데이터소스의 활성 문서 청크 총수)`.
8. 청크 개별 임베딩 실패(예: OpenAI 오류)는 로그를 남기고 **건너뛰고 계속**한다. 남은 청크는 다음 실행에서 멱등으로 채운다.
9. 관리자 화면에서 **데이터소스별 "임베딩 재생성" 버튼 + 커버리지 표시**로 노출한다. 버튼 클릭 후 커버리지를 폴링해 진행률이 오르는 것을 실시간으로 보여준다.

## 범위

포함:
- 저장된 청크를 현재 모델로 재임베딩하는 백엔드 서비스(`EmbeddingMigrationService`)
- 데이터소스별 재임베딩 시작 GraphQL mutation과 커버리지 조회
- 관리자 UI의 "임베딩 재생성" 버튼과 진행률(커버리지) 표시
- 정상/실패 케이스 백엔드 테스트, 프론트 타입체크·단위 테스트, Playwright 실제 화면 확인

제외:
- 다른 모델의 잔여 임베딩 삭제/정리
- 임베딩 차원 변경(pgvector 컬럼 스키마 변경)
- 서버 재시작 시 진행 중이던 재임베딩의 자동 재개(재클릭으로 이어서 처리 — 멱등)
- 전체(모든 데이터소스) 일괄 실행 버튼

## 컴포넌트

### 1. `EmbeddingMigrationService` (신규, 백엔드)

- `reembedDataSource(UUID dataSourceId)`
  - 데이터소스의 활성(삭제되지 않은) 문서에 속한 청크를 배치로 순회한다.
  - 각 청크에 대해 `existsByChunkIdAndModel(chunkId, embeddingClient.model())`이 거짓이면 `embeddings.upsert(chunkId, embeddingClient.model(), embeddingClient.embed(chunk.content))`.
  - `@Async`로 실행하고, 시작 시 `running` 집합(`ConcurrentHashMap.newKeySet`)에 dataSourceId를 넣고 종료 시 제거한다. 이미 있으면 시작하지 않는다.
  - 청크 임베딩 실패는 `log.warn` 후 다음 청크로 진행한다.
- `coverage(UUID dataSourceId)` → `{ totalChunks, coveredChunks }`
  - `totalChunks`: 데이터소스 활성 문서의 청크 총수.
  - `coveredChunks`: 그 청크 중 현재 모델 임베딩이 있는 수.
- 의존: 데이터소스의 활성 문서 청크를 배치로 조회하는 리포지토리 메서드(신규), `EmbeddingClient`, `DocumentEmbeddingRepository`.

### 2. GraphQL (admin)

- `mutation reembedDataSource(id: ID!): ReembedStatus`
  - `ReembedStatus { running: Boolean!, coverage: EmbeddingCoverage! }`
  - 백그라운드 재임베딩을 시작(또는 이미 진행 중이면 그대로)하고 현재 상태를 즉시 반환한다.
- `DataSource.embeddingCoverage: EmbeddingCoverage`
  - `EmbeddingCoverage { model: String!, totalChunks: Int!, coveredChunks: Int! }`
  - 진행 폴링용.

### 3. 관리자 UI (`frontend/admin`)

- 데이터소스 행(또는 상세)에 커버리지 표시(예: `1273/1448 (88%)`)와 **"임베딩 재생성"** 버튼.
- 버튼 클릭 → `reembedDataSource` mutation → 이후 `embeddingCoverage`를 일정 간격으로 폴링해 진행률을 갱신한다. `coveredChunks == totalChunks`면 완료로 표시한다.

## 데이터 흐름

```
[관리자] "임베딩 재생성" 클릭
   → reembedDataSource(id) mutation (즉시 반환: running=true, 현재 coverage)
   → EmbeddingMigrationService가 @Async로 청크 순회
        · 현재 모델 임베딩 없는 청크만 embed()+upsert()
        · 실패 청크는 로그 남기고 계속
   → UI가 embeddingCoverage(id)를 폴링 → coveredChunks 증가 표시
   → coveredChunks == totalChunks → 완료
```

## 에러 처리와 엣지

- **OpenAI 키 없음/API 오류**: 청크 `embed()`가 예외 → 해당 청크 건너뛰고 로그. 실행 종료 후 커버리지가 100% 미만이면 재클릭으로 이어서 채운다(멱등).
- **provider가 `deterministic`**: 재임베딩은 그대로 동작해 그 모델로 채운다(막지 않음). 실질 효과는 없지만 안전하다.
- **서버 재시작**: 인메모리 `running` 플래그가 사라져 진행 중이던 재임베딩은 멈춘다. 커버리지가 진실이므로 재클릭하면 남은 청크부터 채운다.
- **동시 실행**: 같은 데이터소스는 `running` 플래그로 1개만. 서로 다른 데이터소스는 병렬 허용.
- **청크가 없는 데이터소스**: `totalChunks == 0` → 커버리지는 0/0(완료로 표시), 아무 작업도 하지 않는다.

## 테스트

- **백엔드**(결정적 임베딩 클라이언트로 검증, 실제 OpenAI 불필요)
  - 정상: 현재 모델 임베딩이 없는 청크만 채우고, 이미 있는 청크는 다시 임베딩하지 않는다(멱등) — upsert 호출 수/커버리지로 확인.
  - 실패: 특정 청크 임베딩이 예외를 던져도 나머지는 계속 채워지고, 재실행 시 실패했던 청크가 채워진다.
  - 커버리지 계산이 활성 문서 청크 기준으로 정확하다(삭제 문서 제외).
- **프론트**: 버튼·커버리지 렌더, `npm run codegen` / `tsc --noEmit` / `vitest` 통과.
- **Playwright**: 데이터소스에서 "임베딩 재생성" 클릭 → 커버리지가 표시/갱신되는지 실제 화면 확인.
