<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { MobileHostAdapter } from '../../shared/host'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import { validateHomeworkLink } from '../../domain/homework'
import { HeadmanJournalApiError, type HeadmanJournalApi, type HeadmanJournalLesson } from '../headman-journal/headman-journal-client'
import { HeadmanSubjectsApiError, type HeadmanSubjectsApi, type HeadmanSubject, type HeadmanSubjectType } from '../headman-subjects/headman-subjects-client'
import { HeadmanHomeworkApiError, type HeadmanHomeworkApi, type HeadmanManagedHomework, type HeadmanHomeworkSemester, type HeadmanHomeworkUpdateInput, type HeadmanHomeworkHistory } from './headman-homework-client'
import { reuseOrCreateAssistantHomeworkIntent, withAssistantHomeworkPublicationReceipt, intentAfterHomeworkCreateFailure, type AssistantHomeworkCreateIntent, type AssistantHomeworkDraftContext } from './assistant-homework-create-intent'
import { canReselectHomeworkCreate, homeworkWeek, homeworkMonth, shiftHomeworkDate, homeworkDateWritable, homeworkSubjectTypes, homeworkLessonEligible, homeworkLessonOrder, homeworkTypeLabels, homeworkTextSegments } from './headman-homework-state'
import { homeworkSemesterForDate, readPublishedHomeworks } from './headman-published-homework'
import { watchHeadmanHomeworkAvailability } from './headman-homework-availability'
import previousIcon from '../../assets/headman-homework-previous.svg'
import nextIcon from '../../assets/headman-homework-next.svg'
import downIcon from '../../assets/headman-homework-down.svg'
import linkIcon from '../../assets/homework-external-link.svg'
import './headman-homework-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanHomeworkApi | null
  journalApi: HeadmanJournalApi | null
  subjectsApi: HeadmanSubjectsApi | null
  groupId: number | null
  userId: number | null
  offline?: boolean
  readOnly?: boolean
  host?: MobileHostAdapter | null
  openMaterial?: ((url: string) => void) | undefined
  /** Production uses actual Moscow time; review can freeze its clock. */
  now?: () => Date
  initialCreate?: boolean
}>(), { offline: false, readOnly: false, host: null, openMaterial: undefined, now: () => new Date(), initialCreate: false })
const emit = defineEmits<{ error: [cause: unknown]; viewChange: [view: 'calendar' | 'detail' | 'wizard' | 'history'] }>()
const today = () => props.now().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' })
const view = ref<'calendar' | 'detail' | 'wizard' | 'history'>('calendar')
const step = ref(0)
const stepNames = ['Предмет', 'Тип занятия', 'Дата', 'Содержание']
const selectedDate = ref(today())
const monthDate = ref(today())
const semester = shallowRef<HeadmanHomeworkSemester | null>(null)
const semesterOptions = shallowRef<readonly HeadmanHomeworkSemester[]>([])
const subjects = shallowRef<readonly HeadmanSubject[]>([])
const homeworks = shallowRef<readonly HeadmanManagedHomework[]>([])
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([])
const loading = ref(false)
const calendarLoading = ref(false)
const nextLoading = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const denied = ref(false)
const detail = shallowRef<HeadmanManagedHomework | null>(null)
const history = shallowRef<readonly HeadmanHomeworkHistory[]>([])
const historyLoading = ref(false)
const subjectId = ref<number | null>(null)
const lessonType = ref<HeadmanSubjectType | null>(null)
const anyDate = ref(false)
const chosenDate = ref(today())
const chosenLesson = shallowRef<HeadmanJournalLesson | null>(null)
const explicitLesson = ref(false)
const nextByType = shallowRef<Partial<Record<HeadmanSubjectType, HeadmanJournalLesson | null>>>({})
const title = ref('')
const description = ref('')
const link = ref('')
const editing = shallowRef<HeadmanManagedHomework | null>(null)
const createIntent = shallowRef<AssistantHomeworkCreateIntent | null>(null)
const createEverUncertain = ref(false)
const createAttempts = ref(0)
const createCanReselect = ref(false)
const editIntent = shallowRef<Readonly<HeadmanHomeworkUpdateInput> | null>(null)
const editEverUncertain = ref(false)
const conflict = ref(false)
const conflictCurrent = shallowRef<HeadmanManagedHomework | null>(null)
const subjectListOpen = ref(true)
const typeListOpen = ref(true)
let ownerRevision = 0
let calendarRevision = 0
let nextRevision = 0
let dayRevision = 0
const dayLoading = ref(false)
let detailRevision = 0
let disposed = false
const retryLocked = computed(() => createIntent.value !== null || editIntent.value !== null)
const writeAllowed = computed(() => !props.offline && !props.readOnly && !denied.value && props.api !== null && props.groupId !== null && props.userId !== null && semester.value?.active === true)
const selectedSubject = computed(() => subjects.value.find((item) => item.id === subjectId.value) ?? null)
const availableSubjects = computed(() => subjects.value.filter((item) => semester.value && props.groupId !== null
  && item.groupId === props.groupId && item.assignments.some((assignment) => assignment.semesterId === semester.value!.id
    && (assignment.validUntilExclusive === null || assignment.validUntilExclusive > today())
    && (semester.value!.dateTo === null || assignment.validFrom <= semester.value!.dateTo))))
