<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import HeadmanStatsDetailPanel from './HeadmanStatsDetailPanel.vue'
import HeadmanStatsTrendChart from './HeadmanStatsTrendChart.vue'
import {
  HeadmanStatsApiError,
  headmanStatsResponseForCurrentQuery,
  headmanStatsQueryKey,
  headmanStatsQueryScopeKey,
  hasHeadmanStatsResettableCriteria,
  resetHeadmanStatsCriteria,
  toHeadmanStatsReportRequest,
  toHeadmanStatsTrendReportRequest,
  type HeadmanStatsApi,
  type HeadmanStatsBlock,
  type HeadmanStatsColumn,
  type HeadmanStatsFilter,
  type HeadmanStatsFormat,
  type HeadmanStatsMetric,
  type HeadmanStatsResponse,
  type HeadmanStatsSort,
  type HeadmanStatsStudentRow,
  type HeadmanStatsStudentDetailResponse,
  type HeadmanStatsTrendMode,
  type HeadmanStatsTrendFormat,
  type HeadmanStatsTrendQuery,
  type HeadmanStatsTrendResponse,
} from './headman-stats-client'
import './headman-stats-screen.pcss'

interface FilterDraft { contains: string; minimum: string; maximum: string }

const props = withDefaults(defineProps<{
  api: HeadmanStatsApi | null
  groupId: number | null
  assistantPermissions?: readonly string[] | null
  offline?: boolean
  reportDownload?: ReportDownloadPort | null
}>(), {
  assistantPermissions: null,
  offline: false,
  reportDownload: null,
})

const emit = defineEmits<{ error: [cause: unknown]; back: [] }>()

const block = ref<HeadmanStatsBlock>('group')
const response = ref<HeadmanStatsResponse | null>(null)
const responseQueryKey = ref<string | null>(null)
const responseScopeKey = ref<string | null>(null)
const selectedSubjectId = ref<number | null>(null)
const selectedTypes = ref<string[]>([])
const sorts = ref<HeadmanStatsSort[]>([])
const filters = ref<HeadmanStatsFilter[]>([])
const filterDrafts = reactive<Record<string, FilterDraft>>({})
const selectedFormat = ref<HeadmanStatsFormat['code']>('xlsx')
const trendMode = ref<HeadmanStatsTrendMode>('SEMESTER')
const trendWeekStart = ref('')
const trendSubjectId = ref<number | null>(null)
const trendLessonTypes = ref<string[]>([])
const trendResponse = ref<HeadmanStatsTrendResponse | null>(null)
const trendResponseKey = ref<string | null>(null)
const trendLoading = ref(false)
const trendError = ref<string | null>(null)
const trendExportStatus = ref<string | null>(null)
const trendExportError = ref<string | null>(null)
const detailStudentId = ref<number | null>(null)
const detailResponse = ref<HeadmanStatsStudentDetailResponse | null>(null)
const detailResponseKey = ref<string | null>(null)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const latePage = ref(0)
const excusePage = ref(0)
const detailPanelRef = ref<{ focus: () => void } | null>(null)
const detailReturnFocus = ref<HTMLButtonElement | null>(null)
const loading = ref(false)
const exporting = ref(false)
const exportStatus = ref<string | null>(null)
const denied = ref(false)
const error = ref<string | null>(null)
const page = ref(0)
const pageSize = 50
let revision = 0
let activeController: AbortController | null = null
let trendRevision = 0
let trendController: AbortController | null = null
let detailRevision = 0
let detailController: AbortController | null = null

const canView = computed(() => props.assistantPermissions === null
  || props.assistantPermissions.includes('VIEW_STATS'))
const currentQuery = computed(() => ({
  ...queryForCurrentBlock(true),
  page: page.value,
  size: pageSize,
}))
const currentQueryKey = computed(() => headmanStatsQueryKey(currentQuery.value))
const currentScopeKey = computed(() => headmanStatsQueryScopeKey(block.value, currentQuery.value))
const currentTrendQuery = computed<HeadmanStatsTrendQuery | null>(() => {
  if (trendMode.value === 'SEMESTER') return { mode: 'SEMESTER' }
  if (trendMode.value === 'WEEK') return trendWeekStart.value ? { mode: 'WEEK', weekStart: trendWeekStart.value } : null
  if (trendSubjectId.value === null) return null
  return {
    mode: 'SUBJECT',
    subjectId: trendSubjectId.value,
    ...(trendLessonTypes.value.length ? { lessonTypes: [...trendLessonTypes.value] } : {}),
  }
})
const currentTrendKey = computed(() => currentTrendQuery.value === null
  ? null
  : JSON.stringify({ groupId: props.groupId, query: currentTrendQuery.value }))
const displayedTrend = computed(() => trendResponseKey.value !== null
  && trendResponseKey.value === currentTrendKey.value
  && !props.offline && canView.value && !denied.value
  ? trendResponse.value
  : null)
const currentDetailKey = computed(() => detailStudentId.value === null
  ? null
  : JSON.stringify({ groupId: props.groupId, studentId: detailStudentId.value, latePage: latePage.value, excusePage: excusePage.value, size: 20 }))
const displayedDetail = computed(() => detailResponseKey.value !== null
  && detailResponseKey.value === currentDetailKey.value
  && !props.offline && canView.value && !denied.value
  ? detailResponse.value
  : null)
