import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  schema: '../../src/main/resources/graphql/admin.graphqls',
  documents: 'src/graphql/**/*.graphql',
  generates: {
    './src/generated/': {
      preset: 'client',
      config: {
        scalars: {
          ID: 'string',
          String: 'string'
        },
        useTypeImports: true
      }
    }
  }
};

export default config;
