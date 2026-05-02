export const APP_VERSION = __APP_VERSION__
export const APP_UPDATE_REQUIRED_EVENT = 'ruttrack:pwa-update-required'
export const VERSION_POLICY_URL = `${import.meta.env.BASE_URL}version.json`

export interface VersionPolicy {
  latest?: string
  minimumSupported?: string
  force?: boolean
  message?: string
}

export interface UpdateRequiredEventDetail {
  latest?: string
  minimumSupported?: string
  message?: string
}

function parseVersion(version: string): number[] {
  return version
    .split(/[.+-]/)
    .map((part) => Number.parseInt(part, 10))
    .map((part) => (Number.isFinite(part) ? part : 0))
}

export function compareVersions(left: string, right: string): number {
  const a = parseVersion(left)
  const b = parseVersion(right)
  const length = Math.max(a.length, b.length)

  for (let index = 0; index < length; index += 1) {
    const av = a[index] ?? 0
    const bv = b[index] ?? 0
    if (av > bv) return 1
    if (av < bv) return -1
  }
  return 0
}

export function isVersionOlder(current: string, target?: string): boolean {
  if (!target) return false
  return compareVersions(current, target) < 0
}
