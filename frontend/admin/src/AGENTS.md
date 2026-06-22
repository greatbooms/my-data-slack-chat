# 에이전트 안내

이 폴더는 관리자 React 앱의 소스 코드입니다.

## 구조

- `App.tsx`: 관리자 앱 셸과 기본 라우팅 뼈대
- `api`: GraphQL 호출 클라이언트
- `graphql`: Codegen 입력 operation
- `generated`: Codegen 산출물

## 주의사항

- API 응답 타입은 `generated`의 타입을 사용합니다.
- 시각 스타일은 `App.css`에 모으고, 컴포넌트가 과하게 커지면 기능별 폴더로 분리합니다.
