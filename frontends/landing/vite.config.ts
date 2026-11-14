import { fileURLToPath } from 'node:url'
import { realpathSync } from 'node:fs'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { discovery } from './discovery'
import { topics } from './src/topics'
import { browserDependencyOptimization } from '../dev-server'

const location = (path: string) => fileURLToPath(new URL(path, import.meta.url))
const frontends = location('../')

export default defineConfig({
  base: '/presentation/',
  cacheDir: location('./.vite-landing'),
  plugins: [vue(), discovery(), {
    name: 'rct-landing-demo-hmr',
    apply: 'serve',
    transformIndexHtml(html, context) {
      return context.path === '/demo-pwa.html' || context.path === '/presentation/demo-pwa.html'
        ? html.replace('connect-src blob:', 'connect-src blob: ws://127.0.0.1:14312')
        : html
    },
  }],
  optimizeDeps: browserDependencyOptimization,
  resolve: {
    alias: { '@rct/mobile-core': location('../mobile-core/src/index.ts') },
  },
  define: {
    'import.meta.env.VITE_MOBILE_FIXTURE_MODE': JSON.stringify('false'),
    __RCT_SERVICE_WORKER_ENABLED__: false,
    __RCT_APP_VERSION__: JSON.stringify('0.0.0'),
    __RCT_MIN_SUPPORTED_VERSION__: JSON.stringify('0.0.0'),
    __RCT_FORCE_UPDATE__: false,
  },
  css: { postcss: frontends },
  server: {
    host: '127.0.0.1',
    port: 14312,
    strictPort: true,
    fs: {
      allow: [frontends, realpathSync(location('../node_modules'))],
    },
  },
  preview: { host: '127.0.0.1', port: 14312, strictPort: true },
  build: {
    outDir: 'dist-vue',
    // Every filename is an explicit page or asset; dev and preview should also return 404 for unknown URLs.
    assetsInlineLimit: (path) => (/\.woff2?$/.test(path) ? false : undefined),
    rollupOptions: {
      input: {
        presentation: location('./index.html'),
        phone: location('./phone.html'),
        demoPwa: location('./demo-pwa.html'),
        ...Object.fromEntries(topics.map(({ slug }) => [slug, location('./' + slug + '/index.html')])),
      },
    },
  },
  appType: 'mpa',
})
