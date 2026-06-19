# 에이전트 안내

이 패키지는 관리자 API와 관리자 토큰 인증을 담당합니다.

## 핵심 파일

- `AdminDataSourceController`: 수동 데이터소스 sync 요청 API
- `AdminTokenAuthenticationInterceptor`: `X-Admin-Token` 검증
- `AdminWebMvcConfiguration`: `/admin/**` 경로 보호

## 주의사항

- 인증 실패는 요청 body 파싱 전에 처리되어야 합니다.
- 관리자 토큰은 비어 있으면 안 되고, 비교는 constant-time 방식을 유지합니다.
- 새 관리자 API를 추가하면 `/admin/**` 보호 범위 안에 들어가는지 확인합니다.
