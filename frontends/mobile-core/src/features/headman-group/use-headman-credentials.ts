import { computed, onScopeDispose, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  HeadmanGroupApiError,
  type HeadmanCredentialView,
  type HeadmanGroupApi,
  type HeadmanRecoveryLink,
} from './headman-group-client'

export interface HeadmanCredentialContext {
  readonly api: HeadmanGroupApi | null
  readonly groupId: number | null
  readonly actorUserId: string | null
  readonly enabled: boolean
  readonly memberIds: readonly number[]
}

/** One screen owns these secrets; no cache/store/persistence or uncertain-write replay. */
export function useHeadmanCredentials(context: () => HeadmanCredentialContext) {
  const values = ref<Readonly<Record<number, HeadmanCredentialView>>>({})
  const link = ref<HeadmanRecoveryLink | null>(null)
  const loading = ref(false)
  const busy = ref(false)
  const uncertain = ref(false)
  const error = ref<string | null>(null)
  let revision = 0
  let disposed = false
  const available = computed(
    () => context().enabled && !loading.value && !busy.value && !uncertain.value,
  )
  function clear(): void {
    revision++
    values.value = {}
    link.value = null
    loading.value = false
    busy.value = false
    uncertain.value = false
    error.value = null
  }
  function owner(): { api: HeadmanGroupApi; current: () => boolean } | null {
    const initial = context()
    if (!initial.enabled || !initial.api || initial.groupId === null || !initial.actorUserId)
      return null
    const token = revision
    const api = initial.api
    return {
      api,
      current: () => {
        const next = context()
        return (
          !disposed &&
          token === revision &&
          next.enabled &&
          next.api === api &&
          next.groupId === initial.groupId &&
          next.actorUserId === initial.actorUserId
        )
      },
    }
  }
  async function refresh(): Promise<void> {
    clear()
    const active = owner()
    if (!active) return
    const targets = [...context().memberIds]
    loading.value = true
    try {
      const next: Record<number, HeadmanCredentialView> = {}
      // Serial reads keep the existing group-sized carrier bounded without a request burst.
      for (const studentId of targets) {
        const value = await active.api.readCredential(studentId)
        if (!active.current()) return
        next[studentId] = value
      }
      if (active.current()) values.value = next
    } catch (cause) {
      if (!active.current() || cause instanceof StaleSessionGenerationError) return
      values.value = {}
      link.value = null
      error.value = 'Данные доступа не подтверждены. Обнови их перед продолжением.'
    } finally {
      if (active.current()) loading.value = false
    }
  }
  async function perform(
    studentId: number,
    action: 'generate' | 'link',
    expectedVersion?: string,
  ): Promise<void> {
    if (!available.value || !context().memberIds.includes(studentId)) return
    const active = owner()
    const before = values.value[studentId]
    if (
      !active ||
      !before ||
      (action === 'generate' && before.credentialsVersion !== expectedVersion)
    )
      return
    busy.value = true
    values.value = {}
    link.value = null
    error.value = null
    try {
      if (action === 'generate') {
        const result = await active.api.generatePassword(studentId, before.credentialsVersion)
        if (active.current()) values.value = { [studentId]: result }
      } else {
        const result = await active.api.issueRecoveryLink(studentId)
        if (!active.current()) return
        // Link issuance changes no password: the student may have changed it since the prior view.
        const current = await active.api.readCredential(studentId)
        if (active.current()) {
          values.value = { [studentId]: current }
          link.value = result
        }
      }
    } catch (cause) {
      if (!active.current() || cause instanceof StaleSessionGenerationError) return
      // Even a stale CAS must be reread; never blindly repeat a mutation after an uncertain response.
      uncertain.value = true
      error.value =
        cause instanceof HeadmanGroupApiError && cause.response.status === 409
          ? 'Доступ изменился. Обнови данные и проверь текущий пароль перед новым действием.'
          : 'Результат не подтверждён. Проверь данные доступа; повторный сброс не выполняется автоматически.'
    } finally {
      if (active.current()) busy.value = false
    }
  }
  watch(
    context,
    () => {
      void refresh()
    },
    { immediate: true, flush: 'sync' },
  )
  onScopeDispose(() => {
    disposed = true
    clear()
  })
  return { values, link, loading, busy, uncertain, error, available, refresh, perform, clear }
}
