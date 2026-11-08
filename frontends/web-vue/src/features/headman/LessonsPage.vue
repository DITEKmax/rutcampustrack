<script setup lang="ts">
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  shallowReactive,
  shallowRef,
} from 'vue';
import { useRoute } from 'vue-router';
import { useWebLiveRefresh } from '../../app/live';
import { WebAuthorityChangedError } from '../../app/api';
import { message, webSession } from '../../auth/session';
import {
  useHeadmanFeature,
  today,
  addDays,
  dateLabel,
  weekStart,
  lessonTypes,
  lessonTypeCode,
} from './context';
import {
  LessonManagementState,
  lessonCanRoom,
  lessonCanTransfer,
  type LessonCommand,
} from '../../../../mobile-core/src/features/headman-lesson-management/lesson-management-state';
import type { HeadmanJournalLesson } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import { isOneOffDateWithinSemester } from '../../../../mobile-core/src/features/schedule/headman-schedule-client';
import HeadmanPage from './HeadmanPage.vue';
import WebSelect from '../../shared/WebSelect.vue';
import WebDateField from '../../shared/WebDateField.vue';
import StudentPeriodPager from '../student/StudentPeriodPager.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import './headman-schedule.pcss';
const feature = useHeadmanFeature();
const session = webSession.snapshot.value!;
const state = shallowReactive(
  new LessonManagementState(
    feature.journal,
    feature.schedule,
    { userId: session.userId, sessionId: session.sessionId, groupId: feature.groupId },
    sessionStorage,
    () => undefined,
    () => undefined,
    (command) =>
      command.kind === 'CANCEL' || command.kind === 'RESTORE'
        ? feature.writable('CANCEL_LESSONS')
        : feature.writable(),
  ),
);
const route = useRoute();
const date = ref(
    typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date)
      ? route.query.date
      : today(),
  ),
  error = ref<string | null>(null),
  selected = shallowRef<HeadmanJournalLesson | null>(null);
const task = ref<'ROOM' | 'TRANSFER' | 'CANCEL' | 'RESTORE' | 'ONE_OFF' | null>(null);
const draftNeedsRefresh = ref(false);
const form = reactive({ date: today(), number: 1, assignmentId: 0, room: '', reason: '' });
const targetOccupied = shallowRef<ReadonlySet<number>>(new Set()),
  targetDate = ref(''),
  targetBusy = ref(false);
const geoBusy = ref(false),
  geoUnknown = shallowRef<{ lesson: HeadmanJournalLesson; blocked: boolean } | null>(null);
