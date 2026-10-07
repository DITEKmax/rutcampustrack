<script setup lang="ts">
import MobileToolsSheet from '../../shared/components/MobileToolsSheet.vue'
import MobileIcon from '../../shared/components/MobileIcon.vue'
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue'

import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import HeadmanStatsTrendChart from './HeadmanStatsTrendChart.vue'
import HeadmanStatsSelect from './HeadmanStatsSelect.vue'
import HeadmanStatsMobileView from './HeadmanStatsMobileView.vue'
import HeadmanStatsHistory from './HeadmanStatsHistory.vue'
import type { HeadmanJournalApi } from '../headman-journal/headman-journal-client'
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client'
import type { StatisticsGraphRange } from '../statistics/statistics-view-model'
import type { HeadmanStatsSemesterCatalog, HeadmanStatsOverview } from './headman-stats-history-client'
import {
  HeadmanStatsApiError,
  headmanStatsResponseForCurrentQuery,
  headmanStatsQueryKey,
  headmanStatsQueryScopeKey,
  toHeadmanStatsReportRequest,
  toHeadmanStatsTrendReportRequest,
  type HeadmanStatsApi,
  type HeadmanStatsBlock,
  type HeadmanStatsColumn,
  type HeadmanStatsFilter,
  type HeadmanStatsFormat,
  type HeadmanStatsResponse,
  type HeadmanStatsSort,
  type HeadmanStatsStudentDetailResponse,
  type HeadmanStatsTrendMode,
  type HeadmanStatsTrendFormat,
  type HeadmanStatsTrendQuery,
  type HeadmanStatsTrendResponse,
} from './headman-stats-client'
import './headman-stats-screen.pcss'

interface FilterDraft { contains: string; minimum: string; maximum: string }

const statsToolsOpen = ref(false)
const statsTool = ref<'filters' | 'sort' | 'export'>('filters')
const analyticsPage = ref<'trend' | 'tickets' | null>(null)
const selectedTrendFormat = ref<HeadmanStatsTrendFormat['code'] | ''>('')
const filterBlock = ref<HeadmanStatsBlock>('group')
const filterSubjectId = ref('')
const filterTypes = ref<string[]>([])
const personalSubjectId = ref('')
const personalTypes = ref<string[]>([])
const statsToolTitles = { filters: 'Фильтры статистики', sort: 'Сортировка', export: 'Выгрузка', tickets: 'Тикеты студентов' }
const props = withDefaults(defineProps<{
  api: HeadmanStatsApi | null
  groupId: number | null
  assistantPermissions?: readonly string[] | null
  offline?: boolean
  reportDownload?: ReportDownloadPort | null
  showBack?: boolean
  journalApi?: HeadmanJournalApi | null
  requestsApi?: HeadmanRequestsApi | null
}>(), {
  assistantPermissions: null,
  offline: false,
  reportDownload: null,
  showBack: true,
  journalApi: null,
  requestsApi: null,
})

const emit = defineEmits<{ error: [cause: unknown]; back: []; 'view-change': [view: 'overview' | 'detail' | 'history' | 'trend' | 'tickets' | 'subject'] }>()

const block = ref<HeadmanStatsBlock>('group')
const semesterId = ref<number | null>(null)
const semesterCatalog = ref<HeadmanStatsSemesterCatalog | null>(null)
const overview = ref<HeadmanStatsOverview | null>(null)
const metadataError = ref<string | null>(null)
let overviewRevision = 0
let overviewController: AbortController | null = null
const chartRange = ref<StatisticsGraphRange>('weeks')
const personalTrend = ref<HeadmanStatsTrendResponse | null>(null)
const personalTrendLoading = ref(false)
const personalTrendError = ref<string | null>(null)
let personalTrendRevision = 0
let personalTrendController: AbortController | null = null
const response = ref<HeadmanStatsResponse | null>(null)
const responseQueryKey = ref<string | null>(null)
const responseScopeKey = ref<string | null>(null)
const selectedSubjectId = ref<number | null>(null)
const selectedTypes = ref<string[]>([])
const searchText = ref('')
const typesEmpty = computed(() => block.value === 'subject' && selectedTypes.value.length === 0)
const sorts = ref<HeadmanStatsSort[]>([{ field: 'displayName', descending: false }])
const filters = ref<HeadmanStatsFilter[]>([])
const filterDrafts = reactive<Record<string, FilterDraft>>({})
const selectedFormat = ref<HeadmanStatsFormat['code'] | ''>('')
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
const historyOpen = ref(false)
const historyRef = ref<{ ready: boolean; back: () => boolean } | null>(null)
const detailResponse = ref<HeadmanStatsStudentDetailResponse | null>(null)
const detailResponseKey = ref<string | null>(null)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const latePage = ref(0)
const excusePage = ref(0)
const detailReturnFocus = ref<HTMLButtonElement | null>(null)
const mobileViewRef = ref<{ focus: () => void } | null>(null)
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
  const period = { ...(semesterId.value === null ? {} : { semesterId: semesterId.value }), bucketUnit: chartRange.value === 'days' ? 'DAY' as const : 'WEEK' as const }
  if (typesEmpty.value && analyticsPage.value !== 'trend') return null
  if (block.value === 'subject' && analyticsPage.value !== 'trend') return selectedSubjectId.value === null ? null : { mode: 'SUBJECT', subjectId: selectedSubjectId.value, lessonTypes: [...selectedTypes.value], ...period }
  if (trendMode.value === 'SEMESTER' || analyticsPage.value !== 'trend') return { mode: 'SEMESTER', ...period }
  if (trendMode.value === 'WEEK') return trendWeekStart.value ? { mode: 'WEEK', weekStart: trendWeekStart.value, ...period, bucketUnit: 'DAY' } : null
  if (trendSubjectId.value === null) return null
  return {
    mode: 'SUBJECT',
    ...period,
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
  : JSON.stringify({ groupId: props.groupId, studentId: detailStudentId.value, semesterId: semesterId.value, latePage: latePage.value, excusePage: excusePage.value, size: 20 }))
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
const selectedTrendSubject = computed(() => response.value?.subjects.find((item) => item.id === trendSubjectId.value) ?? null)
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

