<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue';
import { useWebLiveRefresh } from '../../app/live';
import { message, webSession } from '../../auth/session';
import { useHeadmanFeature, today, lessonTypes, dateLabel, addDays } from './context';
import type {
  HeadmanSubject,
  HeadmanSemester,
  HeadmanSubjectType,
  HeadmanTeacher,
  HeadmanSubjectInitialAssignment,
  HeadmanSubjectAssignment,
  HeadmanSemesterProgress,
  AssignmentReplacementInput,
  AssignmentReplacementResponse,
} from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import { HeadmanSubjectsApiError } from '../../../../mobile-core/src/features/headman-subjects/headman-subjects-client';
import {
  parseSubjectEditIntent,
  subjectEditStorageKey,
  type SubjectEditIntent,
} from '../../../../mobile-core/src/features/headman-subjects/subject-edit-intent';
import { createSubjectSaveFlow } from '../../../../mobile-core/src/features/headman-subjects/subject-save-flow';
import HeadmanPage from './HeadmanPage.vue';
import SubjectDeletion from './SubjectDeletion.vue';
const feature = useHeadmanFeature(),
  session = webSession.snapshot.value!;
const loading = ref(false),
  busy = ref(false),
  error = ref<string | null>(null),
  editor = ref(false),
  denied = ref(false);
const subjects = shallowRef<readonly HeadmanSubject[]>([]),
  semesters = shallowRef<readonly HeadmanSemester[]>([]),
  teachers = shallowRef<readonly HeadmanTeacher[]>([]),
  progress = shallowRef<HeadmanSemesterProgress | null>(null);
const semesterId = ref(0),
  selected = shallowRef<HeadmanSubject | null>(null),
  deleting = ref(0);
const semester = computed(() => semesters.value.find((item) => item.id === semesterId.value));
const draft = reactive({
  name: '',
  types: ['LECTURE'] as HeadmanSubjectType[],
  initial: [] as HeadmanSubjectInitialAssignment[],
  search: '',
  teacherId: 0,
  type: 'LECTURE' as HeadmanSubjectType,
  from: today(),
  until: '',
  replacingId: 0,
});
const searchPage = ref(0),
  searchMore = ref(false),
  searchBusy = ref(false);
const intent = shallowRef<SubjectEditIntent | null>(null),
  selectedRecoveryId = ref(0);
interface Replacement {
  assignmentId: number;
  subjectId: number;
  input: AssignmentReplacementInput;
  receipt: AssignmentReplacementResponse | null;
}
const replacement = shallowRef<Replacement | null>(null);
const storageKey = subjectEditStorageKey(session.userId, feature.groupId),
  replacementKey = `${storageKey}:web-replacement`;
const writable = computed(() => feature.writable() && !!semester.value?.active && !denied.value);
const locked = computed(() => busy.value || !!intent.value || !!replacement.value);
let alive = true,
  searchRevision = 0;
