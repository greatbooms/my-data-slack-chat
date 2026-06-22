# 에이전트 안내

이 패키지는 관리자 React 앱 정적 서빙 설정을 둡니다.

## 핵심 파일

- `AdminUiWebConfiguration`: `/admin-ui/**` 정적 리소스와 SPA fallback을 설정합니다.

## 주의사항

- `/admin/graphql`과 `/admin/auth/**`는 API 엔드포인트이므로 정적 fallback 대상이 아닙니다.
- `/admin-ui/assets/**`처럼 실제 정적 asset은 존재할 때만 서빙하고, 없는 asset을 `index.html`로 돌리지 않습니다.
