// Generated only into new owner-local state. Real API/physical WS, no fixture transport.
import { defineConfig } from '__REPO__/frontends/node_modules/vite/dist/node/index.js'
import web from '__REPO__/frontends/web-vue/vite.config'
import pwa from '__REPO__/frontends/pwa-vue/vite.config'
import tma from '__REPO__/frontends/tma-vue/vite.config'
export default defineConfig(async environment => {
  const client = process.env.RCT_LOCAL_CLIENT
  if (!['web', 'pwa', 'tma'].includes(client ?? '')) throw new Error('One explicit local client required')
  if (process.env.VITE_MOBILE_FIXTURE_MODE !== 'false') throw new Error('Real transport required')
  const selected = client === 'web' ? web : client === 'pwa' ? pwa : tma
  const original = typeof selected === 'function' ? await selected(environment) : await selected
  return { ...original, cacheDir: `__STATE__/private/vite-${client}`, server: { ...original.server,
    host: '127.0.0.1', port: client === 'web' ? __PORT_WEB__ : client === 'pwa' ? __PORT_PWA__ : __PORT_TMA__,
    strictPort: true, https: undefined,
    fs: { ...original.server?.fs, deny: [...(original.server?.fs?.deny ?? ['.env', '.env.*', '*.{crt,pem}', '**/.git/**']), '**/.pr3-local/**'] },
    proxy: { '/api': { target: '__API_ORIGIN__', changeOrigin: true, ws: true, secure: true } } } }
})
