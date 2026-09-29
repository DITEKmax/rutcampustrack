import { HeadmanJournalApiError } from '../headman-journal/headman-journal-client'
import { HeadmanRequestsApiError } from '../headman-requests/headman-requests-client'
import { HeadmanStatsApiError } from '../headman-stats/headman-stats-client'
import { HeadmanGroupApiError, type HeadmanAssistantPermission } from './headman-group-client'
import { HeadmanHomeworkApiError } from '../homework/headman-homework-client'

export type AssistantPermissionRefreshReason = 'forbidden' | 'foreground'

export interface AssistantPermissionRefresherOptions {
  currentGeneration(): number
  currentOwnerKey(): string | null
  load(
    generation: number,
    ownerKey: string,
  ): Promise<readonly HeadmanAssistantPermission[]>
  apply(permissions: readonly HeadmanAssistantPermission[]): void
  now?: () => number
  foregroundCooldownMs?: number
}

interface RefreshFlight {
  readonly requestId: number
  readonly generation: number
  readonly ownerKey: string
  readonly promise: Promise<boolean>
}

interface RefreshTimestamp {
  readonly generation: number
  readonly ownerKey: string
  readonly at: number
}

/** Coalesces safe refreshes and prevents stale or late responses from changing permissions. */
export function createAssistantPermissionRefresher(options: AssistantPermissionRefresherOptions) {
  const now = options.now ?? Date.now
  const foregroundCooldownMs = options.foregroundCooldownMs ?? 30_000
  let flight: RefreshFlight | null = null
  let lastForeground: RefreshTimestamp | null = null
  let latestRequestId = 0

  async function refresh(reason: AssistantPermissionRefreshReason): Promise<boolean> {
    const generation = options.currentGeneration()
    const ownerKey = options.currentOwnerKey()
    if (ownerKey === null) return false

    if (reason === 'foreground' && flight?.generation === generation && flight.ownerKey === ownerKey) {
      return flight.promise
    }

    if (reason === 'foreground') {
      if (lastForeground?.generation === generation && lastForeground.ownerKey === ownerKey
        && now() - lastForeground.at < foregroundCooldownMs) {
        return false
      }
    }

    const started: RefreshTimestamp = { generation, ownerKey, at: now() }
    if (reason === 'foreground') lastForeground = started
    const requestId = ++latestRequestId

    const promise = Promise.resolve()
      .then(() => options.load(generation, ownerKey))
      .then((permissions) => {
        if (requestId !== latestRequestId || options.currentGeneration() !== generation
          || options.currentOwnerKey() !== ownerKey) return false
        options.apply(permissions)
        return true
      })
      .catch((cause: unknown) => {
        if (requestId !== latestRequestId || options.currentGeneration() !== generation
          || options.currentOwnerKey() !== ownerKey) return false
        throw cause
      })
      .finally(() => {
        if (flight?.requestId === requestId) {
          flight = null
        }
      })

    flight = { requestId, generation, ownerKey, promise }
    return promise
  }

  return { refresh }
}

/** These assistant operations use 403 for capability denial, not session loss. */
export function isAssistantCapabilityForbiddenError(cause: unknown): boolean {
  if (cause instanceof HeadmanHomeworkApiError || cause instanceof HeadmanStatsApiError
    || cause instanceof HeadmanRequestsApiError || cause instanceof HeadmanJournalApiError) {
    return cause.response.status === 403
  }
  return false
}

/** Permission enumeration may be denied independently of the student session. */
export function isAssistantPermissionReadForbidden(cause: unknown): boolean {
  return cause instanceof HeadmanGroupApiError && cause.response.status === 403
}
