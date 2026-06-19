# 에이전트 안내

이 패키지는 데이터 수집 job과 문서 처리 pipeline을 담당합니다.

## 핵심 흐름

1. `IngestionCommandService`가 수집 job을 생성합니다.
2. `IngestionWorker`가 커넥터를 찾아 raw 문서를 가져옵니다.
3. `IngestionPipelineService`가 문서/ACL/청크/임베딩을 저장합니다.

## 주의사항

- worker는 실패 job 상태를 별도 트랜잭션 경로로 기록합니다.
- pipeline 직접 호출은 `@Transactional` 경계를 가집니다.
- ACL 검증은 문서 쓰기 전에 수행되어야 rollback-safe합니다.
- 같은 문서가 변경되지 않았으면 필요한 임베딩만 backfill합니다.
