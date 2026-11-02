<script setup lang="ts">
import { computed } from 'vue';
import type { StudentAttendanceLesson } from '../../../../mobile-core/src/api/types';
import { civilDate } from '../../../../mobile-core/src/features/attendance/attendance-periods';
import { formatLessonDate, lessonTypeLabel, shortDate, studentMark } from './student-format';
const props = defineProps<{ lessons: readonly StudentAttendanceLesson[]; readOnly: boolean; compactDates?: boolean }>();
const lessonTypeShort = { LECTURE: 'ЛК', PRACTICE: 'ПЗ', LAB: 'ЛЗ' } as const;
const dateFormat = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', timeZone: 'UTC' });
const weekdayFormat = computed(() => new Intl.DateTimeFormat('ru-RU', { weekday: props.compactDates ? 'short' : 'long', timeZone: 'UTC' }));
function dateHeader(date: string) {
  if (props.compactDates) return shortDate(date);
  return dateFormat.format(civilDate(date));
}
function weekdayHeader(date: string) {
  const label = weekdayFormat.value.format(civilDate(date));
  if (props.compactDates) return label.toLocaleLowerCase('ru');
  return label.charAt(0).toLocaleUpperCase('ru') + label.slice(1);
}
function dayEnd(index: number) {
  return index < props.lessons.length - 1 && props.lessons[index]?.date !== props.lessons[index + 1]?.date;
}
const dates = computed(() =>
  [...new Set(props.lessons.map((l) => l.date))].map((date) => ({
    date,
    count: props.lessons.filter((l) => l.date === date).length,
  })),
);
</script>
<template>
  <div class="web-stack">
    <h2 class="web-sr-only">
      Мои отметки
    </h2>
    <div
      v-if="lessons.length"
      class="student-attendance-scroll"
      tabindex="0"
      aria-label="Журнал твоей посещаемости"
    >
      <table class="student-attendance-table" :class="{ 'has-compact-dates': compactDates }" :style="{ '--student-attendance-lesson-count': lessons.length }">
        <caption class="web-sr-only">
          Твои отметки посещаемости по занятиям.
        </caption><thead>
          <tr class="student-attendance-weekdays">
            <th
              v-for="(day, index) in dates" :key="day.date" :colspan="day.count"
              :class="{ 'is-day-end': index < dates.length - 1 }"
              :aria-label="`${weekdayHeader(day.date)}, ${formatLessonDate(day.date)}`"
              :title="formatLessonDate(day.date)" scope="colgroup"
            >{{ weekdayHeader(day.date) }}</th>
          </tr>
          <tr class="student-attendance-dates">
            <th
              v-for="(day, index) in dates"
              :key="day.date"
              :colspan="day.count"
              :class="{ 'is-day-end': index < dates.length - 1 }"
              :aria-label="formatLessonDate(day.date)"
              :title="formatLessonDate(day.date)"
              scope="colgroup"
            >
              {{ dateHeader(day.date) }}
            </th>
          </tr><tr>
            <th
              v-for="(lesson, index) in lessons"
              :key="lesson.id"
              scope="col"
              class="student-attendance-lesson"
              :class="{ 'is-day-end': dayEnd(index) }"
            >
              <span class="student-attendance-subject"><span>{{ lesson.subject.name }}</span></span>
            </th>
          </tr><tr class="student-attendance-types-row">
            <th v-for="(lesson, index) in lessons" :key="lesson.id" :class="{ 'is-day-end': dayEnd(index) }" scope="col">
              <abbr :title="lessonTypeLabel(lesson.type)">{{ lessonTypeShort[lesson.type] }}</abbr>
            </th>
          </tr>
        </thead><tbody>
          <tr>
            <td
              v-for="(lesson, index) in lessons"
              :key="lesson.id"
              :class="{ 'is-day-end': dayEnd(index) }"
            >
              <span
                class="student-attendance-mark"
                role="img"
                :aria-label="`${lesson.subject.name}, ${formatLessonDate(lesson.date)}, ${lessonTypeLabel(lesson.type)}: ${studentMark(lesson.status).label}`"
              >
                <span class="student-mark" :data-tone="studentMark(lesson.status).tone" aria-hidden="true">{{ studentMark(lesson.status).symbol }}</span>
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p
      v-else
      class="web-notice"
    >
      За выбранный период пар нет.
    </p>
  </div>
</template>
