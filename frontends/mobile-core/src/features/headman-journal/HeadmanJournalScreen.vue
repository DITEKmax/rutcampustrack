<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { moscowDate } from '../../domain/homework'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import { openRequestAttachmentPopup, runRequestAttachmentOpen, type RequestAttachmentPopup } from '../requests/request-attachment-action'
import type { RequestAttachmentViewState } from '../requests/types'
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalAttendanceStatus,
  type HeadmanJournalExcuseType,
  type HeadmanJournalLesson,
  type HeadmanJournalReport,
  type HeadmanJournalRosterEntry,
  type HeadmanWeeklyExportOptions,
  toHeadmanWeeklyReportRequest,
} from './headman-journal-client'
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client'
import './headman-journal-screen.pcss'

const props = withDefaults(defineProps<{
  api: HeadmanJournalApi | null
  groupId: number | null
  assistantPermissions?: readonly HeadmanAssistantPermission[] | null
  offline?: boolean
  readOnly?: boolean
  reportDownload?: ReportDownloadPort | null
  initialDate?: string
  initialLessonId?: number | null
}>(), {
  assistantPermissions: null,
  offline: false,
  readOnly: false,
  reportDownload: null,
  initialDate: '',
  initialLessonId: null,
})

const emit = defineEmits<{
  error: [cause: unknown]
}>()

const todayIso = (): string => {
  const value = moscowDate(new Date().toISOString())
  if (value === null) throw new Error('Не удалось определить дату журнала.')
  return value
}
const selectedDate = ref(props.initialDate || todayIso())
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([])
const report = shallowRef<HeadmanJournalReport | null>(null)
const selectedLessonId = ref<number | null>(null)
const loadingLessons = ref(false)
const loadingReport = ref(false)
const error = ref<string | null>(null)
const notice = ref<string | null>(null)
const pendingUserId = ref<number | null>(null)
const rowErrors = ref<Record<number, string>>({})
const expandedUserIds = ref<ReadonlySet<number>>(new Set())
const excuseUserId = ref<number | null>(null)
const excuseType = ref<HeadmanJournalExcuseType>('OTHER')
const excuseComment = ref('')
const excuseFile = ref<File | null>(null)
const cancelReason = ref('')
const lessonActionBusy = ref(false)
type LessonActionConfirmation = {
  action: 'cancel' | 'restore'
  api: HeadmanJournalApi
  groupId: number | null
  lessonId: number
  lessonDate: string
  lessonNumber: number | null
  permissions: readonly HeadmanAssistantPermission[] | null
  reason: string | null
}
const lessonActionConfirmationDialog = ref<HTMLDialogElement | null>(null)
const pendingLessonActionConfirmation = shallowRef<LessonActionConfirmation | null>(null)
const attachmentStates = ref<Readonly<Record<number, RequestAttachmentViewState>>>({})
const attachmentOwnerIdentity = ref<string | null>(props.api ? 'headman-journal' : null)
const attachmentOwnerGeneration = ref(0)
const weeklyExportOptions = shallowRef<HeadmanWeeklyExportOptions | null>(null)
const weeklyExportFormatCode = ref('')
const selectedWeeklyStarts = ref<readonly string[]>([])
const loadingWeeklyOptions = ref(false)
const weeklyExportBusy = ref(false)
const weeklyExportError = ref<string | null>(null)
const weeklyExportStatus = ref<string | null>(null)
let lessonsRevision = 0
let reportRevision = 0
let lessonActionRevision = 0
let weeklyOptionsRevision = 0
let weeklyExportRevision = 0
let weeklyExportAbort: AbortController | null = null
let disposed = false
let attachmentDisposed = false
const attachmentPopups = new Set<RequestAttachmentPopup>()
const attachmentObjectUrls = new Set<string>()

const excuseTypes = [
  { value: 'ILLNESS', label: 'Болезнь' },
  { value: 'MEDICAL_EXAMINATION', label: 'Медицинское обследование' },
  { value: 'COMPETITION_PARTICIPATION', label: 'Участие в соревновании' },
  { value: 'FAMILY_CIRCUMSTANCES', label: 'Семейные обстоятельства' },
  { value: 'SUMMONS', label: 'Повестка' },
  { value: 'UNIVERSITY_ORDER', label: 'Распоряжение университета' },
  { value: 'EXEMPTION', label: 'Освобождение' },
  { value: 'OTHER', label: 'Другая причина' },
] as const satisfies readonly { value: HeadmanJournalExcuseType; label: string }[]

const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const entries = computed(() => report.value?.entries ?? [])
const busy = computed(() => loadingLessons.value || loadingReport.value || loadingWeeklyOptions.value || weeklyExportBusy.value)
const selectedWeeklyFormat = computed(() => weeklyExportOptions.value?.formats
  .find((format) => format.code === weeklyExportFormatCode.value) ?? null)
const selectedWeeklyCount = computed(() => selectedWeeklyStarts.value.length)

function formatDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', weekday: 'long' }).format(date)
}

function formatShortDate(value: string): string {
  const date = new Date(`${value}T12:00:00Z`)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short' }).format(date)
}

function formatTime(value: string | null): string {
  return value?.slice(0, 5) ?? '—'
}

function statusLabel(status: HeadmanJournalAttendanceStatus): string {
  switch (status) {
    case 'PRESENT': return 'Был'
    case 'ABSENT': return 'Не был'
    case 'EXCUSED': return 'Уважительная причина'
    case 'CANCELLED': return 'Пара отменена'
    case 'EMPTY': return 'Нет отметки'
    case 'UNSUPPORTED': return 'Неподдерживаемый статус'
  }
}

function statusSymbol(status: HeadmanJournalAttendanceStatus): string {
  switch (status) {
    case 'PRESENT': return '+'
    case 'ABSENT': return 'н'
    case 'EXCUSED': return 'у'
    case 'CANCELLED': return '—'
    case 'EMPTY': return '·'
    case 'UNSUPPORTED': return '?'
  }
}

