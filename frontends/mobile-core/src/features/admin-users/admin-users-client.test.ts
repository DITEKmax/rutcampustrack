import { describe, expect, it } from 'vitest'
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
      fetcher: async (_input, init) => {
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
    await expect(client.archiveUser(42, input)).rejects.toThrow('lost-response')
    await client.archiveUser(42, input)
    expect(bodies).toHaveLength(2)
    expect(bodies[0]).toBe(bodies[1])
  })
})
