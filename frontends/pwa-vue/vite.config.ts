import { readFileSync } from 'node:fs'
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
    appVersion: env.VITE_APP_VERSION ?? '0.0.0',
    minimumSupportedVersion: env.VITE_MIN_SUPPORTED_VERSION || env.VITE_APP_VERSION || '0.0.0',
    forceUpdate: (env.VITE_FORCE_UPDATE ?? 'true').toLowerCase() !== 'false',
  }
}

function versionPolicyPlugin(config: ReturnType<typeof runtime>): Plugin {
  return {
    name: 'rct-pwa-version-policy',
    apply: 'build',
    generateBundle(_, bundle) {
      const publicWorker = readFileSync(new URL('./public/sw.js', import.meta.url), 'utf8')
      const workerMarker = "const releaseVersion = '__RCT_PWA_RELEASE__'"
      const versionedWorker = publicWorker.replace(
        workerMarker,
        `const releaseVersion = ${JSON.stringify(config.appVersion)}`,
      )
      if (versionedWorker === publicWorker) {
        throw new Error('PWA service worker release marker is missing')
      }
      this.emitFile({
        type: 'asset',
        fileName: 'sw.js',
        source: versionedWorker,
      })
      this.emitFile({
        type: 'asset',
        fileName: 'version.json',
        source: `${JSON.stringify({
          latest: config.appVersion,
          minimumSupported: config.minimumSupportedVersion,
          force: config.forceUpdate,
          message: 'Доступна новая версия RutTrack. Обнови приложение, чтобы продолжить.',
        }, null, 2)}\n`,
      })
    },
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
        .filter((fileName) => fileName !== 'sw.js' && fileName !== 'sw-assets.js' && fileName !== 'version.json')
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
    plugins: [vue(), versionPolicyPlugin(config), appShellPrecachePlugin()],
    css: {
      postcss: '../postcss.config.mjs',
    },
    define: {
      __RCT_SERVICE_WORKER_ENABLED__: config.serviceWorkerEnabled,
      __RCT_APP_VERSION__: JSON.stringify(config.appVersion),
      __RCT_MIN_SUPPORTED_VERSION__: JSON.stringify(config.minimumSupportedVersion),
      __RCT_FORCE_UPDATE__: config.forceUpdate,
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