function lessonStatusLabel(status: HeadmanJournalLesson['status']): string {
  switch (status) {
    case 'ACTIVE': return 'идёт'
    case 'CLOSED': return 'закрыта'
    case 'CANCELLED': return 'отменена'
    case 'PLANNED': return 'запланирована'
    case 'UNSUPPORTED': return 'неподдерживаемый статус'
  }
}

function shiftDate(delta: number): void {
  const date = new Date(`${selectedDate.value}T12:00:00Z`)
  date.setUTCDate(date.getUTCDate() + delta)
  selectedDate.value = date.toISOString().slice(0, 10)
}

function setToday(): void {
  selectedDate.value = todayIso()
}

function selectLesson(lessonId: number): void {
  if (selectedLessonId.value === lessonId && report.value?.lessonId === lessonId) return
  selectedLessonId.value = lessonId
  if (canViewReport()) void loadReport(lessonId)
  else report.value = null
}

function clearRowError(userId: number): void {
  if (!(userId in rowErrors.value)) return
  const next = { ...rowErrors.value }
  delete next[userId]
  rowErrors.value = next
}

function setRowError(userId: number, message: string): void {
  rowErrors.value = { ...rowErrors.value, [userId]: message }
}

function isExpanded(userId: number): boolean {
  return expandedUserIds.value.has(userId)
}

function toggleExpanded(userId: number): void {
  const next = new Set(expandedUserIds.value)
  if (next.has(userId)) next.delete(userId)
  else next.add(userId)
  expandedUserIds.value = next
}

function hasAssistantPermission(permission: HeadmanAssistantPermission): boolean {
  return props.assistantPermissions === null || props.assistantPermissions.includes(permission)
}

function canViewReport(): boolean {
  return hasAssistantPermission('MARK_ATTENDANCE') || hasAssistantPermission('VIEW_STATS')
}

function canExportWeekly(): boolean {
  return Boolean(props.api && props.groupId !== null && !props.offline
    && (props.assistantPermissions === null || props.assistantPermissions.includes('VIEW_STATS')))
}

function weekForDate(options: HeadmanWeeklyExportOptions, date: string) {
  return options.weeks.find((week) => date >= week.weekStart && date <= week.weekEnd) ?? null
}

function resetWeeklySelectionForDate(): void {
  const options = weeklyExportOptions.value
  if (!options) {
    selectedWeeklyStarts.value = []
    return
  }
  const selected = weekForDate(options, selectedDate.value)
  selectedWeeklyStarts.value = selected ? [selected.weekStart] : []
}

function toggleWeeklyWeek(weekStart: string, checked: boolean): void {
  const options = weeklyExportOptions.value
  if (!options?.weeks.some((week) => week.weekStart === weekStart)) return
  const next = new Set(selectedWeeklyStarts.value)
  if (checked) next.add(weekStart)
  else next.delete(weekStart)
  selectedWeeklyStarts.value = options.weeks
    .filter((week) => next.has(week.weekStart))
    .map((week) => week.weekStart)
  invalidateWeeklyExport('Недели изменились. Запусти скачивание для нового выбора.')
}

function invalidateWeeklyExport(message?: string): void {
  weeklyExportRevision += 1
  weeklyExportAbort?.abort()
  weeklyExportAbort = null
  weeklyExportBusy.value = false
  weeklyExportError.value = null
  weeklyExportStatus.value = null
  if (message) weeklyExportStatus.value = message
}

async function loadWeeklyExportOptions(): Promise<void> {
  const revision = ++weeklyOptionsRevision
  invalidateWeeklyExport()
  weeklyExportOptions.value = null
  weeklyExportFormatCode.value = ''
  selectedWeeklyStarts.value = []
  if (!canExportWeekly()) {
    loadingWeeklyOptions.value = false
    return
  }
  const api = props.api
  loadingWeeklyOptions.value = true
  weeklyExportError.value = null
  try {
    const options = await api!.getWeeklyExportOptions()
    if (disposed || revision !== weeklyOptionsRevision || api !== props.api || !canExportWeekly()) return
    weeklyExportOptions.value = options
    weeklyExportFormatCode.value = options.formats[0]?.code ?? ''
    resetWeeklySelectionForDate()
  } catch (cause) {
    if (disposed || revision !== weeklyOptionsRevision || cause instanceof StaleSessionGenerationError) return
    weeklyExportError.value = cause instanceof Error
      ? cause.message
      : 'Не удалось загрузить список недель и форматов.'
    emit('error', cause)
  } finally {
    if (revision === weeklyOptionsRevision) loadingWeeklyOptions.value = false
  }
}