function teacherName(id: number) {
  return teachers.value.find((teacher) => teacher.id === id)?.fullName ?? `Преподаватель №${id}`;
}
function persist(next: SubjectEditIntent | null) {
  try {
    feature.assertCurrent();
    if (next) {
      const checked = parseSubjectEditIntent(JSON.stringify(next), session.userId, feature.groupId);
      if (!checked) throw new Error('Состав изменения предмета не прошёл проверку.');
      sessionStorage.setItem(storageKey, JSON.stringify(checked));
      intent.value = checked;
    } else {
      sessionStorage.removeItem(storageKey);
      intent.value = null;
    }
    return true;
  } catch (cause) {
    error.value = message(cause);
    return false;
  }
}
async function read(): Promise<boolean> {
  const values = await feature.subjects.listSubjects(feature.groupId);
  feature.assertCurrent();
  subjects.value = values;
  const ids = [...new Set(values.flatMap((subject) => subject.teacherIds))];
  const names: HeadmanTeacher[] = [];
  for (let offset = 0; offset < ids.length; offset += 100) {
    names.push(...(await feature.subjects.resolveTeachers(ids.slice(offset, offset + 100))));
    feature.assertCurrent();
  }
  teachers.value = [
    ...new Map([...teachers.value, ...names].map((teacher) => [teacher.id, teacher])).values(),
  ];
  if (semester.value) {
    const result = await feature.subjects.semesterProgress(feature.groupId, semester.value);
    feature.assertCurrent();
    progress.value = result;
  }
  if (selected.value)
    selected.value = values.find((subject) => subject.id === selected.value?.id) ?? null;
  return true;
}
const flow = createSubjectSaveFlow({
  api: () => feature.subjects,
  actor: () => session.userId,
  group: () => feature.groupId,
  available: () => writable.value,
  disposed: () => !alive,
  subjects: () => subjects.value,
  intent: () => intent.value,
  persist,
  read,
  busy: (value) => {
    busy.value = value;
  },
  error: (value) => {
    error.value = value;
  },
  denied: (value) => {
    denied.value = value;
  },
  completed: () => {
    editor.value = false;
  },
  failed: () => undefined,
});
async function load() {
  loading.value = true;
  try {
    await feature.initialize();
    if (!feature.isHeadman) throw new Error('Управление предметами доступно старосте.');
    if (!semesters.value.length) {
      const periods = await feature.subjects.listSemesters();
      feature.assertCurrent();
      semesters.value = periods;
      semesterId.value = periods.find((item) => item.active)?.id ?? periods[0]?.id ?? 0;
      const raw = sessionStorage.getItem(storageKey);
      if (raw) {
        intent.value = parseSubjectEditIntent(raw, session.userId, feature.groupId);
        if (!intent.value) throw new Error('Незавершённое изменение предмета повреждено.');
      }
      const stored = sessionStorage.getItem(replacementKey);
      if (stored) replacement.value = JSON.parse(stored) as Replacement;
    }
    await read();
  } catch (cause) {
    error.value = message(cause);
  } finally {
    loading.value = false;
  }
}
function open(subject: HeadmanSubject | null = null) {
  if (locked.value || !writable.value) return;
  selected.value = subject;
  editor.value = true;
  draft.name = subject?.name ?? '';
  draft.types = [...(subject?.lessonTypes ?? ['LECTURE'])];
  draft.initial = [];
  draft.from = today() < (semester.value?.dateFrom ?? '') ? semester.value!.dateFrom : today();
  draft.until = '';
  draft.replacingId = 0;
  error.value = null;
}
async function search(more = false) {
  const revision = ++searchRevision;
  searchBusy.value = true;
  try {
    if (!more) searchPage.value = 0;
    const result = await feature.subjects.searchTeachers(draft.search, searchPage.value);
    feature.assertCurrent();
    if (revision !== searchRevision) return;
    teachers.value = [
      ...new Map(
        [...teachers.value, ...result.items].map((teacher) => [teacher.id, teacher]),
      ).values(),
    ];
    searchMore.value = result.totalPages !== null && result.page + 1 < result.totalPages;
    if (!draft.teacherId) draft.teacherId = result.items[0]?.id ?? 0;
  } catch (cause) {
    error.value = message(cause);
  } finally {
    if (revision === searchRevision) searchBusy.value = false;
  }
}
function assignment(): HeadmanSubjectInitialAssignment {
  if (
    !semester.value ||
    !draft.types.includes(draft.type) ||
    !teachers.value.some((teacher) => teacher.id === draft.teacherId)
  )
    throw new Error('Выбери преподавателя и тип занятия.');
  if (
    draft.from < (today() > semester.value.dateFrom ? today() : semester.value.dateFrom) ||
    draft.from > semester.value.dateTo ||
    (draft.until && (draft.until <= draft.from || draft.until > addDays(semester.value.dateTo, 1)))
  )
    throw new Error('Проверь период назначения в семестре.');
  return {
    teacherId: draft.teacherId,
    semesterId: semester.value.id,
    lessonType: draft.type,
    validFrom: draft.from,
    validUntilExclusive: draft.until || null,
  };
}
function addInitial() {
  try {
    const value = assignment();
    if (!draft.initial.some((item) => JSON.stringify(item) === JSON.stringify(value)))
      draft.initial.push(value);
  } catch (cause) {
    error.value = message(cause);
  }
}
function plan(
  subject: HeadmanSubject | null,
  assignments: readonly HeadmanSubjectInitialAssignment[] = [],
  step = 0,
): SubjectEditIntent {
  return {
    version: 1,
    actorUserId: session.userId,
    groupId: feature.groupId,
    editId: crypto.randomUUID(),
    subjectId: subject?.id ?? null,
    createdId: null,
    name: subject?.name ?? draft.name,
    lessonTypes: subject?.lessonTypes ?? draft.types,
    assignments,
    removals: [],
    pendingAdoptions: [],
    step,
    unknown: false,
  };
}
async function save() {
  if (locked.value || !writable.value) return;
  if (!draft.name.trim() || !draft.types.length) {
    error.value = 'Укажи название и хотя бы один тип занятия.';
    return;
  }
  const value = plan(selected.value, selected.value ? [] : draft.initial);
  if (persist({ ...value, name: draft.name, lessonTypes: [...draft.types] })) await flow.send(true);
}
async function addTeacher() {
  if (!selected.value || locked.value || !writable.value) return;
  try {
    if (persist(plan(selected.value, [assignment()], 1))) await flow.send(true);
  } catch (cause) {
    error.value = message(cause);
  }
}
async function removeTeacher(value: HeadmanSubjectAssignment) {
  if (
    !selected.value ||
    locked.value ||
    !writable.value ||
    !window.confirm(
      `Завершить назначение «${teacherName(value.teacherId)}» с ${dateLabel(draft.from)}? Прошедшая история сохранится.`,
    )
  )
    return;
  const current = plan(selected.value, [], 1);
  if (
    persist({
      ...current,
      removals: [
        {
          subjectId: selected.value.id,
          teacherId: value.teacherId,
          assignmentId: value.id,
          validUntilExclusive: draft.from,
          requestKey: crypto.randomUUID(),
        },
      ],
    })
  )
    await flow.send(true);
}
function persistReplacement(value: Replacement | null) {
  feature.assertCurrent();
  if (value) sessionStorage.setItem(replacementKey, JSON.stringify(value));
  else sessionStorage.removeItem(replacementKey);
  replacement.value = value;
}
async function replace(recover = false) {
  if (busy.value || !writable.value || intent.value) return;
  let pending = replacement.value;
  if (!pending) {
    if (!selected.value || !draft.replacingId || !draft.teacherId) return;
    pending = {
      subjectId: selected.value.id,
      assignmentId: draft.replacingId,
      input: {
        replacementTeacherId: String(draft.teacherId),
        effectiveFrom: draft.from,
        requestKey: crypto.randomUUID(),
      },
      receipt: null,
    };
    if (
      !window.confirm(
        `Заменить преподавателя с ${dateLabel(draft.from)}? Будущие пары перейдут новому назначению, прошедшая история сохранится.`,
      )
    )
      return;
    persistReplacement(pending);
  }
  busy.value = true;
  try {
    if (recover) await read();
    const receipt = pending.receipt
      ? await feature.subjects.getReplacementStatus(pending.receipt.operationId)
      : await feature.subjects.replaceAssignment(pending.assignmentId, pending.input);
    feature.assertCurrent();
    if (
      receipt.sourceAssignmentId !== pending.assignmentId ||
      receipt.targetTeacherId !== Number(pending.input.replacementTeacherId) ||
      receipt.subjectId !== pending.subjectId ||
      receipt.groupId !== feature.groupId
    )
      throw new Error('Сервер подтвердил другую замену.');
    persistReplacement({ ...pending, receipt });
    if (receipt.state === 'COMMITTED') {
      persistReplacement(null);
      draft.replacingId = 0;
      await read();
      error.value = null;
    } else error.value = 'Замена принята, но ещё не завершена. Проверь исходную операцию.';
  } catch (cause) {
    if (
      !recover &&
      cause instanceof HeadmanSubjectsApiError &&
      [400, 403, 409, 422].includes(cause.response.status)
    )
      persistReplacement(null);
    error.value = message(cause);
  } finally {
    busy.value = false;
  }
}
onMounted(load);
onBeforeUnmount(() => {
  alive = false;
  flow.reset();
});
useWebLiveRefresh(load, {
  blocked: () => loading.value || locked.value || editor.value || !!deleting.value,
});
</script>
<template>
  <HeadmanPage
    title="Предметы"
    :loading="loading"
    :error="error"
    @reload="load"
    ><template #actions
      ><button
        class="web-button web-button-primary"
        :disabled="locked || !writable"
        @click="open()"
        >Добавить предмет</button
      ></template
    ><form
      class="headman-toolbar"
      @submit.prevent="load"
      ><label class="web-field"
        >Семестр<select
          v-model.number="semesterId"
          :disabled="locked || editor || !!deleting"
          ><option
            v-for="period in semesters"
            :key="period.id"
            :value="period.id"
            >{{ period.name }}{{ period.active ? '' : ' · архив' }}</option
          ></select
        ></label
      ><button
        class="web-button"
        :disabled="locked || editor"
        >Показать</button
      ></form
    >
    <section
      v-if="intent"
      class="web-notice web-stack"
      ><p
        >Сохранено незавершённое изменение «{{ intent.name }}». Подтверждено шагов:
        {{ intent.step }}.</p
      ><template v-if="intent.subjectId === null && intent.createdId === null && intent.unknown"
        ><label class="web-field"
          >Проверить конкретный созданный предмет<select v-model.number="selectedRecoveryId"
            ><option :value="0">Выбери результат на сервере</option
            ><option
              v-for="subject in subjects"
              :key="subject.id"
              :value="subject.id"
              >{{ subject.name }} · №{{ subject.id }}</option
            ></select
          ></label
        ><button
          class="web-button"
          :disabled="busy || !selectedRecoveryId"
          @click="flow.reconcile(selectedRecoveryId, true)"
          >Подтвердить выбранный результат</button
        ></template
      ><button
        class="web-button"
        :disabled="busy"
        @click="flow.reconcile()"
        >Проверить состояние</button
      ><button
        v-if="flow.canContinue()"
        class="web-button"
        :disabled="busy"
        @click="flow.send()"
        >Продолжить исходное сохранение</button
      ><button
        v-if="intent.rejected"
        class="web-button"
        :disabled="busy"
        @click="flow.cancelRejected()"
        >Отменить отклонённую часть</button
      ></section
    ><p
      v-if="replacement"
      class="web-notice"
      >Замена преподавателя сохранена.
      <button
        class="web-button"
        :disabled="busy"
        @click="replace(true)"
        >Проверить замену</button
      ></p
    >
    <div class="headman-table-scroll"
      ><table class="headman-table"
        ><thead
          ><tr
            ><th>Предмет</th><th>Тип занятия</th><th>Преподаватели</th><th>Пройдено / всего</th
            ><th>Действия</th></tr
          ></thead
        ><tbody
          ><template
            v-for="subject in subjects"
            :key="subject.id"
            ><tr
              v-for="type in subject.lessonTypes"
              :key="type"
              ><th scope="row">{{ subject.name }}</th
              ><td>{{ lessonTypes[type] }}</td
              ><td>{{
                subject.assignments
                  .filter(
                    (assignment) =>
                      assignment.semesterId === semesterId &&
                      assignment.lessonType === type &&
                      (!assignment.validUntilExclusive || assignment.validUntilExclusive > today()),
                  )
                  .map((assignment) => teacherName(assignment.teacherId))
                  .join(' · ') || 'Нет назначения'
              }}</td
              ><td
                ><template
                  v-if="
                    progress?.items.find(
                      (item) => item.subjectId === subject.id && item.lessonType === type,
                    )
                  "
                  >{{
                    progress.items.find(
                      (item) => item.subjectId === subject.id && item.lessonType === type,
                    )!.done
                  }}
                  /
                  {{
                    progress.items.find(
                      (item) => item.subjectId === subject.id && item.lessonType === type,
                    )!.total
                  }}</template
                ><span v-else>Нет данных</span></td
              ><td
                ><div class="headman-actions"
                  ><button
                    class="web-button"
                    :disabled="locked || !writable"
                    @click="open(subject)"
                    >Изменить</button
                  ><button
                    class="web-button"
                    :disabled="locked || !writable"
                    @click="
                      deleting = subject.id;
                      editor = false;
                    "
                    >Удалить</button
                  ></div
                ></td
              ></tr
            ></template
          ></tbody
        ></table
      ></div
    >
    <form
      v-if="editor"
      class="headman-panel web-stack"
      @submit.prevent="save"
      ><h2>{{ selected ? 'Изменить предмет' : 'Добавить предмет' }}</h2
      ><fieldset :disabled="locked"
        ><label class="web-field"
          >Название предмета<input
            v-model="draft.name"
            required
            maxlength="255" /></label
        ><fieldset
          ><legend>Типы занятий</legend
          ><label
            v-for="(label, code) in lessonTypes"
            :key="code"
            ><input
              v-model="draft.types"
              type="checkbox"
              :value="code"
            />
            {{ label }}</label
          ></fieldset
        ></fieldset
      ><div class="headman-actions"
        ><button
          class="web-button web-button-primary"
          :disabled="locked || !writable"
          >Сохранить предмет</button
        ><button
          type="button"
          class="web-button"
          :disabled="locked"
          @click="editor = false"
          >Отмена</button
        ></div
      >
      <h3>Преподаватели</h3
      ><div class="headman-toolbar"
        ><label class="web-field"
          >Найти по имени или табельному номеру<input
            v-model="draft.search"
            type="search"
            :disabled="locked" /></label
        ><button
          type="button"
          class="web-button"
          :disabled="locked || searchBusy"
          @click="search()"
          >Найти</button
        ><button
          v-if="searchMore"
          type="button"
          class="web-button"
          :disabled="locked || searchBusy"
          @click="
            searchPage++;
            search(true);
          "
          >Ещё преподаватели</button
        ></div
      ><fieldset :disabled="locked"
        ><label class="web-field"
          >Преподаватель<select v-model.number="draft.teacherId"
            ><option :value="0">Выбери преподавателя</option
            ><option
              v-for="teacher in teachers"
              :key="teacher.id"
              :value="teacher.id"
              >{{ teacher.fullName
              }}{{ teacher.employeeNumber ? ` · ${teacher.employeeNumber}` : '' }}</option
            ></select
          ></label
        ><label class="web-field"
          >Тип занятия<select v-model="draft.type"
            ><option
              v-for="type in draft.types"
              :key="type"
              :value="type"
              >{{ lessonTypes[type] }}</option
            ></select
          ></label
        ><label class="web-field"
          >С даты<input
            v-model="draft.from"
            type="date"
            :min="semester?.dateFrom"
            :max="semester?.dateTo"
            required /></label
        ><label class="web-field"
          >До даты, не включая<input
            v-model="draft.until"
            type="date" /></label
        ><button
          type="button"
          class="web-button"
          @click="selected ? addTeacher() : addInitial()"
          >{{ selected ? 'Назначить преподавателя' : 'Добавить в новый предмет' }}</button
        ></fieldset
      ><ul
        v-if="!selected"
        class="headman-list"
        ><li
          v-for="(item, index) in draft.initial"
          :key="index"
          >{{ teacherName(item.teacherId) }} · {{ lessonTypes[item.lessonType] }} ·
          {{ dateLabel(item.validFrom)
          }}<button
            type="button"
            class="web-button"
            :disabled="locked"
            @click="draft.initial.splice(index, 1)"
            >Убрать из черновика</button
          ></li
        ></ul
      ><div
        v-else
        class="headman-table-scroll"
        ><table class="headman-table"
          ><thead
            ><tr><th>Преподаватель</th><th>Тип / период</th><th>Действия</th></tr></thead
          ><tbody
            ><tr
              v-for="assignment in selected.assignments.filter(
                (item) => item.semesterId === semesterId,
              )"
              :key="assignment.id"
              ><th>{{ teacherName(assignment.teacherId) }}</th
              ><td
                >{{ lessonTypes[assignment.lessonType] }} · {{ dateLabel(assignment.validFrom) }} —
                {{ dateLabel(assignment.validUntilExclusive) }}</td
              ><td
                ><button
                  type="button"
                  class="web-button"
                  :disabled="locked"
                  @click="removeTeacher(assignment)"
                  >Снять с выбранной даты</button
                ><button
                  type="button"
                  class="web-button"
                  :disabled="locked"
                  @click="draft.replacingId = assignment.id"
                  >Заменить с выбранной даты</button
                ></td
              ></tr
            ></tbody
          ></table
        ></div
      ><button
        v-if="draft.replacingId"
        type="button"
        class="web-button"
        :disabled="locked || !draft.teacherId"
        @click="replace()"
        >Подтвердить замену на {{ teacherName(draft.teacherId) }}</button
      > </form
    ><SubjectDeletion
      v-if="deleting"
      :key="deleting"
      :feature="feature"
      :subject-id="deleting"
      :semester-id="semesterId"
      @completed="
        deleting = 0;
        load();
      "
      @close="deleting = 0"
    />
  </HeadmanPage>
</template>
