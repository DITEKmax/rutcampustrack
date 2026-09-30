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

  read(ownerId: string, expectedScopeKey?: string): Promise<SemesterSnapshot | null> {
    return this.enqueue(async () => {
      if (this.unresolvedOwnerIds.has(ownerId)) return null
      const snapshot = await this.readByOwner(ownerId)
      return expectedScopeKey && snapshot?.scopeKey !== expectedScopeKey ? null : snapshot
    })
  }

  readCurrent(expectedScopeKey?: string): Promise<SemesterSnapshot | null> {
    return this.enqueue(async () => {
      const ownerId = this.readCurrentOwner()
      if (!ownerId || this.unresolvedOwnerIds.has(ownerId)) return null
      const snapshot = await this.readByOwner(ownerId)
      return expectedScopeKey && snapshot?.scopeKey !== expectedScopeKey ? null : snapshot
    })
  }

  /** Retire the previous partition as soon as a new student context is confirmed,
   * before its first schedule request can fail. Keep a matching offline record. */
  clearMismatchedCurrent(expectedScopeKey: string): Promise<SnapshotCleanupResult | null> {
    this.invalidatePendingWrites()
    const capturedGeneration = ++this.lifecycleGeneration
    return this.enqueue(async () => {
      const ownerId = this.readCurrentOwner()
      if (!ownerId) return null
      let snapshot: SemesterSnapshot | null = null
      try {
        snapshot = await this.readByOwner(ownerId)
      } catch {
        // An unreadable record cannot establish an offline context. Still
        // attempt pointer retirement independently of IndexedDB cleanup.
      }
      if (capturedGeneration !== this.lifecycleGeneration) return null
      if (snapshot?.scopeKey === expectedScopeKey) return null
      return this.performClear(ownerId, capturedGeneration)
    })
  }

  write(snapshot: SemesterSnapshot): Promise<void> {
    const capturedGeneration = this.lifecycleGeneration
    const capturedWriteGeneration = this.writeGeneration
    return this.enqueue(async () => {
      if (capturedGeneration !== this.lifecycleGeneration || capturedWriteGeneration !== this.writeGeneration) return
      const writeResult = await this.withWriteStore(capturedWriteGeneration, (store) => this.request(store.put(snapshot)))
      if (!writeResult) return
      if (capturedGeneration !== this.lifecycleGeneration) return
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
    const capturedGeneration = ++this.lifecycleGeneration
    return this.enqueue(() => this.performSwitch(ownerId, capturedGeneration))
  }

  /**
   * Retries only the recorded local cleanup. It does not invalidate the live
   * replacement owner's pending writes or issue any API request.
   */
  retryCleanup(result: SnapshotCleanupResult): Promise<SnapshotCleanupResult> {
    const capturedGeneration = this.lifecycleGeneration
    return this.enqueue(() => result.operation === 'clear'
      ? this.performClear(result.ownerId ?? undefined, capturedGeneration)
      : this.performSwitchRetry(result.ownerId, result.replacementOwnerId, capturedGeneration))
  }

  /** True while this runtime knows that an owner or replacement cleanup is unresolved. */
  hasUnresolvedCleanup(ownerId?: string): boolean {
    return ownerId === undefined ? this.unresolvedOwnerIds.size > 0 : this.unresolvedOwnerIds.has(ownerId)
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

  private async performClear(requestedOwnerId: string | undefined, capturedGeneration: number): Promise<SnapshotCleanupResult> {
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
      this.deleteOwner(target),
      Promise.resolve().then(() => this.removeCurrentOwnerIf(target, capturedGeneration)),
    ])
    const idbDelete = settledOperation(idbDeleteOutcome)
    const pointerCleanup = settledOperation(pointerCleanupOutcome)
    const result = makeCleanupResult('clear', target, null, idbDelete, pointerCleanup)
    this.recordOwnerCleanup(target, result)
    return result
  }

  private async performSwitch(ownerId: string, capturedGeneration: number): Promise<SnapshotCleanupResult> {
    let previous: string | null = null
    let pointerReadError: unknown = null
    try {
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
      ? this.deleteOwner(previous)
      : Promise.resolve(undefined)
    const [idbDeleteOutcome, pointerCleanupOutcome] = await Promise.allSettled([
      idbDeletePromise,
      Promise.resolve().then(() => this.writeCurrentOwnerIfCurrent(previous, ownerId, capturedGeneration)),
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

  private async deleteOwner(ownerId: string): Promise<void> {
    await this.withStore('readwrite', (store) => this.request(store.delete(ownerId)))
  }

  private removeCurrentOwnerIf(ownerId: string, capturedGeneration: number): SnapshotOperationResult {
    if (capturedGeneration !== this.lifecycleGeneration) return skipped('stale-generation')
    const current = this.readCurrentOwner()
    if (current !== ownerId) return skipped('owner-replaced')
    this.removeCurrentOwner()
    return completed()
  }

  private writeCurrentOwnerIfCurrent(previous: string | null, ownerId: string, capturedGeneration: number): SnapshotOperationResult {
    if (capturedGeneration !== this.lifecycleGeneration) return skipped('stale-generation')
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

  private async withWriteStore<T>(capturedWriteGeneration: number, work: (store: IDBObjectStore) => Promise<T>): Promise<{ result: T } | null> {
    const database = await this.open()
    try {
      if (capturedWriteGeneration !== this.writeGeneration) return null
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
