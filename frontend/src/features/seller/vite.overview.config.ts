import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist/seller-store-overview', emptyOutDir: false,
    lib: { entry: 'src/features/seller/index.ts', formats: ['es'], fileName: 'seller-store-overview' },
    rollupOptions: { external: ['react', 'react/jsx-runtime'] },
  },
});
