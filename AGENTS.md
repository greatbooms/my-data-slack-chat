# 에이전트 안내

## 프로젝트 개요

이 프로젝트는 Java 21, Spring Boot 4.1, Gradle 기반의 개인 데이터 RAG 챗봇 백엔드입니다.
초기 목표는 Notion, Slack, Google Drive 같은 외부 데이터를 수집하고 PostgreSQL + pgvector에 저장한 뒤, 질문자의 권한 범위 안에서만 검색해 답변하는 구조를 만드는 것입니다.

## 현재 구현 범위

- PostgreSQL + pgvector 스키마
- Liquibase 기반 DB 형상관리
- ACL 권한 주체 모델
- 수동 수집 job API
- `LOCAL_TEXT` 테스트 커넥터
- 결정적 임베딩 클라이언트
- ACL 필터가 적용된 벡터 검색
- 채팅 답변과 출처 저장
- Slack Socket Mode 수신 기반과 선택형 HTTP Events API 엔드포인트

## 로컬 실행

- 기본 애플리케이션 포트는 `50506`입니다.
- 로컬 DB는 Docker Compose의 `my-data-postgres` 컨테이너를 사용하며 호스트 포트는 `5433`입니다.
- 로컬 환경변수 예시는 `.env.example`을 확인하세요.

주요 명령:

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
./gradlew test
```

## 작업 규칙

- 새 기능이나 수정 작업을 시작할 때는 먼저 `main` 브랜치를 최신 원격 상태로 pull 받습니다.
- 실제 작업은 최신 `main` 기준에서 `codex/` 접두사의 작업 브랜치를 새로 만든 뒤 진행합니다.
- 이미 진행 중인 작업 브랜치가 있다면, 새 작업과 섞이지 않는지 확인한 뒤 계속 사용할지 새 브랜치를 만들지 판단합니다.
- 코드 탐색을 시작할 때는 현재 디렉터리를 Serena 프로젝트로 활성화하고 Serena initial instructions를 먼저 읽습니다.
- 코드 구조 탐색은 `grep` 같은 단순 텍스트 검색보다 Serena symbolic tools를 우선 사용합니다.
- 필요한 심볼이나 관계를 Serena로 좁힌 뒤, 부족한 경우에만 `rg` 같은 텍스트 검색을 보조로 사용합니다.
- 사용자-facing 답변과 문서는 한국어로 작성합니다.
- 실제 비밀값은 커밋하지 않습니다. `.env`는 `.gitignore`에 포함되어 있고, 공유용 값은 `.env.example`에만 둡니다.
- DB 스키마 변경은 `src/main/resources/db/changelog/db.changelog-master.sql`의 Liquibase changeset으로 관리합니다.
- JPA 엔티티와 Liquibase 스키마가 어긋나지 않게 함께 확인합니다.
- pgvector 검색, 임베딩 upsert처럼 PostgreSQL 전용 기능은 `JdbcTemplate`/native SQL 쪽에서 다룹니다.
- ACL이 걸린 검색은 fail-closed가 기본입니다. principal이 없거나 비어 있으면 검색 결과가 없어야 합니다.
- 파일을 직접 수정할 때는 기존 패키지 경계와 테스트 스타일을 따릅니다.

## 중요한 문서

- 설계 스펙: `docs/superpowers/specs/2026-06-17-my-data-rag-chatbot-design.md`
- 구현 계획: `docs/superpowers/plans/2026-06-17-my-data-mvp-foundation.md`
- 실행 가이드: `README.md`
