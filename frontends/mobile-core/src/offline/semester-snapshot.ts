import type { StudentSemesterSchedule } from '../api/types'

export interface SemesterSnapshot {
  ownerId: string
  schedule: StudentSemesterSchedule
  etag: string | null
}

const dbName = 'rct-student-mobile'
const storeName = 'semester-snapshots'
const currentOwnerKey = 'rct-student-mobile.current-owner'

export class SemesterSnapshotStore {
  async read(ownerId: string): Promise<SemesterSnapshot | null> {
    return this.withStore('readonly', (store) => this.request<SemesterSnapshot | undefined>(store.get(ownerId))).then((value) => value ?? null)
  }

  async readCurrent(): Promise<SemesterSnapshot | null> {
    const ownerId = localStorage.getItem(currentOwnerKey)
    return ownerId ? this.read(ownerId) : null
  }

  async write(snapshot: SemesterSnapshot): Promise<void> {
    await this.withStore('readwrite', (store) => this.request(store.put(snapshot)))
    localStorage.setItem(currentOwnerKey, snapshot.ownerId)
  }

  async switchTo(ownerId: string): Promise<void> {
    const previous = localStorage.getItem(currentOwnerKey)
    if (previous && previous !== ownerId) await this.clear(previous)
    localStorage.setItem(currentOwnerKey, ownerId)
  }

  async clear(ownerId?: string): Promise<void> {
    const target = ownerId ?? localStorage.getItem(currentOwnerKey)
    if (target) await this.withStore('readwrite', (store) => this.request(store.delete(target)))
    if (!ownerId || ownerId === localStorage.getItem(currentOwnerKey)) localStorage.removeItem(currentOwnerKey)
  }

  private async withStore<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => Promise<T>): Promise<T> {
    const database = await this.open()
    try {
      return await work(database.transaction(storeName, mode).objectStore(storeName))
    } finally {
      database.close()
    }
  }

  private open(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(dbName, 1)
      request.onupgradeneeded = () => request.result.createObjectStore(storeName, { keyPath: 'ownerId' })
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  }

  private request<T>(request: IDBRequest<T>): Promise<T> {
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  }
}
