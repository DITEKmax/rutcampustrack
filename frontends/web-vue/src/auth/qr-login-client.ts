import type { components } from '../../../mobile-core/src/api/generated/auth'
type IssueDto = components['schemas']['QrLoginIssueResponse']
type StatusDto = components['schemas']['QrLoginStatusResponse']
export type QrProof = {
  purpose: 'LOGIN'
  issuerId: string
  issuerSecret: string
  challengeId: string
}
export type QrChallenge = QrProof & {
  qrPayload: string
  expiresAt: number
  pollAfterSeconds: number
}
export type QrStatus = {
  status: 'pending' | 'confirmed' | 'rejected' | 'expired'
  expiresAt: number
  pollAfterSeconds: number
}
async function send(path: string, body: unknown, signal?: AbortSignal): Promise<unknown> {
  const response = await fetch('/api/auth/qr/' + path, {
    method: 'POST',
    credentials: 'include',
    cache: 'no-store',
    ...(signal ? { signal } : {}),
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok)
    throw new Error(
      response.status === 429
        ? 'Слишком много запросов. Попробуй позже.'
        : 'Не удалось проверить QR-вход. Обнови код или выбери другой способ входа.',
    )
  return response.json()
}
function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('Некорректный ответ QR-входа')
  return value as Record<string, unknown>
}
function text(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0
}
function expiry(value: unknown): number {
  if (!text(value) || !Number.isFinite(Date.parse(value)))
    throw new Error('Неизвестный срок QR-кода')
  return Date.parse(value)
}
function delay(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1 || value > 300)
    throw new Error('Некорректный интервал QR-входа')
  return value
}
function possession(proof: QrProof): QrProof {
  return {
    purpose: proof.purpose,
    issuerId: proof.issuerId,
    issuerSecret: proof.issuerSecret,
    challengeId: proof.challengeId,
  }
}
export const qrLoginClient = {
  async issue(previous?: QrProof, signal?: AbortSignal): Promise<QrChallenge> {
    const dto = record(
      await send(
        'challenges',
        previous
          ? { purpose: 'LOGIN', issuerId: previous.issuerId, issuerSecret: previous.issuerSecret }
          : { purpose: 'LOGIN' },
        signal,
      ),
    ) as IssueDto
    if (
      !text(dto.issuerId) ||
      !text(dto.issuerSecret) ||
      !text(dto.challengeId) ||
      !text(dto.qrPayload) ||
      dto.purpose !== 'LOGIN'
    )
      throw new Error('Некорректный QR-код сервера')
    return {
      purpose: 'LOGIN',
      issuerId: dto.issuerId,
      issuerSecret: dto.issuerSecret,
      challengeId: dto.challengeId,
      qrPayload: dto.qrPayload,
      expiresAt: expiry(dto.expiresAt),
      pollAfterSeconds: delay(dto.pollAfterSeconds),
    }
  },
  async status(proof: QrProof, signal?: AbortSignal): Promise<QrStatus> {
    const dto = record(await send('status', possession(proof), signal)) as StatusDto
    if (
      dto.challengeId !== proof.challengeId ||
      !['pending', 'confirmed', 'rejected', 'expired'].includes(dto.status ?? '')
    )
      throw new Error('Неизвестное состояние QR-входа')
    return {
      status: dto.status as QrStatus['status'],
      expiresAt: expiry(dto.expiresAt),
      pollAfterSeconds: delay(dto.pollAfterSeconds),
    }
  },
  async exchange(proof: QrProof, signal?: AbortSignal): Promise<{ accessToken: string }> {
    const dto = record(await send('exchange', possession(proof), signal))
    if (!text(dto.accessToken)) throw new Error('Некорректный ответ входа')
    return { accessToken: dto.accessToken }
  },
}
