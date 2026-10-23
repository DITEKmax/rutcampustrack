import { computed, readonly, ref, shallowRef } from 'vue'
import { AuthRequestError, createAuthClient } from '../../../pwa-vue/src/auth-client'
import type {
  ProfileRole,
  ProfileSnapshot,
} from '../../../mobile-core/src/features/profile/profile-types'

export type { ProfileRole, ProfileSnapshot }
export const roleLabels: Record<ProfileRole, string> = {
  STUDENT: 'Студент',
  HEADMAN: 'Староста',
  TEACHER: 'Преподаватель',
  ADMIN: 'Администратор',
}

const token = ref<string | null>(null)
const snapshot = shallowRef<ProfileSnapshot | null>(null)
const phase = ref<'starting' | 'guest' | 'ready' | 'error'>('starting')
const busy = ref(false)
const error = ref<string | null>(null)
const generation = ref(0)
let refreshFlight: Promise<void> | null = null
let refreshOwner: number | null = null
let operation = 0
const client = createAuthClient({ accessToken: () => token.value })

function clearSession() {
  token.value = null
  snapshot.value = null
  generation.value++
  phase.value = 'guest'
}

function authorityIdentity(value: ProfileSnapshot | null): string | null {
  return value
    ? JSON.stringify([
        value.userId,
        value.sessionId,
        value.sessionVersion,
        value.rolesVersion,
        value.activeRole,
        value.readOnly,
      ])
    : null
}

function acceptSession(value: ProfileSnapshot) {
  const changedAuthority = authorityIdentity(snapshot.value) !== authorityIdentity(value)
  snapshot.value = value
  if (changedAuthority) generation.value++
  phase.value = 'ready'
}

async function refresh() {
  if (refreshFlight) {
    if (refreshOwner === operation) return refreshFlight
    const caller = operation
    try {
      await refreshFlight
    } catch {
      /* Reconcile the current operation after an obsolete refresh settles. */
    }
    if (caller !== operation) return
    return refresh()
  }
  const revision = operation
  refreshOwner = revision
  refreshFlight = (async () => {
    try {
      const result = await client.refresh()
      if (revision !== operation) return
      token.value = result.accessToken
      const current = await client.getSnapshot()
      if (revision === operation) acceptSession(current)
    } catch (cause) {
      if (revision !== operation) return
      if (cause instanceof AuthRequestError && cause.status === 401) clearSession()
      else {
        snapshot.value = null
        generation.value++
        phase.value = 'error'
        error.value = message(cause)
      }
      throw cause
    }
  })().finally(() => {
    refreshFlight = null
  })
  return refreshFlight
}

async function bootstrap() {
  phase.value = 'starting'
  error.value = null
  try {
    await refresh()
  } catch {
    /* Refresh exposes guest or recoverable authority failure. */
  }
}

async function login(login: string, password: string) {
  const revision = ++operation
  busy.value = true
  error.value = null
  try {
    const result = await client.login({ login, password })
    if (revision !== operation) return
    token.value = result.accessToken
    const current = await client.getSnapshot()
    if (revision === operation) acceptSession(current)
  } catch (cause) {
    if (revision === operation) {
      // A lost login response may have set the refresh cookie. Read it back once.
      if (!(cause instanceof AuthRequestError) || cause.status === undefined) {
        try {
          await refresh()
          if (phase.value === 'ready') return
        } catch {
          /* Keep original login failure. */
        }
      }
      error.value = message(cause)
      if (token.value) phase.value = 'error'
    }
  } finally {
    if (revision === operation) busy.value = false
  }
}

async function selectRole(role: ProfileRole) {
  const current = snapshot.value
  if (current?.activeRole === role) return
  if (
    !current ||
    busy.value ||
    !current.roles.some((grant) => grant.role === role && grant.selectable)
  )
    return
  const revision = ++operation
  busy.value = true
  error.value = null
  // Unmount the old role immediately; no previous-role data remains interactive.
  snapshot.value = null
  generation.value++
  try {
    const result = await client.selectRole({ role, expectedSessionVersion: current.sessionVersion })
    if (revision !== operation) return
    token.value = result.accessToken
    acceptSession(result.session)
  } catch (cause) {
    if (revision !== operation) return
    try {
      await refresh()
    } catch {
      /* An uncertain role mutation is reconciled, never repeated. */
    }
    error.value = message(cause)
  } finally {
    if (revision === operation) busy.value = false
  }
}

async function logout() {
  if (busy.value) return
  ++operation
  busy.value = true
  error.value = null
  try {
    await client.logout()
    clearSession()
  } catch (cause) {
    // Logout may already have committed. Refresh determines whether the session remains.
    try {
      await refresh()
    } catch {
      /* State is authoritative or explicitly unavailable. */
    }
    if (phase.value !== 'guest') error.value = message(cause)
  } finally {
    busy.value = false
  }
}

export function message(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Не удалось связаться с сервером'
}

export const webSession = {
  identity: computed(() => authorityIdentity(snapshot.value)),
  snapshot: readonly(snapshot),
  phase: readonly(phase),
  busy: readonly(busy),
  error: readonly(error),
  generation: readonly(generation),
  roles: computed(
    () =>
      snapshot.value?.roles.filter(
        (grant) => grant.selectable || grant.role === snapshot.value?.activeRole,
      ) ?? [],
  ),
  accessToken: () => token.value,
  bootstrap,
  refresh,
  login,
  selectRole,
  logout,
}
