# 에이전트 안내

이 폴더는 운영 애플리케이션 코드와 리소스를 담습니다.

## 구조

- `java`: Spring Boot 애플리케이션 코드
- `resources`: 설정과 Liquibase changelog

## 주의사항

- 설정 변경은 README와 `.env.example`의 실행 안내도 함께 확인합니다.
- DB 관련 변경은 JPA 엔티티와 Liquibase changelog를 같이 맞춥니다.
