# 에이전트 안내

이 패키지는 관리자 GraphQL에서 외부 계정 매핑을 조회, 생성, 수정, 삭제하는 응용 서비스를 둡니다.

## 주의사항

- 현재 지원 provider는 `SLACK`입니다.
- 매핑 생성/수정 시 삭제되지 않은 내부 유저와 워크스페이스만 허용합니다.
- 동일한 provider, external workspace id, external user id 조합은 하나만 허용합니다.
- Slack 답변 기능은 이 매핑을 통해 내부 `userId`, `workspaceId`, `principalKey`를 찾게 됩니다.
