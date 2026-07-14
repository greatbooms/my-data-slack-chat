# Notion 전체 스냅샷 문서 정리 설계

## 목표

Notion 데이터소스를 다시 수집했을 때 현재 수집 범위에서 사라진 페이지와 데이터베이스 행을 검색 대상에서 제거한다. 같은 Notion 객체가 다시 나타나면 새 문서를 만들지 않고 기존 문서를 복구한다.

수집한 모든 ID를 애플리케이션 메모리에 보관하거나 전체 Notion 순회 동안 DB 트랜잭션을 유지하지 않는다. 일부 항목이라도 수집에 실패하면 기존 문서를 삭제하지 않는 것을 기본 안전 원칙으로 삼는다.

## 배경과 현재 동작

- Notion 페이지 모드는 루트 페이지, 하위 페이지, 하위 데이터베이스와 행을 매 수집마다 전체 순회한다.
- 문서는 `(data_source_id, external_id)`로 upsert되어 같은 Notion ID의 제목, 경로와 내용 변경은 기존 행에 반영된다.
- 수집 중 성공한 문서는 건별 짧은 트랜잭션으로 저장되고, 같은 트랜잭션에서 `ingestion_job_items.document_id`가 기록된다.
- 일부 항목이 실패하면 성공한 문서는 유지되고 job은 `PARTIAL_FAILED`가 된다.
- `external_documents.deleted_at` 컬럼과 검색 쿼리의 `deleted_at IS NULL` 조건은 이미 존재한다.
- JPA 엔티티에는 `deleted_at`이 매핑되어 있지 않고, 성공한 새 스냅샷에 나타나지 않은 기존 문서를 정리하는 단계도 없다.
- 같은 데이터소스에 서로 다른 수집 job이 동시에 `RUNNING`이 되는 것을 현재 claim 로직은 막지 않는다.

따라서 페이지 구조를 바꾸거나 행을 삭제해도 이전 문서는 계속 검색된다. 반대로 단순히 모든 성공 job에 누락 문서 정리를 적용하면 cursor 이후 변경분만 읽는 Slack의 과거 메시지를 잘못 삭제하게 된다.

이 문서는 `2026-07-13-notion-nested-database-ingestion-design.md`에서 제외했던 “기존 문서 삭제 감지와 tombstone 처리”를 후속 범위로 추가한다. 해당 문서의 부분 실패와 짧은 트랜잭션 원칙은 그대로 유지한다.

## 확정된 결정

1. 누락 문서 정리는 현재 Notion 데이터소스에만 적용한다.
2. 커넥터가 `FULL_SNAPSHOT` 정리 가능 여부를 명시하게 하고 기본값은 정리하지 않는 모드로 둔다.
3. 현재 job의 성공 `ingestion_job_items`를 DB에 저장된 확인 목록(seen set)으로 재사용한다.
4. connector가 정상 종료하고 실패 item이 하나도 없는 경우에만 누락 문서를 소프트 삭제한다.
5. `PARTIAL_FAILED`, `FAILED` 또는 예외 종료에서는 누락 문서를 전혀 삭제하지 않는다.
6. 같은 `(data_source_id, external_id)` 문서가 다시 발견되면 기존 ID를 유지한 채 `deleted_at`을 해제한다.
7. 같은 데이터소스의 job은 전체 실행 구간에서 논리적으로 직렬화한다.
8. 원격 API 호출은 트랜잭션 밖에서, 문서 저장과 최종 정리는 각각 짧은 트랜잭션에서 실행한다.

## 범위

- Notion 커넥터의 `FULL_SNAPSHOT` 정리 모드 선언
- 성공 job item의 `document_id`를 이용한 누락 문서 소프트 삭제
- 삭제된 동일 ID 문서의 재수집 복구
- 같은 데이터소스의 동시 수집 차단
- 정리 쿼리와 동시 실행 제약을 위한 Liquibase 인덱스
- 정상, 부분 실패, 전체 실패, 예외 종료와 증분 커넥터 회귀 테스트
- 삭제 및 복구 후 pgvector 검색 결과 검증

