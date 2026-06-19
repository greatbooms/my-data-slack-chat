# 에이전트 안내

이 폴더는 채팅 답변 흐름 테스트를 둡니다.

## 확인 포인트

- 질문과 답변 메시지가 저장됩니다.
- retrieval citation이 assistant 메시지에 연결됩니다.
- `externalThreadId`는 필수입니다.
- 동시 세션 생성 race는 `insertIfAbsent` 경로로 복구됩니다.
