import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist/notifications', emptyOutDir: false,
    lib: { entry: 'src/features/notifications/index.ts', formats: ['es'], fileName: 'notifications' },
    rollupOptions: { external: ['react', 'react/jsx-runtime'] },
  },
});
