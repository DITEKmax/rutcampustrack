import { computed, readonly, ref, shallowRef, watch } from 'vue'
import {
  HeadmanGroupApi,
  type HeadmanAssistantPermission,
} from '../../../mobile-core/src/features/headman-group/headman-group-client'
import { createWebTransport } from './api'
import { webSession } from '../auth/session'

const permissions = shallowRef<readonly HeadmanAssistantPermission[]>([])
const loaded = ref(false)
const checking = ref(false)
const error = ref('')
let owner: string | null = null
let flight: Promise<readonly HeadmanAssistantPermission[]> | null = null
let flightTicket: object | null = null
let forcePending = false

async function load(force = false): Promise<readonly HeadmanAssistantPermission[]> {
  if (webSession.snapshot.value?.activeRole !== 'STUDENT') return []
  const current = webSession.identity.value
  if (owner === current && flight) {
    if (force) forcePending = true
    return flight
  }
  if (!force && owner === current && loaded.value) return permissions.value
  if (owner !== current) {
    permissions.value = []
    loaded.value = false
  }
  owner = current
  checking.value = true
  error.value = ''
  const transport = createWebTransport()
  const api = new HeadmanGroupApi(transport)
  const ticket = {}
  flightTicket = ticket
  const request = (async () => {
    try {
      do {
        forcePending = false
        try {
          const values = await api.listMyPermissions()
          transport.assertCurrent()
          permissions.value = values.map((value) => value.code)
          loaded.value = true
          error.value = ''
        } catch (cause) {
          transport.assertCurrent()
          if (!forcePending) {
            error.value = 'Не удалось проверить делегированный доступ. Попробуй ещё раз.'
            throw cause
          }
        }
      } while (forcePending)
      return permissions.value
    } finally {
      // Clear synchronously before resolving: a later force starts a fresh flight.
      if (flightTicket === ticket) {
        flight = null
        flightTicket = null
        checking.value = false
      }
    }
  })()
  flight = request
  return request
}

watch(
  webSession.identity,
  () => {
    permissions.value = []
    loaded.value = false
    checking.value = false
    error.value = ''
    owner = null
    flight = null
    flightTicket = null
    forcePending = false
    if (webSession.snapshot.value?.activeRole === 'STUDENT') void load().catch(() => undefined)
  },
  { flush: 'sync', immediate: true },
)

export const webAssistantAccess = {
  permissions: readonly(permissions),
  loaded: readonly(loaded),
  checking: readonly(checking),
  error: readonly(error),
  load,
  has: (permission: HeadmanAssistantPermission) =>
    computed(() => loaded.value && permissions.value.includes(permission)),
}
