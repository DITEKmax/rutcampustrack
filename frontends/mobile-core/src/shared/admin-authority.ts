import type {
  AdminAccessClient,
  AdminAccessProfile,
} from '../features/admin-access/admin-access-client'
import { StaleSessionGenerationError } from './session-owner'

export interface AdminAuthoritySnapshot {
  readonly profile: AdminAccessProfile | null
  readonly status: 'idle' | 'loading' | 'ready' | 'error'
  readonly error: unknown | null
  readonly revision: number
}
/** Memory-only current authority. Invalidation publishes null before any await. */
export function createAdminAuthorityOwner() {
  let client: Pick<AdminAccessClient, 'getMe'> | null = null
  let ownerKey: string | null = null
  let expectedUserId: string | null = null
  let disposed = false
  let controller: AbortController | null = null
  let snapshot: AdminAuthoritySnapshot = { profile: null, status: 'idle', error: null, revision: 0 }
  const listeners = new Set<(snapshot: AdminAuthoritySnapshot) => void>()
  function publish(value: AdminAuthoritySnapshot): void {
    snapshot = value
    for (const listener of [...listeners]) listener(value)
  }
  function clear(): void {
    controller?.abort()
    controller = null
    publish({ profile: null, status: 'idle', error: null, revision: snapshot.revision + 1 })
  }
  async function refresh(): Promise<void> {
    if (disposed || !client || !ownerKey) return
    const currentClient = client
    const currentKey = ownerKey
    controller?.abort()
    controller = new AbortController()
    const revision = snapshot.revision + 1
    const request = controller
    publish({ profile: null, status: 'loading', error: null, revision })
    try {
      const profile = await currentClient.getMe(request.signal)
      if (
        disposed ||
        revision !== snapshot.revision ||
        currentClient !== client ||
        currentKey !== ownerKey
      )
        return
      if (expectedUserId !== null && String(profile.userId) !== expectedUserId)
        throw new Error('Не удалось подтвердить владельца прав доступа.')
      publish({ profile, status: 'ready', error: null, revision })
    } catch (error) {
      if (
        disposed ||
        revision !== snapshot.revision ||
        currentClient !== client ||
        currentKey !== ownerKey
      )
        return
      if (error instanceof StaleSessionGenerationError || request.signal.aborted) {
        clear()
        return
      }
      publish({ profile: null, status: 'error', error, revision })
    }
  }
  return {
    get snapshot(): AdminAuthoritySnapshot {
      return snapshot
    },
    subscribe(listener: (value: AdminAuthoritySnapshot) => void): () => void {
      listeners.add(listener)
      listener(snapshot)
      return () => listeners.delete(listener)
    },
    bind(
      value: Pick<AdminAccessClient, 'getMe'> | null,
      key: string | null,
      userId: string | null = null,
    ): void {
      if (disposed || (client === value && ownerKey === key && expectedUserId === userId)) return
      clear()
      client = value
      ownerKey = key
      expectedUserId = userId
    },
    refresh,
    clear,
    isCurrent(revision: number): boolean {
      return (
        !disposed &&
        revision === snapshot.revision &&
        snapshot.status === 'ready' &&
        snapshot.profile?.assigned === true
      )
    },
    assertCurrent(revision: number): void {
      if (
        disposed ||
        revision !== snapshot.revision ||
        snapshot.status !== 'ready' ||
        !snapshot.profile?.assigned
      )
        throw new StaleSessionGenerationError()
    },
    dispose(): void {
      if (disposed) return
      disposed = true
      clear()
      listeners.clear()
      client = null
      ownerKey = null
    },
  }
}

/** Password proof failures retain their form; every opaque/capability 403 fails closed. */
export function isAdminPasswordProofFailure(value: unknown): boolean {
  if (!value || typeof value !== 'object') return false
  const problem = value as { code?: unknown; extras?: { code?: unknown } }
  return (
    problem.code === 'CURRENT_PASSWORD_INVALID' ||
    problem.extras?.code === 'CURRENT_PASSWORD_INVALID'
  )
}
export async function shouldRefreshAdminAuthority(response: Response): Promise<boolean> {
  if (response.status !== 403) return false
  try {
    return !isAdminPasswordProofFailure(await response.clone().json())
  } catch {
    return true
  }
}

/** Pure getter for reactive map predicates. NaN is deliberately never current, including itself. */
export function createAdminGenerationReader(
  currentSession: () => number,
  authorityCurrent: () => boolean,
  capturedSession: number,
): () => number {
  return () => {
    const current = currentSession()
    return current === capturedSession && authorityCurrent() ? current : Number.NaN
  }
}
