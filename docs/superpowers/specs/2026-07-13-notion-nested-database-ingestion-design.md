# Notion 하위 데이터베이스 자동 수집과 부분 실패 설계

## 목표

Notion 페이지 링크를 루트로 등록하면 루트 페이지와 모든 하위 페이지를 재귀적으로 수집하면서, 그 범위에서 발견한 데이터베이스도 자동으로 찾아 행을 수집한다. 일부 페이지나 데이터베이스를 읽지 못하더라도 성공한 문서는 유지하고, 실패한 대상과 이유를 수집 job에서 확인할 수 있게 한다.

원격 Notion API 호출은 DB 트랜잭션 밖에서 수행하고 문서는 건별 짧은 트랜잭션으로 반영해, 전체 수집 시간 동안 트랜잭션과 DB 연결을 점유하지 않게 한다.

## 배경과 현재 동작

- 페이지 모드는 `child_page` 블록을 따라 하위 페이지를 재귀 수집한다.
- `child_database` 블록은 일반 하위 블록처럼 취급될 뿐, database 조회와 data source query 경로로 전환되지 않는다.
- 명시적인 데이터베이스 모드는 database를 조회하고 그 아래 하나의 data source를 query해 행 페이지를 수집한다.
- `NotionClient.queryDataSourcePages`는 모든 페이지네이션 결과를 하나의 목록에 모은다.
- `IngestionWorker`는 connector의 원격 조회와 `IngestionPipelineService` 저장을 하나의 트랜잭션 안에서 실행한다.
- Java에는 `PARTIAL_FAILED` job 상태가 있고 DB에는 `ingestion_job_items` 테이블이 있지만, worker·GraphQL·관리자 UI에서는 사용하지 않는다.
- 관리자 UI는 job의 상태와 시간만 표시하며 실패 항목은 표시하지 않는다.

현재 구조에서는 큰 Notion 트리를 수집할수록 메모리 사용량과 트랜잭션 시간이 함께 늘어난다. 하위 database 하나가 실패하면 이미 처리한 다른 페이지까지 전부 롤백되고 실패 위치도 job 단위 오류 문자열로만 남는다.

## 확정된 결정

1. 페이지 모드는 루트 페이지 아래의 모든 `child_page`와 `child_database`를 재귀적으로 자동 수집한다.
2. 같은 페이지 또는 database가 여러 경로에 나타나면 ID 기준으로 한 번만 처리한다.
3. 성공한 문서는 즉시 건별 저장하고, 실패한 대상은 기록한 뒤 형제 페이지와 database 수집을 계속한다.
4. 일부 성공과 일부 실패가 함께 있으면 job을 `PARTIAL_FAILED`로 종료한다.
5. 원격 API 호출 중에는 DB 트랜잭션을 열지 않는다.
6. 전체 문서를 메모리에 쌓지 않고 현재 문서 하나와 API 페이지 한 묶음만 유지한다.
7. 명시적인 데이터베이스 모드는 유지하며 같은 부분 실패·건별 저장 경계를 사용한다.

## 범위

- 페이지 블록 트리에서 `child_database` 자동 발견
- 하위 페이지에서 발견한 database까지 재귀 수집
- database와 data source의 기존 조회·행 수집 로직 재사용
- 페이지·database 중복 방문 방지
- data source query와 block children 조회의 페이지 단위 스트리밍
- connector 실행과 DB 저장의 트랜잭션 경계 분리
- 문서별 성공·실패를 `ingestion_job_items`에 기록
- `SUCCEEDED`, `PARTIAL_FAILED`, `FAILED` 최종 상태 계산
- 관리자 GraphQL에서 job item 조회와 부분 실패 상태 제공
- 관리자 UI에서 실패 대상과 이유 표시
- Notion 설정 문서와 운영 안내 갱신

## 제외 범위

- Notion UI view의 필터·정렬을 그대로 재현하는 기능
- `?v=` view ID 기준의 부분 행 수집
- 페이지 모드에 database 자체 링크를 넣었을 때 root 타입을 자동 전환하는 기능
- 단순 `link_to_page` 링크를 따라 임의의 외부 페이지나 database까지 확장하는 기능
- Notion API가 원본 ID를 제공하지 않는 linked database를 추측해 해석하는 기능
- 여러 data source를 가진 database의 자동 선택
- 기존 문서 삭제 감지와 tombstone 처리
- 중단된 `RUNNING` job의 lease·재시작 복구
- 여러 worker가 같은 data source를 동시에 처리하는 분산 잠금
- job item 장기 보관 정책과 정리 배치

