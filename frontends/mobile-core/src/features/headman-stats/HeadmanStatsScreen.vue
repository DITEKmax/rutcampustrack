<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import type { ReportDownloadPort } from '../../shared/report-download-client'
import {
  HeadmanStatsApiError,
  headmanStatsResponseForCurrentQuery,
  headmanStatsQueryKey,
  hasHeadmanStatsResettableCriteria,
  resetHeadmanStatsCriteria,
  toHeadmanStatsReportRequest,
  type HeadmanStatsApi,
  type HeadmanStatsColumn,
  type HeadmanStatsFilter,
  type HeadmanStatsFormat,
  type HeadmanStatsMetric,
  type HeadmanStatsResponse,
  type HeadmanStatsSort,
  type HeadmanStatsStudentRow,
} from './headman-stats-client'
import './headman-stats-screen.pcss'

type Block = 'group' | 'subject'
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

const block = ref<Block>('group')
const response = ref<HeadmanStatsResponse | null>(null)
const responseQueryKey = ref<string | null>(null)
const selectedSubjectId = ref<number | null>(null)
const selectedTypes = ref<string[]>([])
const sorts = ref<HeadmanStatsSort[]>([])
const filters = ref<HeadmanStatsFilter[]>([])
const filterDrafts = reactive<Record<string, FilterDraft>>({})
const selectedFormat = ref<HeadmanStatsFormat['code']>('xlsx')
const loading = ref(false)
const exporting = ref(false)
const exportStatus = ref<string | null>(null)
const denied = ref(false)
const error = ref<string | null>(null)
const page = ref(0)
const pageSize = 50
let revision = 0
let activeController: AbortController | null = null

const canView = computed(() => props.assistantPermissions === null
  || props.assistantPermissions.includes('VIEW_STATS'))
const currentQueryKey = computed(() => headmanStatsQueryKey({
  ...queryForCurrentBlock(true),
  page: page.value,
  size: pageSize,
}))
const displayedResponse = computed(() => headmanStatsResponseForCurrentQuery(
  response.value,
  responseQueryKey.value,
  currentQueryKey.value,
))
const activeFormat = computed(() => displayedResponse.value?.formats.find((item) => item.code === selectedFormat.value) ?? null)
const selectedSubject = computed(() => displayedResponse.value?.subjects.find((item) => item.id === selectedSubjectId.value) ?? null)
const hasFilterDraft = computed(() => filters.value.length > 0 || (displayedResponse.value?.columns.some((column) => {
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
  return (displayedResponse.value?.columns ?? []).flatMap((column): HeadmanStatsFilter[] => {
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

async function loadInitial(): Promise<void> {
  revision += 1
  activeController?.abort()
  activeController = null
  loading.value = false
  response.value = null
  responseQueryKey.value = null
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
  if (block.value === 'subject' && selectedSubjectId.value === null) {
    responseQueryKey.value = null
    exportStatus.value = null
    error.value = 'Выбери предмет для статистики.'
    return
  }
  const query = { ...queryForCurrentBlock(true), page: page.value, size: pageSize }
  const queryKey = headmanStatsQueryKey(query)
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
    for (const column of result.columns) filterDraft(column)
    if (selectedSubjectId.value === null && result.subjects.length > 0) {
      selectedSubjectId.value = result.subjects[0]!.id
    }
    if (!result.formats.some((format) => format.code === selectedFormat.value)) {
      selectedFormat.value = result.formats[0]?.code ?? 'xlsx'
    }
  } catch (cause) {
    if (currentRevision !== revision || controller.signal.aborted) return
    denied.value = cause instanceof HeadmanStatsApiError && cause.response.status === 403
    if (denied.value) response.value = null
    error.value = cause instanceof Error ? cause.message : 'Не удалось загрузить статистику.'
    emit('error', cause)
  } finally {
    if (currentRevision === revision) loading.value = false
  }
}

function openBlock(next: Block): void {
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

watch(() => [props.api, props.groupId, props.offline, props.reportDownload, canView.value] as const, () => {
  void loadInitial()
}, { immediate: true })

onBeforeUnmount(() => {
  revision += 1
  activeController?.abort()
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

    <template v-if="displayedResponse && !props.offline && canView">
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
              v-for="subject in displayedResponse.subjects"
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
        <div class="headman-stats__export">
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

      <section
        class="headman-stats__summary"
        aria-label="Сводные показатели после фильтров"
      >
        <article><h2>«+»</h2><p>{{ metricValue(displayedResponse.summary, 'present') }}</p></article>
        <article><h2>«+ и у»</h2><p>{{ metricValue(displayedResponse.summary, 'presentOrExcused') }}</p></article>
        <article><h2>«у»</h2><p>{{ metricValue(displayedResponse.summary, 'excused') }}</p></article>
        <article><h2>«н»</h2><p>{{ metricValue(displayedResponse.summary, 'absent') }}</p></article>
      </section>

      <details class="headman-stats__filters">
        <summary>Фильтры столбцов</summary>
        <div class="headman-stats__filter-grid">
          <fieldset
            v-for="column in displayedResponse.columns"
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
  </main>
</template>
