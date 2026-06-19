# 에이전트 안내

이 폴더는 커넥터 공통 계약과 raw 데이터 record를 정의합니다.

## 핵심 타입

- `DataSourceConnector`: 커넥터 인터페이스
- `RawExternalDocument`: 수집된 외부 문서 단위
- `RawAclEntry`: 문서별 ACL
- `RawContent`: 원문 content
- `SyncCursor`: 증분 수집 cursor

## 주의사항

- record 필드 변경은 모든 커넥터와 ingestion pipeline에 영향을 줍니다.
- ACL principal이 비어 있거나 지원하지 않는 permission이면 pipeline이 실패해야 합니다.