async function downloadWeeklyExport(): Promise<void> {
  const api = props.api
  const reportDownload = props.reportDownload
  const options = weeklyExportOptions.value
  const format = selectedWeeklyFormat.value
  if (!api || !options || !format || !canExportWeekly() || weeklyExportBusy.value) return
  const weekStarts = options.weeks
    .filter((week) => selectedWeeklyStarts.value.includes(week.weekStart))
    .map((week) => week.weekStart)
  if (weekStarts.length === 0) {
    weeklyExportError.value = 'Выбери хотя бы одну неделю.'
    weeklyExportStatus.value = null
    return
  }
  invalidateWeeklyExport()
  const revision = weeklyExportRevision
  const controller = new AbortController()
  weeklyExportAbort = controller
  weeklyExportBusy.value = true
  try {
    if (reportDownload) {
      const request = toHeadmanWeeklyReportRequest(options.weeks, weekStarts, format)
      const isCurrent = (): boolean => !disposed && revision === weeklyExportRevision
        && api === props.api && reportDownload === props.reportDownload
        && options === weeklyExportOptions.value && canExportWeekly()
        && selectedWeeklyFormat.value?.code === format.code
        && JSON.stringify(selectedWeeklyStarts.value) === JSON.stringify(weekStarts)
      const result = await reportDownload.download(request, isCurrent)
      if (result === 'stale' || !isCurrent()) return
      if (result === 'unsupported') {
        weeklyExportStatus.value = 'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.'
      } else {
        weeklyExportStatus.value = result === 'accepted'
          ? 'Telegram принял запрос на скачивание; проверь завершение в Telegram.'
          : 'Скачивание отменено.'
      }
      return
    }

    const downloaded = await api.downloadWeeklyExport(weekStarts, format, controller.signal)
    if (disposed || revision !== weeklyExportRevision || api !== props.api || !canExportWeekly()) return
    if (typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function' || typeof document === 'undefined') {
      throw new Error('Скачивание файла недоступно в этом приложении.')
    }
    const url = URL.createObjectURL(downloaded.blob)
    attachmentObjectUrls.add(url)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = downloaded.filename
    anchor.rel = 'noopener'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => releaseAttachmentObjectUrl(url), 60_000)
    weeklyExportStatus.value = `Файл ${downloaded.filename} подготовлен.`
  } catch (cause) {
    if (disposed || revision !== weeklyExportRevision || cause instanceof StaleSessionGenerationError
      || (typeof DOMException !== 'undefined' && cause instanceof DOMException && cause.name === 'AbortError')) return
    if (cause instanceof HeadmanJournalApiError && cause.response.status === 403) {
      weeklyExportError.value = 'Для выгрузки требуется действующее право VIEW_STATS.'
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status === 413) {
      weeklyExportError.value = cause.message || 'Файл слишком большой. Выбери меньше недель или другой доступный формат; данные не обрезаны.'
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status === 422) {
      weeklyExportError.value = 'Выбранная неделя больше недоступна. Обнови список и выбери неделю из него.'
    } else if (cause instanceof HeadmanJournalApiError && cause.response.status >= 500) {
      weeklyExportError.value = 'Не удалось сформировать журнал. Проверь подключение и попробуй ещё раз.'
    } else {
      weeklyExportError.value = cause instanceof Error ? cause.message : 'Не удалось скачать журнал.'
    }
    emit('error', cause)
  } finally {
    if (revision === weeklyExportRevision) {
      weeklyExportBusy.value = false
      weeklyExportAbort = null
    }
  }
}

function canCancelLessons(): boolean {
  return Boolean(props.api && !props.offline && !props.readOnly && hasAssistantPermission('CANCEL_LESSONS'))
}

function assistantPermissionSnapshot(): readonly HeadmanAssistantPermission[] | null {
  return props.assistantPermissions === null ? null : [...props.assistantPermissions].sort()
}

function assistantPermissionsMatch(snapshot: readonly HeadmanAssistantPermission[] | null): boolean {
  const current = assistantPermissionSnapshot()
  return snapshot === null || current === null
    ? snapshot === current
    : snapshot.length === current.length && snapshot.every((permission, index) => permission === current[index])
}

function isLessonActionConfirmationCurrent(confirmation: LessonActionConfirmation): boolean {
  const lesson = selectedLesson.value
  if (lessonActionBusy.value || !lesson || lesson.id !== confirmation.lessonId
    || selectedLessonId.value !== confirmation.lessonId || props.api !== confirmation.api
    || props.groupId !== confirmation.groupId || props.offline || props.readOnly
    || !canCancelLessons() || !assistantPermissionsMatch(confirmation.permissions)) return false

  if (confirmation.action === 'cancel') {
    return lesson.status !== 'CANCELLED' && confirmation.reason !== null
      && cancelReason.value.trim() === confirmation.reason
  }
  return lesson.status === 'CANCELLED'
}

const lessonActionConfirmationCurrent = computed(() => {
  const confirmation = pendingLessonActionConfirmation.value
  return confirmation !== null && isLessonActionConfirmationCurrent(confirmation)
})

function closeLessonActionConfirmation(): void {
  pendingLessonActionConfirmation.value = null
  const dialog = lessonActionConfirmationDialog.value
  if (dialog?.open) dialog.close()
}

function invalidateLessonActionConfirmation(): void {
  if (pendingLessonActionConfirmation.value !== null) closeLessonActionConfirmation()
}

function openLessonActionConfirmation(confirmation: LessonActionConfirmation): void {
  const dialog = lessonActionConfirmationDialog.value
  if (!dialog || dialog.open || !isLessonActionConfirmationCurrent(confirmation)) return
  pendingLessonActionConfirmation.value = confirmation
  dialog.showModal()
}

function canManageExcuses(): boolean {
  return hasAssistantPermission('MANAGE_EXCUSES')
}

function canWrite(entry: HeadmanJournalRosterEntry): boolean {
  const required = entry.status === 'EXCUSED' ? 'MANAGE_EXCUSES' : 'MARK_ATTENDANCE'
  return Boolean(props.api?.writable && entry.editable && !props.offline && !props.readOnly
    && selectedLessonId.value !== null && hasAssistantPermission(required))
}

function canWriteExcuse(entry: HeadmanJournalRosterEntry): boolean {
  return canManageExcuses() && canWrite(entry)
}

function requestCancelSelectedLesson(): void {
  const api = props.api
  const lesson = selectedLesson.value
  if (lessonActionBusy.value || !api || !lesson || lesson.status === 'CANCELLED' || !canCancelLessons()) return
  const reason = cancelReason.value.trim()
  if (!reason) {
    notice.value = 'Укажи причину отмены пары.'
    return
  }
  if (Array.from(reason).length > 512) {
    notice.value = 'Причина отмены не может быть длиннее 512 символов.'
    return
  }
  openLessonActionConfirmation({
    action: 'cancel',
    api,
    groupId: props.groupId,
    lessonId: lesson.id,
    lessonDate: lesson.date,
    lessonNumber: lesson.lessonNumber,
    permissions: assistantPermissionSnapshot(),
    reason,
  })
}

function requestRestoreSelectedLesson(): void {
  const api = props.api
  const lesson = selectedLesson.value
  if (lessonActionBusy.value || !api || !lesson || lesson.status !== 'CANCELLED' || !canCancelLessons()) return
  openLessonActionConfirmation({
    action: 'restore',
    api,
    groupId: props.groupId,
    lessonId: lesson.id,
    lessonDate: lesson.date,
    lessonNumber: lesson.lessonNumber,
    permissions: assistantPermissionSnapshot(),
    reason: null,
  })
}

