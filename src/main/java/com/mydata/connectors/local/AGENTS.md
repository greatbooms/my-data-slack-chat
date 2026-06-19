# 에이전트 안내

이 폴더는 로컬 테스트와 기반 검증용 `LOCAL_TEXT` 커넥터를 둡니다.

## 역할

- `DataSourceEntity.config_json`의 값을 읽어 단일 raw 문서를 생성합니다.
- content hash는 SHA-256으로 계산합니다.

## 주의사항

- 이 커넥터는 실제 제품 커넥터가 아니라 ingestion/retrieval/chat 경로 검증용입니다.
- 필수 config key는 `externalId`, `title`, `content`, `principalKey`입니다.
