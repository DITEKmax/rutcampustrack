<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { StudentSemesterSchedule } from '../../../../mobile-core/src/api/types';
import { useStudentResource, useStudentScope } from './student-state';
import { formatLessonDate, monday, serverDate, shiftDate } from './student-format';
import StudentRegion from './StudentRegion.vue';
import StudentNotice from './StudentNotice.vue';
import StudentLesson from './StudentLesson.vue';
import StudentPeriodPager from './StudentPeriodPager.vue';
import WebDateField from '../../shared/WebDateField.vue';
const scope = useStudentScope(),
  schedule = useStudentResource<StudentSemesterSchedule>(scope);
const date = ref(''),
  mode = ref<'day' | 'week'>('week');
async function load() {
  const semester = scope.session.value?.semester;
  if (!semester) {
    schedule.clear();
    return;
  }
  await schedule.load(async () => {
    const response = await scope.api.getSemesterSchedule(semester.id);
    scope.transport.assertCurrent();
    if (!response.data) throw Error('Расписание не получено. Повтори загрузку.');
    return response.data;
  }, true);
  if (scope.current() && !date.value)
    date.value = serverDate(scope.session.value?.serverNow) ?? semester.startsOn;
}
watch(scope.refreshed, load);
watch(scope.session, (value) => {
  if (!value) schedule.clear();
});
const first = computed(() =>
  mode.value === 'day' ? date.value : date.value ? monday(date.value) : '',
);
const last = computed(() =>
  first.value ? shiftDate(first.value, mode.value === 'day' ? 0 : 6) : '',
);
const periodLabel = computed(() => `${formatLessonDate(first.value)}${mode.value === 'week' ? ` — ${formatLessonDate(last.value)}` : ''}`);
const days = computed(() =>
  !first.value
    ? []
    : Array.from({ length: mode.value === 'day' ? 1 : 7 }, (_, i) => {
        const day = shiftDate(first.value, i);
        return {
          date: day,
          lessons: schedule.data.value?.lessons.filter((l) => l.date === day) ?? [],
        };
      }),
);
function step(direction: -1 | 1) {
  date.value = shiftDate(date.value, direction * (mode.value === 'day' ? 1 : 7));
}
</script>
<template>
  <StudentRegion
    :scope="scope"
    screen="WEB-STUDENT-SCHEDULE"
  >
    <h1>Расписание</h1>
    <StudentNotice
      :loading="scope.loading.value || schedule.loading.value"
      :error="scope.error.value || schedule.error.value"
      :empty="
        scope.session.value && !scope.session.value.semester ? 'Активного семестра пока нет.' : ''
      "
      @retry="scope.load"
    />
    <template v-if="schedule.data.value && date">
      <div
        class="student-schedule-toolbar web-panel"
        role="group"
        aria-label="Период расписания"
      >
        <StudentPeriodPager
          :label="periodLabel"
          unit="период расписания"
          :previous-disabled="first <= schedule.data.value.semester.startsOn"
          :next-disabled="last >= schedule.data.value.semester.endsOn"
          @step="step"
          @reset="date = serverDate(scope.session.value?.serverNow) ?? schedule.data.value.semester.startsOn"
        >
          <WebDateField
            v-model="date"
            label="Дата расписания"
            :trigger-label="periodLabel"
            :min="schedule.data.value.semester.startsOn"
            :max="schedule.data.value.semester.endsOn"
            :today="serverDate(scope.session.value?.serverNow) ?? undefined"
            required
          />
        </StudentPeriodPager>
        <div
          class="student-schedule-modes"
          role="group"
          aria-label="Вид расписания"
        >
          <button
            v-for="item in [
              { id: 'day', label: 'День' },
              { id: 'week', label: 'Неделя' },
            ] as const"
            :key="item.id"
            class="web-button"
            :aria-pressed="mode === item.id"
            :class="{ 'web-button-selected': mode === item.id }"
            @click="mode = item.id"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
      <section
        v-for="day in days"
        :key="day.date"
        class="web-stack student-schedule-day"
      >
        <h2>{{ formatLessonDate(day.date) }}</h2><p
          v-if="!day.lessons.length"
          class="web-caption"
        >
          Пар нет.
        </p>
        <ol class="student-lesson-list">
          <li
            v-for="lesson in day.lessons"
            :key="lesson.id"
          >
            <StudentLesson :lesson="lesson" />
          </li>
        </ol>
      </section>
    </template>
  </StudentRegion>
</template>