function queryForCurrentBlock(includePaging: boolean): {
  semesterId?: number
  subjectId: number | null
  lessonTypes: readonly string[]
  sorts: readonly HeadmanStatsSort[]
  filters: readonly HeadmanStatsFilter[]
  page?: number
  size?: number
} {
  return {
    ...(semesterId.value === null ? {} : { semesterId: semesterId.value }),
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
  historyOpen.value = false
  personalSubjectId.value = ''; personalTypes.value = []
  personalTrendRevision += 1
  personalTrendController?.abort()
  personalTrend.value = null
  personalTrendLoading.value = false
  personalTrendError.value = null
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
  overviewRevision += 1
  overviewController?.abort()
  overview.value = null
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
      emit('error', cause)
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
  void nextTick(() => mobileViewRef.value?.focus())
  void loadStudentDetail()
  void loadPersonalTrend()
}

async function loadPersonalTrend(): Promise<void> {
  const api = props.api; const group = props.groupId; const student = detailStudentId.value
  const period = { ...(semesterId.value === null ? {} : { semesterId: semesterId.value }), bucketUnit: chartRange.value === 'days' ? 'DAY' as const : 'WEEK' as const }
  const query: HeadmanStatsTrendQuery = personalSubjectId.value ? { mode: 'SUBJECT', subjectId: Number(personalSubjectId.value), ...(personalTypes.value.length ? { lessonTypes: [...personalTypes.value] } : {}), ...period } : { mode: 'SEMESTER', ...period }
  if (!api || !group || student === null || props.offline || !canView.value || denied.value) return
  const generation = ++personalTrendRevision; personalTrendController?.abort(); const controller = new AbortController(); personalTrendController = controller
  personalTrend.value = null; personalTrendLoading.value = true; personalTrendError.value = null
  const current = () => generation === personalTrendRevision && !controller.signal.aborted && api === props.api && group === props.groupId && student === detailStudentId.value && !props.offline && canView.value && !denied.value
  try { const result = await api.studentTrend(student, query, controller.signal); if (!current()) return; if (result.context.groupId !== group || result.context.semesterId !== response.value?.context.semesterId) throw new Error('Сервер вернул статистику другого семестра.'); personalTrend.value = result }
  catch (cause) { if (!current() || cause instanceof StaleSessionGenerationError) return; if (cause instanceof HeadmanStatsApiError && cause.response.status === 403) { clearOnDenied(); emit('error', cause); return }; personalTrendError.value = cause instanceof Error ? cause.message : 'Не удалось загрузить график студента.'; emit('error', cause) }
  finally { if (generation === personalTrendRevision) personalTrendLoading.value = false }
}

function setChartRange(range: StatisticsGraphRange): void { chartRange.value = range; if (detailStudentId.value === null) void loadTrend(); else void loadPersonalTrend() }
function selectSemester(value: string): void {
  const next = Number(value)
  if (!semesterCatalog.value?.semesters.some((semester) => semester.id === next) || semesterId.value === next) return
  semesterId.value = next; page.value = 0; clearDetail(); resetTrendRequest(); void loadBlock()
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
  const query = { latePage: latePage.value, excusePage: excusePage.value, size: 20, ...(semesterId.value === null ? {} : { semesterId: semesterId.value }) }
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
      emit('error', cause)
      return
    }
    detailError.value = cause instanceof Error ? cause.message : 'Не удалось загрузить подробности студента.'
    emit('error', cause)
  } finally {
    if (currentRevision === detailRevision) detailLoading.value = false
  }
}

