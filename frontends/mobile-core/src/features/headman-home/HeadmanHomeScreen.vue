<script setup lang="ts">
import NotificationsEntryButton from '../notifications/NotificationsEntryButton.vue';
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import emptyHomeworkIllustration from '../../assets/headman-homework-empty-compact.svg?raw';
import { academicWeekOrdinal, type AcademicWeekPolicy } from '../../domain/academic-week';
import type {
  HeadmanScheduleApi,
  HeadmanScheduleSemester,
} from '../schedule/headman-schedule-client';
import { readPublishedHomeworks } from '../homework/headman-published-homework';
import type { HeadmanHomeworkLessonContext } from '../homework/headman-homework-state';
import { moscowDate } from '../../domain/homework';
import { StaleSessionGenerationError } from '../../shared/session-owner';
import {
  HeadmanJournalApiError,
  type HeadmanJournalApi,
  type HeadmanJournalLesson,
  type HeadmanJournalReport,
} from '../headman-journal/headman-journal-client';
import type {
  HeadmanSubjectsApi,
  HeadmanSubject,
} from '../headman-subjects/headman-subjects-client';
import type {
  HeadmanHomeworkApi,
  HeadmanManagedHomework,
} from '../homework/headman-homework-client';
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client';
import type { HeadmanRequestsApi } from '../headman-requests/headman-requests-client';
import HeadmanManualRoster from '../headman-journal/HeadmanManualRoster.vue';
import HeadmanManualMarkTask from '../headman-journal/HeadmanManualMarkTask.vue';
import { excuseLabel, isAttendanceDenied, reportMatchesLesson, useHeadmanManualMarks } from '../headman-journal/headman-manual-marks';
import './headman-home-screen.pcss';

const props = withDefaults(
  defineProps<{
    unreadCount?: number | null | undefined;
    onNotifications?: (() => void) | undefined;
    api: HeadmanJournalApi | null;
    assistantPermissions?: readonly HeadmanAssistantPermission[] | null;
    weekPolicyApi?: HeadmanScheduleApi | null;
    groupId: number | null;
    actorUserId?: string | null;
    groupLabel?: string | null;
    subjectsApi?: HeadmanSubjectsApi | null;
    homeworkApi?: HeadmanHomeworkApi | null;
    requestsApi?: HeadmanRequestsApi | null;
    selectedDate?: string;
    offline?: boolean;
    readOnly?: boolean;
    roleSwitchDisabled?: boolean;
    homeworkEnabled?: boolean;
    logEnabled?: boolean;
  }>(),
  {
    unreadCount: null,
    assistantPermissions: null,
    onNotifications: undefined,
    weekPolicyApi: null,
    groupLabel: null,
    actorUserId: null,
    subjectsApi: null,
    homeworkApi: null,
    requestsApi: null,
    selectedDate: '',
    offline: false,
    readOnly: false,
    roleSwitchDisabled: true,
    homeworkEnabled: false,
    logEnabled: false,
  },
);
const emit = defineEmits<{
  openLesson: [lesson: HeadmanJournalLesson];
  openHomework: [context: HeadmanHomeworkLessonContext];
  openLog: [lesson: HeadmanJournalLesson];
  selectDate: [date: string];
  error: [cause: unknown];
  roleSwitch: [];
  taskChange: [active: boolean];
}>();
function todayIso(): string {
  return moscowDate(new Date().toISOString()) ?? new Date().toISOString().slice(0, 10);
}
const selectedDate = ref(props.selectedDate || todayIso());
const lessons = shallowRef<readonly HeadmanJournalLesson[]>([]);
const subjects = shallowRef<readonly HeadmanSubject[]>([]);
const report = shallowRef<HeadmanJournalReport | null>(null);
const homeworks = shallowRef<readonly HeadmanManagedHomework[]>([]);
const selectedLessonId = ref<number | null>(null);
const loading = ref(false);
const detailLoading = ref(false);
const homeworkLoading = ref(false);
const error = ref<string | null>(null);
const detailError = ref<string | null>(null);
const homeworkError = ref<string | null>(null);
const homeworkExpanded = ref(false);
const homeworkLoaded = ref(false);
const weekPolicy = shallowRef<AcademicWeekPolicy | null>(null);
const semesterCatalog = shallowRef<readonly HeadmanScheduleSemester[]>([]);
const weekError = ref<string | null>(null);
const weekLoading = ref(false);
let weekRevision = 0;
let loadRevision = 0;
let detailRevision = 0;
let rosterRevision = 0;
let disposed = false;
const selectedLesson = computed(
  () => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null,
);
const defaultLesson = computed(
  () =>
    lessons.value.find((lesson) => lesson.status === 'ACTIVE' && lesson.current) ??
    lessons.value.find((lesson) => lesson.status === 'ACTIVE') ??
    lessons.value[0] ??
    null,
);
const canReturnToToday = computed(
  () => selectedDate.value !== todayIso() || (selectedLesson.value !== null && selectedLesson.value !== defaultLesson.value),
);
const manualRoster = ref<InstanceType<typeof HeadmanManualRoster> | null>(null);
const lessonNotStarted = computed(
  () => selectedLesson.value?.status === 'PLANNED' && report.value?.editable === false,
);
const manual = useHeadmanManualMarks({
  scope: () => ({
    api: props.api, requestsApi: props.requestsApi, groupId: props.groupId, date: selectedDate.value, lesson: selectedLesson.value,
    actorUserId: props.actorUserId, offline: props.offline, readOnly: props.readOnly,
    permissions: props.assistantPermissions,
  }),
  report,
  loading: () => detailLoading.value,
  refresh: async () => Boolean(await loadAttendance(true)),
  deny: purgeReport,
  error: (cause) => emit('error', cause),
  focus: (userId, choice) => manualRoster.value?.focus(userId, choice),
});
const { draft: manualDraft, draftEntry: manualEntry, pendingUserId, blocked: manualBlocked, staleRows, issues: rowIssues } = manual;
watch(() => manualDraft.value !== null, (active) => emit('taskChange', active), { flush: 'sync' });
defineExpose({ back: manual.closeDraft });

