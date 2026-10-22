import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Build the unmounted feature too; the shared application entry does not import it yet.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist/category-browser',
    emptyOutDir: false,
    lib: { entry: 'src/features/catalog/index.ts', formats: ['es'], fileName: 'category-browser' },
    rollupOptions: { external: ['react', 'react/jsx-runtime'] },
  },
});
