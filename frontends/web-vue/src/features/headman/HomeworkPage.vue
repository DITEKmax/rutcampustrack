<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue';
import { message, webSession } from '../../auth/session';
import { useWebLiveRefresh } from '../../app/live';
import { useHeadmanFeature, today, dateLabel, lessonTypes } from './context';
import type {
  HeadmanManagedHomework,
  HeadmanHomeworkHistory,
  HeadmanHomeworkCreateInput,
  HeadmanHomeworkUpdateInput,
  HeadmanHomeworkPublicationReceipt,
  HeadmanHomeworkPlacementInput,
} from '../../../../mobile-core/src/features/homework/headman-homework-client';
import { HeadmanHomeworkApiError } from '../../../../mobile-core/src/features/homework/headman-homework-client';
import type {
  HeadmanSubject,
  HeadmanSemester,
  HeadmanSubjectType,
} from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import type { HeadmanJournalLesson } from '../../../../mobile-core/src/features/headman-journal/headman-journal-client';
import {
  homeworkDateWritable,
  homeworkSubjectTypes,
  homeworkLessonEligible,
  homeworkLessonOrder,
  canReselectHomeworkCreate,
} from '../../../../mobile-core/src/features/homework/headman-homework-state';
import { readPublishedHomeworks } from '../../../../mobile-core/src/features/homework/headman-published-homework';
import HeadmanPage from './HeadmanPage.vue';
import {
  homeworkEditMatches,
  homeworkPlacementChanged,
  canonicalHomeworkText,
} from './homework-edit-state';
import HomeworkHistory from './HomeworkHistory.vue';
import LinkifiedText from '../../shared/LinkifiedText.vue';
import WebSelect from '../../shared/WebSelect.vue';
import WebDateField from '../../shared/WebDateField.vue';
import './headman-workflows.pcss';

const feature = useHeadmanFeature();
const loading = ref(false),
  busy = ref(false),
  error = ref<string | null>(null),
  archive = ref(false);
const subjects = shallowRef<readonly HeadmanSubject[]>([]),
  semesters = shallowRef<readonly HeadmanSemester[]>([]),
  rows = shallowRef<readonly HeadmanManagedHomework[]>([]);
const semesterId = ref(0),
  subjectFilter = ref(0),
  from = ref(''),
  to = ref(''),
  search = ref('');
const semester = computed(
  () => semesters.value.find((item) => item.id === semesterId.value) ?? null,
);
const editor = ref(false),
  editing = shallowRef<HeadmanManagedHomework | null>(null),
  history = shallowRef<readonly HeadmanHomeworkHistory[] | null>(null),
  historyId = ref(0),
  historyLoading = ref(false),
  historyError = ref<string | null>(null);
const draft = reactive({
  title: '',
  description: '',
  link: '',
  subjectId: 0,
  lessonType: 'LECTURE' as HeadmanSubjectType,
  date: today(),
  bound: true,
});
const nextLesson = shallowRef<HeadmanJournalLesson | null>(null),
  placementLoading = ref(false),
  placementTouched = ref(false),
  dateValid = ref(true),
  placementError = ref<string | null>(null),
  placementLessons = shallowRef<readonly HeadmanJournalLesson[]>([]);