const availableTypes = computed(() => !selectedSubject.value || !semester.value ? []
  : [...new Set(selectedSubject.value.assignments.filter((item) => item.semesterId === semester.value!.id
    && (item.validUntilExclusive === null || item.validUntilExclusive > today())).map((item) => item.lessonType))])
const calendarDays = computed(() => view.value === 'wizard' ? homeworkMonth(monthDate.value) : homeworkWeek(selectedDate.value))
const dateItems = computed(() => homeworks.value.filter((item) => item.lessonDate === selectedDate.value))
const eligibleLessons = computed(() => lessonType.value === null || subjectId.value === null || semester.value === null || props.groupId === null ? []
  : lessons.value.filter((lesson) => homeworkLessonEligible(lesson, { groupId: props.groupId!, subjectId: subjectId.value!, type: lessonType.value!, semester: semester.value!, now: props.now() })
    && selectedSubject.value !== null && homeworkSubjectTypes(selectedSubject.value, props.groupId!, semester.value!.id, lesson.date).includes(lessonType.value!)).sort(homeworkLessonOrder))
const chosenDateLessons = computed(() => eligibleLessons.value.filter((item) => item.date === chosenDate.value))
const primaryLink = computed(() => detail.value ? validateHomeworkLink(detail.value.link) : null)
const contentValid = computed(() => title.value.trim().length > 0 && Array.from(title.value).length <= 255
  && Array.from(description.value).length <= 4000 && Array.from(link.value).length <= 2048
  && (link.value.trim() === '' || validateHomeworkLink(link.value.trim()).supported))
const originalPlacementUnchanged = computed(() => Boolean(editing.value && editing.value.bindingMode === (anyDate.value ? 'DATE' : 'LESSON')
  && editing.value.lessonDate === chosenDate.value && editing.value.lessonNumber === (anyDate.value ? null : chosenLesson.value?.lessonNumber ?? editing.value.lessonNumber)
  && (editing.value.lessonType ?? itemLesson(editing.value)?.lessonType ?? null) === lessonType.value))
const dateValid = computed(() => semester.value !== null && (!anyDate.value ? originalPlacementUnchanged.value || chosenLesson.value !== null
  && eligibleLessons.value.some((item) => item.id === chosenLesson.value?.id)
  : homeworkDateWritable(chosenDate.value, semester.value, today()) && selectedSubject.value !== null && props.groupId !== null
    && lessonType.value !== null && homeworkSubjectTypes(selectedSubject.value, props.groupId, semester.value.id, chosenDate.value).includes(lessonType.value)))
const canContinue = computed(() => writeAllowed.value && !busy.value && !nextLoading.value && !calendarLoading.value && !conflict.value
  && (retryLocked.value || (step.value === 0 ? subjectId.value !== null : step.value === 1 ? lessonType.value !== null : step.value === 2 ? dateValid.value : contentValid.value && dateValid.value)))
const actionLabel = computed(() => busy.value ? 'Сохраняем…' : retryLocked.value ? 'Повторить публикацию' : step.value < 3 ? 'Продолжить' : editing.value ? 'Сохранить изменения' : 'Опубликовать задание')
const useHostAction = computed(() => props.host?.primaryActionOwner === 'host')