## 제외 범위

- Slack, `LOCAL_TEXT`, Google Drive의 누락 문서 정리
- Notion 휴지통 이력이나 삭제 이벤트를 별도로 조회하는 기능
- 문서, chunk, embedding과 ACL의 물리 삭제
- 삭제된 문서 목록과 정리 개수를 관리자 UI에 새로 표시하는 기능
- job item 장기 보관 및 정리 정책
- 중단된 `RUNNING` job의 lease, timeout과 자동 복구
- Notion 재귀 탐색 자체가 사용하는 방문 ID 집합의 외부 저장소 이전

마지막 항목은 수집 결과 전체 ID를 reconciliation 목적으로 추가 보관하지 않는다는 요구와 구분한다. Notion 순환 및 중복 방문 방지를 위한 page/database ID 집합은 기존처럼 실행 중 메모리에 유지한다.

## 핵심 불변 조건

- full snapshot 정리는 `Notion + connector 정상 반환 + 실패 item 0개`가 모두 참일 때만 실행한다.
- 정리 대상은 현재 job과 같은 workspace 및 data source의 문서로 한정한다.
- 현재 job의 성공 item에 연결된 문서는 삭제하지 않는다.
- 이미 소프트 삭제된 문서의 최초 `deleted_at`은 다시 덮어쓰지 않는다.
- 정리와 job `SUCCEEDED` 전환은 하나의 최종화 트랜잭션에서 함께 성공하거나 함께 롤백한다.
- 같은 data source에 `RUNNING` job은 최대 하나만 존재한다.
- 정리 여부를 판단하기 위해 문서 ID 목록을 JVM 컬렉션이나 SQL `IN` 파라미터로 만들지 않는다.

## 대안 비교

### 기존 job item 재사용 — 채택

성공 문서와 성공 job item은 같은 트랜잭션에서 저장된다. 따라서 `ingestion_job_items(job_id, document_id)`는 이미 crash-safe한 현재 snapshot 확인 목록이다. DB anti-join으로 누락 문서를 찾을 수 있어 별도 데이터 모델과 중복 쓰기가 필요 없다.

### `external_documents.last_seen_job_id` 추가 — 제외

각 문서 재수집 때 marker를 갱신하고 다른 marker를 가진 행을 삭제하는 방식이다. 모든 문서에 가변 상태를 추가하고 job 보존 수명과 문서를 결합한다. 동시 job이 marker를 서로 덮어쓰면 오삭제 위험도 생긴다.

### 별도 seen 테이블 또는 메모리 집합 — 제외

별도 테이블은 현재 job item과 기능과 쓰기 비용이 중복된다. 메모리 집합은 문서 수에 비례해 커지고 재시작에 취약하며, 최종 SQL에 큰 ID 목록을 전달해야 한다.

## Connector 정리 모드

공통 connector 계약에 다음 정리 모드를 추가한다.

- `NONE`: 성공하더라도 누락 문서를 정리하지 않는다. 기본값이다.
- `FULL_SNAPSHOT`: 이번 실행이 데이터소스의 현재 전체 범위를 열거하므로 완전 성공 시 누락 문서를 정리할 수 있다.

현재는 `NotionPageConnector`만 `FULL_SNAPSHOT`을 반환한다. Slack은 cursor 이후 메시지만 배출하므로 `NONE`을 유지한다. `LOCAL_TEXT`가 기술적으로 단일 전체 문서를 배출하더라도 이번 기능의 사용자 승인 범위가 Notion뿐이므로 `NONE`을 유지한다. Google Drive도 실제 전체 snapshot 계약이 구현되기 전까지 기본값을 사용한다.

