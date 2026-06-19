# 에이전트 안내

이 폴더는 운영 코드의 핵심 패키지입니다.

## 모듈 지도

- `admin`: 관리자 API와 토큰 인증
- `auth`: 권한 principal과 permission 값
- `chat`: 질문 답변 orchestration, 메시지, 출처 저장
- `common`: 공통 JPA/JSON 유틸리티
- `connectors`: 외부 데이터소스 커넥터 계약과 구현
- `datasources`: 데이터소스 엔티티와 설정
- `documents`: 외부 문서, ACL, 청크 모델
- `embeddings`: 임베딩 클라이언트와 저장소
- `ingestion`: 수집 job, worker, pipeline
- `retrieval`: ACL 필터가 적용된 벡터 검색
- `security`: 애플리케이션 보안 설정 검증
- `slackbot`: Slack 이벤트 수신과 서명 검증
- `users`, `workspaces`: 사용자와 워크스페이스 모델

## 주의사항

- 모듈 간 순환 의존을 만들지 않습니다.
- 권한 필터링은 검색 계층에서 빠지면 안 됩니다.
- Slack 요청은 raw body 기준 서명 검증을 먼저 수행해야 합니다.
