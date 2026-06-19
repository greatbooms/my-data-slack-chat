# 에이전트 안내

이 폴더는 프로젝트 테스트 패키지의 루트입니다.

## 테스트 종류

- 단위 테스트: 순수 도메인/유틸리티/검증 로직
- MVC 테스트: MockMvc 기반 endpoint 검증
- 통합 테스트: Testcontainers PostgreSQL/pgvector 기반 검증

## 주의사항

- DB 스키마, retrieval, ingestion, chat 흐름은 통합 테스트로 검증하는 편이 안전합니다.
- 외부 API 호출은 현재 테스트에서 사용하지 않습니다.