const lessonIndex = computed(() =>
  lessons.value.findIndex((lesson) => lesson.id === selectedLessonId.value),
);
const subjectName = computed(
  () =>
    subjects.value.find((subject) => subject.id === selectedLesson.value?.subjectId)?.name ??
    'Занятие группы',
);
const excused = computed(
  () => report.value?.entries.filter((entry) => entry.status === 'EXCUSED') ?? [],
);
const presentCount = computed(
  () => report.value?.entries.filter((entry) => entry.status === 'PRESENT').length ?? null,
);
const emptyCount = computed(
  () => report.value?.entries.filter((entry) => entry.status === 'EMPTY').length ?? null,
);
const geoMetric = computed(() => {
  const present = report.value?.entries.filter((entry) => entry.status === 'PRESENT') ?? [];
  const knownSources = [
    'student_geo',
    'headman',
    'auto_scheduler',
    'late_checkin',
    'headman_excuse',
  ];
  if (
    !present.length ||
    present.some((entry) => !entry.source || !knownSources.includes(entry.source))
  )
    return null;
  const geoCount = present.filter((entry) => entry.source === 'student_geo').length;
  return {
    numerator: geoCount,
    denominator: present.length,
    percent: Math.round((geoCount / present.length) * 100),
  };
});

const ordinal = computed(() => {
  const semester = semesterCatalog.value.find(
    (item) =>
      item.dateFrom &&
      item.dateTo &&
      selectedDate.value >= item.dateFrom &&
      selectedDate.value <= item.dateTo,
  );
  return semester
    ? academicWeekOrdinal(selectedDate.value, semester.dateFrom ?? null, semester.dateTo ?? null)
    : null;
});
const blockConfirm = ref(false);
const blockTrigger = ref<HTMLButtonElement | null>(null);
const blockCancel = ref<HTMLButtonElement | null>(null);
const blockSubmit = ref<HTMLButtonElement | null>(null);
const geoDenied = ref(false);
function focusBlockTrigger(revision: number): void {
  void nextTick(() => {
    if (!disposed && revision === blockRevision && canBlock.value && !blockPending.value)
      blockTrigger.value?.focus?.({ preventScroll: true });
  });
}
function openBlockConfirmation(): void {
  if (!canBlock.value || blockPending.value) return;
  const revision = blockRevision;
  blockConfirm.value = true;
  void nextTick(() => {
    if (!disposed && revision === blockRevision && blockConfirm.value && canBlock.value)
      blockCancel.value?.focus?.({ preventScroll: true });
  });
}
function dismissBlockConfirmation(): void {
  if (!blockConfirm.value || blockPending.value) return;
  blockConfirm.value = false;
  focusBlockTrigger(blockRevision);
}
const blockPending = ref(false);
const blockError = ref<string | null>(null);
let blockRevision = 0;
const canBlock = computed(
  () =>
    !!props.api?.writable && !geoDenied.value &&
    !props.offline &&
    !props.readOnly &&
    props.assistantPermissions === null &&
    props.groupId !== null &&
    selectedLesson.value?.groupId === props.groupId &&
    selectedLesson.value.date === selectedDate.value &&
    selectedDate.value === todayIso() &&
    selectedLesson.value.current === true &&
    selectedLesson.value.status === 'ACTIVE' &&
    typeof selectedLesson.value.blockedByHeadman === 'boolean',
);
watch(
  () =>
    [
      props.api,
      props.groupId,
      props.offline,
      props.readOnly,
      props.actorUserId,
      props.assistantPermissions?.join('\u0000') ?? null,
      selectedDate.value,
      selectedLessonId.value,
    ] as const,
  () => {
    blockRevision += 1;
    blockConfirm.value = false;
    blockPending.value = false;
    blockError.value = null;
    geoDenied.value = false;
  },
  { flush: 'sync' },
);
async function changeGeoBlock(blocked: boolean): Promise<void> {
  const api = props.api;
  const lesson = selectedLesson.value;
  const group = props.groupId;
  if (!api || !lesson || !canBlock.value || blockPending.value ||
      (blocked ? !blockConfirm.value || lesson.blockedByHeadman : !lesson.blockedByHeadman))
    return;
  const run = ++blockRevision;
  const current = () =>
    !disposed &&
    run === blockRevision &&
    api === props.api &&
    group === props.groupId &&
    lesson.id === selectedLessonId.value &&
    canBlock.value;
  blockPending.value = true;
  blockError.value = null;
  try {
    const updated = await api.setHeadmanBlock(lesson.id, blocked);
    if (!current()) return;
    if (updated.groupId !== group || updated.date !== lesson.date ||
        updated.id !== lesson.id || updated.blockedByHeadman !== blocked)
      throw new Error('Сервер не подтвердил геоблокировку выбранной пары.');
    blockConfirm.value = false;
    lessons.value = lessons.value.map((item) => (item.id === lesson.id ? updated : item));
  } catch (cause) {
    if (!current()) return;
    if (isAttendanceDenied(cause)) {
      blockConfirm.value = false;
      geoDenied.value = true;
      blockPending.value = false;
      onManualAccessDenied(cause);
      return;
    }
    blockError.value =
      cause instanceof Error ? cause.message : 'Не удалось изменить геоблокировку.';
  } finally {
    if (!disposed && run === blockRevision) {
      blockPending.value = false;
      if (blockConfirm.value) {
        void nextTick(() => {
          if (run === blockRevision && blockConfirm.value && !blockPending.value)
            blockSubmit.value?.focus?.({ preventScroll: true });
        });
      } else focusBlockTrigger(run);
    }
  }
}
async function loadWeekPolicy(): Promise<void> {
  const run = ++weekRevision;
  const api = props.weekPolicyApi;
  const date = selectedDate.value;
  const weekStart = weekDays.value[0]?.iso;
  const group = props.groupId;
  weekPolicy.value = null;
  semesterCatalog.value = [];
  weekError.value = null;
  weekLoading.value = false;
  if (!api || props.offline || group === null) {
    weekError.value = 'Чётность недели недоступна.';
    return;
  }
  weekLoading.value = true;
  const current = () =>
    !disposed &&
    run === weekRevision &&
    api === props.weekPolicyApi &&
    weekStart === weekDays.value[0]?.iso &&
    group === props.groupId &&
    !props.offline;
  try {
    const [policy, catalog] = await Promise.all([
      api.academicWeekPolicy(date),
      api.listSemesters(),
    ]);
    if (!current()) return;
    weekPolicy.value = policy;
    semesterCatalog.value = catalog;
  } catch (cause) {
    if (!current() || cause instanceof StaleSessionGenerationError) return;
    weekError.value = 'Не удалось загрузить чётность недели.';
    emit('error', cause);
  } finally {
    if (!disposed && run === weekRevision) weekLoading.value = false;
  }
}
const weekDays = computed(() => {
  const date = new Date(`${selectedDate.value}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() - ((date.getUTCDay() + 6) % 7));
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(date);
    day.setUTCDate(day.getUTCDate() + index);
    return {
      iso: day.toISOString().slice(0, 10),
      number: day.getUTCDate(),
      name: ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'][index],
    };
  });
});
function selectDate(value: string): void {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value === selectedDate.value) return;
  selectedDate.value = value;
  emit('selectDate', value);
}
function shiftWeek(delta: number): void {
  const date = new Date(`${selectedDate.value}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + delta * 7);
  selectDate(date.toISOString().slice(0, 10));
}
function pageLesson(delta: number): void {
  const lesson = lessons.value[lessonIndex.value + delta];
  if (lesson) selectedLessonId.value = lesson.id;
}
function returnToToday(): void {
  const today = todayIso();
  if (selectedDate.value === today) selectedLessonId.value = defaultLesson.value?.id ?? null;
  else selectDate(today);
}
function timeRange(lesson: HeadmanJournalLesson): string {
  return `${lesson.startTime?.slice(0, 5) ?? '—'}–${lesson.endTime?.slice(0, 5) ?? '—'}`;
}
function lessonStatus(lesson: HeadmanJournalLesson): string {
  return lesson.status === 'ACTIVE'
    ? 'текущая'
    : {
        PLANNED: 'предстоит',
        ACTIVE: 'идёт',
        CLOSED: 'прошла',
        CANCELLED: 'отменена',
        TRANSFERRED: 'перенесена',
        UNSUPPORTED: 'статус недоступен',
      }[lesson.status];
}
function typeLabel(value: string | null): string {
  return value === 'LECTURE'
    ? 'Лекция'
    : value === 'PRACTICE'
      ? 'Практика'
      : value === 'LAB'
        ? 'Лабораторная'
        : (value ?? 'Тип не указан');
}
function safeHomeworkLink(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}
async function load(): Promise<void> {
  const revision = ++loadRevision;
  detailRevision += 1;
  const api = props.api;
  const groupId = props.groupId;
  const date = selectedDate.value;
  error.value = null;
  lessons.value = [];
  report.value = null;
  subjects.value = [];
  selectedLessonId.value = null;
  if (props.offline || !api || groupId === null) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const [next, subjectList] = await Promise.all([
      api.listLessons(groupId, date, date),
      props.subjectsApi?.listSubjects(groupId) ?? Promise.resolve([]),
    ]);
    if (disposed || revision !== loadRevision || api !== props.api || groupId !== props.groupId)
      return;
    if (next.some((lesson) => lesson.groupId !== groupId || lesson.date !== date))
      throw new Error('Сервер вернул занятия другой группы или даты.');
    lessons.value = next
      .slice()
      .sort((left, right) => (left.lessonNumber ?? 99) - (right.lessonNumber ?? 99));
    subjects.value = subjectList.filter((subject) => subject.groupId === groupId);
    selectedLessonId.value = defaultLesson.value?.id ?? null;
  } catch (cause) {
    if (disposed || revision !== loadRevision || cause instanceof StaleSessionGenerationError)
      return;
    error.value =
      cause instanceof HeadmanJournalApiError && [401, 403].includes(cause.response.status)
        ? 'Нет доступа к расписанию этой группы.'
        : cause instanceof Error
          ? cause.message
          : 'Не удалось загрузить занятия.';
    emit('error', cause);
  } finally {
    if (!disposed && revision === loadRevision) loading.value = false;
  }
}
function canViewAttendance(): boolean {
  const permissions = props.assistantPermissions;
  return permissions === null || permissions.includes('MARK_ATTENDANCE') || permissions.includes('VIEW_STATS');
}

