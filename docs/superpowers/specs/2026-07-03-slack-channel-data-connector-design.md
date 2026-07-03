# Slack Channel Data Connector Design

## 목표

Slack 채널 하나를 데이터소스로 등록하고, 관리자가 수동 수집을 실행하면 해당 채널의 일반 메시지와 스레드 답글을 문서로 적재한다. 적재된 Slack 문서는 기존 RAG 검색, ACL, 답변 출처 흐름을 그대로 사용한다.

## 범위

- 관리자 데이터소스 폼에서 `SLACK` 타입과 `slackChannelId`를 입력할 수 있다.
- `SLACK` 데이터소스 생성/수정 시 `slackChannelId`를 설정값으로 저장한다.
- Slack bot token으로 `conversations.history`, `conversations.replies`, `chat.getPermalink`를 호출한다.
- public 채널은 `channels:history`, private 채널은 `groups:history` scope가 필요하다.
- MVP ACL은 데이터소스 visibility를 따른다. `PRIVATE`는 owner user, `WORKSPACE`는 workspace principal에 `READ`를 부여한다.
- 각 Slack 메시지 또는 스레드 답글은 개별 외부 문서로 저장한다.

## 비범위

- Slack 워크스페이스 전체 채널 자동 탐색
- Slack 채널 멤버십 기반 per-channel ACL
- scheduled incremental sync cursor
- 파일 첨부, canvas, reaction, edit/delete 이벤트 반영

## 데이터 모델

기존 `data_sources.config_json`에 다음 provider 설정을 저장한다.

- `slackChannelId`: 수집 대상 Slack channel ID

Slack 문서 metadata는 다음 값을 포함한다.

- `slackChannelId`
- `slackUserId`
- `slackMessageTs`
- `slackThreadTs`
- `slackIsThreadReply`
- `slackPermalink`

## 수집 흐름

1. `SlackChannelConnector`가 `slackChannelId`를 읽는다.
2. `SlackClient.listChannelMessages(channelId)`로 채널 history를 조회한다.
3. 각 메시지를 `RawExternalDocument`로 emit한다.
4. 메시지에 thread replies가 있으면 `SlackClient.listThreadReplies(channelId, threadTs)`를 조회한다.
5. root 메시지는 중복 emit하지 않고, 답글만 별도 문서로 emit한다.
6. Slack API error는 ingestion job 실패로 기록한다.

## 문서 구성

문서 제목은 `Slack <channelId> <ts>` 형식을 사용한다. 본문은 사용자 ID, 시간, 스레드 여부, 메시지 텍스트를 포함한다. permalink가 있으면 문서 URI로 사용한다.

## 테스트

- GraphQL: `SLACK` 데이터소스 생성 시 `slackChannelId`가 저장되고 payload로 반환된다.
- GraphQL 실패: `SLACK` 데이터소스는 빈 `slackChannelId`로 생성할 수 없다.
- Connector: history 메시지와 thread reply가 문서로 emit되고 ACL/metadata/content가 맞다.
- Connector 실패: Slack token 누락 또는 Slack API error가 예외로 드러난다.
- Integration: 수동 수집 job이 Slack 문서를 documents/chunks/ACL까지 저장한다.
- UI: 관리자 데이터소스 폼에서 `SLACK` 선택 시 Slack 채널 ID 필드가 보이고 required로 동작한다.
