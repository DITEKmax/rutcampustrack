import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMobileTheme, MOBILE_THEME_ATTRIBUTE, MobileThemeOwnershipError } from './theme'

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
  it('keeps an explicitly locked dark surface dark through system and profile changes', () => {
    const query = mediaQuery(false)
    const root = target(query)
    const controller = createMobileTheme({ target: root as unknown as HTMLElement, mode: 'light', lockedMode: 'dark' })
    controllers.push(controller)
    controller.setMode('light')
    controller.setMode('system')
    query.emit()
    expect(controller.mode).toBe('dark')
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
