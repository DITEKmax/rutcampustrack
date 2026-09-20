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

const dbName = 'rct-student-mobile'
const storeName = 'semester-snapshots'
const currentOwnerKey = 'rct-student-mobile.current-owner'

/**
 * PWA-owned read model storage. Every mutating operation is serialized and
 * its transaction is awaited through `complete`, so a cleared owner cannot be
 * resurrected by a late request or by a pointer write that follows it.
 */
export class SemesterSnapshotStore {
  private lifecycleGeneration = 0
  private operationQueue: Promise<unknown> = Promise.resolve()

  read(ownerId: string, expectedScopeKey?: string): Promise<SemesterSnapshot | null> {
    return this.enqueue(async () => {
      const snapshot = await this.readByOwner(ownerId)
      return expectedScopeKey && snapshot?.scopeKey !== expectedScopeKey ? null : snapshot
    })
  }

  readCurrent(expectedScopeKey?: string): Promise<SemesterSnapshot | null> {
    return this.enqueue(async () => {
      const ownerId = this.readCurrentOwner()
      if (!ownerId) return null
      const snapshot = await this.readByOwner(ownerId)
      return expectedScopeKey && snapshot?.scopeKey !== expectedScopeKey ? null : snapshot
    })
  }

  write(snapshot: SemesterSnapshot): Promise<void> {
    const capturedGeneration = this.lifecycleGeneration
    return this.enqueue(async () => {
      if (capturedGeneration !== this.lifecycleGeneration) return
      await this.withStore('readwrite', (store) => this.request(store.put(snapshot)))
      if (capturedGeneration !== this.lifecycleGeneration) return
      this.writeCurrentOwner(snapshot.ownerId)
    })
  }

  switchTo(ownerId: string): Promise<void> {
    const capturedGeneration = ++this.lifecycleGeneration
    return this.enqueue(async () => {
      const previous = this.readCurrentOwner()
      if (previous && previous !== ownerId) {
        await this.withStore('readwrite', (store) => this.request(store.delete(previous)))
      }
      if (capturedGeneration !== this.lifecycleGeneration) return
      this.writeCurrentOwner(ownerId)
    })
  }

  clear(ownerId?: string): Promise<void> {
    const capturedGeneration = ++this.lifecycleGeneration
    return this.enqueue(async () => {
      const target = ownerId ?? this.readCurrentOwner()
      if (target) await this.withStore('readwrite', (store) => this.request(store.delete(target)))
      if (capturedGeneration !== this.lifecycleGeneration) return
      if (!ownerId || ownerId === this.readCurrentOwner()) this.removeCurrentOwner()
    })
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

  private async withStore<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => Promise<T>): Promise<T> {
    const database = await this.open()
    let transaction: IDBTransaction | null = null
    try {
      transaction = database.transaction(storeName, mode)
      const transactionComplete = this.waitForTransaction(transaction)
      const result = await work(transaction.objectStore(storeName))
      await transactionComplete
      return result
    } finally {
      // Closing after complete/abort is required; closing immediately after a
      // request success can leave a successful-looking write uncommitted.
      database.close()
    }
  }

  private waitForTransaction(transaction: IDBTransaction): Promise<void> {
    return new Promise((resolve, reject) => {
      transaction.oncomplete = () => resolve()
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
