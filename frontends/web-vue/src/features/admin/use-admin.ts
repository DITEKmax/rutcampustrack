import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { createWebTransport, WebAuthorityChangedError } from '../../app/api'
import { webSession, message } from '../../auth/session'
import {
  AdminAccessClient,
  adminHasPermission,
  type AdminAccessProfile,
} from '../../../../mobile-core/src/features/admin-access/admin-access-client'

/** Each mounted page owns one transport; decoded results cannot cross an authority change. */
export function useAdmin() {
  const transport = createWebTransport()
  const identity = webSession.identity.value
  const controller = new AbortController()
  let mounted = true
  const assertCurrent = () => {
    transport.assertCurrent()
    if (!mounted) throw new WebAuthorityChangedError()
  }
  const options = {
    ...transport,
    assertCurrent,
    sessionScopeFor: () => {
      assertCurrent()
      return identity
    },
  }
  const access = new AdminAccessClient(options)
  const authority = shallowRef<AdminAccessProfile | null>(null)
  const loading = ref(true)
  const busy = ref(false)
  const error = ref('')
  const notice = ref('')
  const can = (code: string) => adminHasPermission(authority.value, code)
  const full = computed(() => authority.value?.assigned === true && authority.value.mode === 'FULL')

  async function refreshAuthority() {
    authority.value = null
    const value = await access.getMe(controller.signal)
    assertCurrent()
    if (String(value.userId) !== webSession.snapshot.value?.userId)
      throw new Error('Не удалось подтвердить владельца административных прав.')
    authority.value = value
    return value
  }
  async function requirePermission(code: string) {
    await refreshAuthority()
    assertCurrent()
    if (!can(code)) throw new Error('У тебя нет права на это действие.')
  }
  async function run(task: () => Promise<void>, permission?: string) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    notice.value = ''
    try {
      if (permission) await requirePermission(permission)
      await task()
      assertCurrent()
    } catch (cause) {
      try {
        assertCurrent()
        error.value = message(cause)
        if (
          cause &&
          typeof cause === 'object' &&
          'response' in cause &&
          cause.response instanceof Response &&
          cause.response.status === 403
        ) {
          authority.value = null
          await refreshAuthority().catch(() => undefined)
          assertCurrent()
        }
      } catch {
        /* The shell has retired this page's authority. */
      }
    } finally {
      if (mounted) busy.value = false
    }
  }
  async function init(permission: string, load: () => Promise<void>) {
    await run(async () => {
      await requirePermission(permission)
      await load()
    })
    if (mounted) loading.value = false
  }
  onBeforeUnmount(() => {
    mounted = false
    controller.abort()
    authority.value = null
  })
  return {
    options,
    access,
    authority,
    loading,
    busy,
    error,
    notice,
    can,
    full,
    assertCurrent,
    run,
    init,
    refreshAuthority,
    requirePermission,
    signal: controller.signal,
  }
}

export const roleLabels = {
  STUDENT: 'Студент',
  HEADMAN: 'Староста',
  TEACHER: 'Преподаватель',
  ADMIN: 'Администратор',
}
export const statusLabels = {
  ACTIVE: 'Активна',
  EXPELLED: 'Отчислен',
  GRADUATED: 'Выпустился',
  SUSPENDED: 'Приостановлена',
  DISMISSED: 'Уволен',
  ARCHIVED: 'В архиве',
}
export const dateLabel = (value: string) => new Date(value).toLocaleDateString('ru-RU')
