<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue';
import { useWebLiveRefresh } from '../../app/live';
import { message, webSession } from '../../auth/session';
import { useHeadmanFeature, today, lessonTypes } from './context';
import {
  HeadmanScheduleApiError,
  type HeadmanScheduleItem,
  type HeadmanScheduleAssignment,
  type HeadmanScheduleSemester,
  type HeadmanLessonSlotCatalog,
  type HeadmanLifecyclePreview,
  type HeadmanScheduleUpdateInput,
} from '../../../../mobile-core/src/features/schedule/headman-schedule-client';
import {
  recurringUpdate,
  recurringScope,
  readRecurringIntent,
  persistRecurringIntent,
  type RecurringIntent,
  type RecurringOwner,
} from '../../../../mobile-core/src/features/schedule/headman-recurring-intent';
import {
  freezeRecurringCreate,
  readRecurringCreate,
  recurringCreateScope,
  submitRecurringCreate,
  type RecurringCreateIntent,
} from '../../../../mobile-core/src/features/schedule/headman-recurring-create';
import type { AcademicWeekPolicy } from '../../../../mobile-core/src/domain/academic-week';
import HeadmanPage from './HeadmanPage.vue';
const feature = useHeadmanFeature(),
  session = webSession.snapshot.value!;
const loading = ref(false),
  busy = ref(false),
  error = ref<string | null>(null),
  notice = ref<string | null>(null);
const semesters = shallowRef<readonly HeadmanScheduleSemester[]>([]),
  semesterId = ref(0);
const semester = computed(() => semesters.value.find((item) => item.id === semesterId.value));
const rows = shallowRef<readonly HeadmanScheduleItem[]>([]),
  assignments = shallowRef<readonly HeadmanScheduleAssignment[]>([]);
const catalog = shallowRef<HeadmanLessonSlotCatalog | null>(null),
  policy = shallowRef<AcademicWeekPolicy | null>(null);
const editor = ref(false),
  selected = shallowRef<HeadmanScheduleItem | null>(null),
  form = reactive({
    day: 1,
    number: 1,
    assignmentId: 0,
    week: 'ALL' as 'ALL' | 'ODD' | 'EVEN',
    room: '',
  });
const preview = shallowRef<{
  action: 'UPDATE' | 'DELETE';
  input: Omit<HeadmanScheduleUpdateInput, 'expectedRevision'> | null;
  result: HeadmanLifecyclePreview;
} | null>(null);
const pending = shallowRef<RecurringIntent | null>(null),
  createPending = shallowRef<RecurringCreateIntent | null>(null);
