# Google Drive 커넥터 설계

## 요약

`GOOGLE_DRIVE` 타입 데이터소스의 실제 수집 커넥터를 구현한다. 지정한 Drive 폴더를 재귀 순회하며
Google Docs/Sheets, 텍스트 계열 파일, PDF에서 텍스트를 추출해 기존 수집 파이프라인으로 전달한다.
enum, GraphQL 스키마, 수집 파이프라인은 이미 준비되어 있으므로 이번 작업은 커넥터 모듈과 어드민 UI
노출에 한정한다.

## 목표

- 지정 폴더(하위 폴더 포함)의 지원 파일을 external document로 수집한다.
- Google Docs/Sheets export, `text/*`·markdown·JSON 다운로드, PDF 텍스트 추출을 지원한다.
- 삭제·변경 정합은 기존 `FULL_SNAPSHOT` reconciliation 경로를 그대로 사용한다.
- 어드민 UI에서 `GOOGLE_DRIVE` 데이터소스를 생성할 수 있게 한다.

## 비목표

- Drive 공유 권한의 principal 매핑 (멀티유저 전환 시점에 수행, 로드맵 항목 3).
- per-user OAuth 연결 흐름 (같은 시점).
- 이미지 OCR, 스프레드시트 고급 추출, Slides, 증분(Changes API) 동기화.

## 인증

Notion과 동일한 앱 전역 env 자격증명 패턴을 따른다. 신규 의존성 없음.

- `my-data.google-drive.*` `@ConfigurationProperties`: `client-id`, `client-secret`, `refresh-token`,
  `token-url`(기본 `https://oauth2.googleapis.com/token`), `api-base-url`(기본
  `https://www.googleapis.com`), connect/request timeout.
- `GoogleOAuthTokenProvider`: refresh token으로 access token을 발급받고 만료 전까지 메모리에 캐시한다.
  갱신 실패는 커넥터 단계 실패로 전파한다.
- `.env.example`에 `GOOGLE_DRIVE_CLIENT_ID`, `GOOGLE_DRIVE_CLIENT_SECRET`,
  `GOOGLE_DRIVE_REFRESH_TOKEN` 예시를 추가한다.
- 최초 리프레시 토큰 발급(1회 브라우저 동의)은 README에 절차를 기록한다.

멀티유저 전환 시 in-app OAuth + DB 암호화 저장으로 이전한다는 결정은 로드맵 메모에 정리되어 있다.

## 커넥터 구조

`src/main/java/com/mydata/connectors/googledrive` 패키지, Notion 커넥터 패턴을 따른다.

- `GoogleDriveConnector implements DataSourceConnector`
  - `supports()` = `GOOGLE_DRIVE`
  - `reconciliationMode()` = `FULL_SNAPSHOT` — 삭제 정합과 미변경 SKIPPED 처리는 기존 파이프라인이 담당.
  - `fetchChanges`: config `driveFolderId`(필수)를 읽고 폴더를 BFS/DFS 재귀 순회. visited set으로
    중복 방문을 막는다. 커서는 사용하지 않고 `dataSource.cursor()`를 그대로 반환한다.
- `GoogleDriveClient` 인터페이스 + `GoogleDriveApiClient` 구현 (`java.net.http.HttpClient` + Jackson):
  - `files.list`: `q='<folderId>' in parents and trashed=false`, `pageToken` 페이징,
    `fields`로 id/name/mimeType/size/createdTime/modifiedTime/webViewLink/md5Checksum만 요청.
  - `files.export`: Google Docs → `text/plain`, Sheets → `text/csv`.
  - `files/{id}?alt=media`: 일반 파일 다운로드.
  - 오류는 `GoogleDriveApiException`(status code, 메시지)으로 던진다.

## 파일 타입 처리

| mimeType | 처리 |
|---|---|
| `application/vnd.google-apps.document` | export `text/plain` |
| `application/vnd.google-apps.spreadsheet` | export `text/csv` |
| `text/*`, `application/json`, markdown 계열 | `alt=media` 다운로드 (UTF-8 해석) |
| `application/pdf` | `alt=media` + Apache PDFBox 텍스트 추출 (유일한 신규 의존성) |
| `application/vnd.google-apps.folder` | 재귀 진입 |
| 그 외 (이미지·영상·바로가기 등) | 조용히 무시 |

- 지원 외 타입을 `onFailure`로 올리면 매 동기화가 영구 FAILED가 되고 `failed > 0`이면 삭제
  reconciliation이 보류되므로, 실패가 아니라 무시로 처리한다. 무시 건수는 로그로만 남긴다.
- API 오류(목록/다운로드/export 실패)는 Notion처럼 `ConnectorFailureEvent`로 방출한다.
- 크기 가드: `size`가 20MB를 초과하는 파일은 다운로드하지 않고 무시한다(export API 자체 한도 10MB).
- PDF에서 추출한 텍스트가 비어 있으면(스캔본 등) 문서를 방출하지 않고 무시한다.

## 문서 표현

- `externalId` = Drive file id, `title` = 파일명, `uri` = `webViewLink`, `mimeType` = 원본 mimeType.
- `contentHash` = 추출 텍스트의 SHA-256 (Notion과 동일 — export 파일에는 md5가 없으므로 통일).
- 메타데이터: `driveFileId`, `drivePath`(루트 폴더부터의 경로 리스트), `driveMimeType`,
  `driveWebViewLink`, `driveMd5Checksum`(있을 때), `driveSize`.
- ACL: `visibilityPrincipalKey()`에 READ 1건 (`sourceSystem` = `GOOGLE_DRIVE`). 기존 커넥터와 동일.

## 어드민 UI

- 데이터소스 생성 폼의 종류 select에 `GOOGLE_DRIVE`를 활성화한다.
- `GOOGLE_DRIVE` 선택 시 `driveFolderId` config 입력 필드를 보여준다 (Notion의 rootPageId 필드 패턴).
- `App.test.tsx`의 "GOOGLE_DRIVE 선택지 없음" 단언을 "있음 + config 필드 렌더링" 검증으로 교체한다.

## 테스트

- `GoogleDriveConnector` 단위 테스트 (fake `GoogleDriveClient`):
  - 정상: 폴더 재귀 + Docs export + 텍스트 파일 + PDF가 문서 이벤트로 방출, 경로 메타데이터 검증.
  - 실패: export 실패 → failure 이벤트 방출, 순회는 계속.
  - 무시: 지원 외 mimeType·과대 파일은 문서/실패 어느 쪽으로도 방출되지 않음.
- `GoogleDriveApiClient`는 기존 `NotionApiClient` 테스트 스타일을 따른다 (토큰 갱신 포함).
- 통합 경로(수집 파이프라인, ACL fail-closed)는 기존 테스트가 이미 커버하므로 재작성하지 않는다.

## 검증

- `./gradlew test`
- 어드민 UI 변경은 Playwright로 실제 화면 확인 (GOOGLE_DRIVE 생성 폼 렌더링).
- 실 계정 연동 스모크: 리프레시 토큰 발급 후 로컬에서 폴더 1개 수집 → 검색 확인.
