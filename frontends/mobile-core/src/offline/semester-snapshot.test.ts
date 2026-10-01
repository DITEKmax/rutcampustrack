import { afterEach, describe, expect, it } from 'vitest'
import type { StudentSemesterSchedule } from '../api/types'
import { SemesterSnapshotStore, type SemesterSnapshot } from './semester-snapshot'

const currentOwnerKey = 'rct-student-mobile.current-owner'
const currentScopeKey = 'rct-student-mobile.current-scope'

type Handler = ((event: Event) => void) | null

class FakeRequest {
  result: unknown = undefined
  error: DOMException | null = null
  onsuccess: Handler = null
  onerror: Handler = null

  resolve(result?: unknown): void {
    this.result = result
    this.onsuccess?.(new Event('success'))
  }
}

class FakeOpenRequest extends FakeRequest {
  result: IDBDatabase

  constructor(database: IDBDatabase) {
    super()
    this.result = database
  }

  onupgradeneeded: Handler = null
}

class FakeStorage {
  private readonly values = new Map<string, string>()
  setItemCalls = 0
  removeItemCalls = 0
  setItemFailures = 0
  removeItemFailures = 0
  getItemFailures = 0
  scopeSetFailures = 0

  seed(key: string, value: string): void {
    this.values.set(key, value)
  }

  getItem(key: string): string | null {
    if (this.getItemFailures > 0) {
      this.getItemFailures -= 1
      throw new DOMException('controlled current-owner read failure', 'SecurityError')
    }
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    if (key === currentScopeKey && this.scopeSetFailures > 0) {
      this.scopeSetFailures -= 1
      throw new DOMException('controlled current-scope write failure', 'QuotaExceededError')
    }
    if (key === currentOwnerKey) this.setItemCalls += 1
    if (key === currentOwnerKey && this.setItemFailures > 0) {
      this.setItemFailures -= 1
      throw new DOMException('controlled current-owner write failure', 'QuotaExceededError')
    }
    this.values.set(key, value)
  }

  removeItem(key: string): void {
    if (key === currentOwnerKey) this.removeItemCalls += 1
    if (key === currentOwnerKey && this.removeItemFailures > 0) {
      this.removeItemFailures -= 1
      throw new DOMException('controlled current-owner remove failure', 'QuotaExceededError')
    }
    this.values.delete(key)
  }
}

interface FakeControls {
  deleteFailures: number
  holdOpen: boolean
}

interface FakeEnvironment {
  database: IDBDatabase & {
    records: Map<string, unknown>
    transactionCalls: number
  putCalls: number
  deleteCalls: number
    controls: FakeControls
  }
  storage: FakeStorage
  openRequests: FakeOpenRequest[]
}

function makeSnapshot(ownerId: string, serverNow: string): SemesterSnapshot {
  const schedule = {
    semester: { id: '9', name: 'Осень 2026', startsOn: '2026-09-01', endsOn: '2026-12-31' },
    dateFrom: '2026-09-01',
    dateTo: '2026-12-31',
    updatedAt: '2026-09-08T08:25:00Z',
    lessons: [],
    _links: { self: { href: 'https://example.test/schedule' } },
  } as StudentSemesterSchedule
  return {
    ownerId,
    scopeKey: JSON.stringify(['student-read-model-v1', ownerId, 'STUDENT', '17', '9']),
    scope: { userId: ownerId, activeRole: 'STUDENT', groupId: '17', semesterId: '9' },
    schedule,
    etag: '"lifecycle-schedule-v1"',
    homework: null,
    cachedAt: serverNow,
  }
}

