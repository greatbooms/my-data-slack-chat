# Slack 앱 설정

이 문서는 로컬 개발에서 Slack 이벤트를 받기 위한 앱 설정 절차입니다. 초기 개발은 Socket Mode를 사용합니다. Socket Mode는 애플리케이션이 Slack으로 WebSocket 연결을 열어 이벤트를 받기 때문에 로컬 개발 중에 공개 HTTPS 터널을 열 필요가 없습니다.

현재 코드는 `SLACK_SOCKET_MODE_ENABLED=true`일 때 `app_mention`, `message.im` 이벤트를 내부 질문 이벤트로 변환합니다. `/slack/events` HTTP endpoint는 `SLACK_HTTP_EVENTS_ENABLED=true`일 때만 활성화됩니다.

공식 문서:

- [Using Socket Mode](https://docs.slack.dev/apis/events-api/using-socket-mode/)
- [App manifests](https://docs.slack.dev/app-manifests/configuring-apps-with-app-manifests/)
- [app_mention event](https://docs.slack.dev/reference/events/app_mention/)
- [Verifying requests from Slack](https://docs.slack.dev/authentication/verifying-requests-from-slack/)

## 1. Slack 앱 생성

[Slack 앱 관리 화면](https://api.slack.com/apps)에서 새 앱을 만들거나 기존 앱을 엽니다.
처음 만드는 경우 `Create New App`에서 `From an app manifest`를 선택하면 설정을 빠르게 맞출 수 있습니다.

초기 개발용 manifest 예시는 다음과 같습니다.

```json
{
  "display_information": {
    "name": "My Data",
    "description": "개인 데이터 RAG 챗봇",
    "background_color": "#2563eb"
  },
  "features": {
    "bot_user": {
      "display_name": "My Data",
      "always_online": false
    }
  },
  "oauth_config": {
    "scopes": {
      "bot": [
        "app_mentions:read",
        "chat:write",
        "im:history"
      ]
    }
  },
  "settings": {
    "event_subscriptions": {
      "bot_events": [
        "app_mention",
        "message.im"
      ]
    },
    "org_deploy_enabled": false,
    "socket_mode_enabled": true,
    "token_rotation_enabled": false
  }
}
```

manifest를 사용하지 않고 화면에서 직접 설정해도 됩니다. 직접 설정하는 경우 아래 단계를 그대로 따라갑니다.

## 2. Socket Mode App Token 발급

1. Slack 앱 설정에서 `Basic Information`으로 이동합니다.
2. `App-Level Tokens` 영역에서 `Generate Token and Scopes`를 누릅니다.
3. 토큰 이름은 예를 들어 `socket-mode`로 입력합니다.
4. scope는 `connections:write`를 추가합니다.
5. 생성된 App-Level Token을 복사합니다.

`.env`에는 다음처럼 넣습니다.

```bash
SLACK_SOCKET_MODE_ENABLED=true
SLACK_APP_TOKEN=복사한_App_Level_Token
```

App-Level Token은 보통 `xapp-`로 시작합니다.

## 3. Bot User OAuth Token 준비

1. Slack 앱 설정에서 `OAuth & Permissions`로 이동합니다.
2. `Bot Token Scopes`에 다음 scope를 추가합니다.
3. scope를 바꾼 뒤 `Install to Workspace` 또는 `Reinstall to Workspace`를 실행합니다.
4. 설치가 끝나면 같은 화면의 `Bot User OAuth Token` 값을 복사합니다.

필요한 Bot Token Scope:

- `app_mentions:read`: 채널에서 앱을 멘션한 질문 이벤트를 받습니다.
- `chat:write`: Slack 채널이나 스레드에 답변 메시지를 보냅니다.
- `im:history`: DM 질문 이벤트까지 받을 때 추가합니다.

`.env`에는 다음처럼 넣습니다.

```bash
SLACK_BOT_TOKEN=복사한_Bot_User_OAuth_Token
```

Bot User OAuth Token은 보통 `xoxb-`로 시작합니다.

Socket Mode로 Slack 이벤트를 받으려면 `.env`에 최소 다음 값이 있어야 합니다.

```bash
SLACK_SOCKET_MODE_ENABLED=true
SLACK_APP_TOKEN=xapp-...
SLACK_BOT_TOKEN=xoxb-...
```

## 4. Socket Mode와 이벤트 구독 확인

Slack 앱 설정에서 `Socket Mode`가 켜져 있는지 확인합니다. manifest를 사용했다면 이미 켜져 있습니다.
직접 설정한다면 `Socket Mode` 메뉴에서 `Enable Socket Mode`를 켭니다.

Slack 앱 설정에서 `Event Subscriptions`를 켜고, `Subscribe to bot events`에 다음 이벤트를 추가합니다.

- `app_mention`: 채널에서 앱을 멘션한 질문을 받을 때 사용합니다.
- `message.im`: 앱 DM으로 질문을 받을 때 사용합니다.

Socket Mode에서는 `Request URL`을 입력하지 않습니다. 앱이 Slack으로 연결을 열기 때문에 Slack이 로컬 서버로 직접 HTTP 요청을 보낼 필요가 없습니다.

## 5. 채널 초대

채널에서 앱을 멘션하려면 앱이 해당 채널에 들어와 있어야 합니다.
Slack 채널에서 다음처럼 초대합니다.

```text
/invite @My Data
```

DM으로 질문하려면 Slack 앱의 `App Home` 또는 DM 화면에서 앱에게 메시지를 보냅니다.

## HTTP Events API로 전환할 때

운영 규모가 커지고 공개 HTTPS endpoint를 안정적으로 운영하게 되면 HTTP Events API로 전환할 수 있습니다.
그때는 `Socket Mode`를 끄고 `Event Subscriptions`의 `Request URL`을 다음 형식으로 설정합니다.

```text
https://내_도메인/slack/events
```

HTTP Events API 방식에서는 `SLACK_SIGNING_SECRET`으로 Slack 요청 서명을 검증합니다.
이 방식을 켤 때는 다음 값을 설정합니다.

```bash
SLACK_SOCKET_MODE_ENABLED=false
SLACK_HTTP_EVENTS_ENABLED=true
SLACK_SIGNING_SECRET=Slack_앱_Signing_Secret
```
