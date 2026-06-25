# 에이전트 안내

이 폴더는 Slack Socket Mode 수신 기반, 선택형 HTTP Events API 엔드포인트, signature 검증 테스트를 둡니다.

## 확인 포인트

- Socket Mode lifecycle은 비활성화 상태와 토큰 누락 상태에서 외부 연결을 열지 않아야 합니다.
- Slack 이벤트는 내부 `SlackQuestionEvent`로 변환되어야 하고, 빈 질문은 무시해야 합니다.
- Slack HMAC signature를 검증합니다.
- replay window와 malformed timestamp를 거부합니다.
- URL verification challenge를 plain text로 반환합니다.
- signed malformed JSON은 400으로 응답합니다.
