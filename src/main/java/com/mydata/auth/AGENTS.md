# 에이전트 안내

이 패키지는 권한 판단에 쓰는 principal key와 permission 값을 정의합니다.

## 핵심 개념

- `PrincipalKeys`: 사용자, 워크스페이스, Slack, Google 계열 principal key 생성
- `Permission`: 현재 문서 접근에는 `READ`가 핵심

## 주의사항

- principal key 문자열 형식은 ACL 저장과 검색에 직접 쓰이므로 변경 시 마이그레이션과 테스트가 필요합니다.
- Google 이메일 정규화는 locale 영향을 받지 않도록 `Locale.ROOT`를 사용합니다.
