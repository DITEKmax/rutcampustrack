export type PasswordResetProof = {
  challengeId: string
  code: string
}

export function isPasswordResetEntryPath(pathname: string, baseUrl = '/'): boolean {
  const basePath = new URL(baseUrl, 'https://rutcampustrack.invalid').pathname.replace(/\/+$/, '')
  const resetPath = basePath + '/password-reset'
  return pathname === resetPath || pathname === resetPath + '/'
}

/**
 * Reads a Telegram deep-link fragment and removes it from browser history
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
  const proof = keys.length === 2
    && keys.every((key) => key === 'challengeId' || key === 'code')
    && challengeIds.length === 1
    && codes.length === 1
    && challengeIds[0]?.trim().length
    && codes[0]?.trim().length
    ? { challengeId: challengeIds[0], code: codes[0] }
    : null

  replaceUrl()
  return proof
}