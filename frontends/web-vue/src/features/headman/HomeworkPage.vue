<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef } from 'vue';
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
  canReselectHomeworkCreate,
} from '../../../../mobile-core/src/features/homework/headman-homework-state';
import { readPublishedHomeworks } from '../../../../mobile-core/src/features/homework/headman-published-homework';
import HeadmanPage from './HeadmanPage.vue';
import {
  homeworkEditMatches,
  homeworkPlacementChanged,
  canonicalHomeworkText,
} from './homework-edit-state';
import LinkifiedText from '../../shared/LinkifiedText.vue';

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
  historyTitle = ref('');
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
  placementLoading = ref(false);
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
  placementRun = 0;
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
  editing.value = item;
  editor.value = true;
  draft.title = item?.title ?? '';
  draft.description = item?.description ?? '';
  draft.link = item?.link ?? '';
  draft.subjectId = item?.subjectId ?? subjects.value[0]?.id ?? 0;
  draft.lessonType = item?.lessonType ?? 'LECTURE';
  draft.date = item?.lessonDate ?? today();
  draft.bound = item ? item.bindingMode === 'LESSON' : true;
  nextLesson.value = null;
  error.value = null;
  if (!item) void findNext();
}
async function findNext() {
  const revision = ++placementRun;
  nextLesson.value = null;
  placementLoading.value = false;
  if (!draft.bound || !draft.subjectId || !semester.value) return;
  placementLoading.value = true;
  try {
    const availableTypes = types.value;
    if (!availableTypes.includes(draft.lessonType))
      draft.lessonType = availableTypes[0] ?? 'LECTURE';
    const lesson = await feature.homework.nextHomeworkLesson(
      feature.groupId,
      semester.value.id,
      draft.subjectId,
      draft.lessonType,
      today(),
    );
    feature.assertCurrent();
    if (revision !== placementRun) return;
    if (
      lesson &&
      homeworkLessonEligible(lesson, {
        groupId: feature.groupId,
        subjectId: draft.subjectId,
        type: draft.lessonType,
        semester: semester.value,
        now: new Date(),
      })
    ) {
      nextLesson.value = lesson;
      draft.date = lesson.date;
    }
  } catch (cause) {
    if (revision === placementRun) error.value = message(cause);
  } finally {
    if (revision === placementRun) placementLoading.value = false;
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
  if (!lesson?.occurrenceId || !lesson.occurrenceRevision)
    throw new Error('Выбери подтверждённую ближайшую пару.');
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
    const text = canonicalHomeworkText(draft);
    if (editing.value) {
      if (!editing.value.revision) throw new Error('Версия задания не подтверждена.');
      const candidate: HeadmanHomeworkPlacementInput = {
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
async function showHistory(item: HeadmanManagedHomework) {
  try {
    const result = await feature.homework.history(item.id);
    feature.assertCurrent();
    history.value = result;
    historyTitle.value = item.title;
  } catch (cause) {
    error.value = message(cause);
  }
}
function historyFields(snapshot: unknown) {
  if (!snapshot || typeof snapshot !== 'object') return [];
  const value = snapshot as Record<string, unknown>;
  const fields = [
    ['title', 'Заголовок'],
    ['description', 'Описание'],
    ['link', 'Ссылка'],
    ['bindingMode', 'Размещение'],
    ['lessonDate', 'Дата'],
    ['lessonNumber', 'Номер пары'],
    ['lessonType', 'Тип занятия'],
  ] as const;
  return fields
    .filter(([key]) => key in value)
    .map(([key, label]) => {
      const item = value[key];
      const text =
        item === null || item === ''
          ? 'Не указано'
          : key === 'bindingMode'
            ? item === 'LESSON'
              ? 'На пару'
              : 'На дату'
            : key === 'lessonDate' && typeof item === 'string'
              ? dateLabel(item)
              : key === 'lessonType' && typeof item === 'string'
                ? (lessonTypes[item] ?? item)
                : typeof item === 'string' || typeof item === 'number'
                  ? String(item)
                  : 'Неизвестное значение';
      return { key, label, text };
    });
}
function historyAction(action: string) {
  return (
    (
      {
        CREATED: 'Опубликовано',
        EDITED: 'Изменено',
        DELETED: 'Удалено',
        ARCHIVED: 'В архиве',
      } as Record<string, string>
    )[action] ?? action
  );
}
onMounted(load);
useWebLiveRefresh(load, {
  blocked: () => busy.value || loading.value || editor.value || !!pending.value,
});
</script>
<template>
  <HeadmanPage
    title="Домашнее задание"
    :loading="loading"
    :error="error"
    @reload="load"
    ><template #actions
      ><button
        class="web-button web-button-primary"
        :disabled="!writable || busy || !!pending"
        @click="openEditor()"
        >Добавить</button
      ></template
    >
    <p
      v-if="pending"
      class="web-notice"
      >Сохранено незавершённое изменение. Новая запись заблокирована до проверки результата.
      <button
        class="web-button"
        :disabled="busy"
        @click="execute(true)"
        >Проверить результат</button
      ></p
    >
    <form
      class="headman-panel headman-toolbar"
      @submit.prevent="load"
      ><label class="web-field"
        >Семестр<select
          v-model.number="semesterId"
          :disabled="editor || busy || !!pending"
          ><option
            v-for="item in semesters"
            :key="item.id"
            :value="item.id"
            >{{ item.name }}{{ item.active ? '' : ' · архив' }}</option
          ></select
        ></label
      ><label class="web-field"
        >Раздел<select v-model="archive"
          ><option :value="false">Актуальные</option
          ><option :value="true">Архив</option></select
        ></label
      ><button
        class="web-button"
        :disabled="loading"
        >Показать</button
      ><label class="web-field"
        >Предмет<select v-model.number="subjectFilter"
          ><option :value="0">Все</option
          ><option
            v-for="subject in subjects"
            :key="subject.id"
            :value="subject.id"
            >{{ subject.name }}</option
          ></select
        ></label
      ><label class="web-field"
        >С даты<input
          v-model="from"
          type="date" /></label
      ><label class="web-field"
        >По дату<input
          v-model="to"
          type="date" /></label
      ><label class="web-field"
        >Поиск<input
          v-model="search"
          type="search" /></label
    ></form>
    <form
      v-if="editor"
      class="headman-panel web-stack"
      @submit.prevent="save"
      ><h2>{{ editing ? 'Изменить задание' : 'Добавить задание' }}</h2
      ><fieldset :disabled="busy || !!pending"
        ><label class="web-field"
          >Предмет<select
            v-model.number="draft.subjectId"
            :disabled="!!editing"
            required
            @change="findNext"
            ><option
              v-for="subject in subjects"
              :key="subject.id"
              :value="subject.id"
              >{{ subject.name }}</option
            ></select
          ></label
        ><label class="web-field"
          >Тип занятия<select
            v-model="draft.lessonType"
            required
            @change="findNext"
            ><option
              v-for="type in types"
              :key="type"
              :value="type"
              >{{ lessonTypes[type] }}</option
            ></select
          ></label
        ><label
          ><input
            v-model="draft.bound"
            type="checkbox"
            @change="findNext"
          />
          Привязать к паре</label
        ><p v-if="draft.bound">{{
          placementLoading
            ? 'Поиск ближайшей пары…'
            : nextLesson
              ? `Ближайшая пара: ${dateLabel(nextLesson.date)} · №${nextLesson.lessonNumber}`
              : editing && !nextLesson
                ? 'Сохранение прежней привязки. Для новой привязки выбери ближайшую пару.'
                : 'Подходящей будущей пары нет.'
        }}</p
        ><button
          v-if="draft.bound"
          type="button"
          class="web-button"
          :disabled="placementLoading"
          @click="findNext"
          >Выбрать ближайшую пару</button
        ><label class="web-field"
          >Дата<input
            v-model="draft.date"
            type="date"
            :readonly="draft.bound"
            :min="today()"
            :max="semester?.dateTo"
            required /></label
        ><label class="web-field"
          >Заголовок<input
            v-model="draft.title"
            maxlength="255"
            required /></label
        ><label class="web-field"
          >Описание<textarea
            v-model="draft.description"
            maxlength="4000"
            rows="6"
          /></label
        ><label class="web-field"
          >Основная ссылка<input
            v-model="draft.link"
            type="url"
            maxlength="2048"
            placeholder="https://" /></label></fieldset
      ><div class="headman-actions"
        ><button
          class="web-button web-button-primary"
          :disabled="busy || !!pending || placementLoading"
          >{{ editing ? 'Сохранить' : 'Опубликовать' }}</button
        ><button
          type="button"
          class="web-button"
          :disabled="busy || !!pending"
          @click="editor = false"
          >Отмена</button
        ></div
      ></form
    >
    <div class="web-stack"
      ><article
        v-for="item in visible"
        :key="item.id"
        class="headman-panel web-stack"
        ><div class="headman-heading"
          ><h2>{{ item.title }}</h2
          ><span v-if="item.archived">Архив</span></div
        ><p class="web-caption"
          >{{ subjects.find((subject) => subject.id === item.subjectId)?.name }} ·
          {{ lessonTypes[item.lessonType ?? ''] }} · {{ dateLabel(item.lessonDate)
          }}{{ item.lessonNumber ? ` · пара №${item.lessonNumber}` : '' }} ·
          {{ item.bindingMode === 'LESSON' ? 'К паре' : 'На дату' }}</p
        ><LinkifiedText
          v-if="item.description"
          :text="item.description"
        /><a
          v-if="item.link"
          :href="item.link"
          target="_blank"
          rel="noopener noreferrer"
          >{{ item.link }}</a
        ><p class="web-caption"
          >Автор №{{ item.publishedBy ?? '—' }} · версия {{ item.revision ?? '—' }}</p
        ><div class="headman-actions"
          ><button
            class="web-button"
            @click="showHistory(item)"
            >История</button
          ><template v-if="!item.archived && writable"
            ><button
              class="web-button"
              :disabled="busy || !!pending"
              @click="openEditor(item)"
              >Изменить</button
            ><button
              class="web-button"
              :disabled="busy || !!pending"
              @click="remove(item)"
              >Удалить</button
            ></template
          ></div
        ></article
      ></div
    ><p
      v-if="!loading && !visible.length"
      class="web-notice"
      >Заданий по этим условиям нет.</p
    >
    <section
      v-if="history"
      class="headman-panel web-stack"
      ><div class="headman-heading"
        ><h2>История · {{ historyTitle }}</h2
        ><button
          class="web-button"
          @click="history = null"
          >Закрыть</button
        ></div
      ><ol class="headman-list"
        ><li
          v-for="entry in history"
          :key="entry.id"
          ><p
            >{{ new Date(entry.occurredAt).toLocaleString('ru-RU') }} ·
            {{
              entry.actorDisplayName ?? (entry.actorId ? `Автор №${entry.actorId}` : 'Система')
            }}
            · {{ historyAction(entry.action) }} · версия {{ entry.revision }}</p
          ><details
            ><summary>Изменения</summary
            ><div class="headman-work-grid">
              <section
                v-for="[snapshot, label] in [
                  [entry.before, 'До изменения'],
                  [entry.after, 'После изменения'],
                ]"
                :key="String(label)"
              >
                <h3>{{ label }}</h3>
                <dl
                  v-if="historyFields(snapshot).length"
                  class="headman-facts"
                >
                  <template
                    v-for="field in historyFields(snapshot)"
                    :key="field.key"
                    ><dt>{{ field.label }}</dt
                    ><dd><LinkifiedText :text="field.text" /></dd
                  ></template> </dl
                ><p v-else>Нет записи</p>
              </section>
            </div>
          </details></li
        ></ol
      ><p v-if="!history.length">История пока пуста.</p></section
    >
  </HeadmanPage>
</template>
