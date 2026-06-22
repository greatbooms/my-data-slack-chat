import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  base: '/admin-ui/',
  plugins: [react()],
  test: {
    environment: 'jsdom'
  },
  server: {
    port: 61263,
    proxy: {
      '/admin/auth': 'http://localhost:50506',
      '/admin/graphql': 'http://localhost:50506'
    }
  }
});
