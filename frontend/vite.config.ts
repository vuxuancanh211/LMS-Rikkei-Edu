import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// base: './' makes the production build work from any sub-path.
export default defineConfig({
  plugins: [react()],
  base: './',
  server: { port: 5173, open: true },
});