function purgeReport(): void {
  rosterRevision += 1;
  clearHomework();
  report.value = null;
  detailLoading.value = false;
  detailError.value = 'Состав группы недоступен для текущей роли.';
}
function onManualAccessDenied(cause: unknown): void {
  manual.deny();
  if (!(cause instanceof StaleSessionGenerationError)) emit('error', cause);
}

async function loadAttendance(rowRefresh = false): Promise<HeadmanJournalReport | null> {
  const revision = ++rosterRevision;
  const { api, groupId, actorUserId } = props;
  const date = selectedDate.value;
  const lesson = selectedLesson.value;
  detailError.value = null;
  if (!api || !lesson || props.offline || !canViewAttendance()) {
    detailLoading.value = false;
    return null;
  }
  const current = () =>
    !disposed && revision === rosterRevision && api === props.api && groupId === props.groupId &&
    actorUserId === props.actorUserId && date === selectedDate.value &&
    lesson.id === selectedLessonId.value && !props.offline && canViewAttendance();
  detailLoading.value = true;
  try {
    const next = await api.getLessonAttendance(lesson.id);
    if (!current()) return null;
    if (!reportMatchesLesson(next, lesson, groupId, date))
      throw new Error('Сервер вернул состав другой пары.');
    report.value = next;
    manual.reportRefreshed();
    return next;
  } catch (cause) {
    if (!current()) return null;
    if (isAttendanceDenied(cause)) {
      onManualAccessDenied(cause);
      return null;
    }
    if (!rowRefresh) {
      detailError.value = cause instanceof Error ? cause.message : 'Не удалось загрузить отметки.';
      emit('error', cause);
    }
    return null;
  } finally {
    if (revision === rosterRevision) detailLoading.value = false;
  }
}

