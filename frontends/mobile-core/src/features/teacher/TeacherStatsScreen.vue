<script setup lang="ts">
import ExportButton from '../../shared/components/ExportButton.vue';
import { toTeacherStatsReportSelector } from './teacher-client';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import type { ReportDownloadPort } from '../../shared/report-download-client';
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherJournalQuery,
  type TeacherStatsMetric,
  type TeacherStatsQuery,
  type TeacherStatsResponse,
  type TeacherStatsSubjectOption,
  type TeacherStatsScope,
  type TeacherSearchResponse,
  type TeacherStatsSort,
  type TeacherStatsFilter,
} from './teacher-client';
import {
  readRecoverableTeacherStatsContext,
  resetTeacherStatsCriteria,
  writeTeacherStatsContext,
  parseTeacherStatsRanges,
  teacherStatsNumericColumns,
  teacherStatsSortColumns,
  teacherStatsQueryForContext,
  type TeacherStatsRangeDraft,
  type TeacherStatsRouteContext,
} from './teacher-stats-route';
import HeadmanStatsSelect from '../headman-stats/HeadmanStatsSelect.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import './teacher-stats-screen.pcss';

const props = withDefaults(
  defineProps<{
    api: TeacherApi | null;
    semesterId: number | null;
    initialGroupId?: number | null;
    reportDownload?: ReportDownloadPort | null;
  }>(),
  {
    initialGroupId: null,
    reportDownload: null,
  },
);

const emit = defineEmits<{
  back: [];
  error: [cause: unknown];
  'open-journal': [query: TeacherJournalQuery];
  'open-group': [groupId: number, groupName: string];
}>();

interface TeacherStatsScopeMemory {
  lessonTypes: string[];
  sorts: TeacherStatsSort[];
  filters: TeacherStatsFilter[];
  ranges: Record<string, TeacherStatsRangeDraft>;
}

function createDefaultScopeMemory(): Record<TeacherStatsScope, TeacherStatsScopeMemory> {
  return {
    students: {
      lessonTypes: [],
      sorts: [{ column: 'present', descending: false }],
      filters: [],
      ranges: {},
    },
    groups: {
      lessonTypes: [],
      sorts: [{ column: 'present', descending: false }],
      filters: [],
      ranges: {},
    },
  };
}

const scope = ref<TeacherStatsScope>('groups');
const authorizedGroups = ref<readonly { id: number; name: string }[]>([]);
const catalogSubjectOptions = ref<readonly TeacherStatsSubjectOption[]>([]);
const authorizedSubjectOptions = ref<readonly TeacherStatsSubjectOption[]>([]);
const selectedGroupId = ref<number | null>(props.initialGroupId);
const selectedSubjectId = ref<number | null>(null);
const scopeMemory = ref(createDefaultScopeMemory());
const selectedTypes = computed({
  get: () => scopeMemory.value[scope.value].lessonTypes,
  set: (value: string[]) => {
    scopeMemory.value[scope.value].lessonTypes = value;
  },
});
const directoryInput = ref<HTMLInputElement | null>(null);
const directoryQuery = ref('');
const directoryResult = ref<TeacherSearchResponse | null>(null);
const directoryLoading = ref(false);
const directoryError = ref<string | null>(null);
const directoryValidation = computed(() =>
  directoryQuery.value.length > 128
    ? 'Не больше 128 символов.'
    : directoryQuery.value.trim().length === 1
      ? 'Введи хотя бы 2 символа.'
      : null,
);
let directoryRevision = 0;
let directoryTimer: ReturnType<typeof setTimeout> | null = null;
const stats = ref<TeacherStatsResponse | null>(null);
const statsQuery = ref<TeacherStatsQuery | null>(null);
const contextLoading = ref(false);
const statsLoading = ref(false);
const loading = computed(() => contextLoading.value || statsLoading.value);
let contextRevision = 0;
const contextReady = ref(false);
const canChooseContext = computed(() => contextReady.value && authorizedGroups.value.length > 0);
const error = ref<string | null>(null);
const revision = ref(0);
const sorts = computed({
  get: () => scopeMemory.value[scope.value].sorts,
  set: (value: TeacherStatsSort[]) => {
    scopeMemory.value[scope.value].sorts = value;
  },
});
const criteriaError = ref<string | null>(null);
const fieldErrors = ref<Record<string, string>>({});
const filtersOpen = ref(false);
const metrics = [
  { column: 'present', label: 'Присутствия', symbols: ['+'] },
  { column: 'presentOrExcused', label: 'Присутствия и уважительные причины', symbols: ['+', 'у'] },
  { column: 'excused', label: 'Уважительные причины', symbols: ['у'] },
  { column: 'absent', label: 'Отсутствия', symbols: ['н'] },
] as const;
const visibleRows = computed(() =>
  scope.value === 'groups'
    ? groupRows.value.map((row) => ({
        ...row,
        key: row.groupId,
        name: row.groupName,
        groupId: row.groupId,
      }))
    : studentRows.value.map((row) => ({
        ...row,
        key: row.studentId,
        name: row.displayName,
        groupId: null,
      })),
);
const groupOptions = computed(() =>
  groups.value.map((group) => ({ value: String(group.id), label: group.name })),
);
const subjectOptions = computed(() =>
  subjects.value.map((subject) => ({ value: String(subject.id), label: subject.name })),
);
const visibleSorts = computed(() =>
  sorts.value.length ? sorts.value : [{ column: 'present', descending: false }],
);
const activeCriteria = computed(
  () => selectedTypes.value.length > 0 || scopeMemory.value[scope.value].filters.length > 0,
);
const routeNotice = ref<string | null>(null);
const numericColumns = computed(() => teacherStatsNumericColumns(scope.value));
const displayedStats = computed(() =>
  !loading.value &&
  !error.value &&
  statsQuery.value &&
  JSON.stringify(statsQuery.value) === JSON.stringify(currentStatsQuery())
    ? stats.value
    : null,
);
const canReset = computed(
  () =>
    selectedTypes.value.length > 0 ||
    sorts.value.length > 0 ||
    scopeMemory.value[scope.value].filters.length > 0 ||
    Object.values(scopeMemory.value[scope.value].ranges).some(
      (range) => range.minimum !== '' || range.maximum !== '',
    ),
);
let disposed = false;