const placementDates = computed(() => [...new Set([
  ...placementLessons.value.map(lesson => lesson.date),
  ...(editing.value && !placementTouched.value ? [editing.value.lessonDate] : []),
])]);
const dateLessons = computed(() => placementLessons.value.filter(lesson => lesson.date === draft.date));
const selectedLessonId = computed({
  get: () => nextLesson.value?.id ?? 0,
  set: (id: number) => {
    placementTouched.value = true;
    nextLesson.value = dateLessons.value.find(lesson => lesson.id === id) ?? null;
  },
});
const types = computed(() => {
  const subject = subjects.value.find((item) => item.id === draft.subjectId);
  return subject && semester.value
    ? homeworkSubjectTypes(subject, feature.groupId, semester.value.id, draft.date)
    : [];
});
const writable = computed(() => feature.writable('MANAGE_HOMEWORK') && !!semester.value?.active);
const visible = computed(() =>
  rows.value
    .filter(
      (item) =>
        (!subjectFilter.value || item.subjectId === subjectFilter.value) &&
        (!from.value || item.lessonDate >= from.value) &&
        (!to.value || item.lessonDate <= to.value) &&
        `${item.title} ${item.description ?? ''}`
          .toLocaleLowerCase('ru-RU')
          .includes(search.value.toLocaleLowerCase('ru-RU')),
    )
    .sort(
      (a, b) =>
        a.lessonDate.localeCompare(b.lessonDate) || (a.lessonNumber ?? 0) - (b.lessonNumber ?? 0),
    ),
);
const grouped = computed(() => {
  const groups = new Map<string, HeadmanManagedHomework[]>();
  for (const item of visible.value) { const group = groups.get(item.lessonDate) ?? []; group.push(item); groups.set(item.lessonDate, group); }
  return [...groups].map(([date, items]) => ({ date, items }));
});
function closeEditor() { if (busy.value || pending.value) return; placementRun++; placementLoading.value = false; nextLesson.value = null; editor.value = false; }
function selectSection(archived: boolean) { if (busy.value || pending.value) return; closeHistory(); closeEditor(); if (archive.value !== archived) rows.value = []; archive.value = archived; void load(); }
function publisher(id: number | null) { return id === null ? null : String(id) === webSession.snapshot.value?.userId ? webSession.snapshot.value.displayName : `Пользователь № ${id}`; }
type Pending =
  | {
      kind: 'create';
      input: HeadmanHomeworkCreateInput;
      receipt: HeadmanHomeworkPublicationReceipt | null;
    }
  | { kind: 'edit'; id: number; input: HeadmanHomeworkUpdateInput; semesterId: number }
  | { kind: 'delete'; id: number; semesterId: number };
const pending = shallowRef<Pending | null>(null);
const recoveryKey = `rct:web:headman:homework:${webSession.identity.value}`;
let run = 0,
  placementRun = 0, historyRun = 0;
