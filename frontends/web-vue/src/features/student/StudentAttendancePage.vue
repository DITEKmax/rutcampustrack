<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type {
  StudentAttendanceResponse,
  StudentLessonType,
  StudentStatisticsSubjectDetailResponse,
} from '../../../../mobile-core/src/api/types';
import { useStudentResource, useStudentScope } from './student-state';
import { formatLessonDate, lessonTypeLabel, monday, serverDate, shiftDate } from './student-format';
import StudentRegion from './StudentRegion.vue';
import StudentNotice from './StudentNotice.vue';
import StudentMetrics from './StudentMetrics.vue';
import StudentAttendanceTable from './StudentAttendanceTable.vue';
import StudentPeriodPager from './StudentPeriodPager.vue';
const scope = useStudentScope(),
  attendance = useStudentResource<StudentAttendanceResponse>(scope),
  subjectStats = useStudentResource<StudentStatisticsSubjectDetailResponse>(scope);
const route = useRoute(),
  router = useRouter();
const mode = ref(route.query.view === 'subjects' ? 'subjects' : 'weeks');
const date = ref(
  typeof route.query.date === 'string' &&
    /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) &&
    Number.isFinite(Date.parse(route.query.date))
    ? route.query.date
    : '',
);
const subject = ref(typeof route.query.subject === 'string' ? route.query.subject : '');
const types = ref<StudentLessonType[]>([]);
let restoredTypes = false;
const pendingQueries = new Set<string>();
function queryIdentity(query: Record<string, unknown>) {
  return JSON.stringify([
    query.view ?? 'weeks',
    query.date ?? '',
    query.subject ?? '',
    Array.isArray(query.type) ? query.type : query.type === undefined ? [] : [query.type],
  ]);
}
const allTypes = ['LECTURE', 'PRACTICE', 'LAB'] as const;
const availableTypes = computed(
  () =>
    attendance.data.value?.subjects
      .find((s) => s.id === subject.value)
      ?.typeCards.map((t) => t.type) ?? [],
);
async function load() {
  const semester = scope.session.value?.semester;
  if (!semester) {
    attendance.clear();
    subjectStats.clear();
    return;
  }
  if (!date.value) date.value = serverDate(scope.session.value?.serverNow) ?? semester.startsOn;
  await attendance.load(() => scope.api.getAttendance(semester.id), true);
  if (!scope.current() || !attendance.data.value) return;
  if (!date.value) date.value = serverDate(attendance.data.value.serverNow) ?? semester.startsOn;
  if (!attendance.data.value.subjects.some((s) => s.id === subject.value)) subject.value = '';
  await nextTick();
  await loadSubject();
}
async function loadSubject() {
  if (!scope.current()) return;
  const semester = scope.session.value?.semester;
  if (!semester || !subject.value || !types.value.length) {
    subjectStats.clear();
    return;
  }
  const id = subject.value,
    chosen = [...types.value];
  await subjectStats.load(() => scope.api.getStatisticsSubject(id, semester.id, 'weeks', chosen));
}
async function remember() {
  if (!scope.current()) return;
  scope.transport.assertCurrent();
  const query = {
    view: mode.value,
    date: date.value,
    ...(subject.value ? { subject: subject.value } : {}),
    type: types.value.length ? types.value : '',
  };
  const key = queryIdentity(query);
  pendingQueries.add(key);
  try {
    await router.replace({ query });
  } finally {
    pendingQueries.delete(key);
  }
}
async function chooseSubject() {
  restoredTypes = true;
  types.value = [...availableTypes.value];
  await remember();
  await loadSubject();
}
watch(scope.refreshed, load);
watch(scope.session, (value) => {
  if (!value) {
    attendance.clear();
    subjectStats.clear();
  }
});
watch(availableTypes, (value) => {
  if (!attendance.data.value) return;
  if (!restoredTypes && subject.value && value.length) {
    const requested = Array.isArray(route.query.type) ? route.query.type : [route.query.type];
    types.value =
      route.query.type === undefined ? [...value] : value.filter((t) => requested.includes(t));
    restoredTypes = true;
  } else types.value = types.value.filter((t) => value.includes(t));
});
watch(
  () => route.query,
  () => {
    if (pendingQueries.has(queryIdentity(route.query))) return;
    mode.value = route.query.view === 'subjects' ? 'subjects' : 'weeks';
    if (
      typeof route.query.date === 'string' &&
      /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) &&
      Number.isFinite(Date.parse(route.query.date))
    )
      date.value = route.query.date;
    subject.value = typeof route.query.subject === 'string' ? route.query.subject : '';
    const requested = Array.isArray(route.query.type) ? route.query.type : [route.query.type];
    types.value =
      route.query.type === undefined
        ? [...availableTypes.value]
        : availableTypes.value.filter((t) => requested.includes(t));
    if (scope.session.value) void loadSubject();
  },
);
const week = computed(() => (date.value ? monday(date.value) : ''));
const lessons = computed(
  () =>
    attendance.data.value?.days
      .flatMap((d) => d.lessons)
      .filter((l) =>
        mode.value === 'weeks'
          ? l.date >= week.value && l.date <= shiftDate(week.value, 6)
          : l.subject.id === subject.value && types.value.includes(l.type),
      ) ?? [],
);
const metrics = computed(() =>
  mode.value === 'subjects'
    ? subjectStats.data.value?.selectedAggregate
    : attendance.data.value?.graph.weeks.find(
        (p) => p.dateFrom <= shiftDate(week.value, 6) && p.dateTo >= week.value,
      )?.metrics,
);
const weekContext = computed(() =>
  attendance.data.value?.weeks?.find(
    (w) => w.dateFrom <= shiftDate(week.value, 6) && w.dateTo >= week.value,
  ),
);
function step(direction: -1 | 1) {
  date.value = shiftDate(date.value, direction * 7);
  void remember();
}
</script>
<template>
  <StudentRegion
    :scope="scope"
    screen="WEB-STUDENT-ATTENDANCE"
  >
    <h1>Посещаемость</h1><StudentNotice
      :loading="scope.loading.value || attendance.loading.value"
      :error="scope.error.value || attendance.error.value"
      :empty="
        scope.session.value && !scope.session.value.semester ? 'Активного семестра пока нет.' : ''
      "
      @retry="scope.load"
    />
    <template v-if="attendance.data.value">
      <div
        class="student-actions"
        role="group"
        aria-label="Разрез посещаемости"
      >
        <button
          v-for="item in [
            { id: 'weeks', label: 'По неделям' },
            { id: 'subjects', label: 'По предметам' },
          ]"
          :key="item.id"
          class="web-button"
          :aria-pressed="mode === item.id"
          :class="{ 'web-button-selected': mode === item.id }"
          @click="
            mode = item.id;
            remember();
          "
        >
          {{ item.label }}
        </button>
      </div>
      <template v-if="mode === 'weeks' && week">
        <StudentPeriodPager
          :label="`${formatLessonDate(week)} — ${formatLessonDate(shiftDate(week, 6))}`"
          unit="период посещаемости"
          :previous-disabled="week <= monday(attendance.data.value.semester.startsOn)"
          :next-disabled="shiftDate(week, 7) > attendance.data.value.semester.endsOn"
          @step="step"
          @reset="
            date =
              serverDate(attendance.data.value.serverNow) ??
              attendance.data.value.semester.startsOn;
            remember();
          "
        /><p
          v-if="weekContext"
          class="web-caption"
        >
          Неделя №{{ weekContext.weekOfSemester }} · {{ weekContext.parityLabel }}
        </p>
      </template>
      <div
        v-else-if="mode === 'subjects'"
        class="student-filter"
      >
        <label class="web-field">Предмет<select
          v-model="subject"
          @change="chooseSubject"
        ><option value="">Выбери предмет</option><option
          v-for="item in attendance.data.value.subjects"
          :key="item.id"
          :value="item.id"
        >{{ item.name }}</option></select></label><fieldset class="student-actions">
          <legend>Типы занятий</legend><label
            v-for="type in allTypes"
            :key="type"
          ><input
            v-model="types"
            type="checkbox"
            :value="type"
            :disabled="!availableTypes.includes(type)"
            @change="
              remember();
              loadSubject();
            "
          >{{ lessonTypeLabel(type)
          }}<span
            v-if="!availableTypes.includes(type)"
            class="web-caption"
          >
            · нет занятий</span></label>
        </fieldset>
      </div>
      <StudentNotice
        :loading="subjectStats.loading.value"
        :error="subjectStats.error.value"
        :empty="
          mode === 'subjects' && (!subject || !types.length)
            ? 'Выбери предмет и хотя бы один тип занятия.'
            : ''
        "
        @retry="loadSubject"
      />
      <template v-if="mode === 'weeks' || (subject && types.length)">
        <StudentMetrics
          v-if="metrics"
          :metrics="metrics"
          show-count
        /><p
          v-if="metrics"
          class="web-caption"
        >
          Прошедших учитываемых пар: {{ metrics.held }}. Будущие пары в проценты не входят.
        </p><StudentAttendanceTable
          :lessons="lessons"
          :read-only="scope.readOnly.value || attendance.data.value.terminalReadOnly"
        />
      </template>
    </template>
  </StudentRegion>
</template>
