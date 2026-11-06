<script setup lang="ts">
import { useProjectionRefresh } from '../../shared/projection-refresh';
import ExportButton from '../../shared/components/ExportButton.vue';
import { toTeacherStatsReportSelector } from './teacher-client';
import { computed, nextTick, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
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
import emptyFilterArtwork from './teacher-stats-empty-filter.svg?raw';
import { teacherLessonTypeLabel } from './teacher-ui-format';
import './teacher-stats-screen.pcss';

const props = withDefaults(
  defineProps<{
    api: TeacherApi | null;
    semesterId: number | null;
    initialGroupId?: number | null;
    closeToolSignal?: number;
    realtimeRevision?: number;
    reportDownload?: ReportDownloadPort | null;
  }>(),
  {
    initialGroupId: null,
    closeToolSignal: 0,
    realtimeRevision: 0,
    reportDownload: null,
  },
);

const emit = defineEmits<{
  'tool-open': [open: boolean];
  error: [cause: unknown];
  'open-journal': [query: TeacherJournalQuery];
  'open-group': [groupId: number, groupName: string];
}>();

interface TeacherStatsScopeMemory {
  lessonTypes: string[];
  sorts: TeacherStatsSort[];
  filters: TeacherStatsFilter[];
}

function createDefaultScopeMemory(): Record<TeacherStatsScope, TeacherStatsScopeMemory> {
  return {
    students: {
      lessonTypes: [],
      sorts: [{ column: 'present', descending: false }],
      filters: [],
    },
    groups: {
      lessonTypes: [],
      sorts: [{ column: 'present', descending: false }],
      filters: [],
    },
  };
}

const scope = ref<TeacherStatsScope>('students');
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
const selectedStudent = ref<TeacherSearchResponse['students'][number] | null>(null);
const directoryStudents = computed(() =>
  (directoryResult.value?.students ?? []).filter((student) =>
    groups.value.some((group) => String(group.id) === student.groupId),
  ),
);
const directoryProjectionReady = ref(false);
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
let directoryProjectionEpoch = 0;
let directoryTimer: ReturnType<typeof setTimeout> | null = null;
const stats = ref<TeacherStatsResponse | null>(null);
const statsQuery = ref<TeacherStatsQuery | null>(null);
const contextLoading = ref(false);
const statsLoading = ref(false);
const loading = computed(() => contextLoading.value || statsLoading.value);
let contextRevision = 0;
const contextReady = ref(false);
const canChooseContext = computed(() => contextReady.value && authorizedGroups.value.length > 0);
const canSearchDirectory = computed(() => canChooseContext.value && directoryProjectionReady.value);
const error = ref<string | null>(null);
const projectionError = ref<string | null>(null);
const revision = ref(0);
const sorts = computed({
  get: () => scopeMemory.value[scope.value].sorts,
  set: (value: TeacherStatsSort[]) => {
    scopeMemory.value[scope.value].sorts = value;
  },
});
const fieldErrors = ref<Record<string, string>>({});
const activeTool = ref<'filters' | 'sort' | null>(null);
const filtersOpen = computed(() => activeTool.value === 'filters');
const filterDrafts = ref<Record<string, TeacherStatsRangeDraft>>({});
const sortDrafts = ref<TeacherStatsSort[]>([]);
const toolPanel = ref<HTMLElement | null>(null);
const filterTrigger = ref<HTMLButtonElement | null>(null);
const sortTrigger = ref<HTMLButtonElement | null>(null);
watch(activeTool, (tool) => emit('tool-open', tool !== null), { flush: 'sync' });
watch(
  () => props.closeToolSignal,
  () => closeTool(true),
);
onDeactivated(() => closeTool());
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
        selected: false,
      }))
    : studentRows.value.map((row) => ({
        ...row,
        key: row.studentId,
        name: row.displayName,
        groupId: null,
        selected:
          selectedStudent.value?.id === String(row.studentId) &&
          selectedStudent.value?.groupId === String(selectedGroupId.value),
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
const activeCriteria = computed(() => scopeMemory.value[scope.value].filters.length > 0);
const activeSort = computed(
  () =>
    visibleSorts.value.length > 1 ||
    visibleSorts.value[0]?.column !== 'present' ||
    visibleSorts.value[0]?.descending,
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
    scope.value = 'students';
    scopeMemory.value = createDefaultScopeMemory();
    stats.value = null;
    statsQuery.value = null;
    authorizedGroups.value = [];
    catalogSubjectOptions.value = [];
    authorizedSubjectOptions.value = [];
    routeNotice.value = null;
    fieldErrors.value = {};
    closeTool();
    void loadContext();
  },
  { immediate: true, flush: 'sync' },
);

onBeforeUnmount(() => {
  disposed = true;
  cancelDirectoryRequest();
  closeTool();
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
  selectedStudent.value = null;
  if (restoreFocus) void nextTick(() => directoryInput.value?.focus?.());
}
watch(
  directoryQuery,
  () => {
    cancelDirectoryRequest();
    selectedStudent.value = null;
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
  if (!canSearchDirectory.value || !api || !semesterId || !query.trim() || directoryValidation.value)
    return;
  const current = directoryRevision;
  const isCurrentSearch = () =>
    !disposed &&
    current === directoryRevision &&
    api === props.api &&
    semesterId === props.semesterId &&
    query === directoryQuery.value &&
    canSearchDirectory.value;
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
function chooseDirectoryStudent(student: TeacherSearchResponse['students'][number]): void {
  if (
    !canSearchDirectory.value ||
    !directoryStudents.value.some(
      (value) => value.id === student.id && value.groupId === student.groupId,
    )
  )
    return;
  const group = groups.value.find((value) => String(value.id) === student.groupId);
  if (!group) return;
  const sameGroup = selectedGroupId.value === group.id;
  scope.value = 'students';
  closeTool();
  if (
    !sameGroup ||
    !subjectsForGroup(group.id).some((subject) => subject.id === selectedSubjectId.value)
  ) {
    selectedGroupId.value = group.id;
    selectedSubjectId.value = null;
    clearChildCriteria();
  }
  selectedTypes.value = selectedTypes.value.filter((type) => types.value.includes(type));
  cancelDirectoryRequest();
  selectedStudent.value = student;
  persistContext();
  void loadStats();
}

function studentResultContext(student: TeacherSearchResponse['students'][number]): string {
  const duplicate = directoryStudents.value.some(
    (value) =>
      value.id !== student.id &&
      value.groupId === student.groupId &&
      value.displayName === student.displayName,
  );
  return duplicate ? `${student.groupName} · ID ${student.id}` : student.groupName;
}

function invalidateDirectoryProjection(): void {
  directoryProjectionReady.value = false;
  cancelDirectoryRequest();
  selectedStudent.value = null;
}

watch(
  () => props.realtimeRevision,
  () => {
    directoryProjectionEpoch += 1;
    invalidateDirectoryProjection();
  },
  { flush: 'sync' },
);

async function loadContext(): Promise<void> {
  invalidateDirectoryProjection();
  const directoryEpoch = directoryProjectionEpoch;
  closeTool();
  projectionError.value = null;
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
    directoryProjectionReady.value = directoryEpoch === directoryProjectionEpoch;
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

async function refreshProjection(): Promise<void> {
  invalidateDirectoryProjection();
  const directoryEpoch = directoryProjectionEpoch;
  if (!contextReady.value) {
    await loadContext();
    return;
  }
  const api = props.api,
    semesterId = props.semesterId,
    current = ++contextRevision;
  if (!api || !semesterId) return;
  try {
    const catalog = await api.catalog(semesterId);
    if (!isCurrentContext(current)) return;
    authorizedGroups.value = catalog.groups;
    catalogSubjectOptions.value = mergeSubjectOptions(
      catalog.taughtSubjectOptions,
      catalog.subjectOptions,
    );
    authorizedSubjectOptions.value = mergeSubjectOptions(catalogSubjectOptions.value, []);
    directoryProjectionReady.value = directoryEpoch === directoryProjectionEpoch;
    if (
      selectedGroupId.value !== null &&
      !catalog.groups.some((group) => group.id === selectedGroupId.value)
    ) {
      selectedGroupId.value = null;
      selectedSubjectId.value = null;
      selectedStudent.value = null;
      closeTool();
      stats.value = null;
      statsQuery.value = null;
      return;
    }
    projectionError.value = null;
    await loadStatsForRevision(++revision.value, true);
  } catch (cause) {
    if (!isCurrentContext(current) || cause instanceof StaleSessionGenerationError) return;
    failProjection(cause, 'Не удалось получить назначения преподавателя.');
  }
}

async function loadStats(): Promise<void> {
  if (!canChooseContext.value) return;
  const current = ++revision.value;
  await loadStatsForRevision(current);
}

async function loadStatsForRevision(current: number, retainProjection = false): Promise<void> {
  const api = props.api;
  const semesterId = props.semesterId;
  if (!retainProjection) {
    stats.value = null;
    statsQuery.value = null;
    projectionError.value = null;
  }
  statsLoading.value = !retainProjection;
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
    if (retainProjection) failProjection(cause, 'Не удалось получить статистику.');
    else {
      error.value =
        cause instanceof TeacherApiError ? cause.message : 'Не удалось получить статистику.';
      emit('error', cause);
    }
  } finally {
    if (isCurrent(current)) statsLoading.value = false;
  }
}

function failProjection(cause: unknown, fallback: string): void {
  const message = cause instanceof Error ? cause.message : fallback;
  if (cause instanceof TeacherApiError && [401, 403, 404].includes(cause.response.status)) {
    // Confirmed authority/target loss still retires the inadmissible projection.
    invalidateDirectoryProjection();
    closeTool();
    stats.value = null;
    statsQuery.value = null;
    error.value = message;
    projectionError.value = null;
    emit('error', cause);
  } else {
    // A transient read cannot cancel a pending/unknown export under the same query.
    projectionError.value = message;
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
    scope.value = 'students';
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
  cancelDirectoryRequest();
  selectedStudent.value = null;
  scope.value = next;
  fieldErrors.value = {};
  closeTool();
  persistContext();
  void loadStats();
}

function changeGroup(rawValue: string): void {
  if (!canChooseContext.value) return;
  const nextGroupId = positiveInteger(rawValue);
  const validGroupId =
    nextGroupId && groups.value.some((value) => value.id === nextGroupId) ? nextGroupId : null;
  cancelDirectoryRequest();
  selectedStudent.value = null;
  closeTool();
  selectedGroupId.value = validGroupId;
  selectedSubjectId.value = null;
  clearChildCriteria();
  persistContext();
  void loadStats();
}

function changeSubject(rawValue: string): void {
  if (!canChooseContext.value) return;
  cancelDirectoryRequest();
  closeTool();
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
  cancelDirectoryRequest();
  closeTool();
  const next = selectedTypes.value.includes(type)
    ? selectedTypes.value.filter((value) => value !== type)
    : [...selectedTypes.value, type];
  selectedTypes.value = next;
  persistContext();
  void loadStats();
}

function sortOptions(index: number): Array<{ value: string; label: string }> {
  const used = sortDrafts.value
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
  const next = sortDrafts.value.map((sort) => ({ ...sort }));
  next[index] = { column, descending: value.startsWith('-') };
  sortDrafts.value = next;
}
function addSort(): void {
  if (!canChooseContext.value) return;
  const column = teacherStatsSortColumns(scope.value).find(
    (value) => !sortDrafts.value.some((sort) => sort.column === value),
  );
  if (!column) return;
  sortDrafts.value = [...sortDrafts.value, { column, descending: false }];
}
function removeSort(index: number): void {
  if (!canChooseContext.value) return;
  sortDrafts.value = sortDrafts.value.filter((_, position) => position !== index);
}
function applyFilters(): void {
  if (!canChooseContext.value || activeTool.value !== 'filters') return;
  fieldErrors.value = {};
  for (const column of numericColumns.value) {
    try {
      parseTeacherStatsRanges(scope.value, { [column]: rangeDraft(column) });
    } catch (cause) {
      fieldErrors.value[column] = cause instanceof RangeError ? cause.message : 'Проверь диапазон.';
    }
  }
  if (Object.keys(fieldErrors.value).length) return;
  scopeMemory.value[scope.value].filters = parseTeacherStatsRanges(scope.value, filterDrafts.value);
  closeTool(true);
  persistContext();
  void loadStats();
}

function rangeDraft(column: string): TeacherStatsRangeDraft {
  const ranges = filterDrafts.value;
  return (ranges[column] ??= { minimum: '', maximum: '' });
}

function openTool(tool: 'filters' | 'sort'): void {
  if (!canChooseContext.value) return;
  if (activeTool.value === tool) {
    closeTool(true);
    return;
  }
  filterDrafts.value = Object.fromEntries(
    scopeMemory.value[scope.value].filters.map((filter) => [
      filter.column,
      {
        minimum: String(filter.minPercent ?? filter.minValue ?? ''),
        maximum: String(filter.maxPercent ?? filter.maxValue ?? ''),
      },
    ]),
  );
  sortDrafts.value = visibleSorts.value.map((sort) => ({ ...sort }));
  fieldErrors.value = {};
  activeTool.value = tool;
  void nextTick(() => toolPanel.value?.querySelector?.<HTMLElement>('input, button')?.focus?.());
}

function closeTool(restoreFocus = false): void {
  const trigger = activeTool.value === 'filters' ? filterTrigger.value : sortTrigger.value;
  activeTool.value = null;
  fieldErrors.value = {};
  if (restoreFocus) void nextTick(() => trigger?.focus?.());
}

function toolKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return;
  event.preventDefault();
  event.stopPropagation();
  closeTool(true);
}

function resetFilterDraft(): void {
  if (!canChooseContext.value) return;
  filterDrafts.value = {};
  fieldErrors.value = {};
}

function resetSortDraft(): void {
  if (!canChooseContext.value) return;
  sortDrafts.value = [{ column: 'present', descending: false }];
}

function applySort(): void {
  if (!canChooseContext.value || activeTool.value !== 'sort') return;
  sorts.value = sortDrafts.value.map((sort) => ({ ...sort }));
  closeTool(true);
  persistContext();
  void loadStats();
}

function clearMetricFilters(): void {
  if (!canChooseContext.value) return;
  scopeMemory.value[scope.value].filters = [];
  closeTool();
  fieldErrors.value = {};
  persistContext();
  void loadStats();
}

function lessonTypeLabel(type: string): string {
  return teacherLessonTypeLabel(type);
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
  const group = groups.value.find((value) => value.id === groupId);
  if (!canChooseContext.value || !group) return;
  emit('open-group', group.id, group.name);
}

function openJournal(): void {
  if (
    scope.value !== 'students' ||
    !currentStatsQuery() ||
    !selectedGroupId.value ||
    !selectedSubjectId.value ||
    !props.semesterId
  )
    return;
  emit('open-journal', {
    semesterId: props.semesterId,
    groupId: selectedGroupId.value,
    subjectId: selectedSubjectId.value,
    lessonTypes: [...selectedTypes.value],
    pastOnly: true,
    page: 0,
    pageSize: 100,
  });
}

function formatMetric(metric: TeacherStatsMetric | undefined): string {
  if (!metric || metric.denominator === 0) return '—';
  return `${new Intl.NumberFormat('ru', { maximumFractionDigits: 1 }).format(metric.percent)} %`;
}

function metricLabel(label: string, metric: TeacherStatsMetric | undefined): string {
  return !metric || metric.denominator === 0
    ? `${label}: нет данных`
    : `${label}: ${formatMetric(metric)}, ${metric.numerator} из ${metric.denominator}`;
}

function compactName(name: string): string {
  if (name.length <= 25) return name;
  const [surname, ...parts] = name.trim().split(/\s+/);
  return [surname, ...parts.map((part) => `${part[0]}.`)].join(' ');
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
useProjectionRefresh(refreshProjection, { revision: () => props.realtimeRevision });
</script>

<template>
  <main
    class="teacher-stats"
    aria-labelledby="teacher-stats-title"
    :aria-busy="loading"
  >
    <header class="teacher-stats__header">
      <h1 id="teacher-stats-title">
        Статистика
      </h1>
      <button
        type="button"
        class="teacher-stats__icon-button teacher-stats__journal-button"
        aria-label="Открыть журнал"
        data-teacher-action="journal-open"
        :disabled="scope !== 'students' || !currentStatsQuery()"
        @click="openJournal"
      >
        <MobileIcon name="table" />
      </button>
    </header>
    <form
      class="teacher-stats__directory"
      role="search"
      aria-label="Поиск по имени"
      @submit.prevent="loadDirectorySearch"
    >
      <label
        class="teacher-stats__field"
        for="teacher-stats-directory"
      ><span>Поиск по имени</span><input
        id="teacher-stats-directory"
        ref="directoryInput"
        v-model="directoryQuery"
        type="search"
        autocomplete="off"
        :disabled="!canSearchDirectory"
        :aria-invalid="Boolean(directoryValidation)"
        :aria-describedby="directoryValidation ? 'teacher-stats-directory-error' : undefined"
        placeholder="Фамилия, имя или отчество"
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
        Ищем студентов…
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
        <section aria-label="Найденные студенты">
          <h2>Студенты</h2><button
            v-for="student in directoryStudents"
            :key="`${student.groupId}:${student.id}`"
            type="button"
            :disabled="!canChooseContext"
            @click="chooseDirectoryStudent(student)"
          >
            <span>{{ student.displayName }}</span><span>{{ studentResultContext(student) }}</span>
          </button><p
            v-if="!directoryStudents.length"
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
    <p
      v-if="selectedStudent"
      class="teacher-stats__search-context"
      role="status"
    >
      {{ selectedStudent.displayName }} · {{ selectedStudent.groupName }}. Итоги по всей группе;
      выбранный студент выделен в результатах.
    </p>
    <StudentWarningBlock
      v-if="routeNotice"
      title="Отбор восстановлен"
      :message="routeNotice"
    />
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
    <section
      class="teacher-stats__tools"
      aria-label="Отбор и выгрузка"
    >
      <div class="teacher-stats__toolbar">
        <div class="teacher-stats__tool">
          <button
            ref="filterTrigger"
            type="button"
            class="teacher-stats__icon-button"
            aria-label="Фильтры"
            :disabled="!canChooseContext"
            data-teacher-action="stats-filters"
            :aria-expanded="filtersOpen"
            :aria-pressed="activeCriteria"
            aria-controls="teacher-stats-filters"
            :data-active="activeCriteria"
            @click="openTool('filters')"
          >
            <MobileIcon name="filter" />
          </button>
          <span
            v-if="activeCriteria"
            class="teacher-stats__applied"
          >Применены</span>
        </div>
        <div class="teacher-stats__tool">
          <button
            ref="sortTrigger"
            type="button"
            class="teacher-stats__icon-button"
            aria-label="Сортировка"
            :disabled="!canChooseContext"
            data-teacher-action="stats-sort"
            :aria-expanded="activeTool === 'sort'"
            :aria-pressed="Boolean(activeSort)"
            aria-controls="teacher-stats-sort"
            :data-active="Boolean(activeSort)"
            @click="openTool('sort')"
          >
            <MobileIcon name="sort" />
          </button>
          <span
            v-if="activeSort"
            class="teacher-stats__applied"
          >Применена</span>
        </div>
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
      </div>
      <div
        v-if="activeTool"
        ref="toolPanel"
        class="teacher-stats__tool-panel"
        role="region"
        :aria-label="activeTool === 'filters' ? 'Фильтры статистики' : 'Сортировка статистики'"
        @keydown="toolKeydown"
      >
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
              class="teacher-stats__secondary"
              :disabled="!canChooseContext"
              @click="resetFilterDraft"
            >
              Сбросить фильтры
            </button>
            <button
              type="button"
              class="teacher-stats__secondary"
              @click="closeTool(true)"
            >
              Отмена
            </button>
            <button
              type="submit"
              class="teacher-stats__apply"
              :disabled="!canChooseContext"
            >
              Применить
            </button>
          </div>
        </form>
        <form
          v-else
          id="teacher-stats-sort"
          class="teacher-stats__filters"
          @submit.prevent="applySort"
        >
          <h2>Сортировка</h2>
          <div
            v-for="(sort, index) in sortDrafts"
            :key="index"
            class="teacher-stats__sort-row"
          >
            <HeadmanStatsSelect
              :label="index === 0 ? 'Порядок сортировки' : `Условие сортировки ${index + 1}`"
              :disabled="!canChooseContext"
              :model-value="`${sort.descending ? '-' : ''}${sort.column}`"
              :options="sortOptions(index)"
              @update:model-value="changeSort(index, $event)"
            />
            <button
              v-if="index > 0"
              type="button"
              class="teacher-stats__icon-button"
              :aria-label="`Удалить условие ${index + 1}`"
              :disabled="!canChooseContext"
              @click="removeSort(index)"
            >
              <MobileIcon name="close" />
            </button>
          </div>
          <button
            v-if="sortDrafts.length < teacherStatsSortColumns(scope).length"
            type="button"
            class="teacher-stats__add-sort"
            :disabled="!canChooseContext"
            @click="addSort"
          >
            <MobileIcon name="plus" />Добавить условие сортировки
          </button>
          <div class="teacher-stats__filter-actions">
            <button
              type="button"
              class="teacher-stats__secondary"
              :disabled="!canChooseContext"
              @click="resetSortDraft"
            >
              Сбросить сортировку
            </button>
            <button
              type="button"
              class="teacher-stats__secondary"
              @click="closeTool(true)"
            >
              Отмена
            </button>
            <button
              type="submit"
              class="teacher-stats__apply"
              :disabled="!canChooseContext"
            >
              Применить
            </button>
          </div>
        </form>
      </div>
    </section>
    <StudentWarningBlock
      v-if="error || projectionError"
      title="Статистика недоступна"
      :message="error ?? projectionError ?? ''"
      severity="error"
      action-label="Повторить"
      @action="projectionError ? refreshProjection() : loadContext()"
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
      class="teacher-stats__single-group"
    >
      <h2>Моя группа</h2>
      <button
        class="teacher-stats__group-link"
        type="button"
        :aria-label="`Открыть группу ${singleAuthorizedGroup.name}`"
        @click="openGroup(singleAuthorizedGroup.id)"
      >
        <span>{{ singleAuthorizedGroup.name }}</span><MobileIcon name="chevron-down" />
      </button>
    </section>
    <p
      v-else-if="!currentStatsQuery()"
      class="teacher-stats__state"
      role="status"
    >
      {{
        scope === 'groups'
          ? 'Выбери тип занятия.'
          : 'Выбери группу, предмет и хотя бы один тип занятия.'
      }}
    </p>
    <template v-else-if="displayedStats">
      <h2 class="teacher-stats__results-title">
        {{ scope === 'groups' ? 'Мои группы' : 'Студенты' }}
      </h2>
      <p
        v-if="selectedStudent && scope === 'students' && !visibleRows.some((row) => row.selected)"
        class="teacher-stats__search-context"
        role="status"
      >
        Найденный студент не входит в текущие результаты.
      </p>
      <section
        v-if="visibleRowCount === 0"
        class="teacher-stats__state teacher-stats__empty"
        role="status"
      >
        <!-- eslint-disable vue/no-v-html -->
        <div
          class="teacher-stats__empty-art"
          aria-hidden="true"
          v-html="emptyFilterArtwork"
        />
        <!-- eslint-enable vue/no-v-html -->
        <p>
          {{
            activeCriteria ? 'По этим фильтрам никого не нашли.' : 'Данных для статистики пока нет.'
          }}
        </p>
        <button
          v-if="activeCriteria"
          type="button"
          @click="clearMetricFilters"
        >
          Сбросить фильтры
        </button>
      </section>
      <div
        v-else
        class="teacher-stats__cards"
      >
        <article
          v-for="row in visibleRows"
          :key="row.key"
          class="teacher-stats__card"
          :data-selected="row.selected"
        >
          <h3>
            <span
              v-if="row.selected"
              class="teacher-stats__selected-label"
            >Найденный студент</span>
            <button
              v-if="row.groupId"
              type="button"
              @click="openGroup(row.groupId)"
            >
              <span class="teacher-stats__row-name">{{ row.name }}</span><span
                v-if="'lessonsCount' in row"
                class="teacher-stats__lesson-count"
              >{{ row.lessonsCount }} пар</span><MobileIcon name="chevron-down" />
            </button><span
              v-else
              :aria-label="row.name"
              :title="row.name"
            >{{ compactName(row.name) }}</span>
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
                >{{ symbol.toUpperCase() }}</span>
              </dt><dd :aria-label="metricLabel(metric.label, row[metric.column])">
                {{ formatMetric(row[metric.column]) }}
              </dd>
            </div>
          </dl>
        </article>
      </div>
    </template>
  </main>
</template>