const owner = computed<RecurringOwner>(() => ({
  userId: session.userId,
  sessionId: session.sessionId,
  groupId: feature.groupId,
  semesterId: semesterId.value,
}));
const locked = computed(() => busy.value || !!pending.value || !!createPending.value);
const writable = computed(() => feature.writable() && !!semester.value?.active);
function canAdd(day: number, number: number) {
  const cell = rows.value.filter(
    (item) => item.active && item.dayOfWeek === day && item.lessonNumber === number,
  );
  return (
    !cell.some((item) => item.weekType === 'ALL') &&
    !(cell.some((item) => item.weekType === 'ODD') && cell.some((item) => item.weekType === 'EVEN'))
  );
}
const days = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
async function read() {
  const result = await feature.schedule.listScheduleItems(feature.groupId, semesterId.value);
  feature.assertCurrent();
  rows.value = result;
}
async function load() {
  loading.value = true;
  error.value = null;
  try {
    await feature.initialize();
    if (!feature.isHeadman) throw new Error('Конструктор расписания доступен старосте.');
    if (!semesters.value.length) {
      const [periods, slots, weeks] = await Promise.all([
        feature.schedule.listSemesters(),
        feature.schedule.lessonSlots(),
        feature.schedule.academicWeekPolicy(today()),
      ]);
      feature.assertCurrent();
      semesters.value = periods;
      semesterId.value = periods.find((item) => item.active)?.id ?? periods[0]?.id ?? 0;
      catalog.value = slots;
      policy.value = weeks;
    }
    if (!semesterId.value) throw new Error('Нет доступного семестра.');
    const [items, teachers] = await Promise.all([
      feature.schedule.listScheduleItems(feature.groupId, semesterId.value),
      feature.schedule.listAssignments(feature.groupId, semesterId.value),
    ]);
    feature.assertCurrent();
    rows.value = items;
    assignments.value = teachers;
    pending.value = readRecurringIntent(sessionStorage, owner.value);
    createPending.value = readRecurringCreate(sessionStorage, owner.value);
  } catch (cause) {
    error.value = message(cause);
  } finally {
    loading.value = false;
  }
}
function open(day: number, number: number, item: HeadmanScheduleItem | null = null) {
  if (locked.value || !writable.value) return;
  editor.value = true;
  selected.value = item;
  form.day = day;
  form.number = number;
  form.assignmentId = item?.assignmentId ?? assignments.value[0]?.id ?? 0;
  form.week =
    item?.weekType ??
    (rows.value.some(
      (row) =>
        row.active &&
        row.dayOfWeek === day &&
        row.lessonNumber === number &&
        row.weekType === 'ODD',
    )
      ? 'EVEN'
      : rows.value.some(
            (row) =>
              row.active &&
              row.dayOfWeek === day &&
              row.lessonNumber === number &&
              row.weekType === 'EVEN',
          )
        ? 'ODD'
        : 'ALL');
  form.room = item?.room ?? '';
  preview.value = null;
}
async function calculate(action: 'UPDATE' | 'DELETE') {
  if (!selected.value?.id || locked.value || !writable.value) return;
  busy.value = true;
  error.value = null;
  try {
    const input =
      action === 'DELETE' ? null : recurringUpdate(selected.value, form.week, form.room);
    const result = await feature.schedule.previewScheduleItem(selected.value.id, input);
    feature.assertCurrent();
    preview.value = { action, input, result };
  } catch (cause) {
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
async function create(recover = false) {
  if (busy.value || !writable.value || pending.value) return;
  busy.value = true;
  error.value = null;
  try {
    let intent = createPending.value;
    if (!intent) {
      const assignment = assignments.value.find((item) => item.id === form.assignmentId),
        slot = catalog.value?.slots.find((item) => item.lessonNumber === form.number);
      if (!assignment?.id || !assignment.subjectId || !slot)
        throw new Error('Выбери назначение преподавателя и номер пары.');
      intent = freezeRecurringCreate(owner.value, crypto.randomUUID(), {
        assignmentId: assignment.id,
        groupId: feature.groupId,
        subjectId: assignment.subjectId,
        semesterId: semesterId.value,
        dayOfWeek: form.day,
        lessonNumber: slot.lessonNumber,
        startTime: slot.startTime,
        endTime: slot.endTime,
        weekType: form.week,
        ...(form.room.trim() ? { room: form.room.trim() } : {}),
      });
      sessionStorage.setItem(recurringCreateScope(owner.value), JSON.stringify(intent));
      createPending.value = intent;
    }
    if (recover) await read();
    await submitRecurringCreate(sessionStorage, intent, (input, key) =>
      feature.schedule.createScheduleItem(input, key),
    );
    feature.assertCurrent();
    sessionStorage.removeItem(recurringCreateScope(owner.value));
    createPending.value = null;
    editor.value = false;
    notice.value = 'Регулярная пара добавлена.';
    await read();
  } catch (cause) {
    if (
      !recover &&
      cause instanceof HeadmanScheduleApiError &&
      [400, 403, 409, 422].includes(cause.response.status)
    ) {
      sessionStorage.removeItem(recurringCreateScope(owner.value));
      createPending.value = null;
    }
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
async function apply(recover = false) {
  if (busy.value || !writable.value) return;
  let intent = pending.value;
  if (!intent) {
    if (!selected.value?.id || !preview.value) return;
    intent = {
      owner: owner.value,
      target: { ...selected.value },
      action: preview.value.action,
      input: preview.value.input
        ? { ...preview.value.input, expectedRevision: preview.value.result.revision }
        : null,
      key: crypto.randomUUID(),
      preview: preview.value.result,
      rejected: false,
      transferIds: [],
    };
    persistRecurringIntent(sessionStorage, intent);
    pending.value = intent;
  }
  const id = intent.target.id!;
  busy.value = true;
  error.value = null;
  let acknowledged = false;
  try {
    if (recover) {
      await feature.schedule.getScheduleItem(id);
      feature.assertCurrent();
    }
    if (intent.action === 'DELETE')
      await feature.schedule.deleteScheduleItem(id, intent.preview.revision, intent.key);
    else {
      const accepted = await feature.schedule.updateScheduleItem(id, intent.input!, intent.key);
      feature.assertCurrent();
      intent = {
        ...intent,
        transferIds: (accepted.transfers ?? []).map((transfer) => transfer.operationId),
      };
      persistRecurringIntent(sessionStorage, intent);
      pending.value = intent;
    }
    acknowledged = true;
    const result = await feature.schedule.getScheduleItem(id);
    feature.assertCurrent();
    if (
      result.id !== id ||
      result.groupId !== feature.groupId ||
      result.semesterId !== intent.owner.semesterId ||
      (intent.action === 'DELETE'
        ? result.active !== false
        : result.active !== true ||
          result.weekType !== intent.input!.weekType ||
          (result.room ?? null) !== intent.input!.room)
    )
      throw new Error('Сервер пока не подтвердил состояние серии.');
    const transfers = await Promise.all(
      intent.transferIds.map((operation) => feature.schedule.getRecurringTransfer(operation)),
    );
    feature.assertCurrent();
    if (transfers.some((transfer) => transfer.state !== 'COMPLETED'))
      throw new Error(
        'Серия сохранена, перенос связанных данных ещё не завершён. Проверь исходную операцию.',
      );
    sessionStorage.removeItem(recurringScope(intent.owner));
    pending.value = null;
    preview.value = null;
    editor.value = false;
    notice.value =
      intent.action === 'DELETE'
        ? 'Будущие пары убраны. Прошедшая история сохранена.'
        : 'Будущие пары изменены. Прошедшая история сохранена.';
    await read();
  } catch (cause) {
    if (
      !recover &&
      !acknowledged &&
      cause instanceof HeadmanScheduleApiError &&
      cause.response.status === 409
    ) {
      sessionStorage.removeItem(recurringScope(intent.owner));
      pending.value = null;
      preview.value = null;
    }
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
useWebLiveRefresh(load, { blocked: () => loading.value || locked.value || editor.value });
</script>
<template>
  <HeadmanPage
    title="Конструктор расписания"
    :loading="loading"
    :error="error"
    @reload="load"
    ><form
      class="headman-toolbar"
      @submit.prevent="load"
      ><label class="web-field"
        >Семестр<select
          v-model.number="semesterId"
          :disabled="locked || editor"
          ><option
            v-for="item in semesters"
            :key="item.id"
            :value="item.id"
            >{{ item.name }}{{ item.active ? '' : ' · архив' }}</option
          ></select
        ></label
      ><button
        class="web-button"
        :disabled="locked || editor"
        >Показать</button
      ><p v-if="policy"
        >Текущая неделя · {{ policy.label }} · {{ policy.weekStart }} — {{ policy.weekEnd }}</p
      ></form
    ><p
      v-if="notice"
      role="status"
      >{{ notice }}</p
    ><p
      v-if="pending"
      class="web-notice"
      >Исходное изменение серии сохранено.
      <button
        class="web-button"
        :disabled="busy"
        @click="apply(true)"
        >Проверить и восстановить</button
      ></p
    ><p
      v-if="createPending"
      class="web-notice"
      >Исходный запрос создания сохранён.
      <button
        class="web-button"
        :disabled="busy"
        @click="create(true)"
        >Проверить создание серии</button
      ></p
    >
    <div class="headman-table-scroll"
      ><table class="headman-table headman-schedule"
        ><thead
          ><tr
            ><th>Пара</th
            ><th
              v-for="day in days"
              :key="day"
              >{{ day }}</th
            ></tr
          ></thead
        ><tbody
          ><tr
            v-for="slot in catalog?.slots"
            :key="slot.lessonNumber"
            ><th scope="row"
              >{{ slot.lessonNumber }} пара<p
                >{{ slot.startTime.slice(0, 5) }} — {{ slot.endTime.slice(0, 5) }}</p
              ></th
            ><td
              v-for="(_, day) in days"
              :key="day"
              ><div class="web-stack"
                ><button
                  v-for="item in rows.filter(
                    (row) =>
                      row.active &&
                      row.dayOfWeek === day + 1 &&
                      row.lessonNumber === slot.lessonNumber,
                  )"
                  :key="item.id"
                  class="headman-slot"
                  :disabled="locked || !writable"
                  @click="open(day + 1, slot.lessonNumber, item)"
                  >{{
                    assignments.find((assignment) => assignment.id === item.assignmentId)
                      ?.subjectName ?? `Предмет №${item.subjectId}`
                  }}<span>{{
                    assignments.find((assignment) => assignment.id === item.assignmentId)
                      ?.teacherName
                  }}</span
                  ><span
                    >{{
                      policy?.weekTypes.find((week) => week.weekType === item.weekType)?.label ??
                      item.weekType
                    }}
                    · {{ item.room ?? '—' }}</span
                  ></button
                ><button
                  v-if="canAdd(day + 1, slot.lessonNumber)"
                  class="headman-slot headman-slot-free"
                  :disabled="locked || !writable"
                  :aria-label="`Добавить: ${days[day]}, ${slot.lessonNumber} пара`"
                  @click="open(day + 1, slot.lessonNumber)"
                  >Добавить</button
                ></div
              ></td
            ></tr
          ></tbody
        ></table
      ></div
    >
    <form
      v-if="editor"
      class="headman-panel web-stack"
      @submit.prevent="selected ? calculate('UPDATE') : create()"
      ><h2
        >{{ selected ? 'Изменить регулярный слот' : 'Добавить регулярный слот' }} ·
        {{ days[form.day - 1] }} · {{ form.number }} пара</h2
      ><fieldset :disabled="locked"
        ><label class="web-field"
          >Предмет, тип и преподаватель<select
            v-model.number="form.assignmentId"
            :disabled="!!selected"
            required
            ><option
              v-for="assignment in assignments"
              :key="assignment.id"
              :value="assignment.id"
              >{{ assignment.subjectName }} · {{ lessonTypes[assignment.lessonType ?? ''] }} ·
              {{ assignment.teacherName }}</option
            ></select
          ></label
        ><label class="web-field"
          >Недели<select
            v-model="form.week"
            @change="preview = null"
            ><option
              v-for="week in policy?.weekTypes"
              :key="week.weekType"
              :value="week.weekType"
              >{{ week.label }}</option
            ></select
          ></label
        ><label class="web-field"
          >Аудитория<input
            v-model="form.room"
            maxlength="64"
            @input="preview = null" /></label></fieldset
      ><div class="headman-actions"
        ><button
          class="web-button web-button-primary"
          :disabled="locked"
          >{{ selected ? 'Рассчитать изменение' : 'Добавить' }}</button
        ><button
          v-if="selected"
          type="button"
          class="web-button"
          :disabled="locked"
          @click="calculate('DELETE')"
          >Убрать будущий слот</button
        ><button
          type="button"
          class="web-button"
          :disabled="locked"
          @click="
            editor = false;
            preview = null;
          "
          >Отмена</button
        ></div
      ><section
        v-if="preview"
        class="web-notice web-stack"
        ><h3>{{
          preview.action === 'DELETE' ? 'Убрать будущие пары?' : 'Применить изменение серии?'
        }}</h3
        ><p
          >Будет изменено {{ preview.result.updatedCount }}, убрано
          {{ preview.result.removedCount }}, восстановлено {{ preview.result.restoredCount }},
          создано {{ preview.result.createdCount }}. Прошедшая история сохранится.</p
        ><button
          type="button"
          class="web-button web-button-primary"
          :disabled="locked"
          @click="apply()"
          >Подтвердить расчёт</button
        ></section
      ></form
    >
  </HeadmanPage>
</template>
