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

    await expect(client.updateProfile(42, { middleName: '', employeeNumber: '' }))
      .resolves.toMatchObject({ id: 42, login: 'ivanov.i' })

    expect(capturedPath).toBe('/api/academic/users/42')
    expect(capturedInit?.method).toBe('PATCH')
    expect(JSON.parse(String(capturedInit?.body))).toEqual({ middleName: '', employeeNumber: '' })
    expect(JSON.parse(String(capturedInit?.body))).not.toHaveProperty('role')
    expect(JSON.parse(String(capturedInit?.body))).not.toHaveProperty('groupId')
  })

  it('trims required names and rejects an empty or overlong required name', async () => {
    const client = new AdminUsersClient({
      accessToken: () => null,
      fetcher: async () => new Response(JSON.stringify(userResponse), { status: 200 }),
    })

    await expect(client.updateProfile(42, { firstName: ' Иван ' })).resolves.toMatchObject({ id: 42 })
    expect(() => client.updateProfile(42, { firstName: '  ' })).toThrow('firstName не может быть пустым')
    expect(() => client.updateProfile(42, { lastName: 'я'.repeat(129) })).toThrow('lastName не может быть длиннее 128 символов')
  })
})