const groups = computed(() => authorizedGroups.value);
const singleAuthorizedGroup = computed(() =>
  contextReady.value && scope.value === 'groups' && groups.value.length === 1
    ? groups.value[0]
    : null,
);
const subjects = computed(() => subjectsForGroup(selectedGroupId.value));
const types = computed(() =>
  scope.value === 'groups'
    ? lessonTypesForContext(null, null)
    : selectedGroupId.value && selectedSubjectId.value
      ? lessonTypesForContext(selectedGroupId.value, selectedSubjectId.value)
      : [],
);
const groupRows = computed(() => displayedStats.value?.groups ?? []);
const studentRows = computed(() => displayedStats.value?.students ?? []);
const visibleRowCount = computed(() =>
  scope.value === 'groups' ? groupRows.value.length : studentRows.value.length,
);

watch(
  () => [props.api, props.semesterId, props.initialGroupId] as const,
  () => {
    clearDirectorySearch();
    selectedGroupId.value = props.initialGroupId;
    selectedSubjectId.value = null;
    scope.value = 'groups';
    scopeMemory.value = createDefaultScopeMemory();
    stats.value = null;
    statsQuery.value = null;
    authorizedGroups.value = [];
    catalogSubjectOptions.value = [];
    authorizedSubjectOptions.value = [];
    criteriaError.value = null;
    routeNotice.value = null;
    fieldErrors.value = {};
    filtersOpen.value = false;
    void loadContext();
  },
  { immediate: true, flush: 'sync' },
);

onBeforeUnmount(() => {
  disposed = true;
  cancelDirectoryRequest();
  contextRevision += 1;
  revision.value += 1;
});

function cancelDirectoryRequest(): void {
  directoryRevision += 1;
  if (directoryTimer !== null) clearTimeout(directoryTimer);
  directoryTimer = null;
  directoryLoading.value = false;
  directoryResult.value = null;
  directoryError.value = null;
}
function clearDirectorySearch(restoreFocus = false): void {
  cancelDirectoryRequest();
  directoryQuery.value = '';
  if (restoreFocus) void nextTick(() => directoryInput.value?.focus());
}
watch(
  directoryQuery,
  () => {
    cancelDirectoryRequest();
    if (!directoryQuery.value.trim() || directoryValidation.value) return;
    directoryTimer = setTimeout(() => {
      directoryTimer = null;
      void loadDirectorySearch();
    }, 250);
  },
  { flush: 'sync' },
);
async function loadDirectorySearch(): Promise<void> {
  cancelDirectoryRequest();
  const api = props.api;
  const semesterId = props.semesterId;
  const query = directoryQuery.value;
  if (!api || !semesterId || !query.trim() || directoryValidation.value) return;
  const current = directoryRevision;
  const isCurrentSearch = () =>
    !disposed &&
    current === directoryRevision &&
    api === props.api &&
    semesterId === props.semesterId &&
    query === directoryQuery.value;
  directoryLoading.value = true;
  try {
    const response = await api.search(semesterId, query);
    if (isCurrentSearch()) directoryResult.value = response;
  } catch (cause) {
    if (!isCurrentSearch() || cause instanceof StaleSessionGenerationError) return;
    directoryError.value =
      cause instanceof TeacherApiError
        ? cause.message
        : 'Не удалось выполнить поиск. Попробуй ещё раз.';
    emit('error', cause);
  } finally {
    if (isCurrentSearch()) directoryLoading.value = false;
  }
}
function chooseDirectoryGroup(id: string): void {
  if (!canChooseContext.value) return;
  if (!groups.value.some((group) => String(group.id) === id)) {
    directoryError.value = 'Назначения изменились. Обнови экран.';
    directoryResult.value = null;
    return;
  }
  openGroup(Number(id));
}