function openSelectedHomework(): void {
  const lesson = selectedLesson.value;
  if (
    !lesson ||
    lesson.groupId !== props.groupId ||
    lesson.date !== selectedDate.value ||
    props.offline ||
    props.readOnly ||
    !props.homeworkEnabled
  )
    return;
  const attendance = report.value;
  const semesterId =
    attendance &&
    attendance.subjectId === lesson.subjectId &&
    reportMatchesLesson(attendance, lesson, props.groupId, selectedDate.value)
      ? attendance.semesterId
      : null;
  emit('openHomework', { lesson, semesterId });
}

function clearHomework(): void {
  detailRevision += 1;
  homeworks.value = [];
  homeworkLoaded.value = false;
  homeworkLoading.value = false;
  homeworkError.value = null;
  homeworkExpanded.value = false;
}

async function loadSelectedHomework(attendance: HeadmanJournalReport | null): Promise<void> {
  clearHomework();
  const revision = detailRevision;
  const { groupId, homeworkApi } = props;
  const lesson = selectedLesson.value;
  if (!lesson || groupId === null || props.offline) return;
  if (!attendance || !reportMatchesLesson(attendance, lesson, groupId, selectedDate.value)) {
    homeworkError.value = 'Домашнее задание неизвестно: данные выбранной пары не загрузились.';
    return;
  }
  if (!homeworkApi || attendance.semesterId === null) return;
  const current = () =>
    !disposed && revision === detailRevision && groupId === props.groupId &&
    lesson.id === selectedLessonId.value && homeworkApi === props.homeworkApi && !props.offline;
  homeworkLoading.value = true;
  try {
    const items = await readPublishedHomeworks(homeworkApi, groupId, attendance.semesterId, current);
    if (!current()) return;
    homeworkLoaded.value = true;
    homeworks.value = (items ?? []).filter(
      (item) =>
        item.subjectId === lesson.subjectId &&
        item.lessonDate === lesson.date &&
        (item.bindingMode === 'DATE' || item.lessonNumber === lesson.lessonNumber),
    );
  } catch (cause) {
    if (!current() || cause instanceof StaleSessionGenerationError) return;
    homeworkError.value = 'Не удалось загрузить домашнее задание.';
    emit('error', cause);
  } finally {
    if (current()) homeworkLoading.value = false;
  }
}

