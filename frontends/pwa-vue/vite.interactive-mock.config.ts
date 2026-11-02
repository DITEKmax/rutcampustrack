import { readFileSync, realpathSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { defineConfig, searchForWorkspaceRoot } from 'vite'
import { browserDependencyOptimization } from '../dev-server'

/** Separate serve-only entry: never imported by release configuration. */
export default defineConfig(({ command }) => {
  if (command !== 'serve') throw new Error('Interactive PWA mock is dev-only.')
  const root = fileURLToPath(new URL('.', import.meta.url))
  return {
    root,
    cacheDir: '../../.agent/evidence/pwa-interactive-1103/vite-cache',
    plugins: [vue(), {
      name: 'rct-isolated-interactive-pwa',
      configureServer(server) {
        server.middlewares.use(async (request, response, next) => {
          const path = (request.url ?? '/').split('?')[0] ?? '/'
          if (path === '/api' || path.startsWith('/api/') || path === '/sw.js') {
            response.writeHead(501, { 'Content-Type': 'application/json' })
            response.end(JSON.stringify({ title: 'Локальный mock: реальный API и service worker отключены' }))
            return
          }
          if (request.method === 'GET' && request.headers.accept?.includes('text/html')) {
            try {
              const html = await server.transformIndexHtml('/interactive-mock.html',
                readFileSync(new URL('./interactive-mock.html', import.meta.url), 'utf8'))
              response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
              response.end(html)
            } catch (error) { next(error) }
            return
          }
          next()
        })
      },
    }],
    resolve: {
      alias: { '@rct/mobile-core': fileURLToPath(new URL('../mobile-core/src/index.ts', import.meta.url)) },
    },
    optimizeDeps: browserDependencyOptimization,
    css: { postcss: '../postcss.config.mjs' },
    define: {
      'import.meta.env.VITE_MOBILE_FIXTURE_MODE': JSON.stringify('false'),
      __RCT_SERVICE_WORKER_ENABLED__: false,
      __RCT_APP_VERSION__: JSON.stringify('0.0.0'),
      __RCT_MIN_SUPPORTED_VERSION__: JSON.stringify('0.0.0'),
      __RCT_FORCE_UPDATE__: false,
    },
    server: {
      host: '127.0.0.1', port: 14222, strictPort: true,
      headers: {
        'Cache-Control': 'no-store',
        'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' ws://127.0.0.1:14222; worker-src 'none'; object-src 'none'; frame-src 'self' blob:; form-action 'none'",
        'Permissions-Policy': 'geolocation=(), camera=(), microphone=()',
      },
      fs: { allow: [searchForWorkspaceRoot(root), realpathSync(new URL('../node_modules', import.meta.url))] },
    },
  }
})
