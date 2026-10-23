import { readonly, shallowRef, watch } from 'vue'
import { webSession } from './session'

export interface ReauthenticationReceipt {
  kind: 'generated-password'
  targetLabel: string
  password: string
}
const value = shallowRef<ReauthenticationReceipt | null>(null)
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
  () => webSession.phase.value,
  (phase) => {
    if (phase === 'ready') value.value = null
  },
)