정리 모드는 타입을 검사하는 worker 분기 대신 connector 계약에서 읽는다. 새 커넥터가 추가될 때 전체 범위 보장이 명시되지 않으면 fail-closed로 정리하지 않는다.

## 전체 데이터 흐름

```text
수집 요청
  -> 짧은 claim 트랜잭션
       -> data source row 잠금
       -> 같은 source의 RUNNING job이 없을 때만 PENDING -> RUNNING
  -> 짧은 읽기 트랜잭션: data source snapshot 로드
  -> 트랜잭션 밖: Notion 전체 순회
       -> 문서마다 짧은 트랜잭션
            -> 문서 upsert 또는 복구
            -> ACL/chunk/embedding 반영
            -> SUCCEEDED job item과 document_id 기록
       -> 대상 실패마다 짧은 트랜잭션
            -> FAILED job item 기록
  -> 짧은 최종화 트랜잭션
       -> 성공/실패 item 집계
       -> 실패 0 + FULL_SNAPSHOT이면 DB anti-join 정리
       -> cursor/last_synced_at과 SUCCEEDED 상태 반영
```

connector가 예외를 던지면 최종화 단계에 들어가지 않는다. 이미 성공한 문서별 트랜잭션은 유지하되 누락 문서 정리는 하지 않고 job만 별도 짧은 트랜잭션으로 `FAILED` 처리한다.

## 문서 저장과 복구

`ExternalDocumentEntity`에 `deleted_at`을 매핑한다. 기존 문서를 재수집하는 모든 공통 갱신 경로에서 `deletedAt = null`로 복구한다.

- 내용이 변경된 재등장 문서: 같은 document ID를 복구하고 chunk와 embedding을 새 내용으로 교체한다.
- 내용이 동일한 재등장 문서: 같은 document ID와 기존 chunk/embedding을 유지하면서 metadata를 갱신하고 tombstone만 해제한다.
- 새 문서: `deleted_at`이 없는 활성 상태로 생성한다.

복구는 Notion 전용 분기에 넣지 않는다. 이미 `(data_source_id, external_id)`로 찾은 문서를 수집으로 갱신한다는 공통 엔티티 의미에 포함해 unchanged fast path에서도 빠지지 않게 한다. 정리 기능은 Notion에만 실행되므로 다른 connector의 동작은 실질적으로 변하지 않는다.

## 누락 문서 정리

최종화 트랜잭션은 실패 item이 0개이고 connector 정리 모드가 `FULL_SNAPSHOT`일 때만 정리 저장소를 호출한다. 저장소 쿼리도 호출 계층 실수에 대비해 job 상태와 실패 item을 다시 확인한다.

개념적인 SQL은 다음과 같다.

```sql
UPDATE external_documents d
SET deleted_at = now(),
    updated_at = now()
FROM ingestion_jobs j
WHERE j.id = :jobId
  AND j.status = 'SUCCEEDED'
  AND d.workspace_id = j.workspace_id
  AND d.data_source_id = j.data_source_id
  AND d.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ingestion_job_items failed
      WHERE failed.job_id = j.id
        AND failed.status = 'FAILED'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM ingestion_job_items seen
      WHERE seen.job_id = j.id
        AND seen.status = 'SUCCEEDED'
        AND seen.document_id = d.id
  );
```

job을 `SUCCEEDED`로 바꾸고 flush한 뒤 위 쿼리를 실행하되, 둘은 같은 트랜잭션 안에 둔다. 쿼리가 실패하면 성공 상태와 data source 동기화 시각도 함께 롤백되고 worker의 최상위 실패 처리에서 job을 `FAILED`로 기록한다.

`external_id`는 비교에 사용하지 않는다. job item에는 `page:<id>`, `database:<id>`처럼 대상 종류가 붙은 ID가 저장되지만 문서에는 원본 external ID가 저장되므로, 실제 저장된 `document_id` 연결만 사용한다.

