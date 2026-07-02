# 에이전트 안내

이 패키지는 Slack Socket Mode 수신 기반과 선택형 HTTP Events API 엔드포인트를 담당합니다.

## 핵심 파일

- `SlackSocketModeLifecycle`: Spring 애플리케이션 시작/종료에 맞춰 Socket Mode 클라이언트를 관리합니다.
- `BoltSlackSocketModeClientFactory`: Slack Bolt SDK와 Jakarta Socket Mode 클라이언트를 생성합니다.
- `SlackSocketModeEventHandler`: Slack 이벤트를 내부 `SlackQuestionEvent`로 변환하고 비동기 처리 작업으로 예약합니다.
- `SlackAnswerQuestionEventConsumer`: Slack 외부 계정 매핑을 내부 유저/워크스페이스로 변환하고 채팅 답변을 Slack 스레드에 전송합니다.
- `SlackWebApiMessageClient`: Slack Web API `chat.postMessage` 전송 어댑터입니다.
- `SlackEventController`: `my-data.slack.http-events-enabled=true`일 때만 활성화되는 `/slack/events` 엔드포인트
- `SlackSignatureVerifier`: Slack request signature 검증

## 주의사항

- 초기 개발은 `my-data.slack.socket-mode-enabled=true` 기반 Socket Mode를 사용합니다.
- Slack signature 검증은 JSON 파싱보다 먼저 raw body로 수행합니다.
- timestamp replay window는 5분입니다.
- `test-bypass` 서명은 `test` 프로필에서만 허용합니다.
- URL verification은 challenge를 plain text로 반환해야 합니다.
- Slack 질문은 `SLACK_USER:<teamId>:<userId>`, 내부 `USER:<userId>`, `WORKSPACE:<workspaceId>` principal을 함께 사용해 fail-closed 검색을 수행합니다.
- Slack Web API 실패는 Socket Mode 수신 루프를 죽이지 않도록 로깅하고 가능한 경우 스레드에 안내 메시지를 보냅니다.
- Socket Mode listener는 Slack ack가 지연되지 않도록 질문 처리 완료를 기다리지 않아야 합니다.