페이지에 표시된 database view의 필터가 아니라, 발견한 database 아래 단일 data source의 전체 행을 수집한다. linked database를 사용하려면 원본 database가 integration에 공유돼 있고 API가 조회 가능한 database ID를 제공해야 한다.

## 전체 데이터 흐름

```text
수집 요청
  -> 짧은 트랜잭션: PENDING job을 RUNNING으로 claim
  -> 짧은 읽기 트랜잭션: job과 data source 설정 snapshot 로드
  -> 트랜잭션 밖: Notion 페이지/database 재귀 탐색
       -> 문서 생성 성공
            -> 짧은 트랜잭션: 문서·chunk·embedding·ACL upsert
            -> 같은 트랜잭션: SUCCEEDED job item 기록
       -> 조회 또는 저장 실패
            -> 짧은 트랜잭션: FAILED job item 기록
            -> 형제 대상 수집 계속
  -> 짧은 트랜잭션: job item 집계 후 최종 job 상태 기록
```

worker가 job을 claim한 뒤 connector에 전달하는 값은 영속 JPA entity가 아니라 수집에 필요한 ID, type, config, visibility, owner/workspace principal, cursor를 담은 불변 snapshot으로 만든다. snapshot을 만든 읽기 트랜잭션은 connector 호출 전에 종료한다. connector는 이 snapshot을 읽기만 하며 DB 저장을 직접 수행하지 않는다.

## Notion API와 스트리밍 경계

### Block children

`NotionBlock`은 현재의 ID, type, plain text, child 여부에 더해 database 관련 실패를 식별할 수 있는 최소 참조 정보를 유지한다.

- `child_page`: block ID를 하위 page ID로 수집한다.
- `child_database`: block ID를 database ID로 수집한다.
- database 관련 `unsupported` 블록: underlying block type을 보존하고 실패 항목으로 기록한다.
- `link_to_page`: 자동 수집 범위를 무한히 넓힐 수 있으므로 따라가지 않는다.

block children API는 페이지네이션 한 묶음씩 consumer에 전달한다. 현재 페이지 문서의 본문 조립에 필요한 텍스트만 메모리에 유지하고, 전체 workspace의 block을 한 목록에 모으지 않는다.

### Data source query

`queryDataSourcePages`의 전체 목록 반환 계약을 페이지네이션 consumer 또는 iterator 계약으로 바꾼다. API 응답 한 묶음의 page를 순서대로 connector에 전달하고 다음 cursor를 요청하기 전에 현재 묶음을 처리한다.

메모리 상한은 다음 데이터에 비례한다.

- 현재 문서 한 개의 본문과 metadata
- Notion API 페이지 한 묶음(최대 100개)의 경량 page 객체
- 방문한 page ID와 database ID 집합
- 현재 job의 성공·실패 개수

전체 수집 문서 본문을 메모리에 누적하지 않는다.

## Connector 설계

### 방문 상태

페이지 모드의 한 수집 실행은 다음 상태를 공유한다.

- `visitedPageIds`: child page와 database row page의 중복 방지
- `visitedDatabaseIds`: 같은 database view의 중복 수집 방지
- 현재 상위 page ID, 제목, 경로와 depth

database row는 Notion에서 page 객체이므로 `visitedPageIds`에 포함한다. 동일한 page가 다른 경로에서 다시 발견되면 첫 처리 결과만 유지한다.

### 페이지 처리

페이지 하나의 처리 순서는 다음과 같다.

1. page metadata를 조회한다.
2. block children을 페이지네이션하며 본문, child page ID, child database ID를 수집한다.
3. 완성된 page 문서를 success event로 전달한다.
4. child page를 재귀 처리한다.
5. child database를 database 수집 경로로 전달한다.

한 page의 metadata 또는 block 조회가 실패하면 불완전한 문서를 저장하지 않는다. 해당 page failure event를 전달하고 같은 depth의 다른 형제 대상을 계속 처리한다.

### 데이터베이스 처리

database 하나의 처리 순서는 다음과 같다.

1. database를 조회한다.
2. data source가 정확히 하나인지 검증한다.
3. data source query를 페이지네이션한다.
4. 각 row page를 일반 page와 같은 순회 함수로 처리한다.

