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
import { message, webSession } from '../../auth/session';
import {
  useHeadmanFeature,
  today,
  addDays,
  dateLabel,
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
const form = reactive({ date: today(), number: 1, assignmentId: 0, room: '', reason: '' });
const target = shallowRef<readonly HeadmanJournalLesson[]>([]),
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
const locked = computed(() => state.locked || geoBusy.value || !!geoUnknown.value);
let read = 0;
async function load() {
  try {
    await feature.initialize();
    if (!feature.allowed('CANCEL_LESSONS')) throw new Error('Нет права управления парами.');
    await state.load(date.value);
    feature.assertCurrent();
    if (selected.value)
      selected.value = state.lessons.find((lesson) => lesson.id === selected.value?.id) ?? null;
    else if (!task.value && route.query.lesson)
      selected.value =
        state.lessons.find((lesson) => lesson.id === Number(route.query.lesson)) ?? null;
  } catch (cause) {
    error.value = message(cause);
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
  task.value = kind;
  form.date = kind === 'TRANSFER' ? addDays(today(), 1) : date.value;
  form.room = selected.value?.room ?? '';
  form.reason = '';
  form.assignmentId = assignments.value[0]?.id ?? 0;
  if (kind === 'TRANSFER' || kind === 'ONE_OFF') await loadTarget();
}
async function loadTarget() {
  const revision = ++read;
  targetBusy.value = true;
  targetDate.value = '';
  try {
    const result = await state.targetLessons(form.date);
    feature.assertCurrent();
    if (revision !== read) return;
    target.value = result;
    targetDate.value = form.date;
  } catch (cause) {
    if (revision === read) error.value = message(cause);
  } finally {
    if (revision === read) targetBusy.value = false;
  }
}
function unavailable(number: number) {
  return (
    targetDate.value !== form.date ||
    target.value.some(
      (lesson) =>
        lesson.current && lesson.lessonNumber === number && lesson.status !== 'TRANSFERRED',
    )
  );
}
async function submit() {
  const lesson = selected.value;
  if (!task.value || locked.value) return;
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
    if (!state.intent) {
      task.value = null;
      await load();
    }
  } catch (cause) {
    error.value = message(cause);
  }
}
async function recover() {
  await state.recover();
  feature.assertCurrent();
  if (!state.intent) {
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
onBeforeUnmount(() => state.dispose());
useWebLiveRefresh(load, { blocked: () => locked.value || state.loading || !!task.value });
</script>
<template>
  <HeadmanPage
    title="Управление парами"
    :loading="state.loading"
    :error="error ?? state.error"
    @reload="load"
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
    ><form
      class="headman-panel headman-toolbar"
      @submit.prevent="load"
      ><label class="web-field"
        >Дата<input
          v-model="date"
          type="date"
          :disabled="locked || !!task"
          required /></label
      ><button
        class="web-button"
        :disabled="locked || !!task"
        >Показать</button
      ><span v-if="state.policy"
        >{{ dateLabel(state.policy.weekStart) }} — {{ dateLabel(state.policy.weekEnd) }} ·
        {{ state.policy.label }}</span
      ></form
    >
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
    <div class="headman-table-scroll"
      ><table class="headman-table"
        ><thead
          ><tr
            ><th>Пара / время</th><th>Предмет</th><th>Аудитория</th><th>Статус</th
            ><th>Действия</th></tr
          ></thead
        ><tbody
          ><template
            v-for="item in state.catalog?.slots"
            :key="item.lessonNumber"
            ><tr
              v-for="lesson in state.lessons.filter(
                (value) => value.lessonNumber === item.lessonNumber,
              )"
              :key="lesson.id"
              ><th scope="row"
                >{{ item.lessonNumber }} · {{ item.startTime.slice(0, 5) }} —
                {{ item.endTime.slice(0, 5) }}</th
              ><td
                >{{
                  state.assignments.find((assignment) => assignment.subjectId === lesson.subjectId)
                    ?.subjectName ?? `Предмет №${lesson.subjectId}`
                }}
                · {{ lessonTypes[lessonTypeCode(lesson.lessonType)] }}</td
              ><td>{{ lesson.room ?? '—' }}</td
              ><td>{{
                {
                  PLANNED: 'Предстоит',
                  ACTIVE: 'Идёт',
                  CLOSED: 'Завершена',
                  CANCELLED: 'Отменена',
                  TRANSFERRED: 'Перенесена',
                  UNSUPPORTED: 'Неизвестно',
                }[lesson.status]
              }}</td
              ><td
                ><button
                  class="web-button"
                  :disabled="locked"
                  @click="open(lesson)"
                  >Действия пары</button
                ></td
              ></tr
            ><tr v-if="!state.lessons.some((lesson) => lesson.lessonNumber === item.lessonNumber)"
              ><th scope="row"
                >{{ item.lessonNumber }} · {{ item.startTime.slice(0, 5) }} —
                {{ item.endTime.slice(0, 5) }}</th
              ><td colspan="3">Свободно</td
              ><td
                ><button
                  v-if="feature.isHeadman"
                  class="web-button"
                  :disabled="locked || !feature.writable()"
                  @click="
                    selected = null;
                    form.number = item.lessonNumber;
                    begin('ONE_OFF');
                  "
                  >Добавить пару</button
                ></td
              ></tr
            ></template
          ></tbody
        ></table
      ></div
    >
    <section
      v-if="selected && !task"
      class="headman-panel web-stack"
      ><h2>Пара №{{ selected.lessonNumber }} · {{ dateLabel(selected.date) }}</h2
      ><p v-if="typeof selected.blockedByHeadman === 'boolean'"
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
    >
    <form
      v-if="task"
      class="headman-panel web-stack"
      @submit.prevent="submit"
      ><h2>{{
        {
          CANCEL: 'Отменить пару',
          RESTORE: 'Восстановить пару',
          ROOM: 'Изменить аудиторию',
          TRANSFER: 'Перенести пару',
          ONE_OFF: 'Разовая пара',
        }[task]
      }}</h2
      ><p
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
        ><label
          v-if="task === 'CANCEL'"
          class="web-field"
          >Причина<textarea
            v-model="form.reason"
            maxlength="300"
            required
          /></label
        ><template v-if="task === 'TRANSFER' || task === 'ONE_OFF'"
          ><label class="web-field"
            >Дата<input
              v-model="form.date"
              type="date"
              :min="task === 'TRANSFER' ? addDays(today(), 1) : today()"
              :max="state.semester?.dateTo"
              required
              @change="loadTarget" /></label
          ><label class="web-field"
            >Свободная пара<select
              v-model.number="form.number"
              required
              ><option
                v-for="item in state.catalog?.slots"
                :key="item.lessonNumber"
                :value="item.lessonNumber"
                :disabled="unavailable(item.lessonNumber)"
                >{{ item.lessonNumber }} · {{ item.startTime.slice(0, 5) }} —
                {{ item.endTime.slice(0, 5)
                }}{{ unavailable(item.lessonNumber) ? ' · занято' : '' }}</option
              ></select
            ></label
          ><label
            v-if="task === 'ONE_OFF'"
            class="web-field"
            >Предмет, тип и преподаватель<select
              v-model.number="form.assignmentId"
              required
              ><option
                v-for="assignment in assignments"
                :key="assignment.id"
                :value="assignment.id"
                >{{ assignment.subjectName }} · {{ lessonTypes[assignment.lessonType ?? ''] }} ·
                {{ assignment.teacherName }}</option
              ></select
            ></label
          ></template
        ><label
          v-if="['ROOM', 'TRANSFER', 'ONE_OFF'].includes(task)"
          class="web-field"
          >Аудитория<input
            v-model="form.room"
            maxlength="64" /></label></fieldset
      ><div class="headman-actions"
        ><button
          class="web-button web-button-primary"
          :disabled="locked || targetBusy"
          >Подтвердить</button
        ><button
          type="button"
          class="web-button"
          :disabled="locked"
          @click="task = null"
          >Отмена</button
        ></div
      ></form
    >
  </HeadmanPage>
</template>
