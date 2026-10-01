import type { StudentHomework, StudentSemesterSchedule } from '../api/types'

export interface PersistedStudentScope {
  userId: string
  activeRole: string
  groupId: string | null
  semesterId: string
}

export interface SemesterSnapshot {
  ownerId: string
  schedule: StudentSemesterSchedule
  etag: string | null
  /** Stable student/role/group/semester partition for persisted reads. */
  scopeKey?: string
  scope?: PersistedStudentScope
  homework?: StudentHomework | null
  cachedAt?: string
}

export type SnapshotOperationStatus = 'completed' | 'failed' | 'skipped'

export interface SnapshotOperationError {
  name: string
  message: string
}

export interface SnapshotOperationResult {
  status: SnapshotOperationStatus
  reason?: 'no-owner' | 'owner-replaced' | 'stale-generation'
  error?: SnapshotOperationError
}

/**
 * Truthful result for the two durable mutations involved in owner cleanup.
 * `safeOffline` is false for every partial or unresolved operation.
 */
export interface SnapshotCleanupResult {
  operation: 'clear' | 'switch'
  ownerId: string | null
  replacementOwnerId: string | null
  idbDelete: SnapshotOperationResult
  pointerCleanup: SnapshotOperationResult
  safeOffline: boolean
  retryRequired: boolean
  /** A context retry must never act on a later confirmed lifecycle. */
  expectedScopeKey?: string
  lifecycleGeneration?: number
  contextGuard?: SnapshotOperationResult
  previousGuardScopeKey?: string | null
}

/** Keeps the existing Promise<void> API fail-visible for auth.logout callers. */
export class SnapshotCleanupError extends Error {
  constructor(readonly result: SnapshotCleanupResult) {
    super('Локальная очистка не завершена')
    this.name = 'SnapshotCleanupError'
  }
}

const dbName = 'rct-student-mobile'
const storeName = 'semester-snapshots'
const currentOwnerKey = 'rct-student-mobile.current-owner'
const currentScopeKey = 'rct-student-mobile.current-scope'

interface ActiveSnapshotWrite {
  transaction: IDBTransaction
  committed: boolean
  abortRequested: boolean
}

/**
 * PWA-owned read model storage. Every mutating operation is serialized and
 * its transaction is awaited through `complete`, so a cleared owner cannot be
 * resurrected by a late request or by a pointer write that follows it.
 */
export class SemesterSnapshotStore {
  private lifecycleGeneration = 0
  private writeGeneration = 0
  private activeWrite: ActiveSnapshotWrite | null = null
  private operationQueue: Promise<unknown> = Promise.resolve()
  private readonly unresolvedOwnerIds = new Set<string>()
  private confirmedContext: { scopeKey: string; generation: number; previousGuardScopeKey?: string | null } | null = null
  private unresolvedContextGeneration: number | null = null

  read(ownerId: string, expectedScopeKey?: string): Promise<SemesterSnapshot | null> {
    return this.enqueue(async () => {
      if (this.hasUnresolvedCleanup(ownerId)) return null
      const snapshot = await this.readByOwner(ownerId)
      return expectedScopeKey && snapshot?.scopeKey !== expectedScopeKey ? null : snapshot
    })
  }

  readCurrent(expectedScopeKey?: string): Promise<SemesterSnapshot | null> {
    return this.enqueue(async () => {
      if (this.hasUnresolvedCleanup()) return null
      const ownerId = this.readCurrentOwner()
      if (!ownerId || this.unresolvedOwnerIds.has(ownerId)) return null
      const snapshot = await this.readByOwner(ownerId)
      const scopeKey = expectedScopeKey ?? this.currentContextScopeKey() ?? this.storage().getItem(currentScopeKey)
      return scopeKey && snapshot?.scopeKey !== scopeKey ? null : snapshot
    })
  }

