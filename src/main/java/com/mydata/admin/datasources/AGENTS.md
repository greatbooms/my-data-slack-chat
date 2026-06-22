# 에이전트 안내

이 패키지는 관리자 GraphQL에서 사용하는 데이터소스 관리 로직을 둡니다.

## 핵심 파일

- `AdminDataSourceService`: 데이터소스 조회, 생성, 수정, 소프트 삭제, 수동 수집 요청을 처리합니다.
- `AdminDataSourceInputs`: GraphQL mutation 입력 record를 정의합니다.
- `AdminDataSourcePayload`, `AdminDataSourcePagePayload`: GraphQL 응답 모델입니다.
- `AdminIngestionJobPayload`: 관리자 화면의 수집 job 응답 모델입니다.

## 주의사항

- 모든 서비스 메서드는 관리자 권한을 요구해야 합니다.
- 데이터소스 생성/수정 시 `data_source_access_policies`를 함께 갱신합니다.
- 소프트 삭제된 데이터소스는 목록과 단건 조회에서 제외하고 ACL 정책도 제거합니다.
- JPA 저장 직후 native SQL이나 `JdbcTemplate`으로 관련 테이블을 쓰기 전에는 flush 필요 여부를 확인합니다.
