import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { consumePwaNotificationLaunch } from '../../../mobile-core/src/features/notifications/notifications-launch'
import type { ProfileRole } from '../../../mobile-core/src/features/profile/profile-types'

export const group = 'ДЕМО-411'
export const subject = 'Основы программирования'
export const room = 'А-405'
export const student = 'Анна Смирнова'
export const demoDate = '2026-11-09'
export const emptyDate = '2026-11-14'
export const notificationHomeworkId = '901'
export const notificationHomeworkDate = '2026-11-16'
export const notificationHomeworkTitle = 'Проверь реализацию двоичного поиска'
const dayIds = [
  ['2026-10-19', 700],
  ['2026-10-26', 705],
  ['2026-11-02', 710],
  [demoDate, 715],
  ['2026-11-10', 720],
  ['2026-11-11', 725],
  ['2026-11-12', 730],
  ['2026-11-13', 735],
  ['2026-11-16', 740],
  ['2026-11-23', 745],
  ['2026-11-30', 750],
] as const
export const scheduleDates = dayIds.map(([date]) => date)
const lessonNumbers = [1, 2, 3, 4, 5] as const
const physicalLessons = new Map(dayIds.flatMap(([date, firstId]) =>
  lessonNumbers.map((number, index) => [String(firstId + index), { date, number }] as const),
))
export function physicalLessonId(date: string, number: number) {
  const id = [...physicalLessons].find(([, slot]) => slot.date === date && slot.number === number)?.[0]
  if (!id) throw new RangeError('В демо нет такой пары')
  return id
}
export const lessonId = physicalLessonId(demoDate, 2)
export const scenarios = ['standard', 'cancelled', 'geo-unavailable', 'pending', 'empty-day'] as const
export type DemoScenario = (typeof scenarios)[number]
export type DemoSetupScenario = Exclude<DemoScenario, 'pending'>
export const markedAt = '2026-11-09T07:12:00Z'

export const roles = [
  { id: 'STUDENT', label: 'Студент', home: 'today' },
  { id: 'HEADMAN', label: 'Староста', home: 'group' },
  { id: 'TEACHER', label: 'Преподаватель', home: 'journal' },
  { id: 'ADMIN', label: 'Администратор', home: 'admin' },
] as const satisfies readonly { id: ProfileRole; label: string; home: string }[]
export type MarkState = 'ready' | 'saved' | 'replayed'
export const studentViews = [
  'today',
  'attendance',
  'homework',
  'more',
  'profile',
  'schedule',
  'statistics',
] as const
const roleViews = ['group', 'journal', 'admin', 'admin-attendance'] as const
export type StudentView = (typeof studentViews)[number]
export type RoleView = (typeof roleViews)[number]
export type PhoneView = StudentView | RoleView
export type ManualMark = 'PRESENT' | 'ABSENT'
export const editableMemberIds = [2, 3, 5, 6] as const
export type EditableMemberId = (typeof editableMemberIds)[number]
export interface ManualRow { readonly value: ManualMark; readonly revision: number }
export type ManualMarks = Readonly<Record<string, ManualRow | undefined>>
export const rowKey = (lesson: string, member: EditableMemberId) => lesson + ':' + member
export const channel = 'rct-presentation-demo-v1'

export interface DemoHomeworkFocus {
  readonly itemId: typeof notificationHomeworkId
  readonly requestId: number
}

export interface DemoSnapshot {
  readonly markState: MarkState
  readonly view: PhoneView
  readonly manualMarks: ManualMarks
  readonly revision: number
  readonly resetEpoch: number
  readonly focusMark: boolean
  readonly date: string
  readonly selectedLesson: string | null
  readonly scenario: DemoScenario
  readonly homeworkFocus: DemoHomeworkFocus | null
}

export const initialSnapshot: DemoSnapshot = {
  markState: 'ready',
  view: 'today',
  manualMarks: {},
  revision: 0,
  resetEpoch: 0,
  focusMark: false,
  date: demoDate,
  selectedLesson: lessonId,
  scenario: 'standard',
  homeworkFocus: null,
}

export function isStudentView(value: unknown): value is StudentView {
  return studentViews.some((view) => value === view)
}

export function isPhoneView(value: unknown): value is PhoneView {
  return isStudentView(value) || roleViews.some((view) => value === view)
}

