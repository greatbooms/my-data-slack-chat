# 에이전트 안내

이 패키지는 Slack 이벤트 수신 기반을 담당합니다.

## 핵심 파일

- `SlackEventController`: `/slack/events` 엔드포인트
- `SlackSignatureVerifier`: Slack request signature 검증

## 주의사항

- Slack signature 검증은 JSON 파싱보다 먼저 raw body로 수행합니다.
- timestamp replay window는 5분입니다.
- `test-bypass` 서명은 `test` 프로필에서만 허용합니다.
- URL verification은 challenge를 plain text로 반환해야 합니다.
