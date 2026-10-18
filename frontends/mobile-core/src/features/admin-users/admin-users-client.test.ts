import { describe, expect, it } from 'vitest'
import { IdentityOperationError } from '../../shared/identity-operation'
import type { IdentityRecoveryReference } from './admin-users-client'
import { AdminUsersClient } from './admin-users-client'

const userResponse = {
  id: 42,
  login: 'ivanov.i',
  lastName: 'Иванов',
  firstName: 'Иван',
  middleName: null,
  fullName: 'Иванов Иван',
  role: 'STUDENT',
  status: 'ACTIVE',
  groupId: 7,
  employeeNumber: null,
  telegramId: 123456789,
  createdAt: '2026-09-01T10:00:00Z',
  roles: [],
}

describe('AdminUsersClient.updateProfile', () => {
  it('sends only supplied profile properties as PATCH and preserves empty-string clearing', async () => {
    let capturedPath = ''
    let capturedInit: RequestInit | undefined
    const client = new AdminUsersClient({
      accessToken: () => 'admin-token',
      fetcher: async (input, init) => {
        capturedPath = String(input)
        capturedInit = init
        return new Response(JSON.stringify(userResponse), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      },
    })

    await expect(
      client.updateProfile(42, { middleName: '', employeeNumber: '' }),
    ).resolves.toMatchObject({ id: 42, login: 'ivanov.i' })

    expect(capturedPath).toBe('/api/academic/users/42')
    expect(capturedInit?.method).toBe('PATCH')
    expect(JSON.parse(String(capturedInit?.body))).toEqual({ middleName: '', employeeNumber: '' })
    expect(JSON.parse(String(capturedInit?.body))).not.toHaveProperty('role')
    expect(JSON.parse(String(capturedInit?.body))).not.toHaveProperty('groupId')
  })

  it('binds only the supplied positive Telegram ID without replacing unrelated profile fields', async () => {
    const payloads: unknown[] = []
    const client = new AdminUsersClient({
      accessToken: () => 'admin-token',
      fetcher: async (_input, init) => {
        payloads.push(JSON.parse(String(init?.body)))
        return new Response(
          JSON.stringify({ ...userResponse, role: 'TEACHER', telegramId: 987654321 }),
          { status: 200 },
        )
      },
    })
    await expect(client.updateProfile(42, { telegramId: 987654321 })).resolves.toMatchObject({
      id: 42,
      telegramId: 987654321,
    })
    expect(payloads).toEqual([{ telegramId: 987654321 }])
    for (const telegramId of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1])
      expect(() => client.updateProfile(42, { telegramId })).toThrow()
    expect(payloads).toHaveLength(1)
  })

  it('trims required names and rejects an empty or overlong required name', async () => {
    const client = new AdminUsersClient({
      accessToken: () => null,
      fetcher: async () => new Response(JSON.stringify(userResponse), { status: 200 }),
    })

    await expect(client.updateProfile(42, { firstName: ' Иван ' })).resolves.toMatchObject({
      id: 42,
    })
    expect(() => client.updateProfile(42, { firstName: '  ' })).toThrow(
      'firstName не может быть пустым',
    )
    expect(() => client.updateProfile(42, { lastName: 'я'.repeat(129) })).toThrow(
      'lastName не может быть длиннее 128 символов',
    )
  })
})

describe('AdminUsersClient credential and lifecycle safeguards', () => {
  it('reads only returned detail credentials and clears their projection from list/profile responses', async () => {
    const client = new AdminUsersClient({
      accessToken: () => null,
      fetcher: async (input, init) =>
        new Response(
          JSON.stringify(
            String(input).includes('?')
              ? [{ ...userResponse, initialPassword: 'synthetic-only' }]
              : {
                  ...userResponse,
                  initialPassword: init?.method === 'PATCH' ? null : 'synthetic-only',
                },
          ),
          { status: 200 },
        ),
    })
    expect((await client.getUser(42)).initialPassword).toBe('synthetic-only')
    expect((await client.listUsers()).items[0]?.initialPassword).toBeNull()
    expect((await client.updateProfile(42, { firstName: 'Иван' })).initialPassword).toBeNull()
  })
  it('rejects a different archive preview target and never submits it', async () => {
    let calls = 0
    const client = new AdminUsersClient({
      accessToken: () => null,
      fetcher: async () => {
        calls++
        return new Response(
          JSON.stringify({
            userId: 43,
            previewDigest: 'a'.repeat(64),
            expiresAt: new Date().toISOString(),
          }),
          { status: 200 },
        )
      },
    })
    await expect(client.previewArchive(42)).rejects.toThrow(
      'Не удалось подтвердить данные пользователя.',
    )
    expect(calls).toBe(1)
  })
  it('allows an exact protected lifecycle retry without generating a replacement operation id', async () => {
    const bodies: string[] = []
    const client = new AdminUsersClient({
      accessToken: () => null,
      fetcher: async (path, init) => {
        if (String(path).includes('/identity-operations/'))
          return new Response(null, { status: 404 })
        bodies.push(String(init?.body))
        if (bodies.length === 1) throw new TypeError('lost-response')
        return new Response(null, { status: 204 })
      },
    })
    const input = {
      operationId: '7cab6f3a-cd8f-4b99-a12c-8eb7c341d112',
      previewDigest: 'a'.repeat(64),
      password: 'synthetic-only',
    }
    await expect(client.archiveUser(42, input)).rejects.toMatchObject({
      state: 'UNKNOWN',
    })
    await client.archiveUser(42, input)
    expect(bodies).toHaveLength(2)
    expect(bodies[0]).toBe(bodies[1])
  })
})