const assignments = computed(() =>
  state.assignments.filter(
    (assignment) =>
      assignment.id &&
      assignment.subjectId &&
      assignment.teacherId &&
      assignment.lessonType &&
      (!assignment.validFrom || assignment.validFrom <= form.date) &&
      (!assignment.validUntilExclusive || assignment.validUntilExclusive > form.date),
  ),
);
const slot = computed(() => state.catalog?.slots.find((item) => item.lessonNumber === form.number));
const navigationBusy = ref(false);
const locked = computed(() => state.locked || geoBusy.value || !!geoUnknown.value || navigationBusy.value);
const weekLessons = shallowRef<readonly HeadmanJournalLesson[]>([]);
const weekOneOffIds = shallowRef<ReadonlySet<number>>(new Set());
const weekOneOffUnavailable = ref(false);
const weekError = ref<string | null>(null);
const weekLoading = ref(false);
const publishedWeek = ref('');
const weekFrom = computed(() => weekStart(date.value));
const weekDays = computed(() => Array.from({ length: 6 }, (_, index) => addDays(weekFrom.value, index)));
const weekLabel = computed(() => `${dateLabel(weekFrom.value)} — ${dateLabel(addDays(weekFrom.value, 5))}`);
const weekday = new Intl.DateTimeFormat('ru-RU', { weekday: 'long', timeZone: 'UTC' });
const lessonStatus: Readonly<Record<string, string>> = { PLANNED: 'Предстоит', ACTIVE: 'Идёт', CLOSED: 'Завершена', CANCELLED: 'Отменена', TRANSFERRED: 'Перенесена', UNSUPPORTED: 'Статус недоступен' };
function isTransferred(lesson: HeadmanJournalLesson) {
  return lesson.status !== 'CANCELLED' && (lesson.status === 'TRANSFERRED' || !!lesson.transferOperationId);
}
function cardStatus(lesson: HeadmanJournalLesson) {
  return [
    lesson.status === 'CANCELLED' ? 'Отменена' : null,
    isTransferred(lesson) ? 'Перенесена' : null,
    weekOneOffIds.value.has(lesson.id) ? 'Разовая' : null,
    lesson.status === 'ACTIVE' || lesson.status === 'UNSUPPORTED' ? lessonStatus[lesson.status] : null,
  ].filter(Boolean).join(' · ');
}
function lessonsInSlot(day: string, number: number) {
  return weekLessons.value
    .filter((lesson) => lesson.date === day && lesson.lessonNumber === number)
    .sort((left, right) => Number(right.status === 'CANCELLED') - Number(left.status === 'CANCELLED'));
}
function lessonLabel(lesson: HeadmanJournalLesson) {
  return [
    weekday.format(new Date(`${lesson.date}T12:00:00Z`)),
    dateLabel(lesson.date),
    `${lesson.lessonNumber} пара`,
    subjectName(lesson),
    lessonTypes[lessonTypeCode(lesson.lessonType)] ?? 'Тип не указан',
    lesson.room ? `Аудитория ${lesson.room}` : 'Аудитория не указана',
    cardStatus(lesson) || lessonStatus[lesson.status],
  ].join(', ');
}
let weekRevision = 0;
function isCurrentWeek(revision: number, from: string) {
  return revision === weekRevision && from === weekFrom.value;
}
async function readWeekOneOffs() {
  const revision = weekRevision;
  const from = weekFrom.value;
  weekOneOffUnavailable.value = false;
  try {
    const to = addDays(from, 5);
    const items = await feature.schedule.listOneOffLessons(feature.groupId, from, to);
    feature.assertCurrent();
    if (!isCurrentWeek(revision, from)) return;
    weekOneOffIds.value = new Set(items
      .filter(item => item.groupId === feature.groupId && item.date >= from && item.date <= to)
      .map(item => item.physicalLessonId));
  } catch (cause) {
    if (!isCurrentWeek(revision, from) || cause instanceof WebAuthorityChangedError) return;
    weekOneOffIds.value = new Set();
    weekOneOffUnavailable.value = true;
  }
}
async function readWeek() {
  const revision = ++weekRevision;
  const from = weekFrom.value;
  weekLoading.value = true;
  weekError.value = null;
  if (publishedWeek.value !== from) {
    weekLessons.value = [];
    weekOneOffIds.value = new Set();
  }
  void readWeekOneOffs();
  try {
    const to = addDays(from, 5);
    const lessons = await feature.journal.listLessons(feature.groupId, from, to);
    feature.assertCurrent();
    if (!isCurrentWeek(revision, from)) return;
    weekLessons.value = lessons.filter(lesson => lesson.groupId === feature.groupId && lesson.current && lesson.date >= from && lesson.date <= to);
    publishedWeek.value = from;
  } catch (cause) {
    if (isCurrentWeek(revision, from) && !(cause instanceof WebAuthorityChangedError))
      weekError.value = message(cause);
  } finally {
    if (isCurrentWeek(revision, from)) weekLoading.value = false;
  }
}
async function showDate(value: string) {
  if (locked.value || state.loading || task.value) return;
  date.value = value;
  selected.value = null;
  await load();
}
async function chooseWeekLesson(lesson: HeadmanJournalLesson) {
  if (locked.value || state.loading || task.value) return;
  selected.value = null;
  date.value = lesson.date;
  await load();
  // A display-only week response must never become a mutation target.
  const actual = state.lessons.find(item => item.id === lesson.id && item.date === date.value);
  if (!state.error && actual) open(actual);
}
async function createWeekSlot(day: string, number: number) {
  if (locked.value || state.loading || task.value || !feature.writable()) return;
  selected.value = null;
  date.value = day;
  await load();
  if (state.error || date.value !== day) return;
  form.number = number;
  await begin('ONE_OFF');
}
function subjectName(lesson: HeadmanJournalLesson) {
  return state.assignments.find(assignment => assignment.subjectId === lesson.subjectId)?.subjectName ?? `Предмет №${lesson.subjectId ?? '—'}`;
}
let read = 0;
async function load() {
  if (navigationBusy.value) return;
  navigationBusy.value = true;
  error.value = null;
  try {
    await feature.initialize();
    if (!feature.allowed('CANCEL_LESSONS')) throw new Error('Нет права управления парами.');
    await state.load(date.value);
    feature.assertCurrent();
    void readWeek();
    if (selected.value)
      selected.value = state.lessons.find((lesson) => lesson.id === selected.value?.id) ?? null;
    else if (!task.value && route.query.lesson)
      selected.value =
        state.lessons.find((lesson) => lesson.id === Number(route.query.lesson)) ?? null;
  } catch (cause) {
    error.value = message(cause);
  } finally {
    navigationBusy.value = false;
  }
}
function open(lesson: HeadmanJournalLesson) {
  if (!locked.value) {
    selected.value = lesson;
    task.value = null;
    error.value = null;
  }
}
async function begin(kind: NonNullable<typeof task.value>) {
  if (
    locked.value ||
    (kind === 'CANCEL' || kind === 'RESTORE'
      ? !feature.writable('CANCEL_LESSONS')
      : !feature.writable())
  )
    return;
  draftNeedsRefresh.value = false;
  task.value = kind;
  form.date = kind === 'TRANSFER' ? addDays(today(), 1) : date.value;
  form.room = selected.value?.room ?? '';
  form.reason = '';
  form.assignmentId = assignments.value[0]?.id ?? 0;
  if (kind === 'TRANSFER' || kind === 'ONE_OFF') await loadTarget();
}
async function loadTarget() {
  const kind = task.value;
  if (kind !== 'TRANSFER' && kind !== 'ONE_OFF') return;
  const revision = ++read;
  targetBusy.value = true;
  targetDate.value = '';
  try {
    const requestedDate = form.date;
    const occupied = await state.targetOccupiedSlots(requestedDate, kind === 'TRANSFER' ? 'transfer' : 'oneoff');
    feature.assertCurrent();
    if (revision !== read || requestedDate !== form.date) return;
    targetOccupied.value = occupied;
    targetDate.value = requestedDate;
  } catch (cause) {
    if (revision === read) error.value = message(cause);
  } finally {
    if (revision === read) targetBusy.value = false;
  }
}
function unavailable(number: number) {
  return targetDate.value !== form.date || targetOccupied.value.has(number);
}
function closeTask() {
  task.value = null;
  draftNeedsRefresh.value = false;
}
function canStillEditSource(kind: NonNullable<typeof task.value>, lesson: HeadmanJournalLesson) {
  switch (kind) {
    case 'ROOM': return feature.writable() && lessonCanRoom(lesson, Date.now());
    case 'TRANSFER': return feature.writable() && lessonCanTransfer(lesson, today());
    case 'CANCEL': return feature.writable('CANCEL_LESSONS') && lesson.status !== 'CANCELLED';
    case 'RESTORE': return feature.writable('CANCEL_LESSONS') && lesson.status === 'CANCELLED';
    case 'ONE_OFF': return feature.writable();
  }
}
async function refreshRejectedDraft() {
  const kind = task.value;
  const source = selected.value;
  if (!kind || !draftNeedsRefresh.value || locked.value) return;
  navigationBusy.value = true;
  error.value = null;
  try {
    if (kind !== 'ONE_OFF') {
      const lessons = source ? await state.targetLessons(source.date) : [];
      feature.assertCurrent();
      const fresh = source && lessons.find(lesson =>
        lesson.id === source.id && lesson.occurrenceId === source.occurrenceId &&
        lesson.current && lesson.groupId === feature.groupId && lesson.date === source.date,
      );
      if (!fresh || !canStillEditSource(kind, fresh)) {
        selected.value = fresh || null;
        closeTask();
        error.value = 'Действие для этой пары больше недоступно. Расписание обновлено.';
        void readWeek();
        return;
      }
      selected.value = fresh;
    }
    if (kind === 'TRANSFER' || kind === 'ONE_OFF') {
      await loadTarget();
      feature.assertCurrent();
      if (targetDate.value !== form.date) return;
    }
    draftNeedsRefresh.value = false;
    void readWeek();
  } catch (cause) {
    if (!(cause instanceof WebAuthorityChangedError)) {
      error.value = 'Не удалось обновить данные пары. ' + message(cause);
    }
  } finally {
    navigationBusy.value = false;
  }
}
async function submit() {
  const lesson = selected.value;
  if (!task.value || locked.value || draftNeedsRefresh.value || state.denied) return;
  error.value = null;
  try {
    let command: LessonCommand;
    if (task.value === 'CANCEL') {
      if (!lesson || !feature.writable('CANCEL_LESSONS')) return;
      command = { kind: 'CANCEL', target: lesson, reason: form.reason };
    } else if (task.value === 'RESTORE') {
      if (!lesson || !feature.writable('CANCEL_LESSONS')) return;
      command = { kind: 'RESTORE', target: lesson };
    } else if (task.value === 'ROOM') {
      if (
        !lesson ||
        !lessonCanRoom(lesson, Date.now()) ||
        !lesson.occurrenceRevision ||
        !feature.writable()
      )
        throw new Error('Аудитория доступна для изменения только до начала запланированной пары.');
      command = {
        kind: 'ROOM',
        target: lesson,
        key: crypto.randomUUID(),
        input: { room: form.room.trim() || null, expectedRevision: lesson.occurrenceRevision },
      };
    } else {
      if (
        !feature.writable() ||
        !slot.value ||
        targetBusy.value ||
        unavailable(form.number) ||
        !isOneOffDateWithinSemester(
          form.date,
          task.value === 'TRANSFER' ? addDays(today(), 1) : today(),
          state.semester?.dateFrom,
          state.semester?.dateTo,
        )
      )
        throw new Error('Выбери свободную пару в активном семестре после проверки расписания.');
      if (task.value === 'TRANSFER') {
        if (
          !lesson ||
          !lessonCanTransfer(lesson, today()) ||
          !lesson.occurrenceRevision ||
          new Date(`${form.date}T12:00:00Z`).getUTCDay() === 0
        )
          throw new Error('Перенос доступен для будущей пары на учебный день.');
        const key = crypto.randomUUID();
        command = {
          kind: 'TRANSFER',
          target: lesson,
          key,
          receipt: null,
          input: {
            targetDate: form.date,
            targetLessonNumber: slot.value.lessonNumber,
            targetStartTime: slot.value.startTime,
            targetEndTime: slot.value.endTime,
            targetRoom: form.room.trim() || null,
            expectedRevision: lesson.occurrenceRevision,
            requestKey: key,
          },
        };
      } else {
        const assignment = assignments.value.find((item) => item.id === form.assignmentId);
        if (!assignment?.id || !assignment.subjectId)
          throw new Error('Выбери действующее назначение преподавателя.');
        command = {
          kind: 'ONE_OFF',
          key: crypto.randomUUID(),
          input: {
            groupId: feature.groupId,
            subjectId: assignment.subjectId,
            assignmentId: assignment.id,
            date: form.date,
            lessonNumber: slot.value.lessonNumber,
            startTime: slot.value.startTime,
            endTime: slot.value.endTime,
            ...(form.room.trim() ? { classroom: form.room.trim() } : {}),
          },
        };
      }
    }
    await state.submit(command);
    feature.assertCurrent();
    if (state.intent) return;
    if (state.error) {
      if (state.denied) {
        selected.value = null;
        closeTask();
      } else {
        draftNeedsRefresh.value = true;
        await refreshRejectedDraft();
      }
      return;
    }
    if (command.kind === 'ONE_OFF' && state.confirmedOneOff) {
      date.value = state.confirmedOneOff.date;
    }
    closeTask();
    await load();
  } catch (cause) {
    error.value = message(cause);
  }
}
async function recover() {
  const command = state.intent?.command;
  await state.recover();
  feature.assertCurrent();
  if (!state.intent && !state.error) {
    if (command?.kind === 'ONE_OFF' && state.confirmedOneOff) {
      date.value = state.confirmedOneOff.date;
    }
    task.value = null;
    await load();
  }
}
async function block(lesson: HeadmanJournalLesson) {
  if (!feature.writable() || locked.value || typeof lesson.blockedByHeadman !== 'boolean') return;
  geoUnknown.value = { lesson, blocked: !lesson.blockedByHeadman };
  geoBusy.value = true;
  try {
    await feature.journal.setHeadmanBlock(lesson.id, !lesson.blockedByHeadman);
    feature.assertCurrent();
    geoUnknown.value = null;
    await load();
  } catch (cause) {
    error.value = `${message(cause)}. Результат блокировки проверяется чтением пары.`;
    await checkBlock();
  } finally {
    geoBusy.value = false;
  }
}
async function checkBlock() {
  const pending = geoUnknown.value;
  if (!pending) return;
  try {
    const result = await feature.journal.listLessons(
      feature.groupId,
      pending.lesson.date,
      pending.lesson.date,
    );
    feature.assertCurrent();
    const actual = result.find((lesson) => lesson.id === pending.lesson.id);
    if (actual && actual.blockedByHeadman === pending.blocked) {
      geoUnknown.value = null;
      error.value = null;
      await load();
    } else error.value = 'Блокировка не подтверждена. Повторная запись не отправляется.';
  } catch (cause) {
    error.value = message(cause);
  }
}
onMounted(load);
onBeforeUnmount(() => { weekRevision++; state.dispose(); });
useWebLiveRefresh(load, { blocked: () => locked.value || state.loading || !!task.value });
</script>
<template>
  <HeadmanPage
    title="Управление парами"
    :loading="state.loading"
    :error="error ?? state.error"
    @reload="draftNeedsRefresh ? refreshRejectedDraft() : load()"
    ><template #actions
      ><button
        v-if="feature.isHeadman"
        class="web-button web-button-primary"
        :disabled="locked || !feature.writable()"
        @click="
          selected = null;
          begin('ONE_OFF');
        "
        >Добавить разовую пару</button
      ></template
    ><div class="headman-lesson-period">
      <div class="headman-lesson-context"><span>Группа <strong>{{ feature.groupId }}</strong></span><span v-if="state.policy">Неделя <strong>{{ state.policy.label }}</strong></span></div>
      <StudentPeriodPager :label="weekLabel" unit="неделя" :busy="locked || state.loading || !!task" @step="showDate(addDays(date, $event * 7))" @reset="showDate(today())">
        <WebDateField :model-value="date" label="Дата управления парами" :trigger-label="weekLabel" :disabled="locked || state.loading || !!task" required @update:model-value="showDate" />
      </StudentPeriodPager>
    </div>
    <p
      v-if="state.notice"
      role="status"
      >{{ state.notice }}</p
    ><p
      v-if="state.intent"
      class="web-notice"
      >Сохранено исходное действие пары.
      <button
        class="web-button"
        :disabled="state.busy"
        @click="recover"
        >Проверить и восстановить результат</button
      ></p
    ><p
      v-if="geoUnknown"
      class="web-notice"
      >Результат блокировки неизвестен.
      <button
        class="web-button"
        :disabled="geoBusy"
        @click="checkBlock"
        >Проверить блокировку</button
      ></p
    >
    <p v-if="weekError" class="web-error" role="alert">{{ weekError }} <button type="button" class="web-button" :disabled="weekLoading" @click="readWeek">Повторить</button></p>
    <p
      v-else-if="weekOneOffUnavailable"
      class="web-notice"
      role="status"
    >
      Пометки разовых пар недоступны. <button
        type="button"
        class="web-button"
        @click="readWeekOneOffs"
      >
        Обновить пометки
      </button>
    </p>
    <div
      class="headman-timetable-scroll"
      role="region"
      aria-label="Пары на неделю"
      tabindex="0" :aria-busy="weekLoading"
    >
      <table class="headman-timetable headman-timetable-management">
        <thead><tr><th><span class="web-sr-only">Пара и время</span></th><th v-for="day in weekDays" :key="day" scope="col"><strong>{{ weekday.format(new Date(`${day}T12:00:00Z`)) }}</strong><small>{{ dateLabel(day) }}</small></th></tr></thead>
        <tbody><template v-for="item in state.catalog?.slots" :key="item.lessonNumber"><tr>
          <th scope="row">{{ item.lessonNumber }} пара<small>{{ item.startTime.slice(0, 5) }} — {{ item.endTime.slice(0, 5) }}</small></th>
          <td v-for="day in weekDays" :key="day"><div class="headman-timetable-cell">
            <button v-for="lesson in lessonsInSlot(day, item.lessonNumber)" :key="lesson.id" type="button" class="headman-timetable-lesson" :class="{ 'is-selected': selected?.id === lesson.id, 'is-current': lesson.status === 'ACTIVE', 'is-cancelled': lesson.status === 'CANCELLED', 'is-transferred': isTransferred(lesson), 'is-one-off': weekOneOffIds.has(lesson.id) }" :aria-pressed="selected?.id === lesson.id" :aria-label="lessonLabel(lesson)" :title="lessonLabel(lesson)" :disabled="locked || state.loading || !!task" @click="chooseWeekLesson(lesson)">
              <strong>{{ subjectName(lesson) }}</strong><span>{{ lessonTypes[lessonTypeCode(lesson.lessonType)] ?? 'Тип не указан' }}<template v-if="lesson.room"> · {{ lesson.room }}</template></span><small v-if="cardStatus(lesson)">{{ cardStatus(lesson) }}</small>
            </button>
            <button v-if="feature.isHeadman && publishedWeek === weekFrom && !weekLessons.some(value => value.date === day && value.lessonNumber === item.lessonNumber)" type="button" class="headman-timetable-free" :disabled="locked || state.loading || weekLoading || !!task || !feature.writable()" :aria-label="`Добавить пару: ${dateLabel(day)}, ${item.lessonNumber} пара`" @click="createWeekSlot(day, item.lessonNumber)"><MobileIcon name="plus" /></button>
          </div></td>
        </tr><tr v-if="selected && !task && selected.lessonNumber === item.lessonNumber"><td colspan="7" class="headman-timetable-reveal">    <section
      v-if="selected && !task"
      class="headman-panel web-stack headman-lesson-detail"
      ><div class="headman-lesson-detail-heading">
           <h2>Пара №{{ selected.lessonNumber }} · {{ dateLabel(selected.date) }}</h2><button
             type="button"
             class="web-button web-icon-button"
             aria-label="Закрыть действия пары" :disabled="locked" @click="selected = null"
           >
             <MobileIcon name="close" />
           </button>
         </div><p>
           {{ subjectName(selected) }} · {{ selected.room ?? 'Аудитория не указана' }}<template v-if="cardStatus(selected)">
             · {{ cardStatus(selected) }}
           </template>
         </p>
      <p v-if="typeof selected.blockedByHeadman === 'boolean'"
        >Взаимодействие студентов {{ selected.blockedByHeadman ? 'заблокировано' : 'разрешено' }}.
        Блокировка оставляет только заявку на уважительную причину.</p
      ><div class="headman-actions"
        ><button
          v-if="selected.status !== 'CANCELLED'"
          class="web-button"
          :disabled="locked || !feature.writable('CANCEL_LESSONS')"
          @click="begin('CANCEL')"
          >Отменить пару</button
        ><button
          v-else
          class="web-button"
          :disabled="locked || !feature.writable('CANCEL_LESSONS')"
          @click="begin('RESTORE')"
          >Восстановить</button
        ><button
          v-if="feature.isHeadman"
          class="web-button"
          :disabled="locked || !feature.writable() || !lessonCanTransfer(selected, today())"
          @click="begin('TRANSFER')"
          >Перенести</button
        ><button
          v-if="feature.isHeadman"
          class="web-button"
          :disabled="locked || !feature.writable() || !lessonCanRoom(selected, Date.now())"
          @click="begin('ROOM')"
          >Изменить аудиторию</button
        ><button
          v-if="feature.isHeadman && typeof selected.blockedByHeadman === 'boolean'"
          class="web-button"
          :disabled="
            locked || !feature.writable() || !['PLANNED', 'ACTIVE'].includes(selected.status)
          "
          @click="block(selected)"
          >{{
            selected.blockedByHeadman ? 'Разрешить взаимодействие' : 'Заблокировать взаимодействие'
          }}</button
        ><RouterLink
          class="web-button"
          :to="{ name: 'headman-attendance', query: { lesson: selected.id, date: selected.date } }"
          >Журнал и история</RouterLink
        ></div
      ></section
    ></td></tr></template></tbody>
      </table>
    </div>
    <form
      v-if="task"
      class="headman-panel web-stack headman-timetable-editor"
      @submit.prevent="submit"
    >
      <h2>{{
        {
          CANCEL: 'Отменить пару',
          RESTORE: 'Восстановить пару',
          ROOM: 'Изменить аудиторию',
          TRANSFER: 'Перенести пару',
          ONE_OFF: 'Разовая пара',
        }[task]
      }}</h2>
      <p
        v-if="selected"
        class="web-caption"
      >
        {{ dateLabel(selected.date) }} · пара {{ selected.lessonNumber }} · {{ subjectName(selected) }}
      </p>
      <p
        v-else
        class="web-caption"
      >
        {{ dateLabel(form.date) }} · {{ state.semester?.name }}
      </p>
      <p
        v-if="task === 'CANCEL'"
        class="web-notice"
        >Пара будет отменена. Связанные отметки и заявки изменятся по правилам отмены; история
        действий сохранится. Привязанное ДЗ перейдёт в архив.</p
      ><p
        v-if="task === 'RESTORE'"
        class="web-notice"
        >Пара снова станет доступна в расписании. Восстановление отметок и заявок определяется
        сервером.</p
      ><fieldset :disabled="locked"
        ><legend>{{ task === 'CANCEL' || task === 'RESTORE' ? 'Подтверждение' : 'Параметры пары' }}</legend><label
          v-if="task === 'CANCEL'"
          class="web-field"
          >Причина<textarea
            v-model="form.reason"
            maxlength="300"
            required
          /></label
        ><template v-if="task === 'TRANSFER' || task === 'ONE_OFF'"
          ><WebDateField class="web-field" v-model="form.date" label="Дата пары" :min="task === 'TRANSFER' ? addDays(today(), 1) : today()" :max="state.semester?.dateTo ?? undefined" :disabled="locked" required @change="loadTarget"  /><WebSelect class="web-field" v-model="form.number" label="Свободная пара" :options="(state.catalog?.slots ?? []).map(item => ({ value: item.lessonNumber, label: `${item.lessonNumber} · ${item.startTime.slice(0,5)} — ${item.endTime.slice(0,5)}${unavailable(item.lessonNumber) ? ' · занято' : ''}`, disabled: unavailable(item.lessonNumber) }))" :disabled="locked || targetBusy" required  /><WebSelect v-if="task === 'ONE_OFF'"
            class="web-field" v-model="form.assignmentId" label="Предмет, тип и преподаватель" :options="assignments.flatMap(assignment => assignment.id ? [{ value: assignment.id, label: `${assignment.subjectName} · ${lessonTypes[assignment.lessonType ?? ''] ?? 'Тип не указан'} · ${assignment.teacherName}` }] : [])" :disabled="locked" required  /></template
        ><label
          v-if="['ROOM', 'TRANSFER', 'ONE_OFF'].includes(task)"
          class="web-field"
          >Аудитория<input
            v-model="form.room"
            maxlength="64" /></label></fieldset
      ><div class="headman-actions"
        ><button
        class="web-button"
        :class="task === 'CANCEL' ? 'web-button-danger' : 'web-button-primary'"
          :disabled="locked || targetBusy || draftNeedsRefresh || state.denied || ((task === 'TRANSFER' || task === 'ONE_OFF') && unavailable(form.number))"
          >Подтвердить</button
        ><button
          type="button"
          class="web-button"
          :disabled="locked"
          @click="closeTask"
          >Отмена</button
        ></div
      ></form
    >
  </HeadmanPage>
</template>
