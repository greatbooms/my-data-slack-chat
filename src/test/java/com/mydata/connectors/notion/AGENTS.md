# 에이전트 안내

이 폴더는 Notion API client와 Notion page connector의 단위/경계 테스트를 둡니다.

## 확인 포인트

- 실제 Notion API나 실제 token을 사용하지 않습니다.
- HTTP client 테스트는 local `HttpServer`로 요청 path, header, pagination, timeout, 오류 메시지를 검증합니다.
- connector 테스트는 fake `NotionClient`로 루트 페이지, 하위 페이지, ACL, content hash, metadata 생성을 검증합니다.
- 오류 메시지에는 token 같은 secret이 포함되지 않아야 합니다.
- database/data source root 지원을 추가할 때는 page root 동작을 깨지 않는 회귀 테스트를 함께 둡니다.