function installEnvironment(autoResolveOpen: boolean): FakeEnvironment {
  const records = new Map<string, unknown>([['42', makeSnapshot('42', '2026-09-08T08:30:00Z')]])
  const openRequests: FakeOpenRequest[] = []
  const controls: FakeControls = { deleteFailures: 0, holdOpen: false }
  const database = {
    records,
    controls,
    transactionCalls: 0,
    putCalls: 0,
    deleteCalls: 0,
    objectStoreNames: { contains: () => true },
    transaction: () => {
      database.transactionCalls += 1
      const transaction = {
        oncomplete: null as Handler,
        onerror: null as Handler,
        onabort: null as Handler,
        objectStore: () => ({
          get: (ownerId: string) => {
            const request = new FakeRequest()
            queueMicrotask(() => {
              request.resolve(records.get(ownerId))
              transaction.oncomplete?.(new Event('complete'))
            })
            return request
          },
          put: (value: unknown) => {
            database.putCalls += 1
            const request = new FakeRequest()
            const ownerId = (value as { ownerId: string }).ownerId
            records.set(ownerId, value)
            queueMicrotask(() => {
              request.resolve()
              transaction.oncomplete?.(new Event('complete'))
            })
            return request
          },
          delete: (ownerId: string) => {
            database.deleteCalls += 1
            const request = new FakeRequest()
            if (controls.deleteFailures > 0) {
              controls.deleteFailures -= 1
              queueMicrotask(() => {
                request.error = new DOMException('controlled delete failure', 'AbortError')
                request.onerror?.(new Event('error'))
                transaction.onabort?.(new Event('abort'))
              })
              return request
            }
            records.delete(ownerId)
            queueMicrotask(() => {
              request.resolve()
              transaction.oncomplete?.(new Event('complete'))
            })
            return request
          },
        }),
      }
      return transaction
    },
    close: () => undefined,
  } as unknown as FakeEnvironment['database']
  const storage = new FakeStorage()
  storage.seed(currentOwnerKey, '42')
  const indexedDb = {
    open: () => {
      const request = new FakeOpenRequest(database)
      openRequests.push(request)
      if (autoResolveOpen && !controls.holdOpen) queueMicrotask(() => request.resolve(database))
      return request
    },
  } as unknown as IDBFactory

  Object.defineProperty(globalThis, 'indexedDB', { configurable: true, value: indexedDb })
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage })

  return { database, storage, openRequests }
}

let indexedDbDescriptor: PropertyDescriptor | undefined
let localStorageDescriptor: PropertyDescriptor | undefined

afterEach(() => {
  if (indexedDbDescriptor) Object.defineProperty(globalThis, 'indexedDB', indexedDbDescriptor)
  else delete (globalThis as { indexedDB?: IDBFactory }).indexedDB
  if (localStorageDescriptor) Object.defineProperty(globalThis, 'localStorage', localStorageDescriptor)
  else delete (globalThis as { localStorage?: Storage }).localStorage
  indexedDbDescriptor = undefined
  localStorageDescriptor = undefined
})

function setup(autoResolveOpen: boolean): FakeEnvironment {
  indexedDbDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'indexedDB')
  localStorageDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
  return installEnvironment(autoResolveOpen)
}

