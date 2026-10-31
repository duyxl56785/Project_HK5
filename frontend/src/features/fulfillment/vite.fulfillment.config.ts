import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist/fulfillment', emptyOutDir: false,
    lib: { entry: 'src/features/fulfillment/index.ts', formats: ['es'], fileName: 'fulfillment' },
    rollupOptions: { external: ['react', 'react/jsx-runtime'] },
  },
});
