/** Incoming browser LOGIN proof. This capability stays in component memory only. */
export interface BrowserLoginProof {
  purpose: 'LOGIN'
  challengeId: string
  approvalToken: string
}
export type BrowserLoginState = 'pending' | 'confirmed' | 'rejected' | 'expired'
export interface BrowserLoginPreview {
  challengeId: string
  purpose: 'LOGIN'
  status: BrowserLoginState
  browserLabel: string
  issuedAt: string
  expiresAt: string
  warning: string
}
export interface BrowserLoginStatus {
  challengeId: string
  status: BrowserLoginState
  expiresAt: string
}
export interface BrowserLoginApprovalPort {
  isOnline(): boolean
  preview(proof: BrowserLoginProof): Promise<BrowserLoginPreview>
  decide(proof: BrowserLoginProof, decision: 'CONFIRM' | 'REJECT'): Promise<BrowserLoginStatus>
}
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u
function invalid(): never {
  throw new Error('Данные входа в браузере некорректны')
}
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return invalid()
  return value as Record<string, unknown>
}
function text(value: unknown, maximum = 512): string {
  if (typeof value !== 'string' || !value.trim() || value.length > maximum) return invalid()
  return value
}
function date(value: unknown): string {
  const result = text(value, 64)
  if (!/^\d{4}-\d{2}-\d{2}T/u.test(result) || !Number.isFinite(Date.parse(result))) return invalid()
  return result
}
function state(value: unknown): BrowserLoginState {
  if (value === 'pending' || value === 'confirmed' || value === 'rejected' || value === 'expired')
    return value
  return invalid()
}
export function parseBrowserLoginProof(input: string): BrowserLoginProof {
  if (!input || input.length > 1024) return invalid()
  let parsed: unknown
  try {
    parsed = JSON.parse(input)
  } catch {
    return invalid()
  }
  const value = record(parsed)
  if (
    Object.keys(value).sort().join(',') !== 'approvalToken,challengeId,purpose,version' ||
    value.version !== 1 ||
    value.purpose !== 'LOGIN' ||
    typeof value.challengeId !== 'string' ||
    !uuid.test(value.challengeId) ||
    typeof value.approvalToken !== 'string' ||
    !/^[A-Za-z0-9_-]{43}$/u.test(value.approvalToken)
  )
    return invalid()
  return { purpose: 'LOGIN', challengeId: value.challengeId, approvalToken: value.approvalToken }
}
export function decodeBrowserLoginPreview(
  input: unknown,
  proof: BrowserLoginProof,
): BrowserLoginPreview {
  const value = record(input)
  if (value.challengeId !== proof.challengeId || value.purpose !== 'LOGIN') return invalid()
  return {
    challengeId: proof.challengeId,
    purpose: 'LOGIN',
    status: state(value.status),
    browserLabel: text(value.browserLabel),
    issuedAt: date(value.issuedAt),
    expiresAt: date(value.expiresAt),
    warning: text(value.warning, 2048),
  }
}
export function decodeBrowserLoginStatus(
  input: unknown,
  proof: BrowserLoginProof,
): BrowserLoginStatus {
  const value = record(input)
  if (value.challengeId !== proof.challengeId) return invalid()
  return {
    challengeId: proof.challengeId,
    status: state(value.status),
    expiresAt: date(value.expiresAt),
  }
}
