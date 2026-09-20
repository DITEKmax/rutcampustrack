import { nextTick, watchEffect } from 'vue'
import { describe, expect, it } from 'vitest'
import { ProfileState } from '../../features/profile/profile-state'
import {
  DEFAULT_PASSWORD_POLICY,
  ProfileRequestError,
  type ProfileHistoryPage,
  type ProfilePort,
  type ProfileSessionsPage,
  type ProfileSnapshot,
} from '../../features/profile/profile-types'
import { createProfileViewPublication } from './profile-view-publication'

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void; reject: (reason?: unknown) => void } {
  let resolvePromise: (value: T) => void = () => undefined
  let rejectPromise: (reason?: unknown) => void = () => undefined
  const promise = new Promise<T>((resolve, reject) => {
    resolvePromise = resolve
    rejectPromise = reject
  })
  return { promise, resolve: resolvePromise, reject: rejectPromise }
}

const snapshot = (): ProfileSnapshot => ({
  sessionId: 'session-1',
  userId: 'user-1',
  displayName: 'Иван Петров',
  groupLabel: 'ИКБО-01-24',
  sessionVersion: '7',
  rolesVersion: '4',
  activeRole: 'STUDENT',
  roles: [{
    grantId: 'grant-1',
    role: 'STUDENT',
    status: 'ACTIVE',
    groupId: 'group-1',
    contextLabel: 'ИКБО-01-24',
    selectable: true,
    readOnly: false,
  }],
  readOnly: false,
  passwordPolicy: DEFAULT_PASSWORD_POLICY,
})

function port(overrides: Partial<ProfilePort> = {}): ProfilePort {
  return {
    getSnapshot: async () => snapshot(),
    selectRole: async () => ({ accessToken: 'token', expiresIn: 600, session: snapshot() }),
    listSessions: async (): Promise<ProfileSessionsPage> => ({ items: [], nextCursor: null }),
    listHistory: async (): Promise<ProfileHistoryPage> => ({ items: [], nextCursor: null }),
    changePassword: async () => undefined,
    logoutAll: async () => undefined,
    isOnline: () => true,
    ...overrides,
  }
}

async function settleVue(): Promise<void> {
  await Promise.resolve()
  await nextTick()
}

