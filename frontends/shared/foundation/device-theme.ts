import type { ThemeController, ThemeMode } from './theme'
import { isThemeMode } from './theme-preference'

export interface DeviceThemeSnapshot {
  readonly theme: ThemeMode | null
  readonly revision: string
}
export interface DeviceThemeSession {
  /** Authentication generation plus admitted session identity; never persisted. */
  readonly key: string
  readonly accessToken: string
}
export interface DeviceThemeTransport {
  read(session: DeviceThemeSession): Promise<DeviceThemeSnapshot>
  write(
    session: DeviceThemeSession,
    theme: ThemeMode,
    revision: string,
  ): Promise<DeviceThemeSnapshot>
}
export class DeviceThemeHttpError extends Error {
  constructor(readonly status: number) {
    super('Device theme request failed')
  }
}
export function createDeviceThemeTransport(fetcher: typeof fetch = fetch): DeviceThemeTransport {
  async function request(
    session: DeviceThemeSession,
    body?: { theme: ThemeMode; expectedRevision: string },
  ): Promise<DeviceThemeSnapshot> {
    const response = await fetcher('/api/auth/device-preferences', {
      method: body ? 'PUT' : 'GET',
      credentials: 'include',
      cache: 'no-store',
      headers: {
        Authorization: `Bearer ${session.accessToken}`,
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    })
    if (!response.ok) throw new DeviceThemeHttpError(response.status)
    const value: unknown = await response.json()
    if (
      !value ||
      typeof value !== 'object' ||
      !('theme' in value) ||
      !('revision' in value) ||
      (value.theme !== null && !isThemeMode(value.theme)) ||
      typeof value.revision !== 'string' ||
      !/^(0|[1-9]\d*)$/.test(value.revision)
    ) {
      throw new Error('Invalid device theme response')
    }
    return { theme: value.theme, revision: value.revision }
  }
  return {
    read: (session) => request(session),
    write: (session, theme, expectedRevision) => request(session, { theme, expectedRevision }),
  }
}

/** Serial CAS mirror. Local/offline choice wins; late auth/selection replies cannot change it. */
export function createDeviceThemeMirror(options: {
  theme: ThemeController
  preference: {
    read(): ThemeMode | null
    write(mode: ThemeMode): void
    subscribe(listener: (mode: ThemeMode | null) => void): () => void
  }
  transport: DeviceThemeTransport
}) {
  let session: DeviceThemeSession | null = null
  let generation = 0,
    epoch = 0
  let choice = options.preference.read()
  let applying = false,
    disposed = false,
    running = false,
    dirty = false
  let previous = { mode: options.theme.mode, resolvedMode: options.theme.resolvedMode }
  const stopTheme = options.theme.subscribe((snapshot) => {
    const systemOnly =
      snapshot.mode === previous.mode && snapshot.resolvedMode !== previous.resolvedMode
    previous = snapshot
    if (applying || systemOnly || disposed) return
    choice = snapshot.mode
    options.preference.write(choice)
    epoch++
    schedule()
  })
  const stopStorage = options.preference.subscribe((mode) => {
    choice = mode
    epoch++
    applying = true
    options.theme.setMode(mode ?? 'system')
    applying = false
    schedule()
  })
  function current(capturedGeneration: number): boolean {
    return !disposed && capturedGeneration === generation && session !== null
  }
  async function reconcile(): Promise<void> {
    if (running || disposed || !session) return
    running = true
    try {
      while (dirty && session && !disposed) {
        dirty = false
        const activeSession = session,
          activeGeneration = generation
        let observed: DeviceThemeSnapshot
        try {
          observed = await options.transport.read(activeSession)
        } catch {
          continue
        }
        if (!current(activeGeneration)) continue
        for (let attempt = 0; attempt < 3 && current(activeGeneration); attempt++) {
          if (choice === null) {
            // A selection made while GET was pending is inspected above before fallback adoption.
            if (observed.theme !== null) {
              choice = observed.theme
              options.preference.write(choice)
              applying = true
              options.theme.setMode(choice)
              applying = false
            }
            break
          }
          if (observed.theme === choice) break
          const selected = choice,
            selectedEpoch = epoch
          try {
            const saved = await options.transport.write(activeSession, selected, observed.revision)
            if (!current(activeGeneration)) break
            observed = saved
            if (selectedEpoch !== epoch) continue
            break
          } catch (error) {
            if (!current(activeGeneration)) break
            if (error instanceof DeviceThemeHttpError && error.status !== 409 && error.status < 500)
              break
            // An interrupted PUT may have committed: read first, never retry its old revision blindly.
            try {
              observed = await options.transport.read(activeSession)
            } catch {
              break
            }
          }
        }
      }
    } finally {
      running = false
      if (dirty && session && !disposed) void reconcile()
    }
  }
  function schedule(): void {
    dirty = true
    void reconcile()
  }
  return {
    setSession(next: DeviceThemeSession | null): void {
      if (disposed) return
      if (session?.key === next?.key) {
        const refreshed = session?.accessToken !== next?.accessToken
        session = next
        if (next && refreshed) schedule()
        return
      }
      session = next
      generation++
      if (next) schedule()
    },
    /** Explicit retry on reconnect/admission; no background retry loop. */
    reconcile: schedule,
    dispose(): void {
      disposed = true
      generation++
      session = null
      stopTheme()
      stopStorage()
    },
  }
}
