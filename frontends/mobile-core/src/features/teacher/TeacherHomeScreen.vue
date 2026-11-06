<script setup lang="ts">
import { useProjectionRefresh } from '../../shared/projection-refresh';
import NotificationsEntryButton from '../notifications/NotificationsEntryButton.vue';
import { teacherLessonTypeLabel } from './teacher-ui-format';
import { computed, nextTick, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
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
  type TeacherSemester,
  type TeacherStatsSubjectOption,
} from './teacher-client';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import './teacher-screen.pcss';
import './teacher-home-r3.pcss';
const props = withDefaults(
  defineProps<{
    unreadCount?: number | null | undefined;
    onNotifications?: (() => void) | undefined;
    api: TeacherApi | null;
    semester: TeacherSemester | null;
    currentJournalSubjects: readonly TeacherStatsSubjectOption[];
    contextLoading?: boolean;
    contextError?: string | null;
    semesterChecking?: boolean;
    semesterCheckFailed?: boolean;
    selectedDate?: string;
    realtimeRevision?: number;
    profileEnabled?: boolean;
    roleSwitchEnabled?: boolean;
    lessonSlots?: readonly TeacherDaySlot[];
    calendarCloseSignal?: number;
  }>(),
  {
    unreadCount: null,
    onNotifications: undefined,
    contextLoading: false,
    contextError: null,
    semesterChecking: false,
    semesterCheckFailed: false,
    selectedDate: '',
    realtimeRevision: 0,
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
  'retry-context': [];
  error: [cause: unknown];
}>();
const assignments = ref<readonly TeacherAssignment[]>([]);
const currentJournalKeys = computed(
  () => new Set(props.currentJournalSubjects.map(({ groupId, subjectId }) => `${groupId}:${subjectId}`)),
);
const assignmentRows = computed(() =>
  assignments.value.map((assignment) => ({ assignment, opensJournal: canOpenJournal(assignment) })),
);
const lessons = ref<readonly TeacherDayLesson[]>([]);
const dayLoading = ref(false);
const dayError = ref<string | null>(null);
const assignmentsLoading = ref(false);
const assignmentsError = ref<string | null>(null);
const dateOpen = ref(false);
let dayRevision = 0;
let assignmentsRevision = 0;
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
const otherTimeline = computed(() =>
  timeline.value.filter((item) => item.kind !== 'lesson' || item.lesson.id !== current.value?.id),
);
const dateTrigger = ref<HTMLButtonElement | null>(null);
const navigationSemester = computed(() =>
  props.semesterChecking || props.semesterCheckFailed ? null : props.semester,
);
const canGoBack = computed(
  () => navigationSemester.value !== null && props.selectedDate > navigationSemester.value.dateFrom,
);
const canGoForward = computed(
  () => navigationSemester.value !== null && props.selectedDate < navigationSemester.value.dateTo,
);
const dayLabel = computed(() => {
  const date = new Date(`${props.selectedDate}T12:00:00`);
  return Number.isNaN(date.getTime())
    ? 'Выбери день'
    : new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', weekday: 'long' }).format(
        date,
      );
});
const semesterScope = [
  () => props.api,
  () => props.semester?.id,
  () => props.semester?.dateFrom,
  () => props.semester?.dateTo,
] as const;
watch(
  semesterScope,
  () => void loadAssignments(),
  { immediate: true },
);
watch([() => props.api, () => props.semester?.id], () => {
  dateOpen.value = false;
});
watch([...semesterScope, () => props.selectedDate], () => void loadDay(), { immediate: true });
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
  dayRevision += 1;
  assignmentsRevision += 1;
  if (dateOpen.value) emit('calendar-open', false);
});
onDeactivated(() => {
  dateOpen.value = false;
});
function inSemester(date: string): boolean {
  const { semester } = props;
  return semester !== null && date >= semester.dateFrom && date <= semester.dateTo;
}
async function loadDay(retainProjection = false): Promise<void> {
  const { api, semester, selectedDate } = props;
  const run = ++dayRevision;
  if (!retainProjection) lessons.value = [];
  dayError.value = null;
  if (!api || !semester || !inSemester(selectedDate)) {
    dayLoading.value = false;
    return;
  }
  if (!retainProjection) dayLoading.value = true;
  try {
    const day = await api.day(semester.id, selectedDate);
    if (run === dayRevision) lessons.value = day.lessons;
  } catch (cause) {
    if (run !== dayRevision || cause instanceof StaleSessionGenerationError) return;
    lessons.value = [];
    dayError.value =
      cause instanceof TeacherApiError ? cause.message : 'Не удалось получить расписание.';
    emit('error', cause);
  } finally {
    if (run === dayRevision) dayLoading.value = false;
  }
}
async function loadAssignments(retainProjection = false): Promise<void> {
  const { api, semester } = props;
  const run = ++assignmentsRevision;
  if (!retainProjection) assignments.value = [];
  assignmentsError.value = null;
  if (!api || !semester) {
    assignmentsLoading.value = false;
    return;
  }
  if (!retainProjection) assignmentsLoading.value = true;
  try {
    const assigned = await api.assignments(semester.id, semester.dateFrom, semester.dateTo);
    if (run === assignmentsRevision) assignments.value = assigned;
  } catch (cause) {
    if (run !== assignmentsRevision || cause instanceof StaleSessionGenerationError) return;
    assignments.value = [];
    assignmentsError.value =
      cause instanceof TeacherApiError ? cause.message : 'Не удалось получить назначения.';
    emit('error', cause);
  } finally {
    if (run === assignmentsRevision) assignmentsLoading.value = false;
  }
}
async function moveDay(delta: -1 | 1, event: MouseEvent): Promise<void> {
  const canMove = delta < 0 ? canGoBack : canGoForward;
  if (!canMove.value) return;
  const restoreFocus = event.currentTarget === document.activeElement;
  const date = new Date(`${props.selectedDate}T12:00:00Z`);
  if (Number.isNaN(date.getTime())) return;
  date.setUTCDate(date.getUTCDate() + delta);
  selectDay(date.toISOString().slice(0, 10));
  if (restoreFocus) {
    await nextTick();
    if (!canMove.value) dateTrigger.value?.focus();
  }
}
function selectDay(date: string): void {
  if (!inSemester(date)) return;
  emit('select-date', date);
  dateOpen.value = false;
}
function canOpenJournal({ semesterId, groupId, subjectId }: TeacherAssignment): boolean {
  return semesterId === props.semester?.id && currentJournalKeys.value.has(`${groupId}:${subjectId}`);
}
function openAssignment(assignment: TeacherAssignment): void {
  if (!canOpenJournal(assignment)) return;
  emit('open-journal', {
    semesterId: assignment.semesterId,
    groupId: assignment.groupId,
    subjectId: assignment.subjectId,
    lessonType: assignment.lessonType,
    page: 0,
    pageSize: 100,
  });
}
useProjectionRefresh(() => loadDay(true), { revision: () => props.realtimeRevision });
useProjectionRefresh(() => loadAssignments(true), { revision: () => props.realtimeRevision });
</script>
<template>
  <main
    class="teacher-screen teacher-home"
    aria-label="Сегодня у преподавателя"
    :aria-busy="dayLoading || (!semester && contextLoading)"
  >
    <h1 class="teacher-screen__sr">
      Сегодня
    </h1>
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
      >Преподаватель</span>
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
        :disabled="!canGoBack"
        @click="moveDay(-1, $event)"
      >
        <MobileIcon name="back" />
      </button>
      <button
        ref="dateTrigger"
        class="teacher-home__date-trigger"
        type="button"
        :disabled="!semester"
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
        :disabled="!canGoForward"
        @click="moveDay(1, $event)"
      >
        <MobileIcon
          name="back"
          class="teacher-screen__next"
        />
      </button>
    </div>
    <TeacherDayCalendar
      v-if="dateOpen && semester"
      :selected-date="selectedDate"
      :min-date="semester.dateFrom"
      :max-date="semester.dateTo"
      :return-focus="dateTrigger"
      :checking-semester="semesterChecking"
      :semester-check-failed="semesterCheckFailed"
      @select="selectDay"
      @close="dateOpen = false"
      @retry="emit('retry-context')"
    />
    <p
      v-if="dayLoading || (!semester && contextLoading)"
      class="teacher-screen__state"
      role="status"
    >
      Загружаем расписание…
    </p>
    <StudentWarningBlock
      v-else-if="!semester"
      title="Расписание недоступно"
      :message="contextError ?? 'Границы активного семестра недоступны.'"
      severity="error"
      action-label="Повторить"
      @action="emit('retry-context')"
    />
    <StudentWarningBlock
      v-else-if="dayError"
      title="Расписание недоступно"
      :message="dayError"
      severity="error"
      action-label="Повторить"
      @action="loadDay()"
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
        v-if="current && otherTimeline.length"
        class="teacher-screen__section-title"
      >
        Другие пары
      </h2>
      <ol
        v-if="otherTimeline.length"
        class="teacher-screen__list"
        aria-label="Пары выбранного дня"
      >
        <li
          v-for="item in otherTimeline"
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
          >{{ item.slot.startTime.slice(0, 5) }}–{{ item.slot.endTime.slice(0, 5) }}</span>
        </li>
      </ol>
    </template>
    <section
      v-if="semester && (contextError || assignmentsLoading || assignmentsError || assignments.length)"
      class="teacher-screen__section"
      aria-labelledby="teacher-assignments-title"
    >
      <h2
        id="teacher-assignments-title"
        class="teacher-screen__section-title"
      >
        Мои назначения
      </h2>
      <StudentWarningBlock
        v-if="semester && contextError"
        title="Учебный контекст недоступен"
        :message="contextError"
        severity="error"
        action-label="Повторить"
        @action="emit('retry-context')"
      />
      <p
        v-if="assignmentsLoading"
        class="teacher-screen__state"
        role="status"
      >
        Загружаем назначения…
      </p>
      <StudentWarningBlock
        v-else-if="assignmentsError"
        title="Назначения недоступны"
        :message="assignmentsError"
        severity="error"
        action-label="Повторить"
        @action="loadAssignments()"
      />
      <ul
        v-else
        class="teacher-screen__list"
      >
        <li
          v-for="{ assignment, opensJournal } in assignmentRows"
          :key="assignment.id"
        >
          <component
            :is="opensJournal ? 'button' : 'div'"
            class="teacher-screen__assignment"
            :type="opensJournal ? 'button' : undefined"
            :data-teacher-action="opensJournal ? 'journal-open' : undefined"
            v-on="opensJournal ? { click: () => openAssignment(assignment) } : {}"
          >
            <span><strong>{{ assignment.subjectName }}</strong><small>{{ assignment.groupName }} ·
              {{ teacherLessonTypeLabel(assignment.lessonType) }}</small></span><MobileIcon
              v-if="opensJournal"
              name="back"
              class="teacher-screen__next"
            />
          </component>
        </li>
      </ul>
    </section>
  </main>
</template>
