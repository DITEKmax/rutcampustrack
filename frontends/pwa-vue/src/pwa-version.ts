declare const __RCT_APP_VERSION__: string
declare const __RCT_MIN_SUPPORTED_VERSION__: string
declare const __RCT_FORCE_UPDATE__: boolean

export const APP_VERSION = __RCT_APP_VERSION__
export const MIN_SUPPORTED_VERSION = __RCT_MIN_SUPPORTED_VERSION__
export const FORCE_UPDATE = __RCT_FORCE_UPDATE__
export const VERSION_POLICY_URL = `${import.meta.env.BASE_URL}version.json`
export const APP_UPDATE_REQUIRED_EVENT = 'rct:pwa-update-required'

export type PwaUpdateRequiredReason = 'api' | 'policy'

export interface VersionPolicy {
  latest?: string
  minimumSupported?: string
  force?: boolean
  message?: string
}

export interface PwaUpdateRequiredDetail {
  reason: PwaUpdateRequiredReason
  latest?: string
  minimumSupported?: string
  message?: string
}

export function createPwaFetcher(baseFetcher?: typeof fetch): typeof fetch {
  const fetcher = baseFetcher ?? ((input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init))
  return async (input, init) => {
    const headers = new Headers(input instanceof Request ? input.headers : undefined)
    new Headers(init?.headers).forEach((value, key) => headers.set(key, value))
    headers.set('X-PWA-Version', APP_VERSION)
    const response = await fetcher(input, { ...init, headers })
    if (response.status === 426) void notifyUpdateRequired(response)
    return response
  }
}

export function isUpdateRequiredByPolicy(appVersion: string, policy: VersionPolicy): boolean {
  if (policy.force && policy.latest) return appVersion !== policy.latest
  if (policy.minimumSupported) return compareVersions(appVersion, policy.minimumSupported) < 0
  return false
}

async function notifyUpdateRequired(response: Response): Promise<void> {
  if (typeof window === 'undefined') return

  const detail: PwaUpdateRequiredDetail = {
    reason: 'api',
    ...(response.headers.get('X-PWA-Latest-Version') ? { latest: response.headers.get('X-PWA-Latest-Version')! } : {}),
    ...(response.headers.get('X-PWA-Minimum-Supported-Version')
      ? { minimumSupported: response.headers.get('X-PWA-Minimum-Supported-Version')! }
      : {}),
  }
  try {
    const body = await response.clone().json() as unknown
    if (isRecord(body)) {
      const extras = isRecord(body.extras) ? body.extras : null
      const latest = stringValue(body.latest) ?? stringValue(extras?.latest)
      const minimumSupported = stringValue(body.minimumSupported) ?? stringValue(extras?.minimumSupported)
      const message = stringValue(body.detail)
      if (latest) detail.latest = latest
      if (minimumSupported) detail.minimumSupported = minimumSupported
      if (message) detail.message = message
    }
  } catch {
    // The status and headers are sufficient to hard-block the old client.
  }
  window.dispatchEvent(new CustomEvent<PwaUpdateRequiredDetail>(APP_UPDATE_REQUIRED_EVENT, { detail }))
}

function compareVersions(left: string, right: string): number {
  const a = parseVersion(left)
  const b = parseVersion(right)
  const length = Math.max(a.length, b.length)
  for (let index = 0; index < length; index += 1) {
    const av = a[index] ?? 0
    const bv = b[index] ?? 0
    if (av !== bv) return av > bv ? 1 : -1
  }
  return 0
}

function parseVersion(value: string): number[] {
  return value.split(/[.+-]/).map((part) => {
    const parsed = Number.parseInt(part, 10)
    return Number.isFinite(parsed) ? parsed : 0
  })
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined
}