async function loadInitial(): Promise<void> {
  analyticsPage.value = null; statsToolsOpen.value = false
  overviewRevision += 1
  overviewController?.abort()
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
  semesterId.value = null
  semesterCatalog.value = null
  overview.value = null
  metadataError.value = null
  if (props.offline || props.api === null || props.groupId === null || !canView.value) return
  block.value = 'group'
  sorts.value = [{ field: 'displayName', descending: false }]
  filters.value = []
  page.value = 0
  const api = props.api; const group = props.groupId; const generation = revision
  const metadata = await Promise.allSettled([api.semesters(), api.overview()])
  if (generation !== revision || api !== props.api || group !== props.groupId || props.offline || !canView.value) return
  const [catalogResult, overviewResult] = metadata
  if (catalogResult.status === 'fulfilled' && catalogResult.value.groupId === group) { semesterCatalog.value = catalogResult.value; semesterId.value = catalogResult.value.activeSemesterId }
  else metadataError.value = 'Не удалось загрузить список семестров. Показан текущий семестр.'
  if (overviewResult.status === 'fulfilled' && overviewResult.value.groupId === group) overview.value = overviewResult.value
  else metadataError.value = (metadataError.value ? metadataError.value + ' ' : '') + 'Состав группы и показатели за сегодня не загружены.'
  await loadBlock()
}