async function loadContext(): Promise<void> {
  contextReady.value = false;
  const api = props.api;
  const semesterId = props.semesterId;
  const current = ++contextRevision;
  revision.value += 1;
  statsLoading.value = false;
  contextLoading.value = true;
  error.value = null;
  try {
    if (!api || !semesterId) {
      authorizedGroups.value = [];
      catalogSubjectOptions.value = [];
      authorizedSubjectOptions.value = [];
      stats.value = null;
      statsQuery.value = null;
      return;
    }
    const catalog = await api.catalog(semesterId);
    if (!isCurrentContext(current)) return;
    authorizedGroups.value = catalog.groups;
    catalogSubjectOptions.value = mergeSubjectOptions(
      catalog.taughtSubjectOptions,
      catalog.subjectOptions,
    );
    authorizedSubjectOptions.value = mergeSubjectOptions(catalogSubjectOptions.value, []);
    restoreContext(semesterId);
    persistContext();
    contextReady.value = true;
    contextLoading.value = false;
    if (catalog.groups.length === 0) {
      stats.value = null;
      statsQuery.value = null;
      return;
    }
    await loadStatsForRevision(++revision.value);
  } catch (cause) {
    if (!isCurrentContext(current) || cause instanceof StaleSessionGenerationError) return;
    error.value =
      cause instanceof TeacherApiError
        ? cause.message
        : 'Не удалось получить назначения преподавателя.';
    emit('error', cause);
  } finally {
    if (isCurrentContext(current)) contextLoading.value = false;
  }
}

async function loadStats(): Promise<void> {
  if (!canChooseContext.value) return;
  const current = ++revision.value;
  await loadStatsForRevision(current);
}

async function loadStatsForRevision(current: number): Promise<void> {
  const api = props.api;
  const semesterId = props.semesterId;
  stats.value = null;
  statsQuery.value = null;
  statsLoading.value = true;
  error.value = null;
  try {
    if (!api || !semesterId) {
      stats.value = null;
      statsQuery.value = null;
      return;
    }
    const query = currentStatsQuery();
    if (!query) {
      stats.value = null;
      statsQuery.value = null;
      return;
    }
    const response = await api.stats(query);
    if (!isCurrent(current)) return;
    const currentAssignmentOptions = catalogSubjectOptions.value.filter(
      (option) => query.groupId == null || option.groupId === query.groupId,
    );
    const responseOptions = mergeSubjectOptions(currentAssignmentOptions, response.subjectOptions);
    authorizedSubjectOptions.value =
      query.groupId == null
        ? responseOptions
        : [
            ...authorizedSubjectOptions.value.filter((option) => option.groupId !== query.groupId),
            ...responseOptions,
          ];
    stats.value = response;
    statsQuery.value = query;
  } catch (cause) {
    if (!isCurrent(current) || cause instanceof StaleSessionGenerationError) return;
    error.value =
      cause instanceof TeacherApiError ? cause.message : 'Не удалось получить статистику.';
    emit('error', cause);
  } finally {
    if (isCurrent(current)) statsLoading.value = false;
  }
}

function currentStatsQuery(): TeacherStatsQuery | null {
  if (
    !contextReady.value ||
    groups.value.length === 0 ||
    (scope.value === 'students' &&
      (!groups.value.some((group) => group.id === selectedGroupId.value) ||
        !subjects.value.some((subject) => subject.id === selectedSubjectId.value))) ||
    !selectedTypes.value.length ||
    selectedTypes.value.some((type) => !types.value.includes(type))
  )
    return null;
  return teacherStatsQueryForContext(currentRouteContext());
}

