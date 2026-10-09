/** Public PWA flow on the configured same-origin /app mount; no session context. */
export function publicPasswordRecoveryUrl(origin: string): string {
  return new URL('/app/password-reset', origin).href
}
