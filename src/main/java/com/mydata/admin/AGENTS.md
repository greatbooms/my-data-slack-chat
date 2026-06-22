# 에이전트 안내

이 패키지는 관리자 API와 관리자 세션 인증을 담당합니다.

## 핵심 파일

- `AdminDataSourceController`: 수동 데이터소스 sync 요청 API
- `auth/AdminSecurityConfiguration`: `/admin/**` 세션 인증과 관리자 권한 보호
- `auth/AdminAuthController`: 로그인, 로그아웃, CSRF 토큰 API
- `auth/AdminBootstrapInitializer`: 초기 관리자 계정 생성

## 주의사항

- 인증 실패는 요청 body 파싱 전에 처리되어야 합니다.
- 관리자 로그인은 DB 사용자 `ADMIN` 역할, 활성 상태, 삭제되지 않은 계정만 허용합니다.
- 새 관리자 API를 추가하면 `/admin/**` 보호 범위 안에 들어가는지 확인합니다.