async function confirmLessonAction(): Promise<void> {
  const confirmation = pendingLessonActionConfirmation.value
  if (!confirmation) return
  if (!isLessonActionConfirmationCurrent(confirmation)) {
    closeLessonActionConfirmation()
    return
  }
  closeLessonActionConfirmation()
  if (confirmation.action === 'cancel' && confirmation.reason !== null) {
    await cancelSelectedLesson(confirmation.api, confirmation.lessonId, confirmation.reason)
  } else {
    await restoreSelectedLesson(confirmation.api, confirmation.lessonId)
  }
}

async function cancelSelectedLesson(api: HeadmanJournalApi, lessonId: number, reason: string): Promise<void> {
  if (lessonActionBusy.value || api !== props.api || selectedLessonId.value !== lessonId || !canCancelLessons()) return
  const revision = ++lessonActionRevision
  lessonActionBusy.value = true
  error.value = null
  notice.value = null
  try {
    await api.cancelLesson(lessonId, reason)
    if (disposed || revision !== lessonActionRevision) return
    notice.value = 'Пара отменена. Список обновлён с сервера.'
    cancelReason.value = ''
    await loadLessons()
  } catch (cause) {
    if (disposed || revision !== lessonActionRevision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось отменить пару.'
    emit('error', cause)
  } finally {
    if (!disposed && revision === lessonActionRevision) lessonActionBusy.value = false
  }
}

async function restoreSelectedLesson(api: HeadmanJournalApi, lessonId: number): Promise<void> {
  if (lessonActionBusy.value || api !== props.api || selectedLessonId.value !== lessonId || !canCancelLessons()) return
  const revision = ++lessonActionRevision
  lessonActionBusy.value = true
  error.value = null
  notice.value = null
  try {
    await api.restoreLesson(lessonId)
    if (disposed || revision !== lessonActionRevision) return
    notice.value = 'Пара восстановлена. Список обновлён с сервера.'
    await loadLessons()
  } catch (cause) {
    if (disposed || revision !== lessonActionRevision || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось восстановить пару.'
    emit('error', cause)
  } finally {
    if (!disposed && revision === lessonActionRevision) lessonActionBusy.value = false
  }
}

function writeUnavailableMessage(entry: HeadmanJournalRosterEntry): string {
  if (props.offline) return 'Изменение отметки доступно только онлайн.'
  if (props.readOnly) return 'Текущая сессия открыта только для чтения.'
  if (!entry.editable) return 'Сервер запретил изменение этой отметки.'
  return 'Изменение отметки пока недоступно.'
}

function excuseTypeLabel(value: string | null): string | null {
  return excuseTypes.find((item) => item.value === value)?.label ?? value
}

function formatFileSize(value: number | null): string {
  if (value === null) return ''
  if (value < 1024) return `${value} Б`
  return `${Math.ceil(value / 1024)} КБ`
}

function attachmentState(entry: HeadmanJournalRosterEntry): RequestAttachmentViewState | null {
  return attachmentStates.value[entry.userId] ?? null
}

function releaseAttachmentObjectUrl(url: string): void {
  attachmentObjectUrls.delete(url)
  if (typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(url)
}

function closeAttachmentPopup(popup: RequestAttachmentPopup): void {
  try { popup.close?.() } catch { /* Browser may have closed it already. */ }
  attachmentPopups.delete(popup)
}

function openAttachment(entry: HeadmanJournalRosterEntry): void {
  const lessonId = selectedLessonId.value
  const api = props.api
  if (props.offline || !api || lessonId === null || !entry.attachmentId || !canManageExcuses()
    || attachmentState(entry)?.status === 'pending') return
  const ownerIdentityAtStart = attachmentOwnerIdentity.value
  const ownerGenerationAtStart = attachmentOwnerGeneration.value
  runRequestAttachmentOpen({
    ownerIdentity: ownerIdentityAtStart,
    ownerGeneration: ownerGenerationAtStart,
    currentOwnerIdentity: () => attachmentOwnerIdentity.value,
    currentOwnerGeneration: () => attachmentOwnerGeneration.value,
    isDisposed: () => attachmentDisposed,
    openPopup: () => typeof window === 'undefined' ? null : openRequestAttachmentPopup(window),
    download: () => api.downloadAttachment(lessonId, entry.userId),
    createObjectUrl: (blob) => {
      if (typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') throw new Error('Не удалось подготовить вложение.')
      const url = URL.createObjectURL(blob)
      attachmentObjectUrls.add(url)
      return url
    },
    releaseObjectUrl: releaseAttachmentObjectUrl,
    scheduleRelease: (url) => {
      if (typeof window === 'undefined') {
        releaseAttachmentObjectUrl(url)
        return
      }
      window.setTimeout(() => releaseAttachmentObjectUrl(url), 60_000)
    },
    navigate: (popup, url) => { popup.location.href = url },
    closePopup: closeAttachmentPopup,
    onPopupOpened: (popup) => attachmentPopups.add(popup),
    onPopupNavigated: (popup) => attachmentPopups.delete(popup),
    setState: (state) => {
      if (!attachmentDisposed
        && ownerIdentityAtStart === attachmentOwnerIdentity.value
        && ownerGenerationAtStart === attachmentOwnerGeneration.value) {
        attachmentStates.value = { ...attachmentStates.value, [entry.userId]: state }
      }
    },
    onError: (cause) => emit('error', cause),
    errorMessage: (cause) => cause instanceof Error ? cause.message : 'Не удалось открыть вложение.',
  })
}

function openExcuseForm(entry: HeadmanJournalRosterEntry): void {
  if (!canWriteExcuse(entry)) {
    notice.value = writeUnavailableMessage(entry)
    return
  }
  excuseUserId.value = entry.userId
  excuseType.value = excuseTypes.some((item) => item.value === entry.excuseType)
    ? entry.excuseType as HeadmanJournalExcuseType
    : 'OTHER'
  excuseComment.value = entry.comment ?? ''
  excuseFile.value = null
  notice.value = null
}

function closeExcuseForm(): void {
  if (pendingUserId.value !== null) return
  excuseUserId.value = null
  excuseComment.value = ''
  excuseFile.value = null
}

function chooseExcuseFile(event: Event): void {
  const input = event.target as HTMLInputElement
  excuseFile.value = input.files?.[0] ?? null
}

async function submitExcuse(entry: HeadmanJournalRosterEntry): Promise<void> {
  if (pendingUserId.value !== null || excuseUserId.value !== entry.userId || !canWriteExcuse(entry)) return
  const lessonId = selectedLessonId.value
  const api = props.api
  if (!api || lessonId === null) return
  if (Array.from(excuseComment.value).length > 1000) {
    setRowError(entry.userId, 'Комментарий не может быть длиннее 1000 символов.')
    return
  }
  pendingUserId.value = entry.userId
  notice.value = null
  clearRowError(entry.userId)
  try {
    await api.mark(lessonId, entry.userId, {
      status: 'EXCUSED',
      excuseType: excuseType.value,
      ...(excuseComment.value.trim() ? { comment: excuseComment.value } : {}),
      ...(excuseFile.value ? { file: excuseFile.value } : {}),
    })
    closeExcuseForm()
    await loadReport(lessonId)
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) return
    const message = cause instanceof Error ? cause.message : 'Не удалось сохранить причину.'
    setRowError(entry.userId, message)
    emit('error', cause)
  } finally {
    if (pendingUserId.value === entry.userId) pendingUserId.value = null
  }
}

async function clearExcuse(entry: HeadmanJournalRosterEntry): Promise<void> {
  if (pendingUserId.value !== null
    || (excuseUserId.value !== null && excuseUserId.value !== entry.userId)
    || !canWriteExcuse(entry)) return
  pendingUserId.value = entry.userId
  notice.value = null
  clearRowError(entry.userId)
  try {
    await clearEntry(entry)
    closeExcuseForm()
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) return
    const message = cause instanceof Error ? cause.message : 'Не удалось снять отметку.'
    setRowError(entry.userId, message)
    emit('error', cause)
  } finally {
    if (pendingUserId.value === entry.userId) pendingUserId.value = null
  }
}

async function applyStatus(entry: HeadmanJournalRosterEntry, status: 'PRESENT' | 'ABSENT'): Promise<void> {
  if (pendingUserId.value !== null) return
  if (!canWrite(entry)) {
    notice.value = writeUnavailableMessage(entry)
    return
  }
  const lessonId = selectedLessonId.value
  const api = props.api
  if (!api || lessonId === null) return
  pendingUserId.value = entry.userId
  notice.value = null
  clearRowError(entry.userId)
  try {
    if (entry.status === status && isExpanded(entry.userId)) {
      await clearEntry(entry)
    } else {
      await api.mark(lessonId, entry.userId, { status })
      await loadReport(lessonId)
    }
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) return
    const message = cause instanceof Error ? cause.message : 'Не удалось сохранить отметку.'
    setRowError(entry.userId, message)
    emit('error', cause)
  } finally {
    if (pendingUserId.value === entry.userId) pendingUserId.value = null
  }
}

async function clearEntry(entry: HeadmanJournalRosterEntry): Promise<void> {
  const lessonId = selectedLessonId.value
  const api = props.api
  if (!api || lessonId === null) return
  await api.clear(lessonId, entry.userId)
  // A row is updated only after the server ACK. The fresh GET also carries
  // any server-side source, reason, or editability changes.
  await loadReport(lessonId)
}

function onStatusClick(entry: HeadmanJournalRosterEntry, status: 'PRESENT' | 'ABSENT' | 'EXCUSED'): void {
  if (status === 'EXCUSED') {
    if (!canWriteExcuse(entry)) {
      notice.value = 'Для уважительной причины нужно право «Обрабатывать уважительные причины».'
      return
    }
    if (entry.status === 'EXCUSED' && isExpanded(entry.userId)) {
      void clearExcuse(entry)
      return
    }
    if (entry.status !== 'EMPTY' && !isExpanded(entry.userId)) {
      toggleExpanded(entry.userId)
      return
    }
    openExcuseForm(entry)
    return
  }
  if (entry.status !== 'EMPTY' && !isExpanded(entry.userId)) {
    toggleExpanded(entry.userId)
    return
  }
  void applyStatus(entry, status)
}

async function loadReport(lessonId: number): Promise<void> {
  const revision = ++reportRevision
  attachmentOwnerGeneration.value += 1
  attachmentStates.value = {}
  loadingReport.value = true
  error.value = null
  notice.value = null
  report.value = null
  excuseUserId.value = null
  excuseFile.value = null
  expandedUserIds.value = new Set()
  const api = props.api
  if (!api || props.offline) {
    loadingReport.value = false
    return
  }
  try {
    const next = await api.getLessonAttendance(lessonId)
    if (revision !== reportRevision || selectedLessonId.value !== lessonId) return
    report.value = next
  } catch (cause) {
    if (revision !== reportRevision || selectedLessonId.value !== lessonId) return
    if (cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить состав группы.'
    emit('error', cause)
  } finally {
    if (revision === reportRevision) loadingReport.value = false
  }
}

async function loadLessons(): Promise<void> {
  const revision = ++lessonsRevision
  reportRevision += 1
  loadingLessons.value = true
  loadingReport.value = false
  error.value = null
  notice.value = null
  lessons.value = []
  report.value = null
  selectedLessonId.value = null
  expandedUserIds.value = new Set()
  const api = props.api
  if (!api || props.groupId === null || props.offline) {
    loadingLessons.value = false
    return
  }
  try {
    const next = await api.listLessons(props.groupId, selectedDate.value, selectedDate.value)
    if (revision !== lessonsRevision) return
    lessons.value = next
    const wantsInitialLesson = props.initialLessonId !== null && selectedDate.value === props.initialDate
    const requestedLesson = wantsInitialLesson
      ? next.find((lesson) => lesson.id === props.initialLessonId) ?? null
      : null
    if (wantsInitialLesson && requestedLesson === null) {
      error.value = 'Выбранная пара недоступна на этой дате. Вернись в «Сегодня» и выбери занятие ещё раз.'
      return
    }
    const first = requestedLesson ?? next[0]
    if (first && canViewReport()) {
      selectedLessonId.value = first.id
      await loadReport(first.id)
    } else if (first) {
      selectedLessonId.value = first.id
      report.value = null
    }
  } catch (cause) {
    if (revision !== lessonsRevision) return
    if (cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof HeadmanJournalApiError && (cause.response.status === 401 || cause.response.status === 403)
      ? 'Журнал недоступен для текущей роли.'
      : cause instanceof Error ? cause.message : 'Не удалось загрузить пары.'
    emit('error', cause)
  } finally {
    if (revision === lessonsRevision) loadingLessons.value = false
  }
}

watch(
  () => [props.api, props.groupId, props.offline, selectedDate.value, props.assistantPermissions] as const,
  () => { void loadLessons() },
  { immediate: true },
)

watch(
  () => [props.api, props.groupId, props.offline, props.assistantPermissions, props.reportDownload] as const,
  () => { void loadWeeklyExportOptions() },
  { immediate: true, flush: 'sync' },
)

watch(
  () => selectedDate.value,
  () => {
    invalidateWeeklyExport()
    resetWeeklySelectionForDate()
  },
  { flush: 'sync' },
)

watch(
  () => [
    props.api,
    props.groupId,
    props.offline,
    props.readOnly,
    props.assistantPermissions,
    assistantPermissionSnapshot()?.join('\u0000'),
    selectedDate.value,
    selectedLessonId.value,
    selectedLesson.value?.id,
    selectedLesson.value?.status,
    cancelReason.value.trim(),
    canCancelLessons(),
  ] as const,
  invalidateLessonActionConfirmation,
  { flush: 'sync' },
)

watch(
  () => [props.api, props.groupId, selectedLessonId.value] as const,
  () => {
    attachmentOwnerGeneration.value += 1
    attachmentOwnerIdentity.value = props.api ? `headman-journal-${attachmentOwnerGeneration.value}` : null
    attachmentStates.value = {}
  },
  { flush: 'sync', immediate: true },
)

onBeforeUnmount(() => {
  disposed = true
  closeLessonActionConfirmation()
  lessonActionRevision += 1
  attachmentDisposed = true
  lessonsRevision += 1
  reportRevision += 1
  weeklyOptionsRevision += 1
  invalidateWeeklyExport()
  weeklyExportAbort?.abort()
  weeklyExportOptions.value = null
  attachmentOwnerGeneration.value += 1
  for (const popup of attachmentPopups) closeAttachmentPopup(popup)
  for (const url of attachmentObjectUrls) releaseAttachmentObjectUrl(url)
})
</script>

<template>
  <main
    class="headman-journal"
    :aria-busy="busy"
    aria-labelledby="headman-journal-title"
  >
    <header class="headman-journal__header">
      <div>
        <p class="headman-journal__eyebrow">
          Староста · журнал
        </p>
        <h1 id="headman-journal-title">
          Посещаемость группы
        </h1>
        <p class="headman-journal__date">
          {{ formatDate(selectedDate) }}
        </p>
      </div>
      <button
        class="headman-journal__today"
        type="button"
        :disabled="selectedDate === todayIso()"
        @click="setToday"
      >
        Сегодня
      </button>
    </header>

    <section
      v-if="canExportWeekly()"
      class="headman-journal__weekly-export"
      aria-labelledby="headman-journal-weekly-export-title"
      :aria-busy="loadingWeeklyOptions || weeklyExportBusy"
    >
      <h2 id="headman-journal-weekly-export-title">
        Скачать недельный журнал
      </h2>
      <p
        v-if="loadingWeeklyOptions"
        class="headman-journal__state"
        role="status"
      >
        Загружаем серверный список недель и форматов…
      </p>
      <p
        v-else-if="weeklyExportError && !weeklyExportOptions"
        class="headman-journal__state headman-journal__state--error"
        role="alert"
      >
        {{ weeklyExportError }}
      </p>
      <div
        v-else-if="weeklyExportOptions"
        class="headman-journal__weekly-export-controls"
      >
        <p class="headman-journal__weekly-export-context">
          {{ weeklyExportOptions.semesterName }} · {{ selectedWeeklyCount }} выбрано
        </p>
        <fieldset
          class="headman-journal__weekly-weeks"
          :disabled="weeklyExportBusy"
        >
          <legend>Недели для выгрузки</legend>
          <label
            v-for="week in weeklyExportOptions.weeks"
            :key="week.weekStart"
            class="headman-journal__weekly-week"
          >
            <input
              type="checkbox"
              :checked="selectedWeeklyStarts.includes(week.weekStart)"
              :aria-label="`${week.label}, ${formatShortDate(week.weekStart)}–${formatShortDate(week.weekEnd)}${week.current ? ', текущая неделя' : ''}`"
              @change="toggleWeeklyWeek(week.weekStart, ($event.target as HTMLInputElement).checked)"
            >
            <span>
              {{ week.label }} · {{ formatShortDate(week.weekStart) }}–{{ formatShortDate(week.weekEnd) }}<span v-if="week.current"> · текущая</span>
            </span>
          </label>
        </fieldset>
        <label class="headman-journal__weekly-format">
          <span>Формат файла</span>
          <select
            v-model="weeklyExportFormatCode"
            :disabled="weeklyExportBusy"
          >
            <option
              v-for="format in weeklyExportOptions.formats"
              :key="format.code"
              :value="format.code"
            >
              {{ format.label }}
            </option>
          </select>
        </label>
        <p class="headman-journal__weekly-export-context">
          Общий предел — 20 МиБ на файл; PDF и PNG могут иметь меньший технический предел. Большой отчёт не обрезается.
        </p>
        <button
          class="headman-journal__weekly-download"
          type="button"
          :disabled="weeklyExportBusy || selectedWeeklyCount === 0 || !selectedWeeklyFormat"
          :aria-busy="weeklyExportBusy"
          @click="downloadWeeklyExport"
        >
          {{ weeklyExportBusy ? 'Готовим файл…' : `Скачать${selectedWeeklyCount > 1 ? ` (${selectedWeeklyCount} недели)` : ''}` }}
        </button>
        <p
          v-if="weeklyExportError"
          class="headman-journal__state headman-journal__state--error"
          role="alert"
        >
          {{ weeklyExportError }}
        </p>
        <p
          v-if="weeklyExportStatus"
          class="headman-journal__state"
          role="status"
        >
          {{ weeklyExportStatus }}
        </p>
      </div>
    </section>

    <nav
      class="headman-journal__date-nav"
      aria-label="Выбор даты"
    >
      <button
        type="button"
        aria-label="Предыдущий день"
        @click="shiftDate(-1)"
      >
        ←
      </button>
      <output>{{ formatShortDate(selectedDate) }}</output>
      <button
        type="button"
        aria-label="Следующий день"
        @click="shiftDate(1)"
      >
        →
      </button>
    </nav>

    <p
      v-if="offline"
      class="headman-journal__state"
      role="status"
    >
      Журнал доступен только при подключении к интернету.
    </p>
    <p
      v-else-if="error"
      class="headman-journal__state headman-journal__state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-else-if="groupId === null"
      class="headman-journal__state headman-journal__state--error"
      role="alert"
    >
      Не удалось определить группу старосты.
    </p>
    <p
      v-else-if="!api"
      class="headman-journal__state headman-journal__state--error"
      role="alert"
    >
      Журнал недоступен: источник данных не подключён.
    </p>
    <p
      v-if="notice"
      class="headman-journal__state headman-journal__state--notice"
      role="status"
    >
      {{ notice }}
    </p>

    <section
      class="headman-journal__lessons"
      aria-labelledby="headman-journal-lessons-title"
    >
      <h2 id="headman-journal-lessons-title">
        Пары за день
      </h2>
      <p
        v-if="loadingLessons"
        class="headman-journal__state"
        role="status"
      >
        Загружаем пары…
      </p>
      <p
        v-else-if="!offline && api && groupId !== null && lessons.length === 0"
        class="headman-journal__empty"
      >
        На эту дату сервер не вернул пар.
      </p>
      <div
        v-else
        class="headman-journal__lesson-list"
        role="list"
      >
        <button
          v-for="lesson in lessons"
          :key="lesson.id"
          class="headman-journal__lesson"
          :data-selected="selectedLessonId === lesson.id"
          type="button"
          role="listitem"
          :aria-pressed="selectedLessonId === lesson.id"
          @click="selectLesson(lesson.id)"
        >
          <span class="headman-journal__lesson-number">
            {{ lesson.lessonNumber ?? '—' }}
          </span>
          <span class="headman-journal__lesson-copy">
            <strong>{{ formatTime(lesson.startTime) }}–{{ formatTime(lesson.endTime) }}</strong>
            <span>Пара {{ lesson.lessonNumber ?? '—' }} · {{ lessonStatusLabel(lesson.status) }}</span>
            <span v-if="lesson.room">Аудитория {{ lesson.room }}</span>
          </span>
        </button>
      </div>
    </section>

    <section
      v-if="selectedLesson"
      class="headman-journal__roster"
      aria-labelledby="headman-journal-roster-title"
    >
      <header class="headman-journal__section-header">
        <div>
          <h2 id="headman-journal-roster-title">
            Состав группы
          </h2>
          <p>
            {{ formatShortDate(selectedLesson.date) }} · пара {{ selectedLesson.lessonNumber ?? '—' }}
          </p>
        </div>
        <span
          v-if="report?.editable"
          class="headman-journal__server-flag"
        >
          Изменения разрешены сервером
        </span>
      </header>

      <section
        v-if="canCancelLessons()"
        class="headman-journal__lesson-actions"
        aria-labelledby="headman-journal-lesson-actions-title"
      >
        <h3 id="headman-journal-lesson-actions-title">
          Управление парой
        </h3>
        <template v-if="selectedLesson.status === 'CANCELLED'">
          <p>Пара отменена сервером.</p>
          <button
            type="button"
            :disabled="lessonActionBusy || offline || readOnly"
            @click="requestRestoreSelectedLesson"
          >
            {{ lessonActionBusy ? 'Восстанавливаем…' : 'Восстановить пару' }}
          </button>
        </template>
        <template v-else>
          <label>
            <span>Причина отмены</span>
            <input
              v-model="cancelReason"
              type="text"
              maxlength="512"
              :disabled="lessonActionBusy || offline || readOnly"
              placeholder="Например, преподаватель болен"
            >
          </label>
          <button
            type="button"
            :disabled="lessonActionBusy || offline || readOnly || !cancelReason.trim()"
            @click="requestCancelSelectedLesson"
          >
            {{ lessonActionBusy ? 'Отменяем…' : 'Отменить пару' }}
          </button>
        </template>
      </section>

      <dialog
        ref="lessonActionConfirmationDialog"
        aria-labelledby="headman-journal-lesson-action-confirmation-title"
        aria-describedby="headman-journal-lesson-action-confirmation-detail"
        @close="pendingLessonActionConfirmation = null"
      >
        <h2 id="headman-journal-lesson-action-confirmation-title">
          {{ pendingLessonActionConfirmation?.action === 'cancel' ? 'Отменить пару?' : 'Восстановить пару?' }}
        </h2>
        <p id="headman-journal-lesson-action-confirmation-detail">
          <template v-if="pendingLessonActionConfirmation?.action === 'cancel'">
            {{ formatShortDate(pendingLessonActionConfirmation.lessonDate) }} · пара {{ pendingLessonActionConfirmation.lessonNumber ?? '—' }}.
            Причина: {{ pendingLessonActionConfirmation.reason }}.
            Отмена удалит отметки посещаемости, архивирует домашние задания и пересчитает статистику.
          </template>
          <template v-else-if="pendingLessonActionConfirmation?.action === 'restore'">
            {{ formatShortDate(pendingLessonActionConfirmation.lessonDate) }} · пара {{ pendingLessonActionConfirmation.lessonNumber ?? '—' }}.
            Удалённые отметки посещаемости не вернутся.
          </template>
        </p>
        <button
          type="button"
          @click="closeLessonActionConfirmation"
        >
          Назад
        </button>
        <button
          type="button"
          :disabled="lessonActionBusy || !lessonActionConfirmationCurrent"
          @click="void confirmLessonAction()"
        >
          {{ pendingLessonActionConfirmation?.action === 'cancel' ? 'Отменить пару' : 'Восстановить пару' }}
        </button>
      </dialog>

      <p
        v-if="canViewReport() && loadingReport"
        class="headman-journal__state"
        role="status"
      >
        Загружаем состав и отметки…
      </p>
      <p
        v-else-if="canViewReport() && report && report.entries.length === 0"
        class="headman-journal__empty"
      >
        Сервер вернул пустой состав группы.
      </p>
      <ol
        v-else-if="canViewReport() && report"
        class="headman-journal__roster-list"
      >
        <li
          v-for="entry in entries"
          :key="entry.userId"
          class="headman-journal__row"
          :data-status="entry.status.toLowerCase()"
        >
          <div class="headman-journal__row-main">
            <span class="headman-journal__row-name">{{ entry.displayName }}</span>
            <span class="headman-journal__row-meta">
              <span class="headman-journal__badge">
                {{ statusSymbol(entry.status) }} · {{ statusLabel(entry.status) }}
              </span>
              <span v-if="entry.source">Источник: {{ entry.source }}</span>
              <span v-if="entry.excuseReason">Причина: {{ entry.excuseReason }}</span>
              <span v-if="entry.excuseType">Тип причины: {{ excuseTypeLabel(entry.excuseType) }}</span>
              <button
                v-if="entry.status === 'EXCUSED' && canWrite(entry)"
                class="headman-journal__reason-edit"
                type="button"
                @click="openExcuseForm(entry)"
              >
                Изменить причину
              </button>
              <span v-if="entry.attachmentId && canManageExcuses()">
                <button
                  class="headman-journal__attachment"
                  type="button"
                  :disabled="offline || attachmentState(entry)?.status === 'pending'"
                  :aria-busy="attachmentState(entry)?.status === 'pending'"
                  @click="openAttachment(entry)"
                >
                  {{ attachmentState(entry)?.status === 'pending' ? 'Открываем…' : attachmentState(entry)?.status === 'error' ? 'Повторить файл' : 'Открыть файл' }}
                </button>
                <span v-if="entry.attachmentName">
                  {{ entry.attachmentName }}<span v-if="entry.attachmentSize !== null"> ({{ formatFileSize(entry.attachmentSize) }})</span>
                </span>
                <span
                  v-if="attachmentState(entry)?.status === 'error'"
                  class="headman-journal__attachment-error"
                  role="alert"
                >
                  {{ attachmentState(entry)?.error || 'Не удалось открыть вложение.' }}
                </span>
              </span>
            </span>
          </div>

          <div
            v-if="canWrite(entry) && (entry.status === 'EMPTY' || isExpanded(entry.userId))"
            class="headman-journal__picker"
            role="group"
            :aria-label="`Изменить отметку: ${entry.displayName}`"
          >
            <button
              type="button"
              :disabled="pendingUserId !== null"
              :aria-label="`Отметить «был»: ${entry.displayName}`"
              @click="onStatusClick(entry, 'PRESENT')"
            >
              +
            </button>
            <button
              type="button"
              :disabled="pendingUserId !== null"
              :aria-label="`Отметить «не был»: ${entry.displayName}`"
              @click="onStatusClick(entry, 'ABSENT')"
            >
              н
            </button>
            <button
              v-if="canManageExcuses()"
              type="button"
              :disabled="pendingUserId !== null"
              :aria-label="`Отметить «уважительная причина»: ${entry.displayName}`"
              @click="onStatusClick(entry, 'EXCUSED')"
            >
              у
            </button>
          </div>
          <button
            v-else
            class="headman-journal__current"
            type="button"
            :disabled="pendingUserId !== null || !canWrite(entry)"
            :aria-label="`Открыть изменение отметки: ${entry.displayName}`"
            @click="toggleExpanded(entry.userId)"
          >
            {{ statusSymbol(entry.status) }}
          </button>

          <form
            v-if="excuseUserId === entry.userId"
            class="headman-journal__excuse-form"
            @submit.prevent="submitExcuse(entry)"
          >
            <label>
              <span>Причина</span>
              <select v-model="excuseType">
                <option
                  v-for="item in excuseTypes"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              <span>Комментарий</span>
              <textarea
                v-model="excuseComment"
                maxlength="1000"
                rows="3"
              />
            </label>
            <label>
              <span>Файл (необязательно)</span>
              <input
                type="file"
                @change="chooseExcuseFile"
              >
            </label>
            <p v-if="excuseFile">
              Выбран файл: {{ excuseFile.name }}
            </p>
            <div class="headman-journal__excuse-actions">
              <button
                type="submit"
                :disabled="pendingUserId !== null"
              >
                {{ pendingUserId === entry.userId ? 'Сохраняем…' : 'Сохранить причину' }}
              </button>
              <button
                type="button"
                :disabled="pendingUserId !== null"
                @click="closeExcuseForm"
              >
                Отмена
              </button>
              <button
                type="button"
                :disabled="pendingUserId !== null"
                @click="clearExcuse(entry)"
              >
                Снять отметку
              </button>
            </div>
          </form>

          <span
            v-if="pendingUserId === entry.userId"
            class="headman-journal__row-status"
            role="status"
          >
            Сохраняем…
          </span>
          <span
            v-else-if="rowErrors[entry.userId]"
            class="headman-journal__row-status headman-journal__row-status--error"
            role="alert"
          >
            {{ rowErrors[entry.userId] }}
          </span>
          <span
            v-else-if="entry.status !== 'CANCELLED' && entry.status !== 'UNSUPPORTED' && !canWrite(entry)"
            class="headman-journal__row-status"
          >
            {{ writeUnavailableMessage(entry) }}
          </span>
        </li>
      </ol>
    </section>
  </main>
</template>
