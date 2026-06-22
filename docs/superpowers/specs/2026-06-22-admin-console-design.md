# 관리자 콘솔 설계

## 요약

React 기반 관리자 화면을 추가하고, 빌드된 정적 파일을 Spring Boot가 같은 서버에서 제공한다. 관리자 인증은 기존 `X-Admin-Token` 방식이 아니라 DB 사용자와 Spring Security 세션을 사용한다. 관리자 화면은 데이터소스 관리, 수동 수집, 최근 수집 상태 확인, 유저 관리, 소프트 삭제를 제공한다.

## 확정된 결정

- 프론트엔드는 `frontend/admin`에 React + Vite + TypeScript로 둔다.
- 최종 실행은 Spring Boot 서버 하나로 한다.
- React 빌드 결과는 `/admin-ui/**`에서 정적 서빙한다.
- 화면 레이아웃은 사이드바 관리 콘솔 구조로 한다.
- 관리자 인증은 DB 기반 관리자 계정과 세션 쿠키를 사용한다.
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
- Spring Boot 하나만 실행해도 API와 관리자 UI가 함께 동작한다.

## 제외 범위

- 일반 사용자용 셀프서비스 화면
- OAuth 기반 소셜 로그인
- Notion, Google Drive, Slack 실제 커넥터 구현
- 세밀한 CUSTOM 유저별 데이터소스 공유 UI
- 운영용 감사 로그와 관리자 작업 이력
- 완전한 권한 그룹/조직 관리

## URL 구조

관리자 UI:

```text
/admin-ui/login
/admin-ui
/admin-ui/data-sources
/admin-ui/data-sources/{id}
/admin-ui/users
```

관리자 인증 API:

```text
GET  /admin/auth/csrf
POST /admin/auth/login
POST /admin/auth/logout
GET  /admin/auth/me
```

관리 API:

```text
GET    /admin/api/dashboard/summary

GET    /admin/api/users
POST   /admin/api/users
GET    /admin/api/users/{id}
PATCH  /admin/api/users/{id}
DELETE /admin/api/users/{id}
POST   /admin/api/users/{id}/restore
POST   /admin/api/users/{id}/password

GET    /admin/api/data-sources
POST   /admin/api/data-sources
GET    /admin/api/data-sources/{id}
PATCH  /admin/api/data-sources/{id}
DELETE /admin/api/data-sources/{id}
POST   /admin/api/data-sources/{id}/sync
GET    /admin/api/data-sources/{id}/jobs
```

## 인증과 세션

Spring Security를 추가하고 관리자 화면/API는 세션 쿠키 기반으로 보호한다. 로그인 API는 이메일과 비밀번호를 받아 인증하고, 성공 시 서버 세션을 만든다.

로그인 가능한 사용자의 조건:

- `users.role = 'ADMIN'`
- `users.status = 'ACTIVE'`
- `users.deleted_at IS NULL`
- `users.password_hash`가 존재하고 입력 비밀번호와 일치

비밀번호 저장:

- BCrypt 해시를 사용한다.
- 평문 비밀번호는 DB나 로그에 저장하지 않는다.

CSRF:

- 같은 origin의 브라우저 UI가 세션 쿠키로 API를 호출하므로 CSRF 보호를 켠다.
- React 앱은 `GET /admin/auth/csrf`로 token을 받아 변경 요청에 포함한다.

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
- 관리자 API는 `status`, `started_at`, `finished_at`, `error_message`, `created_at`을 조회해 이력을 표시한다.
- 수집 성공 시 `data_sources.last_synced_at`을 갱신한다.

## 백엔드 컴포넌트

인증:

- `admin.auth` 패키지를 새로 두고 로그인, 로그아웃, 현재 사용자 조회를 담당한다.
- Spring Security 설정은 `/admin-ui/login`, 정적 asset, `/admin/auth/login`, `/admin/auth/csrf`만 공개한다.
- `/admin-ui/**`, `/admin/api/**`, `/admin/auth/me`, `/admin/auth/logout`는 admin 세션이 필요하다.

유저 관리:

- `admin.users` 패키지를 새로 두고 관리자용 사용자 API를 담당한다.
- 소프트 삭제는 `deleted_at`을 설정한다.
- 복구는 `deleted_at`을 `NULL`로 되돌리고 status를 `ACTIVE`로 설정한다.
- 비활성화는 `status = 'DISABLED'`로 처리한다.

데이터소스 관리:

- `admin.datasources` 패키지를 새로 두고 데이터소스 관리 API를 제공한다.
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
npm run dev
```

통합 실행:

```bash
./gradlew bootRun
```

빌드 흐름:

- `frontend/admin`에서 lockfile이 있는 상태로 `npm ci`를 실행한다.
- `npm run build`로 `frontend/admin/dist`를 만든다.
- Gradle task가 dist 결과를 Spring Boot runtime resource의 `static/admin-ui`로 복사한다.
- `/admin-ui/**`는 React SPA의 `index.html`로 fallback한다.
- `/admin/api/**`와 `/admin/auth/**`는 API로 처리되며 정적 fallback과 충돌하지 않는다.

## 에러 처리

관리자 API는 JSON 에러 응답을 반환한다.

대표 케이스:

- 인증되지 않음: `401`
- admin 권한 없음: `403`
- 리소스 없음: `404`
- 입력값 오류: `400`
- 삭제된 유저/데이터소스 접근: 기본적으로 `404`
- 이미 실행 중인 수집 job과 충돌: `409`
- 예상하지 못한 서버 오류: `500`

프론트엔드는 실패 상태를 toast 또는 inline alert로 보여준다. 수동 수집 버튼은 요청 중 disabled 상태를 가진다.

## 테스트 전략

백엔드:

- Liquibase migration test로 새 컬럼과 기본값을 검증한다.
- bootstrap admin 생성 테스트를 추가한다.
- 로그인 성공/실패, 삭제/비활성 유저 로그인 차단 테스트를 추가한다.
- 관리자 API는 MockMvc 기반 통합 테스트로 인증 필요 여부와 CRUD 동작을 검증한다.
- 데이터소스 생성/수정 시 접근 정책이 `PRIVATE`/`WORKSPACE`에 맞게 생성되는지 검증한다.
- 수동 수집 성공 시 `last_synced_at` 갱신을 검증한다.

프론트엔드:

- Vite build가 통과해야 한다.
- 주요 화면은 브라우저에서 실제 렌더링을 확인한다.
- 로그인, 목록, 생성/수정, 수동 수집 버튼의 기본 흐름을 확인한다.

통합:

- `./gradlew test`
- `./gradlew bootRun`
- `/admin-ui` 정적 화면 접근
- `/admin/auth/login` 후 `/admin/api/**` 접근

## 구현 순서 제안

1. Spring Security와 DB 기반 관리자 인증 추가
2. Liquibase와 JPA 모델 확장
3. bootstrap admin 생성
4. 관리자 유저 API 구현
5. 데이터소스 관리 API와 job 이력 API 구현
6. React/Vite 앱 scaffold와 Gradle 빌드 연동
7. 로그인 화면과 세션 처리
8. 사이드바 레이아웃과 대시보드
9. 유저 관리 화면
10. 데이터소스 관리 화면
11. 정적 서빙 fallback과 통합 실행 검증

## 후속 확장

- `CUSTOM` 데이터소스 공유
- Notion connector 설정 UI
- Google Drive connector 설정 UI
- Slack 데이터소스 수집 UI
- 관리자 작업 감사 로그
- 일반 사용자용 셀프서비스 데이터소스 추가 화면