  /** Retire the previous partition as soon as a new student context is confirmed,
   * before its first schedule request can fail. Keep a matching offline record. */
  clearMismatchedCurrent(expectedScopeKey: string): Promise<SnapshotCleanupResult | null> {
    this.invalidatePendingWrites()
    const capturedGeneration = ++this.lifecycleGeneration
    this.confirmedContext = { scopeKey: expectedScopeKey, generation: capturedGeneration }
    // Persist the authority boundary before awaiting an older queued IDB job.
    const guard = this.writeContextGuard(expectedScopeKey)
    return this.enqueue(() => this.performContextCheck(expectedScopeKey, capturedGeneration, undefined, guard))
  }

  write(snapshot: SemesterSnapshot): Promise<void> {
    const capturedGeneration = this.lifecycleGeneration
    const capturedWriteGeneration = this.writeGeneration
    return this.enqueue(async () => {
      if (capturedGeneration !== this.lifecycleGeneration || capturedWriteGeneration !== this.writeGeneration) return
      const expectedScopeKey = this.currentContextScopeKey()
      if (expectedScopeKey && snapshot.scopeKey !== expectedScopeKey) return
      const durableScopeKey = this.storage().getItem(currentScopeKey)
      if (durableScopeKey && durableScopeKey !== snapshot.scopeKey) return
      if (snapshot.scopeKey && !durableScopeKey) this.storage().setItem(currentScopeKey, snapshot.scopeKey)
      const writeResult = await this.withWriteStore(capturedWriteGeneration, snapshot.scopeKey, (store) => this.request(store.put(snapshot)))
      if (!writeResult) return
      if (capturedGeneration !== this.lifecycleGeneration) return
      if (snapshot.scopeKey && this.storage().getItem(currentScopeKey) !== snapshot.scopeKey) return
      this.writeCurrentOwner(snapshot.ownerId)
    })
  }

  /**
   * Synchronously invalidates queued writes and aborts the active write
   * transaction before its IndexedDB commit. A transaction that already
   * completed remains durable and is never compensated by a later delete.
   */
  invalidatePendingWrites(): void {
    this.writeGeneration += 1
    const activeWrite = this.activeWrite
    if (!activeWrite || activeWrite.committed || activeWrite.abortRequested) return
    activeWrite.abortRequested = true
    try {
      activeWrite.transaction.abort()
    } catch {
      // InvalidStateError means the transaction has already finished. The
      // completion handler marks that commit before this method can cancel it.
    }
  }

  switchTo(ownerId: string): Promise<void> {
    return this.switchToDetailed(ownerId).then((result) => {
      if (result.retryRequired) throw new SnapshotCleanupError(result)
    })
  }

  clear(ownerId?: string): Promise<void> {
    return this.clearDetailed(ownerId).then((result) => {
      if (result.retryRequired) throw new SnapshotCleanupError(result)
    })
  }

  /**
   * Starts a cleanup after synchronously invalidating pending writes and the
   * current lifecycle. The two durable operations run independently so an
   * IndexedDB abort cannot prevent pointer cleanup (or vice versa).
   */
  clearDetailed(ownerId?: string): Promise<SnapshotCleanupResult> {
    this.invalidatePendingWrites()
    const capturedGeneration = ++this.lifecycleGeneration
    return this.enqueue(() => this.performClear(ownerId, capturedGeneration))
  }

