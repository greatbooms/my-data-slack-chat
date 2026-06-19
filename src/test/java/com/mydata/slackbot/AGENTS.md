# 에이전트 안내

이 폴더는 Slack 이벤트 엔드포인트와 signature 검증 테스트를 둡니다.

## 확인 포인트

- Slack HMAC signature를 검증합니다.
- replay window와 malformed timestamp를 거부합니다.
- URL verification challenge를 plain text로 반환합니다.
- signed malformed JSON은 400으로 응답합니다.
