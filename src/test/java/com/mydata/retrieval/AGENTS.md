# 에이전트 안내

이 폴더는 pgvector 검색과 ACL 필터 테스트를 둡니다.

## 확인 포인트

- 권한이 있는 principal만 문서를 검색할 수 있습니다.
- 권한이 없거나 blank principal이면 결과가 없어야 합니다.
- 검색 결과에는 title, uri, source type, distance가 포함됩니다.