  /**
   * Owner replacement has the same fail-closed rule as denial cleanup: the
   * previous record and the current-owner pointer are attempted independently.
   */
  switchToDetailed(ownerId: string): Promise<SnapshotCleanupResult> {
    this.invalidatePendingWrites()
    const expectedScopeKey = this.currentContextScopeKey()
    const previousGuardScopeKey = this.confirmedContext?.previousGuardScopeKey
    let guardReadError: unknown
    if (expectedScopeKey) {
      try {
        const durableGuard = this.storage().getItem(currentScopeKey)
        // A confirmed scope is not renewed authority after a delayed GET.
        // Permit only our guard or the observed predecessor of our own failed
        // guard write; another store's newer guard makes switch/write inert.
        if (durableGuard !== expectedScopeKey && durableGuard !== previousGuardScopeKey) {
          return Promise.resolve(makeCleanupResult('switch', null, ownerId, skipped('owner-replaced'), skipped('owner-replaced')))
        }
      } catch (error) {
        guardReadError = error
      }
    }
    const capturedGeneration = ++this.lifecycleGeneration
    let guard: ReturnType<SemesterSnapshotStore['writeContextGuard']> | undefined
    if (expectedScopeKey) {
      this.confirmedContext = { scopeKey: expectedScopeKey, generation: capturedGeneration }
      if (guardReadError) {
        this.unresolvedContextGeneration = capturedGeneration
        return Promise.resolve({
          ...makeCleanupResult('switch', null, ownerId, skipped('no-owner'), failed(guardReadError)),
          expectedScopeKey, lifecycleGeneration: capturedGeneration, previousGuardScopeKey,
        })
      }
      guard = this.writeContextGuard(expectedScopeKey)
    }
    return this.enqueue(async () => {
      if (expectedScopeKey) {
        const context = await this.performContextCheck(expectedScopeKey, capturedGeneration, undefined, guard)
        if (context?.retryRequired || context?.pointerCleanup.reason === 'owner-replaced') return context
      }
      const result = await this.performSwitch(ownerId, capturedGeneration, expectedScopeKey)
      return expectedScopeKey ? { ...result, expectedScopeKey, lifecycleGeneration: capturedGeneration } : result
    })
  }

  /**
   * Retries only the recorded local cleanup. It does not invalidate the live
   * replacement owner's pending writes or issue any API request.
   */
  retryCleanup(result: SnapshotCleanupResult): Promise<SnapshotCleanupResult> {
    if (result.expectedScopeKey !== undefined && result.lifecycleGeneration !== undefined) {
      return this.enqueue(async () => {
        const replaced = (): SnapshotCleanupResult => makeCleanupResult('clear', null, null, skipped('owner-replaced'), skipped('owner-replaced'))
        if (result.lifecycleGeneration !== this.lifecycleGeneration) return replaced()
        try {
          const guard = this.storage().getItem(currentScopeKey)
          if (guard && guard !== result.expectedScopeKey && guard !== result.previousGuardScopeKey) return replaced()
        } catch (error) {
          this.unresolvedContextGeneration = this.lifecycleGeneration
          return { ...result, pointerCleanup: failed(error), safeOffline: false, retryRequired: true }
        }
        return await this.performContextCheck(result.expectedScopeKey!, result.lifecycleGeneration!, result.ownerId ?? undefined)
          ?? makeCleanupResult('clear', null, null, skipped('no-owner'), skipped('no-owner'))
      })
    }
    const capturedGeneration = this.lifecycleGeneration
    return this.enqueue(() => result.operation === 'clear'
      ? this.performClear(result.ownerId ?? undefined, capturedGeneration)
      : this.performSwitchRetry(result.ownerId, result.replacementOwnerId, capturedGeneration))
  }

  /** True while this runtime knows that an owner or replacement cleanup is unresolved. */
  hasUnresolvedCleanup(ownerId?: string): boolean {
    return this.unresolvedContextGeneration === this.lifecycleGeneration
      || (ownerId === undefined ? this.unresolvedOwnerIds.size > 0 : this.unresolvedOwnerIds.has(ownerId))
  }

  private currentContextScopeKey(): string | undefined {
    return this.confirmedContext?.generation === this.lifecycleGeneration ? this.confirmedContext.scopeKey : undefined
  }

  private writeContextGuard(scopeKey: string): { outcome: SnapshotOperationResult; previousScopeKey?: string | null } {
    try {
      this.storage().setItem(currentScopeKey, scopeKey)
      return { outcome: completed() }
    } catch (error) {
      let previousScopeKey: string | null | undefined
      try { previousScopeKey = this.storage().getItem(currentScopeKey) } catch { /* Unknown remains fail-closed. */ }
      return { outcome: failed(error), previousScopeKey }
    }
  }

