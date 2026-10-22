import { afterEach, describe, expect, it, vi } from 'vitest'
import { TelegramHost } from './telegram'
afterEach(() => vi.unstubAllGlobals())
describe('notification native Back claim', () => {
  it('owns exactly one callback, switches flow pages and restores latest shell visibility on idempotent release', () => {
    const listeners = new Set<() => void>(),
      show = vi.fn(),
      hide = vi.fn()
    vi.stubGlobal('window', {
      Telegram: {
        WebApp: {
          BackButton: {
            show,
            hide,
            onClick: (fn: () => void) => listeners.add(fn),
            offClick: (fn: () => void) => listeners.delete(fn),
          },
        },
      },
    })
    const host = new TelegramHost(),
      shell = vi.fn(),
      pages = ['colors', 'settings', 'inbox'],
      closed = vi.fn()
    host.subscribeBack(shell)
    host.setBackVisible(false)
    const release = host.claimBack(() => {
      if (pages.length > 1) pages.shift()
      else closed()
    })
    const click = () => [...listeners].forEach((fn) => fn())
    click()
    expect(pages[0]).toBe('settings')
    expect(shell).not.toHaveBeenCalled()
    click()
    expect(pages[0]).toBe('inbox')
    click()
    expect(closed).toHaveBeenCalledTimes(1)
    host.setBackVisible(true)
    expect(show).toHaveBeenCalled()
    release()
    release()
    click()
    expect(shell).toHaveBeenCalledTimes(1)
    expect(closed).toHaveBeenCalledTimes(1)
    host.setBackVisible(false)
    expect(hide).toHaveBeenCalled()
  })
})