row page의 본문, 하위 page와 그 안의 child database도 같은 방문 상태와 자동 발견 규칙을 사용한다. row metadata의 경로는 `상위 page 경로 / database 제목 / row 제목`으로 이어진다.

database 조회 또는 data source query 자체가 실패하면 database ID와 상위 경로를 가진 failure event 하나를 기록한다. 이미 성공한 다른 database와 page는 유지하고 다음 형제 대상으로 진행한다. query가 중간 cursor에서 실패하면 그전까지 성공한 row는 유지하고 database failure를 추가한다.

## 수집 event 계약

기존 `DocumentHandler`의 성공 문서 callback만으로는 실패 위치를 worker에 전달할 수 없다. `DataSourceConnector`와 worker 사이에 다음 두 event를 표현하는 공통 경계를 둔다. `LOCAL_TEXT`와 Slack connector도 새 성공 event 계약에 맞추되 현재의 수집 범위와 오류 의미는 바꾸지 않는다. Notion connector만 탐색 가능한 형제 단위의 실패를 failure event로 전환해 계속 진행한다.

### 성공 event

- `RawExternalDocument`
- 대상 page ID
- 상위 경로

worker의 sink는 한 번의 짧은 트랜잭션에서 pipeline upsert와 성공 job item 기록을 함께 처리한다. pipeline은 저장된 document ID를 sink에 돌려주거나 같은 트랜잭션 안에서 이를 확인할 수 있게 한다. pipeline 저장에 실패하면 그 트랜잭션을 롤백하고 별도 짧은 트랜잭션으로 실패 item을 기록한다. 이전 수집에서 저장된 해당 문서 버전은 유지한다.

### 실패 event

- 대상 종류: `PAGE`, `DATABASE`, `DATA_SOURCE`, `BLOCK`
- 외부 ID
- 상위 경로와 가능한 제목
- 실패 단계: `RETRIEVE`, `LIST_BLOCKS`, `QUERY`, `PERSIST`
- 사용자에게 보여 줄 정제된 원인

API token, Authorization header, 원본 응답 body와 stack trace는 job item reason에 저장하지 않는다. 서버 로그에는 기존 예외 stack을 남기되 사용자 화면에는 Notion status/code와 행동 가능한 설명만 보여 준다.

## 트랜잭션 설계

### Claim

기존 원자적 `PENDING -> RUNNING` update를 짧은 트랜잭션으로 유지한다.

### 건별 성공 저장

문서 하나마다 다음을 하나의 트랜잭션으로 묶는다.

- external document upsert
- ACL 교체
- chunk 교체
- embedding upsert
- `ingestion_job_items` 성공 행 추가

현재 embedding은 결정적 로컬 구현이므로 이 트랜잭션에 외부 네트워크 호출은 없다. 향후 원격 embedding을 도입할 때에는 embedding 계산도 트랜잭션 밖의 준비 단계로 분리해야 한다.

### 건별 실패 저장

조회 실패는 별도 짧은 트랜잭션으로 실패 item만 기록한다. 문서 저장 트랜잭션이 실패한 경우 해당 트랜잭션을 완전히 롤백한 뒤 새 트랜잭션에서 실패 item을 기록한다.

### Job 종료

마지막 짧은 트랜잭션에서 job item을 집계한다.

| 성공 수 | 실패 수 | 최종 상태 |
|---:|---:|---|
| 0 이상 | 0 | `SUCCEEDED` |
| 1 이상 | 1 이상 | `PARTIAL_FAILED` |
| 0 | 1 이상 | `FAILED` |

빈 page/database처럼 처리 대상이 없고 실패도 없으면 `SUCCEEDED`로 본다.

- `SUCCEEDED`: 다음 cursor를 반영하고 `last_synced_at`을 갱신한다.
- `PARTIAL_FAILED`: 실패 대상을 다음 수집에서 다시 시도할 수 있도록 cursor와 `last_synced_at`을 갱신하지 않는다.
- `FAILED`: cursor와 `last_synced_at`을 갱신하지 않는다.

`PARTIAL_FAILED`의 job `error_message`에는 `전체 N개 중 M개 실패` 요약을 저장한다. `FAILED`에는 루트 실패 또는 전체 항목 실패 요약을 저장하고, 구체적인 대상별 원인은 job item에서 조회한다.