  private async performContextCheck(
    expectedScopeKey: string,
    capturedGeneration: number,
    retryOwnerId?: string,
    capturedGuard?: ReturnType<SemesterSnapshotStore['writeContextGuard']>,
  ): Promise<SnapshotCleanupResult | null> {
    // A recorded unknown-pointer failure has no owner to compare. Its retry
    // must be tied to the original lifecycle, never to the latest pointer.
    if (capturedGeneration !== this.lifecycleGeneration) {
      return makeCleanupResult('clear', null, null, skipped('owner-replaced'), skipped('owner-replaced'))
    }
    const guard = capturedGuard ?? this.writeContextGuard(expectedScopeKey)
    const contextGuard = guard.outcome
    if (this.confirmedContext?.generation === capturedGeneration) {
      this.confirmedContext.previousGuardScopeKey = contextGuard.status === 'failed' ? guard.previousScopeKey : undefined
    }
    const withContext = (cleanup: SnapshotCleanupResult): SnapshotCleanupResult => {
      const retryRequired = cleanup.retryRequired || contextGuard.status === 'failed'
      if (capturedGeneration === this.lifecycleGeneration) {
        this.unresolvedContextGeneration = retryRequired ? capturedGeneration : null
      }
      return {
        ...cleanup, expectedScopeKey, lifecycleGeneration: capturedGeneration,
        contextGuard, safeOffline: !retryRequired, retryRequired,
        previousGuardScopeKey: guard.previousScopeKey,
      }
    }
    let ownerId: string | null
    try {
      ownerId = this.readCurrentOwner()
    } catch (error) {
      return withContext(makeCleanupResult('clear', null, null, skipped('no-owner'), failed(error)))
    }
    let snapshot: SemesterSnapshot | null = null
    const recordOwnerId = ownerId ?? retryOwnerId
    if (recordOwnerId) {
      try {
        snapshot = await this.readByOwner(recordOwnerId)
      } catch {
        // An unreadable record cannot establish an offline context. Still
        // attempt pointer retirement independently of IndexedDB cleanup.
      }
    }
    if (capturedGeneration !== this.lifecycleGeneration) {
      return makeCleanupResult('clear', null, null, skipped('owner-replaced'), skipped('owner-replaced'))
    }
    try {
      const durableGuard = this.storage().getItem(currentScopeKey)
      if (durableGuard !== expectedScopeKey
        && !(contextGuard.status === 'failed' && durableGuard === guard.previousScopeKey)) {
        return makeCleanupResult('clear', null, null, skipped('owner-replaced'), skipped('owner-replaced'))
      }
    } catch (error) {
      return withContext(makeCleanupResult('clear', ownerId, null, skipped('no-owner'), failed(error)))
    }
    if (snapshot?.scopeKey === expectedScopeKey && contextGuard.status === 'completed') {
      this.unresolvedContextGeneration = null
      if (recordOwnerId) this.unresolvedOwnerIds.delete(recordOwnerId)
      return null
    }
    const target = ownerId ?? retryOwnerId
    if (!target) {
      if (contextGuard.status === 'failed') return withContext(makeCleanupResult('clear', null, null, skipped('no-owner'), skipped('no-owner')))
      this.unresolvedContextGeneration = null
      return null
    }
    const cleanup = await this.performClear(target, capturedGeneration, {
      expectedScopeKey, previousSnapshotScopeKey: snapshot?.scopeKey,
      previousGuardScopeKey: contextGuard.status === 'failed' ? guard.previousScopeKey : undefined,
    })
    return withContext(cleanup)
  }

  private enqueue<T>(work: () => Promise<T>): Promise<T> {
    const next = this.operationQueue.then(work, work)
    this.operationQueue = next.then(() => undefined, () => undefined)
    return next
  }

  private async readByOwner(ownerId: string): Promise<SemesterSnapshot | null> {
    return this.withStore('readonly', (store) => this.request<SemesterSnapshot | undefined>(store.get(ownerId)))
      .then((value) => value ?? null)
  }

