import {
  ProfileRequestError,
  type ProfileHistoryPage,
  type ProfilePageRequest,
  type ProfilePort,
  type ProfileRequestErrorCode,
  type ProfileRole,
  type ProfileRoleSelection,
  type ProfileSessionSummary,
  type ProfileSessionsPage,
  type ProfileSnapshot,
} from './profile-types'

export type ProfileResourceStatus = 'idle' | 'loading' | 'ready' | 'error'

export interface ProfileStateView {
  snapshot: ProfileSnapshot | null
  snapshotStatus: ProfileResourceStatus
  snapshotError: ProfileRequestError | null
  sessions: readonly ProfileSessionSummary[]
  sessionsNextCursor: string | null
  sessionsStatus: ProfileResourceStatus
  sessionsError: ProfileRequestError | null
  history: ProfileHistoryPage['items']
  historyNextCursor: string | null
  historyStatus: ProfileResourceStatus
  historyError: ProfileRequestError | null
  error: ProfileRequestError | null
  mutationBusy: 'role' | 'password' | 'logout-all' | 'terminate-session' | null
  terminatingSessionId: string | null
  terminationError: ProfileRequestError | null
}

interface RequestGuard {
  generation: number
  sessionId: string | null
}

interface SessionReadGuard extends RequestGuard { sessionsGeneration: number }

interface SnapshotRequestGuard extends RequestGuard {
  requestId: number
}

const MAX_AUTOMATIC_STALE_RELOADS_PER_AUTHORITY = 1

export class ProfileState {
  readonly view: ProfileStateView = {
    snapshot: null,
    snapshotStatus: 'idle',
    snapshotError: null,
    sessions: [],
    sessionsNextCursor: null,
    sessionsStatus: 'idle',
    sessionsError: null,
    history: [],
    historyNextCursor: null,
    historyStatus: 'idle',
    historyError: null,
    error: null,
    mutationBusy: null,
    terminatingSessionId: null,
    terminationError: null,
  }

  private authorityGeneration = 0
  private sessionsGeneration = 0
  private mutationRequestId = 0
  private sessionIdentity: string | null = null
  private snapshotRequestId = 0
  private staleReloadAttempts = 0
  private staleReloadPromise: Promise<ProfileSnapshot> | null = null
  private staleReloadSettlement: Promise<void> = Promise.resolve()
  private sessionsRequest: Promise<ProfileSessionsPage> | null = null
  private sessionsRequestGuard: SessionReadGuard | null = null
  private allSessionsRequest: Promise<ProfileSessionsPage> | null = null
  private allSessionsGuard: SessionReadGuard | null = null

  constructor(private readonly port: ProfilePort) {}

  async loadSnapshot(): Promise<ProfileSnapshot> {
    return this.loadSnapshotRequest(false)
  }

  /**
   * A SESSION_STATE_STALE read can start one detached automatic snapshot retry.
   * Owners use this framework-neutral signal to publish after that retry has
   * reached ready or error; the signal always settles and never rethrows.
   */
  async waitForAutomaticStaleReload(): Promise<void> {
    await this.staleReloadSettlement
  }

  private async loadSnapshotRequest(automaticStaleReload: boolean): Promise<ProfileSnapshot> {
    if (!automaticStaleReload && !this.staleReloadPromise) this.staleReloadAttempts = 0
    const guard = this.beginSnapshotRequest()
    this.view.snapshotStatus = 'loading'
    this.view.snapshotError = null
    this.view.error = null
    try {
      const snapshot = await this.port.getSnapshot()
      if (!this.isCurrentSnapshotRequest(guard)) return this.view.snapshot ?? snapshot
      this.applyAcceptedSnapshot(snapshot)
      return snapshot
    } catch (error) {
      const typed = await this.handleSnapshotFailure(error, guard)
      if (this.isCurrentSnapshotRequest(guard)) this.view.snapshotStatus = 'error'
      throw typed
    }
  }

  async selectRole(role: ProfileRole): Promise<ProfileRoleSelection> {
    const snapshot = this.requireSnapshot()
    if (!this.isOnline()) throw this.offlineError()
    if (this.view.mutationBusy !== null) throw new ProfileRequestError('UNKNOWN', 'Операция уже выполняется')
    const guard = this.currentGuard()
    const mutationId = ++this.mutationRequestId
    this.view.mutationBusy = 'role'
    this.view.error = null
    try {
      const result = await this.port.selectRole({ role, expectedSessionVersion: snapshot.sessionVersion })
      if (this.isCurrent(guard)) {
        this.applyAcceptedSnapshot(result.session)
      }
      return result
    } catch (error) {
      const typed = await this.handleFailure(error, guard, 'snapshot')
      throw typed
    } finally {
      if (mutationId === this.mutationRequestId) this.view.mutationBusy = null
    }
  }

