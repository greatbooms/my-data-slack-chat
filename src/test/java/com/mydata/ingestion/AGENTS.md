# 에이전트 안내

이 폴더는 수집 pipeline 통합 테스트를 둡니다.

## 확인 포인트

- `LOCAL_TEXT` 데이터소스가 문서/ACL/청크/임베딩으로 저장됩니다.
- unsupported ACL permission이나 blank principal은 문서 쓰기 전에 실패합니다.
- 변경 없는 문서는 중복 처리하지 않고 필요한 임베딩만 보완합니다.