  private async performClear(
    requestedOwnerId: string | undefined,
    capturedGeneration: number,
    context?: { expectedScopeKey: string; previousSnapshotScopeKey: string | undefined; previousGuardScopeKey?: string | null },
  ): Promise<SnapshotCleanupResult> {
    let target: string | null = requestedOwnerId ?? null
    let pointerReadError: unknown = null
    if (!target) {
      try {
        target = this.readCurrentOwner()
      } catch (error) {
        pointerReadError = error
      }
    }

    if (!target) {
      const idbDelete = skipped('no-owner')
      const pointerCleanup = pointerReadError ? failed(pointerReadError) : skipped('no-owner')
      return makeCleanupResult('clear', null, null, idbDelete, pointerCleanup)
    }

    this.unresolvedOwnerIds.add(target)
    const [idbDeleteOutcome, pointerCleanupOutcome] = await Promise.allSettled([
      this.deleteOwner(target, context ? { ...context, generation: capturedGeneration } : undefined),
      Promise.resolve().then(() => this.removeCurrentOwnerIf(target, capturedGeneration, context?.expectedScopeKey, context?.previousGuardScopeKey)),
    ])
    const idbDelete = settledOperation(idbDeleteOutcome)
    const pointerCleanup = settledOperation(pointerCleanupOutcome)
    const result = makeCleanupResult('clear', target, null, idbDelete, pointerCleanup)
    this.recordOwnerCleanup(target, result)
    return result
  }

  private async performSwitch(ownerId: string, capturedGeneration: number, expectedScopeKey?: string): Promise<SnapshotCleanupResult> {
    let previous: string | null = null
    let pointerReadError: unknown = null
    try {
      if (expectedScopeKey && this.storage().getItem(currentScopeKey) !== expectedScopeKey) {
        return makeCleanupResult('switch', null, ownerId, skipped('owner-replaced'), skipped('owner-replaced'))
      }
      previous = this.readCurrentOwner()
    } catch (error) {
      pointerReadError = error
    }

    if (pointerReadError) {
      const result = makeCleanupResult(
        'switch',
        null,
        ownerId,
        skipped('no-owner'),
        failed(pointerReadError),
      )
      this.unresolvedOwnerIds.add(ownerId)
      return result
    }

    const idbDeletePromise = previous && previous !== ownerId
      ? this.deleteOwner(previous, expectedScopeKey ? { generation: capturedGeneration, expectedScopeKey } : undefined)
      : Promise.resolve(undefined)
    const [idbDeleteOutcome, pointerCleanupOutcome] = await Promise.allSettled([
      idbDeletePromise,
      Promise.resolve().then(() => this.writeCurrentOwnerIfCurrent(previous, ownerId, capturedGeneration, expectedScopeKey)),
    ])
    const idbDelete = previous && previous !== ownerId
      ? settledOperation(idbDeleteOutcome)
      : skipped(previous ? 'owner-replaced' : 'no-owner')
    const pointerCleanup = settledOperation(pointerCleanupOutcome)
    const result = makeCleanupResult('switch', previous, ownerId, idbDelete, pointerCleanup)
    this.recordSwitchCleanup(previous, ownerId, result)
    return result
  }

  private async performSwitchRetry(
    previous: string | null,
    ownerId: string | null,
    capturedGeneration: number,
  ): Promise<SnapshotCleanupResult> {
    const replacement = ownerId ?? ''
    if (!replacement) {
      const result = makeCleanupResult('switch', previous, null, skipped('no-owner'), failed(new Error('Replacement owner is unavailable')))
      if (previous) this.unresolvedOwnerIds.add(previous)
      return result
    }
    const shouldDeletePrevious = Boolean(previous && previous !== replacement)
    const [idbDeleteOutcome, pointerCleanupOutcome] = await Promise.allSettled([
      shouldDeletePrevious ? this.deleteOwner(previous!) : Promise.resolve(undefined),
      Promise.resolve().then(() => this.writeReplacementIfSafe(previous, replacement, capturedGeneration)),
    ])
    const idbDelete = shouldDeletePrevious ? settledOperation(idbDeleteOutcome) : skipped(previous ? 'owner-replaced' : 'no-owner')
    const pointerCleanup = settledOperation(pointerCleanupOutcome)
    const result = makeCleanupResult('switch', previous, replacement, idbDelete, pointerCleanup)
    this.recordSwitchCleanup(previous, replacement, result)
    return result
  }

