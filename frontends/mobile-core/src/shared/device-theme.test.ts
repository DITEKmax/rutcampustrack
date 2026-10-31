import { describe, expect, it, vi } from 'vitest'
import {
  createTheme,
  type SystemThemeSource,
  type ThemeMode,
} from '../../../shared/foundation/theme'
import {
  createThemePreference,
  THEME_PREFERENCE_KEY,
} from '../../../shared/foundation/theme-preference'
import {
  createDeviceThemeMirror,
  DeviceThemeHttpError,
  type DeviceThemeSession,
  type DeviceThemeSnapshot,
} from '../../../shared/foundation/device-theme'

const flush = async () => {
  for (let i = 0; i < 20; i++) await Promise.resolve()
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
function setup(initial: string | null = null) {
  const values = new Map<string, string>(initial ? [[THEME_PREFERENCE_KEY, initial]] : [])
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value)
    },
  }
  const preference = createThemePreference(() => storage, null)
  const theme = createTheme({ target: null, mode: preference.read() ?? 'system' })
  return { theme, preference, values }
}

describe('device theme reconciliation', () => {
  it('preserves a newer manual choice while admission GET is pending, including explicit system', async () => {
    const state = setup()
    const pending = deferred<DeviceThemeSnapshot>()
    const write = vi.fn(
      async (_session: DeviceThemeSession, theme: ThemeMode, revision: string) => ({
        theme,
        revision: String(Number(revision) + 1),
      }),
    )
    const mirror = createDeviceThemeMirror({
      ...state,
      transport: { read: () => pending.promise, write },
    })
    mirror.setSession({ key: 'first', accessToken: 'token' })
    state.theme.setMode('system')
    pending.resolve({ theme: 'dark', revision: '3' })
    await flush()
    expect(state.theme.mode).toBe('system')
    expect(state.preference.read()).toBe('system')
    expect(write.mock.calls[0]?.slice(1)).toEqual(['system', '3'])
    mirror.dispose()
    state.theme.dispose()
    state.preference.dispose()
  })

  it('reads back an unknown PUT result before another CAS and avoids repeating a committed write', async () => {
    const state = setup('light')
    let remote: DeviceThemeSnapshot = { theme: 'dark', revision: '4' }
    const order: string[] = []
    const read = async () => {
      order.push('GET')
      return remote
    }
    const write = async () => {
      order.push('PUT')
      remote = { theme: 'light', revision: '5' }
      throw new Error('response lost')
    }
    const mirror = createDeviceThemeMirror({ ...state, transport: { read, write } })
    mirror.setSession({ key: 'first', accessToken: 'token' })
    await flush()
    expect(order).toEqual(['GET', 'PUT', 'GET'])
    expect(state.theme.mode).toBe('light')
    mirror.dispose()
    state.theme.dispose()
    state.preference.dispose()
  })

  it('uses the conflict revision and newest local selection, and bounds an unavailable mirror', async () => {
    const state = setup('light')
    let remote: DeviceThemeSnapshot = { theme: 'dark', revision: '1' }
    const writes: Array<[string, string]> = []
    let first = true
    const mirror = createDeviceThemeMirror({
      ...state,
      transport: {
        read: async () => remote,
        write: async (_session: DeviceThemeSession, theme: ThemeMode, revision: string) => {
          writes.push([theme, revision])
          if (first) {
            first = false
            remote = { theme: 'system', revision: '2' }
            state.theme.setMode('dark')
            throw new DeviceThemeHttpError(409)
          }
          remote = { theme, revision: '3' }
          return remote
        },
      },
    })
    mirror.setSession({ key: 'first', accessToken: 'token' })
    await flush()
    expect(writes).toEqual([
      ['light', '1'],
      ['dark', '2'],
    ])
    expect(state.preference.read()).toBe('dark')
    mirror.dispose()
    state.theme.dispose()
    state.preference.dispose()
  })

  it('ignores late responses after logout and can restore server fallback after a new admission', async () => {
    const state = setup(),
      pending = deferred<DeviceThemeSnapshot>()
    let reads = 0
    const mirror = createDeviceThemeMirror({
      ...state,
      transport: {
        read: () =>
          ++reads === 1 ? pending.promise : Promise.resolve({ theme: 'light', revision: '1' }),
        write: vi.fn(),
      },
    })
    mirror.setSession({ key: 'old', accessToken: 'old-token' })
    mirror.setSession(null)
    pending.resolve({ theme: 'dark', revision: '7' })
    await flush()
    expect(state.preference.read()).toBeNull()
    mirror.setSession({ key: 'new', accessToken: 'new-token' })
    await flush()
    expect(state.theme.mode).toBe('light')
    expect(state.preference.read()).toBe('light')
    mirror.dispose()
    state.theme.dispose()
    state.preference.dispose()
  })

  it('does not persist a live system change as a manual choice', () => {
    const listeners = new Set<() => void>()
    let resolved: 'light' | 'dark' = 'light'
    const source: SystemThemeSource = {
      read: () => resolved,
      subscribe(listener) {
        listeners.add(listener)
        return () => {
          listeners.delete(listener)
        }
      },
    }
    const state = setup()
    state.theme.dispose()
    const theme = createTheme({ target: null, systemSource: source })
    const mirror = createDeviceThemeMirror({
      ...state,
      theme,
      transport: { read: vi.fn(), write: vi.fn() },
    })
    resolved = 'dark'
    for (const listener of listeners) listener()
    expect(theme.resolvedMode).toBe('dark')
    expect(state.preference.read()).toBeNull()
    theme.setMode('light')
    resolved = 'dark'
    for (const listener of listeners) listener()
    expect(theme.resolvedMode).toBe('light')
    expect(state.preference.read()).toBe('light')
    mirror.dispose()
    theme.dispose()
    state.preference.dispose()
  })
})

describe('browser preference storage', () => {
  it.each(['rct.mobile.theme', 'ruttrack.theme', 'web-panel.theme'])(
    'migrates %s even when the migration write is blocked',
    (key) => {
      const preference = createThemePreference(
        () => ({
          getItem: (name) => (name === key ? 'light' : null),
          setItem: () => {
            throw new Error('QuotaExceededError')
          },
        }),
        null,
      )
      expect(preference.read()).toBe('light')
      preference.dispose()
    },
  )
  it('syncs a validated choice between tabs without echoing writes and stops on dispose', () => {
    let listener: EventListener | null = null
    const state = setup('dark')
    const storage = { getItem: (key: string) => state.values.get(key) ?? null, setItem: vi.fn() }
    const events = {
      addEventListener: (_type: string, callback: EventListenerOrEventListenerObject) => {
        listener = callback as EventListener
      },
      removeEventListener: () => {
        listener = null
      },
    } as Pick<Window, 'addEventListener' | 'removeEventListener'>
    const preference = createThemePreference(() => storage, events)
    const mirror = createDeviceThemeMirror({
      ...state,
      preference,
      transport: { read: vi.fn(), write: vi.fn() },
    })
    const event = (value: string) =>
      ({ key: THEME_PREFERENCE_KEY, newValue: value, storageArea: storage }) as unknown as Event
    ;(listener as EventListener | null)?.(event('light'))
    expect(state.theme.mode).toBe('light')
    expect(storage.setItem).not.toHaveBeenCalled()
    ;(listener as EventListener | null)?.(event('corrupt'))
    expect(state.theme.mode).toBe('system')
    mirror.dispose()
    preference.dispose()
    state.theme.dispose()
    state.preference.dispose()
    expect(listener).toBeNull()
  })
})
