# Google Drive 자격증명 발급과 폴더 연결

`GOOGLE_DRIVE` 데이터소스는 앱 전역 OAuth 리프레시 토큰 하나로 소유자 본인의 드라이브를 읽습니다.
아래 절차는 최초 1회만 수행하면 됩니다.

## 1. Google Cloud 프로젝트와 OAuth 클라이언트 만들기

1. [Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트를 만듭니다(기존 프로젝트 재사용 가능).
2. `API 및 서비스 → 라이브러리`에서 **Google Drive API**를 사용 설정합니다.
3. `API 및 서비스 → OAuth 동의 화면`을 구성합니다.
   - User Type은 `외부`로 두고, 게시 상태는 `테스트`면 충분합니다.
   - `테스트 사용자`에 본인 Google 계정을 추가합니다.
4. `API 및 서비스 → 사용자 인증 정보 → 사용자 인증 정보 만들기 → OAuth 클라이언트 ID`에서
   애플리케이션 유형을 **데스크톱 앱**으로 선택해 생성합니다.
5. 발급된 클라이언트 ID와 클라이언트 보안 비밀을 `.env`에 저장합니다.

```bash
GOOGLE_DRIVE_CLIENT_ID=<클라이언트 ID>
GOOGLE_DRIVE_CLIENT_SECRET=<클라이언트 보안 비밀>
```

## 2. 리프레시 토큰 발급 (1회 브라우저 동의)

1. 브라우저에서 아래 주소를 엽니다. `<CLIENT_ID>`만 바꾸면 됩니다.

```text
https://accounts.google.com/o/oauth2/v2/auth?client_id=<CLIENT_ID>&redirect_uri=http://localhost:8089&response_type=code&scope=https://www.googleapis.com/auth/drive.readonly&access_type=offline&prompt=consent
```

2. 동의를 마치면 `http://localhost:8089/?code=...` 로 이동합니다. 페이지는 열리지 않아도 되며,
   주소창의 `code` 값만 복사합니다(`&scope` 앞까지).
3. 복사한 코드를 리프레시 토큰으로 교환합니다.

```bash
curl -d "code=<CODE>&client_id=<CLIENT_ID>&client_secret=<CLIENT_SECRET>&redirect_uri=http://localhost:8089&grant_type=authorization_code" \
  https://oauth2.googleapis.com/token
```

4. 응답 JSON의 `refresh_token` 값을 `.env`에 저장합니다.

```bash
GOOGLE_DRIVE_REFRESH_TOKEN=<refresh_token 값>
```

인가 코드는 발급 후 수 분 안에 만료되므로, 만료되면 1번부터 다시 시도합니다.

## 3. 데이터소스 연결

1. 애플리케이션을 재시작한 뒤 관리자 화면(`/admin-ui`)에서 `데이터소스 추가`를 엽니다.
2. 종류를 `GOOGLE_DRIVE`로 선택하고 수집할 폴더의 링크
   (`https://drive.google.com/drive/folders/<폴더 ID>`) 또는 폴더 ID를 입력합니다.
3. 저장 후 `수동 수집`을 실행하면 폴더와 하위 폴더의 지원 파일이 수집됩니다.

## 수집 범위와 제한

- 지원 타입: Google Docs(텍스트 export), Google Sheets(CSV export), `text/*`·JSON 계열, PDF(텍스트 추출).
- 이미지·영상 등 지원 외 타입과 20MB 초과 파일, 텍스트가 없는 파일(스캔본 PDF 등)은 조용히 건너뜁니다.
- 삭제·변경 정합은 전체 스냅샷 방식으로 처리하므로, 드라이브에서 지운 파일은 다음 수집에서 소프트 삭제됩니다.