  private async deleteOwner(
    ownerId: string,
    context?: { previousSnapshotScopeKey?: string; expectedScopeKey?: string; generation: number },
  ): Promise<SnapshotOperationResult | void> {
    return this.withStore('readwrite', async (store) => {
      if (context) {
        if (context.generation !== this.lifecycleGeneration
          || (!('previousSnapshotScopeKey' in context) && context.expectedScopeKey
            && this.storage().getItem(currentScopeKey) !== context.expectedScopeKey)) {
          return skipped('owner-replaced')
        }
        if ('previousSnapshotScopeKey' in context) {
          const current = await this.request<SemesterSnapshot | undefined>(store.get(ownerId))
          if (context.generation !== this.lifecycleGeneration || current?.scopeKey !== context.previousSnapshotScopeKey) return skipped('owner-replaced')
        }
      }
      await this.request(store.delete(ownerId))
    })
  }

  private removeCurrentOwnerIf(ownerId: string, capturedGeneration: number, expectedScopeKey?: string, previousGuardScopeKey?: string | null): SnapshotOperationResult {
    if (capturedGeneration !== this.lifecycleGeneration) return skipped('stale-generation')
    if (expectedScopeKey) {
      const currentGuard = this.storage().getItem(currentScopeKey)
      if (currentGuard !== expectedScopeKey && currentGuard !== previousGuardScopeKey) return skipped('owner-replaced')
    }
    const current = this.readCurrentOwner()
    if (current !== ownerId) return skipped('owner-replaced')
    this.removeCurrentOwner()
    return completed()
  }

  private writeCurrentOwnerIfCurrent(previous: string | null, ownerId: string, capturedGeneration: number, expectedScopeKey?: string): SnapshotOperationResult {
    if (capturedGeneration !== this.lifecycleGeneration) return skipped('stale-generation')
    if (expectedScopeKey && this.storage().getItem(currentScopeKey) !== expectedScopeKey) return skipped('owner-replaced')
    const current = this.readCurrentOwner()
    if (current !== previous) return skipped('owner-replaced')
    this.writeCurrentOwner(ownerId)
    return completed()
  }

  private writeReplacementIfSafe(previous: string | null, ownerId: string, capturedGeneration: number): SnapshotOperationResult {
    if (capturedGeneration !== this.lifecycleGeneration) return skipped('stale-generation')
    const current = this.readCurrentOwner()
    if (current === ownerId) return skipped('owner-replaced')
    if (current !== previous && current !== null) return skipped('owner-replaced')
    this.writeCurrentOwner(ownerId)
    return completed()
  }

  private recordOwnerCleanup(ownerId: string, result: SnapshotCleanupResult): void {
    if (result.retryRequired) this.unresolvedOwnerIds.add(ownerId)
    else this.unresolvedOwnerIds.delete(ownerId)
  }

  private recordSwitchCleanup(previous: string | null, replacement: string, result: SnapshotCleanupResult): void {
    if (result.retryRequired) {
      if (previous) this.unresolvedOwnerIds.add(previous)
      else this.unresolvedOwnerIds.add(replacement)
      return
    }
    if (previous) this.unresolvedOwnerIds.delete(previous)
    this.unresolvedOwnerIds.delete(replacement)
  }