최종 job 상태 저장 자체가 실패하면 기존 worker의 최상위 오류 처리로 남기며 성공으로 오인하지 않는다.

## Job item 데이터 모델

기존 `ingestion_job_items` 테이블을 그대로 사용하므로 DB migration은 추가하지 않는다.

`IngestionJobItemEntity`, item status enum과 repository를 추가해 현재 Liquibase 스키마에 정확히 매핑한다. 성공/실패 item 저장과 job별 상태 집계는 이 repository를 단일 경계로 사용한다.

- `job_id`: 현재 수집 job
- `external_id`: `page:<uuid>`, `database:<uuid>`, `data-source:<uuid>`, `block:<uuid>` 형식
- `document_id`: 성공한 문서는 저장된 external document ID, 실패 항목은 `NULL`
- `status`: `SUCCEEDED` 또는 `FAILED`
- `reason`: 실패 항목만 사용자용 문구 저장
- `processed_at`: 처리 완료 시각

`reason`은 다음처럼 한 문자열 안에 대상 종류, 경로, 단계와 원인을 포함한다.

```text
[DATABASE] 개인 / 프로젝트 / 작업목록 (QUERY): Notion API status=404, code=object_not_found; 원본 database를 integration에 공유하세요
```

별도 JSON column을 추가하지 않고 현재 요구사항을 충족한다. 구조화된 실패 분석이 필요해지면 후속 migration으로 details JSON을 추가한다.

## GraphQL과 관리자 UI

### GraphQL

- `IngestionJobStatus`에 `PARTIAL_FAILED`를 추가한다.
- job payload에 `succeededItemCount`, `failedItemCount`를 추가한다.
- 선택한 job의 item을 조회하는 관리자 전용 `ingestionJobItems(jobId, status, first, after)` query를 추가한다.
- item payload는 `externalId`, `documentId`, `status`, `reason`, `processedAt`을 제공한다.
- 실패 목록은 선택한 job에 대해서만 요청해 job 목록의 N+1 query와 대용량 응답을 피한다.
- query는 `items`, `hasNextPage`, `endCursor`를 반환한다. `first`는 기본 50, 최대 100이며 `after`는 `processed_at`과 ID를 담은 불투명 cursor다.

### 관리자 UI

- job 표에 `PARTIAL_FAILED` badge와 성공/실패 개수를 표시한다.
- job 행을 선택하면 실패 item만 조회한다.
- 실패 목록에 외부 ID, 경로·단계·이유, 처리 시각을 표시한다.
- 실패가 조회 제한보다 많으면 더 보기로 다음 묶음을 불러온다.
- `SUCCEEDED` job은 실패 상세를 표시하지 않는다.
- 기존 data source 생성·수정과 명시적 페이지/database 선택 UI는 유지한다.

## 오류 분류

| 실패 위치 | 기록 대상 | 계속 처리 |
|---|---|---|
| 루트 page 조회 실패 | root page | 처리할 형제가 없으므로 종료 |
| child page 조회/blocks 실패 | child page | 같은 부모의 다른 child 계속 |
| database 조회 실패 | database | 다른 page/database 계속 |
| data source 개수 오류 | database | 다른 page/database 계속 |
| query 시작 또는 중간 실패 | database | 이미 성공한 row 유지, 다른 database 계속 |
| row page 처리 실패 | row page | 다음 row 계속 |
| 문서 pipeline 저장 실패 | 해당 page | 해당 트랜잭션 롤백 후 다음 대상 계속 |
| job item 기록 등 DB 인프라 실패 | job | 안전하게 계속할 수 없으므로 worker 최상위 실패 |

Notion API가 권한이 없는 database를 404로 응답하는 경우에는 `object_not_found`를 권한·공유 안내로 변환한다. database 관련 unsupported block을 식별할 수 있으면 실패 item으로 기록하고, database와 무관한 일반 unsupported block은 현재처럼 본문에서 제외한다.

## ACL과 보안

- 자동 발견한 database row도 루트 data source의 기존 principal과 visibility를 그대로 상속한다.
- principal이 없거나 비어 있으면 기존 fail-closed 검증을 유지한다.
- 자동 발견이 다른 workspace 또는 다른 data source ACL을 만들지 않는다.
- Notion API가 integration에 공개한 범위만 탐색한다.
- job 실패 reason에는 credential, token, 원본 API body를 저장하지 않는다.

## 테스트와 검증

### Notion API client

