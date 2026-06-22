# 에이전트 안내

이 폴더는 프론트엔드 애플리케이션을 둡니다.

## 모듈 지도

- `admin`: Spring Boot와 같은 서버에서 제공되는 관리자 React 앱

## 주의사항

- 프론트엔드에서 실제 API 호출은 `/admin/graphql`과 `/admin/auth/**` 상대 경로를 사용합니다.
- 빌드 산출물은 Spring Boot 정적 리소스로 복사되어 `/admin-ui/**`에서 제공됩니다.