  private async withWriteStore<T>(capturedWriteGeneration: number, expectedScopeKey: string | undefined, work: (store: IDBObjectStore) => Promise<T>): Promise<{ result: T } | null> {
    const database = await this.open()
    try {
      if (capturedWriteGeneration !== this.writeGeneration) return null
      // Another tab may have confirmed/persisted a new context while open()
      // was pending. Check before acquiring the write transaction, then again
      // before publishing its current-owner pointer in write().
      if (expectedScopeKey && this.storage().getItem(currentScopeKey) !== expectedScopeKey) return null
      const transaction = database.transaction(storeName, 'readwrite')
      const activeWrite: ActiveSnapshotWrite = { transaction, committed: false, abortRequested: false }
      this.activeWrite = activeWrite
      const transactionComplete = this.waitForTransaction(transaction, () => {
        activeWrite.committed = true
        if (this.activeWrite === activeWrite) this.activeWrite = null
      })
      try {
        const result = await work(transaction.objectStore(storeName))
        await transactionComplete
        return { result }
      } catch (error) {
        await transactionComplete.catch(() => undefined)
        throw error
      } finally {
        if (this.activeWrite === activeWrite) this.activeWrite = null
      }
    } finally {
      database.close()
    }
  }

  private async withStore<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => Promise<T>): Promise<T> {
    const database = await this.open()
    try {
      const transaction = database.transaction(storeName, mode)
      const transactionComplete = this.waitForTransaction(transaction)
      try {
        const result = await work(transaction.objectStore(storeName))
        await transactionComplete
        return result
      } catch (error) {
        // A request rejection can abort the transaction after its request
        // promise has already rejected. Consume that terminal event before
        // closing the database so the completion promise cannot become an
        // unhandled rejection and a later queued operation can continue.
        await transactionComplete.catch(() => undefined)
        throw error
      }
    } finally {
      // Closing after complete/abort is required; closing immediately after a
      // request success can leave a successful-looking write uncommitted.
      database.close()
    }
  }

  private waitForTransaction(transaction: IDBTransaction, onComplete?: () => void): Promise<void> {
    return new Promise((resolve, reject) => {
      transaction.oncomplete = () => {
        onComplete?.()
        resolve()
      }
      transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'))
      transaction.onabort = () => reject(transaction.error ?? new Error('IndexedDB transaction aborted'))
    })
  }

  private open(): Promise<IDBDatabase> {
    if (typeof indexedDB === 'undefined') return Promise.reject(new Error('IndexedDB is unavailable'))
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(dbName, 1)
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains(storeName)) request.result.createObjectStore(storeName, { keyPath: 'ownerId' })
      }
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error ?? new Error('IndexedDB open failed'))
    })
  }

  private request<T>(request: IDBRequest<T>): Promise<T> {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'))
    })
  }

  private readCurrentOwner(): string | null {
    return this.storage().getItem(currentOwnerKey)
  }

  private writeCurrentOwner(ownerId: string): void {
    this.storage().setItem(currentOwnerKey, ownerId)
  }

  private removeCurrentOwner(): void {
    this.storage().removeItem(currentOwnerKey)
  }

  private storage(): Storage {
    if (typeof localStorage === 'undefined') throw new Error('Local storage is unavailable')
    return localStorage
  }
}

function completed(): SnapshotOperationResult {
  return { status: 'completed' }
}

function skipped(reason: SnapshotOperationResult['reason']): SnapshotOperationResult {
  return reason ? { status: 'skipped', reason } : { status: 'skipped' }
}

function failed(error: unknown): SnapshotOperationResult {
  return {
    status: 'failed',
    error: {
      name: error instanceof Error ? error.name : 'Error',
      message: error instanceof Error ? error.message : String(error),
    },
  }
}

function settledOperation(
  outcome: PromiseSettledResult<SnapshotOperationResult | void>,
): SnapshotOperationResult {
  return outcome.status === 'fulfilled'
    ? outcome.value ?? completed()
    : failed(outcome.reason)
}

function makeCleanupResult(
  operation: SnapshotCleanupResult['operation'],
  ownerId: string | null,
  replacementOwnerId: string | null,
  idbDelete: SnapshotOperationResult,
  pointerCleanup: SnapshotOperationResult,
): SnapshotCleanupResult {
  const retryRequired = [idbDelete, pointerCleanup].some((item) => item.status === 'failed'
    || item.reason === 'stale-generation')
  return {
    operation,
    ownerId,
    replacementOwnerId,
    idbDelete,
    pointerCleanup,
    safeOffline: !retryRequired,
    retryRequired,
  }
}
