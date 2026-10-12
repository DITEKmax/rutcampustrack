<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowReactive, shallowRef, watch } from 'vue';
import MobileIcon from '../../shared/components/MobileIcon.vue';
import { academicWeekOrdinal } from '../../domain/academic-week';
import StudentWarningBlock from '../../shared/components/StudentWarningBlock.vue';
import HeadmanLessonActionsScreen from '../headman-journal/HeadmanLessonActionsScreen.vue';
import type { HeadmanLessonActionsApi } from '../headman-journal/headman-journal-actions-client';
import type {
  HeadmanJournalApi,
  HeadmanJournalLesson,
} from '../headman-journal/headman-journal-client';
import type { HeadmanScheduleApi } from '../schedule/headman-schedule-client';
import type { HeadmanAssistantPermission } from '../headman-group/headman-group-client';
import {
  LessonManagementState,
  datePlus,
  lessonCanRoom,
  lessonCanTransfer,
  transferEffectiveRoom,
  type ManagementOwner,
  type LessonCommand,
} from './lesson-management-state';
import './lesson-management-screen.pcss';
const props = withDefaults(
  defineProps<{
    journalApi: HeadmanJournalApi | null;
    scheduleApi: HeadmanScheduleApi | null;
    actionsApi?: HeadmanLessonActionsApi | null;
    groupId: number | null;
    owner: { userId: string; sessionId: string } | null;
    assistantPermissions?: readonly HeadmanAssistantPermission[] | null;
    offline?: boolean;
    readOnly?: boolean;
    initialDate?: string;
  }>(),
  {
    actionsApi: null,
    assistantPermissions: null,
    offline: false,
    readOnly: false,
    initialDate: '',
  },
);
export type ManagementView =
  | 'agenda'
  | 'detail'
  | 'transfer'
  | 'cancel'
  | 'room'
  | 'oneoff'
  | 'log';
const emit = defineEmits<{ back: []; error: [unknown]; 'view-change': [ManagementView] }>();
const clock = ref(Date.now()),
  clockTimer = setInterval(() => {
    clock.value = Date.now();
  }, 30000);
const today = () =>
  new Date(clock.value).toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' });
const date = ref(props.initialDate || today()),
  view = ref<ManagementView>('agenda'),
  addMode = ref(false),
  showCancelled = ref(false);
const model = shallowRef<LessonManagementState | null>(null);
const state = computed(() => model.value);
const selected = shallowRef<HeadmanJournalLesson | null>(null),
  targetDate = ref(''),
  targetNumber = ref<number | null>(null),
  room = ref(''),
  reason = ref(''),
  assignmentId = ref<number | null>(null);
const targetLessons = shallowRef<readonly HeadmanJournalLesson[]>([]),
  targetLoadedDate = ref(''),
  targetError = ref<string | null>(null),
  targetBusy = ref(false),
  calendarOpen = ref(false),
  month = ref('');
const formError = ref<string | null>(null),
  confirmingRestore = ref(false);
let targetRun = 0,
  disposed = false;
const mutable = computed(
  () => !props.offline && !props.readOnly && !state.value?.denied && !!state.value?.semester?.id,
);
const headmanMutable = computed(() => mutable.value && props.assistantPermissions === null);
const cancelMutable = computed(
  () =>
    mutable.value &&
    (props.assistantPermissions === null || props.assistantPermissions.includes('CANCEL_LESSONS')),
);
const controlsLocked = computed(() => !mutable.value || !!state.value?.locked);
const title = computed(
  () =>
    ({
      agenda: 'Управление парами',
      detail: 'Пара',
      transfer: 'Перенос пары',
      cancel: 'Отмена пары',
      room: 'Изменить аудиторию',
      oneoff: 'Добавить пару',
      log: 'Журнал действий',
    })[view.value],
);
const subject = (lesson: HeadmanJournalLesson) =>
  state.value?.assignments.find((a) => a.subjectId === lesson.subjectId)?.subjectName || 'Предмет';
const typeName = (type: string | null | undefined) =>
  type === 'LECTURE'
    ? 'Лекция'
    : type === 'PRACTICE'
      ? 'Практика'
      : type === 'LAB'
        ? 'Лабораторная'
        : 'Тип не указан';
