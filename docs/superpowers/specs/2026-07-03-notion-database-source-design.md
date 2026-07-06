# Notion Database Source Design

## 목표

관리자 화면에서 Notion 데이터베이스 링크 또는 database ID를 데이터소스로 등록하고, 수동 수집 시 해당 데이터베이스의 row page들을 RAG 검색 대상으로 저장한다.

## 범위

- 기존 `NOTION` 데이터소스 타입을 유지한다.
- `notionRootPageId`와 별도로 `notionDatabaseId` 설정을 추가한다.
- 생성/수정 시 둘 중 하나만 입력할 수 있게 검증한다.
- 사용자는 Notion에서 복사한 데이터베이스 링크 또는 database ID를 입력할 수 있다.
- 서버는 database ID를 정규화한 뒤 `GET /v1/databases/{database_id}`로 data source ID를 찾고, `POST /v1/data_sources/{data_source_id}/query`로 row page 목록을 조회한다.
- 각 row page는 기존 페이지 수집 방식과 동일하게 page metadata, 본문 block children, ACL, content hash를 저장한다.

## 제외

- Notion data source ID 직접 입력 UI
- 여러 data source를 가진 데이터베이스의 선택 UI
- Notion webhook 기반 증분 수집
- 10,000건 초과 데이터베이스의 created_time windowing full export

## 동작

데이터베이스 모드에서 커넥터는 database ID를 data source ID로 해석한다. database에 data source가 정확히 하나 있으면 그 ID로 query한다. data source가 없거나 여러 개면 사용자가 조치할 수 있는 메시지로 수집을 실패시킨다.

query 결과의 page들은 각각 하나의 `RawExternalDocument`가 된다. 문서 제목은 page title을 우선 사용하고, property 요약과 본문 block text를 함께 content에 포함한다. metadata에는 `notionDatabaseId`, `notionDataSourceId`, `notionPageId`, parent 관계, Notion URL, created/edited user, trash 상태를 기록한다.

ACL은 기존 Notion page source와 동일하게 데이터소스 visibility를 따른다.

## 에러 처리

- Notion token 누락, API 401/403/404/429/5xx는 기존 `NotionApiException` 흐름을 사용한다.
- 입력값에서 database ID를 추출할 수 없으면 관리자 mutation이 실패한다.
- database query 응답에 `request_status.type = incomplete`가 오면 현재 MVP에서는 수집을 실패시킨다. 조용히 일부만 저장하지 않는다.

## 테스트

- `NotionApiClientTest`: retrieve database, query data source pagination, incomplete response failure를 검증한다.
- `NotionPageConnectorTest`: database mode가 row page와 본문을 수집하고 metadata/ACL을 유지하는지 검증한다.
- `AdminDataSourceGraphQlTest`: Notion page/database 설정의 생성/수정 검증과 상호 배타 검증을 추가한다.
- `frontend/admin/src/App.test.tsx`: Notion 데이터베이스 링크 입력 UI와 mutation payload를 검증한다.
- UI 변경 후 Playwright로 관리자 데이터소스 생성 화면의 정상/실패 케이스를 실제 화면에서 확인한다.
