# 에이전트 안내

이 폴더는 애플리케이션 설정과 DB changelog를 담습니다.

## 핵심 파일

- `application.yml`: 공통 설정
- `application-local.yml`: 로컬 프로필 설정
- `db/changelog/db.changelog-master.json`: Liquibase include master changelog
- `db/changelog/changes/`: 실제 Liquibase formatted SQL changeset 파일

## 주의사항

- 기본 서버 포트는 `50506`입니다.
- 로컬 DB 포트는 `5433`입니다.
- Slack은 기본 비활성화이며 `SLACK_SOCKET_MODE_ENABLED` 또는 `SLACK_HTTP_EVENTS_ENABLED`로 수신 방식을 명시적으로 켭니다.
- 환경변수 변경 시 `.env.example`과 README 환경변수 표도 확인합니다.