let alive = true;
function persist(value: Pending | null) {
  feature.assertCurrent();
  if (value && value.kind !== 'delete') canonicalHomeworkText(value.input);
  if (value) sessionStorage.setItem(recoveryKey, JSON.stringify(value));
  else sessionStorage.removeItem(recoveryKey);
  pending.value = value;
}
function restore() {
  const raw = sessionStorage.getItem(recoveryKey);
  if (!raw) return;
  const value: unknown = JSON.parse(raw);
  if (
    !value ||
    typeof value !== 'object' ||
    !('kind' in value) ||
    !['create', 'edit', 'delete'].includes(String(value.kind))
  )
    throw new Error('Не удалось прочитать незавершённое изменение ДЗ.');
  // The typed client validates the frozen payload before any potential recovery write.
  const restored = value as Pending;
  if (restored.kind !== 'delete') {
    try {
      canonicalHomeworkText(restored.input);
    } catch (cause) {
      sessionStorage.removeItem(recoveryKey);
      error.value = message(cause);
      return;
    }
  }
  pending.value = restored;
}
async function load() {
  const revision = ++run;
  loading.value = true;
  error.value = null;
  try {
    await feature.initialize();
    if (!feature.allowed('MANAGE_HOMEWORK')) throw new Error('Нет права управления ДЗ группы.');
    if (!semesters.value.length) {
      const [periods, available] = await Promise.all([
        feature.subjects.listSemesters(),
        feature.subjects.listSubjects(feature.groupId),
      ]);
      feature.assertCurrent();
      semesters.value = periods;
      subjects.value = available;
      semesterId.value = periods.find((item) => item.active)?.id ?? periods[0]?.id ?? 0;
      restore();
    }
    if (!semesterId.value) throw new Error('Нет доступного семестра.');
    const result = await feature.homework.listHomeworks(
      feature.groupId,
      semesterId.value,
      archive.value,
    );
    feature.assertCurrent();
    if (revision === run) rows.value = result;
  } catch (cause) {
    if (revision === run) error.value = message(cause);
  } finally {
    if (revision === run) loading.value = false;
  }
}
function openEditor(item: HeadmanManagedHomework | null = null) {
  if (!writable.value || busy.value || pending.value || item?.archived) return;
  closeHistory();
  editing.value = item;
  editor.value = true;
  placementTouched.value = false;
  dateValid.value = true;
  draft.title = item?.title ?? '';
  draft.description = item?.description ?? '';
  draft.link = item?.link ?? '';
  draft.subjectId = item?.subjectId ?? subjects.value[0]?.id ?? 0;
  draft.lessonType = item?.lessonType ?? 'LECTURE';
  draft.date = item?.lessonDate ?? today();
  draft.bound = item ? item.bindingMode === 'LESSON' : true;
  nextLesson.value = null;
  error.value = null;
  void loadPlacement(!item);
}
function placementContext() {
  return JSON.stringify([feature.groupId, semester.value?.id, draft.subjectId, draft.lessonType, draft.bound, editing.value?.id]);
}
function changePlacementContext() {
  placementTouched.value = true;
  void loadPlacement(true);
}
function dateValidity(valid: boolean) {
  dateValid.value = valid;
  if (!valid) { placementTouched.value = true; nextLesson.value = null; }
}
function chooseDate() {
  placementTouched.value = true;
  nextLesson.value = dateValid.value && dateLessons.value.length === 1 ? dateLessons.value[0]! : null;
}
async function findNext() {
  placementTouched.value = true;
  await loadPlacement(true);
}
async function loadPlacement(nearest = false) {
  const revision = ++placementRun;
  nextLesson.value = null;
  placementLessons.value = [];
  placementError.value = null;
  placementLoading.value = false;
  if (!draft.bound || !draft.subjectId || !semester.value || !writable.value || !editor.value) return;
  const availableTypes = types.value;
  if (!availableTypes.includes(draft.lessonType)) draft.lessonType = availableTypes[0] ?? 'LECTURE';
  const captured = placementContext();
  const period = semester.value, subjectId = draft.subjectId, type = draft.lessonType;
  const from = today() > period.dateFrom ? today() : period.dateFrom;
  if (from > period.dateTo) return;
  placementLoading.value = true;
  try {
    const [lessons, closest] = await Promise.all([
      feature.journal.listLessons(feature.groupId, from, period.dateTo),
      nearest ? feature.homework.nextHomeworkLesson(feature.groupId, period.id, subjectId, type, today()) : Promise.resolve(null),
    ]);
    feature.assertCurrent();
    if (!alive || revision !== placementRun || captured !== placementContext() || !editor.value) return;
    const context = { groupId: feature.groupId, subjectId, type, semester: period, now: new Date() };
    placementLessons.value = lessons.filter(lesson => homeworkLessonEligible(lesson, context)).slice().sort(homeworkLessonOrder);
    if (nearest && closest) {
      const confirmed = placementLessons.value.find(lesson => lesson.id === closest.id && lesson.occurrenceId === closest.occurrenceId && lesson.occurrenceRevision === closest.occurrenceRevision && lesson.date === closest.date && lesson.lessonNumber === closest.lessonNumber);
      if (!confirmed || !homeworkLessonEligible(closest, context)) {
        placementLessons.value = [];
        throw new Error('Расписание изменилось. Обнови выбор пары.');
      }
      nextLesson.value = confirmed;
      draft.date = confirmed.date;
      dateValid.value = true;
    }
  } catch (cause) {
    if (alive && revision === placementRun && captured === placementContext()) placementError.value = message(cause);
  } finally {
    if (alive && revision === placementRun) placementLoading.value = false;
  }
}
function placement(): HeadmanHomeworkPlacementInput {
  if (!semester.value || !homeworkDateWritable(draft.date, semester.value, today()))
    throw new Error('Выбери текущую или будущую дату активного семестра.');
  if (!types.value.includes(draft.lessonType))
    throw new Error('Нет назначения преподавателя для этого типа занятия и даты.');
  if (!draft.bound)
    return {
      bindingMode: 'DATE',
      lessonDate: draft.date,
      lessonNumber: null,
      lessonType: draft.lessonType,
    };
  const lesson = nextLesson.value;
  if (!lesson?.occurrenceId || !lesson.occurrenceRevision || lesson.date !== draft.date || !homeworkLessonEligible(lesson, { groupId: feature.groupId, subjectId: draft.subjectId, type: draft.lessonType, semester: semester.value, now: new Date() }))
    throw new Error('Выбери подтверждённую пару на указанную дату.');
  return {
    bindingMode: 'LESSON',
    lessonDate: lesson.date,
    lessonNumber: lesson.lessonNumber,
    lessonType: draft.lessonType,
    selectedOccurrenceId: lesson.occurrenceId,
    selectedOccurrenceRevision: lesson.occurrenceRevision,
  };
}
async function save() {
  if (!writable.value || busy.value || pending.value || !semester.value) return;
  try {
    if (placementLoading.value || !dateValid.value) throw new Error('Подтверди корректную дату размещения.');
    if (draft.bound && placementTouched.value && !nextLesson.value) throw new Error('Выбери подтверждённую пару на указанную дату.');
    const text = canonicalHomeworkText(draft);
    if (editing.value) {
      if (!editing.value.revision) throw new Error('Версия задания не подтверждена.');
      const candidate: HeadmanHomeworkPlacementInput = !placementTouched.value ? {
        bindingMode: editing.value.bindingMode,
        lessonDate: editing.value.lessonDate,
        lessonNumber: editing.value.lessonNumber,
        lessonType: editing.value.lessonType ?? null,
      } : {
        bindingMode: draft.bound ? 'LESSON' : 'DATE',
        lessonDate: draft.date,
        lessonNumber: draft.bound
          ? (nextLesson.value?.lessonNumber ?? editing.value.lessonNumber)
          : null,
        lessonType: draft.lessonType,
        ...(draft.bound && nextLesson.value?.occurrenceId
          ? { selectedOccurrenceId: nextLesson.value.occurrenceId }
          : {}),
      };
      const changedPlacement = homeworkPlacementChanged(editing.value, candidate);
      persist({
        kind: 'edit',
        id: editing.value.id,
        semesterId: semester.value.id,
        input: {
          ...text,
          expectedRevision: editing.value.revision,
          requestKey: crypto.randomUUID(),
          ...(changedPlacement ? placement() : {}),
        },
      });
    } else
      persist({
        kind: 'create',
        receipt: null,
        input: {
          ...text,
          groupId: feature.groupId,
          subjectId: draft.subjectId,
          semesterId: semester.value.id,
          requestKey: crypto.randomUUID(),
          ...placement(),
        },
      });
    await execute(false);
  } catch (cause) {
    error.value = message(cause);
  }
}
async function execute(recover: boolean) {
  const operation = pending.value;
  if (!operation || busy.value || !feature.writable('MANAGE_HOMEWORK')) return;
  busy.value = true;
  error.value = null;
  try {
    if (recover) {
      if (operation.kind === 'create') {
        const existing = await readPublishedHomeworks(
          feature.homework,
          feature.groupId,
          operation.input.semesterId,
          () => true,
        );
        feature.assertCurrent();
        const actual = existing?.find((item) => item.requestKey === operation.input.requestKey);
        if (actual) {
          persist(null);
          editor.value = false;
          await load();
          return;
        }
      } else {
        const existing = await readPublishedHomeworks(
          feature.homework,
          feature.groupId,
          operation.semesterId,
          () => true,
        );
        feature.assertCurrent();
        const actual = existing?.find((item) => item.id === operation.id);
        if (operation.kind === 'delete') {
          if (actual?.archived) {
            persist(null);
            await load();
            return;
          }
          throw new Error(
            'Удаление пока не подтверждено. Показано актуальное состояние; повторный DELETE не отправляется.',
          );
        }
        if (!actual || actual.archived) {
          persist(null);
          editor.value = false;
          await load();
          throw new Error('Задание перешло в архив. Изменение недоступно.');
        }
        // A changed revision cannot prove which command wrote it. The original keyed PUT
        // returns its immutable receipt, or a conflict; it never gets a fresh identity.
      }
    }
    feature.assertCurrent();
    if (operation.kind === 'create') {
      const result = await feature.homework.createHomework(operation.input, operation.receipt);
      feature.assertCurrent();
      if (result.state === 'PENDING') {
        persist({ ...operation, receipt: result.receipt });
        error.value = 'Публикация принята и ещё выполняется. Проверь её состояние.';
        return;
      }
    } else if (operation.kind === 'edit') {
      const result = await feature.homework.updateHomework(operation.id, operation.input);
      feature.assertCurrent();
      if (!result || !homeworkEditMatches(result, operation.input))
        throw new Error('Сервер не подтвердил полное содержимое и размещение исходного изменения.');
    } else await feature.homework.deleteHomework(operation.id);
    feature.assertCurrent();
    persist(null);
    editor.value = false;
    await load();
  } catch (cause) {
    if (
      cause instanceof HeadmanHomeworkApiError &&
      ([400, 422, 403].includes(cause.response.status) ||
        (operation.kind === 'create' &&
          canReselectHomeworkCreate(cause.problem, {
            attempts: 1,
            everUncertain: recover,
            hasReceipt: operation.receipt !== null,
          })))
    )
      persist(null);
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
async function remove(item: HeadmanManagedHomework) {
  if (
    !writable.value ||
    busy.value ||
    pending.value ||
    item.archived ||
    !window.confirm(`Удалить задание «${item.title}»? Оно останется в архиве и истории.`)
  )
    return;
  persist({ kind: 'delete', id: item.id, semesterId: item.semesterId });
  await execute(false);
}
function closeHistory() {
  historyRun++;
  historyId.value = 0;
  history.value = null;
  historyError.value = null;
  historyLoading.value = false;
}
async function showHistory(item: HeadmanManagedHomework) {
  if (historyId.value === item.id) { closeHistory(); return; }
  closeHistory();
  const revision = ++historyRun;
  historyId.value = item.id;
  historyLoading.value = true;
  try {
    const result = await feature.homework.history(item.id);
    feature.assertCurrent();
    if (alive && revision === historyRun && historyId.value === item.id && !editor.value) history.value = result;
  } catch (cause) {
    if (alive && revision === historyRun) historyError.value = message(cause);
  } finally {
    if (alive && revision === historyRun) historyLoading.value = false;
  }
}
watch(() => [semesterId.value, archive.value, subjectFilter.value, from.value, to.value, search.value], closeHistory);
onBeforeUnmount(() => { alive = false; run++; placementRun++; historyRun++; });
onMounted(load);
useWebLiveRefresh(load, {
  blocked: () => busy.value || loading.value || editor.value || !!pending.value,
});
</script>
<template>
  <HeadmanPage title="Домашнее задание" class="headman-workflows" hide-title :error="error" @reload="load">
    <div class="headman-flow-tabs" role="group" aria-label="Домашнее задание">
      <button type="button" class="web-button" :class="{ 'web-button-selected': editor }" :aria-pressed="editor" :disabled="!writable || busy || !!pending" @click="!editor && openEditor()">{{ editing && editor ? 'Редактирование' : 'Добавить' }}</button>
      <button type="button" class="web-button" :class="{ 'web-button-selected': !editor && !archive }" :aria-pressed="!editor && !archive" :disabled="loading || busy || !!pending" @click="selectSection(false)">Актуальное</button>
      <button type="button" class="web-button" :class="{ 'web-button-selected': !editor && archive }" :aria-pressed="!editor && archive" :disabled="loading || busy || !!pending" @click="selectSection(true)">Архив</button>
    </div>
    <p v-if="pending" class="web-notice" role="status">Сохранено незавершённое изменение. Новая запись заблокирована до проверки результата. <button class="web-button" :disabled="busy" @click="execute(true)">Проверить результат</button></p>
    <form v-if="editor" class="headman-homework-composer web-stack" @submit.prevent="save">
      <h2>{{ editing ? 'Изменить задание' : 'Добавить задание' }}</h2>
      <fieldset :disabled="busy || !!pending" class="headman-flow-fields">
        <WebSelect v-model="draft.subjectId" label="Предмет" :options="subjects.map(item => ({ value: item.id, label: item.name }))" :disabled="!!editing || busy || !!pending" required @change="changePlacementContext" />
        <WebSelect v-model="draft.lessonType" label="Тип занятия" :options="types.map(type => ({ value: type, label: lessonTypes[type] ?? type }))" :disabled="busy || !!pending" required @change="changePlacementContext" />
        <label class="headman-flow-toggle"><input v-model="draft.bound" type="checkbox" @change="changePlacementContext" /><span>{{ draft.bound ? 'Привязано к паре' : 'На выбранную дату' }}</span></label>
        <div class="headman-homework-placement-row">
          <button v-if="draft.bound" type="button" class="web-button" :disabled="placementLoading || busy || !!pending" @click="findNext">Ближайшая пара</button>
          <WebDateField v-model="draft.date" label="Дата" :min="editing && !placementTouched && editing.lessonDate < today() ? editing.lessonDate : today()" :max="semester?.dateTo" :today="today()" :allowed-dates="draft.bound ? placementDates : undefined" :disabled="busy || !!pending || (draft.bound && placementLoading)" required @change="chooseDate" @validity="dateValidity" />
        </div>
        <WebSelect v-if="draft.bound && dateLessons.length > 1" v-model="selectedLessonId" label="Пара" :options="[{ value: 0, label: 'Выбери пару' }, ...dateLessons.map(lesson => ({ value: lesson.id, label: `№${lesson.lessonNumber} · ${lesson.startTime?.slice(0, 5) ?? '—'} — ${lesson.endTime?.slice(0, 5) ?? '—'}` }))]" :disabled="busy || !!pending || placementLoading" required />
        <p v-if="placementLoading" class="web-caption" role="status">Загрузка расписания…</p>
        <p v-else-if="placementError" class="web-error" role="alert">{{ placementError }}</p>
        <p v-else-if="draft.bound && !nextLesson" class="web-caption">{{ editing && !placementTouched ? 'Сохранение прежней привязки.' : placementLessons.length ? 'Выбери дату и пару в расписании.' : 'Подходящих будущих пар нет.' }}</p>
        <label class="web-field">Заголовок<input v-model="draft.title" maxlength="255" required /></label>
        <label class="web-field">Основная ссылка<input v-model="draft.link" type="url" maxlength="2048" placeholder="https://" /></label>
        <label class="web-field">Описание<textarea v-model="draft.description" maxlength="4000" rows="6" /></label>
      </fieldset>
      <div class="headman-flow-form-actions"><button type="button" class="web-button" :disabled="busy || !!pending" @click="closeEditor">Отмена</button><button class="web-button web-button-primary" :disabled="busy || !!pending || placementLoading || !dateValid || (draft.bound && (!editing || placementTouched) && !nextLesson)">{{ editing ? 'Сохранить' : 'Опубликовать' }}</button></div>
    </form>
    <template v-else>
      <form class="headman-flow-filter-grid headman-homework-filters" @submit.prevent="load">
        <WebSelect v-model="semesterId" label="Семестр" :options="semesters.map(item => ({ value: item.id, label: item.name + (item.active ? '' : ' · архив') }))" :disabled="busy || !!pending" />
        <WebSelect v-model="subjectFilter" label="Предмет" :options="[{ value: 0, label: 'Все предметы' }, ...subjects.map(item => ({ value: item.id, label: item.name }))]" />
        <WebDateField v-model="from" label="С даты" :max="to || semester?.dateTo" :today="today()" /><WebDateField v-model="to" label="По дату" :min="from || semester?.dateFrom" :today="today()" />
        <label class="web-field">Поиск<input v-model="search" type="search" /></label>
      </form>
      <section v-for="group in grouped" :key="group.date" class="headman-homework-group web-stack"><h2>{{ group.date === today() ? 'Сегодня, ' : '' }}{{ dateLabel(group.date) }}</h2>
        <div v-for="item in group.items" :key="item.id" class="headman-homework-entry"><article class="headman-flow-card headman-homework-card">
          <div class="headman-heading"><div class="web-stack"><p v-if="item.title" class="web-caption">{{ subjects.find(subject => subject.id === item.subjectId)?.name ?? 'Предмет недоступен' }}</p><h3>{{ item.title || subjects.find(subject => subject.id === item.subjectId)?.name }}</h3></div><span v-if="item.archived" class="headman-flow-status" data-status="CANCELLED">Архив</span></div>
          <p v-if="item.description" class="headman-flow-description"><LinkifiedText :text="item.description" /></p><a v-if="item.link" :href="item.link" target="_blank" rel="noopener noreferrer" :title="item.link">Основная ссылка<span class="web-sr-only"> (откроется в новой вкладке)</span></a>
          <div class="headman-flow-card-footer"><p v-if="publisher(item.publishedBy)" class="web-caption">{{ publisher(item.publishedBy) }}</p><div class="headman-actions"><button type="button" class="web-button" :aria-expanded="historyId === item.id" :aria-controls="`homework-history-${item.id}`" @click="showHistory(item)">История</button><template v-if="!item.archived && writable"><button type="button" class="web-button" :disabled="busy || !!pending" @click="openEditor(item)">Изменить</button><button type="button" class="web-button" :disabled="busy || !!pending" @click="remove(item)">Удалить</button></template></div></div>
        </article>
        <HomeworkHistory v-if="historyId === item.id" :id="`homework-history-${item.id}`" :title="item.title" :entries="history" :loading="historyLoading" :error="historyError" @close="closeHistory" />
        </div>
      </section>
      <p v-if="!loading && !error && !visible.length" class="headman-flow-empty">Заданий по этим условиям нет.</p><p v-if="loading && !rows.length" class="web-caption" role="status">Загрузка заданий…</p>
    </template>
  </HeadmanPage>
</template>
