import { computed, readonly, ref, shallowRef, watch } from 'vue'
import {
  HeadmanGroupApi,
  type HeadmanAssistantPermission,
} from '../../../mobile-core/src/features/headman-group/headman-group-client'
import { createWebTransport } from './api'
import { webSession } from '../auth/session'

const permissions = shallowRef<readonly HeadmanAssistantPermission[]>([])
const loaded = ref(false)
let owner: string | null = null
let flight: Promise<readonly HeadmanAssistantPermission[]> | null = null

async function load(force = false): Promise<readonly HeadmanAssistantPermission[]> {
  if (webSession.snapshot.value?.activeRole !== 'STUDENT') return []
  const current = webSession.identity.value
  if (owner === current && flight) return flight
  if (!force && owner === current && loaded.value) return permissions.value
  owner = current
  loaded.value = false
  const transport = createWebTransport()
  const api = new HeadmanGroupApi(transport)
  const request = (async () => {
    const values = await api.listMyPermissions()
    transport.assertCurrent()
    permissions.value = values.map((value) => value.code)
    loaded.value = true
    return permissions.value
  })()
  flight = request
  try {
    return await request
  } finally {
    if (flight === request) flight = null
  }
}

watch(
  webSession.identity,
  () => {
    permissions.value = []
    loaded.value = false
    owner = null
    flight = null
    if (webSession.snapshot.value?.activeRole === 'STUDENT') void load().catch(() => undefined)
  },
  { flush: 'sync', immediate: true },
)

export const webAssistantAccess = {
  permissions: readonly(permissions),
  loaded: readonly(loaded),
  load,
  has: (permission: HeadmanAssistantPermission) =>
    computed(() => loaded.value && permissions.value.includes(permission)),
}
