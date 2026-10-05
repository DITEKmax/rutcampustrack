import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMobileTheme, MOBILE_THEME_ATTRIBUTE, MobileThemeOwnershipError } from './theme'
import { createMobileThemePreference, MOBILE_THEME_PREFERENCE_KEY } from './theme-preference'

interface FakeMediaQuery {
  matches: boolean
  addEventListener: (type: 'change', listener: () => void) => void
  removeEventListener: (type: 'change', listener: () => void) => void
  addListener: (listener: () => void) => void
  removeListener: (listener: () => void) => void
  emit(): void
}

interface FakeTarget {
  attributes: Map<string, string>
  style: { colorScheme: string }
  ownerDocument: { defaultView: { matchMedia: () => MediaQueryList } }
  getAttribute(name: string): string | null
  setAttribute(name: string, value: string): void
  removeAttribute(name: string): void
}

function mediaQuery(matches = false): FakeMediaQuery {
  const listeners = new Set<() => void>()
  return {
    matches,
    addEventListener: (_type, listener) => listeners.add(listener),
    removeEventListener: (_type, listener) => listeners.delete(listener),
    addListener: (listener) => listeners.add(listener),
    removeListener: (listener) => listeners.delete(listener),
    emit() { for (const listener of [...listeners]) listener() },
  }
}

function target(query: FakeMediaQuery): FakeTarget {
  const attributes = new Map<string, string>()
  const value: FakeTarget = {
    attributes,
    style: { colorScheme: '' },
    ownerDocument: { defaultView: { matchMedia: () => query as unknown as MediaQueryList } },
    getAttribute(name) { return attributes.get(name) ?? null },
    setAttribute(name, attributeValue) { attributes.set(name, attributeValue) },
    removeAttribute(name) { attributes.delete(name) },
  }
  return value
}

const controllers: Array<{ dispose(): void }> = []

afterEach(() => {
  for (const controller of controllers) controller.dispose()
  controllers.length = 0
  vi.restoreAllMocks()
})

describe('createMobileTheme', () => {
  it('retains every chosen mode while keeping locked DOM/resolved appearance dark', () => {
    const query = mediaQuery(false)
    const root = target(query)
    const controller = createMobileTheme({ target: root as unknown as HTMLElement, mode: 'light', lockedMode: 'dark' })
    controllers.push(controller)
    const listener = vi.fn()
    controller.subscribe(listener)
    for (const mode of ['system', 'dark', 'light'] as const) {
      controller.setMode(mode)
      expect(controller.mode).toBe(mode)
      expect(root.attributes.get(MOBILE_THEME_ATTRIBUTE)).toBe('dark')
      expect(controller.resolvedMode).toBe('dark')
    }
    query.emit()
    expect(listener.mock.calls.map(([snapshot]) => snapshot.mode)).toEqual(['system', 'dark', 'light'])
    expect(controller.mode).toBe('light')
    expect(controller.resolvedMode).toBe('dark')
    expect(root.style.colorScheme).toBe('dark')
  })

  it('follows system media, keeps explicit overrides stable, and switches back to system', () => {
    const query = mediaQuery(false)
    const root = target(query)
    const controller = createMobileTheme({ target: root as unknown as HTMLElement })
    controllers.push(controller)

    expect(controller.mode).toBe('system')
    expect(controller.resolvedMode).toBe('light')
    expect(root.attributes.get(MOBILE_THEME_ATTRIBUTE)).toBe('system')
    expect(root.style.colorScheme).toBe('light')

    query.matches = true
    query.emit()
    expect(controller.resolvedMode).toBe('dark')

    controller.setMode('light')
    query.matches = false
    query.emit()
    expect(controller.mode).toBe('light')
    expect(controller.resolvedMode).toBe('light')

    controller.setMode('system')
    expect(controller.resolvedMode).toBe('light')
    query.matches = true
    query.emit()
    expect(controller.resolvedMode).toBe('dark')
  })

  it('rejects a second owner and restores the target plus listener on dispose', () => {
    const query = mediaQuery(true)
    const root = target(query)
    root.setAttribute(MOBILE_THEME_ATTRIBUTE, 'light')
    root.style.colorScheme = 'light'
    const controller = createMobileTheme({ target: root as unknown as HTMLElement, mode: 'dark' })
    controllers.push(controller)

    expect(() => createMobileTheme({ target: root as unknown as HTMLElement, mode: 'light' }))
      .toThrow(MobileThemeOwnershipError)
    expect(root.attributes.get(MOBILE_THEME_ATTRIBUTE)).toBe('dark')
    expect(root.style.colorScheme).toBe('dark')

    const listener = vi.fn()
    controller.subscribe(listener)
    controller.dispose()
    expect(root.attributes.get(MOBILE_THEME_ATTRIBUTE)).toBe('light')
    expect(root.style.colorScheme).toBe('light')
    query.matches = false
    query.emit()
    expect(listener).not.toHaveBeenCalled()

    const next = createMobileTheme({ target: root as unknown as HTMLElement, mode: 'light' })
    controllers.push(next)
    expect(next.resolvedMode).toBe('light')
  })

  it('is safe without a target and does not mutate after disposal', () => {
    const controller = createMobileTheme({ target: null, mode: 'dark' })
    controllers.push(controller)
    expect(controller.resolvedMode).toBe('dark')
    controller.dispose()
    controller.setMode('light')
    expect(controller.mode).toBe('dark')
  })
})


describe('caller-owned mobile theme preference', () => {
  it('persists only the chosen theme key across owners while applied appearance remains locked dark', () => {
    const values = new Map<string, string>([['unrelated-account', 'untouched']])
    const reads: string[] = [], writes: string[] = []
    const preference = createMobileThemePreference(() => ({
      getItem(key) { reads.push(key); return values.get(key) ?? null },
      setItem(key, value) { writes.push(key); values.set(key, value) },
    }))
    expect(preference.read()).toBeNull()
    const root = target(mediaQuery(false))
    const first = createMobileTheme({ target: root as unknown as HTMLElement, mode: preference.read() ?? 'dark', lockedMode: 'dark' })
    const stop = first.subscribe(({ mode }) => preference.write(mode))
    first.setMode('light')
    expect(first.mode).toBe('light')
    expect(root.attributes.get(MOBILE_THEME_ATTRIBUTE)).toBe('dark')
    stop(); first.dispose()
    const second = createMobileTheme({ target: root as unknown as HTMLElement, mode: preference.read() ?? 'dark', lockedMode: 'dark' })
    controllers.push(second)
    expect(second.mode).toBe('light')
    expect(second.resolvedMode).toBe('dark')
    expect(root.attributes.get(MOBILE_THEME_ATTRIBUTE)).toBe('dark')
    expect(values.get('unrelated-account')).toBe('untouched')
    expect(new Set([...reads, ...writes])).toEqual(new Set([MOBILE_THEME_PREFERENCE_KEY]))
  })
  it('ignores invalid stored choices and unavailable storage without affecting the controller', () => {
    const invalid = createMobileThemePreference(() => ({ getItem: () => '{invalid-mode}', setItem: () => { throw new Error('blocked write') } }))
    expect(invalid.read()).toBeNull()
    expect(() => invalid.write('system')).not.toThrow()
    const blocked = createMobileThemePreference(() => { throw new Error('blocked storage') })
    expect(blocked.read()).toBeNull()
    expect(() => blocked.write('light')).not.toThrow()
    const absent = createMobileThemePreference(() => null)
    expect(absent.read()).toBeNull()
    expect(() => absent.write('dark')).not.toThrow()
  })
})