ACL, chunk와 embedding은 물리 삭제하지 않는다. 검색 쿼리가 문서의 `deleted_at IS NULL`을 확인하므로 즉시 검색에서 제외되며, 같은 ID가 다시 나타날 때 기존 하위 데이터를 활용하거나 내용 변경 경로에서 교체할 수 있다.

## 부분 실패와 오류 처리

| 실행 결과 | 문서별 성공 반영 | 누락 문서 정리 | cursor/last synced | job 상태 |
|---|---|---|---|---|
| Notion 전체 성공 | 유지 | 실행 | 갱신 | `SUCCEEDED` |
| Notion 일부 실패 | 성공분 유지 | 실행 안 함 | 갱신 안 함 | `PARTIAL_FAILED` |
| Notion 전부 실패 | 없음 | 실행 안 함 | 갱신 안 함 | `FAILED` |
| connector 예외 종료 | 예외 전 성공분 유지 | 실행 안 함 | 갱신 안 함 | `FAILED` |
| Slack 성공 | 성공분 유지 | 실행 안 함 | 갱신 | `SUCCEEDED` |

부분 실패 job에서는 실패한 가지 아래 문서가 이번 job item에 없더라도 삭제하지 않는다. 어느 페이지를 왜 읽지 못했는지는 기존 실패 item과 관리자 화면에서 확인한다. 다음 완전 성공 job이 현재 전체 상태를 확인한 뒤에만 삭제가 반영된다.

빈 full snapshot이 정상 완료되고 실패 item도 없다면 해당 data source의 활성 문서를 모두 소프트 삭제한다. Notion 페이지 수집에서는 보통 루트 문서가 존재하지만, 이 규칙을 명시해 완전한 빈 snapshot의 의미를 일관되게 유지한다.

## 같은 데이터소스의 job 직렬화

현재 원자 claim은 같은 job ID의 중복 실행만 차단한다. 서로 다른 job 두 개가 같은 data source를 동시에 순회하면 늦게 끝난 오래된 snapshot이 최신 문서를 누락으로 판단할 수 있다. 콘텐츠 갱신과 Slack cursor에도 같은 경쟁 문제가 있다.

claim 트랜잭션에서 다음 순서를 적용한다.

1. 대상 job의 data source ID를 확인한다.
2. 해당 `data_sources` 행을 `PESSIMISTIC_WRITE`/`FOR UPDATE`로 잠근다.
3. 같은 data source에 다른 `RUNNING` job이 없는지 확인한다.
4. 조건을 만족할 때만 현재 job을 `RUNNING`으로 변경한다.

같은 source를 claim하는 트랜잭션은 동일한 data source 행에서 직렬화된다. 이미 실행 중인 job이 있으면 두 번째 job은 `PENDING`에 남고 scheduler의 다음 주기에 재시도한다. 다른 data source의 job은 서로 막지 않는다. 외부 API 순회 동안 행 잠금을 유지하지 않으므로 긴 DB 트랜잭션은 생기지 않는다.

DB 최종 불변 조건으로 `ingestion_jobs(data_source_id) WHERE status = 'RUNNING'` partial unique index도 둔다. 마이그레이션 시 기존 동일 source에 여러 `RUNNING` job이 있으면 임의로 winner를 선택하지 않고 precondition을 실패시켜 운영자가 상태를 확인하게 한다.

중단된 프로세스가 남긴 `RUNNING` job은 기존과 마찬가지로 후속 job을 막을 수 있다. lease와 자동 복구는 별도 운영 기능으로 남기되, 이번 변경으로 조용히 동시 실행을 허용하지 않는다.

## 트랜잭션 경계

