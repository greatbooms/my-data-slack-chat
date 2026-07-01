# 에이전트 안내

이 패키지는 외부 서비스 계정과 내부 유저/워크스페이스를 연결하는 identity 매핑 도메인을 둡니다.

## 주의사항

- `ExternalIdentityEntity`는 기존 `external_identities` 테이블과 일치해야 합니다.
- Slack 매핑 principal은 `PrincipalKeys.slackUser(teamId, userId)` 형식을 사용합니다.
- 이 매핑은 Slack 질문자를 내부 권한 모델에 연결하기 위한 기반이며, 검색 권한 자체는 `USER:*`, `WORKSPACE:*` ACL에서 최종 판단합니다.
