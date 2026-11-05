import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue'
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
  readonly detailStudentId: number | null
}

interface IssuedLink {
  readonly value: HeadmanRecoveryLink
  readonly studentId: number
  readonly expiresAt: number
  readonly isCurrentOwner: () => boolean
}

function copyWithSelection(value: string, isCurrent: () => boolean): boolean {
  if (typeof document === 'undefined' || !document.body || typeof document.execCommand !== 'function')
    return false
  const previous = document.activeElement
  const field = document.createElement('textarea')
  field.className = 'headman-group__clipboard-buffer'
  field.value = value
  field.readOnly = true
  field.tabIndex = -1
  try {
    document.body.append(field)
    field.focus({ preventScroll: true })
    field.select()
    field.setSelectionRange(0, value.length)
    return isCurrent() && document.execCommand('copy')
  } catch {
    return false
  } finally {
    field.value = ''
    field.remove()
    if (previous && previous instanceof HTMLElement && previous.isConnected)
      previous.focus({ preventScroll: true })
  }
}

/** One screen owns these secrets; no cache/store/persistence or uncertain-write replay. */
export function useHeadmanCredentials(context: () => HeadmanCredentialContext) {
  const values = ref<Readonly<Record<number, HeadmanCredentialView>>>({})
  const issuedLink = shallowRef<IssuedLink | null>(null)
  const link = computed(() => issuedLink.value?.value ?? null)
  const linkExpired = ref(false)
  const copyBusy = ref(false)
  const copyFeedback = ref<'copied' | 'manual' | null>(null)
  const loading = ref(false)
  const busy = ref(false)
  const uncertain = ref(false)
  const error = ref<string | null>(null)
  let revision = 0
  let disposed = false
  let expiryTimer: ReturnType<typeof setTimeout> | null = null
  const available = computed(
    () => context().enabled && !loading.value && !busy.value && !copyBusy.value && !uncertain.value,
  )
  function clearLink(expired = false): void {
    if (expiryTimer !== null) clearTimeout(expiryTimer)
    expiryTimer = null
    issuedLink.value = null
    linkExpired.value = expired
    copyBusy.value = false
    copyFeedback.value = null
  }
  function clear(): void {
    revision++
    values.value = {}
    clearLink()
    loading.value = false
    busy.value = false
    uncertain.value = false
    error.value = null
  }
  function owner(): { api: HeadmanGroupApi; current: () => boolean } | null {
    const initial = context()
    if (
      !initial.enabled ||
      !initial.api?.isCurrentGeneration() ||
      initial.groupId === null ||
      !initial.actorUserId
    )
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
          api.isCurrentGeneration() &&
          next.groupId === initial.groupId &&
          next.actorUserId === initial.actorUserId
        )
      },
    }
  }
  function publishLink(value: HeadmanRecoveryLink, studentId: number, isCurrentOwner: () => boolean) {
    const expiresAt = Date.parse(value.expiresAt)
    const remaining = expiresAt - Date.now()
    if (!Number.isFinite(remaining) || remaining <= 0) {
      clearLink(true)
      return
    }
    const issued: IssuedLink = { value, studentId, expiresAt, isCurrentOwner }
    issuedLink.value = issued
    function expire(): void {
      if (issuedLink.value !== issued) return
      const remaining = expiresAt - Date.now()
      if (!isCurrentOwner()) clearLink()
      else if (remaining <= 0) clearLink(true)
      else expiryTimer = setTimeout(expire, Math.min(remaining, 2147483647))
    }
    expiryTimer = setTimeout(expire, Math.min(remaining, 2147483647))
  }
  function currentLink(studentId: number, expected: IssuedLink | null = issuedLink.value) {
    if (!expected || issuedLink.value !== expected || studentId !== expected.studentId) return null
    const next = context()
    if (
      !expected.isCurrentOwner() ||
      next.detailStudentId !== studentId ||
      !next.memberIds.includes(studentId)
    ) {
      clearLink()
      return null
    }
    if (Date.now() >= expected.expiresAt) {
      clearLink(true)
      return null
    }
    return expected
  }
  async function copyLink(studentId: number): Promise<void> {
    const issued = currentLink(studentId)
    if (!issued || copyBusy.value) return
    copyBusy.value = true
    copyFeedback.value = null
    const isCurrent = () => currentLink(studentId, issued) !== null
    let copied = false
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(issued.value.url)
        copied = true
      }
    } catch {
      // Some WebViews deny the async API. Try the synchronous selection fallback below.
    }
    if (!isCurrent()) return
    if (!copied) copied = copyWithSelection(issued.value.url, isCurrent)
    if (!isCurrent()) return
    copyBusy.value = false
    copyFeedback.value = copied ? 'copied' : 'manual'
  }
  function selectLink(studentId: number, field: HTMLInputElement): void {
    if (currentLink(studentId)) field.select()
    else field.value = ''
  }
  function guardManualCopy(studentId: number, event: ClipboardEvent): void {
    if (!currentLink(studentId)) event.preventDefault()
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
      clearLink()
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
    const initial = context()
    if (
      !available.value ||
      !initial.memberIds.includes(studentId) ||
      (action === 'link' && initial.detailStudentId !== studentId)
    )
      return
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
    clearLink()
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
          if (context().detailStudentId === studentId)
            publishLink(result, studentId, active.current)
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
    () => {
      const current = context()
      return [current, current.api?.isCurrentGeneration()] as const
    },
    () => {
      void refresh()
    },
    { immediate: true, flush: 'sync' },
  )
  onScopeDispose(() => {
    disposed = true
    clear()
  })
  return {
    values,
    link,
    linkExpired,
    copyBusy,
    copyFeedback,
    loading,
    busy,
    uncertain,
    error,
    available,
    refresh,
    perform,
    copyLink,
    selectLink,
    guardManualCopy,
    clear,
  }
}
