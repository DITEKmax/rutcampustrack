import { computed, onBeforeUnmount, ref } from 'vue'
import { createWebTransport, WebAuthorityChangedError } from '../../app/api'
import { webSession } from '../../auth/session'
import {
  HeadmanGroupApi,
  type HeadmanAssistantPermission,
} from '../../../../mobile-core/src/features/headman-group/headman-group-client'
import { HeadmanJournalApi } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client'
import { HeadmanLessonActionsApi } from '../../../../mobile-core/src/features/headman-journal/headman-journal-actions-client'
import { HeadmanRequestsApi } from '../../../../mobile-core/src/features/headman-requests/headman-requests-client'
import { HeadmanSubjectsApi } from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client'
import { HeadmanScheduleApi } from '../../../../mobile-core/src/features/schedule/headman-schedule-client'
import { HeadmanHomeworkApi } from '../../../../mobile-core/src/features/homework/headman-homework-client'
import { HeadmanStatsApi } from '../../../../mobile-core/src/features/headman-stats/headman-stats-client'

/** One admitted identity and transport for the entire lifetime of a mounted page. */
export function useHeadmanFeature() {
  const captured = createWebTransport()
  let alive = true
  const assertCurrent = () => {
    captured.assertCurrent()
    if (!alive) throw new WebAuthorityChangedError()
  }
  const transport = { ...captured, assertCurrent }
  onBeforeUnmount(() => {
    alive = false
  })
  const session = webSession.snapshot.value
  const isHeadman = session?.activeRole === 'HEADMAN'
  const groupId = Number(
    session?.roles.find((grant) => grant.role === session.activeRole && grant.status === 'ACTIVE')
      ?.groupId,
  )
  const permissions = ref<readonly HeadmanAssistantPermission[]>([])
  const ready = ref(false)
  const group = new HeadmanGroupApi(transport)
  const journal = new HeadmanJournalApi(transport)
  const actions = new HeadmanLessonActionsApi(transport)
  const requests = new HeadmanRequestsApi(transport)
  const subjects = new HeadmanSubjectsApi(transport)
  const schedule = new HeadmanScheduleApi(transport)
  const homework = new HeadmanHomeworkApi(transport)
  const stats = new HeadmanStatsApi(transport)
  const readOnly = computed(() => !!session?.readOnly)
  const allowed = (permission?: HeadmanAssistantPermission) =>
    ready.value && (isHeadman || (!!permission && permissions.value.includes(permission)))
  const writable = (permission?: HeadmanAssistantPermission) =>
    allowed(permission) && !readOnly.value
  async function initialize() {
    assertCurrent()
    if (
      !Number.isSafeInteger(groupId) ||
      groupId <= 0 ||
      !['HEADMAN', 'STUDENT'].includes(session?.activeRole ?? '')
    )
      throw new Error('Нет действующего доступа к группе.')
    if (!isHeadman) {
      const result = await group.listMyPermissions()
      assertCurrent()
      permissions.value = result.map((option) => option.code)
      if (!permissions.value.length) throw new Error('Тебе не назначены права помощника.')
    }
    ready.value = true
  }
  return {
    transport,
    assertCurrent,
    initialize,
    groupId,
    isHeadman,
    readOnly,
    permissions,
    allowed,
    writable,
    group,
    journal,
    actions,
    requests,
    subjects,
    schedule,
    homework,
    stats,
  }
}

export type HeadmanFeature = ReturnType<typeof useHeadmanFeature>
export const today = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
export function addDays(date: string, count: number) {
  const value = new Date(`${date}T12:00:00Z`)
  value.setUTCDate(value.getUTCDate() + count)
  return value.toISOString().slice(0, 10)
}
export function weekStart(date: string) {
  const day = new Date(`${date}T12:00:00Z`).getUTCDay()
  return addDays(date, -((day + 6) % 7))
}
export function dateLabel(date: string | null | undefined) {
  if (!date) return '—'
  const value = new Date(date.length === 10 ? `${date}T12:00:00Z` : date)
  return Number.isFinite(value.getTime())
    ? value.toLocaleDateString('ru-RU', {
        day: 'numeric',
        month: 'short',
        timeZone: 'Europe/Moscow',
      })
    : date
}
export const lessonTypes: Readonly<Record<string, string>> = {
  LECTURE: 'Лекция',
  PRACTICE: 'Практика',
  LAB: 'Лабораторная',
}
export const markLabels: Readonly<Record<string, string>> = {
  PRESENT: 'Был',
  ABSENT: 'Не был',
  EXCUSED: 'Уважительная',
  EMPTY: 'Нет отметки',
  CANCELLED: 'Отменено',
  UNSUPPORTED: 'Неизвестный статус',
}
export const markSymbols: Readonly<Record<string, string>> = {
  PRESENT: '+',
  ABSENT: 'н',
  EXCUSED: 'у',
  EMPTY: '—',
  CANCELLED: '×',
  UNSUPPORTED: '?',
}
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
