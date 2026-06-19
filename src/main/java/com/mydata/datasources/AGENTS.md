# 에이전트 안내

이 패키지는 데이터소스 도메인을 담당합니다.

## 핵심 파일

- `DataSourceEntity`: 수집 대상 설정, 상태, cursor, config JSON 저장
- `DataSourceType`: 지원 데이터소스 타입
- `DataSourceStatus`, `SyncMode`: 상태와 sync 모드

## 주의사항

- 새 데이터소스 타입을 추가하면 커넥터, 테스트, 문서, 스키마 영향 범위를 함께 봅니다.
- `config_json`은 JSONB이며 JPA에서는 문자열로 보관하므로 ObjectMapper 기반 접근을 유지합니다.
