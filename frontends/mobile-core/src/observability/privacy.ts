/** Error content is untrusted: reconstruct the export from a small structural allowlist. */
export interface SafeErrorEvent {
  event_id?: string
  timestamp?: number
  platform?: string
  level?: string
  release?: string
  environment?: string
  exception?: { values?: SafeException[] }
}
interface SafeException {
  type?: string
  value?: string
  stacktrace?: { frames?: SafeFrame[] }
}
interface SafeFrame {
  filename?: string
  function?: string
  lineno?: number
  colno?: number
  in_app?: boolean
}
const identifier = /^[A-Za-z_$][A-Za-z0-9_.$<> /:-]{0,159}$/
const release = /^(?:[0-9a-f]{7,40}|[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]{1,40})?)$/

export function safeErrorEvent(event: SafeErrorEvent): SafeErrorEvent | null {
  const values = event.exception?.values?.slice(0, 4).map((exception) => ({
    type: exception.type && identifier.test(exception.type) ? exception.type : 'Error',
    value: '[redacted]',
    stacktrace: {
      frames:
        exception.stacktrace?.frames?.slice(-64).map((frame) => {
          const safe: SafeFrame = {}
          // Only generated bundle basenames; no raw URL, query, source text or local path.
          const file = frame.filename?.split(/[?#]/)[0]?.split('/').filter(Boolean)
          const filename = file?.[file.length - 1]
          if (filename && /^[A-Za-z0-9_.-]{1,120}\.(?:js|ts|vue)$/.test(filename)) {
            safe.filename = filename
          }
          if (frame.function && identifier.test(frame.function)) safe.function = frame.function
          if (Number.isInteger(frame.lineno) && frame.lineno! > 0 && frame.lineno! <= 1e7) {
            safe.lineno = frame.lineno!
          }
          if (Number.isInteger(frame.colno) && frame.colno! > 0 && frame.colno! <= 1e7) {
            safe.colno = frame.colno!
          }
          safe.in_app = true
          return safe
        }) ?? [],
    },
  }))
  if (!values?.length) return null
  const safe: SafeErrorEvent = { platform: 'javascript', level: 'error', exception: { values } }
  if (event.event_id && /^[a-f0-9]{32}$/.test(event.event_id)) safe.event_id = event.event_id
  if (Number.isFinite(event.timestamp)) safe.timestamp = event.timestamp!
  if (event.release && release.test(event.release)) safe.release = event.release
  if (
    event.environment &&
    ['production', 'development', 'isolated-ci'].includes(event.environment)
  ) {
    safe.environment = event.environment
  }
  return safe
}
