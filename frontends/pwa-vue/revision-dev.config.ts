import { realpathSync } from 'node:fs'
import { defineConfig, loadEnv } from 'vite'
import baseConfig from './vite.config'
import { developmentServer } from '../dev-server'

export default defineConfig(async (context) => {
  const base = typeof baseConfig === 'function' ? await baseConfig(context) : await baseConfig
  const env = loadEnv(context.mode, '.', '')
  return { ...base, server: { ...developmentServer(env, 5185, env.VITE_API_PROXY_TARGET ?? 'https://127.0.0.1:18514'), host: '127.0.0.1', fs: { allow: ['..', realpathSync(new URL('../node_modules/@fontsource-variable/onest', import.meta.url))] }, } }
})