function formatDate(value: string): string { const label = new Date(`${value}T12:00:00Z`).toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long', weekday: 'long' }); return label.charAt(0).toUpperCase() + label.slice(1) }
function monthLabel(value: string): string { const date = new Date(`${value}T12:00:00Z`); const month = date.toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', month: 'long' }); return `${month.charAt(0).toUpperCase()}${month.slice(1)} ${value.slice(0, 4)}` }
function shortWeekday(value: string): string { return new Date(`${value}T12:00:00Z`).toLocaleDateString('ru-RU', { weekday: 'short', timeZone: 'Europe/Moscow' }) }
function subjectName(id: number): string { return subjects.value.find((item) => item.id === id)?.name ?? 'Предмет недоступен' }
function homeworkHistoryLabel(action: string): string { return ({ CREATED: 'Задание опубликовано', UPDATED: 'Задание изменено', EDITED: 'Задание изменено', LESSON_TRANSFERRED: 'Пара перенесена', ARCHIVED: 'Задание завершено' } as Record<string, string>)[action] ?? 'Изменение задания' }
function homeworkHistoryChanges(entry: HeadmanHomeworkHistory): string {
  const record = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value)
  if (!record(entry.before) || !record(entry.after)) return ''
  const labels: Record<string, string> = { title: 'название', description: 'текст', link: 'материалы', lessonDate: 'дата', lessonNumber: 'номер пары', lessonType: 'тип занятия' }
  const fields = Object.entries(labels).filter(([field]) => entry.before && entry.after && (entry.before as Record<string, unknown>)[field] !== (entry.after as Record<string, unknown>)[field]).map(([, label]) => label)
  return fields.length ? 'Изменено: ' + fields.join(', ') : ''
}
function typeName(value: string | null | undefined): string { return value === 'LECTURE' || value === 'PRACTICE' || value === 'LAB' ? homeworkTypeLabels[value] : 'Тип занятия не указан' }
function nextSummary(type: HeadmanSubjectType): string { const next = nextByType.value[type]; return next ? `Ближайшая: ${formatDate(next.date)}${next.startTime ? `, ${next.startTime.slice(0, 5)}` : ''}` : 'Ближайшая пара не найдена · можно выбрать дату' }
function lessonLabel(lesson: HeadmanJournalLesson): string { return `Пара ${lesson.lessonNumber} · ${lesson.startTime?.slice(0, 5) ?? 'Время не указано'}${lesson.room ? ` · ${lesson.room}` : ''}` }
function itemLesson(item: HeadmanManagedHomework): HeadmanJournalLesson | null { return item.bindingMode === 'LESSON' ? lessons.value.find((entry) => entry.subjectId === item.subjectId && entry.date === item.lessonDate && entry.lessonNumber === item.lessonNumber && entry.current) ?? null : null }
function detailType(item: HeadmanManagedHomework): string { return typeName(item.lessonType ?? itemLesson(item)?.lessonType) }
function reportError(cause: unknown, revision: number): void {
  if (disposed || revision !== ownerRevision || cause instanceof StaleSessionGenerationError) return
  if ((cause instanceof HeadmanHomeworkApiError || cause instanceof HeadmanJournalApiError || cause instanceof HeadmanSubjectsApiError) && cause.response.status === 403) {
    denied.value = true; homeworks.value = []; detail.value = null; clearDraft(); view.value = 'calendar'
  }
  error.value = cause instanceof Error ? cause.message : 'Не удалось получить домашние задания.'
  emit('error', cause)
}
function clearDraft(): void {
  createIntent.value = null; editIntent.value = null; editing.value = null; conflict.value = false; conflictCurrent.value = null
  createEverUncertain.value = false; createAttempts.value = 0; createCanReselect.value = false
  editEverUncertain.value = false
  subjectId.value = null; lessonType.value = null; chosenLesson.value = null; explicitLesson.value = false; title.value = ''; description.value = ''; link.value = ''; anyDate.value = false
}
async function load(): Promise<void> {
  const revision = ++ownerRevision
  calendarRevision += 1; nextRevision += 1; dayRevision += 1; detailRevision += 1
  busy.value = false; loading.value = true; denied.value = false; error.value = null; notice.value = null
  homeworks.value = []; lessons.value = []; subjects.value = []; detail.value = null; semester.value = null; semesterOptions.value = []; clearDraft(); view.value = 'calendar'
  const api = props.api; const subjectsApi = props.subjectsApi; const groupId = props.groupId
  if (!api || !subjectsApi || groupId === null || props.userId === null || props.offline) { loading.value = false; return }
  try {
    const [active, available, semesters] = await Promise.all([api.activeSemester(), subjectsApi.listSubjects(groupId), subjectsApi.listSemesters()])
    if (disposed || revision !== ownerRevision) return
    semester.value = active; subjects.value = available.filter((item) => item.groupId === groupId)
    semesterOptions.value = semesters.map((item) => ({ ...item }))
    await loadDayHomeworks(selectedDate.value)
    await loadCalendar(selectedDate.value, false)
    if (!disposed && revision === ownerRevision && props.initialCreate) openCreate()
  } catch (cause) { reportError(cause, revision) }
  finally { if (!disposed && revision === ownerRevision) loading.value = false }
}
async function loadCalendar(value: string, month: boolean): Promise<void> {
  const api = props.journalApi; const groupId = props.groupId; const revision = ++calendarRevision; const owner = ownerRevision
  if (!api || groupId === null || props.offline) return
  calendarLoading.value = true
  const days = month ? homeworkMonth(value) : homeworkWeek(value)
  try {
    const fetched: HeadmanJournalLesson[] = []
    for (let index = 0; index < days.length; index += 7) {
      fetched.push(...await api.listLessons(groupId, days[index]!, days[Math.min(index + 6, days.length - 1)]!))
      if (disposed || owner !== ownerRevision || revision !== calendarRevision) return
    }
    lessons.value = fetched
    if (view.value === 'wizard' && !retryLocked.value && !anyDate.value && !explicitLesson.value && !editing.value && eligibleLessons.value.filter((item) => item.date === chosenDate.value).length > 1) chosenLesson.value = null
  } catch (cause) { if (revision === calendarRevision) reportError(cause, owner) }
  finally { if (!disposed && revision === calendarRevision && owner === ownerRevision) calendarLoading.value = false }
}
function selectRootDate(value: string): void { selectedDate.value = value; void loadCalendar(value, false); void loadDayHomeworks(value) }
function shiftCalendar(delta: number): void {
  if (view.value === 'wizard') { const date = new Date(`${monthDate.value.slice(0, 7)}-01T12:00:00Z`); date.setUTCMonth(date.getUTCMonth() + delta); monthDate.value = date.toISOString().slice(0, 10); void loadCalendar(monthDate.value, true) }
  else selectRootDate(shiftHomeworkDate(selectedDate.value, delta * 7))
}
async function loadDayHomeworks(date: string): Promise<void> {
  const api = props.api; const group = props.groupId; const owner = ownerRevision; const run = ++dayRevision
  const readSemester = homeworkSemesterForDate(date, semesterOptions.value)
  homeworks.value = []; dayLoading.value = false
  if (!api || group === null || !readSemester || props.offline || denied.value) return
  dayLoading.value = true
  const current = () => !disposed && run === dayRevision && owner === ownerRevision && api === props.api && group === props.groupId && date === selectedDate.value && !props.offline && !denied.value
  try {
    const items = await readPublishedHomeworks(api, group, readSemester.id, current)
    if (current() && items !== null) homeworks.value = items
  } catch (cause) { if (current()) reportError(cause, owner) }
  finally { if (!disposed && run === dayRevision && owner === ownerRevision) dayLoading.value = false }
}
async function openDetail(item: HeadmanManagedHomework): Promise<void> {
  detail.value = item; view.value = 'detail'; history.value = []
  await loadCalendar(item.lessonDate, false)
}
async function showHistory(): Promise<void> {
  const api = props.api; const item = detail.value; const owner = ownerRevision; const revision = ++detailRevision
  if (!api || !item || props.offline || denied.value) return
  view.value = 'history'; historyLoading.value = true; history.value = []
  try { const rows = await api.history(item.id); if (!disposed && owner === ownerRevision && revision === detailRevision) history.value = rows }
  catch (cause) { if (revision === detailRevision) reportError(cause, owner) }
  finally { if (!disposed && owner === ownerRevision && revision === detailRevision) historyLoading.value = false }
}
function openCreate(): void {
  if (!writeAllowed.value || retryLocked.value || busy.value) return
  clearDraft(); step.value = 0; view.value = 'wizard'; chosenDate.value = today(); monthDate.value = today(); subjectListOpen.value = true; typeListOpen.value = true; notice.value = null
}
async function openEdit(): Promise<void> {
  const item = detail.value
  if (!item || item.semesterId !== semester.value?.id || !writeAllowed.value || item.archived || item.revision === null) return
  clearDraft(); editing.value = item; subjectId.value = item.subjectId
  const boundType = itemLesson(item)?.lessonType
  lessonType.value = item.lessonType ?? (boundType === 'LECTURE' || boundType === 'PRACTICE' || boundType === 'LAB' ? boundType : null)
  anyDate.value = item.bindingMode === 'DATE'; chosenDate.value = item.lessonDate; monthDate.value = item.lessonDate; chosenLesson.value = itemLesson(item)
  title.value = item.title; description.value = item.description ?? ''; link.value = item.link ?? ''; view.value = 'wizard'; step.value = 0
  await loadCalendar(monthDate.value, true)
}
function selectSubject(id: number): void {
  if (editing.value || retryLocked.value || busy.value) return
  if (subjectId.value !== id) { subjectId.value = id; lessonType.value = null; chosenLesson.value = null; chosenDate.value = today(); anyDate.value = false; nextByType.value = {} }
  subjectListOpen.value = false
}
async function fetchNextTypes(): Promise<void> {
  const api = props.api; const group = props.groupId; const active = semester.value; const subject = subjectId.value; const revision = ++nextRevision; const owner = ownerRevision
  if (!api || group === null || !active || subject === null) return
  nextLoading.value = true
  try {
    const entries = await Promise.all(availableTypes.value.map(async (type) => [type, await api.nextHomeworkLesson(group, active.id, subject, type, today())] as const))
    if (disposed || revision !== nextRevision || owner !== ownerRevision || subjectId.value !== subject) return
    nextByType.value = Object.fromEntries(entries)
  } catch (cause) { if (revision === nextRevision) reportError(cause, owner) }
  finally { if (!disposed && revision === nextRevision && owner === ownerRevision) nextLoading.value = false }
}
async function selectType(type: HeadmanSubjectType): Promise<void> {
  if (retryLocked.value || busy.value) return
  if (lessonType.value !== type) {
    lessonType.value = type; anyDate.value = false; explicitLesson.value = false; const next = nextByType.value[type] ?? null
    chosenLesson.value = next; chosenDate.value = next?.date ?? today(); monthDate.value = chosenDate.value
    await loadCalendar(monthDate.value, true)
  }
  typeListOpen.value = false
}
function selectWizardDate(value: string): void {
  if (retryLocked.value || busy.value || !semester.value) return
  if (anyDate.value) { if (homeworkDateWritable(value, semester.value, today())) chosenDate.value = value; return }
  const choices = eligibleLessons.value.filter((item) => item.date === value)
  if (!choices.length) return
  chosenDate.value = value; explicitLesson.value = choices.length === 1; chosenLesson.value = choices.length === 1 ? choices[0]! : null
}
function toggleAnyDate(): void {
  if (retryLocked.value || busy.value) return
  anyDate.value = !anyDate.value
  if (anyDate.value) { chosenLesson.value = null; if (!homeworkDateWritable(chosenDate.value, semester.value, today())) chosenDate.value = today() }
  else { const next = nextByType.value[lessonType.value!] ?? null; chosenDate.value = next?.date ?? today(); chosenLesson.value = next; monthDate.value = chosenDate.value; void loadCalendar(monthDate.value, true) }
}
function dayDisabled(date: string): boolean { return anyDate.value ? !homeworkDateWritable(date, semester.value, today()) : !eligibleLessons.value.some((item) => item.date === date) && !(originalPlacementUnchanged.value && date === chosenDate.value) }
function draftContext(): AssistantHomeworkDraftContext | null {
  if (props.userId === null || props.groupId === null || !semester.value || subjectId.value === null || lessonType.value === null) return null
  if (!anyDate.value && !chosenLesson.value && !originalPlacementUnchanged.value) return null
  return { userId: props.userId, groupId: props.groupId, semesterId: semester.value.id, selectedDate: chosenDate.value, lessonDate: chosenDate.value, subjectId: subjectId.value,
    lessonId: anyDate.value ? null : chosenLesson.value?.id ?? null, lessonNumber: anyDate.value ? null : chosenLesson.value?.lessonNumber ?? editing.value?.lessonNumber ?? null,
    bindingMode: anyDate.value ? 'DATE' : 'LESSON', lessonType: lessonType.value,
    ...(!anyDate.value && chosenLesson.value?.occurrenceId && chosenLesson.value.occurrenceRevision ? { selectedOccurrenceId: chosenLesson.value.occurrenceId, selectedOccurrenceRevision: chosenLesson.value.occurrenceRevision } : {}) }
}
async function primaryAction(): Promise<void> {
  if (!canContinue.value) return
  if (step.value < 3) { step.value += 1; if (step.value === 1) await fetchNextTypes(); if (step.value === 2) await loadCalendar(monthDate.value, true); return }
  const api = props.api; const context = draftContext(); const owner = ownerRevision; const item = editing.value
  if (!api || !context || (!retryLocked.value && (!dateValid.value || !contentValid.value))) return
  if (item && item.revision === null) return
  const values = { title: title.value, description: description.value, link: link.value }
  if (item) {
    if (!editIntent.value) editIntent.value = Object.freeze({ ...values, requestKey: crypto.randomUUID(), expectedRevision: item.revision!,
      ...(originalPlacementUnchanged.value ? {} : { bindingMode: context.bindingMode, lessonDate: context.lessonDate, lessonNumber: context.lessonNumber,
        lessonType: context.lessonType, ...(context.selectedOccurrenceId === undefined ? {} : { selectedOccurrenceId: context.selectedOccurrenceId, selectedOccurrenceRevision: context.selectedOccurrenceRevision }) }) })
  } else {
    if (createIntent.value === null) { createEverUncertain.value = false; createAttempts.value = 0; createCanReselect.value = false }
    createIntent.value = reuseOrCreateAssistantHomeworkIntent(createIntent.value, context, values, () => crypto.randomUUID())
    createAttempts.value += 1
  }
  busy.value = true; error.value = null; notice.value = null
  try {
    if (item) {
      await api.updateHomework(item.id, editIntent.value!)
      if (disposed || owner !== ownerRevision) return
    } else {
      const result = await api.createHomework(createIntent.value!.input, createIntent.value!.receipt)
      if (disposed || owner !== ownerRevision) return
      if (result.state === 'PENDING') { createEverUncertain.value = true; createIntent.value = withAssistantHomeworkPublicationReceipt(createIntent.value!, result.receipt); notice.value = 'Публикация выполняется. Повтор проверит результат без нового задания.'; return }
    }
    const items = await api.listHomeworks(props.groupId!, semester.value!.id)
    if (disposed || owner !== ownerRevision) return
    homeworks.value = items; selectedDate.value = chosenDate.value; clearDraft(); view.value = 'calendar'
    notice.value = item ? 'Изменения сохранены.' : 'Задание опубликовано.'; await loadCalendar(selectedDate.value, false); await loadDayHomeworks(selectedDate.value)
  } catch (cause) {
    if (disposed || owner !== ownerRevision || cause instanceof StaleSessionGenerationError) return
    const status = cause instanceof HeadmanHomeworkApiError ? cause.response.status : null
    if (status === null || status >= 500) {
      if (item) editEverUncertain.value = true
      else createEverUncertain.value = true
    }
    if (status === 409) {
      if (!item && cause instanceof HeadmanHomeworkApiError) createCanReselect.value = canReselectHomeworkCreate(cause.problem, { attempts: createAttempts.value, everUncertain: createEverUncertain.value, hasReceipt: createIntent.value?.receipt != null })
      conflict.value = true; notice.value = 'Данные изменились. Проверь текущую версию и подтверди свой вариант ещё раз.'
      if (item && !editEverUncertain.value) { try { const current = await api.getHomework(item.id); if (!disposed && owner === ownerRevision) conflictCurrent.value = current } catch (readCause) { reportError(readCause, owner) } }
    } else if (!item && !createEverUncertain.value && createIntent.value?.receipt == null) createIntent.value = intentAfterHomeworkCreateFailure(createIntent.value, status)
    reportError(cause, owner)
  } finally { if (!disposed && owner === ownerRevision) busy.value = false }
}
async function resolveConflict(): Promise<void> {
  if (!conflict.value || busy.value || !writeAllowed.value) return
  if (editing.value) {
    if (editEverUncertain.value) {
      conflict.value = false; notice.value = 'Повтор проверит исходное изменение без нового запроса.'; return
    }
    const current = conflictCurrent.value
    if (!current || current.archived || current.revision === null) return
    editing.value = current; editIntent.value = null
  } else if (createCanReselect.value) {
    // The first definitive typed rejection permits a fresh user-confirmed placement.
    createIntent.value = null; createAttempts.value = 0; createCanReselect.value = false
  } else {
    // A create may be durably accepted; every retry keeps its original identity.
    conflict.value = false; notice.value = 'Повтор проверит исходную публикацию.'; return
  }
  conflict.value = false; conflictCurrent.value = null; error.value = null; chosenLesson.value = null; step.value = 2
  await fetchNextTypes(); await loadCalendar(monthDate.value, true)
  notice.value = 'Твой текст сохранён. Выбери актуальную пару или дату и подтверди изменения.'
}
function back(): boolean {
  if (view.value === 'calendar') return false
  if (busy.value || retryLocked.value) { notice.value = busy.value ? 'Дождись ответа сервера.' : 'Результат сохранения ещё не подтверждён. Повтори запрос.'; return true }
  if (view.value === 'wizard' && step.value > 0) step.value -= 1
  else if (view.value === 'history') { view.value = 'detail'; detailRevision += 1; historyLoading.value = false }
  else if (view.value === 'detail') { view.value = 'calendar'; detailRevision += 1; detail.value = null }
  else { view.value = 'calendar'; clearDraft() }
  return true
}
function openUrl(url: string | null): void { const result = validateHomeworkLink(url); if (!props.offline && result.supported) props.openMaterial?.(result.original) }
watch(view, (value) => emit('viewChange', value), { immediate: true })
watch(() => [props.api, props.journalApi, props.subjectsApi, props.groupId, props.userId, props.readOnly] as const, () => { void load() }, { immediate: true, flush: 'sync' })
watchHeadmanHomeworkAvailability({
  offline: () => props.offline,
  hasContext: () => semesterOptions.value.length > 0,
  mutationLocked: () => retryLocked.value,
  invalidateReads: () => { dayRevision += 1; calendarRevision += 1; dayLoading.value = false; calendarLoading.value = false },
  loadContext: () => { void load() },
  refreshReads: () => {
    void loadDayHomeworks(selectedDate.value)
    void loadCalendar(view.value === 'wizard' ? monthDate.value : selectedDate.value, view.value === 'wizard')
  },
})

watch(() => [props.host, view.value, actionLabel.value, canContinue.value] as const, ([host, currentView, label, enabled], previous) => {
  if (previous?.[0] && previous[0] !== host) previous[0].setPrimaryAction?.(null)
  if (host?.primaryActionOwner === 'host') host.setPrimaryAction?.(currentView === 'wizard' ? { label, disabled: !enabled, onInvoke: primaryAction } : null)
}, { immediate: true })
onBeforeUnmount(() => { disposed = true; ownerRevision += 1; calendarRevision += 1; nextRevision += 1; dayRevision += 1; detailRevision += 1; props.host?.setPrimaryAction?.(null) })
defineExpose({ back, openCreate, selectRootDate })
</script>

<template>
  <main
    class="headman-homework"
    :data-view="view"
    :aria-busy="loading || busy"
    aria-labelledby="headman-homework-title"
  >
    <header
      class="headman-homework__header"
      :class="{ 'headman-homework__header--task': view !== 'calendar' }"
    >
      <h1 id="headman-homework-title">
        {{ view === 'history' ? 'История изменений' : view === 'wizard' ? editing ? 'Изменить задание' : 'Новое задание' : 'Домашнее задание' }}
      </h1>
    </header>
    <StudentWarningBlock
      v-if="offline"
      title="Ты офлайн"
      message="Управление заданиями доступно онлайн. Подключись к интернету и открой раздел заново."
    />
    <StudentWarningBlock
      v-else-if="denied || !api || !subjectsApi || groupId === null || userId === null"
      title="Раздел недоступен"
      message="Сейчас у тебя нет доступа к заданиям группы."
    />
    <StudentWarningBlock
      v-else-if="error"
      severity="error"
      title="Не удалось завершить действие"
      :message="error"
      v-bind="view === 'calendar' ? { actionLabel: 'Повторить' } : {}"
      @action="load"
    />
    <p
      v-if="notice"
      class="headman-homework__notice"
      role="status"
    >
      {{ notice }}
    </p>
    <p
      v-if="loading"
      class="headman-homework__state"
      role="status"
    >
      Загружаем задания…
    </p>
    <template v-else-if="!denied && !offline && api && subjectsApi && groupId !== null && userId !== null">
      <template v-if="view === 'calendar'">
        <p
          v-if="dayLoading"
          role="status"
        >
          Загружаем задания выбранного дня…
        </p>
        <StudentWarningBlock
          v-if="!semester"
          title="Нет активного семестра"
          message="Выдать новое задание можно в активном семестре. Предыдущие задания доступны по выбранной дате в календаре."
        />
        <section
          v-if="semesterOptions.length > 0"
          class="headman-homework__calendar"
          aria-label="Неделя домашних заданий"
        >
          <div class="headman-homework__calendar-heading">
            <h2>{{ monthLabel(selectedDate) }}</h2>
            <div>
              <button
                type="button"
                aria-label="Предыдущая неделя"
                @click="shiftCalendar(-1)"
              >
                <img
                  :src="previousIcon"
                  alt=""
                >
              </button><button
                type="button"
                aria-label="Следующая неделя"
                @click="shiftCalendar(1)"
              >
                <img
                  :src="nextIcon"
                  alt=""
                >
              </button>
            </div>
          </div>
          <div class="headman-homework__week">
            <button
              v-for="date in calendarDays"
              :key="date"
              type="button"
              :aria-pressed="selectedDate === date"
              :aria-current="date === today() ? 'date' : undefined"
              :aria-label="formatDate(date)"
              @click="selectRootDate(date)"
            >
              <span>{{ shortWeekday(date) }}</span><strong>{{ Number(date.slice(8)) }}</strong>
            </button>
          </div>
        </section>
        <h2
          v-if="semesterOptions.length > 0"
          class="headman-homework__day-title"
        >
          {{ formatDate(selectedDate) }}
        </h2>
        <button
          v-if="semester"
          class="headman-homework__primary headman-homework__create"
          type="button"
          aria-label="Выдать ДЗ"
          :disabled="!writeAllowed"
          @click="openCreate"
        >
          <span aria-hidden="true">＋</span>Выдать ДЗ
        </button>
        <p
          v-if="semesterOptions.length > 0 && !dayLoading && !error && dateItems.length === 0"
          class="headman-homework__state"
        >
          На этот день заданий нет.
        </p>
        <button
          v-for="item in dateItems"
          :key="item.id"
          class="headman-homework__item"
          :data-homework-id="item.id"
          type="button"
          @click="openDetail(item)"
        >
          <span><strong>{{ subjectName(item.subjectId) }}</strong><span>{{ item.title }}</span></span><img
            :src="nextIcon"
            alt=""
          >
        </button>
      </template>
      <template v-else-if="view === 'detail' && detail">
        <section class="headman-homework__context">
          <p>{{ formatDate(detail.lessonDate) }}</p><h2>{{ subjectName(detail.subjectId) }}</h2><p>
            {{ detailType(detail) }}<template v-if="itemLesson(detail)">
              · {{ itemLesson(detail)?.startTime?.slice(0, 5) }}<template v-if="itemLesson(detail)?.room">
                · {{ itemLesson(detail)?.room }}
              </template>
            </template><template v-else-if="detail.bindingMode === 'DATE'">
              · На дату
            </template>
          </p>
        </section>
        <article class="headman-homework__detail">
          <h2>{{ detail.title }}</h2>
          <p class="headman-homework__full-text">
            <template
              v-for="(segment, index) in homeworkTextSegments(detail.description ?? '')"
              :key="index"
            >
              <button
                v-if="segment.url && openMaterial"
                class="headman-homework__text-link"
                type="button"
                @click="openUrl(segment.url)"
              >
                {{ segment.text }}
              </button><template v-else>
                {{ segment.text }}
              </template>
            </template>
          </p>
          <div
            v-if="detail.link"
            class="headman-homework__material"
          >
            <button
              v-if="primaryLink?.supported && openMaterial"
              type="button"
              @click="openUrl(detail.link)"
            >
              <img
                :src="linkIcon"
                alt=""
              >Материалы
            </button><p v-else>
              Материал сейчас недоступен.
            </p>
          </div>
        </article>
        <button
          v-if="writeAllowed && detail.semesterId === semester?.id && !detail.archived && detail.revision !== null"
          class="headman-homework__primary"
          type="button"
          @click="openEdit"
        >
          Изменить задание
        </button>
        <div class="headman-homework__detail-tools">
          <p
            v-if="detail.archived"
            class="headman-homework__muted"
          >
            Архив · только просмотр
          </p>
          <button
            class="headman-homework__secondary"
            type="button"
            :disabled="historyLoading"
            @click="showHistory"
          >
            История изменений
          </button>
        </div>
      </template>
      <template v-else-if="view === 'history'">
        <section
          v-if="detail"
          class="headman-homework__context"
        >
          <h2>{{ detail.title }}</h2><p>{{ subjectName(detail.subjectId) }}</p>
        </section>
        <section
          v-if="detail"
          aria-label="История изменений"
        >
          <p
            v-if="historyLoading"
            role="status"
          >
            Загружаем историю…
          </p><p v-else-if="history.length === 0">
            История изменений пуста.
          </p><ol
            v-else
            class="headman-homework__history"
          >
            <li
              v-for="entry in history"
              :key="entry.id"
            >
              {{ new Date(entry.occurredAt).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' }) }} · {{ homeworkHistoryLabel(entry.action) }}<p>
                {{ entry.actorDisplayName?.trim() || 'Автор не указан' }}
              </p><p v-if="homeworkHistoryChanges(entry)">
                {{ homeworkHistoryChanges(entry) }}
              </p>
            </li>
          </ol>
        </section>
      </template>
      <template v-else-if="view === 'wizard'">
        <div class="headman-homework__progress">
          <p>Шаг {{ step + 1 }} из 4 · {{ stepNames[step] }}</p><progress
            :value="step + 1"
            max="4"
            :aria-label="`Шаг ${step + 1} из 4`"
          />
        </div>
        <template v-if="step < 2">
          <button
            class="headman-homework__picker"
            type="button"
            :disabled="!!editing || retryLocked"
            :aria-expanded="subjectListOpen"
            @click="subjectListOpen = !subjectListOpen"
          >
            <span><small>Предмет</small>{{ selectedSubject?.name ?? 'Выбери предмет' }}</span><img
              :src="downIcon"
              alt=""
              :class="{ 'headman-homework__up': subjectListOpen }"
            >
          </button>
          <p
            v-if="editing"
            class="headman-homework__muted"
          >
            Предмет задания сохраняется.
          </p>
          <div
            v-if="subjectListOpen && !editing"
            class="headman-homework__choices"
            aria-label="Предметы группы"
          >
            <button
              v-for="item in availableSubjects"
              :key="item.id"
              type="button"
              :aria-pressed="subjectId === item.id"
              @click="selectSubject(item.id)"
            >
              {{ item.name }}
            </button><p v-if="!availableSubjects.length">
              Нет предметов с действующими назначениями.
            </p>
          </div>
          <template v-if="step === 1">
            <button
              class="headman-homework__picker"
              type="button"
              :disabled="retryLocked"
              :aria-expanded="typeListOpen"
              @click="typeListOpen = !typeListOpen"
            >
              <span><small>Тип занятия</small>{{ lessonType ? typeName(lessonType) : 'Выбери тип' }}</span><img
                :src="downIcon"
                alt=""
                :class="{ 'headman-homework__up': typeListOpen }"
              >
            </button>
            <div
              v-if="typeListOpen"
              class="headman-homework__choices"
              aria-label="Тип занятия"
            >
              <p
                v-if="nextLoading"
                role="status"
              >
                Ищем ближайшие пары…
              </p><button
                v-for="type in availableTypes"
                :key="type"
                type="button"
                :aria-pressed="lessonType === type"
                :disabled="nextLoading"
                @click="selectType(type)"
              >
                <span>{{ typeName(type) }}</span><small>{{ nextSummary(type) }}</small>
              </button>
            </div>
          </template>
        </template>
        <template v-else>
          <section
            class="headman-homework__context"
            :class="{ 'headman-homework__context--chosen': step === 3 }"
          >
            <h2>{{ selectedSubject?.name }} · {{ typeName(lessonType) }}</h2><p>{{ step === 3 ? formatDate(chosenDate) : anyDate ? 'Свободная дата включена' : chosenLesson ? `Ближайшая: ${formatDate(chosenLesson.date)}, ${chosenLesson.startTime?.slice(0, 5) ?? 'время не указано'}` : 'Выбери актуальную пару или разреши любую дату' }}</p>
          </section>
          <template v-if="step === 2">
            <section
              class="headman-homework__calendar"
              aria-label="Дата задания"
            >
              <div class="headman-homework__calendar-heading">
                <h2>{{ monthLabel(monthDate) }}</h2><div>
                  <button
                    type="button"
                    aria-label="Предыдущий месяц"
                    :disabled="retryLocked"
                    @click="shiftCalendar(-1)"
                  >
                    <img
                      :src="previousIcon"
                      alt=""
                    >
                  </button><button
                    type="button"
                    aria-label="Следующий месяц"
                    :disabled="retryLocked"
                    @click="shiftCalendar(1)"
                  >
                    <img
                      :src="nextIcon"
                      alt=""
                    >
                  </button>
                </div>
              </div>
              <div class="headman-homework__month-weekdays">
                <span
                  v-for="day in ['Пн','Вт','Ср','Чт','Пт','Сб','Вс']"
                  :key="day"
                >{{ day }}</span>
              </div>
              <div
                class="headman-homework__month"
                :data-any-date="anyDate"
              >
                <button
                  v-for="date in calendarDays"
                  :key="date"
                  type="button"
                  :class="{ 'headman-homework__other-month': date.slice(0, 7) !== monthDate.slice(0, 7) }"
                  :aria-label="formatDate(date)"
                  :aria-pressed="chosenDate === date"
                  :disabled="retryLocked || calendarLoading || dayDisabled(date)"
                  @click="selectWizardDate(date)"
                >
                  {{ Number(date.slice(8)) }}
                </button>
              </div>
              <p
                v-if="calendarLoading"
                role="status"
              >
                Загружаем актуальные пары…
              </p>
            </section>
            <div
              v-if="!anyDate && chosenDateLessons.length > 1"
              class="headman-homework__choices"
              aria-label="Выбери пару на эту дату"
            >
              <button
                v-for="lesson in chosenDateLessons"
                :key="lesson.id"
                type="button"
                :aria-pressed="chosenLesson?.id === lesson.id"
                @click="chosenLesson = lesson; explicitLesson = true"
              >
                {{ lessonLabel(lesson) }}
              </button>
            </div>
            <button
              class="headman-homework__any-date"
              type="button"
              role="switch"
              :aria-checked="anyDate"
              :disabled="retryLocked"
              @click="toggleAnyDate"
            >
              <span>Разрешить любую пару</span><span
                class="headman-homework__switch"
                aria-hidden="true"
              />
            </button>
          </template>
          <form
            v-else
            class="headman-homework__content"
            @submit.prevent="primaryAction"
          >
            <label class="headman-homework__field"><span>Название</span><input
              v-model="title"
              maxlength="255"
              required
              :disabled="busy || retryLocked"
              aria-describedby="headman-homework-validation"
            ></label>
            <label class="headman-homework__field headman-homework__field--text"><span>Полный текст</span><textarea
              v-model="description"
              maxlength="4000"
              :disabled="busy || retryLocked"
              aria-describedby="headman-homework-validation"
            /></label>
            <label class="headman-homework__field headman-homework__field--link"><span>Основная ссылка</span><input
              v-model="link"
              type="url"
              maxlength="2048"
              :disabled="busy || retryLocked"
              aria-describedby="headman-homework-validation"
              placeholder="https://"
            ></label>
            <p
              v-if="!contentValid"
              id="headman-homework-validation"
              class="headman-homework__muted"
            >
              Укажи название и проверь длину полей и ссылку.
            </p>
          </form>
        </template>
        <StudentWarningBlock
          v-if="conflict"
          title="Задание изменилось"
          :message="conflictCurrent ? `Текущая версия: ${conflictCurrent.title}. Твой текст сохранён.` : 'Исходный запрос не подтвердился. Проверь результат, прежде чем продолжать.'"
          :action-label="editing ? editEverUncertain ? 'Проверить исходное изменение' : 'Использовать текущую версию и проверить мой вариант' : createCanReselect ? 'Выбрать актуальную пару' : 'Проверить исходную публикацию'"
          @action="resolveConflict"
        />
        <div
          v-if="!useHostAction"
          class="headman-homework__footer"
        >
          <button
            class="headman-homework__primary"
            type="button"
            :disabled="!canContinue"
            @click="primaryAction"
          >
            {{ actionLabel }}
          </button>
        </div>
      </template>
    </template>
  </main>
</template>
