import type { WebTransport } from '../../app/api'

export interface GeneratedCredentials {
  userId: string
  credentialsVersion: string
  state: 'GENERATED' | 'USER_CHANGED' | 'UNAVAILABLE'
  origin: 'initial' | 'headman_reset' | 'admin_reset' | null
  password: string | null
}
export class CredentialsError extends Error {
  constructor(
    readonly status: number | null,
    detail: string,
  ) {
    super(detail)
  }
}
export class CredentialsClient {
  constructor(private readonly transport: WebTransport) {}
  async read(userId: number): Promise<GeneratedCredentials> {
    return this.request(userId, 'initial-password')
  }
  async generate(
    userId: number,
    expectedCredentialsVersion: string,
  ): Promise<GeneratedCredentials> {
    return this.request(userId, 'generated-password', expectedCredentialsVersion)
  }
  private async request(
    userId: number,
    operation: string,
    version?: string,
  ): Promise<GeneratedCredentials> {
    const response = await this.transport.fetcher(`/api/auth/admin/users/${userId}/${operation}`, {
      method: version === undefined ? 'GET' : 'POST',
      cache: 'no-store',
      ...(version === undefined
        ? {}
        : {
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ expectedCredentialsVersion: version }),
          }),
    })
    let value: unknown = null
    try {
      value = await response.json()
    } catch {
      this.transport.assertCurrent()
      if (response.ok) throw new Error('Не удалось подтвердить данные пароля.')
    }
    this.transport.assertCurrent()
    if (!response.ok) {
      const detail =
        value && typeof value === 'object' && 'detail' in value && typeof value.detail === 'string'
          ? value.detail
          : null
      throw new CredentialsError(
        response.status,
        detail ?? `Не удалось получить пароль (HTTP ${response.status}).`,
      )
    }
    if (!value || typeof value !== 'object')
      throw new Error('Не удалось подтвердить данные пароля.')
    const v = value as Record<string, unknown>
    if (
      v.userId !== String(userId) ||
      typeof v.credentialsVersion !== 'string' ||
      !/^[1-9][0-9]*$/.test(v.credentialsVersion) ||
      !['GENERATED', 'USER_CHANGED', 'UNAVAILABLE'].includes(String(v.state)) ||
      (v.state === 'GENERATED'
        ? typeof v.password !== 'string' ||
          !v.password ||
          !['initial', 'headman_reset', 'admin_reset'].includes(String(v.origin))
        : v.password !== null || v.origin !== null)
    )
      throw new Error('Не удалось подтвердить данные пароля.')
    return v as unknown as GeneratedCredentials
  }
}