- claim: data source 잠금과 `PENDING -> RUNNING` 전환만 포함하는 짧은 트랜잭션
- snapshot 로드: connector에 전달할 불변 설정을 읽는 짧은 트랜잭션
- 원격 순회: DB 트랜잭션 없음
- 문서 성공: document, ACL, chunk, embedding과 성공 job item을 함께 저장하는 건별 짧은 트랜잭션
- 대상 실패: 실패 job item 하나를 저장하는 짧은 트랜잭션
- 최종화: item 집계, 선택적 정리, cursor/last synced와 job 상태를 함께 반영하는 짧은 트랜잭션

문서 수가 많아져도 최종 정리는 set-based UPDATE 한 번이다. JVM에는 reconciliation용 전체 ID 목록을 만들지 않으며, 원격 호출 시간만큼 DB connection이나 transaction을 점유하지 않는다.

## DB 변경

기존 `deleted_at` 컬럼을 사용하므로 문서 컬럼 추가나 backfill은 없다. 새 `007` Liquibase changeset에는 다음 인덱스를 추가한다.

```sql
CREATE INDEX IF NOT EXISTS idx_ingestion_job_items_job_succeeded_document
    ON ingestion_job_items(job_id, document_id)
    WHERE status = 'SUCCEEDED' AND document_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ingestion_jobs_running_data_source
    ON ingestion_jobs(data_source_id)
    WHERE status = 'RUNNING';
```

첫 인덱스는 현재 job의 성공 document anti-join을 지원한다. 기존 job item 조회 인덱스에는 `document_id`가 없어 이 목적을 충분히 지원하지 못한다. `external_documents`는 기존 `(data_source_id, external_id)` 인덱스의 선두 컬럼으로 source 범위를 좁힐 수 있으므로 우선 새 인덱스를 추가하지 않는다.

`db.changelog-master.json`은 include 전용으로 유지하고 실제 SQL은 `db/changelog/changes/007-full-snapshot-reconciliation-indexes.sql`에 둔다. JPA의 `deletedAt` 매핑은 최초 스키마의 타입과 nullable 조건에 맞춘다.

## ACL과 검색

- 소프트 삭제는 workspace, data source와 문서 ID 관계를 바꾸지 않는다.
- 기존 ACL, chunk와 embedding을 남겨도 모든 pgvector 검색 경로가 `external_documents.deleted_at IS NULL`을 적용하므로 삭제 문서는 결과에 포함되지 않는다.
- 복구 시 기존 ACL은 수집 pipeline의 현재 교체 규칙에 따라 다시 동기화된다.
- principal이 없거나 비어 있는 수집과 검색은 기존 fail-closed 규칙을 유지한다.
- 정리 SQL은 job ID에서 workspace와 data source를 유도해 다른 범위의 문서를 건드리지 않는다.

## 관측성과 관리자 동작

새 관리자 UI는 추가하지 않는다. 기존 job 상태, 성공/실패 개수와 실패 상세를 그대로 사용한다.

- 완전 성공 시 서버 로그에 job ID, data source ID와 소프트 삭제된 문서 수를 남긴다.
- 부분 실패와 전체 실패에서는 삭제 수 로그를 성공처럼 남기지 않는다.
- 정리된 문서를 별도 성공/실패 job item으로 추가하지 않아 기존 item 개수 의미를 바꾸지 않는다.
- 실패 이유에는 기존처럼 credential, 응답 body와 stack trace를 저장하지 않는다.

## 테스트와 검증

### Connector 계약

- Notion이 `FULL_SNAPSHOT` 정리 모드를 선언한다.
- Slack과 `LOCAL_TEXT`가 기본 `NONE`을 유지한다.

### Pipeline과 복구

- 내용이 바뀐 소프트 삭제 문서를 재수집하면 같은 document ID로 복구하고 chunk/embedding을 교체한다.
- 내용이 같은 소프트 삭제 문서를 재수집해도 같은 ID로 복구하고 기존 chunk/embedding을 유지한다.

### Worker와 정리

