import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Feature build proves the unrouted component is bundled, without test/preview fixtures.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: 'dist/product-management', emptyOutDir: false,
    lib: { entry: 'src/features/product/index.ts', formats: ['es'], fileName: 'product-management' },
    rollupOptions: { external: ['react', 'react/jsx-runtime'] },
  },
});
