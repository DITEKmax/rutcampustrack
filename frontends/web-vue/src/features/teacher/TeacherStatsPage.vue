<script setup lang="ts">
import { computed, nextTick, reactive, ref, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';
import {
  toTeacherStatsReportSelector,
  type TeacherStatsFilter,
  type TeacherStatsQuery,
  type TeacherStatsResponse,
  type TeacherStatsScope,
  type TeacherStatsSort,
} from '../../../../mobile-core/src/features/teacher/teacher-client';
import { useTeacherResource, useTeacherScope, useTeacherSelection } from './teacher-state';
import TeacherRegion from './TeacherRegion.vue';
import TeacherFilters from './TeacherFilters.vue';
import TeacherNotice from './TeacherNotice.vue';
import TeacherStatsTable from './TeacherStatsTable.vue';
import TeacherExport from './TeacherExport.vue';
import { teacherLessonTypeLabel } from '../../../../mobile-core/src/features/teacher/teacher-ui-format';

const scope = useTeacherScope();
const route = useRoute();
const selection = useTeacherSelection(scope);
const stats = useTeacherResource<TeacherStatsResponse>(scope);
const kind = ref<TeacherStatsScope>('students');
const drillGroup = ref<number | null>(null);
const drillHeading = ref<HTMLElement | null>(null);
const statsTable = ref<InstanceType<typeof TeacherStatsTable> | null>(null);
const memory = reactive<
  Record<
    TeacherStatsScope,
    { sorts: TeacherStatsSort[]; filters: TeacherStatsFilter[]; types: string[] }
  >
>({
  students: { sorts: [], filters: [], types: [] },
  groups: { sorts: [], filters: [], types: [] },
});
const requestedName = typeof route.query.studentName === 'string' ? route.query.studentName.trim().slice(0, 128) : '';
if (requestedName) memory.students.filters = [{ column: 'displayName', contains: requestedName }];
const loadedQuery = shallowRef<TeacherStatsQuery | null>(null);
const allTypes = computed(() => [
  ...new Set(
    scope.catalog.value?.taughtSubjectOptions.flatMap((option) => option.lessonTypes) ?? [],
  ),
]);
const query = computed<TeacherStatsQuery | null>(() => {
  const types = kind.value === 'groups' ? memory.groups.types : selection.lessonTypes.value;
  if (
    !scope.semester.value ||
    !types.length ||
    (kind.value === 'students' && !selection.ready.value)
  )
    return null;
  return {
    semesterId: scope.semester.value.id,
    scope: kind.value,
    ...(kind.value === 'students'
      ? { groupId: selection.groupId.value, subjectId: selection.subjectId.value }
      : {}),
    lessonTypes: [...types],
    sorts: memory[kind.value].sorts.map((sort) => ({ ...sort })),
    filters: memory[kind.value].filters.map((filter) => ({ ...filter })),
  };
});
async function load(background = false) {
  const requested = query.value;
  const previous = loadedQuery.value;
  const sameContext = previous && requested && previous.semesterId === requested.semesterId && previous.scope === requested.scope &&
    previous.groupId === requested.groupId && previous.subjectId === requested.subjectId &&
    JSON.stringify(previous.lessonTypes) === JSON.stringify(requested.lessonTypes) &&
    (requested.scope !== 'groups' || stats.data.value?.groups.every(group => selection.groups.value.some(available => available.id === group.groupId)));
  const retain = background === true && !!sameContext;
  if (!retain) loadedQuery.value = null;
  if (!requested) {
    stats.clear();
    return;
  }
  await stats.load(async () => {
    const data = await scope.api.stats(requested);
    scope.transport.assertCurrent();
    if (JSON.stringify(query.value) === JSON.stringify(requested)) loadedQuery.value = requested;
    return data;
  }, sameContext ? (_old, next) => next : undefined, retain);
}
function changeScope(value: TeacherStatsScope) {
  if (kind.value === value) return;
  if (kind.value === 'students') memory.students.types = [...selection.lessonTypes.value];
  kind.value = value;
  drillGroup.value = null;
  if (value === 'students' && memory.students.types.length)
    selection.lessonTypes.value = memory.students.types.filter((type) =>
      selection.types.value.includes(type),
    );
  load();
}
async function openGroup(groupId: number) {
  if (!scope.catalog.value?.groups.some(group => group.id === groupId)) return;
  const subject = selection.subjectId.value;
  const selectedTypes = [...memory.groups.types];
  selection.groupId.value = groupId;
  selection.subjectId.value = subject;
  selection.reconcile();
  const availableTypes = selectedTypes.filter(type => selection.types.value.includes(type));
  selection.lessonTypes.value = availableTypes.length ? availableTypes : [...selection.types.value];
  drillGroup.value = groupId;
  kind.value = 'students';
  await load();
  await nextTick();
  drillHeading.value?.focus();
}
async function returnToGroups() {
  const groupId = drillGroup.value;
  memory.students.types = [...selection.lessonTypes.value];
  drillGroup.value = null;
  kind.value = 'groups';
  await load();
  await nextTick();
  if (groupId !== null) statsTable.value?.focusGroup(groupId);
}
function sort(column: string) {
  const sorts = memory[kind.value].sorts;
  const current = sorts.find((item) => item.column === column);
  if (!current) sorts.push({ column, descending: false });
  else if (!current.descending)
    memory[kind.value].sorts = sorts.map((item) =>
      item.column === column ? { ...item, descending: true } : item,
    );
  else memory[kind.value].sorts = sorts.filter((item) => item.column !== column);
  load();
}
function filters(values: TeacherStatsFilter[]) {
  memory[kind.value].filters = values;
  load();
}
function reset() {
  memory[kind.value].sorts = [];
  memory[kind.value].filters = [];
  load();
}
watch(
  () => scope.refreshed.value,
  () => {
    selection.reconcile();
    if (drillGroup.value !== null && !selection.groups.value.some(group => group.id === drillGroup.value)) drillGroup.value = null;
    memory.groups.types = memory.groups.types.filter((type) => allTypes.value.includes(type));
    if (!memory.groups.types.length) memory.groups.types = [...allTypes.value];
    void load(scope.backgroundRefresh.value);
  },
);
watch(
  () => scope.catalog.value,
  (value) => {
    if (!value) {
      stats.clear();
      loadedQuery.value = null;
    }
  },
);
const exportRequest = computed(() =>
  stats.data.value && loadedQuery.value && !stats.loading.value
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
      <TeacherFilters
        v-if="kind === 'students'"
        :selection="selection"
        :disabled="scope.loading.value"
        @change="load"
      />
      <fieldset
        v-else
        class="teacher-types"
      >
        <legend>Типы занятий в моих группах</legend><label
          v-for="type in allTypes"
          :key="type"
        ><input
           v-model="memory.groups.types"
           type="checkbox"
           :value="type"
           @change="load()"
         >
          {{ teacherLessonTypeLabel(type) }}</label>
      </fieldset>
      <TeacherNotice
        :loading="stats.loading.value"
        :error="stats.error.value"
        :empty="!query ? 'Выбери хотя бы один тип занятия.' : ''"
        @retry="load"
      />
      <template v-if="stats.data.value">
        <p
          class="web-caption"
          role="status"
        >
          {{ kind === 'students' ? 'По студентам группы' : 'По моим группам' }} ·
          {{ scope.semester.value?.name }} ·
          {{ stats.data.value.periodFrom ?? 'Начало семестра' }} —
          {{ stats.data.value.periodTo ?? 'Текущая дата' }} · учтено пар:
          {{ stats.data.value.lessonsCount }}
        </p><TeacherStatsTable
          ref="statsTable"
          :key="kind"
          :data="stats.data.value"
          :sorts="memory[kind].sorts"
          :filters="memory[kind].filters"
          :busy="stats.loading.value || scope.loading.value"
          @group="openGroup"
          @sort="sort"
          @filter="filters"
          @reset="reset"
        />
      </template>
      <TeacherExport
        :scope="scope"
        :request="exportRequest"
      />
    </template>
  </TeacherRegion>
</template>
