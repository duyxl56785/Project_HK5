import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: { outDir: 'dist/tv5-finance-preview', emptyOutDir: false, rollupOptions: { input: 'src/features/finance/preview/index.html' } },
});
