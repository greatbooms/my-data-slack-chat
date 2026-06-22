# 에이전트 안내

이 폴더는 관리자 화면의 API 호출 계층입니다.

## 주의사항

- GraphQL 요청은 `src/generated`의 typed document를 사용합니다.
- 세션 쿠키는 같은 origin 요청으로 전송합니다.
- mutation 요청에는 이후 CSRF header 연결이 필요합니다.
