import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { StudentApi, StudentApiError } from '../../../../mobile-core/src/api/student-client'
import type { StudentSession } from '../../../../mobile-core/src/api/types'
import { createWebTransport } from '../../app/api'
import { useWebLiveRefresh } from '../../app/live'
import { webSession } from '../../auth/session'

export function studentError(cause: unknown): string {
  if (cause instanceof StudentApiError) {
    if (cause.response.status === 403) return 'Доступ изменился. Обнови данные текущей роли.'
    if (cause.response.status === 404) return 'Данные больше недоступны.'
    if (cause.response.status === 410) return 'Срок хранения вложения истёк.'
    return cause.message
  }
  return cause instanceof TypeError
    ? 'Не удалось связаться с сервером. Повтори попытку.'
    : cause instanceof Error
      ? cause.message
      : 'Не удалось получить данные.'
}

/** A mounted page owns one frozen transport and all its decoded projections. */
export function useStudentScope() {
  const transport = createWebTransport()
  const api = new StudentApi(transport)
  const identity = webSession.identity.value
  let alive = true
  let revision = 0
  const current = () => alive && webSession.identity.value === identity
  const session = shallowRef<StudentSession | null>(null)
  const loading = ref(false),
    error = ref(''),
    refreshed = ref(0)
  const dirty = ref(false)
  const blocked = computed(() => dirty.value)
  const contextKey = computed(() => {
    const value = session.value
    return value
      ? JSON.stringify([
          value.user.id,
          value.group?.id,
          value.semester?.id,
          value.sessionId,
          value.sessionVersion,
          value.rolesVersion,
          value.readOnly,
        ])
      : null
  })
  const readOnly = computed(() =>
    Boolean(webSession.snapshot.value?.readOnly || session.value?.readOnly),
  )
  async function load() {
    const run = ++revision
    loading.value = true
    error.value = ''
    try {
      const result = await api.getSession()
      transport.assertCurrent()
      if (!current() || run !== revision) return
      session.value = result
      refreshed.value++
    } catch (cause) {
      if (!current() || run !== revision) return
      session.value = null
      error.value = studentError(cause)
    } finally {
      if (current() && run === revision) loading.value = false
    }
  }
  onMounted(load)
  useWebLiveRefresh(load, { blocked })
  onBeforeUnmount(() => {
    alive = false
    revision++
  })
  return {
    transport,
    api,
    identity,
    current,
    session,
    loading,
    error,
    refreshed,
    readOnly,
    dirty,
    contextKey,
    load,
  }
}
export type StudentScope = ReturnType<typeof useStudentScope>

export function useStudentResource<T>(scope: StudentScope) {
  const data = shallowRef<T | null>(null),
    loading = ref(false),
    error = ref('')
  let revision = 0
  function clear() {
    revision++
    data.value = null
    error.value = ''
    loading.value = false
  }
  async function load(read: () => Promise<T>, retain = false) {
    const run = ++revision
    loading.value = true
    error.value = ''
    if (!retain) data.value = null
    try {
      const value = await read()
      scope.transport.assertCurrent()
      if (scope.current() && run === revision) data.value = value
    } catch (cause) {
      if (!scope.current() || run !== revision) return
      if (cause instanceof StudentApiError && [401, 403, 404].includes(cause.response.status))
        data.value = null
      error.value = studentError(cause)
    } finally {
      if (scope.current() && run === revision) loading.value = false
    }
  }
  onBeforeUnmount(clear)
  watch(scope.contextKey, clear, { flush: 'sync' })
  return { data, loading, error, load, clear }
}
