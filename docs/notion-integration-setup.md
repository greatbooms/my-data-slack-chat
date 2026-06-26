# Notion Integration 키 발급과 페이지 연결

이 문서는 로컬 개발에서 `NOTION` 데이터소스를 수집하기 위해 Notion integration token과 루트 페이지 ID를 준비하는 절차입니다.

## 1. Internal connection 만들기

1. Notion Developer Portal의 [Internal connections](https://www.notion.so/profile/integrations) 화면으로 이동합니다.
2. `Create a new connection`을 누릅니다.
3. connection 이름을 입력하고, 이 프로젝트에서 수집할 Notion workspace를 선택합니다.
4. 생성 후 `Configuration` 탭에서 capability를 확인합니다.
5. 현재 커넥터는 읽기 전용 수집만 하므로 최소 `Read content` 권한이 필요합니다.

공식 문서: [Internal connections](https://developers.notion.com/guides/get-started/internal-connections)

## 2. NOTION_API_TOKEN 받기

1. 생성한 connection의 `Configuration` 탭을 엽니다.
2. `Installation access token` 값을 복사합니다.
3. 프로젝트 루트의 `.env`에 아래처럼 넣습니다.

```bash
NOTION_API_TOKEN=복사한_Installation_access_token
NOTION_API_VERSION=2026-03-11
NOTION_BASE_URL=https://api.notion.com
```

주의:

- 실제 token은 `.env`에만 저장합니다.
- `.env`는 커밋하지 않습니다.
- token이 노출되면 Notion Developer Portal에서 token을 refresh한 뒤 `.env`를 갱신합니다.

## 3. 수집할 페이지에 connection 초대하기

Notion connection은 생성 직후 아무 페이지에도 접근할 수 없습니다. 수집할 루트 페이지에 명시적으로 접근 권한을 줘야 합니다.

방법 A: Notion 화면에서 연결

1. 수집할 루트 페이지를 엽니다.
2. 우측 상단 `...` 메뉴를 엽니다.
3. `Connections` 또는 `+ Add connection`을 선택합니다.
4. 방금 만든 connection을 검색해서 추가합니다.
5. 하위 페이지 접근을 허용하는 확인 창이 나오면 승인합니다.

방법 B: Developer Portal에서 연결

1. connection 상세 화면의 `Content access` 탭을 엽니다.
2. `Edit access`를 누릅니다.
3. 수집할 페이지나 데이터베이스를 선택합니다.

루트 페이지에 connection을 추가하면 하위 페이지 접근도 함께 상속됩니다.

## 4. Notion 루트 페이지 ID 확인하기

관리자 화면의 `Notion 루트 페이지 ID`에는 수집 시작점이 될 페이지 ID를 넣습니다.

페이지 URL 예시:

```text
https://www.notion.so/workspace/Project-Brief-0123456789abcdef0123456789abcdef
```

이 경우 페이지 ID는 마지막 32자리입니다.

```text
0123456789abcdef0123456789abcdef
```

하이픈이 포함된 UUID 형식도 사용할 수 있습니다.

```text
01234567-89ab-cdef-0123-456789abcdef
```

## 5. 이 프로젝트에서 데이터소스 만들기

1. `.env`를 로드한 뒤 서버를 실행합니다.

```bash
set -a
source .env
set +a
./gradlew bootRun
```

2. 관리자 화면에서 데이터소스를 추가합니다.
3. `종류`는 `NOTION`을 선택합니다.
4. `Notion 루트 페이지 ID`에 위에서 확인한 페이지 ID를 입력합니다.
5. 저장 후 수동 수집을 실행합니다.

## 6. 자주 나는 오류

- `object_not_found`: connection이 해당 페이지에 초대되지 않았거나 페이지 ID가 틀렸을 가능성이 큽니다.
- `unauthorized`: `NOTION_API_TOKEN` 값이 비었거나 잘못됐을 가능성이 큽니다.
- 수집 결과가 비어 있음: 현재 커넥터는 텍스트 블록과 하위 페이지를 중심으로 평탄화합니다. 이미지, 파일, 임베드 같은 비텍스트 블록은 본문 텍스트로 저장하지 않습니다.