describe('SemesterSnapshotStore write fence', () => {
  it('blocks old offline fallback after a transient pointer read failure and protects a newer context from retry', async () => {
    const environment = setup(true)
    const store = new SemesterSnapshotStore()
    const nextScopeKey = JSON.stringify(['student-read-model-v1', '42', 'STUDENT', '18', '9'])
    environment.storage.getItemFailures = 1
    const failure = await store.clearMismatchedCurrent(nextScopeKey)
    expect(failure?.retryRequired).toBe(true)
    expect(store.hasUnresolvedCleanup()).toBe(true)
    await expect(store.readCurrent()).resolves.toBeNull()
    // The guard survives a new runtime even though the old pointer/record do.
    expect(environment.database.records.has('42')).toBe(true)
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toBeNull()

    const newest = makeSnapshot('42', '2026-09-08T09:00:00Z')
    newest.scope!.groupId = '19'
    newest.scopeKey = JSON.stringify(['student-read-model-v1', '42', 'STUDENT', '19', '9'])
    await store.clearMismatchedCurrent(newest.scopeKey)
    await store.write(newest)
    await store.retryCleanup(failure!)
    await expect(store.readCurrent()).resolves.toEqual(newest)
  })

  it('reports a failed guard write while attempting independent retirement, then retries', async () => {
    const environment = setup(true)
    const store = new SemesterSnapshotStore()
    environment.storage.scopeSetFailures = 1
    const failure = await store.clearMismatchedCurrent('new-context')
    expect(failure?.contextGuard?.status).toBe('failed')
    expect(failure?.idbDelete.status).toBe('completed')
    expect(failure?.safeOffline).toBe(false)
    expect(failure?.retryRequired).toBe(true)
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toBeNull()
    const retry = await store.retryCleanup(failure!)
    expect(retry.safeOffline).toBe(true)
    expect(store.hasUnresolvedCleanup()).toBe(false)
  })

  it('does not report a later owner switch as safe while the confirmed context guard still fails', async () => {
    const environment = setup(true)
    const store = new SemesterSnapshotStore()
    environment.storage.scopeSetFailures = 2
    const failure = await store.clearMismatchedCurrent('new-context')
    expect(failure?.retryRequired).toBe(true)
    const replacement = await store.switchToDetailed('42')
    expect(replacement.contextGuard?.status).toBe('failed')
    expect(replacement.safeOffline).toBe(false)
    expect(replacement.retryRequired).toBe(true)
    expect(store.hasUnresolvedCleanup()).toBe(true)
    const retry = await store.retryCleanup(replacement)
    expect(retry.safeOffline).toBe(true)
    expect(store.hasUnresolvedCleanup()).toBe(false)
  })

  it('retires the pointer independently when both guard write and IDB delete fail', async () => {
    const environment = setup(true)
    const store = new SemesterSnapshotStore()
    environment.storage.scopeSetFailures = 1
    environment.database.controls.deleteFailures = 1
    const failure = await store.clearMismatchedCurrent('new-context')
    expect(failure?.contextGuard?.status).toBe('failed')
    expect(failure?.idbDelete.status).toBe('failed')
    expect(failure?.retryRequired).toBe(true)
    expect(failure?.safeOffline).toBe(false)
    expect(environment.database.records.has('42')).toBe(true)
    expect(failure?.pointerCleanup.status).toBe('completed')
    expect(environment.storage.getItem(currentOwnerKey)).toBeNull()
    await expect(store.readCurrent()).resolves.toBeNull()
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toBeNull()
    await store.retryCleanup(failure!)
    expect(environment.database.records.has('42')).toBe(false)
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toBeNull()
  })

  it('does not overwrite a newer durable partition with a delayed write from another store', async () => {
    const environment = setup(true)
    const oldStore = new SemesterSnapshotStore()
    const older = makeSnapshot('42', '2026-09-08T08:30:00Z')
    await oldStore.clearMismatchedCurrent(older.scopeKey!)
    environment.database.controls.holdOpen = true
    const pendingOldWrite = oldStore.write(older)
    await new Promise<void>((resolve) => queueMicrotask(resolve))
    const delayedOpen = environment.openRequests.at(-1)!
    environment.database.controls.holdOpen = false
    const currentStore = new SemesterSnapshotStore()
    const newest = makeSnapshot('42', '2026-09-08T09:00:00Z')
    newest.scope!.groupId = '19'
    newest.scopeKey = JSON.stringify(['student-read-model-v1', '42', 'STUDENT', '19', '9'])
    await currentStore.clearMismatchedCurrent(newest.scopeKey)
    await currentStore.write(newest)
    delayedOpen.resolve(environment.database)
    await pendingOldWrite
    // An old request that is queued only after C committed must also be inert.
    await oldStore.write(older)
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toEqual(newest)
  })

  it('does not overwrite a newer durable context from another store during an old retry', async () => {
    const environment = setup(true)
    const oldStore = new SemesterSnapshotStore()
    environment.storage.getItemFailures = 1
    const failure = await oldStore.clearMismatchedCurrent('context-B')
    const currentStore = new SemesterSnapshotStore()
    const newest = makeSnapshot('42', '2026-09-08T09:00:00Z')
    newest.scope!.groupId = '19'
    newest.scopeKey = JSON.stringify(['student-read-model-v1', '42', 'STUDENT', '19', '9'])
    await currentStore.clearMismatchedCurrent(newest.scopeKey)
    await currentStore.write(newest)
    await oldStore.retryCleanup(failure!)
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toEqual(newest)
  })

  it('does not recover the previous group when a confirmed new context schedule request fails', async () => {
    setup(true)
    const store = new SemesterSnapshotStore()
    const nextScopeKey = JSON.stringify(['student-read-model-v1', '42', 'STUDENT', '18', '9'])
    // Confirming the new context must retire the old pointer before any
    // schedule request. A new runtime then cannot recover the old group.
    await store.clearMismatchedCurrent(nextScopeKey)
    await expect(store.read('42', nextScopeKey)).resolves.toBeNull()
    await expect(Promise.reject(new TypeError('schedule network unavailable'))).rejects.toThrow()
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toBeNull()
  })

  it.each([
    ['account', '43', 'STUDENT', '17', '9'],
    ['role', '42', 'HEADMAN', '17', '9'],
    ['semester', '42', 'STUDENT', '17', '10'],
  ])('retires an incompatible %s partition before loading replacement data', async (_name, userId, role, groupId, semesterId) => {
    setup(true)
    const store = new SemesterSnapshotStore()
    const result = await store.clearMismatchedCurrent(JSON.stringify(['student-read-model-v1', userId, role, groupId, semesterId]))
    expect(result?.safeOffline).toBe(true)
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toBeNull()
  })

  it('retains an exact partition and retires its pointer even if record deletion fails', async () => {
    const environment = setup(true)
    const store = new SemesterSnapshotStore()
    const saved = makeSnapshot('42', '2026-09-08T08:30:00Z')
    await expect(store.clearMismatchedCurrent(saved.scopeKey!)).resolves.toBeNull()
    await expect(store.readCurrent()).resolves.toEqual(saved)
    environment.database.controls.deleteFailures = 1
    const result = await store.clearMismatchedCurrent('another-context')
    expect(result?.retryRequired).toBe(true)
    expect(environment.database.records.has('42')).toBe(true)
    await expect(new SemesterSnapshotStore().readCurrent()).resolves.toBeNull()
  })
  it('skips a write that was invalidated while IDB open was pending', async () => {
    const environment = setup(false)
    const before = environment.database.records.get('42')
    const store = new SemesterSnapshotStore()
    const pending = store.write(makeSnapshot('42', '2026-09-08T08:31:00Z'))

    await new Promise<void>((resolve) => queueMicrotask(resolve))
    expect(environment.openRequests).toHaveLength(1)
    expect(environment.openRequests[0]?.onsuccess).toBeTypeOf('function')

    store.invalidatePendingWrites()
    environment.openRequests[0]?.resolve(environment.database)
    await expect(pending).resolves.toBeUndefined()

    expect(environment.database.transactionCalls).toBe(0)
    expect(environment.database.putCalls).toBe(0)
    expect(environment.storage.setItemCalls).toBe(0)
    expect(environment.storage.getItem(currentOwnerKey)).toBe('42')
    expect(environment.database.records.get('42')).toEqual(before)
  })

  it('keeps switchTo and clear operational after a write-only invalidation', async () => {
    const environment = setup(true)
    const store = new SemesterSnapshotStore()

    store.invalidatePendingWrites()
    await store.switchTo('43')
    expect(environment.storage.getItem(currentOwnerKey)).toBe('43')
    expect(environment.storage.setItemCalls).toBe(1)
    expect(environment.database.records.has('42')).toBe(false)

    await store.clear('43')
    expect(environment.storage.getItem(currentOwnerKey)).toBeNull()
    expect(environment.storage.removeItemCalls).toBe(1)
    expect(environment.database.deleteCalls).toBe(2)
  })
})

