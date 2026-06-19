# 에이전트 안내

이 패키지는 내부 사용자 모델을 담당합니다.

## 핵심 파일

- `UserEntity`
- `UserRepository`

## 주의사항

- 사용자 ID는 workspace, identity, 권한 principal과 연결됩니다.
- 이메일 unique 제약과 엔티티 필드는 Liquibase 스키마와 맞춰야 합니다.