  async loadSessions(input?: ProfilePageRequest): Promise<ProfileSessionsPage> {
    const guard = { ...this.requireSessionGuard(), sessionsGeneration: this.sessionsGeneration }
    if (this.sessionsRequest && this.sessionsRequestGuard && this.isCurrentSessions(this.sessionsRequestGuard)) {
      return this.sessionsRequest
    }

    const request = this.loadSessionsPage(input, guard)
    this.sessionsRequest = request
    this.sessionsRequestGuard = guard
    try {
      return await request
    } finally {
      if (this.sessionsRequest === request) {
        this.sessionsRequest = null
        this.sessionsRequestGuard = null
      }
    }
  }

  /** Sequentially resolves the existing keyset API; retries resume a failed remaining page. */
  async loadAllSessions(onProgress?: () => void): Promise<ProfileSessionsPage> {
    const guard = { ...this.requireSessionGuard(), sessionsGeneration: this.sessionsGeneration }
    if (this.allSessionsRequest && this.allSessionsGuard && this.isCurrentSessions(this.allSessionsGuard)) return this.allSessionsRequest
    const request = this.resolveAllSessions(guard, onProgress)
    this.allSessionsRequest = request
    this.allSessionsGuard = guard
    try { return await request } finally {
      if (this.allSessionsRequest === request) {
        this.allSessionsRequest = null
        this.allSessionsGuard = null
      }
    }
  }

  private async resolveAllSessions(guard: SessionReadGuard, onProgress?: () => void): Promise<ProfileSessionsPage> {
    let cursor = this.view.sessionsStatus === 'error' ? this.view.sessionsNextCursor : null
    const seen = new Set<string>()
    while (this.isCurrentSessions(guard)) {
      if (cursor) seen.add(cursor)
      const page = await this.loadSessions(cursor ? { cursor } : undefined)
      if (!this.isCurrentSessions(guard)) break
      onProgress?.()
      if (page.nextCursor === null) return { items: this.view.sessions, nextCursor: null }
      if (seen.has(page.nextCursor)) {
        const error = new ProfileRequestError('INVALID_CURSOR', 'Сервер повторил страницу сессий. Попробуй загрузить список заново.')
        this.view.sessionsStatus = 'error'
        this.view.sessionsError = error
        this.view.sessionsNextCursor = null
        throw error
      }
      cursor = page.nextCursor
    }
    return { items: this.view.sessions, nextCursor: this.view.sessionsNextCursor }
  }

  private async loadSessionsPage(input: ProfilePageRequest | undefined, guard: SessionReadGuard): Promise<ProfileSessionsPage> {
    this.view.sessionsStatus = 'loading'
    this.view.sessionsError = null
    this.view.error = null
    try {
      const page = await this.port.listSessions(input)
      if (this.isCurrentSessions(guard)) {
        this.view.sessions = [...new Map((input?.cursor ? [...this.view.sessions, ...page.items] : page.items).map(item => [item.sessionId, item])).values()]
        this.view.sessionsNextCursor = page.nextCursor
        this.view.sessionsStatus = 'ready'
      }
      return page
    } catch (error) {
      const typed = this.isCurrentSessions(guard) ? await this.handleReadFailure(error, guard, 'sessions') : this.asProfileError(error)
      if (this.isCurrentSessions(guard)) this.view.sessionsStatus = 'error'
      throw typed
    }
  }

  async loadHistory(input?: ProfilePageRequest): Promise<ProfileHistoryPage> {
    const snapshot = this.requireSnapshot()
    if (!snapshot.activeRole) throw new ProfileRequestError('BOOTSTRAP_SCOPE_DENIED', 'История доступна после выбора роли')
    const guard = this.currentGuard()
    this.view.historyStatus = 'loading'
    this.view.historyError = null
    this.view.error = null
    try {
      const page = await this.port.listHistory(input)
      if (this.isCurrent(guard)) {
        this.view.history = input?.cursor ? [...this.view.history, ...page.items] : page.items
        this.view.historyNextCursor = page.nextCursor
        this.view.historyStatus = 'ready'
      }
      return page
    } catch (error) {
      const typed = await this.handleReadFailure(error, guard, 'history')
      if (this.isCurrent(guard)) this.view.historyStatus = 'error'
      throw typed
    }
  }

