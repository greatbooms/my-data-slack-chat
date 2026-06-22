# 에이전트 안내

이 폴더는 Spring GraphQL 스키마 파일을 둡니다.

## 핵심 파일

- `admin.graphqls`: 관리자 화면에서 사용하는 query, mutation, type, input, enum 정의

## 주의사항

- 스키마 enum 이름은 Java enum 상수와 일치해야 합니다.
- GraphQL field 이름과 `AdminGraphQlController` mapping 이름이 어긋나면 애플리케이션 부팅 시 스키마 검사가 실패할 수 있습니다.
- 관리자 mutation은 Spring Security 설정에서 CSRF와 관리자 세션 보호를 받는다는 전제로 설계합니다.
