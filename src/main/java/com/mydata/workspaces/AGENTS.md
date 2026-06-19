# 에이전트 안내

이 패키지는 워크스페이스 모델을 담당합니다.

## 핵심 파일

- `WorkspaceEntity`
- `WorkspaceRepository`

## 주의사항

- workspace id는 데이터소스, 문서, 수집 job, 검색 범위의 기준입니다.
- 멀티 사용자/멤버십 기능 확장 시 기존 ACL 모델과 함께 설계합니다.
