# 에이전트 안내

이 패키지는 질문에 대한 권한 필터링 벡터 검색을 담당합니다.

## 핵심 파일

- `RetrievalService`: principal 정리와 검색 진입점
- `PgVectorSearchRepository`: pgvector native SQL 검색
- `RetrievedChunk`: 검색 결과 DTO

## 주의사항

- principal 목록이 없거나 blank뿐이면 빈 결과를 반환합니다.
- SQL은 workspace, deleted 문서 제외, `READ` 권한, principal 일치를 모두 필터링해야 합니다.
- pgvector 연산자는 JPA 파생 쿼리보다 native SQL로 다룹니다.