describe('createProfileViewPublication', () => {
  it('publishes deferred snapshot loading and ready transitions as fresh observable views', async () => {
    const request = deferred<ProfileSnapshot>()
    const state = new ProfileState(port({ getSnapshot: async () => request.promise }))
    const publication = createProfileViewPublication(() => state.view)
    const renders: string[] = []
    const stop = watchEffect(() => {
      const view = publication.view.value
      renders.push(`${view.snapshotStatus}:${view.snapshot?.displayName ?? 'нет'}`)
    })

    const load = state.loadSnapshot()
    publication.publish()
    await settleVue()
    expect(publication.view.value.snapshotStatus).toBe('loading')
    expect(renders.at(-1)).toBe('loading:нет')

    request.resolve(snapshot())
    await load
    publication.publish()
    await settleVue()
    expect(publication.view.value.snapshotStatus).toBe('ready')
    expect(publication.view.value.snapshot?.displayName).toBe('Иван Петров')
    expect(publication.view.value).not.toBe(state.view)
    expect(renders.at(-1)).toBe('ready:Иван Петров')
    stop()
  })

  it('publishes sessions/history arrays, loading, ready, and error transitions', async () => {
    const sessionsRequest = deferred<ProfileSessionsPage>()
    const historyRequest = deferred<ProfileHistoryPage>()
    const state = new ProfileState(port({
      listSessions: async () => sessionsRequest.promise,
      listHistory: async () => historyRequest.promise,
    }))
    const publication = createProfileViewPublication(() => state.view)
    await state.loadSnapshot()
    publication.publish()

    const loadSessions = state.loadSessions()
    publication.publish()
    await settleVue()
    expect(publication.view.value.sessionsStatus).toBe('loading')

    const session = {
      sessionId: 'browser-1',
      authMethod: 'PASSWORD' as const,
      clientLabel: 'Браузер',
      locationLabel: null,
      createdAt: '2026-09-08T07:00:00Z',
      lastSeenAt: '2026-09-08T08:00:00Z',
      current: true,
    }
    sessionsRequest.resolve({ items: [session], nextCursor: 'sessions-next' })
    await loadSessions
    publication.publish()
    await settleVue()
    expect(publication.view.value.sessionsStatus).toBe('ready')
    expect(publication.view.value.sessions).toEqual([session])
    expect(publication.view.value.sessions).not.toBe(state.view.sessions)
    expect(publication.view.value.sessionsNextCursor).toBe('sessions-next')

    const loadHistory = state.loadHistory()
    publication.publish()
    await settleVue()
    expect(publication.view.value.historyStatus).toBe('loading')

    const historyError = new ProfileRequestError('AUTHORITY_UNAVAILABLE', 'История недоступна', 503)
    historyRequest.reject(historyError)
    await expect(loadHistory).rejects.toBe(historyError)
    publication.publish()
    await settleVue()
    expect(publication.view.value.historyStatus).toBe('error')
    expect(publication.view.value.historyError).toBe(historyError)
    expect(publication.view.value.history).toEqual([])
  })

  it('publishes mutation busy and the invalidated empty boundary', async () => {
    const passwordRequest = deferred<void>()
    const state = new ProfileState(port({ changePassword: async () => passwordRequest.promise }))
    const publication = createProfileViewPublication(() => state.view)
    await state.loadSnapshot()
    publication.publish()

    const changePassword = state.changePassword({ currentPassword: 'old', newPassword: 'NewPassword1!' })
    publication.publish()
    await settleVue()
    expect(publication.view.value.mutationBusy).toBe('password')

    passwordRequest.resolve()
    await changePassword
    publication.publish()
    await settleVue()
    expect(publication.view.value.snapshot).toBeNull()
    expect(publication.view.value.snapshotStatus).toBe('idle')
    expect(publication.view.value.sessions).toEqual([])
    expect(publication.view.value.history).toEqual([])
    expect(publication.view.value.mutationBusy).toBeNull()
  })

  it('publishes the automatic stale retry loading and ready settlement', async () => {
    const initialRequest = deferred<ProfileSnapshot>()
    const retryRequest = deferred<ProfileSnapshot>()
    let snapshotCalls = 0
    const state = new ProfileState(port({
      getSnapshot: async () => {
        snapshotCalls += 1
        return snapshotCalls === 1 ? initialRequest.promise : retryRequest.promise
      },
    }))
    const publication = createProfileViewPublication(() => state.view)

    const load = state.loadSnapshot()
    publication.publish()
    expect(publication.view.value.snapshotStatus).toBe('loading')

    initialRequest.reject(new ProfileRequestError('SESSION_STATE_STALE', 'Состояние сессии устарело', 409))
    await expect(load).rejects.toMatchObject({ code: 'SESSION_STATE_STALE' })
    expect(snapshotCalls).toBe(2)
    publication.publish()
    expect(publication.view.value.snapshotStatus).toBe('loading')

    retryRequest.resolve(snapshot())
    await state.waitForAutomaticStaleReload()
    publication.publish()
    expect(snapshotCalls).toBe(2)
    expect(publication.view.value.snapshotStatus).toBe('ready')
    expect(publication.view.value.snapshot?.displayName).toBe('Иван Петров')
  })

  it('publishes the automatic stale retry error settlement without an unhandled rejection', async () => {
    const initialRequest = deferred<ProfileSnapshot>()
    const retryRequest = deferred<ProfileSnapshot>()
    let snapshotCalls = 0
    const retryError = new ProfileRequestError('AUTHORITY_UNAVAILABLE', 'Профиль временно недоступен', 503)
    const state = new ProfileState(port({
      getSnapshot: async () => {
        snapshotCalls += 1
        return snapshotCalls === 1 ? initialRequest.promise : retryRequest.promise
      },
    }))
    const publication = createProfileViewPublication(() => state.view)

    const load = state.loadSnapshot()
    initialRequest.reject(new ProfileRequestError('SESSION_STATE_STALE', 'Состояние сессии устарело', 409))
    await expect(load).rejects.toMatchObject({ code: 'SESSION_STATE_STALE' })
    expect(snapshotCalls).toBe(2)
    publication.publish()
    expect(publication.view.value.snapshotStatus).toBe('loading')

    retryRequest.reject(retryError)
    await state.waitForAutomaticStaleReload()
    publication.publish()
    expect(snapshotCalls).toBe(2)
    expect(publication.view.value.snapshotStatus).toBe('error')
    expect(publication.view.value.snapshotError).toBe(retryError)
  })

  it('publishes nested snapshot completion before sessions and history area transitions', async () => {
    const snapshotRequest = deferred<ProfileSnapshot>()
    const sessionsRequest = deferred<ProfileSessionsPage>()
    const historyRequest = deferred<ProfileHistoryPage>()
    const state = new ProfileState(port({
      getSnapshot: async () => snapshotRequest.promise,
      listSessions: async () => sessionsRequest.promise,
      listHistory: async () => historyRequest.promise,
    }))
    const publication = createProfileViewPublication(() => state.view)

    const loadSnapshot = state.loadSnapshot()
    publication.publish()
    expect(publication.view.value.snapshotStatus).toBe('loading')
    snapshotRequest.resolve(snapshot())
    await loadSnapshot
    publication.publish()
    expect(publication.view.value.snapshotStatus).toBe('ready')

    const loadSessions = state.loadSessions()
    publication.publish()
    expect(publication.view.value.sessionsStatus).toBe('loading')
    sessionsRequest.resolve({ items: [], nextCursor: null })
    await loadSessions
    publication.publish()
    expect(publication.view.value.sessionsStatus).toBe('ready')

    const loadHistory = state.loadHistory()
    publication.publish()
    expect(publication.view.value.historyStatus).toBe('loading')
    historyRequest.resolve({ items: [], nextCursor: null })
    await loadHistory
    publication.publish()
    expect(publication.view.value.historyStatus).toBe('ready')
  })

  it('suppresses a late publication after disposal', async () => {
    const request = deferred<ProfileSnapshot>()
    let disposed = false
    const state = new ProfileState(port({ getSnapshot: async () => request.promise }))
    const publication = createProfileViewPublication(() => state.view, () => disposed)

    const load = state.loadSnapshot()
    publication.publish()
    const loadingView = publication.view.value
    disposed = true
    request.resolve(snapshot())
    await load
    publication.publish()

    expect(publication.view.value).toBe(loadingView)
    expect(publication.view.value.snapshotStatus).toBe('loading')
  })
})
