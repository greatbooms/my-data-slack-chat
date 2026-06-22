# 에이전트 안내

이 폴더는 관리자 UI 정적 서빙과 SPA fallback 테스트를 둡니다.

## 확인 포인트

- `/admin-ui/login`은 세션 없이 React 앱 shell을 받을 수 있어야 합니다.
- `/admin-ui`와 nested route는 관리자 세션이 있어야 React 앱 shell을 받습니다.
- 실제 asset 요청은 파일이 존재할 때 asset으로 응답해야 합니다.