- 성공한 Notion snapshot은 현재 job에서 본 문서는 유지하고 보지 못한 문서만 삭제한다.
- 빈 성공 snapshot은 해당 data source의 활성 문서를 모두 삭제한다.
- 다른 data source와 workspace의 문서는 유지한다.
- `PARTIAL_FAILED`와 전체 항목 실패에서는 보지 못한 문서를 유지한다.
- 일부 문서 성공 후 connector 예외가 발생해도 보지 못한 문서를 유지한다.
- 성공한 증분 connector job은 이전 문서를 유지한다.
- 정리 SQL 실패 시 job이 `SUCCEEDED`로 남지 않는다.

### 동시 실행

- 같은 data source의 서로 다른 두 job은 동시에 `RUNNING`이 되지 않는다.
- 두 번째 job은 첫 job 실행 중 `PENDING`에 남는다.
- 첫 job 종료 후 두 번째 job을 다시 claim할 수 있다.
- 다른 data source의 job은 독립적으로 claim할 수 있다.

### DB와 검색

- Liquibase가 `007` changeset과 두 인덱스를 생성한다.
- 기존 동일 source의 복수 `RUNNING` 상태를 precondition이 거부한다.
- 소프트 삭제된 문서는 ACL이 일치해도 pgvector 검색 결과에서 제외된다.
- 같은 ID를 복구하면 다시 검색 결과에 포함된다.

### 실제 로컬 확인

이 변경은 관리자 UI를 수정하지 않으므로 Playwright 화면 변경 검증 대상은 아니다. 로컬 서버에서 Notion 테스트 데이터소스를 다음 순서로 확인한다.

1. 루트 아래에 임시 페이지 또는 database row를 만들고 완전 수집해 검색 가능함을 확인한다.
2. 해당 항목을 루트 범위에서 제거하고 다시 완전 수집해 검색 결과에서 제외됨을 확인한다.
3. 항목을 되돌리고 재수집해 같은 document ID가 활성화되고 다시 검색되는지 확인한다.
4. 접근 권한을 잠시 제거한 child database가 있는 실패 케이스에서는 `PARTIAL_FAILED`가 되고 기존 문서가 유지되는지 확인한다.

외부 Notion 상태를 바꾸는 실제 확인은 사용자 소유 테스트 페이지에서만 수행하며, 자동 검증은 격리된 fixture와 integration test를 기본으로 한다.

## 호환성과 배포

- 기존 document ID, chunk, embedding과 citation 관계를 유지한다.
- 소프트 삭제는 물리 삭제가 아니므로 이전 chat citation의 FK를 깨뜨리지 않는다.
- 기존 Notion 문서는 다음 완전 성공 수집 전까지 그대로 활성 상태다.
- migration 전 worker를 중지하고 동일 data source의 복수 `RUNNING` job이 없는지 확인한다.
- migration 후에는 먼저 단일 Notion 테스트 source에서 완전 성공과 삭제 개수를 확인한 뒤 일반 수집을 재개한다.
- 부분 실패 상태에서 기존 문서가 유지되는지 운영 로그와 관리자 실패 상세로 확인한다.

## 완료 기준

- Notion의 완전 성공 재수집 후 현재 범위에서 사라진 문서는 검색되지 않는다.
- 구조를 바꾸더라도 같은 Notion ID의 문서는 기존 ID로 갱신된다.
- 삭제됐던 같은 ID가 다시 발견되면 복구되어 검색된다.
- 일부 또는 전체 실패 수집은 기존 문서를 삭제하지 않는다.
- Slack과 다른 커넥터의 이전 문서는 성공 수집 후에도 유지된다.
- reconciliation을 위해 전체 문서 ID를 메모리에 쌓지 않는다.
- 원격 Notion 호출 동안 DB 트랜잭션을 유지하지 않는다.
- 같은 data source의 수집 job은 동시에 실행되지 않는다.
- Liquibase, JPA 엔티티와 PostgreSQL 쿼리가 일치한다.
- 정상 케이스와 실패 케이스의 integration test가 통과한다.
