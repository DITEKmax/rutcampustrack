<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { StaleSessionGenerationError } from '../../shared/session-owner'
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherExportFormat,
  type TeacherExportFormatCode,
  type TeacherJournalQuery,
  type TeacherStatsMetric,
  type TeacherStatsQuery,
  type TeacherStatsResponse,
  type TeacherStatsSubjectOption,
  type TeacherStatsScope,
  type TeacherStatsSort,
} from './teacher-client'
import {
  readTeacherStatsContext,
  writeTeacherStatsContext,
  type TeacherStatsRouteContext,
} from './teacher-stats-route'
import './teacher-stats-screen.pcss'

const props = withDefaults(defineProps<{
  api: TeacherApi | null
  semesterId: number | null
  initialGroupId?: number | null
}>(), {
  initialGroupId: null,
})

const emit = defineEmits<{
  back: []
  error: [cause: unknown]
  'open-journal': [query: TeacherJournalQuery]
}>()

const scope = ref<TeacherStatsScope>('groups')
const semester = ref<{ id: number; dateFrom: string; dateTo: string } | null>(null)
const authorizedGroups = ref<readonly { id: number; name: string }[]>([])
const authorizedSubjectOptions = ref<readonly TeacherStatsSubjectOption[]>([])
const selectedGroupId = ref<number | null>(props.initialGroupId)
const selectedSubjectId = ref<number | null>(null)
const selectedTypes = ref<string[]>([])
const search = ref('')
const stats = ref<TeacherStatsResponse | null>(null)
const statsQuery = ref<TeacherStatsQuery | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const revision = ref(0)
const sort = ref<TeacherStatsSort>({ column: 'present', descending: false })
const formats = ref<readonly TeacherExportFormat[]>([])
const selectedFormat = ref<TeacherExportFormatCode>('docx')
const formatsLoading = ref(false)
const exportLoading = ref(false)
const exportError = ref<string | null>(null)
let exportRevision = 0
let formatsRevision = 0
const exportObjectUrls = new Set<string>()
let disposed = false

const groups = computed(() => authorizedGroups.value)
const subjects = computed(() => subjectsForGroup(selectedGroupId.value))
const types = computed(() => lessonTypesForContext(selectedGroupId.value, selectedSubjectId.value))
const selectedTypeLabel = computed(() => selectedTypes.value.length === 0 || selectedTypes.value.length === types.value.length
  ? 'все типы занятий' : selectedTypes.value.join(', '))
const groupRows = computed(() => stats.value?.groups ?? [])
const studentRows = computed(() => stats.value?.students ?? [])
const visibleRowCount = computed(() => scope.value === 'groups' ? groupRows.value.length : studentRows.value.length)
const canExport = computed(() => Boolean(
  props.api && stats.value && statsQuery.value && !loading.value && !error.value
  && !formatsLoading.value && !exportLoading.value && formats.value.length === 5
  && JSON.stringify(statsQuery.value) === JSON.stringify(currentStatsQuery())
  && formats.value.some((format) => format.code === selectedFormat.value),
))

watch(
  () => [props.api, props.semesterId, props.initialGroupId] as const,
  () => {
    selectedGroupId.value = props.initialGroupId
    selectedSubjectId.value = null
    selectedTypes.value = []
    search.value = ''
    sort.value = { column: 'present', descending: false }
    stats.value = null
    statsQuery.value = null
    authorizedGroups.value = []
    authorizedSubjectOptions.value = []
    formats.value = []
    exportRevision += 1
    formatsRevision += 1
    exportLoading.value = false
    formatsLoading.value = false
    exportError.value = null
    void loadContext()
    void loadExportFormats()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  disposed = true
  revision.value += 1
  exportRevision += 1
  formatsRevision += 1
  for (const url of exportObjectUrls) URL.revokeObjectURL(url)
  exportObjectUrls.clear()
})