const dayLabel = (value: string) =>
  new Date(value + 'T00:00:00Z').toLocaleDateString('ru-RU', {
    timeZone: 'UTC',
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
const time = (value: string | null) => value?.slice(0, 5) ?? '—';
const weekdays = computed(() =>
  state.value?.policy
    ? Array.from({ length: 7 }, (_, index) => {
        const value = datePlus(state.value!.policy!.weekStart, index);
        return {
          value,
          label: new Date(value + 'T00:00:00Z').toLocaleDateString('ru-RU', {
            timeZone: 'UTC',
            weekday: 'short',
          }),
          number: new Date(value + 'T00:00:00Z').getUTCDate(),
        };
      })
    : [],
);
const weekLabel = computed(() => {
  const p = state.value?.policy;
  if (!p) return 'Чётность недоступна';
  return (
    new Date(p.weekStart + 'T00:00:00Z').toLocaleDateString('ru-RU', {
      timeZone: 'UTC',
      day: 'numeric',
    }) +
    '–' +
    new Date(p.weekEnd + 'T00:00:00Z').toLocaleDateString('ru-RU', {
      timeZone: 'UTC',
      day: 'numeric',
      month: 'long',
    })
  );
});
const weekHeading = computed(() => {
  const policy = state.value?.policy,
    semester = state.value?.semester;
  if (!policy) return 'Чётность недоступна';
  const ordinal = academicWeekOrdinal(
    date.value,
    semester?.dateFrom ?? null,
    semester?.dateTo ?? null,
  );
  return 'неделя ' + policy.parityNumber + (ordinal ? ' · №' + ordinal : '');
});
const agenda = computed(() =>
  (state.value?.catalog?.slots ?? []).map((slot) => ({
    slot,
    lessons: (state.value?.lessons ?? []).filter(
      (l) =>
        l.lessonNumber === slot.lessonNumber && (showCancelled.value || l.status !== 'CANCELLED'),
    ),
  })),
);
const sourceCanTransfer = computed(
  () => !!selected.value && lessonCanTransfer(selected.value, today()),
);
const sourceCanRoom = computed(
  () => !!selected.value && lessonCanRoom(selected.value, clock.value),
);
const assignment = computed(
  () => state.value?.assignments.find((a) => a.id === assignmentId.value) ?? null,
);
const subjects = computed(() => [
  ...new Map((state.value?.assignments ?? []).map((a) => [a.subjectId, a])).values(),
]);
const subjectId = ref<number | null>(null);
const assignmentOptions = computed(() =>
  (state.value?.assignments ?? []).filter(
    (a) =>
      a.subjectId === subjectId.value &&
      !!a.teacherId &&
      !!a.lessonType &&
      (!a.validFrom || a.validFrom <= targetDate.value) &&
      (!a.validUntilExclusive || targetDate.value < a.validUntilExclusive),
  ),
);
const targetSlots = computed(() =>
  (state.value?.catalog?.slots ?? []).map((slot) => ({
    ...slot,
    unavailable:
      targetLoadedDate.value !== targetDate.value ||
      targetLessons.value.some(
        (l) => l.current && l.lessonNumber === slot.lessonNumber && l.status !== 'TRANSFERRED',
      ) ||
      (view.value === 'transfer' &&
        selected.value?.date === targetDate.value &&
        selected.value?.lessonNumber === slot.lessonNumber),
  })),
);
const targetSlot = computed(
  () =>
    targetSlots.value.find((s) => s.lessonNumber === targetNumber.value && !s.unavailable) ?? null,
);
const effectiveTransferRoom = computed(() =>
  transferEffectiveRoom(room.value, selected.value?.room ?? null),
);
const targetDateValid = computed(() => targetDayAllowed(targetDate.value));
const calendarDays = computed(() => {
  const first = month.value + '-01';
  if (!/^\d{4}-\d{2}$/.test(month.value)) return [];
  const offset = (new Date(first + 'T00:00:00Z').getUTCDay() + 6) % 7;
  return Array.from({ length: 42 }, (_, i) => datePlus(first, i - offset));
});
const monthLabel = computed(() =>
  month.value
    ? new Date(month.value + '-01T00:00:00Z').toLocaleDateString('ru-RU', {
        timeZone: 'UTC',
        month: 'long',
        year: 'numeric',
      })
    : '',
);
function shiftMonth(delta: number): void {
  const parsed = new Date(month.value + '-01T00:00:00Z');
  parsed.setUTCMonth(parsed.getUTCMonth() + delta);
  month.value = parsed.toISOString().slice(0, 7);
}
function targetDayAllowed(value: string): boolean {
  const sem = state.value?.semester;
  return (
    !!sem?.dateFrom &&
    !!sem.dateTo &&
    value >= sem.dateFrom &&
    value <= sem.dateTo &&
    value >= (view.value === 'transfer' ? datePlus(today(), 1) : today()) &&
    (view.value !== 'transfer' || new Date(value + 'T00:00:00Z').getUTCDay() !== 0)
  );
}
function openLesson(lesson: HeadmanJournalLesson): void {
  if (state.value?.locked) return;
  selected.value = Object.freeze({ ...lesson });
  formError.value = null;
  view.value = 'detail';
}
function openTask(task: ManagementView): void {
  if (
    controlsLocked.value ||
    !selected.value ||
    (task === 'cancel' ? !cancelMutable.value : !headmanMutable.value)
  )
    return;
  if (
    (task === 'transfer' && !sourceCanTransfer.value) ||
    (task === 'room' && !sourceCanRoom.value)
  )
    return;
  room.value = selected.value.room ?? '';
  reason.value = '';
  formError.value = null;
  confirmingRestore.value = false;
  targetDate.value = datePlus(today(), 1);
  targetNumber.value = null;
  month.value = targetDate.value.slice(0, 7);
  calendarOpen.value = false;
  view.value = task;
}
function openOneOff(number: number): void {
  if (controlsLocked.value || !headmanMutable.value) return;
  selected.value = null;
  targetDate.value = date.value;
  targetNumber.value = number;
  subjectId.value = subjects.value[0]?.subjectId ?? null;
  assignmentId.value = assignmentOptions.value[0]?.id ?? null;
  month.value = date.value.slice(0, 7);
  room.value = '';
  view.value = 'oneoff';
  formError.value = null;
}
function back(): boolean {
  if (state.value?.locked) {
    formError.value = 'Результат действия ещё не подтверждён. Проверь исходный запрос.';
    return true;
  }
  if (calendarOpen.value) {
    calendarOpen.value = false;
    return true;
  }
  if (confirmingRestore.value) {
    confirmingRestore.value = false;
    return true;
  }
  if (view.value === 'agenda') return false;
  view.value = view.value === 'detail' || view.value === 'oneoff' ? 'agenda' : 'detail';
  formError.value = null;
  return true;
}
function handleBack(): void {
  if (!back()) emit('back');
}
defineExpose({ back });
async function loadTarget(): Promise<void> {
  const run = ++targetRun,
    current = model.value,
    requested = targetDate.value;
  targetLoadedDate.value = '';
  targetLessons.value = [];
  targetError.value = null;
  targetBusy.value = false;
  if (
    !current ||
    props.offline ||
    !['transfer', 'oneoff'].includes(view.value) ||
    !targetDateValid.value
  )
    return;
  targetBusy.value = true;
  try {
    const lessons = await current.targetLessons(requested);
    if (run !== targetRun || current !== model.value || disposed) return;
    targetLessons.value = lessons;
    targetLoadedDate.value = requested;
  } catch (cause) {
    if (run !== targetRun || current !== model.value || disposed) return;
    targetError.value = 'Не удалось проверить занятость дня.';
    emit('error', cause);
  } finally {
    if (run === targetRun) targetBusy.value = false;
  }
}
async function afterWrite(): Promise<void> {
  if (!model.value || model.value.locked || props.offline || model.value.error) return;
  view.value = 'agenda';
  selected.value = null;
  await model.value.load(date.value);
}
async function submit(): Promise<void> {
  const current = model.value,
    lesson = selected.value;
  if (
    !current ||
    controlsLocked.value ||
    (view.value === 'cancel' || confirmingRestore.value
      ? !cancelMutable.value
      : !headmanMutable.value)
  )
    return;
  formError.value = null;
  let cmd: LessonCommand;
  try {
    if (view.value === 'cancel') {
      if (!lesson || !reason.value.trim() || reason.value.trim().length > 300)
        throw new Error('Укажи причину отмены до 300 символов.');
      cmd = { kind: 'CANCEL', target: lesson, reason: reason.value.trim() };
    } else if (confirmingRestore.value) {
      if (!lesson) return;
      cmd = { kind: 'RESTORE', target: lesson };
    } else if (view.value === 'room') {
      if (!lesson || !lessonCanRoom(lesson, Date.now()) || !lesson.occurrenceRevision)
        throw new Error('Аудиторию можно изменить только до начала текущей запланированной пары.');
      cmd = {
        kind: 'ROOM',
        target: lesson,
        key: crypto.randomUUID(),
        input: { room: room.value.trim() || null, expectedRevision: lesson.occurrenceRevision },
      };
    } else {
      const slot = targetSlot.value;
      if (
        !slot ||
        !targetDateValid.value ||
        targetBusy.value ||
        targetError.value ||
        targetLoadedDate.value !== targetDate.value
      )
        throw new Error('Выбери свободный слот после проверки расписания.');
      if (view.value === 'transfer') {
        if (!lesson || !sourceCanTransfer.value || !lesson.occurrenceRevision)
          throw new Error('Переносить можно только запланированные пары после сегодняшнего дня.');
        const key = crypto.randomUUID();
        cmd = {
          kind: 'TRANSFER',
          target: lesson,
          key,
          receipt: null,
          input: {
            targetDate: targetDate.value,
            targetLessonNumber: slot.lessonNumber,
            targetStartTime: slot.startTime,
            targetEndTime: slot.endTime,
            targetRoom: room.value.trim() || null,
            expectedRevision: lesson.occurrenceRevision,
            requestKey: key,
          },
        };
      } else {
        const a = assignment.value;
        if (!a?.id || !a.subjectId || !assignmentOptions.value.some((option) => option.id === a.id))
          throw new Error('Выбери действующее назначение преподавателя.');
        cmd = {
          kind: 'ONE_OFF',
          key: crypto.randomUUID(),
          input: {
            groupId: current.owner.groupId,
            subjectId: a.subjectId,
            assignmentId: a.id,
            date: targetDate.value,
            lessonNumber: slot.lessonNumber,
            startTime: slot.startTime,
            endTime: slot.endTime,
            ...(room.value.trim() ? { classroom: room.value.trim() } : {}),
          },
        };
      }
    }
    if (room.value.trim().length > 64) throw new Error('Аудитория — до 64 символов.');
    await current.submit(cmd);
    if (current === model.value) {
      confirmingRestore.value = false;
      if (current.intent === null && current.error) {
        await loadTarget();
        const refreshed = await current.targetLessons(lesson?.date ?? date.value);
        if (current !== model.value) return;
        const fresh = refreshed.find((l) => l.id === lesson?.id && l.current);
        if (fresh) selected.value = fresh;
      }
      await afterWrite();
    }
  } catch (cause) {
    formError.value = cause instanceof Error ? cause.message : 'Не удалось сохранить действие.';
  }
}
async function recover(): Promise<void> {
  const current = model.value;
  if (!current || props.offline || props.readOnly) return;
  const cmd = current.intent?.command;
  if (
    cmd?.kind === 'CANCEL' || cmd?.kind === 'RESTORE' ? !cancelMutable.value : !headmanMutable.value
  )
    return;
  await current.recover();
  if (current === model.value) await afterWrite();
}
watch(
  () =>
    [
      props.journalApi,
      props.scheduleApi,
      props.groupId,
      props.owner?.userId,
      props.owner?.sessionId,
      props.offline,
      props.readOnly,
      props.assistantPermissions,
    ] as const,
  () => {
    model.value?.dispose();
    selected.value = null;
    view.value = 'agenda';
    targetRun++;
    targetLoadedDate.value = '';
    if (!props.journalApi || !props.scheduleApi || props.groupId === null || !props.owner) {
      model.value = null;
      return;
    }
    const owner: ManagementOwner = { ...props.owner, groupId: props.groupId };
    model.value = shallowReactive(
      new LessonManagementState(
        props.journalApi,
        props.scheduleApi,
        owner,
        sessionStorage,
        () => undefined,
        (cause) => emit('error', cause),
        (command) =>
          !props.offline &&
          !props.readOnly &&
          props.owner?.userId === owner.userId &&
          props.owner?.sessionId === owner.sessionId &&
          props.groupId === owner.groupId &&
          (command.kind === 'CANCEL' || command.kind === 'RESTORE'
            ? props.assistantPermissions === null ||
              props.assistantPermissions.includes('CANCEL_LESSONS')
            : props.assistantPermissions === null),
      ),
    );
    if (!props.offline) void model.value.load(date.value);
  },
  { immediate: true, flush: 'sync' },
);
watch(
  () => [date.value, props.offline] as const,
  () => {
    targetRun++;
    if (!props.offline && !model.value?.busy) void model.value?.load(date.value);
  },
);
watch(
  () => [targetDate.value, view.value, props.offline] as const,
  () => {
    void loadTarget();
  },
);
watch(assignmentOptions, (options) => {
  if (!options.some((a) => a.id === assignmentId.value))
    assignmentId.value = options[0]?.id ?? null;
});
watch(
  () => [state.value?.intent, state.value?.busy] as const,
  ([intent, busy], previous) => {
    const before = previous?.[0]?.command;
    if (
      before?.kind === 'TRANSFER' &&
      before.receipt?.state === 'PENDING' &&
      !intent &&
      !busy &&
      state.value?.notice
    )
      void afterWrite();
  },
);
watch(
  view,
  (next) => {
    emit('view-change', next);
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  disposed = true;
  clearInterval(clockTimer);
  model.value?.dispose();
  targetRun++;
});
</script>
<template>
  <main
    v-if="view !== 'log'"
    class="lesson-management"
    :data-management-view="view"
    :aria-busy="state?.loading || state?.busy || false"
  >
    <header :class="{ 'lesson-management__child-header': view !== 'agenda' }">
      <button
        v-if="view !== 'agenda'"
        class="lesson-management__back"
        type="button"
        aria-label="Назад"
        @click="handleBack"
      >
        <MobileIcon name="back" />
      </button>
      <h1>{{ title }}</h1>
    </header>
    <StudentWarningBlock
      v-if="offline"
      title="Нет подключения"
      message="Проверка и изменение пар доступны онлайн."
    />
    <StudentWarningBlock
      v-else-if="state?.denied || !state"
      severity="error"
      title="Нет доступа"
      message="Управление парами этой группы недоступно."
    />
    <StudentWarningBlock
      v-else-if="state?.intent || state?.storageBlocked"
      title="Действие требует проверки"
      :message="
        state.error || 'Сохранён исходный запрос. Проверь его результат перед новым действием.'
      "
      action-label="Проверить исходное действие"
      :action-disabled="state.busy || readOnly"
      @action="recover"
    />
    <StudentWarningBlock
      v-else-if="state.error"
      severity="error"
      title="Не удалось загрузить пары"
      :message="state.error"
      action-label="Повторить"
      @action="state.load(date)"
    />
    <p
      v-if="state?.notice"
      role="status"
      class="lesson-management__notice"
    >
      {{ state.notice }}
    </p>
    <StudentWarningBlock
      v-if="formError"
      severity="error"
      title="Проверь действие"
      :message="formError"
    />
    <p
      v-if="state?.loading"
      role="status"
    >
      Загружаем расписание…
    </p>
    <template v-if="state && !state.denied && !state.loading">
      <template v-if="view === 'agenda'">
        <div class="lesson-management__week">
          <button
            type="button"
            aria-label="Предыдущая неделя"
            :disabled="state.locked"
            @click="date = datePlus(date, -7)"
          >
            <MobileIcon name="back" />
          </button><span>{{ weekHeading }}<small>{{ weekLabel }}</small></span><button
            type="button"
            aria-label="Следующая неделя"
            :disabled="state.locked"
            @click="date = datePlus(date, 7)"
          >
            <MobileIcon
              name="back"
              class="lesson-management__next"
            />
          </button>
        </div>
        <div class="lesson-management__switches">
          <button
            type="button"
            role="switch"
            :aria-checked="addMode"
            :disabled="controlsLocked || !headmanMutable"
            @click="addMode = !addMode"
          >
            Добавление пары<span class="lesson-management__switch" />
          </button><button
            type="button"
            role="switch"
            :aria-checked="showCancelled"
            :disabled="state.locked"
            @click="showCancelled = !showCancelled"
          >
            Отменённые пары<span class="lesson-management__switch" />
          </button>
        </div>
        <div
          class="lesson-management__weekdays"
          aria-label="Учебный день"
        >
          <button
            v-for="day in weekdays"
            :key="day.value"
            type="button"
            :aria-pressed="date === day.value"
            :aria-label="dayLabel(day.value)"
            :disabled="state.locked"
            @click="date = day.value"
          >
            {{ day.label }}<strong>{{ day.number }}</strong>
          </button>
        </div>
        <h2>{{ dayLabel(date) }}</h2>
        <p
          v-if="!state.lessons.length && !addMode"
          class="lesson-management__empty"
        >
          На этот день пар пока нет. Включи добавление, чтобы выбрать свободный слот.
        </p>
        <section
          v-for="row in agenda"
          :key="row.slot.lessonNumber"
          class="lesson-management__row"
        >
          <template v-if="row.lessons.length || addMode">
            <div class="lesson-management__time">
              <strong>{{ row.slot.lessonNumber }} пара</strong><span>{{ time(row.slot.startTime) }}–{{ time(row.slot.endTime) }}</span>
            </div>
            <button
              v-for="lesson in row.lessons"
              :key="lesson.id"
              type="button"
              class="lesson-management__card"
              :data-cancelled="lesson.status === 'CANCELLED'"
              :disabled="state.locked"
              :data-lesson-id="lesson.id"
              @click="openLesson(lesson)"
            >
              <small v-if="lesson.status === 'CANCELLED'">Отменена</small><strong>{{ subject(lesson) }}</strong><span><MobileIcon name="room" />{{ lesson.room || 'Аудитория не указана'
              }}<MobileIcon name="lesson" />{{ typeName(lesson.lessonType) }}</span><MobileIcon
                name="back"
                class="lesson-management__chevron"
              />
            </button>
            <button
              v-if="addMode && !state.lessons.some((l) => l.lessonNumber === row.slot.lessonNumber)"
              type="button"
              class="lesson-management__free"
              :data-slot="row.slot.lessonNumber"
              :disabled="controlsLocked || !headmanMutable || date < today()"
              @click="openOneOff(row.slot.lessonNumber)"
            >
              + Добавить пару в свободный слот
            </button>
          </template>
        </section>
      </template>
      <template v-else>
        <section
          v-if="selected"
          class="lesson-management__card lesson-management__summary"
          :data-cancelled="selected.status === 'CANCELLED'"
        >
          <small v-if="selected.status === 'CANCELLED'">Отменена</small><strong>{{ subject(selected) }}</strong><span>{{ dayLabel(selected.date) }} · {{ selected.lessonNumber }} пара ·
            {{ time(selected.startTime) }}–{{ time(selected.endTime) }}</span><span><MobileIcon name="room" />{{ selected.room || 'Аудитория не указана' }}</span><span><MobileIcon name="lesson" />{{ typeName(selected.lessonType) }}</span>
        </section>
        <template v-if="view === 'detail'">
          <h2 v-if="selected?.status !== 'CANCELLED'">
            Действия
          </h2>
          <template v-if="selected?.status === 'CANCELLED'">
            <button
              type="button"
              class="lesson-management__primary"
              :disabled="controlsLocked || !cancelMutable || !selected.current"
              @click="confirmingRestore = true"
            >
              Восстановить пару
            </button>
          </template>
          <template v-else>
            <button
              type="button"
              class="lesson-management__action"
              :disabled="controlsLocked || !headmanMutable || !sourceCanTransfer"
              @click="openTask('transfer')"
            >
              Перенести пару<MobileIcon
                name="back"
                class="lesson-management__next"
              />
            </button><button
              type="button"
              class="lesson-management__action"
              :disabled="controlsLocked || !headmanMutable || !sourceCanRoom"
              @click="openTask('room')"
            >
              Изменить аудиторию<MobileIcon
                name="back"
                class="lesson-management__next"
              />
            </button><p
              v-if="!sourceCanTransfer"
              class="lesson-management__muted"
            >
              Перенос доступен для запланированной пары после сегодняшнего дня.
            </p>
          </template>
          <button
            type="button"
            class="lesson-management__action"
            :disabled="!actionsApi || state.locked"
            @click="view = 'log'"
          >
            Открыть журнал действий<MobileIcon
              name="back"
              class="lesson-management__next"
            />
          </button>
          <button
            v-if="selected && selected.status !== 'CANCELLED'"
            type="button"
            class="lesson-management__action lesson-management__danger-text"
            :disabled="
              controlsLocked ||
                !cancelMutable ||
                !selected?.current ||
                !['PLANNED', 'ACTIVE', 'CLOSED'].includes(selected.status) ||
                selected.transferState === 'PENDING'
            "
            @click="openTask('cancel')"
          >
            Отменить пару<MobileIcon
              name="back"
              class="lesson-management__next"
            />
          </button>
          <section
            v-if="confirmingRestore"
            class="lesson-management__confirmation"
          >
            <h2>Восстановить пару?</h2><p>Пара снова появится в расписании. История отмены сохранится.</p><button
              type="button"
              class="lesson-management__primary"
              :disabled="controlsLocked || !cancelMutable"
              @click="submit"
            >
              Подтвердить восстановление
            </button><button
              type="button"
              class="lesson-management__action"
              :disabled="state.locked"
              @click="confirmingRestore = false"
            >
              Вернуться
            </button>
          </section>
        </template>
        <template v-else-if="view === 'cancel'">
          <label class="lesson-management__field">Причина отмены<textarea
            v-model="reason"
            maxlength="300"
            :disabled="controlsLocked || !cancelMutable"
            placeholder="Почему пара отменяется?"
          /><small>{{ reason.length }} / 300</small></label><footer class="lesson-management__footer">
            <button
              class="lesson-management__primary lesson-management__danger"
              type="button"
              :disabled="controlsLocked || !cancelMutable || !reason.trim()"
              @click="submit"
            >
              Отменить пару
            </button>
          </footer>
        </template>
        <template v-else-if="view === 'transfer' || view === 'oneoff'">
          <label class="lesson-management__field">{{ view === 'transfer' ? 'Новая дата' : 'Дата пары'
          }}<button
            type="button"
            :disabled="controlsLocked || !headmanMutable"
            class="lesson-management__select"
            @click="calendarOpen = !calendarOpen"
          >{{ dayLabel(targetDate) }}<MobileIcon name="chevron-down" /></button></label>
          <section
            v-if="calendarOpen"
            class="lesson-management__calendar"
          >
            <div>
              <button
                aria-label="Предыдущий месяц"
                type="button"
                @click="shiftMonth(-1)"
              >
                <MobileIcon name="back" />
              </button><strong>{{ monthLabel }}</strong><button
                aria-label="Следующий месяц"
                type="button"
                @click="shiftMonth(1)"
              >
                <MobileIcon
                  name="back"
                  class="lesson-management__next"
                />
              </button>
            </div><div class="lesson-management__calendar-grid">
              <span
                v-for="name in ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']"
                :key="name"
              >{{ name }}</span><button
                v-for="day in calendarDays"
                :key="day"
                type="button"
                :aria-pressed="targetDate === day"
                :data-outside="day.slice(0, 7) !== month"
                :disabled="controlsLocked || !targetDayAllowed(day)"
                @click="
                  targetDate = day;
                  calendarOpen = false;
                "
              >
                {{ Number(day.slice(8)) }}
              </button>
            </div>
          </section>
          <label class="lesson-management__field">Номер пары<select
            v-model.number="targetNumber"
            :disabled="controlsLocked || targetBusy || !targetDateValid"
          ><option :value="null">Выбери свободный слот</option><option
            v-for="slot in targetSlots"
            :key="slot.lessonNumber"
            :value="slot.lessonNumber"
            :disabled="slot.unavailable"
          >{{ slot.lessonNumber }} пара · {{ time(slot.startTime) }}–{{ time(slot.endTime)
          }}{{ slot.unavailable ? ' · занято' : '' }}</option></select></label>
          <StudentWarningBlock
            v-if="targetError"
            title="Занятость не проверена"
            :message="targetError"
            action-label="Повторить"
            @action="loadTarget"
          /><p
            v-if="targetBusy"
            role="status"
          >
            Проверяем свободные слоты…
          </p><p
            v-if="!targetDateValid"
            role="status"
          >
            Выбери допустимую дату текущего семестра.
          </p>
          <template v-if="view === 'oneoff'">
            <label class="lesson-management__field">Предмет<select
              v-model.number="subjectId"
              :disabled="controlsLocked"
            ><option
              v-for="item in subjects"
              :key="item.subjectId"
              :value="item.subjectId"
            >{{ item.subjectName }}</option></select></label><label class="lesson-management__field">Тип занятия<select
              v-model.number="assignmentId"
              :disabled="controlsLocked"
            ><option
              v-for="item in assignmentOptions"
              :key="item.id"
              :value="item.id"
            >{{ typeName(item.lessonType) }}</option></select></label><div class="lesson-management__field">
              Преподаватель<strong>{{
                assignment?.teacherName || 'Нет действующего назначения'
              }}</strong>
            </div>
          </template>
          <label class="lesson-management__field">{{ view === 'transfer' ? 'Новая аудитория' : 'Аудитория'
          }}<input
            v-model="room"
            maxlength="64"
            :disabled="controlsLocked"
            placeholder="Не указана"
          ></label>
          <p
            v-if="view === 'transfer'"
            class="lesson-management__muted"
          >
            Если поле пустое, сохранится текущая аудитория. Убрать аудиторию можно через «Изменить
            аудиторию».
          </p>
          <section
            v-if="view === 'transfer' && selected"
            class="lesson-management__preview"
          >
            <h2>Проверь изменения</h2><div>
              <small>Было</small><strong>{{ dayLabel(selected.date) }} · {{ selected.lessonNumber }} пара ·
                {{ selected.room || 'Без аудитории' }}</strong>
            </div><span class="lesson-management__arrow">↓</span><div>
              <small>Станет</small><strong>{{ dayLabel(targetDate) }} · {{ targetNumber || '—' }} пара ·
                {{ effectiveTransferRoom || 'Без аудитории' }}</strong>
            </div>
          </section>
          <footer class="lesson-management__footer">
            <button
              type="button"
              class="lesson-management__primary"
              :disabled="
                controlsLocked ||
                  !headmanMutable ||
                  !targetSlot ||
                  !targetDateValid ||
                  targetBusy ||
                  (view === 'oneoff' && !assignment)
              "
              @click="submit"
            >
              {{ view === 'transfer' ? 'Перенести пару' : 'Создать разовую пару' }}
            </button>
          </footer>
        </template>
        <template v-else-if="view === 'room'">
          <p class="lesson-management__muted">
            Изменится аудитория только этой пары. Дата, посещаемость и история сохранятся.
          </p><label class="lesson-management__field">Аудитория<input
            v-model="room"
            maxlength="64"
            :disabled="controlsLocked"
            placeholder="Оставь пустым, чтобы очистить"
          ></label><footer class="lesson-management__footer">
            <button
              type="button"
              class="lesson-management__primary"
              :disabled="controlsLocked || !headmanMutable || !sourceCanRoom"
              @click="submit"
            >
              Сохранить аудиторию
            </button>
          </footer>
        </template>
        <HeadmanLessonActionsScreen
          v-else-if="view === 'log' && selected"
          :api="actionsApi"
          :lesson="selected"
          :subject-name="subject(selected)"
          :show-header="false"
          :offline="offline"
          @error="emit('error', $event)"
        />
      </template>
    </template>
  </main>
  <div
    v-if="view === 'log' && selected"
    class="lesson-management"
    data-management-view="log"
  >
    <header class="lesson-management__child-header">
      <button
        type="button"
        class="lesson-management__back"
        aria-label="Назад"
        @click="handleBack"
      >
        <MobileIcon name="back" />
      </button><h1>Журнал действий</h1>
    </header><HeadmanLessonActionsScreen
      :api="actionsApi"
      :lesson="selected"
      :subject-name="subject(selected)"
      :show-header="false"
      :offline="offline"
      @error="emit('error', $event)"
    />
  </div>
</template>
