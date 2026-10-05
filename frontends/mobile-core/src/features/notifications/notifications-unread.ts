import type { NotificationsApi } from './notifications-client'

export interface NotificationsUnreadView {
  count: number | null
  status: 'idle' | 'loading' | 'ready' | 'error'
  error: unknown
}

/** One authoritative count/query owner per authenticated account generation. */
export class NotificationsUnreadState {
  readonly view: NotificationsUnreadView = { count: null, status: 'idle', error: null }
  private key: string | null = null
  private api: NotificationsApi | null = null
  private epoch = 0
  private revision = 0
  private pending: Promise<void> | null = null
  private dirty = false
  private readonly listeners = new Set<() => void>()

  constructor(private readonly onError?: (error: unknown) => void) {}

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  bind(key: string | null, api: NotificationsApi | null): void {
    if (key === this.key && api === this.api) return
    this.epoch += 1
    this.key = key
    this.api = api
    this.pending = null
    this.dirty = false
    this.view.count = null
    this.view.status = 'idle'
    this.view.error = null
    this.publish()
  }

  refresh(): Promise<void> {
    if (!this.api || !this.key) return Promise.resolve()
    if (this.pending) return this.pending
    this.dirty = true
    const epoch = this.epoch
    const api = this.api
    const task = this.resolve(epoch, api)
    this.pending = task
    void task.finally(() => { if (this.pending === task) this.pending = null })
    return task
  }

  /** REST reads or realtime/reconnect invalidate any count already in flight. */
  invalidate(): Promise<void> {
    this.revision += 1
    this.dirty = true
    return this.refresh()
  }

  private async resolve(epoch: number, api: NotificationsApi): Promise<void> {
    while (this.dirty && epoch === this.epoch) {
      this.dirty = false
      const revision = this.revision
      this.view.status = 'loading'
      this.view.error = null
      this.publish()
      try {
        const count = await api.unreadCount()
        if (epoch !== this.epoch || revision !== this.revision) continue
        if (!Number.isSafeInteger(count) || count < 0) throw new Error('Некорректный счётчик уведомлений')
        this.view.count = count
        this.view.status = 'ready'
        this.publish()
      } catch (error) {
        if (epoch !== this.epoch || revision !== this.revision) continue
        this.view.count = null
        this.view.status = 'error'
        this.view.error = error
        this.publish()
        this.onError?.(error)
      }
    }
  }

  private publish(): void { this.listeners.forEach(listener => listener()) }
}