export function roleForView(view: PhoneView): ProfileRole {
  if (view === 'group') return 'HEADMAN'
  if (view === 'journal') return 'TEACHER'
  if (view === 'admin' || view === 'admin-attendance') return 'ADMIN'
  return 'STUDENT'
}

export function isEditableMemberId(value: unknown): value is EditableMemberId {
  return editableMemberIds.some((id) => id === value)
}

export function isManualMark(value: unknown): value is ManualMark {
  return value === 'PRESENT' || value === 'ABSENT'
}

export function isLessonId(value: unknown): value is string {
  return typeof value === 'string' && physicalLessons.has(value)
}

export function isDemoDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^2026-(?:09|10|11|12)-[0-9]{2}$/.test(value)) return false
  const parsed = new Date(value + 'T12:00:00Z')
  return value >= '2026-09-01' && value <= '2026-12-27' &&
    Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

export function isRevision(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
}

export function isSnapshot(value: unknown): value is DemoSnapshot {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<DemoSnapshot>
  return (
    isPhoneView(candidate.view) &&
    ['ready', 'saved', 'replayed'].includes(candidate.markState ?? '') &&
    candidate.manualMarks != null &&
    typeof candidate.manualMarks === 'object' &&
    !Array.isArray(candidate.manualMarks) &&
    Object.entries(candidate.manualMarks).every(([key, row]) => {
      const [lesson, member, extra] = key.split(':')
      return !extra && isLessonId(lesson) && isEditableMemberId(Number(member)) &&
        !!row && isManualMark(row.value) && isRevision(row.revision) && row.revision > 1
    }) &&
    isRevision(candidate.revision) &&
    isRevision(candidate.resetEpoch) &&
    typeof candidate.focusMark === 'boolean' &&
    isDemoDate(candidate.date) &&
    (candidate.selectedLesson === null ||
      (isLessonId(candidate.selectedLesson) && physicalLessons.get(candidate.selectedLesson)?.date === candidate.date)) &&
    scenarios.some(item => item === candidate.scenario) &&
    (candidate.homeworkFocus === null ||
      (candidate.view === 'homework' && candidate.date === notificationHomeworkDate &&
        candidate.homeworkFocus?.itemId === notificationHomeworkId &&
        isRevision(candidate.homeworkFocus.requestId) && candidate.homeworkFocus.requestId > 0))
  )
}

const demoSessionKey = 'rct-presentation-demo-v1'
function readDemoSession(): DemoSnapshot {
  try {
    const stored: unknown = JSON.parse(window.sessionStorage.getItem(demoSessionKey) ?? 'null')
    if (isSnapshot(stored)) return stored
  } catch {
    // The same demo still works if browser storage is unavailable.
  }
  return initialSnapshot
}

