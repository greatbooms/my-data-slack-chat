# 관리자 콘솔 설계

## 요약

React 기반 관리자 화면을 추가하고, 빌드된 정적 파일을 Spring Boot가 같은 서버에서 제공한다. 관리자 인증은 기존 `X-Admin-Token` 방식이 아니라 DB 사용자와 Spring Security 세션을 사용한다. 관리자 업무 데이터 통신은 GraphQL 단일 엔드포인트로 처리하고, 프론트엔드는 GraphQL Code Generator로 API 타입을 관리한다.

관리자 화면은 데이터소스 관리, 수동 수집, 최근 수집 상태 확인, 유저 관리, 소프트 삭제를 제공한다.

## 확정된 결정

- 프론트엔드는 `frontend/admin`에 React + Vite + TypeScript로 둔다.
- 최종 실행은 Spring Boot 서버 하나로 한다.
- React 빌드 결과는 `/admin-ui/**`에서 정적 서빙한다.
- 화면 레이아웃은 사이드바 관리 콘솔 구조로 한다.
- 관리자 인증은 DB 기반 관리자 계정과 세션 쿠키를 사용한다.
- 세션 생성/폐기와 CSRF 토큰 조회는 `/admin/auth/**` 보안 엔드포인트로 둔다.
- 관리자 업무 데이터 통신은 `/admin/graphql` GraphQL 엔드포인트로 통합한다.
- GraphQL 스키마는 백엔드의 `src/main/resources/graphql/admin.graphqls`를 기준 계약으로 관리한다.
- 프론트엔드는 GraphQL Code Generator로 operation 변수와 응답 타입을 생성해 사용한다.
- 최초 관리자 계정은 환경변수로 앱 시작 시 자동 생성한다.
- 사용자 관리는 조회, 생성, 수정, 비활성화, 소프트 삭제, 복구, 비밀번호 재설정을 포함한다.
- 데이터소스 관리는 조회, 생성, 수정, 소프트 삭제, 수동 수집, 최근 수집 job 이력 확인을 포함한다.
- 데이터소스 접근 범위는 `PRIVATE`, `WORKSPACE` 두 가지를 우선 제공한다.
- 초기 개발 단계이므로 기존 `X-Admin-Token` 관리자 API 하위호환성은 유지하지 않는다.

## 목표

- 브라우저에서 로그인 후 관리자 콘솔을 사용할 수 있다.
- 관리자는 데이터소스를 만들고 수정하고 삭제할 수 있다.
- 관리자는 데이터소스별 접근 범위를 `PRIVATE` 또는 `WORKSPACE`로 설정할 수 있다.
- 관리자는 데이터소스 수동 수집을 실행할 수 있다.
- 관리자는 마지막 수집 시각, 최근 수집 job 상태, 에러 메시지를 확인할 수 있다.
- 관리자는 사용자를 만들고 수정하고 비활성화하고 소프트 삭제/복구할 수 있다.
- 프론트엔드는 생성된 GraphQL 타입으로 API 응답 필드와 mutation 변수를 컴파일 타임에 검증한다.
- Spring Boot 하나만 실행해도 API와 관리자 UI가 함께 동작한다.

## 제외 범위

- 일반 사용자용 셀프서비스 화면
- OAuth 기반 소셜 로그인
- Notion, Google Drive, Slack 실제 커넥터 구현
- 세밀한 CUSTOM 유저별 데이터소스 공유 UI
- 운영용 감사 로그와 관리자 작업 이력
- 완전한 권한 그룹/조직 관리
- GraphQL subscription 기반 실시간 화면 갱신
- 외부 클라이언트 공개용 GraphQL API

## URL 구조

관리자 UI:

```text
/admin-ui/login
/admin-ui
/admin-ui/data-sources
/admin-ui/data-sources/{id}
/admin-ui/users
```

관리자 인증 엔드포인트:

```text
GET  /admin/auth/csrf
POST /admin/auth/login
POST /admin/auth/logout
```

관리자 GraphQL 엔드포인트:

```text
POST /admin/graphql
```

`/admin/auth/**`는 세션을 만들고 지우는 보안 경계로만 사용한다. 로그인 이후 현재 관리자 조회, 대시보드, 사용자 관리, 데이터소스 관리는 모두 GraphQL query/mutation으로 처리한다.

## GraphQL 계약

스키마 위치:

```text
src/main/resources/graphql/admin.graphqls
```

초기 스키마 골격:

```graphql
type Query {
  viewer: AdminViewer!
  dashboardSummary: AdminDashboardSummary!
  users(filter: UserFilterInput, page: PageInput): UserPage!
  user(id: ID!): User
  dataSources(filter: DataSourceFilterInput, page: PageInput): DataSourcePage!
  dataSource(id: ID!): DataSource
  ingestionJobs(dataSourceId: ID!, first: Int = 20): [IngestionJob!]!
}

type Mutation {
  createUser(input: CreateUserInput!): User!
  updateUser(id: ID!, input: UpdateUserInput!): User!
  disableUser(id: ID!): User!
  softDeleteUser(id: ID!): User!
  restoreUser(id: ID!): User!
  resetUserPassword(id: ID!, input: ResetUserPasswordInput!): User!

  createDataSource(input: CreateDataSourceInput!): DataSource!
  updateDataSource(id: ID!, input: UpdateDataSourceInput!): DataSource!
  softDeleteDataSource(id: ID!): DataSource!
  requestDataSourceSync(id: ID!): IngestionJob!
}
```

주요 enum:

```graphql
enum UserRole {
  USER
  ADMIN
}

enum UserStatus {
  ACTIVE
  DISABLED
}

enum DataSourceType {
  LOCAL_TEXT
  NOTION
  GOOGLE_DRIVE
  SLACK
}

enum DataSourceVisibility {
  PRIVATE
  WORKSPACE
}

enum DataSourceStatus {
  ACTIVE
  DISABLED
}

enum IngestionJobStatus {
  PENDING
  RUNNING
  SUCCEEDED
  FAILED
}
```

설계 원칙:

- mutation input은 화면 form 단위와 맞춘다.
- 삭제된 사용자/데이터소스는 기본 목록에서 제외하고, 상세 조회는 `NOT_FOUND`로 처리한다.
- 목록 query는 `items`와 `totalCount`를 반환하는 page 타입을 사용한다.
- GraphQL 스키마의 enum 값은 DB 문자열 값과 동일하게 유지한다.
- 화면에서 필요한 조합 응답은 GraphQL type에 명시하고, 프론트엔드는 필요한 field만 operation에 적는다.

## 인증과 세션

Spring Security를 추가하고 관리자 화면과 `/admin/graphql`은 세션 쿠키 기반으로 보호한다. 로그인 엔드포인트는 이메일과 비밀번호를 받아 인증하고, 성공 시 서버 세션을 만든다.

로그인 가능한 사용자의 조건:

- `users.role = 'ADMIN'`
- `users.status = 'ACTIVE'`
- `users.deleted_at IS NULL`
- `users.password_hash`가 존재하고 입력 비밀번호와 일치

비밀번호 저장:

- BCrypt 해시를 사용한다.
- 평문 비밀번호는 DB나 로그에 저장하지 않는다.

CSRF:

- 같은 origin의 브라우저 UI가 세션 쿠키로 요청하므로 CSRF 보호를 켠다.
- React 앱은 `GET /admin/auth/csrf`로 token을 받아 로그인, 로그아웃, 모든 `/admin/graphql` POST 요청에 포함한다.
- `/admin/graphql`은 POST만 허용한다. query도 같은 endpoint를 사용한다.

Spring Security 공개/보호 범위:

- 공개: `/admin-ui/login`, 정적 asset, `/admin/auth/login`, `/admin/auth/csrf`
- 보호: `/admin-ui/**`, `/admin/graphql`, `/admin/auth/logout`

제거되는 기존 방식:

- `AdminTokenAuthenticationInterceptor`
- `AdminWebMvcConfiguration`의 token interceptor 등록
- `ADMIN_API_TOKEN`
- 기존 `/admin/data-sources/{id}/sync`

## 최초 관리자 생성

앱 시작 시 DB에 삭제되지 않은 `ADMIN` 역할 사용자가 하나도 없으면 bootstrap 환경변수로 최초 관리자를 생성한다.

환경변수:

```env
ADMIN_BOOTSTRAP_EMAIL=admin@example.com
ADMIN_BOOTSTRAP_PASSWORD=change-me
ADMIN_BOOTSTRAP_DISPLAY_NAME=관리자
```

동작:

- 삭제되지 않은 admin 사용자가 없고 세 환경변수가 모두 있으면 최초 admin을 생성한다.
- 삭제되지 않은 admin 사용자가 없는데 환경변수가 누락되면 앱 시작을 실패시킨다.
- 삭제되지 않은 admin 사용자가 이미 있으면 bootstrap 환경변수는 무시한다.

## DB 모델 변경

`users` 확장:

```text
password_hash TEXT
role TEXT NOT NULL DEFAULT 'USER'
status TEXT NOT NULL DEFAULT 'ACTIVE'
deleted_at TIMESTAMPTZ
updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
```

`password_hash`는 nullable로 둔다. 외부 identity만 있는 사용자나 과거 테스트 데이터가 있을 수 있기 때문이다. 단, 관리자 콘솔에서 생성한 로그인 사용자는 반드시 비밀번호 해시를 가진다.

`data_sources` 확장:

```text
owner_user_id UUID REFERENCES users(id)
visibility TEXT NOT NULL DEFAULT 'PRIVATE'
deleted_at TIMESTAMPTZ
updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
```

기존 데이터소스의 `owner_user_id`는 같은 workspace의 `owner_user_id`로 채운다.

데이터소스 접근 정책:

- `PRIVATE`: `data_source_access_policies`에 `USER:{ownerUserId}` + `READ`를 저장한다.
- `WORKSPACE`: `data_source_access_policies`에 `WORKSPACE:{workspaceId}` + `READ`를 저장한다.
- 문서 수집 시 provider별 문서 ACL이 없으면 데이터소스 접근 정책을 문서 ACL 기본값으로 사용한다.

`ingestion_jobs`:

- 새 컬럼은 필요하지 않다.
- GraphQL resolver는 `status`, `started_at`, `finished_at`, `error_message`, `created_at`을 조회해 이력을 표시한다.
- 수집 성공 시 `data_sources.last_synced_at`을 갱신한다.

## 백엔드 컴포넌트

추가 의존성:

- `spring-boot-starter-security`
- `spring-boot-starter-graphql`

인증:

- `admin.auth` 패키지를 새로 두고 로그인, 로그아웃, CSRF 응답을 담당한다.
- Spring Security 설정은 `/admin-ui/login`, 정적 asset, `/admin/auth/login`, `/admin/auth/csrf`만 공개한다.
- `/admin-ui/**`, `/admin/graphql`, `/admin/auth/logout`는 admin 세션이 필요하다.

GraphQL:

- `admin.graphql` 패키지를 새로 두고 `@QueryMapping`, `@MutationMapping` controller를 둔다.
- resolver는 화면 전용 DTO를 반환하고 JPA entity를 직접 노출하지 않는다.
- 업무 로직은 `admin.users`, `admin.datasources` service에 둔다.
- GraphQL schema 파일과 resolver method 이름을 함께 관리한다.

유저 관리:

- `admin.users` 패키지를 새로 두고 관리자용 사용자 service를 담당한다.
- 소프트 삭제는 `deleted_at`을 설정한다.
- 복구는 `deleted_at`을 `NULL`로 되돌리고 status를 `ACTIVE`로 설정한다.
- 비활성화는 `status = 'DISABLED'`로 처리한다.

데이터소스 관리:

- `admin.datasources` 패키지를 새로 두고 데이터소스 관리 service를 담당한다.
- 생성/수정 시 `visibility` 값에 맞게 `data_source_access_policies`를 재구성한다.
- 초기 생성 지원 타입은 `LOCAL_TEXT`부터 시작한다.
- 실제 Notion, Google Drive, Slack connector는 후속 작업에서 타입별 설정 UI와 credential 연결을 추가한다.

## React 관리자 UI

위치:

```text
frontend/admin
```

기술:

- React
- TypeScript
- Vite
- React Router
- GraphQL Code Generator
- `graphql-request`
- TanStack Query

GraphQL 타입 생성:

- 스키마 입력: `../../src/main/resources/graphql/admin.graphqls`
- operation 위치: `src/graphql/**/*.graphql`
- 생성 결과: `src/generated/graphql.ts`
- npm script: `npm run codegen`
- 빌드 전 `codegen`을 실행해 query/mutation 변수와 응답 타입을 최신 상태로 맞춘다.
- 생성 파일은 직접 수정하지 않는다.

프론트엔드 API 계층:

- `graphql-request` client는 `/admin/graphql`을 상대 경로로 호출한다.
- 세션 쿠키는 same origin 요청으로 자동 전송한다.
- CSRF token은 앱 시작 시 가져와 로그인, 로그아웃, GraphQL 요청 header에 포함한다.
- TanStack Query hook은 생성된 GraphQL document와 타입을 사용한다.
- 화면 컴포넌트는 직접 문자열 query를 만들지 않는다.

레이아웃:

- 좌측 사이드바
- 상단 현재 관리자/로그아웃 영역
- 본문 route outlet

화면:

- 로그인
- 대시보드
- 데이터소스 목록
- 데이터소스 상세
- 데이터소스 생성/수정 form
- 유저 목록
- 유저 생성/수정 form

UI 성격:

- 운영 도구이므로 조용하고 밀도 있는 관리 콘솔로 만든다.
- 카드형 랜딩 페이지가 아니라 목록, 필터, 상태 배지, 명확한 액션 버튼 중심으로 구성한다.
- 주요 액션은 생성, 수정, 수동 수집, 삭제, 복구, 비밀번호 재설정이다.

## 정적 서빙과 빌드

Gradle에서 React 빌드를 호출하고 결과물을 Spring Boot 정적 리소스로 복사한다.

개발:

```bash
cd frontend/admin
npm install
npm run codegen
npm run dev
```

Vite 개발 서버는 `/admin/auth/**`와 `/admin/graphql`을 `http://localhost:50506`으로 proxy한다.

통합 실행:

```bash
./gradlew bootRun
```

빌드 흐름:

- `frontend/admin`에서 lockfile이 있는 상태로 `npm ci`를 실행한다.
- `npm run codegen`으로 GraphQL 타입을 생성한다.
- `npm run build`로 `frontend/admin/dist`를 만든다.
- Gradle task가 dist 결과를 Spring Boot runtime resource의 `static/admin-ui`로 복사한다.
- `/admin-ui/**`는 React SPA의 `index.html`로 fallback한다.
- `/admin/graphql`과 `/admin/auth/**`는 서버 endpoint로 처리되며 정적 fallback과 충돌하지 않는다.

## 에러 처리

세션/권한 에러:

- 인증되지 않음: Spring Security가 `401`을 반환한다.
- admin 권한 없음: Spring Security가 `403`을 반환한다.

GraphQL 업무 에러:

- 입력값 오류: `errors[].extensions.code = BAD_REQUEST`
- 리소스 없음: `NOT_FOUND`
- 삭제된 유저/데이터소스 접근: 기본적으로 `NOT_FOUND`
- 이미 실행 중인 수집 job과 충돌: `CONFLICT`
- 예상하지 못한 서버 오류: `INTERNAL_ERROR`

프론트엔드는 GraphQL error code를 toast 또는 inline alert로 보여준다. 수동 수집 버튼은 요청 중 disabled 상태를 가진다.

## 테스트 전략

백엔드:

- Liquibase migration test로 새 컬럼과 기본값을 검증한다.
- bootstrap admin 생성 테스트를 추가한다.
- 로그인 성공/실패, 삭제/비활성 유저 로그인 차단 테스트를 추가한다.
- `/admin/graphql`은 Spring GraphQL test 또는 MockMvc 기반 통합 테스트로 인증 필요 여부와 query/mutation 동작을 검증한다.
- 데이터소스 생성/수정 mutation 실행 시 접근 정책이 `PRIVATE`/`WORKSPACE`에 맞게 생성되는지 검증한다.
- 수동 수집 mutation 성공 시 `last_synced_at` 갱신을 검증한다.

프론트엔드:

- `npm run codegen`이 통과해야 한다.
- TypeScript typecheck가 통과해야 한다.
- Vite build가 통과해야 한다.
- 주요 화면은 브라우저에서 실제 렌더링을 확인한다.
- 로그인, 목록, 생성/수정, 수동 수집 버튼의 기본 흐름을 확인한다.

통합:

- `./gradlew test`
- `./gradlew bootRun`
- `/admin-ui` 정적 화면 접근
- `/admin/auth/login` 후 `/admin/graphql` 접근

## 구현 순서 제안

1. Spring Security와 DB 기반 관리자 인증 추가
2. Liquibase와 JPA 모델 확장
3. bootstrap admin 생성
4. Spring GraphQL 의존성, `admin.graphqls`, resolver 골격 추가
5. 관리자 유저 query/mutation 구현
6. 데이터소스 관리 query/mutation과 job 이력 query 구현
7. React/Vite 앱 scaffold와 Gradle 빌드 연동
8. GraphQL Code Generator 설정과 기본 operation 추가
9. 로그인 화면과 세션/CSRF 처리
10. 사이드바 레이아웃과 대시보드
11. 유저 관리 화면
12. 데이터소스 관리 화면
13. 정적 서빙 fallback과 통합 실행 검증

## 후속 확장

- `CUSTOM` 데이터소스 공유
- Notion connector 설정 UI
- Google Drive connector 설정 UI
- Slack 데이터소스 수집 UI
- 관리자 작업 감사 로그
- 일반 사용자용 셀프서비스 데이터소스 추가 화면
