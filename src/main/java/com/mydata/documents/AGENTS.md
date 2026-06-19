# 에이전트 안내

이 패키지는 수집된 외부 문서, 문서 ACL, 청크를 관리합니다.

## 핵심 모델

- `ExternalDocumentEntity`: 외부 문서 메타데이터와 content hash
- `DocumentAclEntryEntity`: 문서별 읽기 권한
- `DocumentChunkEntity`: 검색과 임베딩을 위한 문서 청크

## 주의사항

- 문서 ACL과 청크는 문서 변경 시 교체될 수 있습니다.
- 삭제/교체 동작은 cascade와 orphan 처리, repository flush 순서를 함께 고려합니다.
- 문서별 ACL은 retrieval fail-closed 보장의 핵심 데이터입니다.
