import { describe, expect, it, vi } from 'vitest'
import { createChunkRecovery } from './chunk-recovery'

function browser() {
  const values = new Map<string, string>()
  return {
    sessionStorage: {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => {
        values.set(key, value)
      },
    },
    location: { reload: vi.fn() },
  }
}

describe('WEB lazy-module recovery', () => {
  it('keeps the current view until an explicit action and allows one refresh across page instances', () => {
    const port = browser()
    const recovery = createChunkRecovery(port)
    recovery.observe(new TypeError('Failed to fetch dynamically imported module: /assets/old.js'))
    expect(recovery.failed.value).toBe(true)
    expect(recovery.canRefresh.value).toBe(true)
    expect(port.location.reload).not.toHaveBeenCalled()
    recovery.refresh()
    recovery.refresh()
    expect(port.location.reload).toHaveBeenCalledTimes(1)

    const afterReload = createChunkRecovery(port)
    afterReload.observe(new TypeError('Importing a module script failed.'))
    expect(afterReload.failed.value).toBe(true)
    expect(afterReload.canRefresh.value).toBe(false)
    afterReload.refresh()
    expect(port.location.reload).toHaveBeenCalledTimes(1)
  })

  it('ignores ordinary API, authentication, network and application failures', () => {
    const port = browser()
    const recovery = createChunkRecovery(port)
    for (const error of [
      new Error('401 Unauthorized'),
      new TypeError('Failed to fetch'),
      new Error('CURRENT_PASSWORD_INVALID'),
      new Error('Unknown operation result'),
      null,
      { status: 503 },
    ])
      recovery.observe(error)
    recovery.refresh()
    expect(recovery.failed.value).toBe(false)
    expect(port.location.reload).not.toHaveBeenCalled()
  })

  it('refuses refresh when the persistent tab guard cannot be read or written', () => {
    for (const operation of ['getItem', 'setItem'] as const) {
      const port = browser()
      port.sessionStorage[operation] = () => {
        throw new Error('Storage denied')
      }
      const recovery = createChunkRecovery(port)
      recovery.observe(new Error('Unable to preload CSS for /assets/old.css'))
      recovery.refresh()
      expect(recovery.failed.value).toBe(true)
      expect(recovery.canRefresh.value).toBe(false)
      expect(port.location.reload).not.toHaveBeenCalled()
    }
  })

  it('rechecks the shared tab limit at the user action boundary', () => {
    const port = browser()
    const recovery = createChunkRecovery(port)
    recovery.observe('error loading dynamically imported module')
    port.sessionStorage.setItem('rct.chunk-reload.v1', '1')
    recovery.refresh()
    expect(port.location.reload).not.toHaveBeenCalled()
    expect(recovery.canRefresh.value).toBe(false)
  })
})
