# 에이전트 안내

이 패키지는 애플리케이션 보안 설정의 fail-fast 검증을 담당합니다.

## 핵심 파일

- `SecuritySecretValidator`: admin token과 Slack signing secret 검증

## 주의사항

- `local` 또는 `test` 프로필 밖에서는 로컬 기본 보안값을 허용하지 않습니다.
- blank secret은 어떤 프로필에서도 허용하지 않습니다.
- 새 보안 secret을 추가하면 `.env.example`과 README 환경변수 표도 갱신합니다.
