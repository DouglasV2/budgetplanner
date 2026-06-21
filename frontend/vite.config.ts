import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
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
