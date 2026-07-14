# Notion 페이지 링크 입력 설계

## 목표

관리자 데이터소스 생성·수정 화면에서 Notion 페이지를 등록할 때 데이터베이스와 동일하게 전체 링크 또는 page ID를 입력할 수 있게 한다. 서버는 입력값을 표준 UUID 형식으로 정규화해 저장하고, 기존 Notion 페이지 수집 동작은 유지한다.

## 현재 동작

- `notionDatabaseId`는 전체 HTTP(S) URL, 하이픈이 있는 UUID, 32자리 UUID를 허용한다.
- 서버는 데이터베이스 URL path에서 마지막 Notion ID를 추출하고 소문자 하이픈 UUID로 저장한다.
- `notionRootPageId`는 비어 있지 않은지만 확인한 뒤 입력 문자열을 그대로 저장한다.
- 관리자 화면도 페이지 필드를 `Notion 루트 페이지 ID`로 안내하고 데이터베이스 필드만 링크 입력을 안내한다.

## 범위

- `notionRootPageId` 입력에 전체 HTTP(S) Notion 페이지 URL, 하이픈 UUID, 32자리 UUID를 허용한다.
- 페이지와 데이터베이스 입력이 같은 서버 정규화 함수를 사용하게 한다.
- 관리자 화면의 페이지 필드 라벨과 placeholder를 링크 또는 ID 입력 방식에 맞게 변경한다.
- 생성과 수정 경로에 같은 규칙을 적용한다.
- 관련 관리자 GraphQL 테스트, 프런트엔드 테스트, 실제 브라우저 검증, 사용자 안내 문서를 갱신한다.

## 제외 범위

- GraphQL 필드명 `notionRootPageId`와 `notionDatabaseId` 변경
- `config_json` 구조나 DB 스키마 변경
- 저장된 기존 설정값의 일괄 마이그레이션
- 입력 시 Notion API를 호출해 페이지 존재 여부나 접근 권한을 확인하는 기능
- URL query parameter나 fragment에만 포함된 ID 추출
- Notion 페이지 수집, 하위 페이지 순회, ACL, metadata 동작 변경

## 입력과 정규화 규칙

서버의 관리자 데이터소스 저장 경계에서 다음 순서로 처리한다.

1. 입력값의 앞뒤 공백을 제거하고 빈 값이면 기존 필수값 오류를 반환한다.
2. 값이 `http://` 또는 `https://`로 시작하면 URI로 파싱하고 path만 ID 후보로 사용한다.
3. 후보 문자열에서 마지막 32자리 또는 하이픈 UUID 형태의 16진수 값을 선택한다.
4. 하이픈을 제거하고 소문자로 바꾼 뒤 `8-4-4-4-12` UUID 형식으로 저장한다.
5. ID를 찾을 수 없으면 필드에 맞는 형식 오류를 반환한다.

예를 들어 다음 입력은 모두 같은 값으로 저장한다.

```text
https://www.notion.so/workspace/Project-Wiki-248104cd477e80fdb757e945d38000bd?pvs=4
248104cd477e80fdb757e945d38000bd
248104cd-477e-80fd-b757-e945d38000bd
```

저장값:

```text
248104cd-477e-80fd-b757-e945d38000bd
```

URL은 ID를 로컬에서 추출하기 위한 문자열로만 취급하며 서버가 해당 URL로 요청하지 않는다. 데이터베이스 입력의 현재 허용 범위와 결과는 바꾸지 않는다.

## 백엔드 설계

`AdminDataSourceService`의 데이터베이스 전용 정규화 함수를 필드명을 받는 공통 Notion ID 정규화 함수로 바꾼다.

- 페이지 모드에서는 `normalizeNotionId(rootPageId, "notionRootPageId")` 결과를 `notionRootPageId` config에 저장한다.
- 데이터베이스 모드에서는 `normalizeNotionId(databaseId, "notionDatabaseId")` 결과를 `notionDatabaseId` config에 저장한다.
- 두 값 중 정확히 하나만 허용하는 현재 fail-closed 검증을 유지한다.
- GraphQL schema와 응답 payload 필드명은 유지하므로 API 및 codegen 호환성은 깨지지 않는다.
- `NotionPageConnector`는 이미 저장된 정규화 ID를 소비하므로 변경하지 않는다.

## 관리자 화면 설계

페이지 모드 입력 필드를 다음과 같이 바꾼다.

- 라벨: `Notion 루트 페이지 링크 또는 ID`
- placeholder: 표준 Notion 페이지 링크 예시
- input type은 URL과 ID를 함께 허용해야 하므로 기존 텍스트 입력을 유지한다.
- 프런트엔드는 값을 변환하지 않고 GraphQL mutation에 전달한다. 서버가 단일 정규화·검증 경계가 된다.

수정 화면에는 서버가 저장한 표준 UUID가 표시된다. 기존 저장 데이터는 자동으로 다시 쓰지 않으며, 이후 생성·수정 요청부터 새 검증을 적용한다.

## 오류 처리

- 페이지 입력에서 ID를 추출할 수 없으면 `notionRootPageId 형식이 올바르지 않습니다`를 반환한다.
- 데이터베이스 입력 오류는 기존 `notionDatabaseId 형식이 올바르지 않습니다`를 유지한다.
- 둘 다 입력하거나 둘 다 비운 경우의 기존 상호 배타·필수 오류를 유지한다.
- URI 파싱 실패와 ID 패턴 불일치는 같은 사용자-facing 형식 오류로 통일한다.
- 오류가 발생한 요청은 데이터소스와 ACL 정책을 저장하지 않는다.

## 테스트와 검증

### 백엔드

- 전체 페이지 URL로 생성하면 표준 UUID가 GraphQL 응답과 `config_json`에 저장된다.
- 하이픈 UUID와 32자리 ID도 같은 표준 UUID로 저장된다.
- 잘못된 페이지 URL 또는 ID는 필드별 형식 오류를 반환하고 데이터소스를 만들지 않는다.
- 페이지 링크를 사용한 수정도 표준 UUID로 저장된다.
- 기존 데이터베이스 링크 입력 테스트를 유지해 공통 함수 추출의 회귀가 없음을 확인한다.
- 페이지/데이터베이스 상호 배타와 필수값 실패 테스트를 유지한다.

### 프런트엔드

- 페이지 모드에 `Notion 루트 페이지 링크 또는 ID` 라벨과 링크 placeholder가 표시된다.
- 페이지 URL을 입력하면 `notionRootPageId` mutation 변수로 전달되고 `notionDatabaseId`는 비워진다.
- 데이터베이스 모드의 기존 링크 입력 payload가 유지된다.

### 실제 화면

- Playwright로 전체 페이지 URL을 입력해 데이터소스 생성이 성공하는 정상 케이스를 확인한다.
- 잘못된 페이지 링크를 입력했을 때 저장되지 않고 형식 오류가 표시되는 실패 케이스를 확인한다.
- 자동 테스트와 실제 화면 확인 결과를 커밋 및 PR 본문에 기록한다.

## 호환성과 데이터 영향

- DB 스키마와 GraphQL schema 변경이 없어 배포 시 마이그레이션은 필요하지 않다.
- 기존 데이터소스 행과 문서는 변경하지 않는다.
- 이미 올바른 Notion page ID를 저장한 데이터소스는 수집 및 수정 동작이 그대로 유지된다.
- 새로 저장되는 페이지와 데이터베이스 식별자는 동일한 표준 UUID 표현을 사용한다.