function restoreContext(semesterId: number): void {
  const restored = readRecoverableTeacherStatsContext();
  const saved = restored.context;
  routeNotice.value = restored.notice;
  if (!saved || saved.semesterId !== semesterId) {
    scope.value = 'groups';
    selectedGroupId.value = groups.value.some((group) => group.id === props.initialGroupId)
      ? props.initialGroupId
      : null;
    selectedSubjectId.value = null;
    selectedTypes.value = [];
    sorts.value = [{ column: 'present', descending: false }];
    return;
  }

  scope.value = saved.scope;
  selectedGroupId.value = groups.value.some((group) => group.id === saved.groupId)
    ? saved.groupId
    : null;
  selectedSubjectId.value = subjectsForGroup(selectedGroupId.value).some(
    (subject) => subject.id === saved.subjectId,
  )
    ? saved.subjectId
    : null;
  selectedTypes.value =
    scope.value === 'groups' || selectedSubjectId.value
      ? saved.lessonTypes.filter((type) => types.value.includes(type))
      : [];
  sorts.value = saved.sorts.map((sort) => ({ ...sort }));
  scopeMemory.value[scope.value].filters = saved.filters.map((filter) => ({ ...filter }));
  for (const filter of saved.filters) {
    rangeDraft(filter.column).minimum = String(filter.minPercent ?? filter.minValue ?? '');
    rangeDraft(filter.column).maximum = String(filter.maxPercent ?? filter.maxValue ?? '');
  }
}

function persistContext(): void {
  writeTeacherStatsContext(currentRouteContext());
}

function currentRouteContext(): TeacherStatsRouteContext {
  return {
    semesterId: props.semesterId,
    scope: scope.value,
    groupId: scope.value === 'groups' ? null : selectedGroupId.value,
    subjectId: scope.value === 'groups' ? null : selectedSubjectId.value,
    lessonTypes: selectedTypes.value,
    search: '',
    sorts: sorts.value,
    filters: scopeMemory.value[scope.value].filters,
  };
}

function switchScope(next: TeacherStatsScope): void {
  if (!canChooseContext.value || scope.value === next) return;
  scope.value = next;
  criteriaError.value = null;
  fieldErrors.value = {};
  filtersOpen.value = false;
  persistContext();
  void loadStats();
}

function changeGroup(rawValue: string): void {
  if (!canChooseContext.value) return;
  const nextGroupId = positiveInteger(rawValue);
  const validGroupId =
    nextGroupId && groups.value.some((value) => value.id === nextGroupId) ? nextGroupId : null;
  selectedGroupId.value = validGroupId;
  selectedSubjectId.value = null;
  clearChildCriteria();
  persistContext();
  void loadStats();
}

function changeSubject(rawValue: string): void {
  if (!canChooseContext.value) return;
  const nextSubjectId = positiveInteger(rawValue);
  selectedSubjectId.value =
    nextSubjectId && subjects.value.some((value) => value.id === nextSubjectId)
      ? nextSubjectId
      : null;
  clearChildCriteria();
  persistContext();
  void loadStats();
}

function clearChildCriteria(): void {
  scopeMemory.value.students.lessonTypes = [];
}

function toggleType(type: string): void {
  if (!canChooseContext.value || !types.value.includes(type)) return;
  const next = selectedTypes.value.includes(type)
    ? selectedTypes.value.filter((value) => value !== type)
    : [...selectedTypes.value, type];
  selectedTypes.value = next;
  persistContext();
  void loadStats();
}