function recoveryReceipt(id: string, status: string): Response {
  return new Response(JSON.stringify({ operationId: id, status, retryAfterSeconds: 0 }), {
    status: status === 'PENDING' ? 202 : 200,
    headers: { 'X-Operation-Id': id, Location: `/academic/identity-operations/${id}` },
  })
}
const recoveryPreview = {
  userId: 42,
  linkedGroupCount: 1,
  attendanceMarksCount: 2,
  activeHeadmanGroupCount: 1,
  soleTeacherAssignmentCount: 0,
  requiresPassword: true,
  previewDigest: 'a'.repeat(64),
  expiresAt: '2099-01-01T00:00:00Z',
}
describe('User FAILED_SAFE reconciliation', () => {
  it('captures a terminal reference and explicitly reconciles with a fresh key and empty body', async () => {
    const calls: { path: string; init: RequestInit | undefined }[] = []
    let originalId = '',
      persisted: IdentityRecoveryReference | null = null
    const client = new AdminUsersClient({
      accessToken: () => null,
      fetcher: async (input, init) => {
        const path = String(input)
        calls.push({ path, init })
        const key = new Headers(init?.headers).get('Idempotency-Key')!
        if (path.endsWith('/reconcile')) {
          expect(persisted?.reconcileId).toBe(key) // recorded before the send
          return recoveryReceipt(key, 'SUCCEEDED')
        }
        if (path.includes('/identity-operations/'))
          return recoveryReceipt(originalId, 'FAILED_SAFE')
        originalId = key
        return recoveryReceipt(key, 'PENDING')
      },
    })
    client.bindIdentityRecovery('actor-session-authority', null, (value) => {
      persisted = value
    })
    await expect(
      client.updateProfile(42, { telegramId: 123 }, new AbortController().signal),
    ).rejects.toMatchObject({ state: 'FAILED_SAFE', operationId: originalId || expect.any(String) })
    expect(client.failedIdentityOperation).toMatchObject({
      operationId: originalId,
      kind: 'USER',
      userId: 42,
      reconcileId: null,
    })
    await expect(client.updateProfile(42, { telegramId: 123 })).rejects.toThrow(
      'Сначала восстанови',
    )
    await client.confirmIdentityRecovery()
    await client.reconcileIdentityOperation(undefined, new AbortController().signal)
    const recovery = calls.find((c) => c.path.endsWith('/reconcile'))!
    expect(recovery.init?.body).toBeUndefined()
    expect(new Headers(recovery.init?.headers).get('Idempotency-Key')).not.toBe(originalId)
    const resumed = await client.resumeIdentityOperation()
    expect(resumed).toEqual({ user: null, created: false, recovered: true })
    expect(calls.filter((c) => c.path === '/api/academic/users/42')).toHaveLength(1)
    client.acknowledgeIdentityOperation()
    expect(persisted).toBeNull()
  })
  it('rehydrates archive recovery status first after lost response, without persisting or replaying its password', async () => {
    let persisted: IdentityRecoveryReference | null = null,
      originalId = '',
      newId = '',
      lose = true,
      status404 = true
    const calls: { path: string; init: RequestInit | undefined }[] = []
    const fetcher: typeof fetch = async (input, init) => {
      const path = String(input)
      calls.push({ path, init })
      const key = new Headers(init?.headers).get('Idempotency-Key')!
      if (path.endsWith('/reconcile-preview')) return new Response(JSON.stringify(recoveryPreview))
      if (path.endsWith('/reconcile')) {
        expect(persisted?.reconcileId).toBe(key)
        newId = key
        if (lose) {
          lose = false
          throw new TypeError('lost acceptance')
        }
        return recoveryReceipt(key, 'SUCCEEDED')
      }
      if (path.endsWith('/' + newId) && newId)
        return status404 ? new Response('{}', { status: 404 }) : recoveryReceipt(newId, 'SUCCEEDED')
      if (path.includes('/identity-operations/')) return recoveryReceipt(originalId, 'FAILED_SAFE')
      originalId = key
      return recoveryReceipt(key, 'PENDING')
    }
    const options = { accessToken: () => null, sessionScopeFor: () => 'same-session', fetcher }
    const persist = (value: IdentityRecoveryReference | null) => {
      persisted = value
    }
    const first = new AdminUsersClient(options)
    first.bindIdentityRecovery('same-session-authority', null, persist)
    await expect(
      first.archiveUser(42, {
        operationId: crypto.randomUUID(),
        previewDigest: 'b'.repeat(64),
        password: 'original-proof',
      }),
    ).rejects.toBeInstanceOf(IdentityOperationError)
    await first.confirmIdentityRecovery()
    expect(await first.previewIdentityRecovery()).toMatchObject({
      requiresPassword: true,
      previewDigest: 'a'.repeat(64),
    })
    await expect(
      first.reconcileIdentityOperation({
        archivePreviewDigest: recoveryPreview.previewDigest,
        password: 'fresh-secret-proof',
      }),
    ).rejects.toMatchObject({ state: 'UNKNOWN' })
    expect(JSON.stringify(persisted)).not.toContain('proof')
    expect(JSON.stringify(persisted)).not.toContain('password')
    const reloaded = new AdminUsersClient(options)
    reloaded.bindIdentityRecovery('same-session-authority', persisted, persist)
    expect(reloaded.hasPendingIdentityOperation).toBe(true)
    const count = calls.length
    await expect(reloaded.resumeIdentityOperation()).rejects.toThrow()
    expect(calls.slice(count).map((c) => c.path)).toEqual([
      `/api/academic/identity-operations/${newId}`,
    ])
    expect(reloaded.hasPendingIdentityOperation).toBe(true)
    status404 = false
    expect(await reloaded.resumeIdentityOperation()).toEqual({
      user: null,
      created: false,
      recovered: true,
    })
    const posts = calls.filter((c) => c.path.endsWith('/reconcile'))
    expect(posts).toHaveLength(2)
    expect(new Headers(posts[1]!.init?.headers).get('Idempotency-Key')).toBe(newId)
    expect(JSON.parse(String(posts[1]!.init?.body))).toEqual({
      archivePreviewDigest: recoveryPreview.previewDigest,
    })
    reloaded.acknowledgeIdentityOperation()
    expect(persisted).toBeNull()
  })
  it('retains the effective archive kind when a rehydrated recovery itself reaches FAILED_SAFE', async () => {
    const failed = crypto.randomUUID(),
      child = crypto.randomUUID()
    const calls: string[] = []
    const client = new AdminUsersClient({
      accessToken: () => null,
      fetcher: async (input) => {
        const path = String(input)
        calls.push(path)
        return path.endsWith('/reconcile-preview')
          ? new Response(JSON.stringify(recoveryPreview))
          : recoveryReceipt(child, 'FAILED_SAFE')
      },
    })
    client.bindIdentityRecovery(
      'same-session',
      {
        operationId: failed,
        kind: 'ARCHIVE',
        userId: 42,
        reconcileId: child,
        archivePreviewDigest: 'a'.repeat(64),
      },
      () => {},
    )
    await expect(client.resumeIdentityOperation()).rejects.toMatchObject({
      state: 'FAILED_SAFE',
      operationId: child,
    })
    expect(client.failedIdentityOperation).toMatchObject({
      operationId: child,
      kind: 'ARCHIVE',
      reconcileId: null,
      userId: 42,
    })
    await client.confirmIdentityRecovery()
    expect(await client.previewIdentityRecovery()).toMatchObject({ requiresPassword: true })
    expect(calls).toEqual([
      `/api/academic/identity-operations/${child}`,
      `/api/academic/identity-operations/${child}`,
      `/api/academic/identity-operations/${child}/reconcile-preview`,
    ])
  })
  it('requires fresh FAILED_SAFE confirmation and refuses archive preview/body for ordinary operations', async () => {
    const id = crypto.randomUUID()
    let status = 'PENDING',
      sends = 0
    const client = new AdminUsersClient({
      accessToken: () => null,
      fetcher: async () => {
        sends++
        return recoveryReceipt(id, status)
      },
    })
    const saved: IdentityRecoveryReference = {
      operationId: id,
      kind: 'USER',
      userId: null,
      reconcileId: null,
      archivePreviewDigest: null,
    }
    client.bindIdentityRecovery('scope', saved, () => {})
    expect(await client.confirmIdentityRecovery()).toBeNull()
    await expect(client.reconcileIdentityOperation()).rejects.toThrow('Сначала проверь')
    status = 'FAILED_SAFE'
    await client.confirmIdentityRecovery()
    await expect(client.previewIdentityRecovery()).rejects.toThrow()
    await expect(
      client.reconcileIdentityOperation({ archivePreviewDigest: 'a'.repeat(64) }),
    ).rejects.toThrow('не требуется')
    expect(sends).toBe(2)
    client.bindIdentityRecovery('another-authority', null, () => {})
    expect(client.failedIdentityOperation).toBeNull()
    expect(client.hasPendingIdentityOperation).toBe(false)
  })
})
