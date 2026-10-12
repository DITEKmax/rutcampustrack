<script setup lang="ts">
import NotificationsEntryButton from '../notifications/NotificationsEntryButton.vue';
import { teacherLessonTypeLabel } from './teacher-ui-format';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import TeacherDayCalendar from './TeacherDayCalendar.vue';
import { teacherDayTimeline, type TeacherDaySlot } from './teacher-day-timeline';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import TeacherLessonCard from './TeacherLessonCard.vue';
import {
  TeacherApiError,
  type TeacherApi,
  type TeacherAssignment,
  type TeacherDayLesson,
  type TeacherJournalQuery,
} from './teacher-client';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import './teacher-screen.pcss';
import './teacher-home-r3.pcss';
const props = withDefaults(
  defineProps<{
    unreadCount?: number | null | undefined;
    onNotifications?: (() => void) | undefined;
    api: TeacherApi | null;
    semesterId: number | null;
    selectedDate?: string;
    profileEnabled?: boolean;
    roleSwitchEnabled?: boolean;
    lessonSlots?: readonly TeacherDaySlot[];
    calendarCloseSignal?: number;
  }>(),
  {
    unreadCount: null,
    onNotifications: undefined,
    selectedDate: '',
    profileEnabled: false,
    roleSwitchEnabled: false,
    lessonSlots: () => [],
    calendarCloseSignal: 0,
  },
);
const emit = defineEmits<{
  'select-date': [date: string];
  'open-lesson': [lessonId: number];
  'open-journal': [query: TeacherJournalQuery];
  'open-stats': [];
  'open-profile': [];
  'open-role-switch': [];
  'calendar-open': [open: boolean];
  error: [cause: unknown];
}>();
const assignments = ref<readonly TeacherAssignment[]>([]);
const lessons = ref<readonly TeacherDayLesson[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const dateOpen = ref(false);
let revision = 0;
const ordered = computed(() =>
  [...lessons.value].sort(
    (a, b) =>
      (a.startsAt ?? '').localeCompare(b.startsAt ?? '') ||
      a.lessonNumber - b.lessonNumber ||
      a.id - b.id,
  ),
);
const current = computed(
  () => ordered.value.find((lesson) => !lesson.cancelled && lesson.status === 'ACTIVE') ?? null,
);
const timeline = computed(() => teacherDayTimeline(ordered.value, props.lessonSlots));
const dateTrigger = ref<HTMLButtonElement | null>(null);
const extraAssignments = computed(() =>
  assignments.value.filter(
    (assignment) =>
      !lessons.value.some(
        (lesson) =>
          lesson.groupId === assignment.groupId &&
          lesson.subjectId === assignment.subjectId &&
          lesson.lessonType === assignment.lessonType,
      ),
  ),
);
const dayLabel = computed(() => {
  const date = new Date(`${props.selectedDate}T12:00:00`);
  return Number.isNaN(date.getTime())
    ? 'Выбери день'
    : new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', weekday: 'long' }).format(
        date,
      );
});
watch(
  () => [props.api, props.semesterId, props.selectedDate] as const,
  () => {
    void load();
  },
  { immediate: true },
);
watch(dateOpen, (open) => {
  emit('calendar-open', open);
});
watch(
  () => props.calendarCloseSignal,
  () => {
    dateOpen.value = false;
  },
);
onBeforeUnmount(() => {
  revision += 1;
  if (dateOpen.value) emit('calendar-open', false);
});
async function load(): Promise<void> {
  const api = props.api;
  const semesterId = props.semesterId;
  const date = props.selectedDate;
  const run = ++revision;
  lessons.value = [];
  assignments.value = [];
  error.value = null;
  loading.value = false;
  if (!api || !semesterId || !date) return;
  loading.value = true;
  try {
    const [day, assigned] = await Promise.all([
      api.day(semesterId, date),
      api.assignments(semesterId, date, date),
    ]);
    if (run !== revision) return;
    lessons.value = day.lessons;
    assignments.value = assigned;
  } catch (cause) {
    if (run !== revision || cause instanceof StaleSessionGenerationError) return;
    error.value =
      cause instanceof TeacherApiError ? cause.message : 'Не удалось получить расписание.';
    emit('error', cause);
  } finally {
    if (run === revision) loading.value = false;
  }
}
function moveDay(delta: number): void {
  const date = new Date(`${props.selectedDate}T12:00:00Z`);
  if (Number.isNaN(date.getTime())) return;
  date.setUTCDate(date.getUTCDate() + delta);
  emit('select-date', date.toISOString().slice(0, 10));
}
function openAssignment(assignment: TeacherAssignment): void {
  emit('open-journal', {
    semesterId: assignment.semesterId,
    groupId: assignment.groupId,
    subjectId: assignment.subjectId,
    lessonType: assignment.lessonType,
    page: 0,
    pageSize: 100,
  });
}
</script>
<template>
  <main
    class="teacher-screen teacher-home"
    aria-label="Сегодня у преподавателя"
    :aria-busy="loading"
  >
    <h1 class="teacher-screen__sr"> Сегодня </h1>
    <header class="teacher-home__role-header notifications-role-header">
      <button
        v-if="roleSwitchEnabled"
        class="teacher-home__role"
        type="button"
        aria-label="Преподаватель. Сменить роль"
        @click="emit('open-role-switch')"
      >
        Преподаватель<MobileIcon name="chevron-down" />
      </button>
      <span
        v-else
        class="teacher-home__role"
        >Преподаватель</span
      >
      <NotificationsEntryButton
        v-if="onNotifications"
        :count="unreadCount ?? null"
        @click="onNotifications"
      />
    </header>
    <div class="teacher-home__pager">
      <button
        class="teacher-screen__icon-button"
        type="button"
        aria-label="Предыдущий день"
        @click="moveDay(-1)"
      >
        <MobileIcon name="back" />
      </button>
      <button
        ref="dateTrigger"
        class="teacher-home__date-trigger"
        type="button"
        :aria-expanded="dateOpen"
        aria-controls="teacher-date-choice"
        aria-haspopup="dialog"
        @click="dateOpen = !dateOpen"
      >
        {{ dayLabel }}
      </button>
      <button
        class="teacher-screen__icon-button"
        type="button"
        aria-label="Следующий день"
        @click="moveDay(1)"
      >
        <MobileIcon
          name="back"
          class="teacher-screen__next"
        />
      </button>
    </div>
    <TeacherDayCalendar
      v-if="dateOpen"
      :selected-date="selectedDate"
      :return-focus="dateTrigger"
      @select="
        emit('select-date', $event);
        dateOpen = false;
      "
      @close="dateOpen = false"
    />
    <p
      v-if="loading"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем расписание…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Расписание недоступно"
      :message="error"
      severity="error"
      action-label="Повторить"
      @action="load"
    />
    <p
      v-else-if="!timeline.length"
      class="teacher-screen__state"
      role="status"
    >
      На этот день пар нет.
    </p>
    <template v-else>
      <TeacherLessonCard
        v-if="current"
        :lesson="current"
        current
        @open="emit('open-lesson', $event)"
      />
      <h2
        v-if="current && timeline.length"
        class="teacher-screen__section-title"
      >
        Другие пары
      </h2>
      <ol
        class="teacher-screen__list"
        aria-label="Пары выбранного дня"
      >
        <li
          v-for="item in timeline"
          :key="
            item.kind === 'lesson' ? 'lesson-' + item.lesson.id : 'window-' + item.slot.lessonNumber
          "
        >
          <TeacherLessonCard
            v-if="item.kind === 'lesson'"
            :lesson="item.lesson"
            :current="item.lesson.id === current?.id"
            @open="emit('open-lesson', $event)"
          />
          <span
            v-else
            class="teacher-home__window"
            >{{ item.slot.startTime.slice(0, 5) }}–{{ item.slot.endTime.slice(0, 5) }}</span
          >
        </li>
      </ol>
    </template>
    <section
      v-if="!loading && !error && extraAssignments.length"
      class="teacher-screen__section"
      aria-labelledby="teacher-assignments-title"
    >
      <h2
        id="teacher-assignments-title"
        class="teacher-screen__section-title"
      >
        Мои назначения
      </h2>
      <ul class="teacher-screen__list">
        <li
          v-for="assignment in extraAssignments"
          :key="assignment.id"
        >
          <button
            class="teacher-screen__assignment"
            type="button"
            data-teacher-action="journal-open"
            @click="openAssignment(assignment)"
          >
            <span
              ><strong>{{ assignment.subjectName }}</strong
              ><small
                >{{ assignment.groupName }} ·
                {{ teacherLessonTypeLabel(assignment.lessonType) }}</small
              ></span
            ><MobileIcon
              name="back"
              class="teacher-screen__next"
            />
          </button>
        </li>
      </ul>
    </section>
  </main>
</template>
