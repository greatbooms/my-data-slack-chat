# 에이전트 안내

이 폴더는 Liquibase formatted SQL changelog를 둡니다.

## 핵심 파일

- `db.changelog-master.json`은 include 목록만 둡니다.
- 실제 changeset SQL은 `changes/NNN-description.sql` 파일로 분리합니다.

## 주의사항

- changeset id는 기존 id와 충돌하지 않게 추가합니다.
- 새 DB 변경은 master 파일에 직접 쓰지 말고 `changes/` 아래 새 파일로 추가한 뒤 master에서 include합니다.
- JPA 엔티티 변경과 DB 스키마 변경을 함께 맞춥니다.
- pgvector extension과 vector 차원 변경은 검색/임베딩 테스트까지 확인합니다.
