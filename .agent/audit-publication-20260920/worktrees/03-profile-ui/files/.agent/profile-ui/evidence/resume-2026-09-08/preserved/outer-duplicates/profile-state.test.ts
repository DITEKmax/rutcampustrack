import { describe, expect, it, vi } from 'vitest'
import { ProfileState } from './profile-state'
import {
  DEFAULT_PASSWORD_POLICY,
  ProfileRequestError,
  type ProfileHistoryPage,
  type ProfilePort,
  type ProfileRoleSelection,
  type ProfileSessionsPage,
  type ProfileSnapshot,
  validateNewPassword,
} from './profile-types'

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void; reject: (reason?: unknown) => void } {
  let resolvePromise: (value: T) => void = () => undefined
  let rejectPromise: (reason?: unknown) => void = () => undefined
  const promise = new Promise<T>((resolve, reject) => {
    resolvePromise = resolve
    rejectPromise = reject
  })
  return { promise, resolve: resolvePromise, reject: rejectPromise }
}

const studentGrant = { grantId: '11', role: 'STUDENT' as const, status: 'ACTIVE' as const, groupId: '42', contextLabel: 'ИКБО-01-24', selectable: true, readOnly: false }
const headmanGrant = { grantId: '12', role: 'HEADMAN' as const, status: 'ACTIVE' as const, groupId: '42', contextLabel: 'ИКБО-01-24', selectable: true, readOnly: false }

function snapshot(activeRole: ProfileSnapshot['activeRole'] = 'STUDENT'): ProfileSnapshot {
  return {
    sessionId: 'b4a6c6f2-4f3d-4d1e-9af2-9f88a2d7e031',
    userId: '7',
    displayName: 'Тестовый пользователь',
    groupLabel: 'ИКБО-01-24',
    sessionVersion: '7',
    rolesVersion: '4',
    activeRole,
    roles: [studentGrant, headmanGrant],
    readOnly: false,
    passwordPolicy: DEFAULT_PASSWORD_POLICY,
  }
}

function port(overrides: Partial<ProfilePort> = {}): ProfilePort {
  return {
    getSnapshot: async () => snapshot(),
    selectRole: async (input): Promise<ProfileRoleSelection> => ({ accessToken: `token-${input.role}`, expiresIn: 600, session: snapshot(input.role) }),
    listSessions: async (): Promise<ProfileSessionsPage> => ({ items: [], nextCursor: null }),
    listHistory: async (): Promise<ProfileHistoryPage> => ({ items: [], nextCursor: null }),
    changePassword: async () => undefined,
    logoutAll: async () => undefined,
    isOnline: () => true,
    ...overrides,
  }
}

