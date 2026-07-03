# Slack Connector 안내

이 패키지는 Slack Web API 기반 데이터 수집 커넥터를 담당합니다.

## 규칙

- Slack API 토큰은 `SlackBotProperties.botToken()`만 사용하고 로그나 예외 메시지에 노출하지 않습니다.
- 데이터소스 설정 키는 `SlackChannelConnector.CHANNEL_ID_CONFIG_KEY`를 기준으로 맞춥니다.
- MVP 수집 범위는 데이터소스에 지정된 단일 channel ID입니다.
- ACL은 데이터소스 visibility를 따릅니다. 채널 멤버십 기반 ACL은 후속 확장입니다.
- Slack API 오류는 `SlackApiException`으로 감싸 ingestion job 실패 메시지에 남깁니다.
