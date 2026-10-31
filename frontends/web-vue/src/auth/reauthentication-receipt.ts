import { readonly, shallowRef, watch } from 'vue'
import { webSession } from './session'

export type ReauthenticationReceipt =
  | { kind: 'generated-password'; targetLabel: string; password: string }
  | { kind: 'credential-outcome-unknown'; targetLabel: string }
  | { kind: 'session-ended'; targetLabel: string; message: string }
const value = shallowRef<ReauthenticationReceipt | null>(null)

export interface SelfCredentialHandoff {
  confirmed(password: string): boolean
  unknown(): boolean
  cancel(): void
}
let selfIntent: (SelfCredentialHandoff & { readonly owner: string }) | null = null

/** One volatile self-CAS intent; a retired owner may hand off a notice, never a late secret. */
export function beginSelfCredentialHandoff(
  targetUserId: string,
  targetLabel: string,
  onHandoff: () => void,
): SelfCredentialHandoff {
  const owner = webSession.identity.value
  if (
    !owner ||
    webSession.phase.value !== 'ready' ||
    webSession.busy.value ||
    webSession.snapshot.value?.userId !== targetUserId
  )
    throw new Error('Не удалось подтвердить собственную сессию для изменения пароля.')
  selfIntent?.cancel()
  const intent = {
    owner,
    confirmed(password: string) {
      if (
        selfIntent !== intent ||
        webSession.phase.value !== 'ready' ||
        webSession.busy.value ||
        webSession.identity.value !== owner ||
        !password
      )
        return false
      return publish({ kind: 'generated-password', targetLabel, password })
    },
    unknown() {
      if (selfIntent !== intent) return false
      const current =
        webSession.phase.value === 'ready' &&
        !webSession.busy.value &&
        webSession.identity.value === owner
      const ended =
        webSession.phase.value === 'guest' &&
        webSession.identity.value === null &&
        !webSession.busy.value
      if (!current && !ended) return false
      return publish({ kind: 'credential-outcome-unknown', targetLabel })
    },
    cancel() {
      if (selfIntent === intent) selfIntent = null
    },
  }
  function publish(receipt: ReauthenticationReceipt) {
    // Consume before invalidation so the guest observer cannot replace a confirmed password.
    intent.cancel()
    onHandoff()
    value.value = receipt
    return true
  }
  selfIntent = intent
  return intent
}

/** Volatile only: never persisted, logged, placed in a route, or included in a notification. */
export const reauthenticationReceipt = {
  value: readonly(value),
  show: (receipt: ReauthenticationReceipt) => {
    value.value = receipt
  },
  clear: () => {
    value.value = null
  },
}
watch(
  [webSession.phase, webSession.identity, webSession.busy],
  ([phase, identity, busy], [previousPhase, previousIdentity]) => {
    if (
      (phase === 'ready' && previousPhase !== 'ready') ||
      busy ||
      (identity !== null && identity !== previousIdentity)
    ) {
      value.value = null
      if (selfIntent && (busy || identity !== selfIntent.owner)) selfIntent.cancel()
    }
    if (phase === 'guest' && !busy) selfIntent?.unknown()
  },
  { flush: 'sync' },
)