describe('SemesterSnapshotStore pointer/delete cleanup contract', () => {
  it('clears the pointer when native delete aborts and retries the record later', async () => {
    const environment = setup(true)
    environment.database.controls.deleteFailures = 1
    const store = new SemesterSnapshotStore()

    const first = await store.clearDetailed('42')

    expect(first.idbDelete.status).toBe('failed')
    expect(first.pointerCleanup.status).toBe('completed')
    expect(first.safeOffline).toBe(false)
    expect(first.retryRequired).toBe(true)
    expect(environment.storage.getItem(currentOwnerKey)).toBeNull()
    expect(environment.database.records.has('42')).toBe(true)
    await expect(store.readCurrent()).resolves.toBeNull()

    const retry = await store.retryCleanup(first)

    expect(retry.idbDelete.status).toBe('completed')
    expect(retry.safeOffline).toBe(true)
    expect(retry.retryRequired).toBe(false)
    expect(environment.database.records.has('42')).toBe(false)
    expect(store.hasUnresolvedCleanup('42')).toBe(false)
  })

  it('keeps a throwing pointer fail-closed after delete success and retries both independently', async () => {
    const environment = setup(true)
    environment.storage.removeItemFailures = 1
    const store = new SemesterSnapshotStore()

    const first = await store.clearDetailed('42')

    expect(first.idbDelete.status).toBe('completed')
    expect(first.pointerCleanup.status).toBe('failed')
    expect(first.safeOffline).toBe(false)
    expect(first.retryRequired).toBe(true)
    expect(environment.storage.getItem(currentOwnerKey)).toBe('42')
    expect(environment.database.records.has('42')).toBe(false)
    await expect(store.readCurrent()).resolves.toBeNull()

    const retry = await store.retryCleanup(first)

    expect(retry.pointerCleanup.status).toBe('completed')
    expect(retry.safeOffline).toBe(true)
    expect(environment.storage.getItem(currentOwnerKey)).toBeNull()
    expect(store.hasUnresolvedCleanup('42')).toBe(false)
  })

  it('fails closed when replacement pointer write throws and retries without restoring the old owner', async () => {
    const environment = setup(true)
    environment.storage.setItemFailures = 1
    const store = new SemesterSnapshotStore()

    const first = await store.switchToDetailed('43')

    expect(first.operation).toBe('switch')
    expect(first.ownerId).toBe('42')
    expect(first.replacementOwnerId).toBe('43')
    expect(first.idbDelete.status).toBe('completed')
    expect(first.pointerCleanup.status).toBe('failed')
    expect(first.safeOffline).toBe(false)
    expect(first.retryRequired).toBe(true)
    expect(environment.database.records.has('42')).toBe(false)
    expect(environment.storage.getItem(currentOwnerKey)).toBe('42')
    await expect(store.readCurrent()).resolves.toBeNull()

    const retry = await store.retryCleanup(first)

    expect(retry.pointerCleanup.status).toBe('completed')
    expect(retry.safeOffline).toBe(true)
    expect(environment.storage.getItem(currentOwnerKey)).toBe('43')
    expect(environment.database.records.has('42')).toBe(false)
    expect(store.hasUnresolvedCleanup()).toBe(false)
  })

  it('does not delete a same-owner record while retrying a failed pointer write', async () => {
    const environment = setup(true)
    environment.storage.setItemFailures = 1
    const store = new SemesterSnapshotStore()

    const first = await store.switchToDetailed('42')

    expect(first.ownerId).toBe('42')
    expect(first.replacementOwnerId).toBe('42')
    expect(first.idbDelete.status).toBe('skipped')
    expect(first.idbDelete.reason).toBe('owner-replaced')
    expect(first.pointerCleanup.status).toBe('failed')
    expect(first.retryRequired).toBe(true)
    expect(environment.storage.getItem(currentOwnerKey)).toBe('42')
    expect(environment.database.records.has('42')).toBe(true)
    const deleteCalls = environment.database.deleteCalls

    const retry = await store.retryCleanup(first)

    expect(retry.idbDelete.status).toBe('skipped')
    expect(retry.idbDelete.reason).toBe('owner-replaced')
    expect(retry.pointerCleanup.status).toBe('skipped')
    expect(retry.pointerCleanup.reason).toBe('owner-replaced')
    expect(retry.safeOffline).toBe(true)
    expect(retry.retryRequired).toBe(false)
    expect(environment.database.deleteCalls).toBe(deleteCalls)
    expect(environment.database.records.has('42')).toBe(true)
    expect(environment.storage.getItem(currentOwnerKey)).toBe('42')
    expect(store.hasUnresolvedCleanup()).toBe(false)
  })

  it('reports dual failure as unresolved while retaining in-memory denial', async () => {
    const environment = setup(true)
    environment.database.controls.deleteFailures = 1
    environment.storage.removeItemFailures = 1
    const store = new SemesterSnapshotStore()

    const result = await store.clearDetailed('42')

    expect(result.idbDelete.status).toBe('failed')
    expect(result.pointerCleanup.status).toBe('failed')
    expect(result.safeOffline).toBe(false)
    expect(result.retryRequired).toBe(true)
    expect(store.hasUnresolvedCleanup('42')).toBe(true)
    expect(environment.storage.getItem(currentOwnerKey)).toBe('42')
    expect(environment.database.records.has('42')).toBe(true)
    await expect(store.read('42')).resolves.toBeNull()
    await expect(store.readCurrent()).resolves.toBeNull()
  })

  it('does not remove a replacement pointer when an older cleanup is queued', async () => {
    const environment = setup(true)
    const store = new SemesterSnapshotStore()
    const pending = store.clearDetailed('42')
    environment.storage.seed(currentOwnerKey, '43')

    const result = await pending

    expect(result.pointerCleanup.status).toBe('skipped')
    expect(result.pointerCleanup.reason).toBe('owner-replaced')
    expect(result.safeOffline).toBe(true)
    expect(environment.storage.getItem(currentOwnerKey)).toBe('43')
    expect(environment.database.records.has('42')).toBe(false)
  })
})
