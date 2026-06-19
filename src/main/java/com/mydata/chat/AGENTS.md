# 에이전트 안내

이 패키지는 질문 답변 흐름과 채팅 기록 저장을 담당합니다.

## 핵심 흐름

1. 외부 thread id로 채팅 세션을 찾거나 생성합니다.
2. 사용자 메시지를 저장합니다.
3. ACL 필터가 적용된 retrieval을 호출합니다.
4. LLM 클라이언트로 답변을 생성합니다.
5. assistant 메시지와 citation을 저장합니다.

## 주의사항

- `externalThreadId`는 필수입니다. Slack에서는 `thread_ts ?: ts` 형태로 정규화하는 방향을 유지합니다.
- 세션 생성은 unique index race를 피하기 위해 `insertIfAbsent` 방식입니다.
- 실제 LLM 연결 전까지 `StubLlmClient`는 테스트/기반 구현용입니다.
