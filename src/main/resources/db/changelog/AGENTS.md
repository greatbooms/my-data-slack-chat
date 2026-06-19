# 에이전트 안내

이 폴더는 Liquibase formatted SQL changelog를 둡니다.

## 핵심 파일

- `db.changelog-master.sql`

## 주의사항

- changeset id는 기존 id와 충돌하지 않게 추가합니다.
- JPA 엔티티 변경과 DB 스키마 변경을 함께 맞춥니다.
- pgvector extension과 vector 차원 변경은 검색/임베딩 테스트까지 확인합니다.
