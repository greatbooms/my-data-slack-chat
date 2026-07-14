# Notion Integration 키 발급과 페이지/데이터베이스 연결

이 문서는 로컬 개발에서 `NOTION` 데이터소스를 수집하기 위해 Notion integration token과 페이지 ID 또는 데이터베이스 링크/ID를 준비하는 절차입니다.

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

## 3. 수집할 페이지 또는 데이터베이스에 connection 초대하기

Notion connection은 생성 직후 아무 페이지나 데이터베이스에도 접근할 수 없습니다. 수집할 루트 페이지 또는 원본 데이터베이스에 명시적으로 접근 권한을 줘야 합니다.

방법 A: Notion 화면에서 연결

1. 수집할 루트 페이지 또는 원본 데이터베이스를 엽니다.
2. 우측 상단 `...` 메뉴를 엽니다.
3. `Connections` 또는 `+ Add connection`을 선택합니다.
4. 방금 만든 connection을 검색해서 추가합니다.
5. 하위 페이지나 데이터베이스 row 접근을 허용하는 확인 창이 나오면 승인합니다.

방법 B: Developer Portal에서 연결

1. connection 상세 화면의 `Content access` 탭을 엽니다.
2. `Edit access`를 누릅니다.
3. 수집할 페이지나 원본 데이터베이스를 선택합니다.

루트 페이지에 connection을 추가하면 하위 페이지 접근도 함께 상속됩니다. 데이터베이스를 수집할 때는 linked database가 아니라 원본 데이터베이스에 connection을 추가해야 합니다.

## 4. Notion 페이지 링크 또는 ID 확인하기

관리자 화면에서 `Notion 수집 대상`을 `페이지`로 선택하면 `Notion 루트 페이지 링크 또는 ID`를 입력합니다.

가장 쉬운 방법:

1. Notion에서 수집할 루트 페이지를 엽니다.
2. 우측 상단 `Share`에서 링크를 복사합니다.
3. 복사한 링크 전체를 관리자 화면에 붙여 넣습니다.

서버는 URL path의 마지막 page ID를 추출해 소문자 하이픈 UUID로 저장합니다. 전체 링크 대신 32자리 ID나 하이픈 UUID를 직접 입력해도 됩니다.

## 5. Notion 데이터베이스 링크 또는 ID 확인하기

관리자 화면에서 `Notion 수집 대상`을 `데이터베이스`로 선택하면 `Notion 데이터베이스 링크 또는 ID`를 입력합니다.

가장 쉬운 방법:

1. Notion에서 원본 데이터베이스를 full page로 엽니다.
2. 우측 상단 `Share`에서 링크를 복사합니다.
3. 복사한 링크 전체를 관리자 화면에 붙여 넣습니다.

데이터베이스 URL 예시:

```text
https://www.notion.so/workspace/Roadmap-248104cd477e80fdb757e945d38000bd?v=248104cd477e80afbc30000bd28de8f9
```

서버는 URL path에 있는 database ID만 추출해 저장합니다. 위 예시에서 저장되는 값은 다음과 같습니다.

```text
248104cd-477e-80fd-b757-e945d38000bd
```

Notion API `2026-03-11`에서는 데이터베이스와 data source가 분리되어 있습니다. 이 프로젝트는 사용자가 database 링크/ID를 입력하면 서버가 Notion API로 연결된 data source ID를 찾아 row page를 수집합니다. 데이터베이스에 data source가 여러 개 있으면 현재 버전에서는 어느 data source를 쓸지 자동 선택하지 않고 수집을 실패시킵니다.

## 6. 페이지 아래 데이터베이스 자동 수집

수집 대상을 `페이지`로 등록하면 루트 페이지와 `child_page` 하위 페이지를 재귀적으로 수집하고, 그 범위에서 발견한 `child_database`의 행도 함께 수집합니다. 화면에 보이는 database view의 필터·정렬은 적용하지 않으며, database가 가리키는 단일 data source의 전체 행을 읽습니다.

원본 database를 Notion integration에 직접 공유해야 합니다. linked database에서 원본 ID를 API가 제공하지 않거나 원본이 공유되지 않은 경우 자동 해석하지 않으므로, 원본 database 링크를 `데이터베이스` 대상으로 별도 등록하세요.

## 7. 재수집과 삭제 반영

Notion 수집이 완전히 성공하면 이번 수집 범위에서 발견되지 않은 기존 페이지와 database row는 소프트 삭제되어 새 검색 결과에서 제외됩니다. 같은 Notion ID가 다시 수집 범위에 나타나면 기존 문서 ID를 유지한 채 복구됩니다.

일부 페이지나 database를 읽지 못해 `PARTIAL_FAILED` 또는 `FAILED`가 되면 누락 문서 정리를 실행하지 않습니다. 권한이나 일시적인 API 오류 때문에 기존 문서가 잘못 삭제되지 않도록, 다음 완전 성공 수집에서만 현재 구조를 확정합니다.

## 8. 부분 실패 확인

일부 문서는 저장되고 일부 페이지나 database만 실패하면 job 상태가 `PARTIAL_FAILED`가 됩니다. 관리자 화면의 `수집 기록`에서 성공·실패 개수를 확인하고 `실패 상세 보기`에서 external ID, 경로, 실패 단계와 원인을 확인할 수 있습니다. `더 보기`는 실패 항목을 50개씩 추가로 불러옵니다. 부분 실패와 전체 실패에서는 cursor와 마지막 수집 시각을 갱신하지 않으므로 공유 권한이나 원인을 수정한 뒤 수동 수집을 다시 실행하세요.

## 9. 이 프로젝트에서 데이터소스 만들기

1. `.env`를 로드한 뒤 서버를 실행합니다.

```bash
set -a
source .env
set +a
./gradlew bootRun
```

2. 관리자 화면에서 데이터소스를 추가합니다.
3. `종류`는 `NOTION`을 선택합니다.
4. `Notion 수집 대상`에서 `페이지` 또는 `데이터베이스`를 선택합니다.
5. 페이지를 선택한 경우 `Notion 루트 페이지 링크 또는 ID`에 복사한 페이지 링크나 ID를 입력합니다.
6. 데이터베이스를 선택한 경우 `Notion 데이터베이스 링크 또는 ID`에 복사한 데이터베이스 링크나 ID를 입력합니다.
7. 저장 후 수동 수집을 실행합니다.

## 10. 자주 나는 오류

- `object_not_found`: connection이 해당 페이지에 초대되지 않았거나 페이지 ID가 틀렸을 가능성이 큽니다.
- 데이터베이스 수집의 `object_not_found`: 원본 데이터베이스에 connection이 초대되지 않았거나 linked database 링크를 넣었을 가능성이 큽니다.
- `unauthorized`: `NOTION_API_TOKEN` 값이 비었거나 잘못됐을 가능성이 큽니다.
- `data source가 1개여야 합니다`: 입력한 데이터베이스에 data source가 여러 개 있습니다. 현재 버전에서는 하나의 data source만 가진 데이터베이스를 지원합니다.
- 수집 결과가 비어 있음: 현재 커넥터는 텍스트 블록, 하위 페이지, 데이터베이스 row page의 주요 속성값을 중심으로 평탄화합니다. 이미지, 파일, 임베드 같은 비텍스트 블록은 본문 텍스트로 저장하지 않습니다.