const controlResponse = computed(() => headmanStatsResponseForCurrentQuery(
  response.value,
  responseScopeKey.value,
  currentScopeKey.value,
))
const displayedResponse = computed(() => headmanStatsResponseForCurrentQuery(
  response.value,
  responseQueryKey.value,
  currentQueryKey.value,
))
const activeFormat = computed(() => displayedResponse.value?.formats.find((item) => item.code === selectedFormat.value) ?? null)
const selectedSubject = computed(() => response.value?.subjects.find((item) => item.id === selectedSubjectId.value) ?? null)
const selectedTrendSubject = computed(() => response.value?.subjects.find((item) => item.id === trendSubjectId.value) ?? null)
const hasFilterDraft = computed(() => filters.value.length > 0 || (controlResponse.value?.columns.some((column) => {
  const draft = filterDrafts[column.field]
  return draft && (draft.contains.trim() !== '' || draft.minimum.trim() !== '' || draft.maximum.trim() !== '')
}) ?? false))
const canResetCriteria = computed(() => hasHeadmanStatsResettableCriteria(sorts.value, hasFilterDraft.value))
const canExport = computed(() => activeFormat.value !== null && displayedResponse.value?.emptyState === 'NONE')
const stateMessage = computed(() => {
  if (props.offline || props.api === null) return 'Статистика доступна только при подключении к интернету.'
  if (props.groupId === null) return 'Для этого аккаунта не определена учебная группа.'
  if (!canView.value) return 'Для просмотра статистики нужно право VIEW_STATS.'
  if (denied.value) return 'Право на просмотр статистики отозвано. Обнови список действий и проверь права группы.'
  return null
})

function newFilterDraft(): FilterDraft {
  return { contains: '', minimum: '', maximum: '' }
}

function filterDraft(column: HeadmanStatsColumn): FilterDraft {
  if (!filterDrafts[column.field]) filterDrafts[column.field] = newFilterDraft()
  return filterDrafts[column.field]!
}

function visibleFilters(): HeadmanStatsFilter[] {
  return (controlResponse.value?.columns ?? []).flatMap((column): HeadmanStatsFilter[] => {
    const draft = filterDraft(column)
    if (column.filterKind === 'TEXT') {
      const contains = draft.contains.trim()
      return contains ? [{ field: column.field, contains }] : []
    }
    const minimum = parseBound(draft.minimum)
    const maximum = parseBound(draft.maximum)
    return minimum === null && maximum === null ? [] : [{ field: column.field, minimum, maximum }]
  })
}

