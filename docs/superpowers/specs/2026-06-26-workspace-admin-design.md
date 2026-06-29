# Workspace Admin Design

## 목표

관리자가 유저별 워크스페이스를 관리할 수 있게 한다. 데이터소스 생성 시 UUID를 직접 찾지 않아도 되고, 새 유저나 기존 유저에게 기본 워크스페이스가 보장되어야 한다.

## 범위

- 워크스페이스 목록, 생성, 수정, 소프트 삭제, 복구
- 유저 생성 시 기본 `Personal` 워크스페이스 자동 생성
- 앱 시작 시 워크스페이스가 없는 active 유저에게 기본 `Personal` 워크스페이스 보정 생성
- 데이터소스 생성 폼은 active 워크스페이스만 선택지로 사용

## 제외

- 워크스페이스 멤버십 관리
- 워크스페이스 물리 삭제
- 삭제된 워크스페이스에 연결된 기존 데이터소스 정리

## 설계

`workspaces`에 `deleted_at`, `updated_at`을 추가한다. 기본 목록은 `deleted_at IS NULL`만 반환하고, 별도 restore mutation으로 복구한다. 생성/수정은 소유 유저가 active 상태인지 확인한다.

기본 워크스페이스는 서비스 계층에서 보장한다. 유저 생성 직후 `Personal`을 만들고, bootstrap으로 생성/복구된 관리자도 같은 규칙을 따른다. 시작 시 active 유저를 훑어 active 워크스페이스가 없는 유저에게만 `Personal`을 만든다.

관리자 UI에는 `워크스페이스` 메뉴를 추가한다. 생성/수정 폼은 이름과 소유 유저 select를 제공한다. 삭제/복구는 유저 관리 화면과 같은 패턴으로 처리한다.

## 테스트

- Liquibase/JPA schema validation
- GraphQL: 목록, 생성, 수정, soft delete, restore
- 유저 생성 시 기본 워크스페이스 생성
- bootstrap 보정 시 active 유저별 기본 워크스페이스 생성
- 프론트: 워크스페이스 목록/생성 흐름, 데이터소스 폼 select 유지