describe('ProfileState', () => {
  it('keeps the server-selected role when a role conflict is returned', async () => {
    const selectRole = vi.fn(async () => { throw new ProfileRequestError('ROLE_NOT_GRANTED', 'Роль больше недоступна', 403) })
    const state = new ProfileState(port({ selectRole }))
    await state.loadSnapshot()

    await expect(state.selectRole('HEADMAN')).rejects.toMatchObject({ code: 'ROLE_NOT_GRANTED' })
    expect(selectRole).toHaveBeenCalledWith({ role: 'HEADMAN', expectedSessionVersion: '7' })
    expect(state.view.snapshot?.activeRole).toBe('STUDENT')
  })

  it('ignores a late history response after an accepted role switch', async () => {
    const history = deferred<ProfileHistoryPage>()
    const selection = deferred<ProfileRoleSelection>()
    const state = new ProfileState(port({
      listHistory: async () => history.promise,
      selectRole: async () => selection.promise,
    }))
    await state.loadSnapshot()
    const historyPromise = state.loadHistory()
    const selectPromise = state.selectRole('HEADMAN')

    selection.resolve({ accessToken: 'access-token', expiresIn: 600, session: snapshot('HEADMAN') })
    await selectPromise
    history.resolve({ items: [{ id: 'old', type: 'LOGIN', occurredAt: '2026-09-08T08:00:00Z' }], nextCursor: 'cursor-old' })
    await historyPromise

    expect(state.view.snapshot?.activeRole).toBe('HEADMAN')
    expect(state.view.history).toEqual([])
    expect(state.view.historyNextCursor).toBeNull()
  })

  it('keeps section errors independent when another section succeeds', async () => {
    const state = new ProfileState(port({
      listSessions: async () => { throw new ProfileRequestError('AUTHORITY_UNAVAILABLE', 'Сеансы недоступны', 503) },
      listHistory: async () => ({ items: [{ id: 'h1', type: 'LOGIN', occurredAt: '2026-09-08T08:00:00Z' }], nextCursor: null }),
    }))
    await state.loadSnapshot()
    await expect(state.loadSessions()).rejects.toMatchObject({ code: 'AUTHORITY_UNAVAILABLE' })
    await expect(state.loadHistory()).resolves.toMatchObject({ nextCursor: null })

    expect(state.view.sessionsStatus).toBe('error')
    expect(state.view.sessionsError?.code).toBe('AUTHORITY_UNAVAILABLE')
    expect(state.view.historyStatus).toBe('ready')
    expect(state.view.historyError).toBeNull()
  })

  it('does not call password mutation while offline', async () => {
    const changePassword = vi.fn(async () => undefined)
    const state = new ProfileState(port({ isOnline: () => false, changePassword }))
    await state.loadSnapshot()

    await expect(state.changePassword({ currentPassword: 'current', newPassword: 'Abcdefghij1!' })).rejects.toMatchObject({ code: 'OFFLINE_MUTATION_DISABLED' })
    expect(changePassword).not.toHaveBeenCalled()
  })

  it('passes a short current password through while validating only the new password', async () => {
    const changePassword = vi.fn(async () => undefined)
    const state = new ProfileState(port({ changePassword }))
    await state.loadSnapshot()

    await state.changePassword({ currentPassword: 'x', newPassword: 'Abcdefghij1!' })
    expect(changePassword).toHaveBeenCalledWith({ currentPassword: 'x', newPassword: 'Abcdefghij1!' })
    expect(state.view.snapshot).toBeNull()
  })

  it('enforces Unicode scalar and UTF-8 byte boundaries without normalization', () => {
    expect(validateNewPassword(`${'😀'.repeat(10)}1!`, DEFAULT_PASSWORD_POLICY)).toEqual([])
    expect(validateNewPassword(`${'a'.repeat(70)}1!`, DEFAULT_PASSWORD_POLICY)).toEqual([])
    expect(validateNewPassword(`${'a'.repeat(71)}1!`, DEFAULT_PASSWORD_POLICY)).toContain('MAX_UTF8_BYTES')
    expect(validateNewPassword(`\ud800${'a'.repeat(10)}1!`, DEFAULT_PASSWORD_POLICY)).toContain('UNPAIRED_SURROGATE')
  })

  it('preserves truthful keyset cursors while appending pages', async () => {
    const state = new ProfileState(port({
      listSessions: async (input) => input?.cursor
        ? { items: [{ sessionId: 's2', authMethod: null, clientLabel: null, locationLabel: null, createdAt: '2026-09-07T08:00:00Z', lastSeenAt: '2026-09-08T08:00:00Z', current: false }], nextCursor: null }
        : { items: [{ sessionId: 's1', authMethod: 'PASSWORD', clientLabel: 'Браузер', locationLabel: null, createdAt: '2026-09-07T07:00:00Z', lastSeenAt: '2026-09-08T08:00:00Z', current: true }], nextCursor: 'cursor-2' },
      listHistory: async (input) => input?.cursor
        ? { items: [{ id: 'h2', type: 'PASSWORD_CHANGED', occurredAt: '2026-09-07T08:00:00Z' }], nextCursor: null }
        : { items: [{ id: 'h1', type: 'LOGIN', occurredAt: '2026-09-08T08:00:00Z' }], nextCursor: 'history-2' },
    }))
    await state.loadSnapshot()
    await state.loadSessions()
    await state.loadSessions({ cursor: 'cursor-2' })
    await state.loadHistory()
    await state.loadHistory({ cursor: 'history-2' })

    expect(state.view.sessions.map((item) => item.sessionId)).toEqual(['s1', 's2'])
    expect(state.view.sessionsNextCursor).toBeNull()
    expect(state.view.history.map((item) => item.id)).toEqual(['h1', 'h2'])
    expect(state.view.historyNextCursor).toBeNull()
    expect(state.view.sessions[1]?.locationLabel).toBeNull()
  })
})
