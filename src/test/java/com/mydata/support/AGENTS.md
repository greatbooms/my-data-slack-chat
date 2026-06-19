# 에이전트 안내

이 폴더는 테스트 공통 기반을 둡니다.

## 핵심 파일

- `PostgresIntegrationTest`: Testcontainers PostgreSQL/pgvector 컨테이너와 Spring test profile 설정

## 주의사항

- 통합 테스트 DB 설정을 바꾸면 모든 DB 기반 테스트가 영향을 받습니다.
- 테스트 profile의 secret 기본값은 운영 설정과 분리되어야 합니다.