async function loadDetails(): Promise<void> {
  clearHomework();
  report.value = null;
  if (!selectedLesson.value || props.groupId === null || props.offline) {
    rosterRevision += 1;
    detailLoading.value = false;
    detailError.value = null;
    return;
  }
  homeworkLoading.value = true;
  const revision = rosterRevision + 1;
  const attendance = await loadAttendance();
  if (revision !== rosterRevision || disposed) return;
  await loadSelectedHomework(attendance);
}
watch(
  () => [props.api, props.subjectsApi, props.groupId, props.actorUserId, props.offline, selectedDate.value] as const,
  () => {
    void load();
  },
  { immediate: true, flush: 'sync' },
);
watch(
  () => [selectedLessonId.value, props.readOnly, props.assistantPermissions?.join('\u0000') ?? null] as const,
  () => {
    void loadDetails();
  },
  { flush: 'sync' },
);
watch(() => props.homeworkApi, () => {
  if (report.value) void loadSelectedHomework(report.value);
});
watch(
  () => props.selectedDate,
  (date) => {
    if (date) selectedDate.value = date;
  },
);
watch(
  () => [props.weekPolicyApi, props.groupId, props.offline, weekDays.value[0]?.iso] as const,
  () => {
    void loadWeekPolicy();
  },
  { immediate: true, flush: 'sync' },
);
onBeforeUnmount(() => {
  disposed = true;
  rosterRevision += 1;
  blockRevision += 1;
  weekRevision += 1;
  loadRevision += 1;
  detailRevision += 1;
});
</script>

