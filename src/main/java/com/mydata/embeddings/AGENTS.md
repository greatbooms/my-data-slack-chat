# 에이전트 안내

이 패키지는 임베딩 생성과 저장을 담당합니다.

## 핵심 파일

- `EmbeddingClient`: 임베딩 클라이언트 계약
- `DeterministicEmbeddingClient`: 테스트/로컬용 결정적 1536차원 임베딩
- `DocumentEmbeddingRepository`: pgvector 저장/upsert

## 주의사항

- 현재 vector 차원은 1536입니다. 변경 시 Liquibase 스키마와 테스트를 함께 바꿔야 합니다.
- 실제 임베딩 API를 붙일 때는 비밀키를 환경변수나 secret backend로 관리합니다.