function parseBound(value: string): number | null {
  if (value.trim() === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null
}

function toggleSort(field: string, additive: boolean): void {
  const current = sorts.value.find((sort) => sort.field === field)
  if (!additive) {
    sorts.value = current
      ? [{ field, descending: !current.descending }]
      : [{ field, descending: field.endsWith('Percent') }]
  } else if (!current) {
    sorts.value = [...sorts.value, { field, descending: field.endsWith('Percent') }]
  } else if (current.descending) {
    sorts.value = sorts.value.map((sort) => sort.field === field ? { ...sort, descending: false } : sort)
  } else {
    sorts.value = sorts.value.filter((sort) => sort.field !== field)
  }
  page.value = 0
  void loadBlock()
}

function sortIndicator(field: string): string {
  const index = sorts.value.findIndex((sort) => sort.field === field)
  if (index < 0) return ''
  return `${sorts.value[index]!.descending ? '↓' : '↑'}${sorts.value.length > 1 ? ` ${index + 1}` : ''}`
}

function metricFor(row: HeadmanStatsStudentRow, field: string): HeadmanStatsMetric | null {
  switch (field) {
    case 'presentCount': case 'presentPercent': return row.metrics.present
    case 'presentOrExcusedCount': case 'presentOrExcusedPercent': return row.metrics.presentOrExcused
    case 'excusedCount': case 'excusedPercent': return row.metrics.excused
    case 'absentCount': case 'absentPercent': return row.metrics.absent
    default: return null
  }
}

function cellValue(row: HeadmanStatsStudentRow, field: string): string | number {
  if (field === 'displayName') return row.displayName
  const metric = metricFor(row, field)
  if (metric) {
    if (field.endsWith('Count')) return `${metric.numerator} / ${metric.denominator}`
    return metric.denominator === 0 ? '—' : `${metric.percent.toFixed(1)}%`
  }
  switch (field) {
    case 'lateSubmitted': return row.lateCheckin.submitted
    case 'lateApproved': return row.lateCheckin.approved
    case 'lateRejected': return row.lateCheckin.rejected
    case 'excuseSubmitted': return row.excuse.submitted
    case 'excuseApproved': return row.excuse.approved
    case 'excuseRejected': return row.excuse.rejected
    case 'sourceStudentGeo': return row.sources.studentGeo
    case 'sourceManualRequest': return row.sources.manualRequest
    case 'sourceAutoGeoFailure': return row.sources.autoAfterGeoFailure
    case 'sourceHeadmanManual': return row.sources.headmanManual
    default: return '—'
  }
}

function metricValue(metrics: HeadmanStatsResponse['summary'], kind: 'present' | 'presentOrExcused' | 'excused' | 'absent'): string {
  const metric = metrics[kind]
  const percent = metric.denominator === 0 ? '—' : `${metric.percent.toFixed(1)}%`
  return `${metric.numerator} / ${metric.denominator} · ${percent}`
}

function queryForCurrentBlock(includePaging: boolean): {
  subjectId: number | null
  lessonTypes: readonly string[]
  sorts: readonly HeadmanStatsSort[]
  filters: readonly HeadmanStatsFilter[]
  page?: number
  size?: number
} {
  return {
    subjectId: block.value === 'group' ? null : selectedSubjectId.value,
    lessonTypes: block.value === 'group' ? [] : [...selectedTypes.value],
    sorts: [...sorts.value],
    filters: [...filters.value],
    ...(includePaging ? { page: page.value, size: pageSize } : {}),
  }
}

function toDate(value: string | null): Date | null {
  if (value === null || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return Number.isFinite(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === value ? parsed : null
}

function isoDate(value: Date): string {
  return value.toISOString().slice(0, 10)
}

function mondayOf(value: Date): Date {
  const monday = new Date(value)
  monday.setUTCDate(monday.getUTCDate() - ((monday.getUTCDay() + 6) % 7))
  return monday
}

function boundedWeekStart(context: HeadmanStatsResponse['context']): string {
  const today = new Date()
  const semesterFrom = toDate(context.semesterFrom)
  const semesterTo = toDate(context.semesterTo)
  if (semesterFrom && today < semesterFrom) return isoDate(mondayOf(semesterFrom))
  if (semesterTo && today > semesterTo) return isoDate(mondayOf(semesterTo))
  return isoDate(mondayOf(today))
}

function semesterWeekMin(): string | undefined {
  const start = toDate(response.value?.context.semesterFrom ?? null)
  return start ? isoDate(mondayOf(start)) : undefined
}

function initializeTrendControls(result: HeadmanStatsResponse): void {
  if (trendSubjectId.value === null || !result.subjects.some((subject) => subject.id === trendSubjectId.value)) {
    trendSubjectId.value = result.subjects[0]?.id ?? null
    trendLessonTypes.value = []
  } else {
    const allowed = new Set(result.subjects.find((subject) => subject.id === trendSubjectId.value)?.lessonTypes.map((type) => type.code) ?? [])
    trendLessonTypes.value = trendLessonTypes.value.filter((type) => allowed.has(type))
  }
  const start = toDate(trendWeekStart.value)
  const from = toDate(result.context.semesterFrom)
  const to = toDate(result.context.semesterTo)
  if (!start || from && start.getTime() < mondayOf(from).getTime() || to && start.getTime() > mondayOf(to).getTime()) {
    trendWeekStart.value = boundedWeekStart(result.context)
  }
}

function resetTrendRequest(): void {
  trendRevision += 1
  trendController?.abort()
  trendController = null
  trendResponse.value = null
  trendResponseKey.value = null
  trendLoading.value = false
  trendError.value = null
  trendExportStatus.value = null
  trendExportError.value = null
}

function clearDetail(focusReturn = false): void {
  detailRevision += 1
  detailController?.abort()
  detailController = null
  detailResponse.value = null
  detailResponseKey.value = null
  detailLoading.value = false
  detailError.value = null
  detailStudentId.value = null
  latePage.value = 0
  excusePage.value = 0
  const target = detailReturnFocus.value
  detailReturnFocus.value = null
  if (focusReturn && target) {
    void nextTick(() => {
      if (target.isConnected && !props.offline && canView.value && !denied.value) target.focus()
    })
  }
}

function clearOnDenied(): void {
  denied.value = true
  revision += 1
  activeController?.abort()
  activeController = null
  loading.value = false
  response.value = null
  responseQueryKey.value = null
  responseScopeKey.value = null
  exportStatus.value = null
  resetTrendRequest()
  clearDetail()
}

function setTrendMode(mode: HeadmanStatsTrendMode): void {
  if (trendMode.value === mode) return
  trendMode.value = mode
  trendError.value = null
  void loadTrend()
}

function selectTrendSubject(value: string): void {
  trendSubjectId.value = value ? Number(value) : null
  trendLessonTypes.value = []
  trendError.value = null
  void loadTrend()
}

function selectTrendWeek(value: string): void {
  const date = toDate(value)
  if (!date) return
  trendWeekStart.value = isoDate(mondayOf(date))
  trendError.value = null
  void loadTrend()
}

function shiftTrendWeek(offset: number): void {
  const date = toDate(trendWeekStart.value)
  if (!date || !Number.isInteger(offset)) return
  date.setUTCDate(date.getUTCDate() + offset * 7)
  trendWeekStart.value = isoDate(date)
  trendError.value = null
  void loadTrend()
}

function canShiftTrendWeek(offset: number): boolean {
  const date = toDate(trendWeekStart.value)
  const context = response.value?.context
  if (!date || !context) return false
  date.setUTCDate(date.getUTCDate() + offset * 7)
  const from = toDate(context.semesterFrom)
  const to = toDate(context.semesterTo)
  const weekEnd = new Date(date)
  weekEnd.setUTCDate(weekEnd.getUTCDate() + 6)
  return (!from || weekEnd >= from) && (!to || date <= to)
}

async function loadTrend(): Promise<void> {
  const api = props.api
  const groupId = props.groupId
  const base = response.value
  const query = currentTrendQuery.value
  const key = currentTrendKey.value
  if (api === null || groupId === null || props.offline || !canView.value || denied.value || !base || !query || !key) {
    resetTrendRequest()
    if (trendMode.value === 'SUBJECT' && trendSubjectId.value === null) trendError.value = 'Выбери предмет для динамики.'
    return
  }
  const currentRevision = ++trendRevision
  trendController?.abort()
  const controller = new AbortController()
  trendController = controller
  trendResponse.value = null
  trendResponseKey.value = null
  trendLoading.value = true
  trendError.value = null
  trendExportStatus.value = null
  trendExportError.value = null
  try {
    const result = await api.trend(query, controller.signal)
    if (currentRevision !== trendRevision || controller.signal.aborted) return
    if (api !== props.api || groupId !== props.groupId || props.offline || !canView.value || denied.value
      || key !== currentTrendKey.value) return
    if (result.context.groupId !== groupId || result.context.semesterId !== base.context.semesterId) {
      throw new Error('Сервер вернул динамику для другой группы или семестра.')
    }
    trendResponse.value = result
    trendResponseKey.value = key
  } catch (cause) {
    if (currentRevision !== trendRevision || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    if (api !== props.api || groupId !== props.groupId || props.offline || !canView.value || denied.value) return
    if (cause instanceof HeadmanStatsApiError && cause.response.status === 403) {
      clearOnDenied()
      return
    }
    trendError.value = cause instanceof Error ? cause.message : 'Не удалось загрузить динамику посещаемости.'
    emit('error', cause)
  } finally {
    if (currentRevision === trendRevision) trendLoading.value = false
  }
}

function openStudentDetail(studentId: number, event: MouseEvent): void {
  if (detailStudentId.value === studentId) {
    clearDetail(true)
    return
  }
  clearDetail()
  detailReturnFocus.value = event.currentTarget instanceof HTMLButtonElement ? event.currentTarget : null
  detailStudentId.value = studentId
  void nextTick(() => detailPanelRef.value?.focus())
  void loadStudentDetail()
}

async function loadStudentDetail(): Promise<void> {
  const api = props.api
  const groupId = props.groupId
  const studentId = detailStudentId.value
  const base = response.value
  const key = currentDetailKey.value
  if (api === null || groupId === null || studentId === null || !base || props.offline || !canView.value || denied.value || !key) {
    clearDetail()
    return
  }
  const currentRevision = ++detailRevision
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  const query = { latePage: latePage.value, excusePage: excusePage.value, size: 20 }
  detailResponse.value = null
  detailResponseKey.value = null
  detailLoading.value = true
  detailError.value = null
  try {
    const result = await api.studentDetail(studentId, query, controller.signal)
    if (currentRevision !== detailRevision || controller.signal.aborted) return
    if (api !== props.api || groupId !== props.groupId || studentId !== detailStudentId.value
      || props.offline || !canView.value || denied.value || key !== currentDetailKey.value) return
    if (result.context.groupId !== groupId || result.student.id !== studentId
      || result.context.semesterId !== base.context.semesterId) {
      throw new Error('Сервер вернул подробности для другого студента, группы или семестра.')
    }
    detailResponse.value = result
    detailResponseKey.value = key
  } catch (cause) {
    if (currentRevision !== detailRevision || controller.signal.aborted || cause instanceof StaleSessionGenerationError) return
    if (api !== props.api || groupId !== props.groupId || studentId !== detailStudentId.value
      || props.offline || !canView.value || denied.value) return
    if (cause instanceof HeadmanStatsApiError && cause.response.status === 403) {
      clearOnDenied()
      return
    }
    detailError.value = cause instanceof Error ? cause.message : 'Не удалось загрузить подробности студента.'
    emit('error', cause)
  } finally {
    if (currentRevision === detailRevision) detailLoading.value = false
  }
}

function changeDetailPage(kind: 'late' | 'excuse', pageNumber: number): void {
  if (!Number.isSafeInteger(pageNumber) || pageNumber < 0 || detailLoading.value) return
  if (kind === 'late') latePage.value = pageNumber
  else excusePage.value = pageNumber
  void loadStudentDetail()
}

async function loadInitial(): Promise<void> {
  revision += 1
  activeController?.abort()
  activeController = null
  resetTrendRequest()
  clearDetail()
  loading.value = false
  response.value = null
  responseQueryKey.value = null
  responseScopeKey.value = null
  exportStatus.value = null
  error.value = null
  denied.value = false
  if (props.offline || props.api === null || props.groupId === null || !canView.value) return
  block.value = 'group'
  sorts.value = []
  filters.value = []
  page.value = 0
  await loadBlock()
}

async function loadBlock(): Promise<void> {
  const api = props.api
  if (api === null || props.groupId === null || props.offline || !canView.value) return
  if (detailStudentId.value !== null) clearDetail()
  if (block.value === 'subject' && selectedSubjectId.value === null) {
    responseQueryKey.value = null
    exportStatus.value = null
    error.value = 'Выбери предмет для статистики.'
    return
  }
  const query = { ...queryForCurrentBlock(true), page: page.value, size: pageSize }
  const queryKey = headmanStatsQueryKey(query)
  const queryScopeKey = headmanStatsQueryScopeKey(block.value, query)
  const currentRevision = ++revision
  activeController?.abort()
  const controller = new AbortController()
  activeController = controller
  loading.value = true
  responseQueryKey.value = null
  exportStatus.value = null
  error.value = null
  denied.value = false
  try {
    const result = await api.query(query, controller.signal)
    if (currentRevision !== revision || controller.signal.aborted) return
    if (result.context.groupId !== props.groupId) throw new Error('Сервер вернул статистику для другой группы.')
    response.value = result
    responseQueryKey.value = queryKey
    responseScopeKey.value = queryScopeKey
    for (const column of result.columns) filterDraft(column)
    if (selectedSubjectId.value === null && result.subjects.length > 0) {
      selectedSubjectId.value = result.subjects[0]!.id
    }
    initializeTrendControls(result)
    if (!result.formats.some((format) => format.code === selectedFormat.value)) {
      selectedFormat.value = result.formats[0]?.code ?? 'xlsx'
    }
    void loadTrend()
  } catch (cause) {
    if (currentRevision !== revision || controller.signal.aborted) return
    denied.value = cause instanceof HeadmanStatsApiError && cause.response.status === 403
    if (denied.value) {
      clearOnDenied()
    }
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить статистику.'
    emit('error', cause)
  } finally {
    if (currentRevision === revision) loading.value = false
  }
}

function openBlock(next: HeadmanStatsBlock): void {
  if (next === block.value) return
  block.value = next
  page.value = 0
  sorts.value = []
  filters.value = []
  responseQueryKey.value = null
  exportStatus.value = null
  for (const draft of Object.values(filterDrafts)) Object.assign(draft, newFilterDraft())
  if (next === 'group') void loadBlock()
  else if (selectedSubjectId.value !== null) void loadBlock()
  else error.value = 'Выбери предмет для статистики.'
}

function selectSubject(value: string): void {
  selectedSubjectId.value = value ? Number(value) : null
  selectedTypes.value = []
  page.value = 0
  sorts.value = []
  filters.value = []
  responseQueryKey.value = null
  exportStatus.value = null
  if (block.value === 'subject' && selectedSubjectId.value !== null) void loadBlock()
  else if (block.value === 'subject') error.value = 'Выбери предмет для статистики.'
}

function applyFilters(): void {
  filters.value = visibleFilters()
  page.value = 0
  void loadBlock()
}

function resetCriteria(): void {
  const clearedCriteria = resetHeadmanStatsCriteria()
  for (const draft of Object.values(filterDrafts)) Object.assign(draft, newFilterDraft())
  sorts.value = [...clearedCriteria.sorts]
  filters.value = [...clearedCriteria.filters]
  page.value = 0
  void loadBlock()
}

function changePage(next: number): void {
  const currentResponse = displayedResponse.value
  if (!currentResponse || next < 0 || next >= currentResponse.totalPages) return
  page.value = next
  void loadBlock()
}

async function exportCurrent(): Promise<void> {
  const api = props.api
  const reportDownload = props.reportDownload
  const format = activeFormat.value
  if (api === null || format === null || props.offline || exporting.value || !canView.value) return
  const currentRevision = revision
  const query = queryForCurrentBlock(false)
  const querySnapshot = JSON.stringify(query)
  exporting.value = true
  error.value = null
  exportStatus.value = null
  try {
    if (reportDownload) {
      const isCurrent = (): boolean => currentRevision === revision
        && api === props.api && reportDownload === props.reportDownload
        && !props.offline && canView.value && canExport.value
        && selectedFormat.value === format.code
        && JSON.stringify(queryForCurrentBlock(false)) === querySnapshot
      const result = await reportDownload.download(toHeadmanStatsReportRequest(query, format.code), isCurrent)
      if (result === 'stale' || !isCurrent()) return
      if (result === 'unsupported') {
        error.value = 'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.'
      } else {
        exportStatus.value = result === 'accepted'
          ? 'Telegram принял запрос на скачивание; проверь завершение в Telegram.'
          : 'Скачивание отменено.'
      }
      return
    }

    const downloaded = await api.downloadExport(query, format)
    if (currentRevision !== revision || api !== props.api) return
    const url = URL.createObjectURL(downloaded.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = downloaded.filename
    anchor.rel = 'noopener'
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 0)
  } catch (cause) {
    if (currentRevision !== revision || cause instanceof StaleSessionGenerationError) return
    denied.value = cause instanceof HeadmanStatsApiError && cause.response.status === 403
    if (denied.value) response.value = null
    error.value = cause instanceof Error ? cause.message : 'Не удалось скачать статистику.'
    emit('error', cause)
  } finally {
    exporting.value = false
  }
}

async function exportTrend(formatCode: HeadmanStatsTrendFormat['code']): Promise<void> {
  const api = props.api
  const reportDownload = props.reportDownload
  const query = currentTrendQuery.value
  const trend = displayedTrend.value
  const format = trend?.formats.find((item) => item.code === formatCode)
  const key = currentTrendKey.value
  if (api === null || query === null || trend === null || format === undefined || key === null
    || props.offline || exporting.value || !canView.value) return
  const querySnapshot = JSON.stringify(query)
  const currentTrendRevision = trendRevision
  exporting.value = true
  trendExportStatus.value = null
  trendExportError.value = null
  try {
    if (reportDownload) {
      const isCurrent = (): boolean => currentTrendRevision === trendRevision
        && api === props.api && reportDownload === props.reportDownload
        && !props.offline && canView.value && !denied.value
        && key === currentTrendKey.value
        && JSON.stringify(currentTrendQuery.value) === querySnapshot
      const result = await reportDownload.download(toHeadmanStatsTrendReportRequest(query, format.code), isCurrent)
      if (result === 'stale' || !isCurrent()) return
      if (result === 'unsupported') {
        trendExportError.value = 'Скачивание файлов недоступно в этой версии Telegram. Обнови Telegram до версии 8.0 или новее.'
      } else {
        trendExportStatus.value = result === 'accepted'
          ? 'Telegram принял запрос на скачивание графика; проверь завершение в Telegram.'
          : 'Скачивание графика отменено.'
      }
      return
    }

    const downloaded = await api.downloadTrendExport(query, format)
    if (currentTrendRevision !== trendRevision || api !== props.api
      || props.offline || !canView.value || denied.value
      || key !== currentTrendKey.value || JSON.stringify(currentTrendQuery.value) !== querySnapshot) return
    const url = URL.createObjectURL(downloaded.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = downloaded.filename
    anchor.rel = 'noopener'
    document.body.append(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 0)
    trendExportStatus.value = `График скачан: ${downloaded.filename}`
  } catch (cause) {
    if (cause instanceof StaleSessionGenerationError) return
    if (cause instanceof HeadmanStatsApiError && cause.response.status === 403) {
      clearOnDenied()
      return
    }
    trendExportError.value = cause instanceof Error ? cause.message : 'Не удалось скачать график.'
    emit('error', cause)
  } finally {
    exporting.value = false
  }
}

function formatGeneratedAt(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat('ru-RU', {
    dateStyle: 'short', timeStyle: 'short', timeZone: 'Europe/Moscow',
  }).format(date) + ' МСК'
}

function emptyStateLabel(): string | null {
  switch (displayedResponse.value?.emptyState) {
    case 'NO_ACTIVE_SEMESTER': return 'Нет активного семестра.'
    case 'NO_COMPLETED_LESSONS': return 'В активном семестре ещё нет завершённых занятий.'
    case 'NO_MEMBERS': return 'В группе пока нет участников.'
    case 'FILTERED_EMPTY': return 'По выбранным фильтрам данных нет.'
    default: return null
  }
}

function trendEmptyStateLabel(): string | null {
  switch (displayedTrend.value?.emptyState) {
    case 'NO_ACTIVE_SEMESTER': return 'Нет активного семестра.'
    case 'NO_COMPLETED_LESSONS': return 'В активном семестре ещё нет завершённых занятий.'
    case 'NO_MATCHING_LESSONS': return 'Нет занятий, соответствующих выбранному предмету и типам.'
    default: return null
  }
}

watch(() => [props.api, props.groupId, props.offline, props.reportDownload, canView.value] as const, () => {
  void loadInitial()
}, { immediate: true })

onBeforeUnmount(() => {
  revision += 1
  activeController?.abort()
  resetTrendRequest()
  clearDetail()
})
</script>

<template>
  <main
    class="headman-stats"
    aria-labelledby="headman-stats-title"
  >
    <header class="headman-stats__header">
      <div>
        <p class="headman-stats__eyebrow">
          Староста · статистика
        </p>
        <h1 id="headman-stats-title">
          Посещаемость группы
        </h1>
        <p
          v-if="displayedResponse"
          class="headman-stats__context"
        >
          {{ displayedResponse.context.groupName }}<span v-if="displayedResponse.context.semesterName"> · {{ displayedResponse.context.semesterName }}</span>
        </p>
      </div>
      <button
        class="headman-stats__back"
        type="button"
        @click="emit('back')"
      >
        Назад
      </button>
    </header>

    <p
      v-if="stateMessage"
      class="headman-stats__state"
      role="status"
    >
      {{ stateMessage }}
    </p>
    <div
      v-if="error"
      class="headman-stats__state headman-stats__state--error"
      role="alert"
    >
      <span>{{ error }}</span>
      <button
        v-if="!denied && canView && !offline"
        type="button"
        :disabled="loading"
        @click="loadBlock"
      >
        Повторить
      </button>
    </div>
    <p
      v-if="loading"
      class="headman-stats__state"
      role="status"
      aria-live="polite"
    >
      Загружаем статистику группы…
    </p>

    <template v-if="response && !props.offline && canView">
      <nav
        class="headman-stats__blocks"
        aria-label="Раздел статистики"
      >
        <button
          type="button"
          :aria-pressed="block === 'group'"
          @click="openBlock('group')"
        >
          Вся группа
        </button>
        <button
          type="button"
          :aria-pressed="block === 'subject'"
          @click="openBlock('subject')"
        >
          По предмету и типу
        </button>
      </nav>

      <section
        class="headman-stats__section"
        aria-label="Параметры статистики"
      >
        <label
          v-if="block === 'subject'"
          class="headman-stats__field"
        >
          <span>Предмет</span>
          <select
            :value="selectedSubjectId ?? ''"
            @change="selectSubject(($event.target as HTMLSelectElement).value)"
          >
            <option
              value=""
              disabled
            >Выбери предмет</option>
            <option
              v-for="subject in response.subjects"
              :key="subject.id"
              :value="subject.id"
            >{{ subject.label }}</option>
          </select>
        </label>
        <fieldset
          v-if="block === 'subject' && selectedSubject"
          class="headman-stats__types"
        >
          <legend>Типы занятий</legend>
          <label
            v-for="type in selectedSubject.lessonTypes"
            :key="type.code"
          >
            <input
              v-model="selectedTypes"
              type="checkbox"
              :value="type.code"
              @change="page = 0; loadBlock()"
            >
            {{ type.label }}
          </label>
        </fieldset>
        <div
          v-if="displayedResponse"
          class="headman-stats__export"
        >
          <label class="headman-stats__field">
            <span>Формат выгрузки</span>
            <select v-model="selectedFormat">
              <option
                v-for="format in displayedResponse.formats"
                :key="format.code"
                :value="format.code"
              >{{ format.label }}</option>
            </select>
          </label>
          <button
            type="button"
            :disabled="offline || loading || exporting || !canExport"
            @click="exportCurrent"
          >
            {{ exporting ? 'Готовим файл…' : 'Скачать этот блок' }}
          </button>
        </div>
        <p
          v-if="exportStatus"
          class="headman-stats__state"
          role="status"
        >
          {{ exportStatus }}
        </p>
      </section>

      <template v-if="displayedResponse">
        <section
          class="headman-stats__summary"
          aria-label="Сводные показатели после фильтров"
        >
          <article><h2>«+»</h2><p>{{ metricValue(displayedResponse.summary, 'present') }}</p></article>
          <article><h2>«+ и у»</h2><p>{{ metricValue(displayedResponse.summary, 'presentOrExcused') }}</p></article>
          <article><h2>«у»</h2><p>{{ metricValue(displayedResponse.summary, 'excused') }}</p></article>
          <article><h2>«н»</h2><p>{{ metricValue(displayedResponse.summary, 'absent') }}</p></article>
        </section>
      </template>

      <section
        class="headman-stats__trend-section"
        aria-labelledby="headman-stats-trend-title"
      >
        <div class="headman-stats__table-heading">
          <div>
            <h2 id="headman-stats-trend-title">
              Динамика посещаемости
            </h2>
            <p>Данные сервера, проценты по завершённым занятиям.</p>
          </div>
        </div>
        <nav
          class="headman-stats__blocks"
          aria-label="Период динамики"
        >
          <button
            type="button"
            :aria-pressed="trendMode === 'SEMESTER'"
            @click="setTrendMode('SEMESTER')"
          >
            По семестру
          </button>
          <button
            type="button"
            :aria-pressed="trendMode === 'WEEK'"
            @click="setTrendMode('WEEK')"
          >
            По неделе
          </button>
          <button
            type="button"
            :aria-pressed="trendMode === 'SUBJECT'"
            @click="setTrendMode('SUBJECT')"
          >
            По предмету
          </button>
        </nav>
        <div
          v-if="trendMode === 'WEEK'"
          class="headman-stats__trend-controls"
        >
          <button
            type="button"
            :disabled="!canShiftTrendWeek(-1) || trendLoading"
            @click="shiftTrendWeek(-1)"
          >
            Предыдущая неделя
          </button>
          <label class="headman-stats__field">
            <span>Неделя, выбери любую дату</span>
            <input
              type="date"
              :value="trendWeekStart"
              :min="semesterWeekMin()"
              :max="response?.context.semesterTo ?? undefined"
              @change="selectTrendWeek(($event.target as HTMLInputElement).value)"
            >
          </label>
          <button
            type="button"
            :disabled="!canShiftTrendWeek(1) || trendLoading"
            @click="shiftTrendWeek(1)"
          >
            Следующая неделя
          </button>
        </div>
        <div
          v-if="trendMode === 'SUBJECT'"
          class="headman-stats__trend-controls"
        >
          <label class="headman-stats__field">
            <span>Предмет для динамики</span>
            <select
              :value="trendSubjectId ?? ''"
              @change="selectTrendSubject(($event.target as HTMLSelectElement).value)"
            >
              <option value="">Выбери предмет</option>
              <option
                v-for="subject in response.subjects"
                :key="subject.id"
                :value="subject.id"
              >{{ subject.label }}</option>
            </select>
          </label>
          <fieldset
            v-if="selectedTrendSubject"
            class="headman-stats__types"
          >
            <legend>Типы занятий</legend>
            <label
              v-for="type in selectedTrendSubject.lessonTypes"
              :key="type.code"
            >
              <input
                v-model="trendLessonTypes"
                type="checkbox"
                :value="type.code"
                @change="loadTrend"
              >
              {{ type.label }}
            </label>
          </fieldset>
        </div>
        <p
          v-if="trendLoading"
          class="headman-stats__state"
          role="status"
          aria-live="polite"
        >
          Загружаем динамику…
        </p>
        <div
          v-if="trendError"
          class="headman-stats__state headman-stats__state--error"
          role="alert"
        >
          <span>{{ trendError }}</span>
          <button
            type="button"
            :disabled="trendLoading || offline"
            @click="loadTrend"
          >
            Повторить
          </button>
        </div>
        <p
          v-if="trendEmptyStateLabel()"
          class="headman-stats__state"
          role="status"
        >
          {{ trendEmptyStateLabel() }}
        </p>
        <HeadmanStatsTrendChart
          v-if="displayedTrend"
          :points="displayedTrend.points"
        />
        <div
          v-if="displayedTrend"
          class="headman-stats__trend-controls"
          aria-label="Скачать график динамики"
          role="group"
        >
          <button
            v-for="format in displayedTrend.formats"
            :key="format.code"
            type="button"
            :disabled="exporting || offline || !canView"
            @click="exportTrend(format.code)"
          >
            Скачать {{ format.label }}
          </button>
        </div>
        <p
          v-if="trendExportStatus"
          class="headman-stats__state"
          role="status"
        >
          {{ trendExportStatus }}
        </p>
        <p
          v-if="trendExportError"
          class="headman-stats__state headman-stats__state--error"
          role="alert"
        >
          {{ trendExportError }}
        </p>
      </section>

      <details
        v-if="controlResponse"
        class="headman-stats__filters"
      >
        <summary>Фильтры столбцов</summary>
        <div class="headman-stats__filter-grid">
          <fieldset
            v-for="column in controlResponse.columns"
            :key="column.field"
          >
            <legend>{{ column.label }}</legend>
            <input
              v-if="column.filterKind === 'TEXT'"
              v-model="filterDraft(column).contains"
              :aria-label="`Фильтр: ${column.label}`"
              type="search"
              placeholder="Содержит"
            >
            <template v-else>
              <input
                v-model="filterDraft(column).minimum"
                :aria-label="`От: ${column.label}`"
                type="number"
                min="0"
                step="any"
                placeholder="От"
              >
              <input
                v-model="filterDraft(column).maximum"
                :aria-label="`До: ${column.label}`"
                type="number"
                min="0"
                step="any"
                placeholder="До"
              >
            </template>
          </fieldset>
        </div>
        <div class="headman-stats__filter-actions">
          <button
            type="button"
            :disabled="loading"
            @click="applyFilters"
          >
            Применить фильтры
          </button>
          <button
            type="button"
            :disabled="loading || !canResetCriteria"
            @click="resetCriteria"
          >
            Сбросить
          </button>
          <p>Фильтры применяются на сервере ко всему набору студентов.</p>
        </div>
      </details>

      <template v-if="displayedResponse">
        <section
          class="headman-stats__table-section"
          aria-labelledby="headman-stats-table-title"
        >
          <div class="headman-stats__table-heading">
            <div>
              <h2 id="headman-stats-table-title">
                {{ block === 'group' ? 'Вся группа' : `Предмет: ${selectedSubject?.label ?? ''}` }}
              </h2>
              <p>{{ displayedResponse.filteredStudents }} студентов · {{ displayedResponse.context.lessonsCount }} завершённых занятий · сформировано {{ formatGeneratedAt(displayedResponse.context.generatedAt) }}</p>
            </div>
            <p v-if="sorts.length > 1">
              Для сортировки выбери столбцы с Shift; номер задаёт приоритет.
            </p>
          </div>
          <p
            v-if="emptyStateLabel()"
            class="headman-stats__state"
            role="status"
          >
            {{ emptyStateLabel() }}
          </p>
          <div
            v-if="displayedResponse.rows.length > 0"
            class="headman-stats__table-wrap"
            tabindex="0"
            aria-label="Таблица статистики, прокручивается по горизонтали"
          >
            <table>
              <thead>
                <tr>
                  <th
                    v-for="column in displayedResponse.columns"
                    :key="column.field"
                    scope="col"
                  >
                    <button
                      type="button"
                      class="headman-stats__sort"
                      @click="toggleSort(column.field, $event.shiftKey)"
                    >
                      {{ column.label }} <span aria-hidden="true">{{ sortIndicator(column.field) }}</span>
                    </button>
                  </th>
                  <th scope="col">
                    Подробности
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in displayedResponse.rows"
                  :key="row.studentId"
                >
                  <td
                    v-for="column in displayedResponse.columns"
                    :key="column.field"
                  >
                    {{ cellValue(row, column.field) }}
                  </td>
                  <td>
                    <button
                      type="button"
                      class="headman-stats__detail-action"
                      :aria-expanded="detailStudentId === row.studentId"
                      :aria-label="`${detailStudentId === row.studentId ? 'Закрыть' : 'Открыть'} подробности студента ${row.displayName || row.studentId}`"
                      @click="openStudentDetail(row.studentId, $event)"
                    >
                      {{ detailStudentId === row.studentId ? 'Открыто' : 'Подробности' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <nav
            v-if="displayedResponse.totalPages > 1"
            class="headman-stats__pagination"
            aria-label="Страницы таблицы"
          >
            <button
              type="button"
              :disabled="!displayedResponse.hasPrevious || loading"
              @click="changePage(page - 1)"
            >
              Предыдущая
            </button>
            <span>Страница {{ displayedResponse.page + 1 }} из {{ displayedResponse.totalPages }}</span>
            <button
              type="button"
              :disabled="!displayedResponse.hasNext || loading"
              @click="changePage(page + 1)"
            >
              Следующая
            </button>
          </nav>
        </section>
      </template>
      <HeadmanStatsDetailPanel
        v-if="detailStudentId !== null"
        ref="detailPanelRef"
        :student-id="detailStudentId"
        :detail="displayedDetail"
        :loading="detailLoading"
        :error="detailError"
        :offline="offline"
        @close="clearDetail(true)"
        @retry="loadStudentDetail"
        @page="changeDetailPage"
      />
    </template>
  </main>
</template>
