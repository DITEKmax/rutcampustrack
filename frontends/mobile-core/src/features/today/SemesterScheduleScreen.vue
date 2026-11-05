<script setup lang="ts">
import { computed } from 'vue';
import type { LessonSchedule, StudentSemesterSchedule } from '../../api/types';
import { academicDate, academicWeekOrdinal } from '../../domain/academic-week';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import './semester-schedule-screen.pcss';

const props = defineProps<{
  schedule: StudentSemesterSchedule | null;
  /** Kept for existing hosts; the semester view no longer selects a date. */
  selectedDate?: string;
  currentDate?: string;
  offline: boolean;
  loading?: boolean;
  error?: string | null;
  retryEnabled?: boolean;
}>();
const emit = defineEmits<{ back: []; retry: []; selectDate: [date: string] }>();
const dayNames = [
  'Понедельник',
  'Вторник',
  'Среда',
  'Четверг',
  'Пятница',
  'Суббота',
  'Воскресенье',
];
const lessonTypes = { LECTURE: 'Лекция', PRACTICE: 'Практика', LAB: 'Лабораторная' };
const dayMs = 86_400_000;
const today = computed(() => {
  if (props.currentDate && academicDate(props.currentDate) !== null) return props.currentDate;
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Moscow' }).format(new Date());
});
const currentWeek = computed(() =>
  academicWeekOrdinal(
    today.value,
    props.schedule?.semester.startsOn ?? null,
    props.schedule?.semester.endsOn ?? null,
  ),
);
function weekday(date: string): number {
  return (new Date(`${date}T12:00:00Z`).getUTCDay() + 6) % 7;
}
function dateLabel(date: string): string {
  return new Date(`${date}T12:00:00Z`).toLocaleDateString('ru-RU', {
    timeZone: 'Europe/Moscow',
    day: 'numeric',
    month: 'long',
  });
}
function dateRange(first: string, last: string): string {
  if (first === last) return dateLabel(first);
  if (first.slice(0, 7) === last.slice(0, 7)) return `${Number(first.slice(8))}–${dateLabel(last)}`;
  return `${dateLabel(first)} — ${dateLabel(last)}`;
}
function baseRoom(lesson: LessonSchedule): string | null {
  // A dated room change must not replace the repeating timetable's room.
  return lesson.room.changeState === 'CHANGED' ? lesson.room.previous : lesson.room.current;
}
function repetition(dates: string[]): { label: string; dates: string } {
  const first = dates[0]!;
  const last = dates.at(-1)!;
  const intervals = dates
    .slice(1)
    .map((date, index) => (academicDate(date)! - academicDate(dates[index]!)!) / dayMs);
  if (intervals.length && intervals.every((interval) => interval === 7)) {
    return { label: 'Каждую неделю', dates: dateRange(first, last) };
  }
  if (intervals.length && intervals.every((interval) => interval === 14)) {
    return { label: 'Через неделю', dates: dates.map(dateLabel).join(' · ') };
  }
  return {
    label: dates.length === 1 ? 'Разовое занятие' : 'По датам',
    dates: dates.map(dateLabel).join(' · '),
  };
}
const days = computed(() => {
  const groups = new Map<string, { lesson: LessonSchedule; occurrences: LessonSchedule[] }>();
  for (const lesson of props.schedule?.lessons ?? []) {
    if (academicDate(lesson.date) === null) continue;
    const key = JSON.stringify([
      weekday(lesson.date),
      lesson.lessonNumber,
      lesson.startsAt,
      lesson.endsAt,
      lesson.subject.id,
      lesson.subject.name,
      lesson.subject.type,
      baseRoom(lesson),
    ]);
    const group = groups.get(key);
    if (group) group.occurrences.push(lesson);
    else groups.set(key, { lesson, occurrences: [lesson] });
  }
  const rows = [...groups]
    .map(([key, group]) => {
      const occurrences = group.occurrences.slice().sort((a, b) => a.date.localeCompare(b.date));
      const dates = [...new Set(occurrences.map((lesson) => lesson.date))];
      return {
        key,
        lesson: group.lesson,
        day: weekday(group.lesson.date),
        room: baseRoom(group.lesson),
        repetition: repetition(dates),
        // Historical exceptions never become active timetable changes.
        changes: occurrences.filter(
          (lesson) =>
            lesson.date >= today.value &&
            (lesson.status === 'CANCELLED' || lesson.room.changeState === 'CHANGED'),
        ),
      };
    })
    .sort(
      (a, b) =>
        a.lesson.startsAt.localeCompare(b.lesson.startsAt) ||
        a.lesson.lessonNumber - b.lesson.lessonNumber ||
        a.lesson.subject.name.localeCompare(b.lesson.subject.name),
    );
  return dayNames
    .map((name, index) => ({
      name,
      index,
      isToday: weekday(today.value) === index && currentWeek.value !== null,
      lessons: rows.filter((row) => row.day === index),
    }))
    .filter((day) => day.lessons.length > 0);
});
const loadedPeriod = computed(() => {
  const dates = (props.schedule?.lessons ?? [])
    .map((lesson) => lesson.date)
    .filter((date) => academicDate(date) !== null)
    .sort();
  return dates.length ? dateRange(dates[0]!, dates.at(-1)!) : null;
});
</script>
<template>
  <main
    class="semester-screen"
    aria-labelledby="semester-title"
  >
    <header class="today-task-header">
      <button
        class="today-back"
        type="button"
        aria-label="Вернуться в Ещё"
        @click="emit('back')"
      >
        <MobileIcon name="back" />
      </button>
      <h1 id="semester-title"> Расписание семестра </h1>
    </header>
    <p
      v-if="loading"
      role="status"
      aria-busy="true"
    >
      Загружаем расписание семестра…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      severity="error"
      title="Не удалось получить расписание"
      :message="error"
      action-label="Повторить"
      :action-disabled="!retryEnabled"
      @action="emit('retry')"
    />
    <StudentWarningBlock
      v-else-if="!schedule"
      title="Расписание пока недоступно"
      :message="
        offline
          ? 'Сохранённого расписания семестра нет. Открой PWA онлайн, чтобы загрузить данные.'
          : 'Данные расписания семестра пока недоступны.'
      "
    />
    <template v-else>
      <StudentWarningBlock
        v-if="offline"
        title="Сохранённое расписание"
        message="Ты офлайн. Показаны последние сохранённые данные семестра."
      />
      <section
        class="semester-screen__summary"
        aria-label="Учебный период"
      >
        <div class="semester-screen__semester">
          <h2>{{ schedule.semester.name }}</h2>
          <span v-if="currentWeek">{{ currentWeek }}-я учебная неделя</span>
        </div>
        <p class="semester-screen__period">
          {{ dateRange(schedule.semester.startsOn, schedule.semester.endsOn) }}
        </p>
        <p class="semester-screen__today">
          <MobileIcon name="calendar" />Сегодня, {{ dateLabel(today) }}
        </p>
      </section>
      <template v-if="days.length">
        <div class="semester-screen__intro">
          <h2>Учебная неделя</h2>
          <p>Занятия: {{ loadedPeriod }}. Разовые изменения указаны у пар.</p>
        </div>
        <section
          v-for="day in days"
          :key="day.index"
          class="semester-screen__day"
          :aria-labelledby="`semester-day-${day.index}`"
        >
          <header
            class="semester-screen__day-header"
            :data-today="day.isToday"
          >
            <h2 :id="`semester-day-${day.index}`">
              {{ day.name }}
            </h2>
            <span v-if="day.isToday">Сегодня</span>
          </header>
          <ol class="semester-screen__lessons">
            <li
              v-for="row in day.lessons"
              :key="row.key"
              class="semester-screen__lesson"
            >
              <div class="semester-screen__time">
                <strong>{{ row.lesson.startsAt.slice(0, 5) }}</strong>
                <span>{{ row.lesson.endsAt.slice(0, 5) }}</span>
                <span class="semester-screen__number">{{ row.lesson.lessonNumber }} пара</span>
              </div>
              <div class="semester-screen__details">
                <h3>{{ row.lesson.subject.name }}</h3>
                <p class="semester-screen__type">
                  {{ lessonTypes[row.lesson.subject.type] }}
                </p>
                <p class="semester-screen__rotation">
                  {{ row.repetition.label }}
                </p>
                <p class="semester-screen__dates">
                  {{ row.repetition.dates }}
                </p>
                <p class="semester-screen__room">
                  <MobileIcon name="room" />{{ row.room ?? 'Аудитория уточняется' }}
                </p>
                <ul
                  v-if="row.changes.length"
                  class="semester-screen__changes"
                  aria-label="Изменения занятия"
                >
                  <li
                    v-for="change in row.changes"
                    :key="change.id"
                    :data-cancelled="change.status === 'CANCELLED'"
                  >
                    <strong>{{ dateLabel(change.date) }}</strong>
                    <span v-if="change.status === 'CANCELLED'">Пара отменена</span>
                    <template v-else>
                      <span>Аудитория {{ change.room.current ?? 'уточняется' }}</span>
                      <span v-if="change.room.previous"
                        >вместо <s>{{ change.room.previous }}</s></span
                      >
                    </template>
                  </li>
                </ul>
              </div>
            </li>
          </ol>
        </section>
      </template>
      <p
        v-else
        role="status"
      >
        В расписании семестра пока нет занятий.
      </p>
    </template>
  </main>
</template>