function sortOptions(index: number): Array<{ value: string; label: string }> {
  const used = visibleSorts.value
    .filter((_, position) => position !== index)
    .map((sort) => sort.column);
  return teacherStatsSortColumns(scope.value)
    .filter((column) => !used.includes(column))
    .flatMap((column) => [
      {
        value: column,
        label: `${columnLabel(column)}: ${column === 'displayName' || column === 'groupName' ? 'А–Я' : 'сначала ниже'}`,
      },
      {
        value: `-${column}`,
        label: `${columnLabel(column)}: ${column === 'displayName' || column === 'groupName' ? 'Я–А' : 'сначала выше'}`,
      },
    ]);
}
function changeSort(index: number, value: string): void {
  if (!canChooseContext.value) return;
  const column = value.replace(/^-/, '');
  if (!sortOptions(index).some((option) => option.value === value)) return;
  const next = visibleSorts.value.map((sort) => ({ ...sort }));
  next[index] = { column, descending: value.startsWith('-') };
  sorts.value = next;
  persistContext();
  void loadStats();
}
function addSort(): void {
  if (!canChooseContext.value) return;
  const column = teacherStatsSortColumns(scope.value).find(
    (value) => !visibleSorts.value.some((sort) => sort.column === value),
  );
  if (!column) return;
  sorts.value = [...visibleSorts.value, { column, descending: false }];
  persistContext();
  void loadStats();
}
function removeSort(index: number): void {
  if (!canChooseContext.value) return;
  sorts.value = visibleSorts.value.filter((_, position) => position !== index);
  persistContext();
  void loadStats();
}
function applyFilters(): void {
  if (!canChooseContext.value) return;
  fieldErrors.value = {};
  for (const column of numericColumns.value) {
    try {
      parseTeacherStatsRanges(scope.value, { [column]: rangeDraft(column) });
    } catch (cause) {
      fieldErrors.value[column] = cause instanceof RangeError ? cause.message : 'Проверь диапазон.';
    }
  }
  if (Object.keys(fieldErrors.value).length) return;
  scopeMemory.value[scope.value].filters = parseTeacherStatsRanges(
    scope.value,
    scopeMemory.value[scope.value].ranges,
  );
  criteriaError.value = null;
  filtersOpen.value = false;
  persistContext();
  void loadStats();
}

function rangeDraft(column: string): TeacherStatsRangeDraft {
  const ranges = scopeMemory.value[scope.value].ranges;
  return (ranges[column] ??= { minimum: '', maximum: '' });
}

function resetCriteria(): void {
  if (!canChooseContext.value) return;
  const cleared = resetTeacherStatsCriteria(currentRouteContext());
  selectedTypes.value = [...cleared.lessonTypes];
  sorts.value = [...cleared.sorts];
  scopeMemory.value[scope.value].filters = [...cleared.filters];
  scopeMemory.value[scope.value].ranges = {};
  criteriaError.value = null;
  fieldErrors.value = {};
  routeNotice.value = null;
  persistContext();
  void loadStats();
}