<template>
  <main
    v-if="manualDraft && manualEntry && selectedLesson"
    class="headman-home"
  >
    <HeadmanManualMarkTask
      :key="manualEntry.userId"
      :manual="manual"
      :draft="manualDraft"
      :entry="manualEntry"
      :lesson-id="selectedLesson.id"
      :api="api"
      :requests-api="requestsApi"
      :context="`${subjectName} · ${selectedLesson.lessonNumber ?? '—'} пара · ${timeRange(selectedLesson)}`"
      @access-denied="onManualAccessDenied"
      @error="emit('error', $event)"
    />
  </main>
  <main
    v-else
    class="headman-home"
    aria-labelledby="headman-home-title"
    :aria-busy="loading"
    :data-lesson-id="selectedLessonId"
    :data-group-id="groupId"
  >
    <header class="headman-home__header notifications-role-header">
      <h1
        id="headman-home-title"
        class="headman-home__visually-hidden"
      >
        Сегодня
      </h1>
      <button
        class="headman-home__role"
        type="button"
        :disabled="offline || roleSwitchDisabled"
        aria-label="Сменить роль, активная роль: староста"
        @click="emit('roleSwitch')"
      >
        Староста<MobileIcon name="chevron-down" />
      </button>
      <NotificationsEntryButton
        v-if="onNotifications"
        :count="unreadCount ?? null"
        @click="onNotifications"
      />
    </header>
    <nav
      class="headman-home__week-pager"
      aria-label="Выбор недели"
    >
      <button
        type="button"
        aria-label="Предыдущая неделя"
        @click="shiftWeek(-1)"
      >
        <MobileIcon name="back" />
      </button>
      <output>{{
        new Date((weekDays[0]?.iso ?? selectedDate) + 'T12:00:00Z').toLocaleDateString('ru-RU', {
          day: 'numeric',
          month: 'short',
          timeZone: 'UTC',
        })
      }}
        —
        {{
          new Date((weekDays[6]?.iso ?? selectedDate) + 'T12:00:00Z').toLocaleDateString('ru-RU', {
            day: 'numeric',
            month: 'short',
            timeZone: 'UTC',
          })
        }}<small>{{
          weekPolicy
            ? 'неделя ' + weekPolicy.parityNumber + (ordinal ? ' · №' + ordinal : '')
            : weekLoading
              ? 'Загружаем чётность…'
              : 'Чётность недоступна'
        }}</small></output>
      <button
        type="button"
        aria-label="Следующая неделя"
        @click="shiftWeek(1)"
      >
        <MobileIcon
          class="headman-home__next"
          name="back"
        />
      </button>
    </nav>
    <StudentWarningBlock
      v-if="weekError && !offline"
      title="Чётность недоступна"
      :message="weekError"
      action-label="Повторить"
      @action="loadWeekPolicy"
    />
    <nav
      class="headman-home__calendar"
      aria-label="Дни недели"
    >
      <button
        v-for="day in weekDays"
        :key="day.iso"
        type="button"
        :aria-label="day.iso"
        :aria-current="day.iso === todayIso() ? 'date' : undefined"
        :aria-pressed="day.iso === selectedDate"
        @click="selectDate(day.iso)"
      >
        <span>{{ day.name }}</span><strong>{{ day.number }}</strong>
      </button>
    </nav>

    <StudentWarningBlock
      v-if="offline"
      title="Нет подключения"
      message="Расписание группы доступно при подключении к интернету."
    />
    <p
      v-else-if="groupId === null || !api"
      class="headman-home__state"
      role="alert"
    >
      Не удалось подключить расписание группы.
    </p>
    <p
      v-else-if="loading"
      class="headman-home__state"
      role="status"
    >
      Загружаем занятия…
    </p>
    <StudentWarningBlock
      v-else-if="error"
      title="Расписание недоступно"
      :message="error"
      action-label="Повторить"
      @action="load"
    />
    <p
      v-else-if="!selectedLesson"
      class="headman-home__state"
      role="status"
    >
      На этот день занятий нет.
    </p>
    <template v-else>
      <nav
        class="headman-home__pager"
        aria-label="Выбор пары"
      >
        <button
          type="button"
          aria-label="Предыдущая пара"
          :disabled="lessonIndex === 0"
          @click="pageLesson(-1)"
        >
          <MobileIcon name="back" />
        </button><span>{{ selectedLesson.lessonNumber ?? '—' }} пара · {{ lessonStatus(selectedLesson) }}</span><button
          type="button"
          aria-label="Следующая пара"
          :disabled="lessonIndex === lessons.length - 1"
          @click="pageLesson(1)"
        >
          <MobileIcon
            class="headman-home__next"
            name="back"
          />
        </button>
      </nav>
      <button
        class="headman-home__lesson"
        :data-current="selectedLesson.status === 'ACTIVE'"
        type="button"
        aria-label="Открыть учёт выбранной пары"
        @click="emit('openLesson', selectedLesson)"
      >
        <span class="headman-home__time">{{ timeRange(selectedLesson) }}</span><strong>{{ subjectName }}</strong><span class="headman-home__meta"><span><MobileIcon name="room" />{{ selectedLesson.room ?? 'Аудитория не указана' }}</span><span><MobileIcon name="lesson" />{{ typeLabel(selectedLesson.lessonType) }}</span></span>
      </button>
      <StudentWarningBlock
        v-if="detailError && !report"
        title="Отметки недоступны"
        :message="detailError"
        action-label="Повторить"
        @action="loadDetails"
      />
      <div
        v-if="report"
        class="headman-home__metrics"
        :aria-busy="detailLoading"
        :aria-label="`Отметки выбранной пары${groupLabel ? `, ${groupLabel}` : ''}`"
      >
        <div>
          <span
            class="headman-home__circle"
            data-status="present"
            aria-label="Были"
          >+</span><strong>{{ presentCount ?? '—' }}</strong>
        </div><div>
          <span
            class="headman-home__circle"
            aria-label="Без отметки"
          >—</span><strong>{{ emptyCount ?? '—' }}</strong>
        </div><div>
          <MobileIcon name="geo-approved" /><strong
            :aria-label="
              geoMetric
                ? `Геолокацией подтверждено ${geoMetric.numerator} из ${geoMetric.denominator} отметок присутствия, ${geoMetric.percent}%`
                : 'Доля подтверждённых геоотметок неизвестна: нет присутствующих или известного источника каждой отметки'
            "
          >{{ geoMetric ? `${geoMetric.percent}%` : '—' }}</strong>
        </div>
      </div>

      <section
        v-if="report"
        class="headman-home__excused"
        aria-label="Уважительные причины выбранной пары"
      >
        <span class="headman-home__excused-count"><span
          class="headman-home__circle"
          data-status="excused"
        >у</span><strong>{{ excused.length }}</strong></span><span class="headman-home__excused-list"><span
          v-for="entry in excused"
          :key="entry.userId"
        ><strong>{{ entry.displayName }}</strong><small>{{
          excuseLabel(entry.excuseReason ?? entry.excuseType) ?? 'Уважительная причина'
        }}</small></span><span v-if="excused.length === 0">Уважительных причин нет</span></span>
      </section>
      <section
        v-if="report?.entries.length"
        class="headman-home__quick-roster"
        aria-labelledby="headman-home-roster-title"
      >
        <h2
          id="headman-home-roster-title"
          class="headman-home__visually-hidden"
        >
          Отметки выбранной пары
        </h2>
        <HeadmanManualRoster
          ref="manualRoster"
          :entries="report.entries"
          label="Отметки студентов"
          :future="lessonNotStarted"
          :pending-user-id="pendingUserId"
          :blocked="manualBlocked"
          :issues="rowIssues"
          :stale-rows="staleRows"
          :can-choose="manual.canChoose"
          @choose="manual.choose"
          @refresh="manual.refreshRow"
        />
      </section>
      <p v-else-if="report" class="headman-home__state">Состав группы пуст.</p>
      <p v-else-if="detailLoading" class="headman-home__state" role="status">Загружаем состав группы…</p>
      <section
        class="headman-home__homework"
        :data-expanded="homeworkExpanded"
        aria-label="Домашнее задание пары"
      >
        <button
          class="headman-home__homework-toggle"
          type="button"
          :aria-expanded="homeworkExpanded"
          :disabled="!homeworks.length"
          @click="homeworkExpanded = !homeworkExpanded"
        >
          <!-- Trusted project SVG from the headman handoff; its colors use the active theme tokens. -->
          <!-- eslint-disable vue/no-v-html -->
          <span
            v-if="homeworkLoaded && !homeworks.length && !homeworkError"
            class="headman-home__empty-art"
            aria-hidden="true"
            v-html="emptyHomeworkIllustration"
          />
          <!-- eslint-enable vue/no-v-html -->
          <MobileIcon
            v-else
            name="homework"
          /><span><strong>{{
            homeworkError
              ? 'ДЗ недоступно'
              : homeworkLoading
                ? 'Загружаем ДЗ…'
                : !homeworkLoaded
                  ? 'ДЗ недоступно'
                  : homeworks.length
                    ? 'Домашнее задание'
                    : 'ДЗ нет'
          }}</strong><small v-if="!homeworkExpanded && homeworks.length">{{
            homeworks.map((item) => item.title).join(' · ')
          }}</small></span><MobileIcon
            v-if="homeworks.length"
            class="headman-home__next"
            name="back"
          />
        </button><template v-if="homeworkExpanded">
          <article
            v-for="item in homeworks"
            :key="item.id"
          >
            <h2>{{ item.title }}</h2><p v-if="item.description">
              {{ item.description }}
            </p><a
              v-if="safeHomeworkLink(item.link)"
              :href="safeHomeworkLink(item.link)!"
              target="_blank"
              rel="noopener noreferrer"
            >Материалы</a>
          </article>
        </template><p
          v-if="homeworkError"
          role="alert"
        >
          {{ homeworkError }}
        </p>
      </section>
      <section
        class="headman-home__actions"
        aria-label="Действия с выбранной парой"
        :aria-busy="blockPending"
      >
        <div
          v-if="blockConfirm && canBlock"
          class="headman-home__block-confirm"
          @keydown.esc.stop.prevent="dismissBlockConfirmation"
        >
          <p>Заблокировать геоотметки на этой паре? Ручной учёт останется доступен.</p>
          <div class="headman-home__actions">
            <button
              ref="blockCancel"
              type="button"
              :disabled="blockPending"
              @click="dismissBlockConfirmation"
            >
              Отмена
            </button>
            <button
              ref="blockSubmit"
              type="button"
              :disabled="blockPending"
              aria-label="Подтвердить блокировку геоотметок"
              @click="changeGeoBlock(true)"
            >
              {{ blockPending ? 'Блокируем…' : 'Заблокировать' }}
            </button>
          </div>
        </div>
        <button
          v-else-if="canBlock"
          ref="blockTrigger"
          class="headman-home__geo-block"
          type="button"
          :disabled="blockPending"
          :aria-label="selectedLesson.blockedByHeadman ? 'Разблокировать геоотметки' : 'Заблокировать геоотметки'"
          @click="selectedLesson.blockedByHeadman ? changeGeoBlock(false) : openBlockConfirmation()"
        >
          <svg class="mobile-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="3.5" y="7" width="9" height="6.5" rx="1.5" />
            <path :d="selectedLesson.blockedByHeadman ? 'M5.5 7V5a2.5 2.5 0 0 1 5 0' : 'M5.5 7V5a2.5 2.5 0 0 1 5 0v2'" />
          </svg>
          {{ blockPending ? 'Разблокируем…' : selectedLesson.blockedByHeadman ? 'Разблокировать геоотметки' : 'Заблокировать геоотметки' }}
        </button>
        <p v-if="blockError" class="headman-home__geo-error" role="alert">{{ blockError }}</p>
        <button
          type="button"
          :disabled="offline || readOnly || !homeworkEnabled"
          @click="openSelectedHomework"
        >
          Выдать ДЗ
        </button><button
          type="button"
          :disabled="offline || !logEnabled"
          @click="emit('openLog', selectedLesson)"
        >
          Журнал
        </button>
      </section>
    </template>
    <button
      v-if="canReturnToToday"
      class="headman-home__return-today"
      type="button"
      @click="returnToToday"
    >
      Сегодня
    </button>
  </main>
</template>
