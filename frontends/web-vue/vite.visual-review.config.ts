import { fileURLToPath } from 'node:url'
import { readFileSync, realpathSync } from 'node:fs'
import vue from '@vitejs/plugin-vue'
import { defineConfig, searchForWorkspaceRoot } from 'vite'
import { browserDependencyOptimization } from '../dev-server'

/** Explicit local-only launcher. The normal Vite config and release entry never import this. */
export default defineConfig(({ command }) => {
  if (command !== 'serve') throw new Error('Visual review is dev-only; use the normal config to build.')
  const root = fileURLToPath(new URL('.', import.meta.url))
  return {
    root,
    cacheDir: '../../.agent/evidence/web-visual-1102/vite-cache',
    plugins: [vue(), {
      name: 'rct-isolated-visual-review',
      configureServer(server) {
        server.middlewares.use(async (request, response, next) => {
          const path = (request.url ?? '/').split('?')[0] ?? '/'
          if (path === '/api' || path.startsWith('/api/')) {
            response.writeHead(501, { 'Content-Type': 'application/json' })
            response.end(JSON.stringify({ title: 'Visual preview: real API is disabled' }))
            return
          }
          if (request.method === 'GET' && request.headers.accept?.includes('text/html')) {
            try {
              const html = await server.transformIndexHtml(
                '/visual-review.html', readFileSync(new URL('./visual-review.html', import.meta.url), 'utf8'),
              )
              response.writeHead(200, {
                'Content-Type': 'text/html; charset=utf-8',
                'Cache-Control': 'no-store',
                'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self' ws://127.0.0.1:14220; object-src 'none'; form-action 'none'; frame-src 'none'",
                'Permissions-Policy': 'geolocation=(), camera=(), microphone=()',
              })
              response.end(html)
            } catch (error) { next(error) }
            return
          }
          next()
        })
      },
    }],
    optimizeDeps: browserDependencyOptimization,
    css: { postcss: '../postcss.config.mjs' },
    server: {
      host: '127.0.0.1', port: 14220, strictPort: true,
      fs: { allow: [searchForWorkspaceRoot(root), realpathSync(new URL('../node_modules', import.meta.url))] },
    },
  }
})
