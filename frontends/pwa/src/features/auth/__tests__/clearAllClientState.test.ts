import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { clearAllClientState } from '../clearAllClientState'

/**
 * M08 Группа 6 (P2-8/6) — regression guard для 09 P0-4 cross-user cache leak.
 *
 * Verifies что clearAllClientState():
 *  1. Чистит localStorage + sessionStorage.
 *  2. Удаляет headman-api-cache-* + runtime-* из Service Worker caches.
 *  3. НЕ удаляет другие caches (precache etc).
 *  4. Unsubscribe из PushManager + DELETE /api/push/subscribe.
 */

describe('clearAllClientState — logout state cleanup (09 P0-4 regression guard)', () => {
  const originalLocalStorage = globalThis.localStorage
  const originalSessionStorage = globalThis.sessionStorage
  const originalCaches = (globalThis as any).caches
  const originalFetch = globalThis.fetch
  const originalNavigator = globalThis.navigator

  beforeEach(() => {
    // LocalStorage + SessionStorage mocks
    const storageMock = () => {
      let store: Record<string, string> = { foo: 'bar' }
      return {
        clear: vi.fn(() => { store = {} }),
        getItem: (k: string) => store[k] ?? null,
        setItem: vi.fn(),
        removeItem: vi.fn(),
        length: 1,
        key: () => null,
      }
    }
    Object.defineProperty(globalThis, 'localStorage', {
      value: storageMock(), configurable: true, writable: true,
    })
    Object.defineProperty(globalThis, 'sessionStorage', {
      value: storageMock(), configurable: true, writable: true,
    })

    // CacheStorage mock
    const cachesMock = {
      keys: vi.fn().mockResolvedValue([
        'headman-api-cache-v1',
        'runtime-cache-v2',
        'precache-v1-RutTrack',  // должен остаться
        'headman-api-cache-v2',
      ]),
      delete: vi.fn().mockResolvedValue(true),
    }
    Object.defineProperty(globalThis, 'caches', {
      value: cachesMock, configurable: true, writable: true,
    })

    // Fetch mock — для DELETE /api/push/subscribe
    globalThis.fetch = vi.fn().mockResolvedValue({ ok: true, status: 204 } as Response)

    // Navigator mock — serviceWorker + PushManager
    const pushSubMock = {
      endpoint: 'https://fcm.googleapis.com/fcm/send/test-endpoint',
      unsubscribe: vi.fn().mockResolvedValue(true),
    }
    const swRegMock = {
      pushManager: {
        getSubscription: vi.fn().mockResolvedValue(pushSubMock),
      },
    }
    Object.defineProperty(globalThis, 'navigator', {
      value: {
        ...originalNavigator,
        serviceWorker: { ready: Promise.resolve(swRegMock) },
      },
      configurable: true, writable: true,
    })
    // PushManager должен быть на window
    Object.defineProperty(globalThis, 'PushManager', {
      value: function () {}, configurable: true, writable: true,
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    Object.defineProperty(globalThis, 'localStorage', { value: originalLocalStorage, configurable: true })
    Object.defineProperty(globalThis, 'sessionStorage', { value: originalSessionStorage, configurable: true })
    if (originalCaches !== undefined) {
      Object.defineProperty(globalThis, 'caches', { value: originalCaches, configurable: true })
    }
    globalThis.fetch = originalFetch
    Object.defineProperty(globalThis, 'navigator', { value: originalNavigator, configurable: true })
  })

  it('clears localStorage + sessionStorage', async () => {
    const lsSpy = localStorage.clear as any
    const ssSpy = sessionStorage.clear as any

    await clearAllClientState('test_access_token')

    expect(lsSpy).toHaveBeenCalledOnce()
    expect(ssSpy).toHaveBeenCalledOnce()
  })

  it('deletes headman-api-cache-* + runtime-* from CacheStorage, keeps precache', async () => {
    const deleteMock = (caches as any).delete

    await clearAllClientState('test_access_token')

    // headman-api-cache-v1, headman-api-cache-v2, runtime-cache-v2 → delete
    expect(deleteMock).toHaveBeenCalledWith('headman-api-cache-v1')
    expect(deleteMock).toHaveBeenCalledWith('headman-api-cache-v2')
    expect(deleteMock).toHaveBeenCalledWith('runtime-cache-v2')
    // precache-v1-RutTrack — НЕ delete
    expect(deleteMock).not.toHaveBeenCalledWith('precache-v1-RutTrack')
  })

  it('calls backend DELETE for push subscription with access token', async () => {
    const fetchMock = globalThis.fetch as any
    const token = 'access_jwt_test_token'

    await clearAllClientState(token)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/push/subscribe',
      expect.objectContaining({
        method: 'DELETE',
        credentials: 'include',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        }),
      })
    )
  })

  it('unsubscribes PushManager subscription locally', async () => {
    const reg = await navigator.serviceWorker.ready
    const sub = await reg.pushManager.getSubscription()
    const unsubSpy = (sub as any).unsubscribe

    await clearAllClientState('test_token')

    expect(unsubSpy).toHaveBeenCalledOnce()
  })

  it('does not call fetch with Authorization if accessToken null', async () => {
    const fetchMock = globalThis.fetch as any

    await clearAllClientState(null)

    if (fetchMock.mock.calls.length > 0) {
      const headers = fetchMock.mock.calls[0][1].headers
      expect(headers.Authorization).toBeUndefined()
    }
  })
})
