# 에이전트 안내

이 폴더는 관리자 데이터소스 GraphQL 테스트를 둡니다.

## 확인 포인트

- 관리자 세션과 CSRF 토큰이 있는 요청만 성공해야 합니다.
- 데이터소스 생성/수정/삭제는 GraphQL mutation으로 검증합니다.
- 가시성 변경은 `data_source_access_policies` principal 갱신까지 확인합니다.
- 수동 수집 요청은 `PENDING` ingestion job 생성까지 확인합니다.