  async changePassword(input: { currentPassword: string; newPassword: string }): Promise<void> {
    const snapshot = this.requireSnapshot()
    if (!snapshot.activeRole) throw new ProfileRequestError('BOOTSTRAP_SCOPE_DENIED', 'Смена пароля доступна после выбора роли')
    if (!this.isOnline()) throw this.offlineError()
    if (this.view.mutationBusy !== null) throw new ProfileRequestError('UNKNOWN', 'Операция уже выполняется')
    const guard = this.currentGuard()
    const accountId = snapshot.userId
    const mutationId = ++this.mutationRequestId
    this.view.mutationBusy = 'password'
    this.view.error = null
    try {
      await this.port.changePassword(input)
      if (!this.canApplyDurableSuccess(guard, accountId)) return
      this.invalidate('password-changed')
      await this.notifyInvalidated('password-changed')
    } catch (error) {
      const typed = await this.handleFailure(error, guard, 'mutation')
      throw typed
    } finally {
      if (mutationId === this.mutationRequestId) this.view.mutationBusy = null
    }
  }

  async logoutAll(): Promise<void> {
    const snapshot = this.requireSnapshot()
    if (!this.isOnline()) throw this.offlineError()
    if (this.view.mutationBusy !== null) throw new ProfileRequestError('UNKNOWN', 'Операция уже выполняется')
    const guard = this.currentGuard()
    const accountId = snapshot.userId
    const mutationId = ++this.mutationRequestId
    this.view.mutationBusy = 'logout-all'
    this.view.error = null
    try {
      await this.port.logoutAll()
      if (!this.canApplyDurableSuccess(guard, accountId)) return
      this.invalidate('logout-all')
      await this.notifyInvalidated('logout-all')
    } catch (error) {
      const typed = await this.handleFailure(error, guard, 'snapshot')
      throw typed
    } finally {
      if (mutationId === this.mutationRequestId) this.view.mutationBusy = null
    }
  }