async function loadContext(): Promise<void> {
  const api = props.api
  const semesterId = props.semesterId
  const current = ++revision.value
  loading.value = true
  error.value = null
  try {
    if (!api || !semesterId) {
      authorizedGroups.value = []
      authorizedSubjectOptions.value = []
      semester.value = null
      stats.value = null
      statsQuery.value = null
      return
    }
    const value = await api.semester()
    if (!isCurrent(current)) return
    semester.value = value
    const groupQuery: TeacherStatsQuery = {
      semesterId,
      scope: 'groups',
      groupId: null,
      subjectId: null,
      lessonTypes: [],
      sorts: [{ column: 'present', descending: false }],
      filters: [],
    }
    const groupStats = await api.stats(groupQuery)
    if (!isCurrent(current)) return
    authorizedGroups.value = groupStats.groups
      .filter((group) => Number.isSafeInteger(group.groupId) && group.groupId > 0)
      .map((group) => ({ id: group.groupId, name: group.groupName }))
    authorizedSubjectOptions.value = groupStats.subjectOptions
    stats.value = groupStats
    statsQuery.value = groupQuery
    restoreContext(semesterId)
    persistContext()
    if (!canReuseUnfilteredGroupStats()) await loadStatsForRevision(current)
  } catch (cause) {
    if (!isCurrent(current) || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось получить назначения преподавателя.'
    emit('error', cause)
  } finally {
    if (isCurrent(current)) loading.value = false
  }
}

async function loadStats(): Promise<void> {
  exportRevision += 1
  exportLoading.value = false
  exportError.value = null
  const current = ++revision.value
  await loadStatsForRevision(current)
}

async function loadStatsForRevision(current: number): Promise<void> {
  const api = props.api
  const semesterId = props.semesterId
  loading.value = true
  error.value = null
  try {
    if (!api || !semesterId) {
      stats.value = null
      statsQuery.value = null
      return
    }
    const query = currentStatsQuery()
    if (!query) {
      stats.value = null
      statsQuery.value = null
      return
    }
    const response = await api.stats(query)
    if (!isCurrent(current)) return
    stats.value = response
    statsQuery.value = query
  } catch (cause) {
    if (!isCurrent(current) || cause instanceof StaleSessionGenerationError) return
    error.value = cause instanceof TeacherApiError ? cause.message : 'Не удалось получить статистику.'
    emit('error', cause)
  } finally {
    if (isCurrent(current)) loading.value = false
  }
}

async function loadExportFormats(): Promise<void> {
  const api = props.api
  const current = ++formatsRevision
  if (!api || !props.semesterId) {
    formats.value = []
    formatsLoading.value = false
    return
  }
  formatsLoading.value = true
  exportError.value = null
  try {
    const value = await api.statsExportFormats()
    if (current !== formatsRevision || api !== props.api || !props.semesterId) return
    formats.value = value
    if (!value.some((format) => format.code === selectedFormat.value)) {
      selectedFormat.value = value[0]?.code ?? 'docx'
    }
  } catch (cause) {
    if (current !== formatsRevision || cause instanceof StaleSessionGenerationError) return
    exportError.value = cause instanceof TeacherApiError
      ? cause.message
      : 'Не удалось получить список форматов выгрузки.'
    emit('error', cause)
  } finally {
    if (current === formatsRevision) formatsLoading.value = false
  }
}

async function exportStats(): Promise<void> {
  const api = props.api
  const query = statsQuery.value
  const current = ++exportRevision
  const queryKey = JSON.stringify(query)
  const format = selectedFormat.value
  if (!api || !query || !canExport.value) return
  exportLoading.value = true
  exportError.value = null
  try {
    const file = await api.exportStats(query, format)
    if (!isCurrentExport(current, api, queryKey, format)) return
    const url = URL.createObjectURL(file.blob)
    exportObjectUrls.add(url)
    const link = document.createElement('a')
    link.href = url
    link.download = file.filename
    link.click()
    window.setTimeout(() => {
      exportObjectUrls.delete(url)
      URL.revokeObjectURL(url)
    }, 60_000)
  } catch (cause) {
    if (!isCurrentExport(current, api, queryKey, format)
      || cause instanceof StaleSessionGenerationError) return
    exportError.value = cause instanceof TeacherApiError
      ? cause.message
      : 'Не удалось скачать статистику. Попробуй ещё раз.'
    emit('error', cause)
  } finally {
    if (current === exportRevision) exportLoading.value = false
  }
}

function currentStatsQuery(): TeacherStatsQuery | null {
  const semesterId = props.semesterId
  if (!semesterId || scope.value === 'students' && (!selectedGroupId.value || !selectedSubjectId.value)) return null
  return {
    semesterId,
    scope: scope.value,
    groupId: scope.value === 'students' ? selectedGroupId.value : null,
    subjectId: scope.value === 'students' ? selectedSubjectId.value : null,
    lessonTypes: [...selectedTypes.value],
    sorts: [{ ...sort.value }],
    filters: search.value.trim() ? [{
      column: scope.value === 'students' ? 'displayName' : 'groupName',
      contains: search.value.trim(),
    }] : [],
  }
}

function isCurrentExport(current: number,
                         api: TeacherApi,
                         queryKey: string,
                         format: TeacherExportFormatCode): boolean {
  return !disposed && current === exportRevision && api === props.api
    && JSON.stringify(currentStatsQuery()) === queryKey && selectedFormat.value === format
}

function restoreContext(semesterId: number): void {
  const saved = readTeacherStatsContext()
  if (!saved || saved.semesterId !== semesterId) {
    scope.value = 'groups'
    selectedGroupId.value = null
    selectedSubjectId.value = null
    selectedTypes.value = []
    search.value = ''
    sort.value = { column: 'present', descending: false }
    return
  }

  scope.value = saved.scope
  if (scope.value === 'students') {
    // Keep the route context until stats returns the server's current group
    // and subject options. A replacement teacher can select a subject absent
    // from their own assignment list.
    const savedGroup = saved.groupId && groups.value.some((value) => value.id === saved.groupId)
      ? saved.groupId : null
    const initialGroup = props.initialGroupId && groups.value.some((value) => value.id === props.initialGroupId)
      ? props.initialGroupId : null
    selectedGroupId.value = savedGroup ?? initialGroup ?? groups.value[0]?.id ?? null
    const availableSubjects = subjectsForGroup(selectedGroupId.value)
    selectedSubjectId.value = savedGroup === selectedGroupId.value && saved.subjectId
      ? saved.subjectId
      : availableSubjects[0]?.id ?? null
  } else {
    selectedGroupId.value = null
    selectedSubjectId.value = null
  }
  selectedTypes.value = [...new Set(saved.lessonTypes.filter((value) => value.trim() !== ''))]
  search.value = saved.search
  sort.value = normalizeSort(scope.value, saved.sort)
}

function persistContext(): void {
  writeTeacherStatsContext(currentRouteContext())
}

function currentRouteContext(): TeacherStatsRouteContext {
  return {
    semesterId: props.semesterId,
    scope: scope.value,
    groupId: scope.value === 'students' ? selectedGroupId.value : null,
    subjectId: scope.value === 'students' ? selectedSubjectId.value : null,
    lessonTypes: selectedTypes.value,
    search: search.value,
    sort: sort.value,
  }
}

function canReuseUnfilteredGroupStats(): boolean {
  return scope.value === 'groups'
    && selectedTypes.value.length === 0
    && search.value.trim() === ''
    && sort.value.column === 'present'
    && !sort.value.descending
}

function switchScope(next: TeacherStatsScope): void {
  if (scope.value === next) return
  scope.value = next
  if (next === 'students') {
    const groupId = selectedGroupId.value && groups.value.some((value) => value.id === selectedGroupId.value)
      ? selectedGroupId.value : groups.value[0]?.id ?? null
    selectedGroupId.value = groupId
    selectedSubjectId.value = subjectsForGroup(groupId)[0]?.id ?? null
  } else {
    selectedGroupId.value = null
    selectedSubjectId.value = null
  }
  selectedTypes.value = []
  sort.value = normalizeSort(next, sort.value)
  persistContext()
  void loadStats()
}

function changeGroup(rawValue: string): void {
  const nextGroupId = positiveInteger(rawValue)
  const validGroupId = nextGroupId && groups.value.some((value) => value.id === nextGroupId)
    ? nextGroupId : null
  const nextSubjects = subjectsForGroup(validGroupId)
  selectedGroupId.value = validGroupId
  selectedSubjectId.value = nextSubjects[0]?.id ?? null
  selectedTypes.value = []
  persistContext()
  void loadStats()
}

function changeSubject(rawValue: string): void {
  const nextSubjectId = positiveInteger(rawValue)
  selectedSubjectId.value = nextSubjectId && subjects.value.some((value) => value.id === nextSubjectId)
    ? nextSubjectId : null
  selectedTypes.value = []
  persistContext()
  void loadStats()
}

function toggleType(type: string): void {
  const effective = selectedTypes.value.length === 0 ? types.value : selectedTypes.value
  const next = effective.includes(type)
    ? effective.filter((value) => value !== type)
    : [...effective, type]
  selectedTypes.value = next.length === types.value.length ? [] : next
  persistContext()
  void loadStats()
}

function setSort(column: string): void {
  if (!sortColumns(scope.value).includes(column)) return
  sort.value = sort.value.column === column
    ? { column, descending: !sort.value.descending }
    : { column, descending: false }
  persistContext()
  void loadStats()
}

function applySearch(): void {
  search.value = search.value.trim().slice(0, 120)
  persistContext()
  void loadStats()
}

function openGroup(groupId: number): void {
  if (!groups.value.some((value) => value.id === groupId)) return
  selectedGroupId.value = groupId
  selectedSubjectId.value = subjectsForGroup(groupId)[0]?.id ?? null
  scope.value = 'students'
  selectedTypes.value = []
  sort.value = normalizeSort('students', sort.value)
  persistContext()
  void loadStats()
}

function openJournal(lessonType: string): void {
  if (!selectedGroupId.value || !selectedSubjectId.value || !props.semesterId || !lessonType) return
  emit('open-journal', {
    semesterId: props.semesterId,
    groupId: selectedGroupId.value,
    subjectId: selectedSubjectId.value,
    lessonType,
    page: 0,
    pageSize: 100,
  })
}

function formatMetric(metric: TeacherStatsMetric | undefined): string {
  if (!metric || metric.denominator === 0) return 'нет данных'
  return `${metric.percent.toFixed(1)}% (${metric.numerator}/${metric.denominator})`
}

function sortMark(column: string): string {
  if (sort.value.column !== column) return ''
  return sort.value.descending ? ' ↓' : ' ↑'
}

function subjectsForGroup(groupId: number | null): Array<{ id: number; name: string }> {
  if (groupId == null) return []
  const values = new Map<number, { id: number; name: string }>()
  for (const option of authorizedSubjectOptions.value) {
    if (option.groupId === groupId) values.set(option.subjectId, {
      id: option.subjectId,
      name: option.subjectName,
    })
  }
  return [...values.values()].sort((left, right) => left.name.localeCompare(right.name, 'ru'))
}

function lessonTypesForContext(groupId: number | null, subjectId: number | null): string[] {
  const values = new Set<string>()
  for (const option of authorizedSubjectOptions.value) {
    if ((groupId == null || option.groupId === groupId)
      && (subjectId == null || option.subjectId === subjectId)) {
      option.lessonTypes.forEach((value) => values.add(value))
    }
  }
  return [...values].sort((left, right) => left.localeCompare(right, 'ru'))
}

function sortColumns(nextScope: TeacherStatsScope): string[] {
  return nextScope === 'groups'
    ? ['groupName', 'present', 'presentOrExcused', 'excused', 'absent', 'lessonsCount']
    : ['displayName', 'present', 'presentOrExcused', 'excused', 'absent']
}

function normalizeSort(nextScope: TeacherStatsScope, value: TeacherStatsSort): TeacherStatsSort {
  return sortColumns(nextScope).includes(value.column)
    ? { column: value.column, descending: Boolean(value.descending) }
    : { column: 'present', descending: false }
}

function positiveInteger(value: string | number | null | undefined): number | null {
  const text = String(value ?? '')
  if (!/^\d+$/.test(text)) return null
  const parsed = Number(text)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

function isCurrent(requestRevision: number): boolean {
  return !disposed && requestRevision === revision.value
}

</script>

<template>
  <main
    class="teacher-stats"
    aria-labelledby="teacher-stats-title"
    :aria-busy="loading"
  >
    <header class="teacher-stats__header">
      <div>
        <button
          type="button"
          class="teacher-stats__back"
          @click="emit('back')"
        >
          Назад
        </button>
        <p class="teacher-stats__eyebrow">
          Преподаватель
        </p>
        <h1 id="teacher-stats-title">
          Статистика
        </h1>
      </div>
      <span
        v-if="stats"
        class="teacher-stats__meta"
      >Учтено пар: {{ stats.lessonsCount }}</span>
    </header>

    <p
      v-if="error"
      class="teacher-stats__state teacher-stats__state--error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-else-if="loading"
      class="teacher-stats__state"
      aria-live="polite"
    >
      Загружаем статистику…
    </p>
    <p
      v-else-if="!api || !semesterId"
      class="teacher-stats__state"
    >
      Сессия преподавателя недоступна.
    </p>
    <template v-else>
      <div
        class="teacher-stats__tabs"
        role="tablist"
        aria-label="Разрез статистики"
      >
        <button
          type="button"
          :aria-selected="scope === 'students'"
          @click="switchScope('students')"
        >
          По студентам группы
        </button>
        <button
          type="button"
          :aria-selected="scope === 'groups'"
          @click="switchScope('groups')"
        >
          По моим группам
        </button>
      </div>

      <section
        class="teacher-stats__context"
        aria-label="Выбор контекста"
      >
        <label v-if="scope === 'students'">
          <span>Группа</span>
          <select
            :value="selectedGroupId ?? ''"
            @change="changeGroup(($event.target as HTMLSelectElement).value)"
          >
            <option value="">Выбери группу</option>
            <option
              v-for="group in groups"
              :key="group.id"
              :value="group.id"
            >{{ group.name }}</option>
          </select>
        </label>
        <label v-if="scope === 'students'">
          <span>Предмет</span>
          <select
            :value="selectedSubjectId ?? ''"
            :disabled="!selectedGroupId"
            @change="changeSubject(($event.target as HTMLSelectElement).value)"
          >
            <option value="">Выбери предмет</option>
            <option
              v-for="subject in subjects"
              :key="subject.id"
              :value="subject.id"
            >{{ subject.name }}</option>
          </select>
        </label>
        <fieldset>
          <legend>Тип занятия</legend>
          <label
            v-for="type in types"
            :key="type"
            class="teacher-stats__type"
          >
            <input
              type="checkbox"
              :checked="selectedTypes.length === 0 || selectedTypes.includes(type)"
              @change="toggleType(type)"
            >
            {{ type }}
          </label>
          <small>{{ selectedTypeLabel }}</small>
        </fieldset>
        <label class="teacher-stats__search">
          <span>{{ scope === 'students' ? 'Поиск студента' : 'Поиск группы' }}</span>
          <input
            v-model="search"
            type="search"
            @keyup.enter="applySearch"
          >
        </label>
        <button
          type="button"
          class="teacher-stats__apply"
          @click="applySearch"
        >
          Применить
        </button>
      </section>

      <section
        v-if="scope === 'students' && selectedGroupId && selectedSubjectId && types.length > 0"
        class="teacher-stats__journal-actions"
        aria-label="Журнал выбранного предмета"
      >
        <span>Открыть журнал:</span>
        <button
          v-for="type in types"
          :key="type"
          type="button"
          class="teacher-stats__row-link"
          @click="openJournal(type)"
        >
          Журнал · {{ type }}
        </button>
      </section>

      <dl
        v-if="stats"
        class="teacher-stats__definition"
      >
        <div v-if="scope === 'students'">
          <dt>Группа</dt>
          <dd>{{ groups.find((value) => value.id === selectedGroupId)?.name ?? '—' }}</dd>
        </div>
        <div v-if="scope === 'students'">
          <dt>Предмет</dt>
          <dd>{{ subjects.find((value) => value.id === selectedSubjectId)?.name ?? '—' }}</dd>
        </div>
        <div>
          <dt>Типы</dt>
          <dd>{{ selectedTypeLabel }}</dd>
        </div>
        <div>
          <dt>Период</dt>
          <dd>{{ stats.periodFrom ?? '—' }} — {{ stats.periodTo ?? '—' }}</dd>
        </div>
        <div>
          <dt>Учтено пар</dt>
          <dd>{{ stats.lessonsCount }}</dd>
        </div>
      </dl>

      <section
        v-if="stats && statsQuery"
        class="teacher-stats__export"
        aria-label="Выгрузка статистики"
        :aria-busy="exportLoading || formatsLoading"
      >
        <p
          v-if="formatsLoading"
          class="teacher-stats__state"
          aria-live="polite"
        >
          Загружаем форматы выгрузки…
        </p>
        <label v-else-if="formats.length > 0">
          <span>Формат файла</span>
          <select
            v-model="selectedFormat"
            :disabled="exportLoading"
          >
            <option
              v-for="format in formats"
              :key="format.code"
              :value="format.code"
            >{{ format.label }}</option>
          </select>
        </label>
        <button
          type="button"
          class="teacher-stats__apply"
          :disabled="!canExport"
          @click="exportStats"
        >
          {{ exportLoading ? 'Готовим файл…' : 'Скачать' }}
        </button>
      </section>
      <p
        v-if="exportError"
        class="teacher-stats__state teacher-stats__state--error"
        role="alert"
      >
        {{ exportError }}
      </p>

      <p
        v-if="scope === 'students' && !selectedGroupId"
        class="teacher-stats__state"
      >
        Выбери группу.
      </p>
      <p
        v-else-if="scope === 'students' && !selectedSubjectId"
        class="teacher-stats__state"
      >
        Выбери предмет.
      </p>
      <p
        v-else-if="scope === 'students' && stats && stats.lessonsCount === 0"
        class="teacher-stats__state"
      >
        Пар ещё не было по выбранному контексту.
      </p>
      <div
        v-else-if="stats"
        class="teacher-stats__table-wrap"
      >
        <table class="teacher-stats__table">
          <thead>
            <tr>
              <th>
                <button
                  type="button"
                  @click="setSort(scope === 'students' ? 'displayName' : 'groupName')"
                >
                  {{ scope === 'students' ? 'Студент' : 'Группа' }}{{ sortMark(scope === 'students' ? 'displayName' : 'groupName') }}
                </button>
              </th>
              <th>
                <button
                  type="button"
                  @click="setSort('present')"
                >
                  % «+»{{ sortMark('present') }}
                </button>
              </th>
              <th>
                <button
                  type="button"
                  @click="setSort('presentOrExcused')"
                >
                  % «+ и у»{{ sortMark('presentOrExcused') }}
                </button>
              </th>
              <th>
                <button
                  type="button"
                  @click="setSort('excused')"
                >
                  % «у»{{ sortMark('excused') }}
                </button>
              </th>
              <th>
                <button
                  type="button"
                  @click="setSort('absent')"
                >
                  % «н»{{ sortMark('absent') }}
                </button>
              </th>
              <th v-if="scope === 'groups'">
                <button
                  type="button"
                  @click="setSort('lessonsCount')"
                >
                  Пар{{ sortMark('lessonsCount') }}
                </button>
              </th>
            </tr>
          </thead>
          <tbody v-if="scope === 'groups'">
            <tr
              v-for="row in groupRows"
              :key="row.groupId"
            >
              <th scope="row">
                <button
                  type="button"
                  class="teacher-stats__row-link"
                  @click="openGroup(row.groupId)"
                >
                  {{ row.groupName }}
                </button>
              </th>
              <td>{{ formatMetric(row.present) }}</td>
              <td>{{ formatMetric(row.presentOrExcused) }}</td>
              <td>{{ formatMetric(row.excused) }}</td>
              <td>{{ formatMetric(row.absent) }}</td>
              <td>{{ row.lessonsCount }}</td>
            </tr>
          </tbody>
          <tbody v-else>
            <tr
              v-for="row in studentRows"
              :key="row.studentId"
            >
              <th scope="row">
                {{ row.displayName }}
              </th>
              <td>{{ formatMetric(row.present) }}</td>
              <td>{{ formatMetric(row.presentOrExcused) }}</td>
              <td>{{ formatMetric(row.excused) }}</td>
              <td>{{ formatMetric(row.absent) }}</td>
            </tr>
          </tbody>
        </table>
        <p
          v-if="visibleRowCount === 0"
          class="teacher-stats__state"
        >
          По заданному фильтру данных нет.
        </p>
      </div>
    </template>
  </main>
</template>