async function loadBlock(keepDetail = false): Promise<void> {
  const api = props.api
  if (api === null || props.groupId === null || props.offline || !canView.value) return
  const catalogOnly = block.value === 'subject' && selectedSubjectId.value === null
  if (typesEmpty.value && !catalogOnly) {
    revision += 1
    activeController?.abort()
    responseQueryKey.value = null
    loading.value = false
    exportStatus.value = null
    error.value = null
    resetTrendRequest()
    return
  }
  if (detailStudentId.value !== null && !keepDetail) clearDetail()
  // Catalog metadata is supplied by a supported whole-group query. Keep the
  // subject catalog open and never expose these rows as a selected subject.
  const query = catalogOnly
    ? { ...(semesterId.value === null ? {} : { semesterId: semesterId.value }), subjectId: null, lessonTypes: [], sorts: [{ field: 'displayName', descending: false }], filters: [], page: 0, size: pageSize }
    : { ...queryForCurrentBlock(true), page: page.value, size: pageSize }
  const queryKey = headmanStatsQueryKey(query)
  const queryScopeKey = headmanStatsQueryScopeKey(block.value, query)
  const currentRevision = ++revision
  activeController?.abort()
  const controller = new AbortController()
  activeController = controller
  loading.value = true
  if (catalogOnly) { response.value = null; responseScopeKey.value = null }
  responseQueryKey.value = null
  exportStatus.value = null
  error.value = null
  denied.value = false
  try {
    const result = await api.query(query, controller.signal)
    if (currentRevision !== revision || controller.signal.aborted || api !== props.api || props.offline || !canView.value) return
    if (result.context.groupId !== props.groupId) throw new Error('Сервер вернул статистику для другой группы.')
    if (semesterId.value !== null && result.context.semesterId !== semesterId.value) throw new Error('Сервер вернул статистику другого семестра.')
    response.value = result
    responseQueryKey.value = catalogOnly ? null : queryKey
    responseScopeKey.value = queryScopeKey
    for (const column of result.columns) filterDraft(column)
    initializeTrendControls(result)
    if (!result.formats.some((format) => format.code === selectedFormat.value)) selectedFormat.value = ''
    if (!catalogOnly) void loadTrend()
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
  if (next === block.value && !(next === 'subject' && selectedSubjectId.value !== null)) return
  clearDetail(); analyticsPage.value = null; block.value = next; page.value = 0
  selectedSubjectId.value = null; selectedTypes.value = []
  sorts.value = [{ field: 'displayName', descending: false }]; filters.value = []; error.value = null
  for (const draft of Object.values(filterDrafts)) Object.assign(draft, newFilterDraft())
  if (next === 'group') void loadBlock()
  else { responseQueryKey.value = null; resetTrendRequest() }
}

function selectSubject(value: string): void {
  selectedSubjectId.value = value ? Number(value) : null
  selectedTypes.value = response.value?.subjects.find((item) => item.id === selectedSubjectId.value)?.lessonTypes.map((item) => item.code) ?? []
  page.value = 0
  sorts.value = [{ field: 'displayName', descending: false }]
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

function setMobileTypes(types: readonly string[]): void {
  selectedTypes.value = [...types]
  page.value = 0
  void loadBlock()
}

function applyMobileSearch(): void {
  const column = controlResponse.value?.columns.find((item) => item.field === 'displayName' && item.filterKind === 'TEXT')
  if (!column) return
  filterDraft(column).contains = searchText.value
  applyFilters()
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
    if (cause instanceof HeadmanStatsApiError && cause.response.status === 403) clearOnDenied()
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
      emit('error', cause)
      return
    }
    trendExportError.value = cause instanceof Error ? cause.message : 'Не удалось скачать график.'
    emit('error', cause)
  } finally {
    exporting.value = false
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

watch([detailStudentId, historyOpen, analyticsPage, block, selectedSubjectId], ([value, history, analytics, activeBlock, subject]) => { emit('view-change', analytics ?? (value !== null ? history ? 'history' : 'detail' : activeBlock === 'subject' && subject !== null ? 'subject' : 'overview')) }, { immediate: true })
function back(): boolean {
  if (statsToolsOpen.value) { statsToolsOpen.value = false; return true }
  if (analyticsPage.value) { analyticsPage.value = null; void loadTrend(); return true }
  if (detailStudentId.value === null) { if (block.value === 'subject' && selectedSubjectId.value !== null) { openBlock('subject'); return true }; return false }
  if (historyOpen.value) { if (historyRef.value?.back()) return true; historyOpen.value = false; void nextTick(() => mobileViewRef.value?.focus()); return true }
  clearDetail(true)
  return true
}
function openHistory(): void { if (detailStudentId.value !== null) { historyOpen.value = true; void nextTick(() => mobileViewRef.value?.focus()) } }
async function loadOverview(): Promise<void> {
  const api = props.api; const group = props.groupId; const semester = semesterId.value; const generation = revision
  if (!api || group === null || props.offline || !canView.value || denied.value) return
  const request = ++overviewRevision; overviewController?.abort(); const controller = new AbortController(); overviewController = controller
  overview.value = null
  const current = () => request === overviewRevision && generation === revision && !controller.signal.aborted && api === props.api && group === props.groupId && semester === semesterId.value && !props.offline && canView.value && !denied.value
  try { const result = await api.overview(controller.signal); if (!current()) return; if (result.groupId !== group) throw new Error('Сервер вернул обзор другой группы.'); overview.value = result }
  catch (cause) { if (!current() || cause instanceof StaleSessionGenerationError) return; if (cause instanceof HeadmanStatsApiError && cause.response.status === 403) clearOnDenied(); metadataError.value = cause instanceof Error ? cause.message : 'Не удалось обновить показатели за сегодня.'; emit('error', cause) }
}
function refreshedHistory(): void { void loadBlock(true); void loadStudentDetail(); void loadPersonalTrend(); void loadOverview() }


const sortPresets = computed(() => [
  { value: 'displayName:asc', label: 'По алфавиту · А–Я' },
  ...(['present', 'excused', 'absent', 'presentOrExcused'] as const).flatMap((kind) => ['Count', 'Percent'].flatMap((unit) => [false, true].map((descending) => ({ value: `${kind}${unit}:${descending ? 'desc' : 'asc'}`, label: `${{present:'«+»',excused:'«у»',absent:'«н»',presentOrExcused:'Посещаемость «+ и у»'}[kind]} · ${unit === 'Count' ? 'количество' : 'процент'} · ${descending ? 'по убыванию' : 'по возрастанию'}` })))),
])
const sortPreset = computed(() => `${sorts.value[0]?.field ?? 'displayName'}:${sorts.value[0]?.descending ? 'desc' : 'asc'}`)
const visibleTools = computed(() => (['filters','sort','export','trend','tickets'] as const).filter(tool => !(detailStudentId.value !== null && (tool === 'sort' || tool === 'export' || tool === 'trend')) && !(subjectCatalog.value && (tool === 'export' || tool === 'tickets'))))
const subjectCatalog = computed(() => block.value === 'subject' && selectedSubjectId.value === null && detailStudentId.value === null)
function chooseSort(value: string): void {
  if (!sortPresets.value.some((item) => item.value === value)) return
  const [field, direction] = value.split(':'); sorts.value = [{ field: field!, descending: direction === 'desc' }]; page.value = 0; statsToolsOpen.value = false; void loadBlock()
}
function openTool(tool: 'filters' | 'sort' | 'export' | 'trend' | 'tickets'): void {
  if (tool === 'trend' || tool === 'tickets') { analyticsPage.value = tool; if (tool === 'trend') void loadTrend(); return }
  statsTool.value = tool
  filterBlock.value = detailStudentId.value !== null ? personalSubjectId.value ? 'subject' : 'group' : block.value; filterSubjectId.value = detailStudentId.value !== null ? personalSubjectId.value || String(response.value?.subjects[0]?.id ?? '') : String(selectedSubjectId.value ?? response.value?.subjects[0]?.id ?? ''); filterTypes.value = detailStudentId.value !== null ? [...personalTypes.value] : [...selectedTypes.value]
  statsToolsOpen.value = true
}
function applyToolFilters(): void {
  if (detailStudentId.value !== null) { personalSubjectId.value = filterBlock.value === 'subject' ? filterSubjectId.value : ''; personalTypes.value = filterBlock.value === 'subject' ? [...filterTypes.value] : []; statsToolsOpen.value = false; void loadPersonalTrend(); return }
  block.value = filterBlock.value; selectedSubjectId.value = filterBlock.value === 'subject' ? Number(filterSubjectId.value) || null : null; selectedTypes.value = filterBlock.value === 'subject' ? [...filterTypes.value] : []
  filters.value = visibleFilters(); page.value = 0; statsToolsOpen.value = false; if (block.value === 'group' || selectedSubjectId.value !== null) void loadBlock()
}
function resetToolFilters(): void {
  filterBlock.value = 'group'; filterSubjectId.value = String(response.value?.subjects[0]?.id ?? ''); filterTypes.value = []
  for (const draft of Object.values(filterDrafts)) Object.assign(draft, newFilterDraft())
}
function changeFilterSubject(value: string): void { filterSubjectId.value = value; filterTypes.value = response.value?.subjects.find((item) => item.id === Number(value))?.lessonTypes.map((item) => item.code) ?? [] }
const ticketCounters = computed(() => detailStudentId.value !== null ? displayedDetail.value?.lateCheckin && displayedDetail.value.excuse ? { lateCheckin: displayedDetail.value.lateCheckin, excuse: displayedDetail.value.excuse } : null : displayedResponse.value?.summaryTickets ?? null)
function ticketRatio(count: number, submitted: number): string { return submitted ? `${(count / submitted * 100).toFixed(1)}%` : '—' }

onBeforeUnmount(() => {
  overviewRevision += 1
  overviewController?.abort()
  revision += 1
  activeController?.abort()
  resetTrendRequest()
  clearDetail()
})

const ready = computed(() => {
  if (analyticsPage.value === 'tickets') return detailStudentId.value !== null ? displayedDetail.value !== null : displayedResponse.value !== null
  if (analyticsPage.value === 'trend') return displayedTrend.value !== null
  if (detailStudentId.value !== null) return historyOpen.value ? Boolean(historyRef.value?.ready) : displayedDetail.value !== null && personalTrend.value !== null
  return subjectCatalog.value || typesEmpty.value ? response.value !== null : displayedResponse.value !== null && displayedTrend.value !== null
})
defineExpose({ catalogReady: computed(() => response.value !== null), ready, openBlock, selectSubject, openTool, openStudentDetail, clearDetail, back, openHistory, setChartRange, setMobileTypes })
</script>

<template>
  <main
    class="headman-stats"
    aria-labelledby="headman-stats-title"
  >
    <HeadmanStatsMobileView
      v-if="analyticsPage === null"
      ref="mobileViewRef"
      :response="displayedResponse"
      :catalog="response"
      :block="block"
      :subject-id="selectedSubjectId"
      :selected-types="selectedTypes"
      :types-empty="typesEmpty"
      :trend="displayedTrend"
      :trend-loading="trendLoading"
      :trend-error="trendError"
      :detail="displayedDetail"
      :detail-student-id="detailStudentId"
      :detail-loading="detailLoading"
      :detail-error="detailError"
      :show-back="showBack"
      :history-open="historyOpen"
      :semester-catalog="semesterCatalog"
      :semester-id="semesterId"
      :overview="overview"
      :range="chartRange"
      :personal-trend="personalTrend"
      :personal-trend-loading="personalTrendLoading"
      :personal-trend-error="personalTrendError"
      @back="back() || emit('back')"
      @block="openBlock"
      @subject="selectSubject"
      @types="setMobileTypes"
      @student="openStudentDetail"
      @close-student="back"
      @retry="loadTrend"
      @retry-personal-trend="loadPersonalTrend"
      @retry-detail="loadStudentDetail"
      @range="setChartRange"
      @semester="selectSemester"
    >
      <template #toolbar>
        <nav
          v-if="response && !offline && canView "
          class="headman-stats__toolbar"
          :class="{ 'headman-stats__toolbar--pills': detailStudentId === null && block === 'group' }"
          aria-label="Инструменты статистики"
        >
          <button
            v-for="tool in visibleTools"
            :key="tool"
            type="button"
            :aria-label="{filters:'Фильтры',sort:'Сортировка',export:'Выгрузка',trend:'Динамика',tickets:'Тикеты'}[tool]"
            @click="openTool(tool)"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            ><path
              v-if="tool === 'filters'"
              d="M4 6h16M7 12h10M10 18h4"
            /><path
              v-else-if="tool === 'sort'"
              d="M5 5h14M5 12h10M5 19h6M18 11v9m-3-3 3 3 3-3"
            /><path
              v-else-if="tool === 'export'"
              d="M12 3v12m-4-4 4 4 4-4M4 16v5h16v-5"
            /><path
              v-else-if="tool === 'trend'"
              d="M4 3v17h17M6 15l5-6 4 3 5-8"
            /><path
              v-else
              d="M5 4h14v16H5zM9 8h6M9 12h6M9 16h3"
            /></svg>
          </button>
        </nav>
      </template>
      <template #search>
        <form
          v-if="controlResponse?.columns.some((item) => item.field === 'displayName' && item.filterKind === 'TEXT')"
          class="headman-stats__search"
          @submit.prevent="applyMobileSearch"
        >
          <label><span class="statistics-visually-hidden">Найти студента</span><input
            v-model="searchText"
            type="search"
            placeholder="Найти студента"
          ></label>
          <button
            type="submit"
            aria-label="Найти студента"
          >
            <MobileIcon name="search" />
          </button>
        </form>
      </template>
      <template #history>
        <nav
          v-if="historyOpen"
          class="headman-stats__toolbar"
          aria-label="Инструменты истории"
        >
          <button
            type="button"
            aria-label="Фильтры"
            @click="openTool('filters')"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              aria-hidden="true"
            ><path d="M4 6h16M7 12h10M10 18h4" /></svg>
          </button>
        </nav>
        <HeadmanStatsHistory
          v-if="historyOpen && detailStudentId !== null"
          ref="historyRef"
          :api="api"
          :journal-api="journalApi"
          :requests-api="requestsApi"
          :group-id="groupId"
          :student-id="detailStudentId"
          :semester-id="semesterId"
          :subject-id="personalSubjectId ? Number(personalSubjectId) : null"
          :lesson-types="personalTypes"
          :offline="offline"
          :allowed="canView && !denied"
          :mark-allowed="assistantPermissions === null || assistantPermissions.includes('MARK_ATTENDANCE')"
          :excuse-allowed="assistantPermissions === null || assistantPermissions.includes('MANAGE_EXCUSES')"
          @refreshed="refreshedHistory"
        />
        <button
          v-else
          type="button"
          class="headman-stats__history-link"
          @click="openHistory"
        >
          Прошедшие пары <span aria-hidden="true">›</span>
        </button>
      </template>
    </HeadmanStatsMobileView>
    <StudentWarningBlock
      v-if="metadataError"
      title="Данные недоступны"
      :message="metadataError"
      action-label="Повторить"
      :action-disabled="loading || offline"
      @action="loadInitial"
    />

    <StudentWarningBlock
      v-if="stateMessage"
      title="Статистика"
      :message="stateMessage"
    />
    <StudentWarningBlock
      v-if="error"
      title="Не удалось загрузить статистику"
      :message="error"
      :action-label="!denied && canView && !offline ? 'Повторить' : ''"
      :action-disabled="loading"
      @action="loadBlock()"
    />
    <p
      v-if="loading"
      class="headman-stats__state"
      role="status"
      aria-live="polite"
    >
      Загружаем статистику группы…
    </p>

    <section
      v-if="analyticsPage"
      class="headman-stats__analytics"
      :aria-label="analyticsPage === 'trend' ? 'Динамика посещаемости' : 'Аналитика тикетов'"
    >
      <header class="headman-stats-mobile__header">
        <button
          v-if="showBack"
          class="headman-stats-mobile__back"
          type="button"
          aria-label="Назад"
          @click="back"
        >
          <MobileIcon name="back" />
        </button><h1 id="headman-stats-title">
          {{ analyticsPage === 'trend' ? 'Динамика посещаемости' : 'Аналитика тикетов' }}
        </h1>
      </header>
      <p v-if="analyticsPage === 'tickets'">
        {{ detailStudentId !== null ? displayedDetail?.student.displayName : response?.context.groupName }} · {{ response?.context.semesterName }}{{ detailStudentId === null && block === 'subject' && selectedSubjectId !== null ? ' · ' + response?.subjects.find(item => item.id === selectedSubjectId)?.label : '' }}
      </p>
      <template v-if="analyticsPage === 'trend'">
        <nav
          class="headman-stats__blocks headman-stats__blocks--trend"
          aria-label="Период динамики"
        >
          <button
            v-for="mode in (['SEMESTER','WEEK','SUBJECT'] as const)"
            :key="mode"
            type="button"
            :aria-pressed="trendMode === mode"
            @click="setTrendMode(mode)"
          >
            {{ {SEMESTER:'Семестр',WEEK:'Неделя',SUBJECT:'Предмет'}[mode] }}
          </button>
        </nav>
        <label
          v-if="trendMode === 'WEEK'"
          class="headman-stats__field"
        ><span>Неделя</span><input
          type="date"
          :value="trendWeekStart"
          :min="semesterWeekMin()"
          :max="response?.context.semesterTo ?? undefined"
          @change="selectTrendWeek(($event.target as HTMLInputElement).value)"
        ></label>
        <template v-if="trendMode === 'SUBJECT'">
          <HeadmanStatsSelect
            label="Предмет динамики"
            :model-value="String(trendSubjectId ?? '')"
            :options="(response?.subjects ?? []).map(subject => ({ value: String(subject.id), label: subject.label }))"
            @update:model-value="selectTrendSubject"
          /><fieldset class="headman-stats__types">
            <legend>Типы занятий</legend><label
              v-for="type in selectedTrendSubject?.lessonTypes ?? []"
              :key="type.code"
            ><input
              v-model="trendLessonTypes"
              type="checkbox"
              :value="type.code"
              @change="loadTrend"
            >{{ type.label }}</label>
          </fieldset>
        </template>
        <p
          v-if="trendLoading"
          role="status"
        >
          Загружаем динамику…
        </p><StudentWarningBlock
          v-if="trendError"
          title="График недоступен"
          :message="trendError"
          action-label="Повторить"
          @action="loadTrend"
        /><p
          v-if="trendEmptyStateLabel()"
          role="status"
        >
          {{ trendEmptyStateLabel() }}
        </p>
        <HeadmanStatsTrendChart
          v-if="displayedTrend"
          :points="displayedTrend.points"
          :mode="displayedTrend.mode"
          :from="displayedTrend.context.semesterFrom"
          :to="displayedTrend.context.semesterTo"
          :week-start="trendWeekStart"
        />
        <nav
          v-if="trendMode !== 'WEEK'"
          class="headman-stats-mobile__ranges"
          aria-label="Период графика"
        >
          <button
            v-for="range in (['days','weeks'] as const)"
            :key="range"
            type="button"
            :aria-pressed="chartRange === range"
            @click="setChartRange(range)"
          >
            {{ range === 'days' ? 'Дни' : 'Недели' }}
          </button>
        </nav>
        <div class="headman-stats__download">
          <HeadmanStatsSelect
            label="Формат графика"
            placeholder="Выбери формат"
            :model-value="selectedTrendFormat"
            :options="(displayedTrend?.formats ?? []).map(format => ({ value: format.code, label: format.label }))"
            @update:model-value="selectedTrendFormat = $event as HeadmanStatsTrendFormat['code']"
          /><button
            type="button"
            aria-label="Скачать график"
            :disabled="!selectedTrendFormat || exporting || !displayedTrend"
            @click="selectedTrendFormat && exportTrend(selectedTrendFormat)"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              aria-hidden="true"
            ><path d="M12 3v12m-4-4 4 4 4-4M4 16v5h16v-5" /></svg>
          </button>
        </div>
        <p
          v-if="trendExportStatus"
          role="status"
        >
          {{ trendExportStatus }}
        </p><p
          v-if="trendExportError"
          role="alert"
        >
          {{ trendExportError }}
        </p>
      </template>
      <template v-else>
        <p
          v-if="!ticketCounters"
          role="status"
        >
          Сводка тикетов за выбранный период недоступна. Обнови статистику, чтобы получить полные данные.
        </p><section
          v-for="item in ticketCounters ? [{label:'Тикеты «н»',counts:ticketCounters.lateCheckin},{label:'Тикеты «у»',counts:ticketCounters.excuse}] : []"
          :key="item.label"
          class="headman-stats-mobile__card"
        >
          <h2>{{ item.label }}</h2><dl class="headman-stats__ticket-metrics">
            <div><dt>Подано</dt><dd>{{ item.counts.submitted }}</dd></div><div><dt>Одобрено</dt><dd>{{ item.counts.approved }} · {{ ticketRatio(item.counts.approved,item.counts.submitted) }}</dd></div><div><dt>Отклонено</dt><dd>{{ item.counts.rejected }} · {{ ticketRatio(item.counts.rejected,item.counts.submitted) }}</dd></div><div><dt>Ожидают</dt><dd>{{ item.counts.submitted-item.counts.approved-item.counts.rejected }} · {{ ticketRatio(item.counts.submitted-item.counts.approved-item.counts.rejected,item.counts.submitted) }}</dd></div>
          </dl><div
            class="headman-stats__decision-bar"
            role="img"
            :aria-label="`${item.label}: одобрено ${item.counts.approved}, отклонено ${item.counts.rejected}, ожидают ${item.counts.submitted-item.counts.approved-item.counts.rejected}`"
          >
            <span
              class="headman-stats__decision-bar--approved"
              :style="{flexGrow:item.counts.approved}"
            /><span
              class="headman-stats__decision-bar--rejected"
              :style="{flexGrow:item.counts.rejected}"
            /><span
              class="headman-stats__decision-bar--pending"
              :style="{flexGrow:item.counts.submitted-item.counts.approved-item.counts.rejected}"
            />
          </div>
        </section>
      </template>
    </section>
    <MobileToolsSheet
      v-if="response && !offline && canView"
      v-model:open="statsToolsOpen"
      :title="statsToolTitles[statsTool]"
      :icon-close="true"
      class="headman-stats__advanced"
    >
      <template v-if="statsTool === 'filters'">
        <div class="headman-stats__exclusive">
          <button
            type="button"
            :aria-pressed="filterBlock === 'group'"
            @click="filterBlock = 'group'"
          >
            {{ detailStudentId === null ? 'Вся группа' : 'Весь семестр' }}
          </button><button
            type="button"
            :aria-pressed="filterBlock === 'subject'"
            @click="filterBlock = 'subject'; changeFilterSubject(filterSubjectId)"
          >
            По предмету и типу
          </button>
        </div>
        <template v-if="filterBlock === 'subject'">
          <HeadmanStatsSelect
            label="Предмет фильтра"
            :model-value="filterSubjectId"
            :options="response.subjects.map(subject => ({ value: String(subject.id), label: subject.label }))"
            @update:model-value="changeFilterSubject"
          /><fieldset class="headman-stats__types">
            <legend>Типы занятий</legend><label
              v-for="type in response.subjects.find(item => item.id === Number(filterSubjectId))?.lessonTypes ?? []"
              :key="type.code"
            ><input
              v-model="filterTypes"
              type="checkbox"
              :value="type.code"
            >{{ type.label }}</label>
          </fieldset>
        </template>
        <div
          v-if="detailStudentId === null"
          class="headman-stats__filter-grid"
        >
          <fieldset
            v-for="column in controlResponse?.columns ?? []"
            :key="column.field"
          >
            <legend>{{ column.label }}</legend><input
              v-if="column.filterKind === 'TEXT'"
              v-model="filterDraft(column).contains"
              type="search"
              :aria-label="`Фильтр: ${column.label}`"
              placeholder="Содержит"
            ><template v-else>
              <input
                v-model="filterDraft(column).minimum"
                type="number"
                min="0"
                placeholder="От"
                :aria-label="`От: ${column.label}`"
              ><input
                v-model="filterDraft(column).maximum"
                type="number"
                min="0"
                placeholder="До"
                :aria-label="`До: ${column.label}`"
              >
            </template>
          </fieldset>
        </div>
        <div class="headman-stats__filter-actions">
          <button
            type="button"
            aria-label="Сбросить фильтры"
            @click="resetToolFilters"
          >
            <MobileIcon name="retry" />
          </button><button
            type="button"
            :disabled="loading || filterBlock === 'subject' && (!filterSubjectId || !filterTypes.length)"
            @click="applyToolFilters"
          >
            Применить
          </button>
        </div>
      </template>
      <HeadmanStatsSelect
        v-else-if="statsTool === 'sort'"
        label="Порядок"
        :model-value="subjectCatalog ? 'displayName:asc' : sortPreset"
        :options="subjectCatalog ? sortPresets.slice(0,1) : sortPresets"
        :disabled="subjectCatalog"
        @update:model-value="chooseSort"
      />
      <template v-else-if="statsTool === 'export'">
        <div class="headman-stats__download">
          <HeadmanStatsSelect
            label="Формат выгрузки"
            placeholder="Выбери формат"
            :model-value="selectedFormat"
            :options="(displayedResponse?.formats ?? []).map(format => ({ value: format.code, label: format.label }))"
            @update:model-value="selectedFormat = $event as HeadmanStatsFormat['code']"
          /><button
            type="button"
            aria-label="Скачать статистику"
            :disabled="loading || exporting || !canExport"
            @click="exportCurrent"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              aria-hidden="true"
            ><path d="M12 3v12m-4-4 4 4 4-4M4 16v5h16v-5" /></svg>
          </button>
        </div><p
          v-if="exportStatus"
          role="status"
        >
          {{ exportStatus }}
        </p>
      </template>
    </MobileToolsSheet>
    <nav
      v-if="displayedResponse && displayedResponse.totalPages > 1 && analyticsPage === null && detailStudentId === null"
      class="headman-stats__pagination"
      aria-label="Страницы студентов"
    >
      <button
        type="button"
        :disabled="!displayedResponse.hasPrevious || loading"
        @click="changePage(page - 1)"
      >
        Назад
      </button><span>{{ displayedResponse.page + 1 }} из {{ displayedResponse.totalPages }}</span><button
        type="button"
        :disabled="!displayedResponse.hasNext || loading"
        @click="changePage(page + 1)"
      >
        Дальше
      </button>
    </nav>
  </main>
</template>
