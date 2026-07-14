/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // Sprint 10.185 (privacy/consent hardening): unit + integration tests for the analytics-consent layer
  // (consent gating, GA cookie cleanup, legal/analytics guards). jsdom gives document/localStorage/cookies.
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    globals: true,
    restoreMocks: true
  },
  server: {
    port: 5173,
    host: true
  },
  build: {
    rollupOptions: {
      output: {
        // Sprint 10.76 (perf): split React into a stable `vendor` chunk so it stays cached across app-only
        // deploys (the app ships frequently; framework bytes shouldn't re-download every time).
        manualChunks: {
          vendor: ['react', 'react-dom']
        }
      }
    }
  }
});
