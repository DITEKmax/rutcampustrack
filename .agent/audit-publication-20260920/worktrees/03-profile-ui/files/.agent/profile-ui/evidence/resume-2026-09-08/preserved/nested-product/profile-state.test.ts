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

  it('clears loaded dependent pages after role and account authority changes', async () => {
    let snapshotCalls = 0
    const state = new ProfileState(port({
      getSnapshot: async () => {
        snapshotCalls += 1
        return snapshotCalls === 1
          ? snapshot()
          : { ...snapshot(), userId: '8', sessionId: 'new-account-session', activeRole: null }
      },
      selectRole: async () => ({ accessToken: 'role-token', expiresIn: 600, session: snapshot('HEADMAN') }),
      listSessions: async () => ({
        items: [{ sessionId: 's1', authMethod: 'PASSWORD', clientLabel: 'Браузер', locationLabel: null, createdAt: '2026-09-08T07:00:00Z', lastSeenAt: '2026-09-08T08:00:00Z', current: true }],
        nextCursor: 'sessions-next',
      }),
      listHistory: async () => ({
        items: [{ id: 'h1', type: 'LOGIN', occurredAt: '2026-09-08T08:00:00Z' }],
        nextCursor: 'history-next',
      }),
    }))
    await state.loadSnapshot()
    await state.loadSessions()
    await state.loadHistory()
    await state.selectRole('HEADMAN')

    expect(state.view.sessions).toEqual([])
    expect(state.view.history).toEqual([])
    expect(state.view.sessionsStatus).toBe('idle')
    expect(state.view.historyStatus).toBe('idle')

    await state.loadSessions()
    await state.loadHistory()
    await state.loadSnapshot()

    expect(state.view.snapshot?.userId).toBe('8')
    expect(state.view.sessions).toEqual([])
    expect(state.view.history).toEqual([])
    expect(state.view.sessionsNextCursor).toBeNull()
    expect(state.view.historyNextCursor).toBeNull()
  })

  it('returns bootstrap scope denial before reading history or changing password', async () => {
    const listHistory = vi.fn(async () => ({ items: [], nextCursor: null }))
    const changePassword = vi.fn(async () => undefined)
    const state = new ProfileState(port({
      getSnapshot: async () => ({ ...snapshot(), activeRole: null }),
      listHistory,
      changePassword,
    }))
    await state.loadSnapshot()

    await expect(state.loadHistory()).rejects.toMatchObject({ code: 'BOOTSTRAP_SCOPE_DENIED' })
    await expect(state.changePassword({ currentPassword: 'current', newPassword: 'Abcdefghij1!' })).rejects.toMatchObject({ code: 'BOOTSTRAP_SCOPE_DENIED' })
    expect(listHistory).not.toHaveBeenCalled()
    expect(changePassword).not.toHaveBeenCalled()
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

  it('notifies the adapter once when a read invalidates the account', async () => {
    const onInvalidated = vi.fn(async () => undefined)
    const state = new ProfileState(port({
      getSnapshot: async () => { throw new ProfileRequestError('INVALID_SESSION', 'Сессия недействительна', 401) },
      onInvalidated,
    }))

    await expect(state.loadSnapshot()).rejects.toMatchObject({ code: 'INVALID_SESSION' })
    expect(onInvalidated).toHaveBeenCalledTimes(1)
    expect(onInvalidated).toHaveBeenCalledWith('account-invalidated')
  })

  it('notifies the adapter once after a durable logout invalidation', async () => {
    const onInvalidated = vi.fn(async () => undefined)
    const logoutAll = vi.fn(async () => undefined)
    const state = new ProfileState(port({ logoutAll, onInvalidated }))
    await state.loadSnapshot()

    await state.logoutAll()
    expect(logoutAll).toHaveBeenCalledTimes(1)
    expect(onInvalidated).toHaveBeenCalledTimes(1)
    expect(onInvalidated).toHaveBeenCalledWith('logout-all')
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

  it('coalesces concurrent session page loads until the first page settles', async () => {
    const sessions = deferred<ProfileSessionsPage>()
    const listSessions = vi.fn(async () => sessions.promise)
    const state = new ProfileState(port({ listSessions }))
    await state.loadSnapshot()

    const first = state.loadSessions()
    const second = state.loadSessions({ cursor: 'ignored-while-busy' })
    expect(listSessions).toHaveBeenCalledTimes(1)

    sessions.resolve({ items: [{ sessionId: 's1', authMethod: null, clientLabel: null, locationLabel: null, createdAt: '2026-09-08T07:00:00Z', lastSeenAt: '2026-09-08T08:00:00Z', current: true }], nextCursor: 'cursor-2' })
    await expect(Promise.all([first, second])).resolves.toHaveLength(2)
    expect(state.view.sessionsNextCursor).toBe('cursor-2')
  })

  it.each([
    'changePassword',
    'logoutAll',
  ] as const)('keeps a durable %s success after a same-account refresh', async (mutation) => {
    const refresh = deferred<ProfileSnapshot>()
    const mutate = deferred<void>()
    let firstSnapshot = true
    const mutationPort = mutation === 'changePassword'
      ? { changePassword: async () => mutate.promise }
      : { logoutAll: async () => mutate.promise }
    const state = new ProfileState(port({
      getSnapshot: async () => {
        if (firstSnapshot) {
          firstSnapshot = false
          return snapshot()
        }
        return refresh.promise
      },
      ...mutationPort,
    }))
    await state.loadSnapshot()
    const pendingMutation = mutation === 'changePassword'
      ? state.changePassword({ currentPassword: 'current', newPassword: 'Abcdefghij1!' })
      : state.logoutAll()
    const pendingRefresh = state.loadSnapshot()
    refresh.resolve({ ...snapshot(), sessionId: 'same-account-refresh' })
    await pendingRefresh
    mutate.resolve()
    await pendingMutation

    expect(state.view.snapshot).toBeNull()
    expect(state.view.snapshotStatus).toBe('idle')
    expect(state.view.error).toBeNull()
  })

  it.each([
    ['changePassword'] as const,
    ['logoutAll'] as const,
  ])('ignores a late durable %s success after a different-account refresh', async (mutation) => {
    const refresh = deferred<ProfileSnapshot>()
    const mutate = deferred<void>()
    let firstSnapshot = true
    const mutationPort = mutation === 'changePassword'
      ? { changePassword: async () => mutate.promise }
      : { logoutAll: async () => mutate.promise }
    const state = new ProfileState(port({
      getSnapshot: async () => {
        if (firstSnapshot) {
          firstSnapshot = false
          return snapshot()
        }
        return refresh.promise
      },
      ...mutationPort,
    }))
    await state.loadSnapshot()

    const pendingMutation = mutation === 'changePassword'
      ? state.changePassword({ currentPassword: 'current', newPassword: 'Abcdefghij1!' })
      : state.logoutAll()
    const pendingRefresh = state.loadSnapshot()
    refresh.resolve({ ...snapshot(), userId: 'different-account', sessionId: 'new-account-session' })
    await pendingRefresh
    mutate.resolve()
    await pendingMutation

    expect(state.view.snapshot?.userId).toBe('different-account')
    expect(state.view.snapshotStatus).toBe('ready')
    expect(state.view.error).toBeNull()
  })
})
