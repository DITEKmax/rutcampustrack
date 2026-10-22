import type { App } from 'vue'
import { init, vueIntegration, globalHandlersIntegration } from '@sentry/vue'
import { safeErrorEvent } from './privacy'

export interface ErrorReportingConfig {
  dsn?: string
  release?: string
  environment?: string
}

/** Optional telemetry must never prevent mounting or a business operation. */
export function installErrorReporting(app: App, config: ErrorReportingConfig): void {
  if (!config.dsn) return
  try {
    const dsn = new URL(config.dsn)
    if (!['http:', 'https:'].includes(dsn.protocol)) return
    init({
      app,
      dsn: config.dsn,
      release: config.release,
      environment: config.environment ?? 'production',
      dataCollection: {
        userInfo: false,
        cookies: false,
        httpHeaders: false,
        httpBodies: [],
        urlQueryParams: false,
        stackFrameVariables: false,
        frameContextLines: 0,
      },
      defaultIntegrations: false,
      // Vue error handler and SDK capture only; no breadcrumbs, tracing, sessions or replay.
      integrations: [vueIntegration({ attachProps: false }), globalHandlersIntegration()],
      maxBreadcrumbs: 0,
      normalizeDepth: 2,
      sampleRate: 1,
      beforeSend: (event, hint) => {
        hint.attachments = []
        const safe = safeErrorEvent(event)
        return safe ? { ...safe, type: undefined, level: 'error' } : null
      },
      beforeBreadcrumb: () => null,
    })
  } catch {
    // No exception text is logged: SDK/configuration errors may contain the DSN.
  }
}
