<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue';
import { useWebLiveRefresh } from '../../app/live';
import { message, webSession } from '../../auth/session';
import { useHeadmanFeature, today, lessonTypes } from './context';
import {
  HeadmanScheduleApiError,
  canDeleteUnusedSeries,
  seriesDeletionProtection,
  scheduleProblemCode,
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
  continueRecurringDeletion,
  type RecurringIntent,
  type RecurringOwner,
} from '../../../../mobile-core/src/features/schedule/headman-recurring-intent';
import {
  freezeRecurringCreate,
  readRecurringCreate,
  recurringCreateScope,
  submitRecurringCreate,
  recurringGenerationNotice,
  type RecurringCreateIntent,
} from '../../../../mobile-core/src/features/schedule/headman-recurring-create';
import type { AcademicWeekPolicy } from '../../../../mobile-core/src/domain/academic-week';
import HeadmanPage from './HeadmanPage.vue';
import WebSelect from '../../shared/WebSelect.vue';
import MobileIcon from '../../../../mobile-core/src/shared/components/MobileIcon.vue';
import './headman-schedule.pcss';
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
const includeInactive = ref(false);
const inactiveRows = computed(() => rows.value.filter((item) => item.active === false));
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
const createRemoved = ref(false);
const owner = computed<RecurringOwner>(() => ({
  userId: session.userId,
  sessionId: session.sessionId,
  groupId: feature.groupId,
  semesterId: semesterId.value,
}));
const locked = computed(() => busy.value || !!pending.value || !!createPending.value);
const publishedSemester = ref(0);
const writable = computed(
  () =>
    feature.writable() &&
    !!semester.value?.active &&
    !loading.value &&
    publishedSemester.value === semesterId.value,
);
function cellItems(day: number, number: number) {
  return rows.value.filter(
    (item) => item.active && item.dayOfWeek === day && item.lessonNumber === number,
  );
}
function availableWeek(day: number, number: number) {
  const cell = cellItems(day, number);
  if (cell.some((item) => item.weekType === 'ALL')) return null;
  const odd = cell.some((item) => item.weekType === 'ODD'),
    even = cell.some((item) => item.weekType === 'EVEN');
  return odd && even ? null : odd ? 'EVEN' : even ? 'ODD' : 'ALL';
}
function cellEntries(day: number, number: number) {
  const entries: { week: HeadmanScheduleItem['weekType']; item: HeadmanScheduleItem | null }[] =
    cellItems(day, number).map((item) => ({ week: item.weekType, item }));
  const freeWeek = availableWeek(day, number);
  if (freeWeek) entries.push({ week: freeWeek, item: null });
  return entries.sort((left, right) => Number(left.week === 'EVEN') - Number(right.week === 'EVEN'));
}
function assignmentFor(item: HeadmanScheduleItem) {
  return assignments.value.find((assignment) => assignment.id === item.assignmentId);
}
function subjectName(item: HeadmanScheduleItem) {
  return assignmentFor(item)?.subjectName ?? `Предмет №${item.subjectId}`;
}
function lessonType(item: HeadmanScheduleItem) {
  return lessonTypes[assignmentFor(item)?.lessonType ?? ''] ?? 'Тип не указан';
}
function weekLabel(week: string | null | undefined) {
  return week === 'ODD' ? '1 неделя' : week === 'EVEN' ? '2 неделя' : 'Обе недели';
}
function lessonLabel(item: HeadmanScheduleItem) {
  return [
    days[(item.dayOfWeek ?? 1) - 1],
    `${item.lessonNumber} пара`,
    semester.value?.name,
    weekLabel(item.weekType),
    subjectName(item),
    lessonType(item),
    assignmentFor(item)?.teacherName,
    item.room ? `Аудитория ${item.room}` : 'Аудитория не указана',
  ].filter(Boolean).join(', ');
}
const days = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
let readRevision = 0;
async function read() {
  const revision = ++readRevision,
    query = semesterId.value;
  loading.value = true;
  try {
    const result = await feature.schedule.listScheduleItems(feature.groupId, query, includeInactive.value);
    feature.assertCurrent();
    if (revision === readRevision && query === semesterId.value) {
      rows.value = result;
      publishedSemester.value = query;
    }
  } finally {
    if (revision === readRevision && query === semesterId.value) loading.value = false;
  }
}
async function load() {
  const revision = ++readRevision;
  let query = semesterId.value;
  const current = () => revision === readRevision && query === semesterId.value;
  loading.value = true;
  error.value = null;
  if (publishedSemester.value !== query) {
    rows.value = [];
    assignments.value = [];
    selected.value = null;
    preview.value = null;
  }
  try {
    await feature.initialize();
    if (!current()) return;
    if (!feature.isHeadman) throw new Error('Конструктор расписания доступен старосте.');
    if (!semesters.value.length) {
      const [periods, slots, weeks] = await Promise.all([
        feature.schedule.listSemesters(),
        feature.schedule.lessonSlots(),
        feature.schedule.academicWeekPolicy(today()),
      ]);
      feature.assertCurrent();
      if (!current()) return;
      semesters.value = periods;
      query = periods.find((item) => item.active)?.id ?? periods[0]?.id ?? 0;
      semesterId.value = query;
      catalog.value = slots;
      policy.value = weeks;
    }
    if (!query) throw new Error('Нет доступного семестра.');
    const [items, teachers] = await Promise.all([
      feature.schedule.listScheduleItems(feature.groupId, query, includeInactive.value),
      feature.schedule.listAssignments(feature.groupId, query),
    ]);
    feature.assertCurrent();
    if (!current()) return;
    rows.value = items;
    assignments.value = teachers;
    publishedSemester.value = query;
    const queryOwner = { ...owner.value, semesterId: query };
    pending.value = readRecurringIntent(sessionStorage, queryOwner);
    createPending.value = readRecurringCreate(sessionStorage, queryOwner);
    createRemoved.value = false;
  } catch (cause) {
    if (current()) error.value = message(cause);
  } finally {
    if (current()) loading.value = false;
  }
}
function open(day: number, number: number, item: HeadmanScheduleItem | null = null) {
  if (locked.value || !writable.value) return;
  const freeWeek = availableWeek(day, number);
  if (!item && !freeWeek) return;
  editor.value = true;
  selected.value = item;
  form.day = day;
  form.number = number;
  form.assignmentId = item?.assignmentId ?? assignments.value[0]?.id ?? 0;
  form.week = item?.weekType ?? freeWeek ?? 'ALL';
  form.room = item?.room ?? '';
  preview.value = null;
}
async function calculate(action: 'UPDATE' | 'DELETE') {
  if (!selected.value?.id || locked.value || !writable.value) return;
  if (action === 'UPDATE' && selected.value.active === false) return;
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
  createRemoved.value = false;
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
    const receipt = await submitRecurringCreate(sessionStorage, intent, (input, key) =>
      feature.schedule.createScheduleItem(input, key),
    );
    feature.assertCurrent();
    sessionStorage.removeItem(recurringCreateScope(owner.value));
    createPending.value = null;
    editor.value = false;
    notice.value = recurringGenerationNotice(receipt);
    await read();
  } catch (cause) {
    if (cause instanceof HeadmanScheduleApiError && cause.response.status === 410 && scheduleProblemCode(cause) === 'CONSTRUCTOR_SERIES_DELETED') {
      createRemoved.value = true;
      error.value = 'Эта серия уже удалена. Исходный запрос не создаст её повторно.';
      return;
    }
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
function acknowledgeRemovedCreate() {
  const intent = createPending.value;
  if (!createRemoved.value || busy.value || !intent || recurringCreateScope(intent.owner) !== recurringCreateScope(owner.value)) return;
  try {
    feature.assertCurrent();
    sessionStorage.removeItem(recurringCreateScope(intent.owner));
    createPending.value = null;
    createRemoved.value = false;
    editor.value = false;
    error.value = null;
    notice.value = 'Запрос удалённой серии закрыт. Новую серию можно создать отдельным действием.';
  } catch (cause) { error.value = message(cause); }
}
async function apply(recover = false) {
  if (busy.value || !writable.value) return;
  let intent = pending.value;
  if (!intent) {
    if (!selected.value?.id || !preview.value) return;
    if (preview.value.action === 'DELETE' && !canDeleteUnusedSeries(preview.value.result)) {
      error.value = seriesDeletionProtection(preview.value.result);
      return;
    }
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
  notice.value = null;
  try {
    if (recover && intent.action !== 'DELETE') {
      await feature.schedule.getScheduleItem(id);
      feature.assertCurrent();
    }
    if (intent.action === 'DELETE') {
      const deletion = await continueRecurringDeletion(feature.schedule, intent, () => {
        feature.assertCurrent();
        return intent!.owner.semesterId === semesterId.value && intent!.owner.groupId === feature.groupId;
      }, (next) => {
        persistRecurringIntent(sessionStorage, next);
        pending.value = next;
      });
      if (!deletion) return;
      intent = deletion.intent;
      acknowledged = true;
      if (deletion.operation && deletion.operation.state !== 'COMPLETED') {
        if (deletion.operation.state === 'RELEASED') {
          sessionStorage.removeItem(recurringScope(intent.owner));
          pending.value = null;
          preview.value = null;
          throw new Error('Удаление не выполнено. Данные сохранены; обнови расчёт перед новой попыткой.');
        }
        notice.value = 'Полное удаление ещё выполняется. Исходная операция сохранена; проверь её состояние.';
        return;
      }
    } else {
      const accepted = await feature.schedule.updateScheduleItem(id, intent.input!, intent.key);
      feature.assertCurrent();
      intent = {
        ...intent,
        transferIds: (accepted.transfers ?? []).map((transfer) => transfer.operationId),
      };
      persistRecurringIntent(sessionStorage, intent);
      pending.value = intent;
      acknowledged = true;
      const result = await feature.schedule.getScheduleItem(id);
      feature.assertCurrent();
      if (
        result.id !== id ||
        result.groupId !== feature.groupId ||
        result.semesterId !== intent.owner.semesterId ||
        result.active !== true ||
        result.weekType !== intent.input!.weekType ||
        (result.room ?? null) !== intent.input!.room
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
    }
    sessionStorage.removeItem(recurringScope(intent.owner));
    pending.value = null;
    preview.value = null;
    editor.value = false;
    notice.value =
      intent.action === 'DELETE'
        ? 'Шаблон и все пары этой серии полностью удалены.'
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
    ><div class="headman-constructor-toolbar">
      <WebSelect class="web-field" v-model="semesterId" label="Семестр" :options="semesters.flatMap(item => item.id ? [{ value: item.id, label: item.name + (item.active ? '' : ' · архив') }] : [])" :disabled="locked || editor || loading" @change="load" />
      <span v-if="policy" class="web-caption">Текущая неделя · {{ policy.label }}</span>
      <button type="button" class="web-button" :disabled="locked || editor || loading" :aria-pressed="includeInactive" @click="includeInactive = !includeInactive; load()">Ранее убранные шаблоны</button>
    </div>
    <p
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
      ><button v-if="createRemoved" type="button" class="web-button" :disabled="busy" @click="acknowledgeRemovedCreate">Закрыть запрос удалённой серии</button></p
    >
    <p class="web-caption headman-constructor-legend">Сверху — 1 неделя · снизу — 2 неделя</p>
    <div class="headman-timetable-scroll"
         tabindex="0"
         role="region"
         aria-label="Регулярное расписание"
      ><table class="headman-timetable headman-timetable-constructor"
        ><thead
          ><tr
            ><th>Пара</th
            ><th
              v-for="day in days"
              :key="day"
              scope="col"
              >{{ day }}</th
            ></tr
          ></thead
        ><tbody
          ><tr
            v-for="slot in catalog?.slots"
            :key="slot.lessonNumber"
            ><th scope="row"
              >{{ slot.lessonNumber }} пара<small
                >{{ slot.startTime.slice(0, 5) }} — {{ slot.endTime.slice(0, 5) }}</small
              ></th
            ><td
              v-for="(_, day) in days"
              :key="day"
              ><div class="headman-timetable-cell"
                ><template
                  v-for="entry in cellEntries(day + 1, slot.lessonNumber)"
                  :key="entry.item?.id ?? entry.week ?? 'ALL'"
                ><button
                  v-if="entry.item"
                  class="headman-timetable-lesson"
                  :class="{
                    'is-selected': selected?.id === entry.item.id && editor,
                    'is-odd-week': entry.week === 'ODD',
                    'is-even-week': entry.week === 'EVEN',
                    'is-both-weeks': entry.week === 'ALL',
                  }"
                  :aria-pressed="selected?.id === entry.item.id && editor"
                  :aria-label="lessonLabel(entry.item)"
                  :title="lessonLabel(entry.item)"
                  :disabled="locked || !writable"
                  @click="open(day + 1, slot.lessonNumber, entry.item)"
                  ><strong>{{ subjectName(entry.item) }}</strong><span>{{ lessonType(entry.item) }}</span></button
                ><button
                  v-else
                  class="headman-timetable-free"
                  :class="{
                    'is-odd-week': entry.week === 'ODD',
                    'is-even-week': entry.week === 'EVEN',
                    'is-both-weeks': entry.week === 'ALL',
                  }"
                  :disabled="locked || !writable"
                  :aria-label="`Добавить: ${days[day]}, ${slot.lessonNumber} пара, ${weekLabel(entry.week)}, ${semester?.name ?? ''}`"
                  @click="open(day + 1, slot.lessonNumber)"
                  ><MobileIcon name="plus" /></button
                ></template></div
              ></td
            ></tr
          ></tbody
        ></table
      ></div
    >
    <section v-if="includeInactive && !editor" class="headman-panel web-stack" aria-label="Ранее убранные шаблоны">
      <p class="web-caption">Эти шаблоны убраны из конструктора. Полное удаление доступно только после проверки истории.</p>
      <p v-if="!inactiveRows.length" class="web-caption">Ранее убранных шаблонов нет.</p>
      <div v-for="item in inactiveRows" :key="item.id" class="headman-actions"><span>{{ subjectName(item) }} · {{ days[(item.dayOfWeek ?? 1) - 1] }} · {{ item.lessonNumber }} пара</span><button type="button" class="web-button" :disabled="locked || !writable" @click="open(item.dayOfWeek ?? 1, item.lessonNumber ?? 1, item)">Проверить полное удаление</button></div>
    </section>
    <form
      v-if="editor"
      class="headman-panel web-stack headman-timetable-editor"
      @submit.prevent="selected ? calculate('UPDATE') : create()"
      ><h2
        >{{ selected?.active === false ? 'Ранее убранный шаблон' : selected ? 'Изменить регулярный слот' : 'Добавить регулярный слот' }} ·
        {{ days[form.day - 1] }} · {{ form.number }} пара</h2
     ><p class="web-caption">
       {{ semester?.name }} · {{ weekLabel(form.week) }}
     </p>
      <fieldset :disabled="locked || selected?.active === false"
      ><legend>Параметры слота</legend><WebSelect class="web-field" v-model="form.assignmentId" label="Предмет, тип и преподаватель" :options="assignments.flatMap(assignment => assignment.id ? [{ value: assignment.id, label: `${assignment.subjectName} · ${lessonTypes[assignment.lessonType ?? ''] ?? 'Тип не указан'} · ${assignment.teacherName}` }] : [])" :disabled="locked || !!selected" required  /><WebSelect class="web-field" v-model="form.week" label="Недели" :options="(policy?.weekTypes ?? []).map(week => ({ value: week.weekType, label: week.label }))" :disabled="locked" @change="preview = null"  /><label class="web-field"
          >Аудитория<input
            v-model="form.room"
            maxlength="64"
            @input="preview = null" /></label></fieldset
      ><div class="headman-actions"
        ><button
          class="web-button web-button-primary"
          :disabled="locked || selected?.active === false"
          >{{ selected ? 'Рассчитать изменение' : 'Добавить' }}</button
        ><button
          v-if="selected"
          type="button"
          class="web-button"
          :disabled="locked"
          @click="calculate('DELETE')"
          >Удалить из расписания</button
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
          preview.action === 'DELETE' ? 'Удалить регулярное занятие из расписания?' : 'Применить изменение серии?'
        }}</h3
        ><p
          v-if="preview.action === 'DELETE'"
          ><template v-if="canDeleteUnusedSeries(preview.result)">Шаблон и все пары серии будут полностью удалены. Пар к удалению: {{ preview.result.removableCount }}.</template><template v-else>Полное удаление недоступно: {{ seriesDeletionProtection(preview.result) }}.</template> Автоматические отметки «н» не мешают удалению. При наличии реальных действий серия сохраняется целиком.</p
        ><p
          v-else
          >Будет изменено {{ preview.result.updatedCount }}, убрано
          {{ preview.result.removedCount }}, восстановлено {{ preview.result.restoredCount }},
          создано {{ preview.result.createdCount }}. Прошедшая история сохранится.</p
        ><div class="headman-actions"><button
          type="button"
          class="web-button web-button-primary"
          :disabled="locked || (preview.action === 'DELETE' && !canDeleteUnusedSeries(preview.result))"
          @click="apply()"
          >{{ preview.action === 'DELETE' ? 'Удалить из расписания' : 'Подтвердить расчёт' }}</button
        ><button
          v-if="preview.action === 'DELETE'"
          type="button"
          class="web-button"
          :disabled="locked"
          @click="preview = null"
          >Отмена удаления</button
        ></div></section
      ></form
    >
  </HeadmanPage>
</template>
