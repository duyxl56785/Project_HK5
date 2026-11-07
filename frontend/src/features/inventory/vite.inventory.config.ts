import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist/inventory-dashboard', emptyOutDir: false,
    lib: { entry: 'src/features/inventory/index.ts', formats: ['es'], fileName: 'inventory-dashboard' },
    rollupOptions: { external: ['react', 'react/jsx-runtime'] },
  },
});