function lessonTypeLabel(type: string): string {
  return (
    ({ LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' } as Record<string, string>)[
      type.toUpperCase()
    ] ?? 'Занятие'
  );
}

function columnLabel(column: string): string {
  return (
    (
      {
        displayName: 'Имя',
        groupName: 'Название группы',
        present: 'Присутствия',
        presentOrExcused: 'Присутствия и уважительные',
        excused: 'Уважительные причины',
        absent: 'Отсутствия',
        lessonsCount: 'Количество пар',
      } as Record<string, string>
    )[column] ?? column
  );
}

function openGroup(groupId: number): void {
  if (!canChooseContext.value || !groups.value.some((value) => value.id === groupId)) return;
  scope.value = 'students';
  // A group summary spans distinct authorized subjects; never infer one by its name.
  changeGroup(String(groupId));
}

function openJournal(): void {
  if (!selectedGroupId.value || !selectedSubjectId.value || !props.semesterId) return;
  emit('open-journal', {
    semesterId: props.semesterId,
    groupId: selectedGroupId.value,
    subjectId: selectedSubjectId.value,
    lessonTypes: [],
    pastOnly: true,
    page: 0,
    pageSize: 100,
  });
}

function formatMetric(metric: TeacherStatsMetric | undefined): string {
  if (!metric || metric.denominator === 0) return 'нет данных';
  return `${new Intl.NumberFormat('ru', { maximumFractionDigits: 1 }).format(metric.percent)} % · ${metric.numerator}/${metric.denominator}`;
}

function mergeSubjectOptions(
  assignments: readonly TeacherStatsSubjectOption[],
  historical: readonly TeacherStatsSubjectOption[],
): TeacherStatsSubjectOption[] {
  const values = new Map<string, TeacherStatsSubjectOption>();
  // Server metadata comes last so its name wins for an existing subject.
  for (const option of [...assignments, ...historical]) {
    const key = `${option.groupId}:${option.subjectId}`;
    const existing = values.get(key);
    values.set(key, {
      ...option,
      lessonTypes: [...new Set([...(existing?.lessonTypes ?? []), ...option.lessonTypes])],
    });
  }
  return [...values.values()];
}

function subjectsForGroup(groupId: number | null): Array<{ id: number; name: string }> {
  if (groupId == null) return [];
  const values = new Map<number, { id: number; name: string }>();
  for (const option of authorizedSubjectOptions.value) {
    if (option.groupId === groupId)
      values.set(option.subjectId, {
        id: option.subjectId,
        name: option.subjectName,
      });
  }
  return [...values.values()].sort((left, right) => left.name.localeCompare(right.name, 'ru'));
}

function lessonTypesForContext(groupId: number | null, subjectId: number | null): string[] {
  const values = new Set<string>();
  for (const option of authorizedSubjectOptions.value) {
    if (
      (groupId == null || option.groupId === groupId) &&
      (subjectId == null || option.subjectId === subjectId)
    ) {
      option.lessonTypes.forEach((value) => values.add(value));
    }
  }
  return [...values].sort((left, right) => left.localeCompare(right, 'ru'));
}

function positiveInteger(value: string | number | null | undefined): number | null {
  const text = String(value ?? '');
  if (!/^\d+$/.test(text)) return null;
  const parsed = Number(text);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function isCurrentContext(requestRevision: number): boolean {
  return !disposed && requestRevision === contextRevision;
}

function isCurrent(requestRevision: number): boolean {
  return !disposed && requestRevision === revision.value;
}
</script>

<template>
  <main
    class="teacher-stats"
    aria-labelledby="teacher-stats-title"
    :aria-busy="loading"
  >
    <ExportButton
      v-if="api && statsQuery"
      :port="reportDownload"
      :formats="() => api!.statsExportFormats()"
      :request="
        (format) => ({
          kind: 'TEACHER_STATS',
          teacherStats: toTeacherStatsReportSelector(statsQuery!, format),
        })
      "
      :scope-key="JSON.stringify(statsQuery)"
      :disabled="loading || !!error"
      @error="emit('error', $event)"
    />
    <h1 id="teacher-stats-title">
      Статистика
    </h1>
    <StudentWarningBlock
      v-if="routeNotice"
      title="Отбор восстановлен"
      :message="routeNotice"
    />
    <form
      class="teacher-stats__directory"
      role="search"
      aria-label="Поиск групп и студентов"
      @submit.prevent="loadDirectorySearch"
    >
      <label
        class="teacher-stats__field"
        for="teacher-stats-directory"
      ><span>Поиск групп и студентов</span><input
        id="teacher-stats-directory"
        ref="directoryInput"
        v-model="directoryQuery"
        type="search"
        autocomplete="off"
        :disabled="!api || !semesterId"
        :aria-invalid="Boolean(directoryValidation)"
        :aria-describedby="directoryValidation ? 'teacher-stats-directory-error' : undefined"
        placeholder="Название группы или полное имя"
      ></label>
      <button
        v-if="directoryQuery"
        class="teacher-stats__icon-button"
        type="button"
        aria-label="Очистить поиск"
        @click="clearDirectorySearch(true)"
      >
        <MobileIcon name="close" />
      </button>
      <p
        v-if="directoryValidation"
        id="teacher-stats-directory-error"
        class="teacher-stats__field-error"
        role="status"
      >
        {{ directoryValidation }}
      </p>
    </form>
    <section
      v-if="directoryLoading || directoryError || directoryResult"
      class="teacher-stats__directory-results"
      aria-label="Результаты поиска"
      :aria-busy="directoryLoading"
    >
      <p
        v-if="directoryLoading"
        role="status"
      >
        Ищем группы и студентов…
      </p>
      <StudentWarningBlock
        v-else-if="directoryError"
        title="Поиск недоступен"
        :message="directoryError"
        severity="error"
        action-label="Повторить поиск"
        @action="loadDirectorySearch"
      />
      <template v-else-if="directoryResult">
        <section aria-label="Найденные группы">
          <h2>Группы</h2><button
            v-for="group in directoryResult.groups"
            :key="group.id"
            type="button"
            :disabled="!canChooseContext"
            @click="chooseDirectoryGroup(group.id)"
          >
            {{ group.name }}
          </button><p
            v-if="!directoryResult.groups.length"
            role="status"
          >
            Групп не найдено.
          </p>
        </section>
        <section aria-label="Найденные студенты">
          <h2>Студенты</h2><button
            v-for="student in directoryResult.students"
            :key="`${student.groupId}:${student.id}`"
            type="button"
            :disabled="!canChooseContext"
            @click="chooseDirectoryGroup(student.groupId)"
          >
            <span>{{ student.displayName }}</span><span>{{ student.groupName }}</span>
          </button><p
            v-if="!directoryResult.students.length"
            role="status"
          >
            Студентов не найдено.
          </p>
        </section>
        <p
          v-if="directoryResult.truncated"
          role="status"
        >
          Показаны первые совпадения. Уточни запрос.
        </p>
      </template>
    </section>
    <div
      class="teacher-stats__tabs"
      aria-label="Разрез статистики"
    >
      <button
        type="button"
        :disabled="!canChooseContext"
        :aria-pressed="scope === 'students'"
        @click="switchScope('students')"
      >
        По студентам
      </button>
      <button
        type="button"
        :disabled="!canChooseContext"
        :aria-pressed="scope === 'groups'"
        @click="switchScope('groups')"
      >
        По моим группам
      </button>
    </div>
    <section
      v-if="scope === 'students'"
      class="teacher-stats__context"
      aria-label="Выбор контекста"
    >
      <div class="teacher-stats__field">
        <span>Группа</span><HeadmanStatsSelect
          label="Группа"
          :disabled="!canChooseContext"
          :model-value="String(selectedGroupId ?? '')"
          :options="groupOptions"
          placeholder="Выбери группу"
          @update:model-value="changeGroup"
        />
      </div>
      <div
        v-if="selectedGroupId"
        class="teacher-stats__field"
      >
        <span>Предмет</span><HeadmanStatsSelect
          label="Предмет"
          :model-value="String(selectedSubjectId ?? '')"
          :options="subjectOptions"
          placeholder="Выбери предмет"
          :disabled="!canChooseContext || !selectedGroupId"
          @update:model-value="changeSubject"
        />
      </div>
    </section>
    <fieldset
      v-if="types.length"
      class="teacher-stats__types"
      :disabled="!canChooseContext"
    >
      <legend>Тип занятия</legend><button
        v-for="type in types"
        :key="type"
        type="button"
        :aria-pressed="selectedTypes.includes(type)"
        @click="toggleType(type)"
      >
        {{ lessonTypeLabel(type) }}
      </button>
    </fieldset>
    <div
      class="teacher-stats__toolbar"
      data-teacher-action="stats-sort"
    >
      <HeadmanStatsSelect
        label="Сортировка"
        :disabled="!canChooseContext"
        :model-value="`${visibleSorts[0]?.descending ? '-' : ''}${visibleSorts[0]?.column}`"
        :options="sortOptions(0)"
        @update:model-value="changeSort(0, $event)"
      />
      <button
        type="button"
        class="teacher-stats__icon-button"
        aria-label="Фильтры"
        :disabled="!canChooseContext"
        data-teacher-action="stats-filters"
        :aria-expanded="filtersOpen"
        aria-controls="teacher-stats-filters"
        :data-active="filtersOpen || activeCriteria"
        @click="filtersOpen = !filtersOpen"
      >
        <MobileIcon name="filter" />
      </button>
    </div>
    <div
      v-for="(sort, index) in visibleSorts.slice(1)"
      :key="sort.column"
      class="teacher-stats__toolbar"
    >
      <HeadmanStatsSelect
        :label="`Условие сортировки ${index + 2}`"
        :disabled="!canChooseContext"
        :model-value="`${sort.descending ? '-' : ''}${sort.column}`"
        :options="sortOptions(index + 1)"
        @update:model-value="changeSort(index + 1, $event)"
      />
      <button
        type="button"
        class="teacher-stats__icon-button"
        :aria-label="`Удалить условие ${index + 2}`"
        :disabled="!canChooseContext"
        @click="removeSort(index + 1)"
      >
        <MobileIcon name="close" />
      </button>
    </div>
    <button
      v-if="visibleSorts.length < teacherStatsSortColumns(scope).length"
      type="button"
      class="teacher-stats__add-sort"
      :disabled="!canChooseContext"
      @click="addSort"
    >
      <MobileIcon name="plus" />Добавить условие сортировки
    </button>
    <form
      v-if="filtersOpen"
      id="teacher-stats-filters"
      class="teacher-stats__filters"
      @submit.prevent="applyFilters"
    >
      <h2>Фильтры</h2>
      <fieldset
        v-for="column in numericColumns"
        :key="column"
        class="teacher-stats__range"
        :aria-invalid="Boolean(fieldErrors[column])"
        :aria-describedby="fieldErrors[column] ? `teacher-stats-${column}-error` : undefined"
        :disabled="!canChooseContext"
      >
        <legend>{{ columnLabel(column) }}{{ column === 'lessonsCount' ? '' : ', %' }}</legend><label :for="`teacher-stats-${column}-min`"><span>От</span><input
          :id="`teacher-stats-${column}-min`"
          v-model="rangeDraft(column).minimum"
          inputmode="decimal"
          :aria-invalid="Boolean(fieldErrors[column])"
          :aria-describedby="
            fieldErrors[column] ? `teacher-stats-${column}-error` : undefined
          "
        ></label><label :for="`teacher-stats-${column}-max`"><span>До</span><input
          :id="`teacher-stats-${column}-max`"
          v-model="rangeDraft(column).maximum"
          inputmode="decimal"
          :aria-invalid="Boolean(fieldErrors[column])"
          :aria-describedby="
            fieldErrors[column] ? `teacher-stats-${column}-error` : undefined
          "
        ></label><p
          v-if="fieldErrors[column]"
          :id="`teacher-stats-${column}-error`"
          class="teacher-stats__field-error"
        >
          {{ fieldErrors[column] }}
        </p>
      </fieldset>
      <div class="teacher-stats__filter-actions">
        <button
          type="button"
          class="teacher-stats__icon-button"
          aria-label="Сбросить фильтры и сортировку"
          :disabled="!canChooseContext || !canReset"
          @click="resetCriteria"
        >
          <MobileIcon name="retry" />
        </button><button
          type="submit"
          class="teacher-stats__apply"
          :disabled="!canChooseContext"
        >
          Применить
        </button>
      </div>
    </form>
    <StudentWarningBlock
      v-if="error"
      title="Статистика недоступна"
      :message="error"
      severity="error"
      action-label="Повторить"
      @action="loadContext"
    />
    <p
      v-else-if="loading"
      class="teacher-stats__state"
      role="status"
    >
      Загружаем статистику…
    </p>
    <p
      v-else-if="!api || !semesterId"
      class="teacher-stats__state"
      role="status"
    >
      Сессия преподавателя недоступна.
    </p>
    <p
      v-else-if="contextReady && groups.length === 0"
      class="teacher-stats__state"
      role="status"
    >
      Нет доступных групп.
    </p>
    <section
      v-else-if="singleAuthorizedGroup"
      class="teacher-stats__state"
    >
      <p role="status">
        У тебя одна группа — сравнивать не с чем.
      </p>
      <div class="teacher-stats__journal">
        <button
          type="button"
          @click="openGroup(singleAuthorizedGroup.id)"
        >
          Перейти к студентам
        </button>
      </div>
    </section>
    <p
      v-else-if="!currentStatsQuery()"
      class="teacher-stats__state"
      role="status"
    >
      {{ scope === 'groups'
        ? 'Выбери хотя бы один тип занятия. Учитываем только твои предметы в доступных группах.'
        : 'Выбери группу, предмет и хотя бы один тип занятия.' }}
    </p>
    <template v-else-if="displayedStats">
      <h2 class="teacher-stats__results-title">
        {{ scope === 'groups' ? 'Мои группы' : 'Студенты'
        }}<span v-if="scope === 'students'"> · Пар учтено: {{ displayedStats.lessonsCount }}</span>
      </h2>
      <p
        v-if="visibleRowCount === 0"
        class="teacher-stats__state"
        role="status"
      >
        {{
          activeCriteria ? 'По заданным фильтрам данных нет.' : 'Пока нет данных для статистики.'
        }}
      </p>
      <div
        v-else
        class="teacher-stats__cards"
      >
        <article
          v-for="row in visibleRows"
          :key="row.key"
          class="teacher-stats__card"
        >
          <h3>
            <button
              v-if="row.groupId"
              type="button"
              @click="openGroup(row.groupId)"
            >
              <span class="teacher-stats__row-name">{{ row.name }}</span><span
                v-if="'lessonsCount' in row"
                class="teacher-stats__lesson-count"
              >{{ row.lessonsCount }} пар</span><MobileIcon name="chevron-down" />
            </button><template v-else>
              {{ row.name }}
            </template>
          </h3>
          <dl class="teacher-stats__metrics">
            <div
              v-for="metric in metrics"
              :key="metric.column"
            >
              <dt :aria-label="metric.label">
                <span
                  v-for="symbol in metric.symbols"
                  :key="symbol"
                  aria-hidden="true"
                  :class="[
                    'teacher-stats__symbol',
                    {
                      'teacher-stats__symbol--present': symbol === '+',
                      'teacher-stats__symbol--excused': symbol === 'у',
                      'teacher-stats__symbol--absent': symbol === 'н',
                    },
                  ]"
                />
              </dt><dd>{{ formatMetric(row[metric.column]) }}</dd>
            </div>
          </dl>
        </article>
      </div>
      <div
        v-if="scope === 'students' && selectedSubjectId && types.length"
        class="teacher-stats__journal"
      >
        <button
          type="button"
          @click="openJournal"
        >
          Открыть журнал
        </button>
      </div>
    </template>
  </main>
</template>
