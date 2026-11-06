<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';
import {
  toTeacherStatsReportSelector,
  type TeacherStatsFilter,
  type TeacherStatsQuery,
  type TeacherStatsResponse,
  type TeacherStatsScope,
  type TeacherStatsSort,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
import { shortDate, useTeacherResource, useTeacherScope, useTeacherSelection, type TeacherSelection } from './teacher-state';
import TeacherRegion from './TeacherRegion.vue';
import TeacherFilters from './TeacherFilters.vue';
import TeacherNotice from './TeacherNotice.vue';
import TeacherStatsTable from './TeacherStatsTable.vue';
import TeacherExport from './TeacherExport.vue';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';

const scope = useTeacherScope();
const route = useRoute();
const students = useTeacherSelection(scope);
const drill = useTeacherSelection(scope);
const stats = useTeacherResource<TeacherStatsResponse>(scope);
const kind = ref<TeacherStatsScope>('students');
const drillGroup = ref<number | null>(null);
const drillHeading = ref<HTMLElement | null>(null);
const groupsTab = ref<HTMLButtonElement | null>(null);
const statsTable = ref<InstanceType<typeof TeacherStatsTable> | null>(null);
const selection = computed(() => drillGroup.value === null ? students : drill);
type StatsContext = TeacherStatsScope | 'drill';
const context = computed<StatsContext>(() => kind.value === 'groups' ? 'groups' : drillGroup.value === null ? 'students' : 'drill');
const memory = reactive<
  Record<
    StatsContext,
    { sorts: TeacherStatsSort[]; filters: TeacherStatsFilter[] }
  >
>({
  students: { sorts: [], filters: [] },
  groups: { sorts: [], filters: [] },
  drill: { sorts: [], filters: [] },
});
const groupTypes = ref<string[]>([]);
let groupTypesInitialized = false;
let studentsInitialized = false;
const requestedName = typeof route.query.studentName === 'string' ? route.query.studentName.trim().slice(0, 128) : '';
if (requestedName) memory.students.filters = [{ column: 'displayName', contains: requestedName }];
const loadedQuery = shallowRef<TeacherStatsQuery | null>(null);
const nameColumn = computed(() => kind.value === 'groups' ? 'groupName' : 'displayName');
const search = computed({
  get: () => memory[context.value].filters.find(filter => filter.column === nameColumn.value)?.contains ?? '',
  set: (value: string) => {
    const filters = memory[context.value].filters.filter(filter => filter.column !== nameColumn.value);
    if (value) filters.push({ column: nameColumn.value, contains: value });
    memory[context.value].filters = filters;
    scheduleSearch();
  },
});
const searchPending = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | null = null;
let loadRevision = 0;
let alive = true;
function cancelSearch() {
  if (searchTimer !== null) clearTimeout(searchTimer);
  searchTimer = null;
  searchPending.value = false;
}
function scheduleSearch() {
  cancelSearch();
  loadRevision++;
  loadedQuery.value = null;
  stats.clear();
  if (!alive) return;
  searchPending.value = true;
  searchTimer = setTimeout(() => {
    searchTimer = null;
    if (alive) void load();
  }, 250);
}
onBeforeUnmount(() => {
  alive = false;
  cancelSearch();
  loadRevision++;
  stats.clear();
});
const allTypes = computed(() => [
  ...new Set(
    scope.catalog.value?.taughtSubjectOptions.flatMap((option) => option.lessonTypes) ?? [],
  ),
]);
const query = computed<TeacherStatsQuery | null>(() => {
  const types = kind.value === 'groups' ? groupTypes.value : selection.value.lessonTypes.value;
  if (
    !scope.semester.value ||
    !types.length ||
    (kind.value === 'students' && !selection.value.ready.value)
  )
    return null;
  return {
    semesterId: scope.semester.value.id,
    scope: kind.value,
    ...(kind.value === 'students'
      ? { groupId: selection.value.groupId.value, subjectId: selection.value.subjectId.value }
      : {}),
    lessonTypes: [...types],
    sorts: memory[context.value].sorts.map((sort) => ({ ...sort })),
    filters: memory[context.value].filters
      .filter(filter => filter.contains === undefined || filter.contains.trim())
      .map(filter => ({ ...filter, ...(filter.contains !== undefined ? { contains: filter.contains.trim() } : {}) })),
  };
});
async function load(background = false) {
  cancelSearch();
  const run = ++loadRevision;
  if (!alive) return;
  const requested = query.value;
  const previous = loadedQuery.value;
  const sameContext = previous && requested && previous.semesterId === requested.semesterId && previous.scope === requested.scope &&
    previous.groupId === requested.groupId && previous.subjectId === requested.subjectId &&
    JSON.stringify(previous.lessonTypes) === JSON.stringify(requested.lessonTypes) &&
    (requested.scope !== 'groups' || stats.data.value?.groups.every(group => students.groups.value.some(available => available.id === group.groupId)));
  const retain = background === true && !!sameContext;
  if (!sameContext) loadedQuery.value = null;
  if (!requested) {
    stats.clear();
    return;
  }
  await stats.load(async () => {
    const data = await scope.api.stats(requested);
    scope.transport.assertCurrent();
    if (alive && run === loadRevision && JSON.stringify(query.value) === JSON.stringify(requested)) loadedQuery.value = requested;
    return data;
  }, sameContext ? (_old, next) => next : undefined, retain);
}
function changeScope(value: TeacherStatsScope) {
  if (kind.value === value && drillGroup.value === null) return;
  kind.value = value;
  drillGroup.value = null;
  void load();
}
async function openGroup(groupId: number) {
  if (kind.value !== 'groups' || !scope.catalog.value?.groups.some(group => group.id === groupId)) return;
  drill.groupId.value = groupId;
  drill.subjectId.value = students.subjectId.value;
  drill.lessonTypes.value = [...groupTypes.value];
  drill.reconcile();
  memory.drill = { sorts: [], filters: [] };
  drillGroup.value = groupId;
  kind.value = 'students';
  await load();
  await nextTick();
  if (drillGroup.value === groupId) drillHeading.value?.focus();
}
async function returnToGroups() {
  const groupId = drillGroup.value;
  drillGroup.value = null;
  kind.value = 'groups';
  await load();
  await nextTick();
  if (kind.value === 'groups' && (groupId === null || !statsTable.value?.focusGroup(groupId))) groupsTab.value?.focus();
}
function sort(column: string) {
  const sorts = memory[context.value].sorts;
  const current = sorts.find((item) => item.column === column);
  if (!current) sorts.push({ column, descending: false });
  else if (!current.descending)
    memory[context.value].sorts = sorts.map((item) =>
      item.column === column ? { ...item, descending: true } : item,
    );
  else memory[context.value].sorts = sorts.filter((item) => item.column !== column);
  void load();
}
function toggleGroupType(type: string) {
  if (scope.loading.value) return;
  groupTypes.value = groupTypes.value.includes(type)
    ? groupTypes.value.filter(value => value !== type)
    : [...groupTypes.value, type];
  void load();
}
function reconcileSelection(target: TeacherSelection, initialized: boolean) {
  const { groupId, subjectId, lessonTypes } = target;
  const previous = [groupId.value, subjectId.value];
  const keepEmpty = initialized && !lessonTypes.value.length;
  target.reconcile();
  if (keepEmpty && groupId.value === previous[0] && subjectId.value === previous[1]) lessonTypes.value = [];
}
function resetFilters() {
  if (scope.loading.value) return;
  memory[context.value] = { sorts: [], filters: [] };
  if (kind.value === 'groups') groupTypes.value = [...allTypes.value];
  else selection.value.lessonTypes.value = [...selection.value.types.value];
  void load();
}
watch(
  () => scope.refreshed.value,
  () => {
    reconcileSelection(students, studentsInitialized);
    studentsInitialized = true;
    if (drillGroup.value !== null) {
      if (drill.groups.value.some(group => group.id === drill.groupId.value)) reconcileSelection(drill, true);
      else drillGroup.value = null;
    }
    const previousTypes = groupTypes.value;
    groupTypes.value = previousTypes.filter(type => allTypes.value.includes(type));
    if (!groupTypesInitialized || (previousTypes.length && !groupTypes.value.length)) groupTypes.value = [...allTypes.value];
    groupTypesInitialized ||= allTypes.value.length > 0;
    void load(scope.backgroundRefresh.value);
  },
);
const appliedDetails = computed(() => {
  const applied = loadedQuery.value;
  const data = stats.data.value;
  if (!applied || !data) return null;
  const group = students.groups.value.find(value => value.id === applied.groupId)?.name;
  const subject = scope.catalog.value?.subjectOptions.find(value =>
    value.groupId === applied.groupId && value.subjectId === applied.subjectId)?.subjectName;
  const context = applied.scope === 'groups' ? 'Мои группы' : [group, subject].filter(Boolean).join(' · ');
  const period = data.periodFrom && data.periodTo
    ? `${shortDate(data.periodFrom)} — ${shortDate(data.periodTo)}`
    : scope.semester.value?.name;
  const types = applied.lessonTypes?.map(teacherLessonTypeLabel).join(', ');
  const name = applied.filters?.find(filter => filter.column === (applied.scope === 'groups' ? 'groupName' : 'displayName'))?.contains;
  return { context, period, types, name };
});
watch(
  () => scope.catalog.value,
  (value) => {
    if (!value) {
      cancelSearch();
      loadRevision++;
      stats.clear();
      loadedQuery.value = null;
    }
  },
);
const exportRequest = computed(() =>
  stats.data.value && loadedQuery.value && !searchPending.value && !stats.loading.value && !scope.loading.value && !stats.error.value &&
  JSON.stringify(query.value) === JSON.stringify(loadedQuery.value)
    ? (format: Parameters<typeof toTeacherStatsReportSelector>[1]) => ({
        kind: 'TEACHER_STATS' as const,
        teacherStats: toTeacherStatsReportSelector(loadedQuery.value!, format),
      })
    : null,
);
</script>
<template>
  <TeacherRegion
    :scope="scope"
    class="teacher-statistics-page"
    screen="WEB-TEACHER-STATISTICS"
  >
    <h1 class="web-sr-only">
      Статистика преподавателя
    </h1>
    <TeacherNotice
      :loading="scope.loading.value && !scope.catalog.value"
      :error="scope.error.value"
      @retry="scope.load"
    />
    <template v-if="scope.catalog.value">
      <div
        class="teacher-scope-tabs"
        role="group"
        aria-label="Разрез статистики"
      >
        <button
          class="web-button"
          :class="{ 'web-button-selected': kind === 'students' }"
          :aria-pressed="kind === 'students'"
          @click="changeScope('students')"
        >
          По студентам группы
        </button><button
          ref="groupsTab"
          class="web-button"
          :class="{ 'web-button-selected': kind === 'groups' }"
          :aria-pressed="kind === 'groups'"
          @click="changeScope('groups')"
        >
          По моим группам
        </button>
      </div>
      <div
        v-if="drillGroup !== null && kind === 'students'"
        class="teacher-toolbar"
      >
        <h2
          ref="drillHeading"
          tabindex="-1"
        >
          Студенты группы {{ selection.groups.value.find(group => group.id === selection.groupId.value)?.name }}
        </h2>
        <button
          class="web-button"
          @click="returnToGroups"
        >
          Вернуться к моим группам
        </button>
      </div>
      <div class="teacher-panel teacher-statistics-filters web-stack">
        <TeacherFilters
          v-if="kind === 'students'"
          :selection="selection"
          :disabled="scope.loading.value"
          @change="load"
        />
        <div
          v-else
          class="teacher-types"
          role="group"
          aria-label="Типы занятий"
        >
          <span
            class="teacher-types-label"
            aria-hidden="true"
          >Типы занятий</span>
          <div class="teacher-type-buttons">
            <button
              v-for="type in allTypes"
              :key="type"
              type="button"
              class="web-button"
              :aria-pressed="groupTypes.includes(type)"
              :disabled="scope.loading.value"
              @click="toggleGroupType(type)"
            >
              {{ teacherLessonTypeLabel(type) }}
            </button>
          </div>
        </div>
        <div class="teacher-toolbar">
          <label class="web-field teacher-statistics-search">{{ kind === 'students' ? 'Найти студента' : 'Найти группу' }}<input
            v-model="search"
            type="search"
            maxlength="128"
            :placeholder="kind === 'students' ? 'ФИО студента' : 'Название группы'"
            :disabled="scope.loading.value"
            autocomplete="off"
          ></label>
          <button
            class="web-button"
            type="button"
            :disabled="scope.loading.value"
            aria-label="Сбросить фильтры и сортировку"
            @click="resetFilters"
          >
            Сбросить
          </button>
        </div>
      </div>
      <TeacherNotice
        :loading="stats.loading.value || searchPending"
        :error="stats.error.value"
        :empty="!query ? 'Выбери хотя бы один тип занятия.' : ''"
        @retry="load"
      />
      <template v-if="stats.data.value">
        <p
          v-if="appliedDetails"
          class="web-caption teacher-applied-context"
        >
          {{ appliedDetails.context }}<template v-if="appliedDetails.period">
            · {{ appliedDetails.period }}
          </template><template v-if="appliedDetails.types">
            · {{ appliedDetails.types }}
          </template><template v-if="appliedDetails.name">
            · поиск: «{{ appliedDetails.name }}»
          </template>
        </p>
        <TeacherStatsTable
          ref="statsTable"
          :key="context"
          :data="stats.data.value"
          :sorts="loadedQuery?.sorts ?? []"
          :filters="loadedQuery?.filters ?? []"
          :busy="stats.loading.value || scope.loading.value || searchPending"
          @group="openGroup"
          @sort="sort"
        />
      </template>
      <TeacherExport
        :scope="scope"
        :request="exportRequest"
      />
    </template>
  </TeacherRegion>
</template>
