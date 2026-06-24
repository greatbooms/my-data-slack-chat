# 에이전트 안내

이 패키지는 Slack Socket Mode 수신 기반과 선택형 HTTP Events API 엔드포인트를 담당합니다.

## 핵심 파일

- `SlackSocketModeLifecycle`: Spring 애플리케이션 시작/종료에 맞춰 Socket Mode 클라이언트를 관리합니다.
- `BoltSlackSocketModeClientFactory`: Slack Bolt SDK와 Jakarta Socket Mode 클라이언트를 생성합니다.
- `SlackSocketModeEventHandler`: Slack 이벤트를 내부 `SlackQuestionEvent`로 변환합니다.
- `SlackEventController`: `my-data.slack.http-events-enabled=true`일 때만 활성화되는 `/slack/events` 엔드포인트
- `SlackSignatureVerifier`: Slack request signature 검증

## 주의사항

- 초기 개발은 `my-data.slack.socket-mode-enabled=true` 기반 Socket Mode를 사용합니다.
- Slack signature 검증은 JSON 파싱보다 먼저 raw body로 수행합니다.
- timestamp replay window는 5분입니다.
- `test-bypass` 서명은 `test` 프로필에서만 허용합니다.
- URL verification은 challenge를 plain text로 반환해야 합니다.
- Slack 유저를 내부 유저/권한으로 매핑하기 전까지 Socket Mode 이벤트는 실제 RAG 답변 전송 대신 내부 질문 이벤트로만 변환합니다.
