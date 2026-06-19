# 에이전트 안내

이 패키지는 외부 데이터소스 수집 커넥터를 담당합니다.

## 구조

- `core`: 모든 커넥터가 따라야 하는 계약
- `local`: 로컬 테스트용 `LOCAL_TEXT` 커넥터

## 주의사항

- 새 커넥터는 `DataSourceConnector` 계약을 구현합니다.
- 커넥터는 원본 문서, content hash, ACL, raw content를 pipeline이 이해하는 형태로 넘깁니다.
- 실제 외부 API credential은 코드나 git에 저장하지 않습니다.
