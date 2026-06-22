# 에이전트 안내

이 폴더는 React, Vite, TypeScript 기반 관리자 화면입니다.

## 주요 명령

```bash
npm install
npm run codegen
npm run build
npm run dev
```

## 핵심 파일

- `codegen.ts`: Spring GraphQL 스키마와 operation에서 타입을 생성합니다.
- `src/graphql/admin.graphql`: 관리자 화면에서 사용하는 GraphQL operation입니다.
- `src/generated`: GraphQL Code Generator 산출물입니다. 직접 수정하지 않습니다.
- `vite.config.ts`: `/admin/auth/**`, `/admin/graphql` 개발 proxy와 `/admin-ui/` base path를 설정합니다.

## 주의사항

- 화면 컴포넌트에서 문자열 GraphQL query를 직접 만들지 않습니다.
- GraphQL operation을 바꾸면 `npm run codegen`을 다시 실행합니다.
- 관리자 화면은 조용하고 밀도 있는 운영 콘솔 톤을 유지합니다.
