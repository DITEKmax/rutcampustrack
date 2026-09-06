import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv, type Plugin } from 'vite'

function publicBase(mode: string): string {
  const value = loadEnv(mode, '.', '').VITE_PUBLIC_BASE ?? '/'
  if (!value.startsWith('/') || !value.endsWith('/')) {
    throw new Error('VITE_PUBLIC_BASE must begin and end with /')
  }
  return value
}

function runtime(mode: string) {
  const env = loadEnv(mode, '.', '')
  return {
    apiTarget: env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
    serviceWorkerEnabled: mode === 'production',
  }
}

/**
 * `public/sw.js` is intentionally not bundled, so it cannot discover Vite's
 * hashed app files by itself. Emit its install-time list from the final bundle:
 * a successful service-worker install therefore means the app shell is cached.
 */
function appShellPrecachePlugin(): Plugin {
  return {
    name: 'rct-pwa-app-shell-precache',
    apply: 'build',
    generateBundle(_, bundle) {
      const generatedFiles = Object.values(bundle)
        .map((output) => output.fileName)
        .filter((fileName) => fileName !== 'sw.js' && fileName !== 'sw-assets.js')
        .sort()
      const precache = ['./', './manifest.webmanifest', ...generatedFiles.map((fileName) => `./${fileName}`)]
      this.emitFile({
        type: 'asset',
        fileName: 'sw-assets.js',
        source: `self.__RCT_PRECACHE_ASSETS__ = ${JSON.stringify(precache)};\n`,
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  const config = runtime(mode)
  const proxy = {
    '/api': {
      target: config.apiTarget,
      changeOrigin: true,
    },
  }
  return {
    base: publicBase(mode),
    plugins: [vue(), appShellPrecachePlugin()],
    css: {
      postcss: '../postcss.config.mjs',
    },
    define: {
      __RCT_SERVICE_WORKER_ENABLED__: config.serviceWorkerEnabled,
    },
    server: {
      port: 5175,
      strictPort: true,
      proxy,
    },
    preview: {
      port: 5175,
      strictPort: true,
      proxy,
    },
  }
})
