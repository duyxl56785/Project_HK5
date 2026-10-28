import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist/fulfillment-preview',
    emptyOutDir: false,
    rollupOptions: { input: 'src/features/fulfillment/preview/index.html' },
  },
});
