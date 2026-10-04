import { readFileSync } from 'node:fs'
import type { DepOptimizationOptions, ServerOptions } from 'vite'

// SockJS's browser crypto shim expects Node's global alias. Supply it only in
// Vite's dependency prebundle; application globals and release builds stay intact.
export const browserDependencyOptimization: DepOptimizationOptions = {
  esbuildOptions: { define: { global: 'globalThis' } },
}

/** Local HTTPS preview uses the existing stand without changing its CORS policy. */
export function developmentServer(
  env: Record<string, string>,
  port: number,
  apiTarget: string,
): ServerOptions {
  const certificate = env.RCT_DEV_TLS_CERT
  const privateKey = env.RCT_DEV_TLS_KEY
  if (Boolean(certificate) !== Boolean(privateKey)) {
    throw new Error('RCT_DEV_TLS_CERT and RCT_DEV_TLS_KEY must be configured together')
  }

  if (!certificate || !privateKey) {
    return {
      port,
      strictPort: true,
      proxy: { '/api': { target: apiTarget, changeOrigin: true, ws: true, secure: true } },
    }
  }

  const upstream = new URL(apiTarget)
  if (upstream.protocol !== 'https:' || !['127.0.0.1', 'localhost'].includes(upstream.hostname)) {
    throw new Error('The HTTPS development preview requires a local HTTPS API target')
  }
  const developmentOrigin = `https://127.0.0.1:${port}`

  return {
    host: '127.0.0.1',
    port,
    strictPort: true,
    https: { cert: readFileSync(certificate), key: readFileSync(privateKey) },
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        ws: true,
        secure: true,
        configure(proxy) {
          // Only this preview's browser origin is mapped. Foreign origins stay
          // intact so the gateway and notification server can reject them.
          proxy.on('proxyReq', (outgoing, incoming) => {
            if (incoming.headers.origin === developmentOrigin) {
              outgoing.setHeader('Origin', upstream.origin)
            }
          })
          proxy.on('proxyReqWs', (outgoing, incoming) => {
            if (incoming.headers.origin === developmentOrigin) {
              outgoing.setHeader('Origin', upstream.origin)
            }
          })
        },
      },
    },
  }
}