  async terminateSession(sessionId: string): Promise<void> {
    const snapshot = this.requireSnapshot()
    if (!this.isOnline()) throw this.offlineError()
    if (this.view.mutationBusy !== null) throw new ProfileRequestError('UNKNOWN', 'Операция уже выполняется')
    if (!this.port.terminateSession) throw new ProfileRequestError('UNKNOWN', 'Завершение выбранной сессии недоступно')
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u.test(sessionId)) throw new ProfileRequestError('SESSION_NOT_FOUND', 'Сессия недоступна')
    const guard = this.currentGuard()
    const accountId = snapshot.userId
    const mutationId = ++this.mutationRequestId
    this.view.mutationBusy = 'terminate-session'
    this.view.terminatingSessionId = sessionId
    this.view.terminationError = null
    this.view.error = null
    try {
      await this.port.terminateSession(sessionId)
      if (!this.canApplyDurableSuccess(guard, accountId)) return
      if (this.view.snapshot?.sessionId === sessionId) {
        this.invalidate('session-terminated')
        await this.notifyInvalidated('session-terminated')
        return
      }
      // Fence pre-mutation pages so their late results cannot resurrect the revoked card.
      this.sessionsGeneration += 1
      this.sessionsRequest = null
      this.sessionsRequestGuard = null
      this.allSessionsRequest = null
      this.allSessionsGuard = null
      this.view.sessions = this.view.sessions.filter(session => session.sessionId !== sessionId)
      this.view.sessionsNextCursor = null
      this.view.sessionsStatus = 'ready'
      try { await this.loadAllSessions() } catch { /* Durable revoke succeeded; keep the independent list-read error. */ }
    } catch (error) {
      const typed = await this.handleFailure(error, guard, 'mutation')
      if (this.isCurrent(guard)) this.view.terminationError = typed
      throw typed
    } finally {
      if (mutationId === this.mutationRequestId) {
        this.view.mutationBusy = null
        this.view.terminatingSessionId = null
      }
    }
  }

  invalidate(reason: 'logout-all' | 'password-changed' | 'session-terminated' | 'account-invalidated' = 'account-invalidated'): void {
    this.authorityGeneration += 1
    this.mutationRequestId += 1
    this.sessionIdentity = null
    this.view.snapshot = null
    this.view.snapshotStatus = 'idle'
    this.view.snapshotError = null
    this.view.sessions = []
    this.view.sessionsNextCursor = null
    this.view.sessionsStatus = 'idle'
    this.view.sessionsError = null
    this.view.history = []
    this.view.historyNextCursor = null
    this.view.historyStatus = 'idle'
    this.view.historyError = null
    this.view.error = reason === 'account-invalidated' ? new ProfileRequestError('ACCOUNT_INVALIDATED', 'Аккаунт больше недоступен') : null
    this.view.mutationBusy = null
    this.view.terminatingSessionId = null
    this.view.terminationError = null
  }

  private requireSnapshot(): ProfileSnapshot {
    if (!this.view.snapshot) throw new ProfileRequestError('ACCOUNT_INVALIDATED', 'Профиль ещё не загружен')
    return this.view.snapshot
  }

  private requireSessionGuard(): RequestGuard {
    this.requireSnapshot()
    return this.currentGuard()
  }

  private beginSnapshotRequest(): SnapshotRequestGuard {
    this.snapshotRequestId += 1
    return { ...this.currentGuard(), requestId: this.snapshotRequestId }
  }

  private currentGuard(): RequestGuard {
    return { generation: this.authorityGeneration, sessionId: this.sessionIdentity }
  }

  private authorityChanged(snapshot: ProfileSnapshot): boolean {
    const previous = this.view.snapshot
    if (!previous) return true
    return previous.userId !== snapshot.userId
      || previous.sessionId !== snapshot.sessionId
      || previous.sessionVersion !== snapshot.sessionVersion
      || previous.rolesVersion !== snapshot.rolesVersion
      || previous.activeRole !== snapshot.activeRole
  }

  private applyAcceptedSnapshot(snapshot: ProfileSnapshot): void {
    if (this.view.snapshot && (this.view.snapshot.userId !== snapshot.userId || this.view.snapshot.sessionId !== snapshot.sessionId)) {
      this.mutationRequestId += 1
      this.view.mutationBusy = null
      this.view.terminatingSessionId = null
      this.view.terminationError = null
    }
    if (this.authorityChanged(snapshot)) {
      this.authorityGeneration += 1
      this.sessionIdentity = snapshot.sessionId
      this.resetDependentResources()
    } else {
      this.sessionIdentity = snapshot.sessionId
    }
    this.view.snapshot = snapshot
    this.view.snapshotStatus = 'ready'
    this.view.snapshotError = null
    this.view.error = null
    this.staleReloadAttempts = 0
  }

  private resetDependentResources(): void {
    this.sessionsGeneration += 1
    this.sessionsRequest = null
    this.sessionsRequestGuard = null
    this.view.sessions = []
    this.view.sessionsNextCursor = null
    this.view.sessionsStatus = 'idle'
    this.view.sessionsError = null
    this.view.history = []
    this.view.historyNextCursor = null
    this.view.historyStatus = 'idle'
    this.view.historyError = null
  }

  private isCurrentSessions(guard: SessionReadGuard): boolean {
    return this.isCurrent(guard) && guard.sessionsGeneration === this.sessionsGeneration
  }

  private isCurrent(guard: RequestGuard): boolean {
    return guard.generation === this.authorityGeneration && guard.sessionId === this.sessionIdentity
  }

  private isCurrentSnapshotRequest(guard: SnapshotRequestGuard): boolean {
    return guard.requestId === this.snapshotRequestId && this.isCurrent(guard)
  }

  private canApplyDurableSuccess(guard: RequestGuard, accountId: string): boolean {
    if (this.isCurrent(guard)) return true
    return this.view.snapshot?.userId === accountId
  }

  private isOnline(): boolean {
    return this.port.isOnline()
  }

  private offlineError(): ProfileRequestError {
    return new ProfileRequestError('OFFLINE_MUTATION_DISABLED', 'Это действие доступно только онлайн')
  }

  private recordError(error: unknown, guard: RequestGuard, area: 'snapshot' | 'sessions' | 'history' | 'mutation'): ProfileRequestError {
    const typed = this.asProfileError(error)
    if (this.isCurrent(guard)) {
      this.view.error = typed
      if (area === 'snapshot') this.view.snapshotError = typed
      if (area === 'sessions') this.view.sessionsError = typed
      if (area === 'history') this.view.historyError = typed
    }
    return typed
  }

  private async handleFailure(error: unknown, guard: RequestGuard, area: 'snapshot' | 'sessions' | 'history' | 'mutation'): Promise<ProfileRequestError> {
    const typed = this.recordError(error, guard, area)
    if (this.isSessionInvalidating(typed) && this.isCurrent(guard)) {
      this.invalidate('account-invalidated')
      this.view.error = typed
      if (area === 'snapshot' || area === 'mutation') this.view.snapshotError = typed
      if (area === 'sessions') this.view.sessionsError = typed
      if (area === 'history') this.view.historyError = typed
      await this.notifyInvalidated('account-invalidated')
    } else if (typed.code === 'SESSION_STATE_STALE' && this.isCurrent(guard)) {
      this.requestStaleReload()
    }
    if (typed.code === 'REFRESH_ALREADY_ROTATED') await this.port.onRefreshAlreadyRotated?.(typed)
    return typed
  }

  private async handleReadFailure(error: unknown, guard: RequestGuard, area: 'snapshot' | 'sessions' | 'history'): Promise<ProfileRequestError> {
    const typed = this.recordError(error, guard, area)
    if (this.isSessionInvalidating(typed) && this.isCurrent(guard)) {
      this.invalidate('account-invalidated')
      this.view.error = typed
      if (area === 'snapshot') this.view.snapshotError = typed
      if (area === 'sessions') this.view.sessionsError = typed
      if (area === 'history') this.view.historyError = typed
      await this.notifyInvalidated('account-invalidated')
    } else if (typed.code === 'SESSION_STATE_STALE' && this.isCurrent(guard)) {
      this.requestStaleReload()
    }
    if (typed.code === 'REFRESH_ALREADY_ROTATED') await this.port.onRefreshAlreadyRotated?.(typed)
    return typed
  }

  private async handleSnapshotFailure(error: unknown, guard: SnapshotRequestGuard): Promise<ProfileRequestError> {
    const typed = this.asProfileError(error)
    if (this.isCurrentSnapshotRequest(guard)) {
      this.view.error = typed
      this.view.snapshotError = typed
      if (this.isSessionInvalidating(typed)) {
        this.invalidate('account-invalidated')
        this.view.error = typed
        this.view.snapshotError = typed
        await this.notifyInvalidated('account-invalidated')
      } else if (typed.code === 'SESSION_STATE_STALE') {
        this.requestStaleReload()
      }
    }
    if (typed.code === 'REFRESH_ALREADY_ROTATED') await this.port.onRefreshAlreadyRotated?.(typed)
    return typed
  }

  private requestStaleReload(): void {
    if (this.staleReloadAttempts >= MAX_AUTOMATIC_STALE_RELOADS_PER_AUTHORITY || this.staleReloadPromise) return
    this.staleReloadAttempts += 1
    const request = this.loadSnapshotRequest(true)
    this.staleReloadPromise = request
    const settlement = request.then(() => undefined, () => undefined)
    this.staleReloadSettlement = settlement
    void settlement.finally(() => {
      if (this.staleReloadPromise === request) this.staleReloadPromise = null
    })
  }

  private async notifyInvalidated(reason: 'logout-all' | 'password-changed' | 'session-terminated' | 'account-invalidated'): Promise<void> {
    try {
      await this.port.onInvalidated?.(reason)
    } catch {
      // Local state is already invalidated; preserve the durable result.
    }
  }

  private isSessionInvalidating(error: ProfileRequestError): boolean {
    return error.code === 'INVALID_SESSION' || error.code === 'SESSION_REVOKED' || error.code === 'REFRESH_REJECTED' || error.code === 'ACCOUNT_INVALIDATED'
  }

  private asProfileError(error: unknown): ProfileRequestError {
    if (error instanceof ProfileRequestError) return error
    if (error instanceof Error) return new ProfileRequestError('NETWORK', error.message)
    return new ProfileRequestError('UNKNOWN', 'Не удалось выполнить запрос')
  }
}

export function isProfileRequestErrorCode(value: string): value is ProfileRequestErrorCode {
  return new Set<ProfileRequestErrorCode>([
    'SESSION_STATE_STALE', 'SESSION_VERSION_CONFLICT', 'REFRESH_ALREADY_ROTATED', 'ROLE_NOT_GRANTED',
    'ROLE_NOT_SELECTABLE', 'ROLE_READ_ONLY', 'BOOTSTRAP_SCOPE_DENIED', 'CURRENT_PASSWORD_INVALID',
    'PASSWORD_POLICY_VIOLATION', 'INVALID_CURSOR', 'AUTHORITY_UNAVAILABLE', 'OFFLINE_MUTATION_DISABLED',
    'ACCOUNT_INVALIDATED', 'INVALID_SESSION', 'SESSION_REVOKED', 'SESSION_NOT_FOUND', 'REFRESH_REJECTED', 'NETWORK', 'UNKNOWN',
  ]).has(value as ProfileRequestErrorCode)
}
