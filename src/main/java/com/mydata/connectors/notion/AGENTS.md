# 에이전트 안내

이 패키지는 Notion API를 호출해 루트 페이지와 하위 페이지의 텍스트 블록을 수집하는 커넥터를 담당합니다.

## 핵심 파일

- `NotionApiClient`: Notion HTTP API 호출, 페이지 메타데이터 조회, 블록 children pagination 처리
- `NotionPageConnector`: 데이터소스 설정의 루트 페이지 ID를 기준으로 페이지 트리를 순회하고 raw document/ACL을 생성
- `NotionConfiguration`, `NotionProperties`: Notion 토큰, API 버전, base URL, timeout 설정 바인딩

## 주의사항

- 실제 Notion token은 코드, 테스트 fixture, 문서에 넣지 않습니다.
- 현재 커넥터는 Notion page root와 `child_page` 순회를 지원합니다. database/data source root 지원은 별도 기능으로 다룹니다.
- 수집 문서는 content hash, 원문 텍스트, Notion URL, `notionPageId`, `notionRootPageId` metadata를 유지해야 합니다.
- ACL은 데이터소스 visibility에 따라 `USER:<ownerUserId>` 또는 `WORKSPACE:<workspaceId>` principal로 생성합니다.
- Notion API 오류 메시지에 token이 섞여 나오지 않게 예외 메시지는 sanitizing된 형태를 유지합니다.
- 외부 API 호출은 timeout을 유지하고, 테스트에서는 fake client나 local HTTP server를 사용합니다.