/** The parent owns the journal. Navigation revisions never become row revisions. */
export function useDemo() {
  const initial = readDemoSession()
  const savedMark = { lesson: lessonId, markedAt }
  const record = shallowRef(initial.markState === 'ready' ? null : savedMark)
  const manualMarks = shallowRef(initial.manualMarks)
  const replayed = ref(initial.markState === 'replayed')
  const view = ref(initial.view)
  const date = ref<string>(initial.date)
  const selectedLesson = ref(initial.selectedLesson)
  const scenario = ref(initial.scenario)
  const revision = ref(0)
  const resetEpoch = ref(0)
  const focusMark = ref(false)
  const homeworkFocus = shallowRef<DemoHomeworkFocus | null>(null)
  let homeworkRequestId = 0
  const markState = computed<MarkState>(() =>
    !record.value ? 'ready' : replayed.value ? 'replayed' : 'saved',
  )
  const snapshot = computed<DemoSnapshot>(() => ({
    markState: markState.value, view: view.value, manualMarks: manualMarks.value,
    revision: revision.value, resetEpoch: resetEpoch.value, focusMark: focusMark.value,
    date: date.value, selectedLesson: selectedLesson.value, scenario: scenario.value,
    homeworkFocus: homeworkFocus.value,
  }))
  watch(snapshot, next => {
    try {
      const stored = { ...next, revision: 0, resetEpoch: 0, focusMark: false, homeworkFocus: null }
      if (JSON.stringify(stored) === JSON.stringify(initialSnapshot))
        window.sessionStorage.removeItem(demoSessionKey)
      else
        window.sessionStorage.setItem(demoSessionKey, JSON.stringify(stored))
    } catch {
      // Storage is optional for the local demo.
    }
  })
  function restoreOnReturn(event: PageTransitionEvent) {
    if (!event.persisted) return
    const next = readDemoSession()
    if (next.markState === snapshot.value.markState && next.view === view.value &&
        next.date === date.value && next.selectedLesson === selectedLesson.value &&
        next.scenario === scenario.value && JSON.stringify(next.manualMarks) === JSON.stringify(manualMarks.value))
      return
    record.value = next.markState === 'ready' ? null : savedMark
    replayed.value = next.markState === 'replayed'
    manualMarks.value = next.manualMarks
    view.value = next.view
    date.value = next.date
    selectedLesson.value = next.selectedLesson
    scenario.value = next.scenario
    focusMark.value = false
    clearHomeworkFocus()
    resetEpoch.value++
  }
  onMounted(() => window.addEventListener('pageshow', restoreOnReturn))
  onBeforeUnmount(() => window.removeEventListener('pageshow', restoreOnReturn))
  function selectDate(next: string) {
    if (!isDemoDate(next) || next === date.value) return
    date.value = next
    clearHomeworkFocus()
    selectedLesson.value = next === demoDate ? lessonId : null
  }
  function selectLesson(id: string) {
    if (physicalLessons.get(id)?.date === date.value)
      selectedLesson.value = id
  }
  function navigate(next: PhoneView, focus = false) {
    view.value = next
    clearHomeworkFocus()
    focusMark.value = focus
    if (focus) {
      selectDate(demoDate)
      revision.value++
    }
  }
  function clearHomeworkFocus() {
    homeworkFocus.value = null
  }
  function openNotificationHomework(encoded: string) {
    if (roleForView(view.value) !== 'STUDENT') return false
    const url = new URL(window.location.href)
    url.search = ''
    url.searchParams.set('notificationAction', encoded)
    const { action } = consumePwaNotificationLaunch(url)
    if (action?.kind !== 'homework' || action.role !== 'STUDENT' ||
        action.homeworkId !== notificationHomeworkId || action.lessonDate !== notificationHomeworkDate)
      return false
    selectDate(action.lessonDate)
    selectLesson(physicalLessonId(action.lessonDate, 2))
    navigate('homework')
    homeworkFocus.value = { itemId: notificationHomeworkId, requestId: ++homeworkRequestId }
    return true
  }
  function mark(id: string) {
    if (id !== lessonId) return
    if (scenario.value === 'geo-unavailable') {
      scenario.value = 'pending'
      return
    }
    if (scenario.value !== 'standard') return
    if (record.value) {
      replayed.value = true
      return
    }
    record.value = { lesson: lessonId, markedAt }
    focusMark.value = false
  }
  function row(lesson: string, id: EditableMemberId): ManualRow | undefined {
    return manualMarks.value[rowKey(lesson, id)]
  }
  function markMember(lesson: string, id: EditableMemberId, value: ManualMark, expectedRevision: number) {
    const slot = physicalLessons.get(lesson)
    if (!slot || slot.date > demoDate || (slot.date === demoDate && slot.number > 2) ||
        (lesson === lessonId && scenario.value === 'cancelled')) return false
    const current = row(lesson, id)?.revision ?? 1
    if (expectedRevision !== current) return false
    manualMarks.value = { ...manualMarks.value, [rowKey(lesson, id)]: { value, revision: current + 1 } }
    return true
  }
  function chooseScenario(next: DemoSetupScenario) {
    scenario.value = next
    record.value = null
    manualMarks.value = {}
    replayed.value = false
    view.value = 'today'
    date.value = next === 'empty-day' ? emptyDate : demoDate
    selectedLesson.value = next === 'empty-day' ? null : lessonId
    focusMark.value = false
    clearHomeworkFocus()
    resetEpoch.value++
  }
  function reset() {
    chooseScenario('standard')
  }
  return { snapshot, markState, navigate, selectDate, selectLesson, chooseScenario, mark, markMember, openNotificationHomework, clearHomeworkFocus, reset }
}