- `child_database`와 database 관련 unsupported block 정보를 파싱한다.
- block children 페이지네이션을 묶음 단위로 전달한다.
- data source query를 묶음 단위로 전달하며 전체 목록을 누적하지 않는다.
- 중간 cursor 실패를 호출자에게 전달한다.

### Notion connector

- 루트 page, child page와 각각의 child database 행을 모두 배출한다.
- 하위 page 내부의 database도 수집한다.
- 같은 database와 page가 중복 노출돼도 한 번만 수집한다.
- child database 실패를 failure event로 배출하고 다른 형제 수집을 계속한다.
- query 중간 실패 전에 배출한 row는 유지한다.
- linked/unsupported database 실패 이유가 경로와 함께 기록된다.
- 기존 page 전용과 database 전용 수집 결과가 유지된다.

### Worker와 pipeline

- connector 실행 중 실제 DB 트랜잭션이 활성화되지 않았음을 검증한다.
- 성공 문서 각각이 독립 트랜잭션으로 저장된다.
- 두 번째 문서 저장 실패 시 첫 번째와 세 번째 문서는 남고 두 번째 변경은 롤백된다.
- 성공/실패 item과 document ID 연결을 검증한다.
- 전부 성공, 일부 실패, 전부 실패 상태를 각각 검증한다.
- `PARTIAL_FAILED`와 `FAILED`에서 cursor와 `last_synced_at`이 유지되는지 검증한다.
- 기존 claim 중복 방지와 job 실패 경로의 회귀를 검증한다.

### GraphQL과 관리자 UI

- `PARTIAL_FAILED` job, 성공/실패 개수와 실패 item을 조회한다.
- 다른 data source나 workspace의 job item을 조회할 수 없다.
- job 선택 전에는 item query를 실행하지 않는다.
- 실패 ID, 경로, 단계와 이유가 표시된다.
- 더 보기로 다음 실패 묶음을 불러온다.
- 민감한 오류 세부정보가 노출되지 않는다.

### 실제 화면

- Playwright로 page 루트 data source를 수집해 하위 database row가 문서로 저장되는 정상 케이스를 확인한다.
- 접근할 수 없는 child database가 포함된 fixture 또는 격리된 QA 경계로 `PARTIAL_FAILED`와 실패 상세 표시를 확인한다.
- 성공 문서가 검색 가능한 상태로 남고 실패 문서만 실패 목록에 나타나는지 확인한다.
- QA data source, job item과 문서를 정리한다.

## 문서 갱신

`docs/notion-integration-setup.md`에 다음 내용을 추가한다.

- 페이지 모드가 child page와 child database를 재귀 수집한다는 설명
- view 필터가 아니라 원본 data source 전체 행을 수집한다는 설명
- 원본 database를 integration에 공유하는 방법
- `PARTIAL_FAILED` 상태와 실패 상세 확인 방법
- linked database를 자동 해석할 수 없는 경우 명시적 database 모드를 사용하는 방법

## 호환성과 migration

- 기존 `notionRootPageId`와 `notionDatabaseId` config 구조는 유지한다.
- 명시적 database mode의 API 경로와 metadata는 유지한다.
- 기존 `ingestion_job_items` 테이블과 `PARTIAL_FAILED` Java enum을 사용하므로 DB migration은 없다.
- GraphQL schema와 생성된 관리자 UI 타입은 함께 갱신한다.
- 기존 성공/실패 job 조회는 새 count 필드와 item query를 사용하지 않아도 동작한다.
- 이전 수집에서 저장된 문서는 실패한 새 수집 항목 때문에 삭제하지 않는다.

## 완료 기준

- 페이지 링크 하나로 루트·하위 페이지와 그 안의 모든 조회 가능한 database가 수집된다.
- database view가 여러 위치에 있어도 ID당 한 번만 수집된다.
- 원격 Notion API 호출 동안 DB 트랜잭션이 열리지 않는다.
- 전체 문서 본문을 메모리에 누적하지 않는다.
- 한 항목 실패가 다른 성공 항목을 롤백하지 않는다.
- job 상태가 성공·부분 실패·전체 실패를 정확히 구분한다.
- 관리자 화면에서 실패한 페이지/database와 이유를 확인할 수 있다.
- ACL은 루트 data source 범위 안에서 fail-closed로 유지된다.
- 기존 페이지·database 직접 수집 동작이 회귀하지 않는다.
