import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3003,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq, req) => {
            console.log('[VITE PROXY]', req.method, req.url, '->', proxyReq.path)
          })
          proxy.on('proxyRes', (proxyRes, req) => {
            console.log('[VITE PROXY]', req.method, req.url, '->', proxyRes.statusCode)
          })
          proxy.on('error', (err, req) => {
            console.error('[VITE PROXY ERROR]', req.method, req.url, err.message)
          })
        },
      },
      '/fyers': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
