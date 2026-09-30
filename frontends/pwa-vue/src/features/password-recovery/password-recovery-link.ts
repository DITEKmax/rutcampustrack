export type PasswordResetProof = {
  challengeId: string
  code: string
} | {
  resetTicket: string
  expiresAt: string
}

export function isPasswordResetEntryPath(pathname: string, baseUrl = '/'): boolean {
  const basePath = passwordRecoveryLoginPath(baseUrl).replace(/\/+$/, '')
  const resetPath = basePath + '/password-reset'
  return pathname === resetPath || pathname === resetPath + '/'
}

export function passwordRecoveryLoginPath(baseUrl = '/'): string {
  const path = new URL(baseUrl, 'https://rutcampustrack.invalid').pathname
  return path.endsWith('/') ? path : `${path}/`
}

/**
 * Reads a Telegram or admin recovery fragment and removes it from browser history
 * before returning the proof to the caller. A failed history update throws so
 * callers cannot accidentally send a code that remains visible in the URL.
 */
export function consumePasswordResetFragment(
  fragment: string,
  replaceUrl: () => void,
): PasswordResetProof | null {
  if (!fragment) return null

  const params = new URLSearchParams(fragment.startsWith('#') ? fragment.slice(1) : fragment)
  const keys = Array.from(params.keys())
  const challengeIds = params.getAll('challengeId')
  const codes = params.getAll('code')
  const tickets = params.getAll('resetTicket')
  const expiries = params.getAll('expiresAt')
  const adminProof = keys.length === 2
    && keys.every((key) => key === 'resetTicket' || key === 'expiresAt')
    && tickets.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(tickets[0] ?? '')
    && expiries.length === 1
    && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(expiries[0] ?? '')
    && Number.isFinite(Date.parse(expiries[0] ?? ''))
    && new Date(expiries[0]!).toISOString().slice(0, 19) === expiries[0]!.slice(0, 19)
    ? { resetTicket: tickets[0]!, expiresAt: expiries[0]! }
    : null
  const proof = adminProof ?? (keys.length === 2
    && keys.every((key) => key === 'challengeId' || key === 'code')
    && challengeIds.length === 1
    && codes.length === 1
    && challengeIds[0]?.trim().length
    && codes[0]?.trim().length
    ? { challengeId: challengeIds[0], code: codes[0] }
    : null)

  replaceUrl()
  return proof
}
