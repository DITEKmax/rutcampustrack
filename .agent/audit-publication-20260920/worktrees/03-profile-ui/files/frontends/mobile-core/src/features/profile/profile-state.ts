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
  mutationBusy: 'role' | 'password' | 'logout-all' | null
}

interface RequestGuard {
  generation: number
  sessionId: string | null
}

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
  }

  private authorityGeneration = 0
  private sessionIdentity: string | null = null
  private snapshotRequestId = 0
  private staleReloadAttempts = 0
  private staleReloadPromise: Promise<ProfileSnapshot> | null = null
  private sessionsRequest: Promise<ProfileSessionsPage> | null = null
  private sessionsRequestGuard: RequestGuard | null = null

  constructor(private readonly port: ProfilePort) {}

  async loadSnapshot(): Promise<ProfileSnapshot> {
    return this.loadSnapshotRequest(false)
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
      if (this.view.mutationBusy === 'role') this.view.mutationBusy = null
    }
  }

  async loadSessions(input?: ProfilePageRequest): Promise<ProfileSessionsPage> {
    const guard = this.requireSessionGuard()
    if (this.sessionsRequest && this.sessionsRequestGuard && this.isCurrent(this.sessionsRequestGuard)) {
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

  private async loadSessionsPage(input: ProfilePageRequest | undefined, guard: RequestGuard): Promise<ProfileSessionsPage> {
    this.view.sessionsStatus = 'loading'
    this.view.sessionsError = null
    this.view.error = null
    try {
      const page = await this.port.listSessions(input)
      if (this.isCurrent(guard)) {
        this.view.sessions = input?.cursor ? [...this.view.sessions, ...page.items] : page.items
        this.view.sessionsNextCursor = page.nextCursor
        this.view.sessionsStatus = 'ready'
      }
      return page
    } catch (error) {
      const typed = await this.handleReadFailure(error, guard, 'sessions')
      if (this.isCurrent(guard)) this.view.sessionsStatus = 'error'
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
    this.view.mutationBusy = 'password'
    this.view.error = null
    try {
      await this.port.changePassword(input)
      if (!this.canApplyDurableSuccess(guard, accountId)) return
      this.invalidate('password-changed')
      await this.notifyInvalidated('password-changed')
    } catch (error) {
      const typed = await this.handleFailure(error, guard, 'snapshot')
      throw typed
    } finally {
      if (this.view.mutationBusy === 'password') this.view.mutationBusy = null
    }
  }

  async logoutAll(): Promise<void> {
    const snapshot = this.requireSnapshot()
    if (!this.isOnline()) throw this.offlineError()
    if (this.view.mutationBusy !== null) throw new ProfileRequestError('UNKNOWN', 'Операция уже выполняется')
    const guard = this.currentGuard()
    const accountId = snapshot.userId
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
      if (this.view.mutationBusy === 'logout-all') this.view.mutationBusy = null
    }
  }

  invalidate(reason: 'logout-all' | 'password-changed' | 'account-invalidated' = 'account-invalidated'): void {
    this.authorityGeneration += 1
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

  private recordError(error: unknown, guard: RequestGuard, area: 'snapshot' | 'sessions' | 'history'): ProfileRequestError {
    const typed = this.asProfileError(error)
    if (this.isCurrent(guard)) {
      this.view.error = typed
      if (area === 'snapshot') this.view.snapshotError = typed
      if (area === 'sessions') this.view.sessionsError = typed
      if (area === 'history') this.view.historyError = typed
    }
    return typed
  }

  private async handleFailure(error: unknown, guard: RequestGuard, area: 'snapshot' | 'sessions' | 'history'): Promise<ProfileRequestError> {
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
    void request.catch(() => undefined).finally(() => {
      if (this.staleReloadPromise === request) this.staleReloadPromise = null
    })
  }

  private async notifyInvalidated(reason: 'logout-all' | 'password-changed' | 'account-invalidated'): Promise<void> {
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
    'ACCOUNT_INVALIDATED', 'INVALID_SESSION', 'SESSION_REVOKED', 'REFRESH_REJECTED', 'NETWORK', 'UNKNOWN',
  ]).has(value as ProfileRequestErrorCode)
}
